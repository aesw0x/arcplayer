# arc player

A video player for Android: a local library on one tab, and a Cloud tab that streams videos
straight out of Telegram chats without downloading them first. Kotlin and Jetpack Compose on the
UI, libVLC for decoding, TDLib for Telegram. This is a personal build; it is not published to any
store.

## How it works

**Local library.** Folders are browsed as a drill-in grid with a hand-rolled two-stop scroll engine
and a frosted-glass treatment that follows the content.

**Cloud playback.** The Cloud tab authenticates against Telegram through TDLib and hands the player
the library's own temp file, read as an ordinary file. TDLib fetches one contiguous byte window per
file, so the app moves that window ahead of the play head, and a committed seek cancels the running
fetch and re-aims it at the target.

**Seeking** uses the film's own Matroska cue table rather than a constant-bitrate guess, and the
table is read *before* the file is handed to libVLC — libVLC builds its Matroska index once, at
open, and silently drops any later seek it cannot answer.

**Seek feedback.** The scrubber holds at the drag target for the whole fetch, the last frame stays
on screen under a light scrim with a spinner, and a live `N% cached · 766 KB/s` line says what the
download is doing.

## Requirements

- JDK 17, Android SDK with platform 36 installed
- Gradle comes from the committed wrapper (8.14.5); no local Gradle install needed
- `minSdk` 31, ABIs `arm64-v8a` and `x86_64`

## Credentials (required, and never committed)

The Telegram API identity lives in `local.properties`, which is git-ignored:

```properties
sdk.dir=C:\\Users\\you\\AppData\\Local\\Android\\Sdk
tg.api.id=1234567
tg.api.hash=0123456789abcdef0123456789abcdef
```

Get the id and hash from https://my.telegram.org. Both are read into `BuildConfig` at configuration
time. If they are missing the build still succeeds — `tg.api.id` falls back to `0` — and the Cloud
tab opens on a "missing credentials" screen rather than crashing.

Any CI must restore `local.properties` from secrets before running a Gradle task, and must never
print it.

## Build, test, install

```bash
./gradlew :app:testDebugUnitTest     # the pure cores: window arithmetic, cue index, auth, scroll gate
./gradlew :app:assembleDebug
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

## Decisions worth knowing before you change things

- **Release is signed with the debug keystore** on purpose. It is what lets `adb install -r` keep
  working over the air on a personal device, and it means updates never need an uninstall. Do not
  distribute an APK built that way.
- **Debug logging is stripped from release builds** by `app/proguard-rules.pro`
  (`-assumenosideeffects` on `Log.v/d/i`). Tags `ArcCloud` and `ArcFrameRate` are the ones to watch
  when diagnosing a seek; debug installs still log normally.
- **The Telegram session is not backed up.** TDLib keeps its database and downloaded files under
  `filesDir/telegram`, which `res/xml/data_extraction_rules.xml` excludes from both cloud backup and
  device transfer. Saved playback positions live in shared preferences and still back up.
- **A cold file costs up to 15 seconds before the first frame**, because the cue table is fetched
  during the open wait. That is the price of seeks working at all.
- **Throughput is the floor of the feature, not a bug in it.** Measured about 350 KB/s on a phone
  and 1.4–3.3 MB/s on an emulator, so a first long seek into a 3 GB file can legitimately take
  30–60 seconds. Budgets are 45 s for the cue table and 60 s per seek-window attempt. Shrinking
  those timeouts does not make seeks faster, it makes them fail.
- The byte-window idea was studied in TMPlayer's published source; this repository contains no
  GPL code. See `THIRD_PARTY_NOTICES.md`.

## Licensing

This app's own source is under Apache License 2.0 — see `LICENSE`. Third-party code it links to or
vendors is listed in `THIRD_PARTY_NOTICES.md`, including one caveat about libVLC to settle before
redistributing an APK.
