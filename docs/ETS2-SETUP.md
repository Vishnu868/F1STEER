# Euro Truck Simulator 2 / American Truck Simulator setup

The phone wheel works in ETS2 and ATS in two ways at once:

| What | Goes to the game as | Needs |
|---|---|---|
| Steering, throttle, brake | Virtual **Xbox 360 controller** (same as F1 25) | Game set to use the gamepad |
| Indicators, lights, horn, parking brake… | **Keyboard keys** from the receiver | Keys in the receiver = keys in the game |

Truck games have many more functions than an Xbox pad has buttons. The games already have
keyboard keys for all of them, so the receiver presses those keys for you. It uses the
standard Windows `SendInput` call. There is no extra driver and no extra program.

> **Status:** steering and pedals verified in ETS2 through the game's own input wizard
> (app 1.3.0). The keyboard keys and the throttle lock (app / receiver 1.4.0) are not
> verified in the game yet. Step 5 checks them in two minutes.

## 1. Phone

1. Update the phone app to **1.4.0** or newer.
2. On the setup screen, under **Controller layout**, pick **Truck (ETS2 / ATS)**.
3. Under **Steering response**, tap the **Truck** preset. Full lock is then at **540° each
   way** (1.5 turns, 3 turns lock to lock, like a real truck). The app tracks the wheel past
   180°, so keep turning: the gauge shows `+540° · 1.5 TURNS`. Change the two *Full lock*
   sliders if you want fewer turns; they go up to 900°.
4. Hold the phone level and **set the centre** once. Do it again whenever the gauge is not at 0°
   with the wheel straight.

A 3-turn wheel cannot have a USB cable on it: the cable wraps round. Use Wi-Fi (see *Latency*).

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

### Throttle lock

Turning a truck wheel three times takes both hands off the pedals, so in the Truck layout
THROTTLE **locks**:

| You do | Result |
|---|---|
| Tap THROTTLE | Stays on at 100 % (bright green, *LOCKED 100%*). Hands are free to turn. |
| Tap it again | Off. |
| Touch BRAKE | Off, always. Braking never has to wait for a second tap. |
| Press, slide up or down, lift (Analog pedal mode) | Locks at the level you lifted from, e.g. *LOCKED 60%*. |

Leaving the driving screen, opening PAD mode or losing the connection releases it, and the
receiver neutralises the pedals on its own 150 ms after the phone stops sending. The switch
*Throttle lock (Truck layout)* on the setup screen turns the feature off. BRAKE is always
hold-to-brake.

Handling tip: turn the wheel hand over hand, like a real truck, and let the thumbs come back
to the pedals and strips when the wheel is straight again. The indicators in the corners stay
reachable mid-turn.

## 2. Laptop receiver

Use receiver **1.4.0** or newer (the angle read-out and the phone must use the same scale).
Open **Settings**. The **TRUCK KEYS** column lists each phone button and the key it presses.
They are the game's own default keys, so with a fresh ETS2 install nothing needs mapping.
The first ten were read off the game's *Keys & Buttons* page; the last four (CRUISE, ENTER,
MAP, MENU) are the usual defaults but were not checked against the page yet:

| Phone button | Key | Game function (Keys & Buttons) |
|---|---|---|
| ◀ Indicator | `[` | Left-Turn Indicator |
| Indicator ▶ | `]` | Right-Turn Indicator |
| HAZARD | `F` | Hazard Warning |
| LIGHTS | `L` | Light Modes |
| HIGH BEAM | `K` | High Beam Headlights |
| HORN | `H` | Horn |
| P-BRAKE | `Space` | Parking Brake |
| ENGINE | `E` | Start/Stop Engine |
| CRUISE | `C` | Cruise Control |
| WIPERS | `P` | Wipers |
| TRAILER | `T` | Trailer Attach/Detach |
| ENTER | `Enter` | Activate (confirm prompts) |
| MAP | `M` | Route Advisor map |
| MENU | `Esc` | Pause menu |

If you changed a key in the game, change the same key in the receiver's Settings and Save.
Keys from this list that are not a letter or digit are entered by name (`SPACE`, `ENTER`, `ESC`).

**Only send to ETS2 / ATS window** is on by default. The keys are then sent only while
`eurotrucks2.exe` or `amtrucks.exe` is the window in front, so the phone can never type
into another program. When the game isn't in front, the dashboard shows
*Truck keys - NOT SENT: game window not in front*.

## 3. In the game: first start

1. Start the receiver first, then the game, so the game sees the Xbox controller.
2. The game opens its **Input wizard** the first time (or *Options → Controls → Input wizard*):
   * **Select control method** → **Controls** (the wheel / gamepad box). Not Keyboard, not
     Mouse steering. Ignore the yellow Steam note; nothing needs changing in Steam.
   * **Select gearbox type** → **Simple Automatic**. THROTTLE drives forward, BRAKE slows
     the truck and, held after it stops, reverses. No gear buttons needed. The other three
     types need buttons the Truck layout doesn't have.
   * The summary says *Joy left stick = steering, RT = forward, LT = reverse*. That is the
     receiver's output. **Finish wizard**.
3. *Options → Controls* afterwards should show *Keyboard + XInput Gamepad 1*, *Transmission:
   Simple Automatic*, Steering axis = Joy left stick, Acceleration = RT, Brake = LT.

Do **not** bind the phone's truck buttons in *Keys & Buttons*: they already arrive as the
default keys. Clicking a slot there and tapping a phone button while the game has the
default keys only moves the key to another slot. If a slot shows something odd like
*Left Windows* or *Up* (a key pressed while the slot was waiting), or the bottom line says
*Lost function assignment*, use **Reset to defaults** on the *Keys & Buttons* page.

## 4. In-game settings worth changing

*Options → Controls*
* **Steering sensitivity** (gamepad, default 0.40): leave it at 0.40 to start and change it
  only if the truck feels twitchy or lazy. The number of turns is set on the phone, not here.
* **Steering non-linearity**: 0.00 (linear). The phone's own *Response curve* does this job.
* **Force feedback**: has no effect; the phone has no motor. Leave or untick.

*Options → Gameplay*
* **Automatic engine and electricity start**: ticked (default), so the truck starts when you
  press THROTTLE and ENGINE is rarely needed.
* **Automatic parking brake engage**: your choice. Off means P-BRAKE is only used when you
  set it yourself.

## 5. Check the keys (two minutes)

1. Receiver running, phone on the driving screen with the Truck layout, game in front, truck
   on the road.
2. Tap **◀** on the phone: the left indicator arrow at the bottom-left of the game screen
   blinks. Tap again: it stops.
3. Tap **LIGHTS**, hold **HORN**, tap **P-BRAKE**.
4. Tap **THROTTLE** once and take your thumb off: the truck keeps accelerating, the button
   stays bright green. Touch **BRAKE**: it stops.

If the steering works but no key does, look at the receiver dashboard line *Truck keys
(ETS2 / ATS)* while you hold a phone button:
* **Line missing / receiver older than 1.3.0**: update the receiver. Older receivers ignore
  the truck buttons completely.
* **Lamp lights green, game does nothing**: the game uses a different key for that function.
  Change the key in the receiver's Settings (step 2) and Save.
* **Lamp amber, "NOT SENT: game window not in front"**: click into the game once. If it says
  that while the game is clearly in front, untick *Only send to ETS2 / ATS window*, open
  Notepad, use **Bind helper → horn → Send in 3 s** and click into Notepad: an `h` must
  appear. If it does, the game is the problem; if not, the receiver cannot send keys on
  this PC (see next point).
* Running the game **as administrator** but the receiver not? Windows blocks keys from a
  normal program to an elevated one. Run both normally (recommended) or both as administrator.

## Latency on Wi-Fi

USB tethering is the lowest latency but impossible with a 3-turn wheel. For Wi-Fi:

* Use the **laptop's own hotspot** (Windows *Mobile hotspot*) or the phone's hotspot, and
  pick the **5 GHz** band in its settings. A home router in between adds hops and jitter.
* On the laptop, keep the Wi-Fi adapter out of power saving: *Control Panel → Power
  Options → Change plan settings → Change advanced power settings → Wireless Adapter
  Settings → Power Saving Mode → Maximum Performance*. This is a power setting, not a
  security setting.
* The phone already holds a low-latency Wi-Fi lock while connected, so the phone side needs
  nothing.
* If the dashboard's *interruptions* counter climbs, drop the phone's **Send rate** to
  100 Hz: fewer packets per second survive a busy channel better.

Rough expectations: a few ms over USB, around 5-15 ms over a direct hotspot, more through a
router. Read the real number from the LIVE chip on the phone.

## Menus

ETS2 menus are made for the mouse, so use the laptop's mouse or touchpad for them. **MENU**
(Esc) opens and closes the pause menu. The phone's **PAD** mode also works in the game's
menus wherever the game supports a gamepad. Lay the phone flat or open PAD mode while
clicking in menus: a tilted phone moves the stick and can jump the menu selection.
