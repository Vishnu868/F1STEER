"""Persistent receiver configuration (stored as JSON in %APPDATA%\\F1Wheel)."""
from __future__ import annotations

import json
import os
import secrets
from dataclasses import asdict, dataclass, field
from pathlib import Path

from .keys import KEY_NAMES
from .protocol import DEFAULT_PORT

# Xbox 360 button names understood by the backends.
XBOX_BUTTONS = [
    "A", "B", "X", "Y", "LB", "RB", "BACK", "START",
    "LEFT_THUMB", "RIGHT_THUMB", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT",
    "NONE",
]

# Phone function -> Xbox button. F1 25 lets you re-bind every action in
# Settings > Controls, so these only need to be unique and sensible.
DEFAULT_MAPPING = {
    "gear_up": "A",
    "gear_down": "X",
    "drs": "Y",
    "ers": "RB",
    "clutch": "LB",
    "pause": "START",
    "look_back": "RIGHT_THUMB",
    "flashback": "BACK",
    "pit_limiter": "B",
    "mfd": "DPAD_UP",
}

# Truck layout (ETS2 / ATS): phone function -> keyboard key. These are the keys
# the games use out of the box as far as we know; check them in the game under
# Options > Controls and change them here if your game uses different ones.
DEFAULT_TRUCK_KEYS = {
    "ind_left": "[",
    "ind_right": "]",
    "hazard": "F",
    "lights": "L",
    "high_beam": "K",
    "horn": "H",
    "park_brake": "SPACE",
    "engine": "E",
    "cruise": "C",
    "wipers": "P",
    "trailer": "T",
    "activate": "ENTER",
    "map": "M",
    "menu": "ESC",
}


def config_dir() -> Path:
    base = os.environ.get("APPDATA") or str(Path.home())
    p = Path(base) / "F1Wheel"
    p.mkdir(parents=True, exist_ok=True)
    return p


def new_pin() -> str:
    return f"{secrets.randbelow(1_000_000):06d}"


@dataclass
class Config:
    port: int = DEFAULT_PORT
    pin: str = field(default_factory=new_pin)
    backend: str = "vigem"            # "vigem" | "vjoy" | "none"
    timeout_ms: int = 150             # no packets for this long -> neutral outputs
    takeover_ms: int = 1000           # a new phone may take over after this much silence
    gear_press_ms: int = 60           # how long a gear-shift button is held
    gear_gap_ms: int = 60             # release time between queued shifts
    max_queued_shifts: int = 4
    invert_steering: bool = False     # extra inversion on the PC side
    beacon: bool = True               # broadcast so the phone can find the laptop
    mapping: dict = field(default_factory=lambda: dict(DEFAULT_MAPPING))
    truck_keys: dict = field(default_factory=lambda: dict(DEFAULT_TRUCK_KEYS))
    truck_keys_only_in_game: bool = True   # send truck keys only while ETS2 / ATS is in front

    @classmethod
    def load(cls) -> "Config":
        path = config_dir() / "config.json"
        cfg = cls()
        if path.exists():
            try:
                data = json.loads(path.read_text(encoding="utf-8"))
                for k, v in data.items():
                    if k == "mapping" and isinstance(v, dict):
                        m = dict(DEFAULT_MAPPING)
                        m.update({a: b for a, b in v.items() if a in DEFAULT_MAPPING and b in XBOX_BUTTONS})
                        cfg.mapping = m
                    elif k == "truck_keys" and isinstance(v, dict):
                        t = dict(DEFAULT_TRUCK_KEYS)
                        t.update({a: b for a, b in v.items() if a in DEFAULT_TRUCK_KEYS and b in KEY_NAMES})
                        cfg.truck_keys = t
                    elif hasattr(cfg, k):
                        cur = getattr(cfg, k)
                        # Only accept a value of the same type (bool is also an int in Python).
                        if type(v) is type(cur):
                            setattr(cfg, k, v)
            except (ValueError, TypeError, OSError):
                pass  # fall back to defaults on a corrupt file
        if not (cfg.pin.isdigit() and len(cfg.pin) == 6):
            cfg.pin = new_pin()
        cfg.save()
        return cfg

    def save(self) -> None:
        path = config_dir() / "config.json"
        tmp = path.with_suffix(".tmp")
        tmp.write_text(json.dumps(asdict(self), indent=2), encoding="utf-8")
        tmp.replace(path)
