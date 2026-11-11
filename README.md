# DIY F1 Wheel

Turn an Android phone into an **analog racing wheel** for
EA SPORTS F1 25 (and other PC racing games that accept an Xbox controller).

* **Steering** from the phone's gyroscope: true analog, drift-corrected by gravity,
  calibrated centre and axis, adjustable range, dead zone, curve and smoothing.
* **Touchscreen pedals:** throttle and brake (digital, or analog by thumb position).
* **Two layouts:** *Auto gears* (big DRS and Clutch buttons) or *Paddle shift* (right = gear up,
  left = gear down, exactly one shift per tap).
* **Extra buttons:** DRS, ERS, clutch, pause, look back, flashback, pit limiter, MFD (pick what you want on screen).
* The Windows receiver presents a **virtual Xbox 360 controller** (ViGEmBus) that games recognise natively.
* Local network only: UDP, PIN pairing with signed packets, no accounts, no cloud, no telemetry.
* Safety: any disconnect, timeout or leaving the driving screen sets the controls to **neutral** within 150 ms.

```
 phone (gyro + touch) ──UDP 200 Hz, signed──► Windows receiver ──► virtual Xbox 360 pad ──► F1 25
```

## Quick start

1. **Laptop:** install [ViGEmBus 1.22.0](https://github.com/nefarius/ViGEmBus/releases), then run
   `F1WheelReceiver.exe` from the release zip (or `windows-receiver\run-receiver.bat` from source).
   Allow it on **Private** networks when Windows asks. → [Windows setup](docs/INSTALL-WINDOWS.md)
2. **Phone:** install `DIYF1Wheel-x.y.z.apk` → *Find laptop* → enter the PIN shown on the
   laptop → *CONNECT*. → [Android setup](docs/INSTALL-ANDROID.md)
3. **Wheel:** build the cardboard wheel and mount the phone in landscape, facing you. →
   [Cardboard guide](docs/CARDBOARD-WHEEL.md)
4. Hold the wheel straight → **Set centre** → **START DRIVING**. → [Calibration](docs/CALIBRATION.md)
5. **F1 25:** set up the controller and bind gear up/down to the paddles. → [F1 25 guide](docs/F1-25-SETUP.md)

No wheel yet? Hold the phone upright like a steering pad and turn it. It works the same.

## Documentation

| | |
|---|---|
| [Architecture](docs/ARCHITECTURE.md) | Design decisions, protocol, data flow, latency budget |
| [Windows setup](docs/INSTALL-WINDOWS.md) | Driver, receiver, firewall, dashboard |
| [Android setup](docs/INSTALL-ANDROID.md) | APK install, connection, USB tethering, building from source |
| [Cardboard wheel](docs/CARDBOARD-WHEEL.md) | Dimensions, construction, phone mounting, grip |
| [Calibration](docs/CALIBRATION.md) | Centre, axis, presets, every tuning parameter |
| [F1 25 configuration](docs/F1-25-SETUP.md) | Controller settings, mapping, gearbox, assists |
| [Testing](docs/TESTING.md) | Staged test plan, with what has and hasn't been verified yet |
| [Troubleshooting](docs/TROUBLESHOOTING.md) | Connection, controller, steering and touch issues |

## Project status

| Component | Status |
|---|---|
| Virtual Xbox controller (ViGEmBus), analog axes + buttons, read back through Windows XInput | ✅ verified |
| Receiver: protocol, pairing, timeout/neutral, gear-shift dedup under packet loss | ✅ verified (unit + end-to-end tests over real UDP) |
| Android app: protocol (golden bytes shared with Python), steering math | ✅ verified (JVM unit tests) |
| Android app on a real phone (Nothing Phone (2), Android 16) | ✅ verified: steering, centring, pedals, paddles, disconnect safety, auto-reconnect ([log](docs/TESTING.md)) |
| F1 25 in-game (EA app, *Auto gears* layout) | ✅ verified: steering, throttle, brake, DRS, disconnect safety ([log](docs/TESTING.md)) |

## Repository layout

```
android-app/        Kotlin Android app (no Android Studio needed to build)
windows-receiver/   Python receiver + dashboard, tests, tools, exe build script
docs/               All guides
tools/              Toolchain setup, APK build, signing key, release packaging
```

## Building

```bash
powershell -ExecutionPolicy Bypass -File tools\setup-android-toolchain.ps1
```

```bash
powershell -ExecutionPolicy Bypass -File tools\make-release.ps1
```

`release/` then contains the APK, the receiver zip and `SHA256SUMS.txt`.

## Dependencies and licences

* [ViGEmBus](https://github.com/nefarius/ViGEmBus) (BSD-3-Clause), the virtual gamepad bus driver.
  Retired upstream; 1.22.0 is the final signed release.
* [vgamepad](https://github.com/yannbouteiller/vgamepad) (MIT), the Python client for ViGEm.
* Optional: [vJoy, BrunnerInnovation fork](https://github.com/BrunnerInnovation/vJoy), an experimental fallback.

This project is not affiliated with EA, Codemasters or Formula 1. "F1" is used only to
describe compatibility.
