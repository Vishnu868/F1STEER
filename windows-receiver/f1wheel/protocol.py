"""DIY F1 Wheel wire protocol (version 1).

All integers are little-endian. Every phone<->PC packet (except the
discovery beacon) is authenticated with a truncated HMAC-SHA256 tag whose
key is derived from the pairing PIN shown in the Windows receiver.

    Header (12 bytes)
      0  2s  magic  b"F1"
      2  B   version (1)
      3  B   type
      4  I   session id (random per phone connection)
      8  I   sequence number (strictly increasing within a session)
    Payload (type dependent)
    Tag (8 bytes) = HMAC-SHA256(key, header + payload)[:8]

The Android implementation lives in
android-app/app/src/main/java/com/diyf1wheel/net/Protocol.kt and must be kept
byte-for-byte identical. docs/ARCHITECTURE.md documents the format.
"""
from __future__ import annotations

import hashlib
import hmac
import math
import struct
from dataclasses import dataclass

MAGIC = b"F1"
VERSION = 1
DEFAULT_PORT = 47800
BEACON_PORT = 47801
BEACON_MAGIC = b"F1WB"
TAG_LEN = 8

T_HELLO = 1
T_STATE = 2
T_BYE = 3
T_ACK = 16

HEADER = struct.Struct("<2sBBII")
# phone_ms, steer, throttle, brake, buttons, gear_up, gear_down,
# raw_angle_cdeg, sensor_hz, rtt_tenth_ms, flags
STATE = struct.Struct("<IhHHIHHhHHB")
# echo_phone_ms, controller_ok, rx_hz, dropped
ACK = struct.Struct("<IBHI")

# STATE.flags bits
F_ACTIVE = 0x01      # phone is on the driving screen and sending live input
F_SENSOR_OK = 0x02   # a rotation sensor is delivering samples
F_CALIBRATED = 0x04  # neutral position has been set

# Button bits (held state). Gear shifts are NOT here: they are counters.
# Bits 0-7: racing functions, re-mappable in the receiver settings.
# Bits 8-17: the phone's PAD mode (menu navigation). These are fixed to the Xbox
# button of the same name so the game's own prompts (A = select, B = back) stay true.
# Bits 18-31: the phone's Truck layout (Euro Truck Simulator 2 / American Truck
# Simulator). The receiver sends these as keyboard keys (see keys.py), not Xbox buttons.
BUTTONS = [
    "drs",
    "ers",
    "clutch",
    "pause",
    "look_back",
    "flashback",
    "pit_limiter",
    "mfd",
    "pad_a",
    "pad_b",
    "pad_x",
    "pad_y",
    "dpad_up",
    "dpad_down",
    "dpad_left",
    "dpad_right",
    "start",
    "back",
    "ind_left",
    "ind_right",
    "hazard",
    "lights",
    "high_beam",
    "horn",
    "park_brake",
    "engine",
    "cruise",
    "wipers",
    "trailer",
    "activate",
    "map",
    "menu",
]
BUTTON_BIT = {name: 1 << i for i, name in enumerate(BUTTONS)}
RACING_BUTTONS = BUTTONS[:8]
PAD_BUTTONS = BUTTONS[8:18]
TRUCK_BUTTONS = BUTTONS[18:]
# Fixed Xbox button for every PAD button (not user-mappable).
PAD_XBOX = {
    "pad_a": "A", "pad_b": "B", "pad_x": "X", "pad_y": "Y",
    "dpad_up": "DPAD_UP", "dpad_down": "DPAD_DOWN", "dpad_left": "DPAD_LEFT", "dpad_right": "DPAD_RIGHT",
    "start": "START", "back": "BACK",
}

RTT_UNKNOWN = 0xFFFF


def derive_key(pin: str) -> bytes:
    return hashlib.sha256(("F1WHEEL-PAIR-v1:" + pin.strip()).encode("utf-8")).digest()


def _tag(key: bytes, data: bytes) -> bytes:
    return hmac.new(key, data, hashlib.sha256).digest()[:TAG_LEN]


@dataclass
class Packet:
    type: int
    session: int
    seq: int
    payload: bytes


@dataclass
class StateMsg:
    phone_ms: int
    steer: float          # -1 .. +1  (positive = right)
    throttle: float       # 0 .. 1
    brake: float          # 0 .. 1
    buttons: int
    gear_up: int          # monotonically increasing 16-bit counter
    gear_down: int
    raw_angle_deg: float
    sensor_hz: int
    rtt_ms: float | None
    flags: int


def encode(key: bytes, ptype: int, session: int, seq: int, payload: bytes = b"") -> bytes:
    data = HEADER.pack(MAGIC, VERSION, ptype, session & 0xFFFFFFFF, seq & 0xFFFFFFFF) + payload
    return data + _tag(key, data)


def decode(key: bytes, data: bytes) -> Packet | None:
    """Return the packet if it is well formed and correctly signed, else None."""
    if len(data) < HEADER.size + TAG_LEN or len(data) > 512:
        return None
    body, tag = data[:-TAG_LEN], data[-TAG_LEN:]
    if not hmac.compare_digest(_tag(key, body), tag):
        return None
    magic, version, ptype, session, seq = HEADER.unpack_from(body)
    if magic != MAGIC or version != VERSION:
        return None
    return Packet(ptype, session, seq, body[HEADER.size:])


def _clamp(v: float, lo: float, hi: float) -> float:
    return lo if v < lo else hi if v > hi else v


def _round(v: float) -> int:
    """Round half up, identical to Java/Kotlin Math.round (Python's round() is half-even)."""
    return int(math.floor(v + 0.5))


def pack_state(m: StateMsg) -> bytes:
    rtt = RTT_UNKNOWN if m.rtt_ms is None else int(_clamp(m.rtt_ms * 10, 0, RTT_UNKNOWN - 1))
    return STATE.pack(
        m.phone_ms & 0xFFFFFFFF,
        _round(_clamp(m.steer, -1, 1) * 32767),
        _round(_clamp(m.throttle, 0, 1) * 65535),
        _round(_clamp(m.brake, 0, 1) * 65535),
        m.buttons & 0xFFFFFFFF,
        m.gear_up & 0xFFFF,
        m.gear_down & 0xFFFF,
        int(_clamp(m.raw_angle_deg * 100, -32767, 32767)),
        int(_clamp(m.sensor_hz, 0, 65535)),
        rtt,
        m.flags & 0xFF,
    )


def unpack_state(payload: bytes) -> StateMsg | None:
    if len(payload) != STATE.size:
        return None
    (phone_ms, steer, thr, brk, buttons, gu, gd, raw, hz, rtt, flags) = STATE.unpack(payload)
    return StateMsg(
        phone_ms=phone_ms,
        steer=max(-1.0, steer / 32767.0),
        throttle=thr / 65535.0,
        brake=brk / 65535.0,
        buttons=buttons,
        gear_up=gu,
        gear_down=gd,
        raw_angle_deg=raw / 100.0,
        sensor_hz=hz,
        rtt_ms=None if rtt == RTT_UNKNOWN else rtt / 10.0,
        flags=flags,
    )


def pack_ack(echo_phone_ms: int, controller_ok: bool, rx_hz: int, dropped: int) -> bytes:
    return ACK.pack(echo_phone_ms & 0xFFFFFFFF, 1 if controller_ok else 0,
                    int(_clamp(rx_hz, 0, 65535)), dropped & 0xFFFFFFFF)


def unpack_ack(payload: bytes):
    if len(payload) != ACK.size:
        return None
    return ACK.unpack(payload)


def pack_hello(device_name: str) -> bytes:
    name = device_name.encode("utf-8")[:32]
    return bytes([len(name)]) + name


def unpack_hello(payload: bytes) -> str:
    if not payload:
        return "phone"
    n = min(payload[0], len(payload) - 1, 32)
    return payload[1:1 + n].decode("utf-8", "replace") or "phone"


def pack_beacon(port: int, name: str) -> bytes:
    n = name.encode("utf-8")[:32]
    return BEACON_MAGIC + struct.pack("<BHB", VERSION, port, len(n)) + n
