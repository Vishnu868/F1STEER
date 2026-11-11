# Test plan and verification status

Legend: ✅ **verified** (run and passed, see "evidence") · 🟡 **verified in simulation**
(real code, simulated device) · ⬜ **to do on real hardware** (procedure below).

## Automated tests (run on every build)

| Suite | Command | Result |
|---|---|---|
| Receiver unit tests: protocol, HMAC, replay, timeout→neutral, gear dedup/loss, session takeover, mapping (17 tests) | `cd windows-receiver` then `python -m unittest discover -s tests -v` | ✅ 17/17 |
| Android JVM tests: golden protocol bytes vs Python, wrong-PIN/tamper rejection, swing-twist, wobble rejection, axis calibration, gravity fallback, tuning curve, 1€ filter (15 tests) | `tools\build-android.ps1` | ✅ 15/15 |
| Phase 2 proof of concept: virtual Xbox 360 pad read back through **Windows XInput** | `python tools\controller_poc.py` | ✅ 9/9 checks: analog steer −100/+25/+100 %, throttle 50/100 %, brake 30 %, A, X, neutral |
| End to end on one PC: simulated phone → real UDP (5 % packet loss) → receiver → ViGEmBus → XInput | `python tools\integration_test.py` | ✅ full lock both ways, analog brake, **exactly 3 up + 3 down shifts despite loss**, neutral after disconnect, 249 Hz controller rate, ~5 ms loopback RTT |
| Dashboard UI smoke test with live traffic | (manual script) | ✅ no UI exceptions, live at ~200 packets/s |

## Staged hardware tests

### TEST 1: Sensor (phone only) ⬜
1. Open the app → *Live steering* card. It should say **SENSOR OK** and show
   *Game rotation vector*, with a sample rate of 100-500 Hz.
2. Tap **Set centre**, then rotate the phone (landscape, facing you) clockwise.
   **Expected:** the angle goes positive (R %), anticlockwise goes negative (L %), and a
   90° turn reads about 90°.

### TEST 2: Calibration ⬜
1. Hold the wheel straight → *Set centre* → the gauge shows **CENTRE**.
2. Turn left and right several times and return to the 12 o'clock mark.
   **Expected:** back within ±1-2° (inside the dead zone → "CENTRE").
3. Put the wheel down for 2 minutes, then pick it up straight.
   **Expected:** drift under about 2° when held upright.

### TEST 3: Communication ⬜
1. Receiver running → phone *Find laptop* → PIN → *CONNECT*.
   **Expected:** phone shows *Connected - controller ready*; dashboard shows *Phone connected*.
2. **START DRIVING** and turn the wheel.
   **Expected:** the dashboard steering bar follows in real time; packets about 200/s;
   RTT on Wi-Fi typically 5-20 ms.

### TEST 4: Analog controller in Windows ✅ (virtual pad) / ⬜ (with phone)
`Win + R` → `joy.cpl` → *Controller (XBOX 360 For Windows)* → *Properties*.
**Expected:** X axis follows steering; Z axis moves with throttle (one way) and brake
(other way); buttons light up for paddles/DRS/ERS. (The dashboard's *Controller test*
does a full sweep without the phone.)

### TEST 5: Paddle shifting ⬜
Tap the right paddle once. **Expected:** dashboard *Gear-up presses* +1, exactly one.
Tap the left paddle once: *Gear-down presses* +1. Tap 5 times fast: +5 (queued, 60 ms apart).
*(Logic ✅ verified in automated tests, including duplicates and packet loss.)*

### TEST 6: F1 25 ⬜ (final step, on the gaming laptop)
Follow [F1-25-SETUP.md](F1-25-SETUP.md) section 6. **Expected:** steering, throttle,
brake work; with a manual gearbox one tap = one gear.

### TEST 7: Reliability ⬜ (logic 🟡 verified in simulation)
| Scenario | Expected |
|---|---|
| Turn phone Wi-Fi off while holding throttle | Within 0.15 s the game sees throttle 0 / centre; dashboard *Interruptions* +1; reconnects by itself when Wi-Fi returns |
| Close the app (swipe away) | BYE sent → immediate neutral |
| Restart the receiver while the phone is connected | Phone shows *Connecting…*, then reconnects automatically within about 1 s |
| Press the power button (screen lock) | Driving screen pauses → neutral; after unlocking, the app resumes and reconnects |
| Pull down the notification shade while driving | All inputs released |
| Hold throttle with one thumb, tap the paddle with the other | Both work at once; a finger sliding from throttle onto the paddle does **not** shift |
| Wrong PIN | Never connects; dashboard *Rejected packets* rises |

Record your results (✅/❌ plus notes) in this file when you run them.
