"""Pretend to be the Android app, for testing the receiver without a phone.

    python tools\\simulate_phone.py --ip 127.0.0.1 --pin 123456 --seconds 10

Sweeps steering, pulses throttle/brake, shifts up 3x and down 3x, then sends
BYE. Prints the RTT measured from the receiver's ACKs.
"""
from __future__ import annotations

import argparse
import math
import random
import socket
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from f1wheel import protocol as P  # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ip", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=P.DEFAULT_PORT)
    ap.add_argument("--pin", required=True)
    ap.add_argument("--seconds", type=float, default=10)
    ap.add_argument("--hz", type=int, default=200)
    ap.add_argument("--loss", type=float, default=0.0, help="simulated packet loss 0..1")
    a = ap.parse_args()

    key = P.derive_key(a.pin)
    session = random.getrandbits(32)
    seq = 0
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setblocking(False)
    dst = (a.ip, a.port)
    t0 = time.perf_counter()
    rtts = []
    acks = 0

    def send(ptype, payload=b""):
        nonlocal seq
        seq += 1
        if random.random() >= a.loss or ptype != P.T_STATE:
            sock.sendto(P.encode(key, ptype, session, seq, payload), dst)

    send(P.T_HELLO, P.pack_hello("simulator"))
    gu = gd = 0
    shifts_done = 0
    period = 1 / a.hz
    nxt = time.perf_counter()
    while (now := time.perf_counter()) - t0 < a.seconds:
        t = now - t0
        steer = math.sin(t * 1.5)
        thr = 1.0 if (t % 4) < 2 else 0.0
        brk = 0.0 if thr else 0.8
        # 3 up-shifts then 3 down-shifts, one per second from t=1s
        if shifts_done < 6 and t >= 1 + shifts_done:
            if shifts_done < 3:
                gu = (gu + 1) & 0xFFFF
            else:
                gd = (gd + 1) & 0xFFFF
            shifts_done += 1
        phone_ms = int((now - t0) * 1000)
        rtt = sum(rtts[-20:]) / len(rtts[-20:]) if rtts else None
        send(P.T_STATE, P.pack_state(P.StateMsg(phone_ms, steer, thr, brk, 0, gu, gd,
                                                steer * 90, 400, rtt, P.F_ACTIVE | P.F_SENSOR_OK)))
        try:
            while True:
                data, _ = sock.recvfrom(256)
                pkt = P.decode(key, data)
                if pkt and pkt.type == P.T_ACK:
                    ack = P.unpack_ack(pkt.payload)
                    acks += 1
                    if ack and ack[0]:
                        rtts.append(int((time.perf_counter() - t0) * 1000) - ack[0])
        except (BlockingIOError, ConnectionResetError):
            pass
        nxt += period
        time.sleep(max(0, nxt - time.perf_counter()))
    send(P.T_BYE)
    print(f"sent {seq} packets, got {acks} ACKs, "
          f"RTT avg {sum(rtts)/len(rtts):.1f} ms" if rtts else f"sent {seq} packets, no ACKs (wrong PIN/IP?)")
    print(f"gear up x{gu}, gear down x{gd}")
    return 0 if acks else 1


if __name__ == "__main__":
    raise SystemExit(main())
