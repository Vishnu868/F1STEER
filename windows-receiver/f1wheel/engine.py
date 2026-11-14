"""Receiver core: network input -> validated state -> virtual controller.

Threading model
    * net thread   : blocking UDP recv, validates packets (handle_datagram)
    * output thread: 250 Hz loop, applies timeout/neutral + gear pulses (tick)
    * beacon thread: 1 Hz discovery broadcast
    * UI thread    : reads snapshot() only

handle_datagram() and tick() take an explicit `now` so they are unit-testable
without sockets or drivers (see tests/test_engine.py).
"""
from __future__ import annotations

import logging
import socket
import threading
import time
from collections import deque
from dataclasses import dataclass, field

from . import protocol as P
from .backends import Backend, BackendError, NullBackend, create_backend
from .config import Config

log = logging.getLogger("f1wheel")

OUTPUT_HZ = 250


@dataclass
class GearChannel:
    """Turns counter increments into discrete, well-separated button presses."""
    pending: int = 0
    pressed_until: float = 0.0
    next_allowed: float = 0.0
    total: int = 0

    def is_pressed(self, now: float) -> bool:
        return now < self.pressed_until

    def tick(self, now: float, press_s: float, gap_s: float) -> bool:
        if self.pending > 0 and now >= self.next_allowed:
            self.pending -= 1
            self.total += 1
            self.pressed_until = now + press_s
            self.next_allowed = now + press_s + gap_s
        return self.is_pressed(now)

    def clear(self) -> None:
        self.pending = 0
        self.pressed_until = 0.0


@dataclass
class Stats:
    rx_packets: int = 0
    rejected: int = 0
    dropped: int = 0
    interruptions: int = 0
    rx_hz: float = 0.0
    out_hz: float = 0.0
    phone_rtt_ms: float | None = None
    sensor_hz: int = 0
    _rx_window: deque = field(default_factory=lambda: deque(maxlen=2000))
    _out_window: deque = field(default_factory=lambda: deque(maxlen=2000))


class Receiver:
    def __init__(self, cfg: Config, backend: Backend | None = None, clock=time.perf_counter):
        self.cfg = cfg
        self.clock = clock
        self.key = P.derive_key(cfg.pin)
        self.backend: Backend = backend if backend is not None else NullBackend()
        self.backend_error: str | None = None
        self.lock = threading.RLock()
        self.stats = Stats()
        self.events: deque[str] = deque(maxlen=200)

        # session
        self.session: int | None = None
        self.last_seq = 0
        self.phone_name = ""
        self.phone_addr: tuple | None = None
        self.last_rx = -1e9
        self.last_ack = -1e9
        self.counters_seeded = False
        self.last_gu = 0
        self.last_gd = 0
        self.timed_out = True
        self._seq_memory: dict[int, int] = {}

        # latest input (from phone)
        self.steer = 0.0
        self.throttle = 0.0
        self.brake = 0.0
        self.buttons = 0
        self.flags = 0
        self.raw_angle = 0.0

        self.gear_up = GearChannel()
        self.gear_down = GearChannel()

        # dashboard test overrides
        self.test_until = 0.0
        self.test_state: tuple | None = None  # (steer, thr, brk, set(buttons))

        # last applied output, for the dashboard
        self.out = (0.0, 0.0, 0.0, frozenset())

        self._sock: socket.socket | None = None
        self._threads: list[threading.Thread] = []
        self._running = threading.Event()
        self._stopping = threading.Event()

    # ------------------------------------------------------------------ events
    def event(self, msg: str) -> None:
        stamp = time.strftime("%H:%M:%S")
        self.events.append(f"{stamp}  {msg}")
        log.info(msg)

    # ---------------------------------------------------------------- backend
    def open_backend(self) -> None:
        with self.lock:
            try:
                self.backend.close()
            except Exception:
                pass
            try:
                self.backend = create_backend(self.cfg.backend)
                self.backend_error = None
                self.event(f"Virtual controller ready ({self.backend.name})")
            except BackendError as e:
                self.backend = NullBackend()
                self.backend_error = str(e)
                self.event(f"Controller error: {e}")

    # ------------------------------------------------------------ pairing
    def set_pin(self, pin: str) -> None:
        with self.lock:
            self.cfg.pin = pin
            self.cfg.save()
            self.key = P.derive_key(pin)
            self._end_session("PIN changed")

    def _end_session(self, reason: str) -> None:
        if self.session is not None:
            self.event(f"Phone disconnected: {reason}")
        self.session = None
        self.phone_addr = None
        self._neutral_inputs()

    def _neutral_inputs(self) -> None:
        self.steer = self.throttle = self.brake = 0.0
        self.buttons = 0
        self.flags = 0
        self.gear_up.clear()
        self.gear_down.clear()

    # ------------------------------------------------------------ network in
    def handle_datagram(self, data: bytes, addr, now: float) -> bytes | None:
        """Process one datagram. Returns a reply datagram (ACK) or None."""
        pkt = P.decode(self.key, data)
        with self.lock:
            if pkt is None:
                self.stats.rejected += 1
                return None
            idle = now - self.last_rx
            if pkt.session != self.session:
                if pkt.type == P.T_BYE:
                    return None
                # A different phone/session may only take over an idle receiver.
                if self.session is not None and idle * 1000 < self.cfg.takeover_ms:
                    self.stats.rejected += 1
                    return None
                # A session id seen earlier must continue from its last sequence
                # number, so captured packets cannot be replayed after a reconnect.
                if pkt.seq <= self._seq_memory.get(pkt.session, -1):
                    self.stats.rejected += 1
                    return None
                self.session = pkt.session
                self.last_seq = pkt.seq
                self.counters_seeded = False
                self.phone_addr = addr
                self.stats.dropped = 0
                self._neutral_inputs()
                name = P.unpack_hello(pkt.payload) if pkt.type == P.T_HELLO else "phone"
                self.phone_name = name
                self.event(f"Phone connected: {name} from {addr[0]}")
            else:
                if pkt.seq <= self.last_seq:          # replay / reordered / duplicate
                    self.stats.rejected += 1
                    return None
                gap = pkt.seq - self.last_seq - 1
                if gap > 0:
                    self.stats.dropped += gap
                self.last_seq = pkt.seq
                self.phone_addr = addr
            self._seq_memory[pkt.session] = pkt.seq
            if len(self._seq_memory) > 64:
                self._seq_memory.pop(next(iter(self._seq_memory)))

            self.last_rx = now
            self.stats.rx_packets += 1
            self.stats._rx_window.append(now)

            if pkt.type == P.T_BYE:
                self._end_session("phone closed the connection")
                return None
            if pkt.type == P.T_HELLO:
                return self._ack(0, now, force=True)
            if pkt.type != P.T_STATE:
                return None
            st = P.unpack_state(pkt.payload)
            if st is None:
                self.stats.rejected += 1
                return None

            self.steer = st.steer
            self.throttle = st.throttle
            self.brake = st.brake
            self.buttons = st.buttons
            self.flags = st.flags
            self.raw_angle = st.raw_angle_deg
            self.stats.sensor_hz = st.sensor_hz
            self.stats.phone_rtt_ms = st.rtt_ms

            if not self.counters_seeded:
                self.last_gu, self.last_gd = st.gear_up, st.gear_down
                self.counters_seeded = True
            else:
                self._shift(self.gear_up, st.gear_up, "last_gu")
                self._shift(self.gear_down, st.gear_down, "last_gd")
            return self._ack(st.phone_ms, now)

    def _shift(self, ch: GearChannel, value: int, attr: str) -> None:
        delta = (value - getattr(self, attr)) & 0xFFFF
        setattr(self, attr, value)
        if delta == 0 or delta > 0x8000:   # no change, or counter went backwards
            return
        ch.pending = min(ch.pending + delta, self.cfg.max_queued_shifts)

    def _ack(self, echo: int, now: float, force: bool = False) -> bytes | None:
        if not force and now - self.last_ack < 0.05:
            return None
        self.last_ack = now
        payload = P.pack_ack(echo, self.backend.connected, int(self.stats.rx_hz), self.stats.dropped)
        return P.encode(self.key, P.T_ACK, self.session or 0, int(now * 1000), payload)

    # ------------------------------------------------------------ output
    def compute_output(self, now: float):
        """Decide what the virtual controller should show right now."""
        with self.lock:
            if self.test_state is not None and now < self.test_until:
                return self.test_state
            self.test_state = None

            fresh = (now - self.last_rx) * 1000 <= self.cfg.timeout_ms
            live = self.session is not None and fresh and (self.flags & P.F_ACTIVE)
            if not live:
                if not self.timed_out and self.session is not None:
                    if fresh:
                        self.event("Phone left the driving screen - outputs neutral")
                    else:
                        self.stats.interruptions += 1
                        self.event("Input timeout - outputs set to neutral")
                self.timed_out = True
                self.gear_up.clear()
                self.gear_down.clear()
                return (0.0, 0.0, 0.0, frozenset())
            if self.timed_out:
                self.timed_out = False
                self.event("Phone input live")

            m = self.cfg.mapping
            press_s = self.cfg.gear_press_ms / 1000
            gap_s = self.cfg.gear_gap_ms / 1000
            btns: set[str] = set()
            if self.gear_up.tick(now, press_s, gap_s):
                btns.add(m["gear_up"])
            if self.gear_down.tick(now, press_s, gap_s):
                btns.add(m["gear_down"])
            for name, bit in P.BUTTON_BIT.items():
                if self.buttons & bit:
                    btns.add(P.PAD_XBOX.get(name) or m.get(name, "NONE"))
            btns.discard("NONE")
            steer = -self.steer if self.cfg.invert_steering else self.steer
            return (steer, self.throttle, self.brake, frozenset(btns))

    def tick(self, now: float) -> None:
        out = self.compute_output(now)
        try:
            self.backend.set_state(out[0], out[1], out[2], set(out[3]))
        except Exception as e:
            with self.lock:
                self.event(f"Controller write failed: {e}")
                self.backend = NullBackend()
                self.backend_error = str(e)
        with self.lock:
            self.out = out
            self.stats._out_window.append(now)
            for w, attr in ((self.stats._rx_window, "rx_hz"), (self.stats._out_window, "out_hz")):
                while w and now - w[0] > 1.0:
                    w.popleft()
                setattr(self.stats, attr, float(len(w)))

    def run_test(self, steer=0.0, throttle=0.0, brake=0.0, buttons=(), seconds=0.5) -> None:
        with self.lock:
            self.test_state = (steer, throttle, brake, frozenset(buttons))
            self.test_until = self.clock() + seconds

    # ------------------------------------------------------------ threads
    @property
    def running(self) -> bool:
        return self._running.is_set()

    def start(self) -> None:
        if self.running:
            return
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.bind(("0.0.0.0", self.cfg.port))
        sock.settimeout(0.25)
        if hasattr(socket, "SIO_UDP_CONNRESET"):
            # Windows: stop ICMP "port unreachable" replies from raising on recv.
            try:
                sock.ioctl(socket.SIO_UDP_CONNRESET, False)
            except OSError:
                pass
        self._sock = sock
        self._stopping.clear()
        self._running.set()
        self._threads = [
            threading.Thread(target=self._net_loop, name="net", daemon=True),
            threading.Thread(target=self._out_loop, name="out", daemon=True),
            threading.Thread(target=self._beacon_loop, name="beacon", daemon=True),
        ]
        for t in self._threads:
            t.start()
        self.event(f"Server listening on UDP port {self.cfg.port}")

    def stop(self) -> None:
        if not self.running:
            return
        self._running.clear()
        self._stopping.set()
        for t in self._threads:
            t.join(timeout=1.0)
        if self._sock:
            self._sock.close()
            self._sock = None
        with self.lock:
            self._end_session("server stopped")
            try:
                self.backend.neutral()
            except Exception:
                pass
        self.event("Server stopped")

    def shutdown(self) -> None:
        self.stop()
        with self.lock:
            self.backend.close()

    def _net_loop(self) -> None:
        sock = self._sock
        while self._running.is_set():
            try:
                data, addr = sock.recvfrom(1024)
            except socket.timeout:
                continue
            except OSError:
                # Windows reports ICMP "port unreachable" as ConnectionResetError on UDP.
                continue
            reply = self.handle_datagram(data, addr, self.clock())
            if reply:
                try:
                    sock.sendto(reply, addr)
                except OSError:
                    pass

    def _out_loop(self) -> None:
        period = 1.0 / OUTPUT_HZ
        nxt = self.clock()
        while self._running.is_set():
            self.tick(self.clock())
            nxt += period
            delay = nxt - self.clock()
            if delay > 0:
                time.sleep(delay)
            else:
                nxt = self.clock()

    def _beacon_loop(self) -> None:
        name = socket.gethostname()
        while self._running.is_set():
            if self.cfg.beacon:
                msg = P.pack_beacon(self.cfg.port, name)
                for ip in local_ipv4s():
                    try:
                        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                            s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
                            s.bind((ip, 0))
                            s.sendto(msg, ("255.255.255.255", P.BEACON_PORT))
                    except OSError:
                        pass
            self._stopping.wait(1.0)   # returns early when stop() is called

    # ------------------------------------------------------------ UI helpers
    def snapshot(self) -> dict:
        now = self.clock()
        with self.lock:
            connected = self.session is not None and (now - self.last_rx) * 1000 <= self.cfg.takeover_ms
            return {
                "running": self.running,
                "connected": connected,
                "live": connected and not self.timed_out,
                "phone_name": self.phone_name,
                "phone_addr": self.phone_addr[0] if self.phone_addr else "",
                "steer_in": self.steer,
                "raw_angle": self.raw_angle,
                "out": self.out,
                "gear_up_total": self.gear_up.total,
                "gear_down_total": self.gear_down.total,
                "buttons": self.buttons,
                "flags": self.flags,
                "rx_hz": self.stats.rx_hz,
                "out_hz": self.stats.out_hz,
                "sensor_hz": self.stats.sensor_hz,
                "rtt": self.stats.phone_rtt_ms,
                "dropped": self.stats.dropped,
                "rejected": self.stats.rejected,
                "interruptions": self.stats.interruptions,
                "backend": self.backend.name if self.backend.connected else "none",
                "backend_ok": self.backend.connected,
                "backend_error": self.backend_error,
            }


_ip_cache: tuple[float, list[str]] = (0.0, [])


def local_ipv4s() -> list[str]:
    """Local IPv4 addresses, most likely LAN address first (cached for 2 s)."""
    global _ip_cache
    now = time.monotonic()
    if now - _ip_cache[0] < 2.0:
        return _ip_cache[1]
    ips = _resolve_local_ipv4s()
    _ip_cache = (now, ips)
    return ips


def _resolve_local_ipv4s() -> list[str]:
    ips: list[str] = []
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if not ip.startswith("127.") and ip not in ips:
                ips.append(ip)
    except OSError:
        pass
    # The interface that would route to the internet (no packet is sent).
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("192.0.2.1", 9))
            ip = s.getsockname()[0]
            if ip in ips:
                ips.remove(ip)
            ips.insert(0, ip)
    except OSError:
        pass
    return ips
