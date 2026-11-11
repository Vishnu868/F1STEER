# Troubleshooting

Start with the receiver's **Diagnostics** window (*Copy to clipboard* for bug reports) and
the phone's **Sensor debug** switch.

## Connection

| Symptom | Cause / fix |
|---|---|
| Phone stays *Connecting…* | Wrong IP or PIN. The PIN must match the receiver exactly; after *New PIN* re-enter it on the phone. Check the receiver's *Rejected packets* counter: if it rises, the PIN is wrong. |
| Still *Connecting…*, receiver shows nothing | Windows firewall: the Wi-Fi profile must be **Private** and the receiver allowed on Private networks (*Windows Security → Firewall → Allow an app through firewall*). Both devices on the same network (not a guest network). |
| *Find laptop* finds nothing | The router blocks broadcasts ("AP isolation", "client isolation", common on guest/hotel/campus Wi-Fi). Type the IP manually. If the phone can't reach the laptop at all, use **USB tethering**. |
| Phone on mobile data / VPN | Turn off VPN apps and "Wi-Fi assistant / smart network switch" features on the phone. |
| *Network error - retrying* | Wi-Fi dropped. The app retries every second and resumes automatically. |
| High latency (> 30 ms) or *Dropped packets* climbing | Use 5 GHz Wi-Fi, move closer to the router, stop big downloads/streams, try **USB tethering**, or lower the send rate to 100 Hz. Turn off the phone's battery saver. |
| Laptop IP changes every day | Give the laptop a fixed DHCP reservation in the router, or just use *Find laptop*. |

## Virtual controller

| Symptom | Fix |
|---|---|
| Dashboard: *No virtual controller* | Install ViGEmBus 1.22.0 ([INSTALL-WINDOWS.md](INSTALL-WINDOWS.md)), then click **Reconnect controller**. |
| `joy.cpl` shows no *XBOX 360 For Windows* controller | Same as above; also try *Device Manager → Action → Scan for hardware changes*. |
| Two controllers listed | Another tool (DS4Windows, reWASD, Steam) is also creating a virtual pad, or a real pad is connected. Close or unplug it. |
| *PC HAS NO CONTROLLER* on the phone | The receiver is running without a driver. See the first row. |

## Steering

| Symptom | Fix |
|---|---|
| Car drifts left/right with the wheel centred | Hold **CENTRE** on the driving screen. If it drifts again within minutes, you're probably holding the wheel too flat: keep it upright (see [CARDBOARD-WHEEL.md](CARDBOARD-WHEEL.md)). |
| Steering reversed | *Invert steering direction* on the phone. (Running **Calibrate axis** also fixes it, because the axis is defined by your right turn.) |
| Angle changes when tilting (not turning) the wheel | Run **Calibrate axis** ([CALIBRATION.md](CALIBRATION.md)). |
| Jittery | Raise *Smoothing* to 40-50 %, add 1-2 % dead zone, make sure the phone is tight in the mount. |
| Laggy / floaty | Lower *Smoothing*, choose the *Sensitive* preset, check latency. Set F1 25's controller steering linearity/saturation to 0. |
| Too twitchy at speed | Raise *Response curve* to 1.5-2.0 or increase *Full lock* to 110-130°. |
| *Gravity only (no gyroscope)* | The phone has no gyroscope. Steering works from gravity alone: keep the wheel upright and smooth your movements. |
| Sample rate below 100 Hz | Battery saver on, or the phone limits sensor rates. Turn battery saver off. |

## Touch controls

| Symptom | Fix |
|---|---|
| Throttle cuts out while steering hard | Your thumb slid off the pedal (it releases 28 dp outside its edge, on purpose). Keep your thumb on the pedal, or rearrange your grip. |
| Double gear shifts | Each tap is exactly one shift; two quick taps are two shifts. If shifts happen without a tap, your palm may be touching the paddle area. |
| Notification shade opens while driving | Swipes from the screen edge are system gestures. The app releases all inputs when the shade opens. Keep thumbs slightly away from the very top edge. |
| No haptics | *Vibrate on paddle press* switch, and the phone's system "touch vibration" setting. |

## In game (F1 25)

See [F1-25-SETUP.md](F1-25-SETUP.md). The usual issues: another controller is active, a
duplicate button binding, or Steam Input remapping the pad (Steam copies only).
