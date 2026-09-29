# Lina Mood 🐱

A fully offline Android companion: a little cat that lives on your screen,
checks in on Lina's mood every 2 hours, and pops in with a mood-flavored
message every 30 minutes in between. No server, no account, no network
permission at all — every message lives in the app itself.

## The cat

- Drawn entirely in code (`overlay/CatCharacter.kt`) — no image assets — so
  it can be animated: ears wiggle, it blinks, its tail wags at a speed and
  amplitude that reflects the current mood (fast and big when happy, slow
  and low when sad).
- Idles between random actions — jump, spin, play with a ball, look
  around, stretch, nap, or a quiet sigh with a tear — weighted by mood
  (`pickAction` in `CatCharacter.kt`): mostly jumping/playing when Lina's
  mood is Good, mostly napping/sighing when it's Too Bad.
- **Always on screen** once enabled — tapping anywhere else on the screen
  never dismisses it. The only way to remove it is the switch in the app
  (`CatPrefs.enabled`).
- **Draggable** — touch and drag the cat anywhere on screen; its position
  is remembered (`CatPrefs.posX/posY`) across restarts.
- A speech bubble docks above it for messages and the mood check-in
  (`overlay/Bubbles.kt`); tapping a message bubble dismisses it early, or
  it clears itself automatically after enough time to read it.
- If the cat is switched off, or the overlay permission isn't granted,
  everything falls back to plain Android notifications instead
  (`service/NotificationHelper.kt`) — the mood check-ins and messages
  still work, just without the on-screen cat.

## Schedule

`scheduler/TickScheduler.kt` runs one alarm chain, every 30 minutes,
from 8:00 to 22:30:
- **Every 2 hours, on the hour** (8:00, 10:00, 12:00 ... 22:00) → a mood
  check-in (with a greeting that varies by time of day —
  `data/Mood.kt`'s `DaySlot`)
- **Every other half-hour mark** → a message from the current mood's pool
- About 1 in 5 of those messages is swapped for a "just for fun" line
  instead (`data/FunMessages.kt`) — silly, affectionate, no particular
  mood attached

## The message pools

All content lives in `data/MoodMessages.kt` and `data/FunMessages.kt`:
- **Too Bad** — 150+ lines, comfort and reassurance
- **Normal** — 115+ lines
- **Good** — 130+ lines
- **Fun** — 60+ playful "lina 🥺 your cat loves you" style lines

`MessagePicker` uses a shuffle-bag per pool so a line won't repeat until
the whole pool has cycled once. `MoodStore` keeps a local mood-history
log; `MoodInsights` reads it for a streak (3+ of the same mood in a row
triggers one extra follow-up message about a minute later) — entirely
on-device, no external model.

## Permissions

Overlay (`SYSTEM_ALERT_WINDOW`) for the cat, foreground service +
notification for the always-running mood engine, exact alarms for on-time
ticks, and `RECEIVE_BOOT_COMPLETED` so the schedule survives a restart.
No `INTERNET` permission — the app can't reach the network even if it
wanted to.

## Opening the project

1. Open this folder in Android Studio (Koala or newer), or push it to
   GitHub — `.github/workflows/build-apk.yml` builds a debug APK and
   publishes it as a GitHub Release automatically on every push.
2. Run on a device/emulator running Android 8.0+ (minSdk 26).
3. On first launch: grant notification permission (Android 13+), the
   "draw over other apps" overlay permission (for the cat), and — on
   Android 12+ — the "Alarms & reminders" permission for on-time ticks.

## Project layout

```
app/src/main/java/com/seyfbk/dynamicnotify/
├── MainActivity.kt                  # permissions, cat on/off switch, test buttons
├── data/
│   ├── Mood.kt                      # Mood + DaySlot (time-of-day greetings)
│   ├── CatPrefs.kt                  # cat on/off + remembered screen position
│   ├── MoodMessages.kt              # mood message pools (100+ each)
│   ├── FunMessages.kt               # just-for-fun lines
│   └── MoodStore.kt                 # local mood history + shuffle-bag state
├── engine/
│   ├── MessagePicker.kt             # shuffle-bag message selection
│   └── MoodInsights.kt              # local streak detection
├── scheduler/
│   ├── TickScheduler.kt             # 30-min ticks / 2h check-ins / quiet hours
│   ├── TickReceiver.kt              # handles a tick, re-arms the next one
│   └── BootReceiver.kt              # re-arms the schedule after a reboot
├── overlay/
│   ├── CatCharacter.kt              # the hand-drawn, mood-driven animated cat
│   ├── CatOverlay.kt                # draggable overlay window + bubble docking
│   └── Bubbles.kt                   # message + mood-ask speech bubble UI
└── service/
    ├── MoodForegroundService.kt     # ties it all together, no network
    ├── NotificationHelper.kt        # fallback notifications when the cat is off
    └── MoodPickReceiver.kt          # handles a mood button tap from a notification
```

## Notes / things worth deciding next

- Check-in cadence (2h), message cadence (30 min), quiet hours (23:00–8:00),
  the fun-message chance (20%), and the streak threshold (3) are all just
  constants in `TickScheduler.kt` / `MoodForegroundService.kt` — trivial
  to retune.
- Since some OEMs (MIUI, ColorOS, etc.) restrict background overlays and
  foreground services aggressively, you may need to whitelist the app in
  battery settings for reliable delivery.
