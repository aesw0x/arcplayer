# Arc Player — Privacy Policy

Effective 22 November 2026. Applies to the Arc Player Android app (`com.aeswox.arcplayer`),
both the GitHub-distributed APK and the Google Play build.

## The short version

Arc Player is a video player for files you already own: videos on this device and videos in
your own Telegram cloud. The developer operates **no server**. There are **no ads, no
analytics, no crash-reporting SDKs, and no tracking**. Everything the app remembers stays on
your device, and the only network traffic is to the services listed below, each of which you
can see exactly what it is for.

## What stays on your device

- **Telegram sign-in session.** If you connect Telegram, the session keys are stored in the
  app's private storage and are used only to talk to Telegram. They are excluded from Android
  cloud backup and device-to-device transfer.
- **Watch progress and preferences.** Resume positions, favorites, shelf layout and settings
  are stored locally in app preferences.
- **Offline copies.** Videos you save offline are ordinary files in the public folder
  `Movies/Arc Player/`, readable by your gallery and any other player. Delete them from the
  in-app Downloads page or any file manager.

## What leaves your device, and to whom

- **Telegram (telegram.org).** Signing in sends your phone number and the one-time code to
  Telegram's own servers, and streaming or downloading your cloud videos transfers those
  files between Telegram and your device. This is Telegram's service under
  [Telegram's privacy policy](https://telegram.org/privacy); Arc Player adds nothing to it.
  Connecting Telegram is optional — local playback works without it.
- **TMDB (themoviedb.org).** To show posters, titles and descriptions, the app sends movie
  and show names and your search terms to TMDB's metadata API. No account identifier, device
  identifier or location is included. See [TMDB's privacy policy](https://www.themoviedb.org/privacy-policy).
- **GitHub (github.com).** Only in the GitHub-distributed build, the app asks GitHub's public
  release API whether a newer version exists and, if you tap update, downloads the APK from
  GitHub. The Google Play build contains no updater at all; Play delivers updates.
- **Your own TV, on your local network.** If you pair an Android TV, the phone serves your
  local videos to that TV over your Wi-Fi. Nothing goes to the internet or to the developer.

No other data is collected, sold, or shared. The developer never receives your phone number,
your videos, your watch history, or any identifier.

## Permissions and why they exist

- **Videos/media access** — to list and play your own library, the core function of the app.
- **Notifications** — download progress and a note when a film is ready offline. Optional.
- **Foreground service (data sync) and wake lock** — so a download or a TV session you started
  survives the screen going off. Both show an ongoing notification and stop when finished.
- **Install packages** — GitHub build only, to hand a downloaded update to the system
  installer after you tap Install. Absent from the Google Play build.

## Deleting your data

All app-stored data (Telegram session, watch progress, preferences) is deleted by
**Android Settings → Apps → Arc Player → Storage → Clear storage**, or by uninstalling.
Offline copies are deleted as described above. Because the developer keeps no server-side
data, there is nothing further to request from us. Data held by Telegram (account, phone
number, cloud videos) is managed and deleted in Telegram's own app and account settings.

## Children

Arc Player is not directed at children and collects no data from anyone by design; it plays
only media the user already possesses.

## Changes and contact

The public policy is published at
https://aesw0x.github.io/app-privacy-policies/arcplayer/. If it changes, the new version is
published there with a new effective date. Questions or requests: open an issue at
[github.com/aesw0x/app-privacy-policies](https://github.com/aesw0x/app-privacy-policies/issues).
Do not include phone numbers, login codes, passwords, session files, or private videos in a
public issue.