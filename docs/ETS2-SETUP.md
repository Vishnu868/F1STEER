# Euro Truck Simulator 2 / American Truck Simulator setup

The phone wheel works in ETS2 and ATS in two ways at once:

| What | Goes to the game as | Needs |
|---|---|---|
| Steering, throttle, brake | Virtual **Xbox 360 controller** (same as F1 25) | Game set to use the gamepad |
| Indicators, lights, horn, parking brake… | **Keyboard keys** from the receiver | Keys in the receiver = keys in the game |

Truck games have many more functions than an Xbox pad has buttons. The games already have
keyboard keys for all of them, so the receiver presses those keys for you. It uses the
standard Windows `SendInput` call. There is no extra driver and no extra program.

> **Not tested yet.** This layout is new in app 1.3.0 and has not been tried in ETS2 or ATS
> yet. The key list below is the games' usual default as far as we know, but nobody has
> checked it against a fresh install. Do the check in step 4, which takes about two minutes.

## 1. Phone

1. Update the phone app to **1.3.0** or newer.
2. On the setup screen, under **Controller layout**, pick **Truck (ETS2 / ATS)**.
3. Steering feel: trucks turn slowly, so **Smooth** is a good start. If full lock comes too
   early, raise *max angle left / right* in the steering settings.

Driving screen:

```
 ┌────────┬───────────┐                    ┌─────────┬────────┐
 │        │ MAP       │   ● LIVE · 3 ms    │ PAD     │        │
 │ ◀ IND  │ ENGINE    │                    │ MENU    │ IND ▶  │  ← index fingers
 │        │ WIPERS    │  ( steering arc )  │ ENTER   │        │
 ├────────┤ HAZARD    │                    │ TRAILER ├────────┤
 │ BRAKE  │ HIGH BEAM │                    │ P-BRAKE │THROTTLE│  ← thumbs
 │        │ LIGHTS    │ [hold to centre]   │ CRUISE  │        │
 │        │           │                    │ HORN    │        │
 └────────┴───────────┘                    └─────────┴────────┘
```

Made for holding the phone with both hands. Your **index fingers** reach the indicators in the
top corners. Your **thumbs** rest on BRAKE / THROTTLE and slide inwards to the button strips.
The buttons you use most (LIGHTS, HORN, CRUISE) are at the bottom of each strip, the
shortest move from the pedal. The middle has nothing to press: it only shows the steering.

Tap a button = one key press. Hold it = the key stays held (useful for the horn).

## 2. Laptop receiver

Use receiver **1.3.0** or newer. Open **Settings**. The **TRUCK KEYS** column lists each
phone button and the key it presses:

| Phone button | Key |
|---|---|
| ◀ Indicator | `[` |
| Indicator ▶ | `]` |
| HAZARD | `F` |
| LIGHTS | `L` |
| HIGH BEAM | `K` |
| HORN | `H` |
| P-BRAKE | `Space` |
| ENGINE | `E` |
| CRUISE | `C` |
| WIPERS | `P` |
| TRAILER | `T` |
| ENTER | `Enter` |
| MAP | `M` |
| MENU | `Esc` |

**Only send to ETS2 / ATS window** is on by default. The keys are then sent only while
`eurotrucks2.exe` or `amtrucks.exe` is the window in front, so the phone can never type
into another program. When the game isn't in front, the dashboard shows
*Truck keys - NOT SENT: game window not in front*.

## 3. In the game

1. Start the receiver first, then the game, so the game sees the Xbox controller.
2. *Options → Controls*: choose the game's **gamepad / Xbox controller** setup. Then check
   these three in the list (*Options → Controls*) and set them if needed:
   * **Steering** → left stick, left / right (X axis)
   * **Throttle** → right trigger
   * **Brake** → left trigger
   If a setting asks, use the receiver's **Bind helper** (*steer_left*, *throttle*,
   *brake* → **Send in 3 s**) and switch to the game while it counts down.
3. *Options → Gameplay*: pick an **automatic** transmission unless you want to shift with
   the keyboard.
4. **Check the keys.** In *Options → Controls → Keyboard*, look up each function from the
   table above. If the game shows a different key, change it in the receiver's
   Settings (not in the game) and Save. Test from the receiver: **Bind helper** → pick e.g.
   `horn` → **Send in 3 s** → switch to the game, which should honk.

## 4. Quick test on the road

1. Drive screen on the phone, game in front on the laptop.
2. Tap **◀**: the left indicator should blink. Tap again: it stops.
3. Tap **LIGHTS**, **HORN** (hold), **P-BRAKE**.
4. Steer and press the pedals: the truck should respond the same way as in F1 25.

If the steering works but no key does, check the dashboard line *Truck keys (ETS2 / ATS)*.
* **Lamps light green** and nothing happens in the game: the game uses a different key.
  Fix it in the receiver Settings (step 4 above).
* **Lamps amber, "NOT SENT"**: the game window isn't in front. Click into the game once.
* Running the game **as administrator** but the receiver not? Windows then blocks keys
  from the receiver to the game. Run both normally (recommended) or both as administrator.

## Menus

ETS2 menus are made for the mouse, so use the laptop's mouse or touchpad for them. **MENU**
(Esc) opens and closes the pause menu. The phone's **PAD** mode also works in the game's
menus wherever the game supports a gamepad.
