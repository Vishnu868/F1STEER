"""Phase 2 proof of concept: create the virtual Xbox controller and read it
back through Windows' own XInput API to prove that the OS (and therefore games)
sees analog steering, analog throttle/brake and the gear buttons.

Run:  python tools\\controller_poc.py
"""
from __future__ import annotations

import ctypes
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from f1wheel.backends import ViGEmX360Backend  # noqa: E402


class XINPUT_GAMEPAD(ctypes.Structure):
    _fields_ = [("wButtons", ctypes.c_ushort), ("bLeftTrigger", ctypes.c_ubyte),
                ("bRightTrigger", ctypes.c_ubyte), ("sThumbLX", ctypes.c_short),
                ("sThumbLY", ctypes.c_short), ("sThumbRX", ctypes.c_short), ("sThumbRY", ctypes.c_short)]


class XINPUT_STATE(ctypes.Structure):
    _fields_ = [("dwPacketNumber", ctypes.c_uint), ("Gamepad", XINPUT_GAMEPAD)]


xinput = ctypes.WinDLL("xinput1_4")
BTN = {"A": 0x1000, "B": 0x2000, "X": 0x4000, "Y": 0x8000, "LB": 0x0100, "RB": 0x0200, "START": 0x0010}


def read_all():
    pads = {}
    for i in range(4):
        st = XINPUT_STATE()
        if xinput.XInputGetState(i, ctypes.byref(st)) == 0:
            pads[i] = st.Gamepad
    return pads


def main() -> int:
    before = set(read_all())
    pad = ViGEmX360Backend()
    time.sleep(0.5)
    new = sorted(set(read_all()) - before)
    if not new:
        print("FAIL: virtual controller not visible through XInput")
        return 1
    slot = new[0]
    print(f"Virtual Xbox 360 controller visible to Windows in XInput slot {slot}")

    def check(name, steer, thr, brk, buttons, expect):
        pad.set_state(steer, thr, brk, set(buttons))
        time.sleep(0.08)
        g = read_all()[slot]
        ok = expect(g)
        print(f"  {'PASS' if ok else 'FAIL'}  {name:<28} LX={g.sThumbLX:+6d} LT={g.bLeftTrigger:3d} "
              f"RT={g.bRightTrigger:3d} buttons=0x{g.wButtons:04x}")
        return ok

    results = [
        check("steering full left", -1, 0, 0, [], lambda g: g.sThumbLX <= -32700),
        check("steering 25% right (analog)", 0.25, 0, 0, [], lambda g: 8000 < g.sThumbLX < 8400),
        check("steering full right", 1, 0, 0, [], lambda g: g.sThumbLX >= 32700),
        check("throttle 50% (analog)", 0, 0.5, 0, [], lambda g: 125 <= g.bRightTrigger <= 130),
        check("throttle 100%", 0, 1, 0, [], lambda g: g.bRightTrigger == 255),
        check("brake 30% (analog)", 0, 0, 0.3, [], lambda g: 74 <= g.bLeftTrigger <= 79),
        check("gear up (A)", 0, 0, 0, ["A"], lambda g: g.wButtons == BTN["A"]),
        check("gear down (X)", 0, 0, 0, ["X"], lambda g: g.wButtons == BTN["X"]),
        check("neutral", 0, 0, 0, [], lambda g: g.wButtons == 0 and g.sThumbLX == 0 and g.bRightTrigger == 0),
    ]
    pad.close()
    print(f"\n{sum(results)}/{len(results)} checks passed")
    return 0 if all(results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
