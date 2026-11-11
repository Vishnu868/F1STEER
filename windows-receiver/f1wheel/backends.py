"""Virtual controller outputs.

ViGEmX360Backend (default)
    Creates an Xbox 360 controller through the ViGEmBus driver (via the
    `vgamepad` package). F1 25 supports Xbox controllers out of the box:
    steering = left stick X, throttle = RT, brake = LT (all analog).

VJoyBackend (fallback, experimental)
    Drives a vJoy DirectInput joystick (BrunnerInnovation signed build 2.2.2.0)
    through vJoyInterface.dll. F1 25 does not officially list vJoy, so this
    path needs manual mapping in game and is not verified.

NullBackend
    Does nothing; lets the dashboard run when no driver is installed.
"""
from __future__ import annotations

import ctypes
import os
from pathlib import Path

from .config import XBOX_BUTTONS


class BackendError(RuntimeError):
    pass


class Backend:
    name = "none"
    connected = False

    def set_state(self, steer: float, throttle: float, brake: float, buttons: set[str]) -> None:
        raise NotImplementedError

    def neutral(self) -> None:
        self.set_state(0.0, 0.0, 0.0, set())

    def close(self) -> None:
        pass


class NullBackend(Backend):
    name = "none"
    connected = False

    def set_state(self, steer, throttle, brake, buttons):
        pass


class ViGEmX360Backend(Backend):
    name = "vigem"

    def __init__(self) -> None:
        try:
            import vgamepad as vg  # imported lazily: it loads ViGEmClient.dll
        except Exception as e:  # pragma: no cover - depends on host
            raise BackendError(f"vgamepad is not available: {e}") from e
        try:
            self._pad = vg.VX360Gamepad()
        except Exception as e:
            raise BackendError(
                "Could not create the virtual Xbox controller - the ViGEmBus driver is probably "
                "not installed. Install ViGEmBus 1.22.0 from "
                "https://github.com/nefarius/ViGEmBus/releases, then click Reconnect controller. "
                f"({e})") from e
        B = vg.XUSB_BUTTON
        self._map = {
            "A": B.XUSB_GAMEPAD_A, "B": B.XUSB_GAMEPAD_B, "X": B.XUSB_GAMEPAD_X,
            "Y": B.XUSB_GAMEPAD_Y, "LB": B.XUSB_GAMEPAD_LEFT_SHOULDER,
            "RB": B.XUSB_GAMEPAD_RIGHT_SHOULDER, "BACK": B.XUSB_GAMEPAD_BACK,
            "START": B.XUSB_GAMEPAD_START, "LEFT_THUMB": B.XUSB_GAMEPAD_LEFT_THUMB,
            "RIGHT_THUMB": B.XUSB_GAMEPAD_RIGHT_THUMB, "DPAD_UP": B.XUSB_GAMEPAD_DPAD_UP,
            "DPAD_DOWN": B.XUSB_GAMEPAD_DPAD_DOWN, "DPAD_LEFT": B.XUSB_GAMEPAD_DPAD_LEFT,
            "DPAD_RIGHT": B.XUSB_GAMEPAD_DPAD_RIGHT,
        }
        self._held: set[str] = set()
        self.connected = True
        self.neutral()

    def set_state(self, steer, throttle, brake, buttons):
        x = int(round(max(-1.0, min(1.0, steer)) * 32767))
        self._pad.left_joystick(x_value=x, y_value=0)
        self._pad.right_trigger(value=int(round(max(0.0, min(1.0, throttle)) * 255)))
        self._pad.left_trigger(value=int(round(max(0.0, min(1.0, brake)) * 255)))
        for b in self._held - buttons:
            if b in self._map:
                self._pad.release_button(button=self._map[b])
        for b in buttons - self._held:
            if b in self._map:
                self._pad.press_button(button=self._map[b])
        self._held = set(buttons)
        self._pad.update()

    def close(self):
        try:
            self._pad.reset()
            self._pad.update()
        except Exception:
            pass
        try:
            del self._pad
        except Exception:
            pass
        self.connected = False


class VJoyBackend(Backend):
    """Device 1 of vJoy: X = steering, Y = throttle, Z = brake, buttons 1..14."""
    name = "vjoy"
    HID_X, HID_Y, HID_Z = 0x30, 0x31, 0x32
    DEVICE = 1

    def __init__(self) -> None:
        dll = None
        candidates = [
            os.environ.get("VJOY_DLL", ""),
            r"C:\Program Files\vJoy\x64\vJoyInterface.dll",
            str(Path(__file__).with_name("vJoyInterface.dll")),
        ]
        for c in candidates:
            if c and Path(c).exists():
                dll = ctypes.WinDLL(c)
                break
        if dll is None:
            raise BackendError("vJoyInterface.dll not found. Install vJoy 2.2.2.0 (signed build).")
        self._d = dll
        if not dll.vJoyEnabled():
            raise BackendError("vJoy is installed but not enabled.")
        status = dll.GetVJDStatus(self.DEVICE)
        # 0 = owned by us, 1 = free
        if status not in (0, 1) or not dll.AcquireVJD(self.DEVICE):
            raise BackendError("vJoy device 1 is missing or used by another program.")
        dll.ResetVJD(self.DEVICE)
        self._index = {name: i + 1 for i, name in enumerate(XBOX_BUTTONS) if name != "NONE"}
        self.connected = True
        self.neutral()

    def set_state(self, steer, throttle, brake, buttons):
        d, dev = self._d, self.DEVICE
        def axis(v01: float) -> int:  # vJoy axis range is 1..32768
            return int(1 + max(0.0, min(1.0, v01)) * 32767)
        d.SetAxis(axis((steer + 1) / 2), dev, self.HID_X)
        d.SetAxis(axis(throttle), dev, self.HID_Y)
        d.SetAxis(axis(brake), dev, self.HID_Z)
        for name, idx in self._index.items():
            d.SetBtn(1 if name in buttons else 0, dev, idx)

    def close(self):
        try:
            self._d.ResetVJD(self.DEVICE)
            self._d.RelinquishVJD(self.DEVICE)
        except Exception:
            pass
        self.connected = False


def create_backend(kind: str) -> Backend:
    if kind == "vigem":
        return ViGEmX360Backend()
    if kind == "vjoy":
        return VJoyBackend()
    return NullBackend()
