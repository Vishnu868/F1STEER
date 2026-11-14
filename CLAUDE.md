# DIY F1 Wheel - notes for Claude

A phone is a steering wheel for PC racing / truck games over the local network.

* `android-app/` - Kotlin app (`com.diyf1wheel`). Reads the gyro, sends UDP state packets.
* `windows-receiver/` - Python receiver. Turns the packets into a virtual Xbox 360 pad
  (ViGEm through `vgamepad`) and, for the Truck layout, keyboard keys (`f1wheel/keys.py`,
  Windows `SendInput`).
* Protocol v1 is defined twice and must stay byte-identical: `windows-receiver/f1wheel/protocol.py`
  and `android-app/.../net/Protocol.kt`. Golden bytes in `ProtocolTest.kt` come from the Python side.
* Docs: `docs/` (`ETS2-SETUP.md` for trucks, `F1-25-SETUP.md`, `TROUBLESHOOTING.md`, `ARCHITECTURE.md`).

## Rules from the owner (always apply)

* Never suggest disabling Windows security protections (Defender, SmartScreen, UAC, firewall
  off, ...) to make the app work.
* No suspicious third-party software, no unnecessary or unsigned kernel drivers, no
  downloads from untrusted sites. ViGEmBus (signed, from its official release) is the only driver.
* Sensor data stays on the local network. No telemetry, no external servers.
* Do not claim a game or feature works unless it was tested. Mark untested things as untested.
* No TODO placeholders in code or docs.
* Nothing about the physical wheel build may be committed. The owner keeps those files
  locally only, excluded through `.git/info/exclude`. Check `git status` before every commit.
* The owner likes short, simple, one-step-at-a-time answers. Measurements in cm.
* Commit / push only when the owner asks.

## Build and test

Receiver (Python 3.10+):

```
cd windows-receiver
python -m pip install -r requirements.txt
python -m unittest discover -s tests          # must pass
python -m f1wheel                              # run from source (GUI)
powershell -ExecutionPolicy Bypass -File build-exe.ps1   # exe in dist\F1WheelReceiver
```

Android: `tools\build-android.ps1` needs the local toolchain in `.toolchain\` (not in git);
on a machine without it, use the APK from GitHub Releases instead of building.

Receiver settings and log: `%APPDATA%\F1Wheel\config.json` and `receiver.log`.

## Truck / ETS2 status (open work)

Steering and pedals work in ETS2 (Xbox pad, Simple Automatic gearbox). The **truck buttons
did not reach the game** with receiver 1.3.0: the phone's Truck-layout buttons should make
the receiver press keyboard keys (`[` = left indicator, `L` lights, `H` horn, ...: the
game's defaults), but nothing happened in ETS2. Sending the same keys into a normal window
works. 1.4.0 adds an 80 ms minimum key hold and names the foreground program when keys are
blocked; untested in the game so far.

How the key path works (`engine.py` `compute_output` → `tick` → `keys.py` `KeySender.apply`):
truck bits 18-31 → `cfg.truck_keys` → scan-code `SendInput`, only while the foreground
process is `eurotrucks2.exe` / `amtrucks.exe` (`truck_keys_only_in_game`).

Ways to test on the gaming laptop without the phone:
* Receiver GUI → **Bind helper** → pick e.g. `horn` → **Send in 3 s** → click into the game.
* A script that imports `f1wheel.keys.KeySender` and calls `apply({"H"}, only_in_game=False)`
  while ETS2 is in front (switch to the game during a countdown; Windows does not let a
  script steal focus).

Things to check, in order: is the foreground exe name what `TRUCK_GAMES` expects; does
`SendInput` return 1; does ETS2 react to scan-code input at all (try also virtual-key
input, and holding longer); is the game running elevated (UIPI blocks input from a normal
process to an elevated one, so run both normally); does `receiver.log` show errors.
