# Third-party code and licenses

arc player is Apache License 2.0 for its own source (see `LICENSE`). This file lists what it links
to, vendors, and took ideas from, and how each line was checked.

## Dependencies

| Component | Version | License | How this was checked |
|---|---|---|---|
| libVLC for Android (`org.videolan.android:libvlc-all`) | 3.6.0 | POM declares **LGPL-2.1** | Read from the artifact POM in the local Gradle cache |
| TDLib (`com.github.tdlibx:td`, via JitPack) | 1.8.56 | **Apache-2.0** | Read from the artifact POM in the local Gradle cache |
| NanoHTTPD (`org.nanohttpd:nanohttpd`) | 2.3.1 | **BSD-3-Clause** | Read from the project POM and from `LICENSE.txt` inside the artifact jar; reproduced below |
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

## NanoHTTPD notice (BSD-3-Clause)

The LAN TV server links `org.nanohttpd:nanohttpd:2.3.1` as a binary dependency, and BSD-3-Clause asks
that a binary redistribution reproduce its copyright notice, its conditions and its disclaimer. That
text follows, copied from the `LICENSE.txt` carried inside the artifact.

```
Copyright (c) 2012 - 2016, nanohttpd

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the nanohttpd nor the names of its contributors
   may be used to endorse or promote products derived from this software without
   specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
OF THE POSSIBILITY OF SUCH DAMAGE.
```

None of nanohttpd's source is vendored into this repository; the only thing under `tvserver/` written
here is the code that calls it.

## libVLC distribution considerations

The `libvlc-all` 3.6.0 POM declares LGPL-2.1. This repository's Apache-2.0 license covers Arc Player's
own source; it does not replace the licenses of libVLC, native codecs, or other bundled components.

Distributors must review the exact bundled libraries and comply with their applicable terms,
including license notices, corresponding source availability, and LGPL modification/relinking
requirements where applicable. Producing a signed APK is not, by itself, a license-compliance audit.
Whether an app can be distributed through a particular store is a separate question; using libVLC
does not establish a blanket Google Play prohibition.

## TDLib and Telegram

TDLib is Apache-2.0. Two obligations that are not about code:

- The API id and hash come from https://my.telegram.org and identify *your* client. They are kept in
  git-ignored `local.properties` and are never committed.
- Telegram asks that third-party apps not present themselves as Telegram or use its branding. This
  app calls itself arc player.

## Catalogue data, not code

`metadata/` queries catalogue services to identify media. They do not contribute application code,
but their data, artwork, and service access remain subject to their own terms:

- **TMDB** supplies film and series titles and artwork, under a v3 API key registered
  to this app. Their terms require attribution, which is on the Menu tab under "Data Sources", and
  cover non-commercial use only. The key is read from git-ignored `local.properties` at build time
  and compiled into `BuildConfig`, so it **is visible to anyone who unpacks the APK** — accepted here
  because the key grants read-only access to public catalogue data and carries no billing.
- **AniList** supplies series titles and anime artwork over a keyless GraphQL endpoint.
- **IMDb** provides an additional title and artwork lookup path. Its content and access are subject
  to IMDb's terms; the application does not own or relicense that catalogue.
- **Wikipedia** provides another title lookup path through the MediaWiki search API. Thumbnail
  availability varies; individual images retain their own licenses and usage restrictions.

## Provenance of the streaming design

The shape of Telegram byte-window streaming was read in TMPlayer's published source (GPL-3.0) while
this feature was being designed, as documentation of what TDLib does — not as code to copy. The
arithmetic in `stream/StreamWindow.kt` and the TDLib window planner in `cloud/` were then written
here against TDLib's own API and this app's tests. No GPL-3.0 source, and no code derived from
TMPlayer, is included; that was verified on 2026-10-17 by comparing every non-trivial line of this
repository against TMPlayer's tree and finding no distinctive overlap.

No code from NextPlayer or any other GPL player is present; this app decodes with libVLC and does
not use Media3.
