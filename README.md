<div align="center">

# Arc Player

### Your collection. A quieter way to watch.

A cinematic Android player that brings local movies, series, and your personal Telegram library into one artwork-first collection.

[Download](https://github.com/aesw0x/arcplayer/releases/latest) · [Build from source](#build-from-source) · [Release automation](#release-automation) · [Report an issue](https://github.com/aesw0x/arcplayer/issues)

**Android 12+ · Kotlin & Jetpack Compose · Powered by libVLC**

[![Release APKs](https://github.com/aesw0x/arcplayer/actions/workflows/release.yml/badge.svg)](https://github.com/aesw0x/arcplayer/actions/workflows/release.yml)

</div>

---

## About

Arc Player is built around the things you actually want to watch—not a wall of filenames. It turns recognised films and episodes into a collection with posters, title artwork, details, and a place to pick up where you left off.

Its **Frosted Jelly** interface combines deep-black surfaces, floating glass controls, artwork parallax, spring motion, and tactile feedback. Local files and Telegram-backed media share the same viewing experience, while downloads let you keep selected titles available offline.

Arc Player is an independent project. It does not provide movies, subscriptions, public streaming catalogues, or access to anyone else's library. Bring media you own or have permission to access.

## Highlights

| | What you get |
|---|---|
| **An artwork-first collection** | Movie and series grouping, title details, posters, cast information, search, and Continue Watching. |
| **Local and cloud, together** | Recognised device media and files from your configured Telegram shelf, with multiple copies grouped under a title. |
| **Capable playback** | libVLC decoding, hardware acceleration, audio-track selection, subtitle tracks and external subtitle files. |
| **A player that gets out of the way** | Immersive controls, gesture seeking, fit/crop/stretch modes, rotation controls, and picture-in-picture. |
| **Subtitle adjustments** | Timing, text size, and vertical position controls, with saved size and position preferences. |
| **Offline viewing** | Keep selected cloud titles on the device, follow download progress, and browse download history. |
| **Thoughtful continuity** | Saved playback positions, copy-aware resume, and next-episode playback. |
| **Purposeful design** | Dark-only Frosted Jelly styling, frosted panels, animated transitions, and haptic feedback. |

## Download and install

Get signed APKs from [GitHub Releases](https://github.com/aesw0x/arcplayer/releases). Arc Player requires **Android 12 / API 31 or newer**.

| APK | Choose this for |
|---|---|
| `ArcPlayer-1.0.0-arm64-v8a.apk` | Most current Android phones and tablets. **Recommended.** |
| `ArcPlayer-1.0.0-x86_64.apk` | Compatible Intel/AMD Android devices and x86_64 emulators. |
| `ArcPlayer-1.0.0-universal.apk` | Both supported architectures in one larger download. |

The version portion of each filename changes with each release. These builds do not support 32-bit ARM or 32-bit x86. The universal APK contains the two supported 64-bit architectures, not every Android architecture.

1. Download the APK for your device.
2. Allow installation from the browser or file manager when Android asks.
3. Install Arc Player and grant access to the videos you want it to see.
4. Add recognised movies or episodes to your device, or connect your Telegram library inside the app.

Each release includes `SHA256SUMS.txt` so you can verify download integrity:

```bash
sha256sum --check SHA256SUMS.txt --ignore-missing
```

**Updating:** releases signed with the same key can update an existing release installation. An older **debug-signed** development build cannot be updated with the release-signed APK. Moving between them requires removing the old installation, which can erase app settings, sign-in state, and app-private data. Back up what you need first; do not uninstall casually.

## Using your library

### On-device movies and episodes

Put your media in a normal media folder such as `Movies/` and give it a recognisable title, ideally with a year for films or season/episode numbers for series:

```text
Movies/
  Example Film (2024).mkv
  Example Series S01E01.mkv
  Example Series S01E02.mkv
```

Arc Player is a **collection browser, not a general gallery**. Local candidates are filtered by folder and runtime before metadata lookup; the current minimum runtime is 20 minutes. Camera media, screen recordings, short clips, app-private files, and titles that do not pass collection recognition are intentionally excluded from the collection and device browsing screens.

Catalogue lookup needs a connection. Metadata and artwork are cached, but an unrecognised filename or unavailable provider can prevent a local item from appearing.

### Your personal Telegram shelf

Sign in through the Cloud area and follow the in-app shelf setup. Arc Player uses **TDLib**, Telegram's official client library, to access media available to your account.

Cloud playback starts from a growing local file instead of waiting for a whole download. The app moves Telegram's active download window ahead of playback and repositions it when you seek. Where available, Matroska cue information helps map a requested time to the correct byte range.

Your account session is created on your device. It is not included in the APK or the source repository. Network quality, Telegram throughput, media size, and indexing all affect startup and seek time; cloud playback is not an instant-streaming guarantee.

**Take care with removal actions:** removing a cloud item from the shelf can delete its underlying Telegram message. Removing a kept download is a separate action that removes the offline copy. Neither is necessary for ordinary playback.

### Keep a title offline

Use the download control on a title's detail page to keep a cloud copy. The Downloads page shows progress and history, and completed copies can be played without an active connection. Notification permission enables visible progress; the battery-optimisation setting can help on devices that aggressively stop background work.

## Playback notes

- Codec support depends on libVLC, the media file, and the device's decoder capabilities.
- Subtitle size and position changes currently rebuild the playback engine, so applying a change briefly interrupts playback. Resume accuracy after these changes is still being investigated.
- Subtitles are rendered by VLC with the video. Zooming or cropping can affect their placement; the controls are not a separate screen-anchored subtitle renderer.
- Some subtitle formats carry their own styling or bitmap content and may not respond like plain text subtitles.
- A cold cloud open or distant seek may need buffering and index preparation before playback can continue.

## Build from source

### Toolchain

| Requirement | Version |
|---|---|
| JDK | 17 recommended; used by GitHub Actions |
| Android SDK platform | 36 |
| Android SDK Build Tools | 35.0.0 |
| Minimum / target Android API | 31 / 35 |
| Android Gradle Plugin | 8.9.2 |
| Kotlin | 2.0.21 |
| Gradle | 8.14.5, through the checked-in wrapper |

Dependencies are resolved through Google Maven, Maven Central, and JitPack. The first build requires network access. The native VLC and TDLib libraries make downloads and build outputs larger than a typical UI-only application.

```bash
git clone https://github.com/aesw0x/arcplayer.git
cd arcplayer
```

Open the project in Android Studio, or build with the wrapper. On Windows, use `gradlew.bat` in place of `./gradlew` when running from PowerShell or Command Prompt.

### API credentials

Create a root-level `local.properties`, which is ignored by Git:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
tg.api.id=YOUR_TELEGRAM_API_ID
tg.api.hash=YOUR_TELEGRAM_API_HASH
tmdb.api.key=YOUR_TMDB_V3_API_KEY
```

Use your own Telegram application identity from [my.telegram.org](https://my.telegram.org) and a TMDB v3 API key for metadata and artwork. Android Studio can manage `sdk.dir`; on Windows, use forward slashes in that path.

The corresponding environment variables take precedence:

| Local property | Environment variable | Used for |
|---|---|---|
| `tg.api.id` | `TG_API_ID` | Telegram application ID |
| `tg.api.hash` | `TG_API_HASH` | Telegram application hash |
| `tmdb.api.key` | `TMDB_API_KEY` | TMDB metadata and artwork |

Debug builds can compile without these values, with reduced cloud/metadata functionality. **Release builds require all three**, so a distributable APK cannot silently ship without the configured Telegram identity or TMDB key.

These values are compiled into `BuildConfig` and are recoverable from an APK. Keeping them in GitHub Secrets protects the source repository and build logs; it does **not** make client-side credentials impossible to extract. They are application credentials, not your Telegram login session. Never embed account passwords, session databases, or personal access tokens.

### Debug builds and tests

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

Unit tests cover the pure logic behind stream windows, buffering, seek holds, metadata matching, grouping, playback ordering, downloads, and subtitle matching. They complement—not replace—playback testing on an Android device.

### Signed release builds

Release signing is deliberately separate from debug signing. There is no fallback to the debug keystore.

Create a private release keystore with JDK `keytool` if you are maintaining your own fork. For this repository's official releases, **reuse the existing release key**; do not generate a replacement for each version.

Create an ignored root-level `signing.properties`:

```properties
storeFile=signing/arcplayer-release.jks
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=arcplayer
keyPassword=YOUR_KEY_PASSWORD
```

Alternatively, use `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`. Relative keystore paths resolve from the repository root.

```bash
# Separate ARM64, x86_64, and universal signed APKs
./gradlew :app:assembleRelease -PsplitApks=true

# One signed APK containing both supported architectures
./gradlew :app:assembleRelease
```

Outputs are written to `app/build/outputs/apk/release/`. Release builds use R8 code shrinking, resource shrinking, and the private signing configuration.

**Back up the keystore and its passwords separately in secure storage.** Losing the key prevents normal updates to existing installations. Never commit the key, passwords, or a Base64 copy of the key. GitHub Secrets are build inputs, not a substitute for a recoverable backup.

## Release automation

The [Release APKs workflow](.github/workflows/release.yml) runs on pushes to **`main`**. It publishes a release only when `VERSION_NAME` changes in [`version.properties`](version.properties). The first push containing this file also qualifies. A push without a version change skips the release build.

### Required GitHub Actions secrets

Add these under **Settings → Secrets and variables → Actions → Repository secrets**:

| Secret | Contents |
|---|---|
| `TG_API_ID` | Telegram application ID |
| `TG_API_HASH` | Telegram application hash |
| `TMDB_API_KEY` | TMDB v3 API key |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded bytes of the release keystore |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Signing alias, `arcplayer` for the official key |
| `RELEASE_KEY_PASSWORD` | Signing-key password |

The repository must allow GitHub Actions to run, and its policies must permit the release job's `contents: write` permission. No personal access token is stored in the workflow: publishing uses GitHub's job-scoped token.

The workflow:

1. Validates the version and requires `VERSION_CODE` to increase with a version-name change.
2. Sets up Java 17 and the Android SDK.
3. Restores the signing key to temporary runner storage and passes API credentials through environment variables.
4. Runs unit tests and builds signed, shrunk release APKs.
5. Verifies APK signatures, versions, ABI contents, required native libraries, and embedded API credentials without printing credential values.
6. Creates versioned APK filenames and SHA-256 checksums.
7. Uploads Actions artifacts and publishes a GitHub Release tagged `v<VERSION_NAME>` at the build's exact commit.
8. Removes the temporary signing key, including after build failures.

Only the APKs and checksums are uploaded—not the keystore, signing properties, generated source, or the Gradle directory. Persistent build caching is not enabled in this workflow because build inputs contain credentials.

### Publishing the next version

Update **both** values in `version.properties`, for example:

```properties
VERSION_NAME=1.0.1
VERSION_CODE=2
```

Commit the change with the intended app changes and push to `main`. Use numeric `major.minor.patch` names and a monotonically increasing Android version code.

Existing version tags are not overwritten. To retry a failed build before a tag was created, rerun the failed Actions run or use **Run workflow** on `main`. Changing credentials alone does not publish a new APK; dispatch the unreleased version or increment the version for an already published release.

## Project structure

```text
app/src/main/java/com/aeswox/arcplayer/
  MainActivity.kt          Application UI, preferences, player integration
  CollectionScreens.kt     Collection browsing and title presentation
  Playable.kt              Shared local/cloud playback representation
  cloud/                   Telegram client, shelf, streaming and downloads
  metadata/                Filename parsing, catalogue matching and artwork
  playback/                Resume, seek, track and episode rules
  stream/                  Stream-window and buffering calculations
  subtitle/                Subtitle-file matching
  ui/                      Frosted Jelly components, motion, theme and haptics
  backdrop/                Vendored backdrop rendering implementation
app/src/test/              Unit tests
.github/workflows/         Automated signed releases
version.properties         App version name and Android version code
```

## Privacy and permissions

- **Media access:** discovers eligible videos on your device. Android 14+ can grant access to selected media only.
- **Internet:** connects to Telegram and metadata/artwork providers. Catalogue requests include title information inferred from filenames.
- **Notifications and foreground service:** report and maintain active downloads.
- **Wake lock and battery optimisation request:** support background transfers when permitted by the device.
- **Vibration:** provides interaction feedback.

Telegram's local session database and working files are excluded from Android backup and device transfer. Playback preferences may be backed up by Android. Artwork and catalogue metadata are cached locally. Provider requests remain subject to the respective services' privacy policies and terms.

## Contributing and reporting problems

Small, focused changes are welcome. Run the unit tests, check that a debug build compiles, and test playback/UI changes on a device before submitting a pull request.

For a useful bug report, include the Android version, device/CPU architecture, app version, whether playback is local or cloud-backed, and clear reproduction steps. Codec/container details help with playback issues. **Do not attach API credentials, Telegram sessions, private media, or unredacted logs containing secrets.**

## Credits and licensing

Arc Player's own source is licensed under the [Apache License 2.0](LICENSE). Bundled and linked components retain their own licenses; see [Third-party notices](THIRD_PARTY_NOTICES.md), including the libVLC distribution considerations.

Built with Jetpack Compose, libVLC, TDLib, Haze, and the vendored Kyant0 backdrop implementation. Metadata and artwork are resolved through TMDB, AniList, IMDb, and Wikipedia where available.

**This product uses the TMDB API but is not endorsed or certified by TMDB.** Arc Player is not affiliated with Telegram, VideoLAN, or the metadata providers. Media, posters, trademarks, and catalogue content remain the property of their respective owners.
