# Android app setup

Requirements: Android 8.0 or newer. A gyroscope is strongly recommended. Phones without one
fall back to gravity-only steering, which is noisier.

## 1. Install the APK

1. On the phone, download **`DIYF1Wheel-<version>.apk`** from the project's GitHub *Releases* page.
2. Open it. Android asks to allow installs from your browser or file manager:
   *Settings → Allow from this source* → back → **Install**.
3. Optional: turn the "unknown sources" permission off again afterwards.

Play Protect may show "app scanned / unknown developer". That's expected for apps outside
the Play Store.

Alternatively, from a PC with USB debugging enabled:

```bash
adb install -r DIYF1Wheel-1.0.0.apk
```

## 2. Connect to the laptop

1. Phone and laptop on the **same Wi-Fi** (5 GHz preferred). Start the receiver on the laptop.
2. Open **DIY F1 Wheel** → tap **Find laptop**. The IP address fills in automatically. If
   nothing is found, type the IP address shown in the receiver.
3. Enter the 6-digit **PIN** shown in the receiver → **CONNECT**.
4. The status turns green: *Connected - controller ready*. The app reconnects by itself
   after Wi-Fi drops, app restarts and laptop restarts.

### Alternative: USB tethering (lowest latency, no Wi-Fi needed)

1. Connect the phone to the laptop with a USB cable.
2. Phone: *Settings → Network & internet → Hotspot & tethering → USB tethering* → on.
3. On the laptop, the receiver now shows an extra IP (often `192.168.x.x` from the phone's
   network). Use that IP on the phone (or *Find laptop*).
4. Allow the receiver on the new network if Windows asks (Private).

The cable also keeps the phone charged.

## 3. Calibrate and drive

See [CALIBRATION.md](CALIBRATION.md). In short: hold the wheel straight → **Set centre** →
**START DRIVING**.

Pick a **controller layout** on the setup screen. Pedals are always **BRAKE** bottom left and
**THROTTLE** bottom right; the layout decides the two big top buttons:

| Layout | Top left | Top right | Use with |
|---|---|---|---|
| **Auto gears** (default) | **DRS** + **ERS** | **CLUTCH** + **PAUSE** | Automatic gearbox in the game |
| **Paddle shift** | **DOWN** (gear −) | **UP** (gear +) | Manual gearbox in the game |
| **Truck (ETS2 / ATS)** | **◀** indicator | **▶** indicator | Euro Truck Simulator 2 / American Truck Simulator, see [ETS2-SETUP.md](ETS2-SETUP.md) |

Also on the driving screen:

* **Four-finger layout** (all layouts): index fingers on the two top corners, thumbs on the
  pedals in the bottom corners. Next to each pedal is a strip of small buttons. The thumb
  reaches them by sliding inwards, and the first ones sit at the bottom, closest to the
  thumb. F1 layouts: the small buttons you enabled (e.g. PIT) and **PAD**. Truck: lights,
  horn, cruise, parking brake and the rest. Buttons go close to the screen edges.
* Middle: connection status and the steering arc, nothing to press while driving. Bottom
  centre: **HOLD TO CENTRE** (hold 0.6 s to re-centre).
* **PAD mode** (tap PAD in the right strip, or tap PAUSE): the screen becomes a plain gamepad for the game's
  menus: a D-pad on the left, **A / B / X / Y** on the right (Xbox colours: A = select,
  B = back), **START** and **BACK** in the middle. Steering and pedals are switched off while
  the pad is up, so turning the wheel can't move the menu cursor. Tap **BACK TO DRIVING** to
  race again. These buttons always send the Xbox button of the same name; they aren't
  affected by the receiver's button mapping.
* Back gesture → returns to setup. Input to the game stops immediately (neutral).

The screen stays on while the app is open. Input is only sent to the game while the
driving screen is in front.

## 4. Battery and heat

* Sensors at full rate, the screen on and Wi-Fi in low-latency mode use noticeably more
  battery. Charge during long sessions (USB tethering does both).
* If the phone gets hot: lower the send rate to 100 Hz (setup screen), lower the screen
  brightness, remove a thick case, point a fan at the back of the wheel.

## 5. Build the APK yourself

Needs no Android Studio. From the repository root on Windows:

```bash
powershell -ExecutionPolicy Bypass -File tools\setup-android-toolchain.ps1
```

```bash
powershell -ExecutionPolicy Bypass -File tools\build-android.ps1
```

The first script downloads JDK 21, the Android command-line tools and SDK platform 35 into
`.toolchain\` (git-ignored). The second runs the unit tests and builds
`android-app\app\build\outputs\apk\release\app-release.apk` (or a debug APK if you have
no signing key).
