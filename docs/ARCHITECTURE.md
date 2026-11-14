# Architecture

## Overview

```
┌──────────── Android phone (mounted on the cardboard wheel) ─────────────┐
│                                                                          │
│  GAME_ROTATION_VECTOR  ──►  q_rel = q_neutral⁻¹ · q_now                  │
│  (gyro + accel fusion,      ──► swing-twist about the steering axis      │
│   200-500 Hz)               ──► 1€ filter ──► range / dead zone / curve   │
│                             ──► steer  -1 … +1                           │
│                                                                          │
│  DriveView (multi-touch)    throttle 0…1   brake 0…1                     │
│                             gear-up / gear-down COUNTERS                 │
│                             held buttons: DRS ERS CLUTCH PAUSE …         │
│                                                                          │
│  UdpLink  ── 200 Hz STATE packets, HMAC-signed with the pairing PIN ──┐  │
└───────────────────────────────────────────────────────────────────────┼──┘
                     local Wi-Fi (or USB tethering)                     │
┌───────────────────────────────────────────────────────────────────────┼──┐
│  Windows receiver (Python)                                             ▼  │
│                                                                          │
│  net thread:   verify HMAC → session/sequence check → latest state       │
│                gear counter delta → queued presses        ACK (20 Hz) ──►│
│  output thread (250 Hz):                                                 │
│                timeout (150 ms) / inactive → NEUTRAL                     │
│                gear press 60 ms, gap 60 ms                               │
│                ──► ViGEmBus virtual Xbox 360 controller                  │
│  beacon thread: LAN broadcast so the phone can find the laptop           │
│  Tk dashboard: status, live axes, latency, rates, drops, bind helper     │
└──────────────────────────────────────────────────────────────────────────┘
                                   │ XInput
                                   ▼
                    EA SPORTS F1 25 (controller input)
        left stick X = steering · RT = throttle · LT = brake · buttons
```

## Technology decisions

| Area | Choice | Why | Rejected alternatives |
|---|---|---|---|
| Phone app | **Native Kotlin**, framework views only (no AndroidX) | Direct `SensorManager` access at `SENSOR_DELAY_FASTEST`, real multi-touch, wake/Wi-Fi locks, tiny APK, nothing to break | Flutter (extra layer between the app and the sensors and touch input), web Device-Orientation API (browser-limited rate, no wake lock, poor multi-touch) |
| Orientation | **Game Rotation Vector** + quaternion swing-twist | The OS fuses gyro + accelerometer. No magnetometer means the laptop's magnets can't disturb it. Gravity removes drift for any non-vertical steering axis. Swing-twist ignores wobble on the other axes. | Raw gyro integration (drifts), Euler roll/pitch/yaw (gimbal lock, depends on how the phone is mounted), magnetometer rotation vector (magnetic interference) |
| No-gyro fallback | Gravity projection onto the steering plane | Works on cheap phones without a gyroscope (noisier) | — |
| Transport | **UDP**, full state every packet | Lowest latency. A lost packet is replaced 5 ms later. No retransmission stalls. | WebSocket/TCP (head-of-line blocking: one lost packet delays everything behind it) |
| Gear events | **Monotonic counters** | Lost packets can't swallow a shift and duplicates can't double it | One-shot event packets (lost or duplicated) |
| Auth | PIN → SHA-256 key → HMAC-SHA256 tag (8 bytes) per packet + strictly increasing sequence number | Other devices on the LAN can't inject or replay input | None / plaintext |
| Virtual controller | **ViGEmBus 1.22 (Xbox 360) via `vgamepad`** | F1 25 supports Xbox controllers natively, with an analog stick and analog triggers. Signed driver, works on Windows 11. | vJoy (signed 2.2.2.0 exists, but F1 25 doesn't list generic DirectInput devices: kept as an experimental backend), writing our own driver (needs test-signing = weaker security), Steam Input (cannot create devices) |
| PC app | Python 3 + Tkinter (stdlib), PyInstaller folder build | One dependency (`vgamepad`). The dashboard needs no extra GUI toolkit. | C#/.NET (equally viable; Python keeps the protocol easy to test and change) |

### ViGEmBus maintenance status

The ViGEmBus project has been **retired by its author** (no further updates). The final
release, 1.22.0, is a properly signed driver that installs and works on Windows 11. Many
controller tools still depend on it. The risk is that a future Windows change breaks it and
nobody fixes it. Mitigation: the receiver's output layer is pluggable (`backends.py`), and a
vJoy backend is already included as a fallback.

## Wire protocol v1

Little-endian. Implemented in `windows-receiver/f1wheel/protocol.py` and
`android-app/.../net/Protocol.kt`. Both are tested against the same golden bytes.

```
Header 12 B : "F1" | ver u8 | type u8 | session u32 | seq u32
Payload     : depends on type
Tag 8 B     : HMAC-SHA256(SHA-256("F1WHEEL-PAIR-v1:" + PIN), header+payload)[0:8]
```

| Type | Dir | Payload |
|---|---|---|
| 1 HELLO | phone→PC | nameLen u8, name (≤32 B UTF-8) |
| 2 STATE | phone→PC | phoneMs u32, steer i16 (±32767), throttle u16, brake u16, buttons u32, gearUp u16, gearDown u16, rawAngle i16 (0.01°), sensorHz u16, rtt u16 (0.1 ms, 0xFFFF = unknown), flags u8 — 25 B |
| 3 BYE | phone→PC | — (PC neutralises immediately) |
| 16 ACK | PC→phone | echoPhoneMs u32, controllerOk u8, rxHz u16, dropped u32 |
| Beacon | PC→broadcast :47801 | "F1WB", ver u8, port u16, nameLen u8, name (unauthenticated, only used to fill in the IP address) |

`flags`: bit0 ACTIVE (driving screen in front, sensor healthy, calibrated), bit1 SENSOR_OK, bit2 CALIBRATED.
`buttons` bit order: DRS, ERS, CLUTCH, PAUSE, LOOK_BACK, FLASHBACK, PIT_LIMITER, MFD (bits 0-7,
re-mappable in the receiver), then the phone's **PAD mode** buttons PAD_A, PAD_B, PAD_X, PAD_Y,
DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, START, BACK (bits 8-17, always the Xbox button of the
same name). While the phone is in PAD mode it sends steering 0 and no pedals, so the gyro can't
move the menu cursor. Bits 18-31 are the **Truck layout** (ETS2 / ATS): IND_LEFT, IND_RIGHT,
HAZARD, LIGHTS, HIGH_BEAM, HORN, PARK_BRAKE, ENGINE, CRUISE, WIPERS, TRAILER, ACTIVATE, MAP, MENU.
The receiver does not send these to the Xbox pad. It holds a keyboard key for each one instead
(`keys.py`, Windows `SendInput` with scan codes, keys set in the receiver's Settings). By default
it only does this while `eurotrucks2.exe` / `amtrucks.exe` is the window in front, and it
releases every key on timeout, BYE, server stop and focus loss.

### Safety rules

* The receiver outputs **neutral** (stick centre, triggers 0, no buttons) when:
  no valid packet for `timeout_ms` (150 ms), the phone isn't ACTIVE, the phone sent BYE,
  the server stops, or the PIN changes.
* Gear counters are re-seeded on every new session. A phone that reconnects with counter
  37 never replays 37 shifts.
* A new session can take over only after the current one has been silent for 1 s.
* Packets with a wrong tag, an old sequence number or the wrong size are rejected and counted.
* The phone clears ACTIVE when it leaves the driving screen, loses window focus
  (notification shade), or the sensor stops for more than 250 ms.

## Latency budget (expected; measure yours in the dashboard)

| Stage | Typical |
|---|---|
| Sensor sample → fused quaternion | 2-5 ms (200-500 Hz) |
| 1€ filter at 30 % smoothing | ~0-8 ms effective lag, mostly while the wheel is held still |
| Send interval (200 Hz) | 0-5 ms |
| Wi-Fi one-way (5 GHz, quiet) | 2-10 ms (USB tethering: 1-3 ms) |
| Receiver → ViGEm update (250 Hz) | 0-4 ms |
| Game polling / frame | 8-16 ms (game dependent) |

The phone measures round-trip time from the ACK echoes and shows it on both devices.
One-way latency is estimated as RTT/2.

## Source layout

```
android-app/app/src/main/java/com/diyf1wheel/
  WheelApp.kt              Application; owns the engine
  engine/WheelEngine.kt    state, calibration, safety flags, lifecycle
  sensor/SteeringSensor.kt sensor selection, sampling
  sensor/SteeringMath.kt   quaternions, swing-twist, gravity fallback, 1€ filter, tuning
  net/Protocol.kt          wire format (mirrors protocol.py)
  net/UdpLink.kt           200 Hz sender, ACK receiver, auto-reconnect, Wi-Fi lock
  net/Discovery.kt         finds the laptop's beacon
  settings/Settings.kt     SharedPreferences, presets
  ui/MainActivity.kt       setup, calibration, tuning, diagnostics
  ui/DriveActivity.kt      full-screen landscape controller
  ui/DriveView.kt          multi-touch controls
  ui/SteeringGauge.kt      wheel/percentage rendering
windows-receiver/f1wheel/
  protocol.py  config.py  backends.py  engine.py  ui.py  __main__.py
windows-receiver/tests/test_engine.py   protocol and safety unit tests
windows-receiver/tools/controller_poc.py   Phase 2 proof of concept (reads back through XInput)
windows-receiver/tools/integration_test.py UDP → receiver → ViGEm → XInput end to end
windows-receiver/tools/simulate_phone.py   fake phone for testing without a device
```
