# Chimy's Mood 🐱

A fully offline Android mood companion. No server, no account, no network
permission at all — every message lives in the app itself. Chimy checks in
3 times a day (morning, evening, night) as a "Dynamic Island" style pill
at the top of the screen, and shows supportive messages from Chimy's
current mood pool the rest of the time.

## How it works

- `MoodForegroundService` arms the 3x/day check-in schedule and drives the
  overlay — no server connection of any kind.
- `MoodAlarmScheduler` + `MoodAskReceiver` fire the check-in at the
  scheduled times (default 9:00 / 19:00 / 22:30 — edit `SLOT_TIMES` in
  `MoodAlarmScheduler.kt`).
- Answering a check-in picks a message from that mood's pool
  (`MoodMessages.kt`) and shows it; `RandomMessageScheduler` then queues a
  few more random pushes from the same pool before the next check-in.
- `MessagePicker` uses a shuffle-bag per mood so a line won't repeat until
  the whole pool (100+ lines per mood) has cycled once.
- `MoodStore` keeps a local mood-history log; `MoodInsights` reads it for
  a streak (3+ of the same mood in a row triggers one extra follow-up
  message about a minute later) — the one bit of local "learning," done
  entirely on-device with no external model.

## The message pools

All content lives in `data/MoodMessages.kt`:
- **Too Bad** — 150+ lines, the largest pool, all comfort/reassurance
- **Normal** — 115+ lines
- **Good** — 130+ lines

Nothing is downloaded or generated remotely — this is the full, final set
you can edit directly in that file whenever you want to add, remove, or
tweak a line.

## The island interaction

- A new message appears as a **small icon only** (cat, idle animation) —
  nothing opens automatically.
- **Tap the icon** → it morphs smoothly into a small black fish (swimming
  animation) and expands to reveal the message. The overlay grows to
  cover the screen so it can detect a tap anywhere else.
- **Tap anywhere else on screen** → the island disappears entirely. The
  next scheduled message (random push, or the next 3x/day check-in) will
  bring it back on its own.
- If a message is left untouched for ~20 seconds, it quietly disappears
  rather than sitting on screen indefinitely.
- The mood-ask check-in (with its Too Bad / Normal / Good buttons) is
  exempt from the tap-outside-to-dismiss behavior — it stays up until
  answered, since it's waiting on you rather than just informing you.

## App icon

The launcher icon is your provided artwork directly
(`res/mipmap-xxxhdpi/ic_launcher.png` / `ic_launcher_round.png`) — swap in
a new file with the same name any time you want to update it.

## Permissions

Only what's needed for a fully local app: drawing the overlay
(`SYSTEM_ALERT_WINDOW`), running the foreground service, posting the
status notification, exact alarms for on-time check-ins, and
`RECEIVE_BOOT_COMPLETED` so the schedule survives a restart. No `INTERNET`
permission — the app can't reach the network even if it wanted to.

## Opening the project

1. Open this folder in Android Studio (Koala or newer), or push it to
   GitHub — `.github/workflows/build-apk.yml` builds a debug APK and
   publishes it as a GitHub Release automatically on every push.
2. Run on a device/emulator running Android 8.0+ (minSdk 26).
3. On first launch: grant notification permission (Android 13+), the
   "draw over other apps" overlay permission, and — on Android 12+ — the
   "Alarms & reminders" permission for on-time check-ins.

## Project layout

```
app/src/main/java/com/seyfbk/dynamicnotify/
├── MainActivity.kt                  # permissions + test buttons, no server UI
├── data/
│   ├── Mood.kt                      # Mood + DaySlot (morning/evening/night) enums
│   ├── MoodMessages.kt              # all message content (100+ per mood)
│   └── MoodStore.kt                 # local mood history + shuffle-bag state
├── engine/
│   ├── MessagePicker.kt             # shuffle-bag message selection
│   └── MoodInsights.kt              # local streak detection
├── scheduler/
│   ├── MoodAlarmScheduler.kt        # 3x/day check-in scheduling
│   ├── MoodAskReceiver.kt           # fires the check-in, re-arms itself
│   ├── RandomMessageReceiver.kt     # fires an in-between random message
│   └── BootReceiver.kt              # re-arms the schedule after a reboot
├── overlay/
│   ├── DynamicIslandOverlay.kt      # WindowManager overlay host + tap logic
│   ├── IslandPill.kt                # pill UI (message + mood-ask variants)
│   └── CritterIcon.kt               # animated cat/fish with morph transition
└── service/
    └── MoodForegroundService.kt     # ties it all together, no network
```

## Notes / things worth deciding next

- Fish odds on show (`CritterKind.random`) are currently unused now that
  the fish appears specifically on tap rather than randomly — say the word
  if you'd like an *additional* random chance of a fish even before any
  tap.
- The 20-second auto-hide timeout for an untouched message, the streak
  threshold (3), and the check-in times are all just constants — trivial
  to retune.
- Since some OEMs (MIUI, ColorOS, etc.) restrict background overlays and
  foreground services aggressively, you may need to whitelist the app in
  battery settings for reliable delivery.
