"""Unit tests for protocol + receiver logic (no sockets, no drivers).

Run:  python -m unittest discover -s tests -v      (from windows-receiver/)
"""
from __future__ import annotations

import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ["APPDATA"] = tempfile.mkdtemp()  # keep tests away from the real config

from f1wheel import protocol as P  # noqa: E402
from f1wheel.backends import Backend  # noqa: E402
from f1wheel.config import Config  # noqa: E402
from f1wheel.engine import Receiver  # noqa: E402

ADDR = ("192.168.1.50", 50000)


class FakeBackend(Backend):
    name = "fake"
    connected = True

    def __init__(self):
        self.history = []

    def set_state(self, steer, throttle, brake, buttons):
        self.history.append((steer, throttle, brake, frozenset(buttons)))


class Phone:
    def __init__(self, pin="123456", session=0xABCD):
        self.key = P.derive_key(pin)
        self.session = session
        self.seq = 0
        self.gu = 0
        self.gd = 0

    def pkt(self, ptype, payload=b""):
        self.seq += 1
        return P.encode(self.key, ptype, self.session, self.seq, payload)

    def hello(self):
        return self.pkt(P.T_HELLO, P.pack_hello("Nothing Phone (2)"))

    def state(self, steer=0.0, thr=0.0, brk=0.0, buttons=0, flags=P.F_ACTIVE | P.F_SENSOR_OK):
        return self.pkt(P.T_STATE, P.pack_state(P.StateMsg(
            phone_ms=1000 + self.seq, steer=steer, throttle=thr, brake=brk, buttons=buttons,
            gear_up=self.gu, gear_down=self.gd, raw_angle_deg=steer * 90, sensor_hz=400,
            rtt_ms=8.5, flags=flags)))


def make(pin="123456"):
    cfg = Config(pin=pin)
    be = FakeBackend()
    return Receiver(cfg, backend=be, clock=lambda: 0.0), be


class ProtocolTests(unittest.TestCase):
    def test_roundtrip_state(self):
        ph = Phone()
        pkt = P.decode(ph.key, ph.state(steer=-0.5, thr=1.0, brk=0.25, buttons=0b101))
        st = P.unpack_state(pkt.payload)
        self.assertAlmostEqual(st.steer, -0.5, places=3)
        self.assertAlmostEqual(st.throttle, 1.0, places=3)
        self.assertAlmostEqual(st.brake, 0.25, places=3)
        self.assertEqual(st.buttons, 0b101)
        self.assertAlmostEqual(st.rtt_ms, 8.5)

    def test_wrong_pin_rejected(self):
        data = Phone(pin="000000").state()
        self.assertIsNone(P.decode(P.derive_key("123456"), data))

    def test_tampered_rejected(self):
        ph = Phone()
        data = bytearray(ph.state(steer=0.1))
        data[14] ^= 0xFF
        self.assertIsNone(P.decode(ph.key, bytes(data)))

    def test_state_size_matches_android(self):
        # Protocol.kt asserts the same sizes; keep them in sync.
        self.assertEqual(P.HEADER.size, 12)
        self.assertEqual(P.STATE.size, 25)
        self.assertEqual(P.ACK.size, 11)


class ReceiverTests(unittest.TestCase):
    def test_connect_and_ack(self):
        rx, _ = make()
        ph = Phone()
        reply = rx.handle_datagram(ph.hello(), ADDR, 0.0)
        self.assertIsNotNone(reply)
        ack = P.decode(ph.key, reply)
        self.assertEqual(ack.type, P.T_ACK)
        self.assertTrue(rx.snapshot()["connected"])

    def test_wrong_pin_ignored(self):
        rx, _ = make()
        rx.handle_datagram(Phone(pin="999999").hello(), ADDR, 0.0)
        self.assertIsNone(rx.session)
        self.assertEqual(rx.stats.rejected, 1)

    def test_analog_output(self):
        rx, be = make()
        ph = Phone()
        rx.handle_datagram(ph.hello(), ADDR, 0.0)
        rx.handle_datagram(ph.state(steer=0.4, thr=0.7, brk=0.0), ADDR, 0.01)
        steer, thr, brk, _ = rx.compute_output(0.02)
        self.assertAlmostEqual(steer, 0.4, places=3)
        self.assertAlmostEqual(thr, 0.7, places=3)

    def test_timeout_neutralises(self):
        rx, _ = make()
        ph = Phone()
        rx.handle_datagram(ph.hello(), ADDR, 0.0)
        rx.handle_datagram(ph.state(steer=1.0, thr=1.0, brk=0.5, buttons=P.BUTTON_BIT["drs"]), ADDR, 0.0)
        self.assertEqual(rx.compute_output(0.1)[1], 1.0)
        out = rx.compute_output(0.2)  # 200 ms of silence > 150 ms timeout
        self.assertEqual(out, (0.0, 0.0, 0.0, frozenset()))
        self.assertEqual(rx.stats.interruptions, 1)

    def test_inactive_flag_neutralises(self):
        rx, _ = make()
        ph = Phone()
        rx.handle_datagram(ph.hello(), ADDR, 0.0)
        rx.handle_datagram(ph.state(thr=1.0, flags=P.F_SENSOR_OK), ADDR, 0.0)
        self.assertEqual(rx.compute_output(0.01)[1], 0.0)

    def test_bye_neutralises_immediately(self):
        rx, _ = make()
        ph = Phone()
        rx.handle_datagram(ph.hello(), ADDR, 0.0)
        rx.handle_datagram(ph.state(thr=1.0), ADDR, 0.0)
        rx.handle_datagram(ph.pkt(P.T_BYE), ADDR, 0.01)
        self.assertIsNone(rx.session)
        self.assertEqual(rx.compute_output(0.02)[1], 0.0)

    def test_replay_rejected(self):
        rx, _ = make()
        ph = Phone()
        rx.handle_datagram(ph.hello(), ADDR, 0.0)
        old = ph.state(thr=1.0)
        rx.handle_datagram(old, ADDR, 0.0)
        rx.handle_datagram(ph.state(thr=0.0), ADDR, 0.01)
        rx.handle_datagram(old, ADDR, 0.02)       # replayed packet
        self.assertEqual(rx.throttle, 0.0)
        self.assertGreaterEqual(rx.stats.rejected, 1)

    def _gear_presses(self, rx, t0, t1, button="A"):
        """Count rising edges of `button` between t0 and t1 at 4 ms steps."""
        presses, prev, t = 0, False, t0
        while t < t1:
            on = button in rx.compute_output(t)[3]
            presses += on and not prev
            prev = on
            t += 0.004
        return presses

    def test_one_press_one_shift_even_with_duplicates(self):
        rx, _ = make()
        ph = Phone()
        rx.handle_datagram(ph.hello(), ADDR, 0.0)
        rx.handle_datagram(ph.state(), ADDR, 0.0)      # seeds counters
        ph.gu += 1
        # the same counter value repeats in every following packet
        t = 0.005
        for _ in range(20):
            rx.handle_datagram(ph.state(), ADDR, t)
            t += 0.005
        self.assertEqual(self._gear_presses(rx, 0.005, 0.1), 1)

    def test_lost_packets_do_not_lose_shift(self):
        rx, _ = make()
        ph = Phone()
        rx.handle_datagram(ph.hello(), ADDR, 0.0)
        rx.handle_datagram(ph.state(), ADDR, 0.0)
        ph.gu += 2           # two quick presses ...
        ph.state(); ph.state()  # ... whose packets are lost
        rx.handle_datagram(ph.state(), ADDR, 0.01)
        self.assertEqual(rx.stats.dropped, 2)
        t, presses, prev = 0.01, 0, False
        while t < 0.4:
            rx.handle_datagram(ph.state(), ADDR, t)  # keep the link alive
            on = "A" in rx.compute_output(t)[3]
            presses += on and not prev
            prev = on
            t += 0.004
        self.assertEqual(presses, 2)

    def test_gear_down_separate_button(self):
        rx, _ = make()
        ph = Phone()
        rx.handle_datagram(ph.hello(), ADDR, 0.0)
        rx.handle_datagram(ph.state(), ADDR, 0.0)
        ph.gd += 1
        rx.handle_datagram(ph.state(), ADDR, 0.005)
        btns = rx.compute_output(0.006)[3]
        self.assertIn("X", btns)
        self.assertNotIn("A", btns)

    def test_initial_counter_not_replayed(self):
        """A phone that reconnects with gear counter 37 must not fire 37 shifts."""
        rx, _ = make()
        ph = Phone()
        ph.gu = 37
        rx.handle_datagram(ph.hello(), ADDR, 0.0)
        rx.handle_datagram(ph.state(), ADDR, 0.0)
        self.assertEqual(self._gear_presses(rx, 0.0, 0.1), 0)

    def test_second_phone_cannot_hijack_live_session(self):
        rx, _ = make()
        a, b = Phone(session=1), Phone(session=2)
        rx.handle_datagram(a.hello(), ADDR, 0.0)
        rx.handle_datagram(b.hello(), ("192.168.1.99", 1), 0.1)
        self.assertEqual(rx.session, 1)
        rx.handle_datagram(b.hello(), ("192.168.1.99", 1), 2.0)  # A silent for 2 s
        self.assertEqual(rx.session, 2)

    def test_old_session_cannot_be_replayed_after_reconnect(self):
        """Captured packets from an earlier session must not drive the car later."""
        rx, _ = make()
        ph = Phone(session=7)
        captured = [ph.hello(), ph.state(thr=1.0), ph.state(thr=1.0)]
        for i, p in enumerate(captured):
            rx.handle_datagram(p, ADDR, i * 0.005)
        rx.handle_datagram(ph.pkt(P.T_BYE), ADDR, 0.02)
        self.assertIsNone(rx.session)
        for p in captured:                       # attacker replays the recording
            rx.handle_datagram(p, ADDR, 5.0)
        self.assertIsNone(rx.session)
        self.assertEqual(rx.compute_output(5.01)[1], 0.0)
        # ... but the real phone continuing its sequence is fine
        rx.handle_datagram(ph.state(thr=0.5), ADDR, 5.1)
        self.assertEqual(rx.session, 7)

    def test_leaving_drive_screen_is_not_an_interruption(self):
        rx, _ = make()
        ph = Phone()
        rx.handle_datagram(ph.hello(), ADDR, 0.0)
        rx.handle_datagram(ph.state(thr=1.0), ADDR, 0.0)
        rx.compute_output(0.01)
        rx.handle_datagram(ph.state(thr=1.0, flags=P.F_SENSOR_OK), ADDR, 0.02)
        self.assertEqual(rx.compute_output(0.03), (0.0, 0.0, 0.0, frozenset()))
        self.assertEqual(rx.stats.interruptions, 0)

    def test_button_mapping(self):
        rx, _ = make()
        ph = Phone()
        rx.handle_datagram(ph.hello(), ADDR, 0.0)
        rx.handle_datagram(ph.state(buttons=P.BUTTON_BIT["drs"] | P.BUTTON_BIT["clutch"]), ADDR, 0.0)
        self.assertEqual(rx.compute_output(0.01)[3], frozenset({"Y", "LB"}))

    def test_pad_buttons_are_fixed(self):
        """PAD mode buttons ignore the user mapping and always hit the same-named Xbox button."""
        rx, _ = make()
        rx.cfg.mapping["gear_up"] = "B"       # must not steal B from the pad
        ph = Phone()
        rx.handle_datagram(ph.hello(), ADDR, 0.0)
        bits = P.BUTTON_BIT["pad_a"] | P.BUTTON_BIT["dpad_left"] | P.BUTTON_BIT["start"]
        rx.handle_datagram(ph.state(buttons=bits), ADDR, 0.0)
        self.assertEqual(rx.compute_output(0.01)[3], frozenset({"A", "DPAD_LEFT", "START"}))
        self.assertEqual(len(P.BUTTONS), 18)
        self.assertEqual(set(P.PAD_XBOX), set(P.PAD_BUTTONS))


if __name__ == "__main__":
    unittest.main()
