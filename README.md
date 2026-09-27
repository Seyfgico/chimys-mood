# Chimy's Mood 🐱

An Android client for [push-notifications-api](https://github.com/viktorholk/push-notifications-api)
that shows incoming pushes as a "Dynamic Island" style pill overlay at the
top of the screen (like iPhone 14 Pro), with a little cat leading the pill.

It speaks the exact same server API as the original project — you can point
it at the same Node/Express server, no server changes needed.

## How it works

- `MainActivity` — enter your server address, tap **Register & Connect**.
  This calls `POST /register`, saves the returned token, and starts the
  foreground service.
- `SseForegroundService` — holds a persistent connection to
  `GET /events?token=...` (Server-Sent Events), same as the original app.
- `DynamicIslandOverlay` — instead of posting a normal Android notification,
  every incoming push is drawn as a floating pill via `WindowManager`
  (`TYPE_APPLICATION_OVERLAY`), anchored top-center, an animated cat (or
  occasionally a little fish) always at the leading edge, expanding to show
  title/message, auto-collapsing after ~4.5s. Tapping the expanded pill
  opens the notification's `url` if present.

## Opening the project

1. Open this folder in Android Studio (Koala or newer).
2. Let Gradle sync — it will pull Compose, OkHttp (+ okhttp-sse) via Google/Maven Central.
3. Run on a device/emulator running Android 8.0+ (minSdk 26).
4. On first launch: grant notification permission (Android 13+) and the
   "draw over other apps" overlay permission, then register with your server.

## Project layout

```
app/src/main/java/com/seyfbk/dynamicnotify/
├── MainActivity.kt              # connect screen
├── data/
│   ├── Prefs.kt                 # stores server URL + device token
│   ├── PushNotification.kt      # SSE payload model
│   └── NotificationApiClient.kt # /register + /events (SSE)
├── service/
│   └── SseForegroundService.kt  # keeps SSE alive, feeds the overlay
└── overlay/
    ├── DynamicIslandOverlay.kt  # WindowManager overlay host
    └── IslandPill.kt            # the pill's Compose UI (cat + text)
```

## Mood check-ins (Chimy 🐱)

Three times a day (default 9:00, 19:00, 22:30 — tweak `SLOT_TIMES` in
`MoodAlarmScheduler`), the island itself becomes an interactive check-in:
the cat leads a greeting with three buttons — **Too Bad / Normal / Good**.

- Answering immediately shows one message from that mood's pool.
- Between now and the *next* scheduled check-in, 2–4 more messages from the
  same pool pop up at random times (`RandomMessageScheduler`).
- All of this works fully offline — the built-in phrase lists in
  `MoodMessages.kt` are the floor. See "Mood history & learning" below for
  how the pool grows over time when you're online.
- Messages never repeat until the whole pool (built-in + synced) has cycled
  once (`MessagePicker`'s shuffle-bag).
- Android 12+ needs the "Alarms & reminders" permission for the check-ins
  to fire exactly on time; the app prompts for it if missing.

## Mood history & "learning"

- Every mood pick is logged locally (`MoodStore.addHistoryEntry`, capped at
  the last 300 entries) — nothing is lost when the service restarts.
- **On-device:** `MoodInsights` reads that log for a current streak (e.g.
  3+ "Too Bad" picks in a row) and recent 30-day counts. A streak triggers
  one extra, more pointed follow-up message about a minute after the
  regular one. This is honest pattern-matching, not a trained model —
  there's no real ML happening on the phone.
- **Server-side (optional):** whenever there's *any* internet connection
  (WiFi or mobile data — `MessageSyncWorker` now uses `NetworkType.CONNECTED`,
  not `UNMETERED`), the app POSTs the recent mood counts to your sync URL
  and merges back new messages:
  ```json
  // sent
  { "recentMoodCounts": { "TOO_BAD": 2, "NORMAL": 5, "GOOD": 9 } }
  // expected back
  { "too_bad": ["..."], "normal": ["..."], "good": ["..."] }
  ```
  This fires once right after every mood pick, plus a ~12h periodic check.
  Actually *generating* new phrasing from those counts (e.g. running them
  through an LLM) is server work you'd implement behind that URL — this
  worker only handles the upload/merge plumbing, and the app is fully
  usable without it ever succeeding.

## App icon

The launcher icon is your provided artwork directly
(`res/mipmap-xxxhdpi/ic_launcher.png` / `ic_launcher_round.png`), not a
recreation — swap in a new file with the same name any time you want to
update it. It's a plain (non-adaptive) launcher icon, since the image
already has its rounded-square framing baked in; most launchers will
apply their own mask on top of it as usual for this icon style.

## Animated cat & fish

- The leading icon is now hand-drawn on Canvas (`CritterIcon.kt`), not a
  static emoji, so it can actually animate: the cat's ears wiggle and it
  blinks on a natural-feeling cycle; a small black fish (which stands in
  for the cat about 15% of the time — see `CritterKind.random()`) sways
  side to side with a flicking tail, like it's swimming in place.
- The swap between cat/fish is re-rolled every time the pill is shown
  (message or mood-ask), so it feels a little different each time.

## Notes / things worth deciding next

- Swipe-to-dismiss and a real notification queue (currently only the
  latest message shows; a rapid-fire burst will just replace itself) are
  easy follow-ups if you want them.
- The fish-vs-cat odds (`CritterKind.random(fishChance = 0.15)`) and the
  streak threshold for the bonus follow-up (currently 3) are just constants
  — trivial to retune.
- Since some OEMs (MIUI, ColorOS, etc.) restrict background overlays and
  foreground services aggressively, you may need to whitelist the app in
  battery settings for reliable delivery.
- The sync endpoint contract (counts in, messages out) is defined but there's
  no reference server implementation here — say the word if you want a small
  example Node endpoint for it.
