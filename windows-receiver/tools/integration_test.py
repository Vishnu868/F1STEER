"""End-to-end test on one PC: simulated phone --UDP--> receiver --> ViGEm --> XInput.

    python tools\\integration_test.py

Checks over real sockets and the real driver that steering/throttle/brake
arrive as analog values, that exactly 3 gear-up and 3 gear-down presses reach
the controller, and that outputs return to neutral after the phone leaves.
"""
from __future__ import annotations

import ctypes
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "tools"))
os.environ["APPDATA"] = tempfile.mkdtemp()

from f1wheel.config import Config  # noqa: E402
from f1wheel.engine import Receiver  # noqa: E402
from controller_poc import XINPUT_STATE, read_all  # noqa: E402

PORT = 47899


def main() -> int:
    cfg = Config(pin="424242", port=PORT, beacon=False)
    before = set(read_all())
    rx = Receiver(cfg)
    rx.open_backend()
    if not rx.backend.connected:
        print("FAIL: no virtual controller:", rx.backend_error)
        return 1
    rx.start()
    time.sleep(0.4)
    slot = sorted(set(read_all()) - before)[0]
    xinput = ctypes.WinDLL("xinput1_4")

    sim = subprocess.Popen([sys.executable, str(ROOT / "tools" / "simulate_phone.py"),
                            "--pin", "424242", "--port", str(PORT), "--seconds", "8",
                            "--loss", "0.05"], stdout=subprocess.PIPE, text=True)
    lx_min = lx_max = 0
    rt_values, lt_values = set(), set()
    a_presses = x_presses = 0
    prev_a = prev_x = False
    st = XINPUT_STATE()
    t_end = time.monotonic() + 9.0
    while time.monotonic() < t_end:
        if xinput.XInputGetState(slot, ctypes.byref(st)) == 0:
            g = st.Gamepad
            lx_min, lx_max = min(lx_min, g.sThumbLX), max(lx_max, g.sThumbLX)
            rt_values.add(g.bRightTrigger)
            lt_values.add(g.bLeftTrigger)
            a, x = bool(g.wButtons & 0x1000), bool(g.wButtons & 0x4000)
            a_presses += a and not prev_a
            x_presses += x and not prev_x
            prev_a, prev_x = a, x
        time.sleep(0.002)
    out, _ = sim.communicate(timeout=5)
    time.sleep(0.3)
    xinput.XInputGetState(slot, ctypes.byref(st))
    g = st.Gamepad
    snap = rx.snapshot()
    rx.shutdown()

    print(out.strip())
    checks = [
        ("steering reached full left", lx_min < -32000),
        ("steering reached full right", lx_max > 32000),
        ("throttle reached 100%", 255 in rt_values),
        ("brake analog 80%", any(200 <= v <= 208 for v in lt_values)),
        ("exactly 3 gear-up presses (5% packet loss)", a_presses == 3),
        ("exactly 3 gear-down presses (5% packet loss)", x_presses == 3),
        ("neutral after phone disconnect", g.sThumbLX == 0 and g.bRightTrigger == 0 and g.bLeftTrigger == 0),
        ("receiver counted dropped packets", snap["dropped"] > 0),
    ]
    for name, ok in checks:
        print(f"  {'PASS' if ok else 'FAIL'}  {name}")
    print(f"  info: gear presses seen A={a_presses} X={x_presses}, dropped={snap['dropped']}, "
          f"controller rate={snap['out_hz']:.0f} Hz")
    return 0 if all(ok for _, ok in checks) else 1


if __name__ == "__main__":
    raise SystemExit(main())
