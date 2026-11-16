# Media size hint when auto-download is off

Status: implemented 2026-11-08 on `feat/media-size-hint` (plan:
`2026-11-08-media-size-hint-implementation.md`). Unit tests and the pre-push checks pass. Checked on
an Android emulator and a Pixel 9a: images, videos, audio, PDFs, gallery tiles, long-form covers,
game carts, encrypted and plain DM images, offline recovery and Tor. Checked on Desktop (macOS,
Video Playback = Never): video placeholders, the PDF card and the long-form cover show their size
and load on click; a game cart shows Play without a size and audio still auto-loads, because
Desktop note images and Desktop audio ignore the setting (see "Out of scope"), so no placeholder
is drawn to carry the label.

Where the build departs from the text below:
- `MediaSizeLabel` takes an optional `color`, so the label reads on the neutral cards.
- The Desktop audio card asks for a size only while media is held back, like the Android one.
- A video with no declared shape gets the 16:9 card its box falls back to, and a grid cell gets a
  card that fills it; both were found on the emulator.
- A held-back game cartridge shows the cart's Play button with the size under it, not the
  download icon under Play.

## Goal

A user asked to see how big a file is before tapping to download it, when the auto-download
setting holds media back. The hint goes on every placeholder that waits for a tap: images,
videos, audio and PDFs, on Android and Desktop. Website link previews are out of scope.

Success: a placeholder shows its type and size ("Video · 12.4 MB") without downloading the
file. When the size cannot be learned it shows the type alone, never a guess.

## Where the size comes from

The file itself must not be downloaded; preventing that is the setting's whole purpose. Three
sources remain, measured on 2026-11-08:

| Source | Coverage | Cost |
|---|---|---|
| Declared in the event: imeta `size` (NIP-92/94), or the `size` tag of an encrypted file message (kind 15) | imeta: 7 of 41 notes with media in a 500-note sample from nos.lol. Most of those notes had an imeta tag (36/41), but few carried `size`: Amethyst and Nostrich did, Primal and Damus did not. One relay, rough numbers. | None |
| The MP4 track probe that already runs | Every MP4-family video whose declaration doesn't say it is audio. `rememberMediaTracks` reads the file's box headers with Range requests before any tap, on Android and Desktop, to tell AAC-in-MP4 audio from video (`MediaTrackProbe.jvmAndroid.kt`). Its 206 answer carries `Content-Range: bytes a-b/total`. | None extra |
| HTTP `HEAD` → `Content-Length` | 10 of 12 media hosts sampled, including nostr.build, blossom.primal.net, blossom.band and nostrmedia. Blossom servers must answer `HEAD` (BUD-01). Flickr sent no length; Midjourney answered 403 with the length of its error page. | One request, no body |

**Decision:** declared size first; for media the track probe reads, its `Content-Range` total;
otherwise `HEAD`. Only a 2xx answer with a positive length counts. A `HEAD` still tells the
host the user's IP and which file they looked at. It goes through the same Tor routing as the
media itself, and no setting is added (decided with the user).

## What exists today

- `MediaUrlVideo.sizeBytes` holds the imeta `size` for video and audio. `RichTextParser`
  reads `size` only on the video branch (`RichTextParser.kt:105`), so images and PDFs drop it.
- The Android audio card already shows "MP3 · 4.8 MB" from `sizeBytes` (`PlayableAudioView`,
  `AudioCardInfo.fileFacts`). The Desktop `AudioPlayer` passes `sizeBytes = null`.
- Shared code reaches HTTP through `accountViewModel.httpClientBuilder`
  (`IRoleBasedHttpClientBuilder`). The model to copy is `MediaTrackProbe.kt`: a common
  `suspend` function that consults a cache, an `internal expect` that does the request (with a
  `jvmAndroid` actual and an iOS stub), and a 500-entry cache that composition can read
  (`MediaTrackProbeCache`).
- `ImageDownloader.waitUntilServed` already sends a `HEAD` and reads `Content-Length`. It
  ignores the length when `Content-Encoding` is set and treats 405/501 as "HEAD not supported".
- Placeholders that wait for a tap:
  - Android `ZoomableContentView.kt` `UrlImageView`: blur + icon when it has a blur **and** a
    known ratio; otherwise `ImageUrlWithDownloadButton`. `LocalImageView` has the same
    placeholder but draws a file already on the device, so it costs no data and gets no hint.
  - Android `VideoView.kt`: blur + icon, or `ImageUrlWithDownloadButton` when there is no
    blur. In that second case the surrounding box is sized to the video's aspect ratio while
    only a one-line URL row is drawn, leaving an empty black area (seen on Primal posts, which
    carry `dim` but no blurhash).
  - `commonsUI` `MyAsyncImage.kt`: its own icon placeholder. It draws cover and banner images
    from a bare URL (long-form, wiki, calendar, goal and other cards; 43 call sites), so there
    is no declared size.
  - `commonsUI` `PdfPreviewCard.kt` (`PlaceholderPdfCard`, "tap to load"), which fetches
    through `okHttpClientForPreview`.
  - `commonsUI` profile gallery `GalleryThumb.kt` `UrlImageView`: blur + icon or icon only, in
    a grid tile.
  - Desktop `DesktopVideoPlayer.kt`: the `loadOnDemand` download button, in both the native
    and the Chromium-routed player.
- Byte formatters: `formatFileSize` (`PlayableMediaCards.kt`, audio card) divides by 1,048,576
  but prints "MB"; `countToHumanReadableBytes` (`commons` `NumberFormatters.kt`) is decimal
  and rounds to whole units; `formatBytes` (`SoftwareApp.kt`, also used by `SoftwareRelease`
  and the browser's `DownloadPromptCard`) is binary with a no-break space.
- `blossom:` URIs can reach the placeholders (`MediaPlaceholders.kt`); Coil resolves them
  through `BlossomServerResolver`. They are not HTTP URLs.

## Design

### 1. Model

Add `sizeBytes: Long? = null` to `MediaUrlContent` and remove it from `MediaUrlVideo`, so
images, videos, audio and PDFs all carry it. No subclass of `MediaUrlContent` exists outside
`MediaContentModels.kt`, and the constructor calls pass named arguments, so a trailing
defaulted parameter breaks none of them.

- `RichTextParser.createMediaContent` reads `size` (URL fragment first, then imeta, as for
  every other field) on the image, video and PDF branches.
- `EncryptedMediaUrlImage` and `EncryptedMediaUrlVideo` gain a `sizeBytes` parameter (the video
  one currently drops it). Their ten call sites:
  - `RenderEncryptedFile.kt` (two calls) passes the kind-15 event's `size()`;
  - `NewGroupDMScreen.kt` (two) passes the uploaded file's size (`data.result.fileHeader.size`);
  - `RenderMarmotEncryptedMedia.kt` (four) and `CordnGroupChatScreen.kt` (two) pass nothing:
    `EncryptedMediaReferenceV2`, `Mip04MediaMeta` and `CordnMediaAttachment` carry no size.
  Without one they fall back to `HEAD`, which answers with the ciphertext length, within a
  few bytes of the real size. A `HEAD` passes through `EncryptedBlobInterceptor` unharmed:
  the empty body fails to decrypt and the original response, headers intact, is returned.

### 2. Size probe

In `commons/commonMain/.../service/MediaSizeProbe.kt`, after the `MediaTrackProbe` pattern:

```kotlin
enum class MediaSizeRole { IMAGE, VIDEO, PREVIEW }   // picks okHttpClientForImage / ForVideo / ForPreview

suspend fun IRoleBasedHttpClientBuilder.resolveMediaSize(url: String, mimeType: String?, role: MediaSizeRole, trackProbeWaitMs: Long = 2_000): Long?
suspend fun IRoleBasedHttpClientBuilder.probeMediaSize(url: String, mimeType: String?, role: MediaSizeRole): Long?
internal expect suspend fun IRoleBasedHttpClientBuilder.headContentLength(url: String, role: MediaSizeRole): Long?

object MediaSizeCache   // 500 entries, readable from composition; stores misses too
```

`resolveMediaSize` is what the UI calls. It returns a cached answer at once. When the URL is one
the MP4 track probe may read (`mayHoldVideo(mimeType, url)`) and that probe has not answered, it
waits only for a probe that is actually running: `probeMediaTracks` marks the URLs it is reading
in `MediaTrackProbeCache.inFlight`. A probe the same screen is starting gets 300 ms to show up;
when none does (gallery tiles, cover images, a probe cancelled by scrolling), the `HEAD` goes out
at once. A running probe is waited on for at most `trackProbeWaitMs` (2 s). Then it returns the
size the probe recorded, if any, and otherwise calls `probeMediaSize`.

`probeMediaSize`:
- returns a cached answer (hit or miss) without a request;
- returns null without a request for anything but `http(s)` URLs (`blossom:` and the like) and
  for HLS (`isHlsMedia`), since a playlist's length means nothing;
- otherwise calls `headContentLength`, caches a size or a "no size" answer, and caches nothing
  when the request itself failed, so a later placeholder for the file asks again.

`headContentLength` (`jvmAndroid`) sends a `HEAD` with `Accept-Encoding: identity` through the
role's client, which applies the user's Tor routing, and follows redirects (OkHttp's default):
- a 2xx with `Content-Length` > 0 and no `Content-Encoding` returns the length (`HeadAnswer.Size`);
- any other answer, or a URL OkHttp can't request, returns `HeadAnswer.NoSize`;
- an `IOException` (offline, a timeout, Tor still connecting) returns `HeadAnswer.Failed`, which
  is not cached (both logged at debug level);
- cancellation propagates;
- it uses the role client's own timeouts.

No in-flight deduplication of `HEAD`s: the cache stops repeat requests, and the rare duplicate
when two placeholders show the same URL at once is cheaper than a shared request that one
caller's cancellation can break.

The iOS actual answers `NoSize`.

**The track probe feeds the same cache.** `readMp4AudioOnly` (`MediaTrackProbe.jvmAndroid.kt`)
records the total from its first answer into `MediaSizeCache`: the `Content-Range` total of
a 206, or the `Content-Length` of a 200 when the server ignored the range. Its existing
requests are unchanged.

Known limits, accepted:
- `BlossomReadAuthInterceptor` retries only `GET`, so a host that requires read auth answers
  the `HEAD` with 401: a cached miss.
- `LocalBlossomCacheRedirectInterceptor` leaves `HEAD` alone, so the request goes to the origin
  even when the file would come from the local Blossom cache.

### 3. Composable hook

In `commonsUI`:
`@Composable fun rememberMediaSize(url: String, kind: MediaKind, accountViewModel, mimeType: String? = null, declaredSize: Long? = null, held: Boolean = true): Long?`,
plus a `MediaUrlContent` overload that passes the content's `url`, `mimeType`, `sizeBytes` and
kind. With `held = false` (the file loads anyway) it returns the declared size and never asks the
host, so callers pass the setting instead of wrapping the call in an `if`. `MediaKind` (Image, Video, Audio, PDF) names the label's type word and maps to a
`MediaSizeRole` (Audio uses the video client, PDF the preview client).

- With `declaredSize` set, it returns that at once and sends nothing.
- Otherwise it observes only this URL's entry in `MediaSizeCache`, so a size recorded later by
  the track probe or by another placeholder appears, and one answer doesn't recompose every
  placeholder in the feed.
- It calls `resolveMediaSize` in a `LaunchedEffect(url)`, which is cancelled when the
  placeholder leaves composition.

Placeholders exist only while auto-download holds media back. A lazy feed composes them for
the items on screen plus the few it prefetches, so the request count follows what the user
scrolls past. A note that auto-downloads never reaches the hook and never sends a `HEAD`. The
audio card is the exception: it is drawn whatever the setting is, so it calls the hook only
while the setting holds media back, and otherwise shows the declared size alone.

### 4. UI (mockup option A, with the type)

One shared label in a new `commonsUI` file, `ui/components/MediaSizeHint.kt`, next to
`MediaPlaceholders.kt`: `MediaSizeLabel(kind, sizeBytes)`.
It renders "Video · 12.4 MB", or "Video" while the size is unknown, and fades the size in when
it arrives, with no spinner.

| Placeholder | Today | New |
|---|---|---|
| Blur and known ratio (Android `UrlImageView`, `VideoView`) | Blur + 75 dp icon | Same, plus the label in a translucent pill under the icon |
| No blur (or no ratio), `dim` known | Empty box at the aspect ratio with a URL row (video), URL row (image) | `MediaDownloadCard`: neutral surface at the aspect ratio, icon, label, host below |
| No blur, no `dim` | `ImageUrlWithDownloadButton` (URL text + small icon) | Compact `MediaDownloadRow`: icon, label, host. Tap loads the media. |
| `MyAsyncImage` | Icon only | Icon + label ("Image · …") |
| `PdfPreviewCard` placeholder | Filename + "tap to load" | Filename + "tap to load · 2.1 MB" (role `PREVIEW`) |
| Profile gallery tile | Blur + icon, or icon | Same, plus the size alone ("12.4 MB"): a grid tile has no room for the type |
| Desktop video `loadOnDemand` | Download button | Download button + label beneath it |
| Audio card, Android | "MP3 · 4.8 MB" from imeta only | Same, falling back to the hook |
| Audio card, Desktop | No size (`sizeBytes = null`) | `AudioPlayer` gains a `sizeBytes` parameter, fed by the hook from the two `DesktopNotePlatform` callers that draw note media (`ZoomableContentView` and `NoteMediaView`) |
| Desktop `VideoView` port | Download button | Same label, fed by the hook while video loading is off |

The label's type word comes from the content's class and MIME type: Image, Video, Audio, PDF.
`MyAsyncImage` always says Image. New keys in `commonsUI` `composeResources/values/strings.xml`:
`media_kind_image`, `media_kind_video`, `media_kind_audio`, `media_kind_pdf`, and the format
string `media_type_and_size` = `%1$s · %2$s`. Existing "Video"/"Audio" keys such as `kind_video`
are not reused: they belong to other screens, and sharing a key ties their translations together.

**Behaviour change, deliberate:** in the no-blur, no-`dim` row, tapping used to open the URL in
a browser. It now loads the media in place, like every other placeholder. The URL stays
reachable: the host is shown, and if loading fails the error state already shows the full
clickable URL (`ClickableUrlOrBlossom`).

### 5. Formatting

Move `formatFileSize` from `PlayableMediaCards.kt` to `commons/.../util/NumberFormatters.kt`
and make it decimal (1 MB = 1,000,000 bytes, the unit Android's own file-size formatter uses)
with a GB step. Bytes under 1 KB print as "1 KB"; KB is always whole; MB and GB get one decimal
place below 100 and none from 100 up: "850 KB", "12.4 MB", "140 MB", "1.2 GB". It puts a
no-break space (`\u00A0`) between number and unit so the label never wraps there. The audio
card and the new labels both use it. Switching the audio card from binary to decimal makes its
numbers about 5 % larger and correct for the unit it prints. `formatBytes` and
`countToHumanReadableBytes` are left as they are; merging them is out of scope.

## Error handling

| Situation | Result |
|---|---|
| Size declared in the event | Shown on the first frame, no request |
| MP4 video the track probe reads | Size appears when the probe's first answer lands; `HEAD` if the probe ends without one, or is still running after 2 s |
| MP4 nothing reads (gallery tile, cover image) | `HEAD` after a 300 ms grace for a probe that is about to start |
| `HEAD` in flight | Type only; size fades in |
| Non-2xx (403 error page, 404, 401 read auth, 405 `HEAD` not allowed) | Type only, cached as a miss |
| 2xx without `Content-Length`, or with `Content-Encoding` | Type only, cached as a miss |
| Network error or timeout | Type only, not cached: the next placeholder for the file asks again |
| `blossom:` URI, HLS playlist | Type only, no request |
| Placeholder leaves composition mid-request | Request cancelled; nothing cached |

## Testing

- `commons` `commonTest`: `RichTextParser` fills `sizeBytes` for an image, a video and a PDF
  from imeta and from a URL fragment.
- `commons` `jvmTest`, driving `probeMediaSize` through a `SingleRoleBasedHttpClientBuilder`
  against a JDK `HttpServer` that records each request (as `EncryptedCartDownloadTest` does;
  `commons` has no MockWebServer):
  - a 200 with a length returns it, and the request method is `HEAD`;
  - a 403 with a body returns null;
  - a 200 without a length returns null;
  - a 200 with `Content-Encoding: gzip` returns null;
  - `probeMediaSize` makes no request for a cached URL, a `blossom:` URI or an `.m3u8`.
- The track probe records the `Content-Range` total from a 206 (same `HttpServer` approach).
- `resolveMediaSize`:
  - it uses a size the track probe recorded;
  - it falls back to `HEAD` after the wait when no track probe ever answers (the gallery-tile
    case);
  - it doesn't wait for a URL the track probe never reads.
- `formatFileSize`: under 1 KB, the KB/MB/GB boundaries and the 100 cut-off, decimal units.
- Manual, Android device with auto-download off:
  - a Primal video note (no blur, `dim` known);
  - an Amethyst image note (imeta `size`);
  - a bare image URL without imeta;
  - an audio note and a PDF;
  - a profile gallery;
  - an encrypted DM file.
- Manual, Desktop: `tools/desktop-run/run-headless.sh` with video loading off.

## Out of scope

- Website link previews.
- Desktop note images seem to load whatever the auto-download setting says
  (`DesktopNotePlatform.MediaImage` does not check `showImages()`), and Desktop audio prepares
  its player regardless of the setting (`AudioPlayer` calls `GlobalMediaPlayer.prepareAudio`
  unconditionally). Both are separate bugs, to confirm and fix on their own.
- GIFs: `GifVideoView` gates only on `autoPlayVideos()` and is never held back, so it has no
  placeholder to label.
- Merging the three byte formatters.
- A setting to turn the `HEAD` probe off.
