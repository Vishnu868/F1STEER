"""Record what games see from the virtual Xbox controller, for hardware tests.

    python tools\\xinput_recorder.py out.csv          # records until Ctrl+C / killed
    python tools\\xinput_recorder.py --summary out.csv

Samples every XInput slot at ~200 Hz and writes time, steering (-1..1),
throttle, brake (0..1) and button bits to CSV. --summary prints ranges,
centre-return accuracy and rising-edge counts for each button.
"""
from __future__ import annotations

import csv
import ctypes
import sys
import time

BUTTONS = {"A": 0x1000, "B": 0x2000, "X": 0x4000, "Y": 0x8000, "LB": 0x0100, "RB": 0x0200,
           "START": 0x0010, "BACK": 0x0020, "RIGHT_THUMB": 0x0080, "DPAD_UP": 0x0001}


class GAMEPAD(ctypes.Structure):
    _fields_ = [("wButtons", ctypes.c_ushort), ("bLeftTrigger", ctypes.c_ubyte),
                ("bRightTrigger", ctypes.c_ubyte), ("sThumbLX", ctypes.c_short),
                ("sThumbLY", ctypes.c_short), ("sThumbRX", ctypes.c_short), ("sThumbRY", ctypes.c_short)]


class STATE(ctypes.Structure):
    _fields_ = [("dwPacketNumber", ctypes.c_uint), ("Gamepad", GAMEPAD)]


def record(path: str) -> None:
    xi = ctypes.WinDLL("xinput1_4")
    st = STATE()
    t0 = time.perf_counter()
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["t", "slot", "steer", "throttle", "brake", "buttons"])
        n = 0
        while True:
            for slot in range(4):
                if xi.XInputGetState(slot, ctypes.byref(st)) == 0:
                    g = st.Gamepad
                    w.writerow([f"{time.perf_counter() - t0:.4f}", slot, f"{g.sThumbLX / 32767:.4f}",
                                f"{g.bRightTrigger / 255:.3f}", f"{g.bLeftTrigger / 255:.3f}", g.wButtons])
            n += 1
            if n % 200 == 0:
                f.flush()
            time.sleep(0.005)


def summary(path: str) -> None:
    rows = list(csv.DictReader(open(path)))
    if not rows:
        print("no samples (no controller visible?)")
        return
    slot = max(set(r["slot"] for r in rows), key=lambda s: sum(float(r["steer"]) != 0 for r in rows if r["slot"] == s))
    rows = [r for r in rows if r["slot"] == slot]
    t = [float(r["t"]) for r in rows]
    steer = [float(r["steer"]) for r in rows]
    thr = [float(r["throttle"]) for r in rows]
    brk = [float(r["brake"]) for r in rows]
    btn = [int(r["buttons"]) for r in rows]
    print(f"slot {slot}: {len(rows)} samples over {t[-1] - t[0]:.1f} s")
    print(f"steering  min {min(steer):+.3f}  max {max(steer):+.3f}  distinct values {len(set(steer))}")
    print(f"throttle  max {max(thr):.3f}  time held {sum(1 for v in thr if v > 0) * (t[-1]-t[0]) / len(t):.1f} s")
    print(f"brake     max {max(brk):.3f}  time held {sum(1 for v in brk if v > 0) * (t[-1]-t[0]) / len(t):.1f} s")
    for name, bit in BUTTONS.items():
        edges, prev = 0, False
        for b in btn:
            on = bool(b & bit)
            edges += on and not prev
            prev = on
        if edges:
            print(f"button {name:<11} pressed {edges}x")
    # centre returns: moments the steering rested (<2 % change over 0.5 s) after a big turn
    rests, i = [], 0
    while i < len(steer):
        j = i
        while j < len(steer) and t[j] - t[i] < 0.5:
            j += 1
        if j < len(steer) and max(steer[i:j]) - min(steer[i:j]) < 0.02 and abs(steer[i]) < 0.15:
            rests.append(steer[i])
            while j < len(steer) and abs(steer[j] - steer[i]) < 0.02:
                j += 1
        i = max(j, i + 1)
    if rests:
        print("centre rests: " + "  ".join(f"{v * 100:+.1f}%" for v in rests))


if __name__ == "__main__":
    if sys.argv[1] == "--summary":
        summary(sys.argv[2])
    else:
        try:
            record(sys.argv[1])
        except KeyboardInterrupt:
            pass
