# F1 25 configuration (EA app, Windows)

The receiver creates a standard **Xbox 360 controller**. F1 25 supports Xbox controllers
natively, so the game treats your phone wheel as a gamepad: steering on the left stick,
throttle on RT, brake on LT, everything else on buttons.

> **Not yet verified in game.** Everything below is the planned procedure. The virtual
> controller itself is verified working in Windows (XInput). The final in-game check is
> TEST 6 in [TESTING.md](TESTING.md), done on the gaming laptop.

Menu names below follow the F1 25 PC menus as documented publicly. If a label differs
slightly in your version, look for the closest match.

## 0. Before launching the game

1. Start **F1 Wheel Receiver** and connect the phone (dashboard shows *Phone connected*).
2. **Unplug or switch off any other gamepad** (real Xbox/PlayStation controller), so the
   game doesn't listen to the wrong device.
3. Check Windows sees the virtual pad: `Win + R` → `joy.cpl` → *Controller (XBOX 360 For Windows)*.
4. Start the receiver **before** the game. If you start it after, the game usually
   still detects the new controller. If it doesn't, restart the game.

EA app: nothing special is needed. Steam Input isn't involved for the EA app version. (On a
Steam copy, disable Steam Input for F1 25 so Steam doesn't remap the virtual pad: Steam →
F1 25 → Properties → Controller → *Disable Steam Input*.)

## 1. Select the controller

*Settings → Controls, Vibration & Force Feedback.* The active device should show as an
Xbox / controller device. Pick it if several are listed. Press the phone's throttle: the
game's input display should react.

## 2. Calibration screen (stick and triggers)

In the controller's calibration / input-response settings set:

| Game setting | Value | Why |
|---|---|---|
| Steering deadzone | **0** | The phone app already has its own dead zone |
| Steering linearity | **0** (try 0-20 later) | The phone app's response curve handles this |
| Steering saturation | **0** | Full lock already comes from the phone's "full lock" angle |
| Throttle / brake deadzone | **0** | Touch pedals start from exactly 0 |
| Throttle / brake saturation | **0** | |
| Vibration | Off | There's no motor in the cardboard 🙂 |

Tune steering on the **phone** (presets / sliders), not in the game, so everything stays in one place.

## 3. Button mapping

The receiver's defaults (change them in *Receiver → Settings*):

| Phone control | Xbox button | Bind to in-game action |
|---|---|---|
| Steering (gyro) | Left stick X | Steer left / steer right (default) |
| Throttle pedal | RT | Accelerate (default) |
| Brake pedal | LT | Brake (default) |
| Right paddle | **A** | **Gear up** |
| Left paddle | **X** | **Gear down** |
| DRS | Y | DRS |
| ERS | RB | ERS / Overtake |
| Clutch | LB | Clutch (only used for manual race starts, if your mapping lists it) |
| Pause | START | Pause (default) |
| Look back | Right stick click | Look back |
| Flashback | BACK | Flashback / rewind |
| Pit limiter | B | Pit limiter |
| MFD | D-pad up | MFD / radio menu |

**The easiest way to bind** is in-game: *Controls → Edit mappings*. Select an action
(e.g. *Gear Up*); when the game asks for an input, **press that control on the phone**
(the driving screen must be open). Do this for each action you use.

If the game ignores input while the dashboard window is focused, use the dashboard's
**Bind helper**: pick the function → *Send in 3 s* → switch to the game's "press a button"
prompt.

Make sure no other action is bound to the same Xbox button. Unbind duplicates in the game.

## 4. Gearbox

* **Automatic gears:** *Settings → Assists → Gearbox: Automatic*, and choose the
  **Auto gears** layout in the phone app (big DRS and CLUTCH buttons instead of paddles).
* **Manual with paddles:** *Gearbox: Manual*, and choose the **Paddle shift** layout. The right paddle shifts up exactly one gear per
  tap and the left paddle down one gear per tap. Repeated taps queue up to 4 shifts,
  60 ms apart.

## 5. Recommended assists for touch pedals

Touch throttle is on/off (or thumb-position analog), so:

* **Traction control: Medium or Full.** This makes a digital throttle drivable out of corners.
* **ABS: On.** A 100 % touch brake would otherwise lock the wheels.
* Braking assist / racing line as you like.

## 6. First drive checklist

1. Time trial, any track. Phone on the driving screen, wheel at centre → hold **CENTRE**.
2. On the straight: the car goes straight with the wheel centred.
3. Turn slowly to full lock: the car steers smoothly, without jumps.
4. Hold throttle: accelerates. Release: lifts. Brake: slows.
5. Manual gearbox test: tap the right paddle once → one gear up; tap the left once → one gear down.
6. Pull Wi-Fi off the phone mid-straight: within about 0.15 s the car coasts (throttle 0, steering centre).

## Notes and limitations

* F1 25 applies its own controller steering assistance and speed-sensitive smoothing to
  gamepads, so steering is a little softer than a real wheel. That's a property of the
  game's controller mode, not of this project.
* Force feedback isn't possible (the virtual pad has rumble only, and the cardboard has no motor).
* EA's anti-cheat runs on PC. The ViGEmBus driver is a signed system driver used by many
  common controller tools and doesn't touch the game process, but this project can't
  guarantee how a future anti-cheat update will treat it.
* **vJoy fallback:** if the Xbox controller ever stops working, install vJoy 2.2.2.0 (signed,
  BrunnerInnovation fork), set *Receiver → Settings → Controller type = vjoy*, and map the
  axes manually in game. Experimental: F1 25 doesn't officially list vJoy.
