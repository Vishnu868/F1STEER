# Calibration and tuning

All settings are saved on the phone and survive restarts.

## 1. Set the centre (always do this)

1. Mount the phone, hold the wheel straight (red 12 o'clock mark pointing up), the way you'll drive.
2. On the setup screen tap **Set centre**, or on the driving screen **hold CENTRE** for
   half a second.
3. The live gauge should read **CENTRE / 0°**. Turn the wheel: the angle follows; return it:
   it comes back to about 0°.

If you start driving without ever setting a centre, the app uses the wheel's position at
that moment as the centre (not saved).

## 2. Calibrate the steering axis (only if needed)

By default the app assumes the steering axis is the line straight through the screen. That's
correct when the phone sits flat on the wheel's face. If the phone is mounted at an angle,
or the angle readout changes when you merely tilt the wheel towards yourself, calibrate it:

1. Tap **Calibrate axis**.
2. Hold the wheel at centre → **Next**.
3. Turn the wheel **right about 90°** (a quarter turn) and hold it there → **Finish**.

The app computes the true rotation axis from that movement. **Reset axis** returns to the
default. Needs a gyroscope. Gravity-only phones always use the default.

## 3. Tuning parameters

| Setting | What it does | Typical |
|---|---|---|
| **Full lock left / right** | How many degrees you physically turn the wheel to reach 100 % steering in that direction. This is the **saturation point**: beyond it the output stays at 100 %. Smaller = quicker, more twitchy steering. Larger = calmer and more precise, but more arm movement. | 60-120° |
| **Sensitivity** | Multiplies the angle before saturation (×1.5 with 90° full lock behaves like 60°). Handy for quick changes. | ×1.0 |
| **Centre dead zone** | Tiny movements around centre are ignored, and the output is rescaled so there's no jump at its edge. | 1-3 % |
| **Response curve** | 1.0 = linear. Above 1 = gentler near centre with full lock still reachable (good for high-speed corners). Below 1 = more aggressive near centre. | 1.0-1.6 |
| **Smoothing** | 1€ filter strength. It removes hand tremor when the wheel is still and stays responsive during fast moves. High values add noticeable lag. | 15-40 % |
| **Invert** | Swaps left and right (only needed if the phone is mounted face-away from you). | off |

### Presets

| Preset | Full lock | Dead zone | Curve | Smoothing | Feel |
|---|---|---|---|---|---|
| Sensitive | 60° | 1 % | 1.0 | 15 % | Quick, like a gamepad stick |
| **Balanced** (default) | 90° | 2 % | 1.3 | 30 % | Good starting point |
| Smooth | 120° | 3 % | 1.6 | 60 % | Calm, precise, more arm movement |
| Custom | — | — | — | — | Any slider you move switches to Custom |

## 4. Pedal mode

* **Digital** (default): touching the pedal gives 100 %. Pair it with F1 25's traction control
  assist for drivable throttle.
* **Analog**: the higher your thumb sits on the pedal, the more you get (bottom = 15 %,
  top = 100 %). Slide your thumb up and down while holding it.

## 5. Testing each input

On the **driving screen** (only there is input live), watch the laptop dashboard:

| Action | Dashboard shows |
|---|---|
| Turn left / right | Steering bar moves, ±100 % at full lock |
| Hold throttle / brake | Throttle / brake bar 100 % (or your thumb position in analog mode) |
| Tap right paddle once | Gear-up presses +1, "Gear up" lamp blinks |
| Tap left paddle once | Gear-down presses +1 |
| Hold DRS / ERS / Clutch… | The matching lamp lights while held |

Windows' own tester works too: press `Win + R`, type `joy.cpl`, double-click
**Controller (XBOX 360 For Windows)**. Steering = X axis, throttle/brake = Z axis (combined
in this old panel), buttons 1-10.
