"""Keyboard output for the phone's Truck layout (ETS2 / ATS).

Truck games have far more functions than an Xbox pad has buttons (indicators,
lights, horn, parking brake, engine, ...). The games already have keyboard keys
for all of them, so the receiver presses those keys instead.

Implementation: the standard Windows SendInput API (user32.dll) with hardware
scan codes, through ctypes. No driver, no extra package, no hook.

Safety: by default keys are only sent while the window in front belongs to one
of the truck games (eurotrucks2.exe / amtrucks.exe). Typing into any other
program is refused, and every held key is released as soon as the game loses
focus or the phone stops sending.
"""
from __future__ import annotations

import ctypes
import os
import sys

# Process names of the games the keys may be sent to.
TRUCK_GAMES = ("eurotrucks2.exe", "amtrucks.exe")

# Key name -> (scan code, extended flag). US keyboard positions; the game reads
# scan codes, so this is the physical key whatever the Windows layout is.
_SCAN = {
    **{c: (code, False) for c, code in zip("1234567890", range(0x02, 0x0C))},
    **{c: (code, False) for c, code in zip("QWERTYUIOP", range(0x10, 0x1A))},
    **{c: (code, False) for c, code in zip("ASDFGHJKL", range(0x1E, 0x27))},
    **{c: (code, False) for c, code in zip("ZXCVBNM", range(0x2C, 0x33))},
    "[": (0x1A, False), "]": (0x1B, False), ";": (0x27, False), "'": (0x28, False),
    ",": (0x33, False), ".": (0x34, False), "/": (0x35, False), "-": (0x0C, False),
    "=": (0x0D, False), "`": (0x29, False), "\\": (0x2B, False),
    "SPACE": (0x39, False), "ENTER": (0x1C, False), "ESC": (0x01, False),
    "TAB": (0x0F, False), "BACKSPACE": (0x0E, False),
    "LSHIFT": (0x2A, False), "LCTRL": (0x1D, False), "LALT": (0x38, False),
    **{f"F{i}": (0x3A + i, False) for i in range(1, 11)},
    "F11": (0x57, False), "F12": (0x58, False),
    "UP": (0x48, True), "DOWN": (0x50, True), "LEFT": (0x4B, True), "RIGHT": (0x4D, True),
    "INSERT": (0x52, True), "DELETE": (0x53, True), "HOME": (0x47, True), "END": (0x4F, True),
    "PAGE_UP": (0x49, True), "PAGE_DOWN": (0x51, True),
    **{f"NUM{i}": (code, False) for i, code in
       zip(range(10), (0x52, 0x4F, 0x50, 0x51, 0x4B, 0x4C, 0x4D, 0x47, 0x48, 0x49))},
    "NUM_ENTER": (0x1C, True), "NUM+": (0x4E, False), "NUM-": (0x4A, False),
}
KEY_NAMES = list(_SCAN) + ["NONE"]


class KeySender:
    """Keeps the set of pressed keys equal to what the phone holds."""

    def __init__(self):
        self.held: set[str] = set()
        self.available = sys.platform == "win32"
        self.blocked_reason = ""          # shown on the dashboard
        self._fg_cache: tuple[int, str] = (0, "")
        if self.available:
            self._init_win32()

    # ------------------------------------------------------------ win32
    def _init_win32(self) -> None:
        from ctypes import wintypes

        class KEYBDINPUT(ctypes.Structure):
            _fields_ = [("wVk", wintypes.WORD), ("wScan", wintypes.WORD), ("dwFlags", wintypes.DWORD),
                        ("time", wintypes.DWORD), ("dwExtraInfo", ctypes.c_size_t)]

        class MOUSEINPUT(ctypes.Structure):   # only here so the union has the right size
            _fields_ = [("dx", wintypes.LONG), ("dy", wintypes.LONG), ("mouseData", wintypes.DWORD),
                        ("dwFlags", wintypes.DWORD), ("time", wintypes.DWORD), ("dwExtraInfo", ctypes.c_size_t)]

        class _U(ctypes.Union):
            _fields_ = [("ki", KEYBDINPUT), ("mi", MOUSEINPUT)]

        class INPUT(ctypes.Structure):
            _fields_ = [("type", wintypes.DWORD), ("u", _U)]

        self._INPUT, self._KEYBDINPUT, self._U = INPUT, KEYBDINPUT, _U
        self._user32 = ctypes.WinDLL("user32", use_last_error=True)
        self._kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        self._user32.SendInput.argtypes = (wintypes.UINT, ctypes.c_void_p, ctypes.c_int)
        self._user32.GetForegroundWindow.restype = wintypes.HWND
        self._user32.GetWindowThreadProcessId.argtypes = (wintypes.HWND, ctypes.POINTER(wintypes.DWORD))
        self._kernel32.OpenProcess.restype = wintypes.HANDLE
        self._kernel32.QueryFullProcessImageNameW.argtypes = (
            wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD))
        self._kernel32.CloseHandle.argtypes = (wintypes.HANDLE,)

    def _send(self, key: str, down: bool) -> None:
        scan, ext = _SCAN[key]
        flags = 0x0008 | (0x0001 if ext else 0) | (0 if down else 0x0002)  # SCANCODE | EXTENDED | KEYUP
        inp = self._INPUT(type=1, u=self._U(ki=self._KEYBDINPUT(0, scan, flags, 0, 0)))
        self._user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(inp))

    def foreground_exe(self) -> str:
        """Lower-case file name of the program whose window is in front ('' if unknown)."""
        if not self.available:
            return ""
        from ctypes import wintypes
        hwnd = self._user32.GetForegroundWindow()
        if not hwnd:
            return ""
        if self._fg_cache[0] == hwnd:
            return self._fg_cache[1]
        pid = wintypes.DWORD()
        self._user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        name = ""
        h = self._kernel32.OpenProcess(0x1000, False, pid.value)   # PROCESS_QUERY_LIMITED_INFORMATION
        if h:
            try:
                buf = ctypes.create_unicode_buffer(520)
                size = wintypes.DWORD(len(buf))
                if self._kernel32.QueryFullProcessImageNameW(h, 0, buf, ctypes.byref(size)):
                    name = os.path.basename(buf.value).lower()
            finally:
                self._kernel32.CloseHandle(h)
        self._fg_cache = (hwnd, name)
        return name

    # ------------------------------------------------------------ public
    def allowed(self, only_in_game: bool) -> bool:
        if not self.available:
            self.blocked_reason = "keyboard output needs Windows"
            return False
        if only_in_game:
            exe = self.foreground_exe()
            if exe not in TRUCK_GAMES:
                self.blocked_reason = "game window not in front"
                return False
        self.blocked_reason = ""
        return True

    def apply(self, wanted: set[str], only_in_game: bool = True) -> None:
        """Press keys that are newly wanted, release keys that are no longer wanted."""
        wanted = {k for k in wanted if k in _SCAN}
        if wanted and not self.allowed(only_in_game):
            wanted = set()
        if wanted == self.held:
            return
        for k in self.held - wanted:
            self._release(k)
        for k in wanted - self.held:
            self._press(k)

    def release_all(self) -> None:
        for k in list(self.held):
            self._release(k)

    def _press(self, key: str) -> None:
        if self.available:
            self._send(key, True)
        self.held.add(key)

    def _release(self, key: str) -> None:
        if self.available:
            self._send(key, False)
        self.held.discard(key)
