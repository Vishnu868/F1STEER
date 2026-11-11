# Windows receiver setup

Works on Windows 10/11 (64-bit). Do this on the laptop that runs F1 25.

## 1. Install the ViGEmBus driver (once)

The receiver creates a virtual Xbox 360 controller through **ViGEmBus**, a signed system driver.

1. Open the official release page: <https://github.com/nefarius/ViGEmBus/releases>
2. Download **`ViGEmBus_1.22.0_x64_x86_arm64.exe`** from release *v1.22.0*. Only download
   it from that GitHub page.
3. Run it, accept the license, click **Install**, and allow the administrator prompt.
4. Reboot if asked.

Check: *Device Manager → System devices →* **Nefarius Virtual Gamepad Emulation Bus** is listed.

> The ViGEm project is retired (no future updates), but 1.22.0 is the final, signed and
> working release. You never need to disable Secure Boot, driver signature enforcement or
> antivirus for this project. If anything asks you to, stop: something is wrong.

## 2. Install the receiver

### Option A: packaged app (recommended)

1. Download `F1WheelReceiver-windows.zip` from the project's GitHub *Releases* page.
2. Extract it anywhere, e.g. `C:\Games\F1WheelReceiver\`.
3. Double-click **`F1WheelReceiver.exe`**.
   *Windows SmartScreen* may warn because the exe isn't code-signed. Click
   *More info → Run anyway* only if you downloaded it from this project's GitHub release.
   Optional: right-click the exe → *Send to → Desktop (create shortcut)*.

### Option B: from source

1. Install Python 3.10 or newer from <https://www.python.org/downloads/> (tick *Add python.exe to PATH*).
2. Download or clone this repository.
3. Double-click **`windows-receiver\run-receiver.bat`**. The first run creates a private
   environment and installs `vgamepad` (about 1 minute). Later runs start immediately.

To build the exe yourself:

```bash
powershell -ExecutionPolicy Bypass -File windows-receiver\build-exe.ps1
```

The output is `windows-receiver\dist\F1WheelReceiver\F1WheelReceiver.exe`.

## 3. Allow it through the firewall (first start)

Windows Defender Firewall asks whether *F1WheelReceiver* (or *Python*) may communicate:

* Tick **Private networks** → **Allow access**.
* Leave *Public networks* unticked.

Your home Wi-Fi must be set to **Private**: *Settings → Network & internet → Wi-Fi →
(your network) → Network profile type → Private network*. On a "Public" network the
firewall blocks the phone, by design.

## 4. Using the dashboard

| Area | Shows |
|---|---|
| Connection | Phone status, **laptop IP**, port, **pairing PIN** (enter both on the phone) |
| Virtual controller | Whether the Xbox 360 controller exists; Reconnect; Controller test; Bind helper |
| Live inputs | The exact steering/throttle/brake/buttons being sent to the game |
| Performance | Packets/s, phone sensor rate, controller rate, round-trip latency, drops, interruptions |
| Settings | Controller type, port, timeout, gear press timing, PC-side steering invert, button mapping |
| Diagnostics | Full state + event log, with *Copy to clipboard* for bug reports |

*New PIN* generates a new pairing PIN (do this if someone else learned it).

Files: settings in `%APPDATA%\F1Wheel\config.json`, log in `%APPDATA%\F1Wheel\receiver.log`.

## Command-line options

```bash
F1WheelReceiver.exe --backend vjoy     # experimental vJoy output
F1WheelReceiver.exe --port 47810       # different UDP port
```

From source only (the packaged exe has no console window):

```bash
python -m f1wheel --nogui              # text mode, prints live values
```
