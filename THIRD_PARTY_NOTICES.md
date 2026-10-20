# Third-party code and licenses

arc player is Apache License 2.0 for its own source (see `LICENSE`). This file lists what it links
to, vendors, and took ideas from, and how each line was checked.

## Dependencies

| Component | Version | License | How this was checked |
|---|---|---|---|
| libVLC for Android (`org.videolan.android:libvlc-all`) | 3.6.0 | POM declares **LGPL-2.1** | Read from the artifact POM in the local Gradle cache |
| TDLib (`com.github.tdlibx:td`, via JitPack) | 1.8.56 | **Apache-2.0** | Read from the artifact POM in the local Gradle cache |
| Haze (`dev.chrisbanes.haze:haze`) | 0.7.3 | Apache-2.0 per upstream repository | **Not** verified from the artifact — the cached AAR carries no licence metadata |
| AndroidX, Compose BOM, Kotlin, kotlinx.coroutines | see `app/build.gradle.kts` | Apache-2.0 | Standard |
| JUnit | 4.13.2 | EPL 1.0 | Test-only, never ships |
| Google Fonts (`ui-text-google-fonts`) | via Compose BOM | n/a | Typefaces are fetched at runtime and stay licensed by their own designers |

## Vendored source

`app/src/main/java/com/aeswox/arcplayer/backdrop/` — 30 files vendored from **Kyant0/backdrop
v2.0.0** (`io.github.kyant0:backdrop`), Apache License 2.0, Copyright 2025 Kyant0. Every file keeps
its upstream header. Modified here: package renamed, the KMP `expect`/`actual` declarations merged
into one Android source set, and a backdrop resolution scale added for cheaper effect rendering.

Apache-2.0 requires the license text to travel with those files, which is why `LICENSE` is committed
at the repository root.

## The libVLC caveat

`libvlc-all`'s own POM declares LGPL-2.1, and this repository's Apache-2.0 grant is compatible with
that. But VLC's upstream sources are GPL-family and the bundle ships third-party codecs, and the AAR
contains no license file of its own (checked by unpacking it). Building and using this app on your
own device is unremarkable; **redistributing an APK needs the question settled against VideoLAN's
own terms first**, which is also why libVLC players historically cannot be listed on Google Play.

## TDLib and Telegram

TDLib is Apache-2.0. Two obligations that are not about code:

- The API id and hash come from https://my.telegram.org and identify *your* client. They are kept in
  git-ignored `local.properties` and are never committed.
- Telegram asks that third-party apps not present themselves as Telegram or use its branding. This
  app calls itself arc player.

## Catalogue data, not code

`metadata/` asks three services what a file actually is. None of them contribute code, but each has
terms that are not about licensing:

- **TMDB** supplies film and series titles and all live-action artwork, under a v3 API key registered
  to this app. Their terms require attribution, which is on the Menu tab under "Data Sources", and
  cover non-commercial use only. The key is read from git-ignored `local.properties` at build time
  and compiled into `BuildConfig`, so it **is visible to anyone who unpacks the APK** — accepted here
  because the key grants read-only access to public catalogue data and carries no billing.
- **AniList** supplies series titles and anime artwork over a keyless GraphQL endpoint.
- **Wikipedia** fills in titles the other two miss, via the MediaWiki search API. It withholds
  fair-use film posters from its thumbnail endpoint, so a Wikipedia-sourced card has a correct name
  and no artwork by design.

## Provenance of the streaming design

The shape of Telegram byte-window streaming was read in TMPlayer's published source (GPL-3.0) while
this feature was being designed, as documentation of what TDLib does — not as code to copy. The
arithmetic in `stream/StreamWindow.kt` and the TDLib window planner in `cloud/` were then written
here against TDLib's own API and this app's tests. No GPL-3.0 source, and no code derived from
TMPlayer, is included; that was verified on 2026-10-17 by comparing every non-trivial line of this
repository against TMPlayer's tree and finding no distinctive overlap.

No code from NextPlayer or any other GPL player is present; this app decodes with libVLC and does
not use Media3.
