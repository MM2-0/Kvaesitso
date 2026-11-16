# AVIF support — comprehensive design

> **Status:** shipped — AVIF upload/display support landed — `MediaMimeTypes.kt` plus the compressor/stripper/preview changes are in tree.
> _Audited 2026-06-30._

**Issue:** [vitorpamplona/amethyst#837](https://github.com/vitorpamplona/amethyst/issues/837)
**Date:** 2026-05-26
**Branch:** `feat/avif-support` (based on `d1610bf97`, origin/main = upstream/main)
**Author:** davotoula
**Manual test plan:** `~/docs/amethyst/2026-05-26-avif-manual-test-plan.md`

---

## 1. Goal

Make AVIF (still and animated) a first-class image format in Amethyst, working on every surface where any other image format works — uploads, feeds, profiles, DMs, emoji packs, reactions, gallery, caching — with a graceful no-crash fallback on Android API < 31 where the platform decoder cannot handle AVIF at all.

This is **not** a minimum-merge or bounty-targeted effort. The goal is a full solution.

---

## 2. Context

### 2.1 What works today on `main`

AVIF is **already partially supported**:

- `commons/.../richtext/RichTextParser.kt` recognizes `.avif` extension and `image/avif` MIME — links in note text render as image embeds.
- `amethyst/.../video/datasource/FeedBasis.kt` includes `image/avif` in `SUPPORTED_VIDEO_FEED_MIME_TYPES` — AVIF posts are eligible for the media feed.
- `commons/.../jvmMain/.../upload/MediaMetadata.kt` maps the `.avif` extension to `image/avif`.
- Desktop file pickers (`DesktopFilePicker.kt`, `ComposeNoteDialog.kt`, `ChatPane.kt`) accept `.avif`.
- `amethyst/.../service/images/ImageLoaderSetup.kt` registers `coil3.gif.AnimatedImageDecoder.Factory()`. On Android **API 31+** this routes through the platform `ImageDecoder`, which supports both still and animated AVIF natively.

Net: on Android 12+, display of still and animated AVIF should mostly already work via Coil. The original issue author may not realize this.

### 2.2 What's broken today

The upload pipeline does not know about AVIF:

- `amethyst/src/main/java/.../service/uploads/MediaCompressor.kt::compressImage()` re-encodes every non-GIF/SVG image as `Bitmap.CompressFormat.JPEG` at 640 px width. An uploaded animated AVIF is **destroyed** — flattened to a single JPEG frame.
- `amethyst/src/main/java/.../service/uploads/MetadataStripper.kt` uses `ExifInterface` to rewrite metadata. `ExifInterface` does not reliably handle AVIF containers, so AVIFs either fail or are corrupted.
- `amethyst/src/main/java/.../service/uploads/PreviewMetadataCalculator.kt` uses `BitmapFactory.decodeStream` / `decodeByteArray`. **`BitmapFactory` does not support AVIF on any Android version.** Result: AVIFs upload with no blurhash, no thumbhash, no dimensions — receivers see a blank placeholder until the full image downloads.
- `amethyst/src/main/java/.../service/uploads/nip96/Nip96Uploader.kt` falls back to a no-extension multipart filename when `MimeTypeMap` is silent (which it can be for AVIF on older Android). Some NIP-96 servers reject extension-less uploads.

### 2.3 Prior work

Four PRs were opened against #837 and all closed without merge:

| PR | Author | Verdict |
|---|---|---|
| #2825 | KingParmenides | Added a parallel `AnimatedUrlImage` composable that bypassed loading/thumbhash/blurhash slots. Vitor: "delivered very little." **Discard.** |
| #3008 | alan747271363-art | Narrow MediaCompressor + MetadataStripper skip. No on-device test. |
| #3010 | ProtonsAndElectrons | Skip compression + ImageDecoder previews + AVIF EXIF inspection + `.avif` filename fallback. Emulator boot only. |
| #3016 | cybercraftsolutionsllc | Same as #3010 plus `image/avif-sequence` MIME (not real) and an instrumented test. Closed because contributor never did a manual signed-in upload via the UI. |

All upload-pipeline PRs converged on roughly the same fix. They were rejected for **lack of manual on-device verification**, not for being wrong. We will draw inspiration from #3016's diff structure but write the production code from scratch on current main.

### 2.4 Inbound branches that could matter later

Four Claude-Code experimental branches exist but have **no PR opened**, so they are not on any merge path today:

- `upstream/claude/amethyst-kmp-conversion-zYNmg` — would move `amethyst/src/main/` → `amethyst/src/androidMain/`. If this lands later, all file paths in this spec need a search-and-replace.
- `upstream/claude/add-crop-trim-uploads-RuQBe` — would add UCrop image crop + media3-transformer video trim before upload. If it lands, a new AVIF surface decision is needed: UCrop almost certainly does not handle AVIF input.
- `origin/claude/fix-video-deletion-timing-LGFn4` — introduces `TempFileTracker` in `UploadOrchestrator`. If it lands, the AVIF passthrough (`MediaCompressorResult(uri, contentType, null)`) needs reverification against the new lifecycle.

None of these are scheduled. Re-check them at PR-ready time.

---

## 3. Architecture and guiding principle

**AVIF is not a new subsystem.** It is the act of making AVIF behave like animated WebP everywhere. The right mental model: find every place WebP/GIF gets special-cased and make sure AVIF lands in the same branch.

Production code change: small (estimated ~150–250 LoC, ~5 files).
Verification surface: large — see §5 for the ~12 display surfaces; each is exercised against {still, animated} × {API 31+, API < 31}, against two upload protocols (Blossom and NIP-96). The full row count in the manual test plan is 50.

**Explicit non-goals:**

- Shipping a JNI libavif decoder for API < 31. Too much APK weight (~3 MB) for too little gain. API < 31 gets graceful placeholder behavior (existing Coil error slot), documented as a known limitation.
- Adding new composables. PR #2825 made that mistake. Existing `MyAsyncImage` / `RobohashAsyncImage` / `ZoomableContentView` / `AsyncImage` paths must handle AVIF unchanged.
- Supporting `image/avif-sequence`. Not IANA-registered. RFC 9081 defines `image/avif` for both still and animated.

---

## 4. Upload-pipeline changes (Android, `amethyst/`)

Concrete file-level diff. All paths verified against current main on branch `feat/avif-support`.

| File | Change |
|---|---|
| `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/MediaMimeTypes.kt` *(new)* | Centralize AVIF MIME helpers: `isAvif(contentType: String?): Boolean`, `const val AVIF_MIME = "image/avif"`, `const val AVIF_EXTENSION = "avif"`. Keep the four consumer files below honest. |
| `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/MediaCompressor.kt` | In `compressImage()`, branch early on `isAvif(contentType)`: return `MediaCompressorResult(uri, contentType, null)` — same pattern SVG already follows. Verify SVG branch as the reference pattern. |
| `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/MetadataStripper.kt` | For AVIF: inspect EXIF via `ExifInterface(stream)`. If no sensitive tags → pass through unchanged. If sensitive tags found OR inspection fails for any reason → **fail closed** (reject upload, surface error to user). Do not attempt to rewrite the AVIF container. |
| `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/PreviewMetadataCalculator.kt` | For AVIF on API ≥ 28, decode via `ImageDecoder.createSource(...).decodeBitmap()`. For animated AVIF this returns the first frame — which is exactly what we want for static hashes. Use the same decoded bitmap to compute blurhash + thumbhash + dimensions. On API < 28, skip preview metadata (return nulls, log warning). The "decoded once, both hashes computed from same pixels" invariant in the existing code is preserved. |
| `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/nip96/Nip96Uploader.kt` | When `MimeTypeMap` returns null for the AVIF content type, fall back to `.avif` (or `AVIF_EXTENSION` from `MediaMimeTypes`) for the multipart filename. |
| `amethyst/src/main/java/com/vitorpamplona/amethyst/service/uploads/blossom/...` | Same filename fallback if the Blossom uploader has equivalent logic. Inspect first; may be no-op. |

### 4.1 Cross-references for sanity

The same `MediaMimeTypes.isAvif(...)` helper should also be used by the existing `RichTextParser.kt` and `FeedBasis.kt` if practical — but only as a follow-up cleanup, not as part of this change. Adding it now creates churn that's hard to review.

---

## 5. Display surface

### 5.1 Expected zero-code-change surfaces (verify only)

These should work today on API 31+ through Coil's `AnimatedImageDecoder.Factory()`. Verification only:

- Note body images via `MyAsyncImage` / `ZoomableContentView`.
- Profile pictures via `RobohashAsyncImage` (`AnimatedImageDrawable` should route through).
- Profile banner.
- DM images.
- Profile media tab / gallery.
- Long-form (NIP-23) post body.
- Compose preview thumbnail.
- Emoji pack image rendering (`Emoji.kt`, `ShowEmojiSuggestionList.kt`, `EmojiPackCard.kt`, `EmojiPackScreen.kt`).
- Emoji suggestion popup.
- Custom emoji inline in note text.
- Custom emoji as a reaction.

If any of these fail on a real device, the fix is in `ImageLoaderSetup.kt` configuration, not in adding new composables.

### 5.2 Coil decoder factory order

In `amethyst/src/main/java/.../service/images/ImageLoaderSetup.kt`, `AnimatedImageDecoder.Factory()` must run **before** `GifDecoder.Factory()` so AVIFs aren't accidentally routed to the GIF decoder. Verify the current order matches.

### 5.3 Desktop (`desktopApp/`)

Desktop uses Coil-JVM which does **not** include `AnimatedImageDecoder` (no platform `ImageDecoder` outside Android). Animated AVIF on Desktop will likely show first-frame only. Still AVIF: unknown — may require additional Coil decoder. **Verify, then document as known limitation if confirmed.** Do not add JVM libavif binding in this PR.

---

## 6. Animation lifecycle (pause off-screen, resume on-screen)

PR #2825 attempted this for AVIF and broke loading/blurhash slots in the process. The correct approach: discover the **existing** pause/resume mechanism for animated WebP and ensure AVIF lands in the same branch.

### 6.1 Verification step (before any code change)

1. Locate the pause/resume mechanism. Likely candidates: `MyAsyncImage`, `ZoomableContentView`, `GifVideoView`, or a `DisposableEffect` on visibility that calls `AnimatedImageDrawable.start()` / `stop()`.
2. Check the predicate:
   - **If it branches on `drawable is AnimatedImageDrawable`** → MIME-agnostic, AVIF works for free, **zero code change**.
   - **If it branches on a hardcoded MIME list** (e.g. `setOf("image/gif", "image/webp")`) → add `"image/avif"`.
3. Same audit for the emoji composables modified by PR #2825 (`Emoji.kt`, `ShowEmojiSuggestionList.kt`, `EmojiPackCard.kt`, `EmojiPackScreen.kt`).

### 6.2 Hard constraint

**Do not introduce a parallel `AnimatedUrlImage` composable.** That was PR #2825's mistake and the reason it was closed. Any new abstraction must be additive to existing composables, not a replacement.

---

## 7. Caching

Coil 3's `ImageLoader` (configured in `ImageLoaderSetup.kt`) caches in two layers: memory (bitmaps) and disk (raw bytes). Both are URL-keyed, format-agnostic.

**Expected: zero code change.** Verification:

- First load of an AVIF URL downloads; second load hits memory cache (no network).
- Background → return → load hits disk cache; no network.
- Force-stop → relaunch → load hits disk cache; no network.
- Confirm Coil's memory budget accounts for animated AVIF frame buffers (an animated AVIF avatar can be 5–15 MB decoded). If thrashing observed, adjust `ImageLoader.Builder.memoryCache { ... }` in `ImageLoaderSetup.kt`.
- Confirm the disk cache survives app restart (`coil_image_cache` under app cache dir).
- For animated AVIF in scroll-heavy feeds: confirm Coil doesn't decode every frame from disk on every scroll-into-view. If it does, that's a Coil-level concern and may require either a feature request to Coil or a workaround in the composable (cache the drawable, not just the bytes).

---

## 8. Pre-API-31 fallback

Amethyst's `minSdk = 26`. Five API levels (26–30) have **no platform AVIF support at all**.

**Decision: accept Coil's existing error slot.** AVIF fails to decode → Coil falls through to its built-in error placeholder (broken-image icon, same UX as a 404). Avatar surfaces fall back to the existing robohash placeholder. No new strings, no new dialogs, no JNI libavif.

Document in PR description and §11 below.

---

## 9. Testing & hardening

### 9.1 Three layers

**Layer 1 — JVM unit tests** (in `amethyst/src/test/`):

- `MediaCompressorTest`: AVIF URI returns unchanged; MIME preserved; not converted to JPEG.
- `MetadataStripperTest`: clean AVIF passes; AVIF with synthetic GPS EXIF rejected; AVIF that ExifInterface can't parse → fails closed.
- `PreviewMetadataCalculatorTest`: AVIF input via a fake `ImageDecoder.Source` → blurhash + thumbhash + dimensions returned (or skipped on API < 28 with no crash).
- `Nip96UploaderTest` (or equivalent): filename fallback to `.avif` when `MimeTypeMap` is silent.

Runs via the standard pre-push hook scope: `:amethyst:testPlayDebugUnitTest`.

**Layer 2 — Android instrumented tests** (in `amethyst/src/androidTest/`):

- `AvifUploadPipelineInstrumentedTest`: generate an 8×8 still AVIF in app cache, drive it through `MediaCompressor` → `MetadataStripper` → `PreviewMetadataCalculator` → `Nip96Uploader` filename helper. Assert: bytes preserved, blurhash + thumbhash non-null, no JPEG output.
- `AvifAnimatedDecodeInstrumentedTest`: tiny 8×8 / 3-frame animated AVIF, assert Coil's `ImageLoader.execute()` returns an `AnimatedImageDrawable` with `numberOfFrames > 1`.
- Run on at least two emulators: API 35 (modern path) and API 28 (pre-API-31 path), to confirm both code branches are exercised.

**Layer 3 — Manual on-device verification.** This is the gate that killed prior PRs.

See **`~/docs/amethyst/2026-05-26-avif-manual-test-plan.md`** for the full 50-row checklist covering:

- §2.1 Compose / upload flow (8 rows)
- §2.2 Display in feeds / notes (6 rows)
- §2.3 Profile pictures and banner (6 rows)
- §2.4 Direct messages (3 rows)
- §2.5 Emoji packs / NIP-30 — the original use case (8 rows)
- §2.6 Caching (5 rows)
- §3.1–3.5 Hardening (corrupted, large, EXIF, API < 31, animation lifecycle)
- §4 Desktop spot-check
- §5 PR sign-off checklist

### 9.2 Upload protocol matrix

Each upload row of the manual checklist runs against **both**:

- A Blossom server (e.g. `blossom.primal.net`).
- A NIP-96 server (e.g. `nostr.build`).

Bytes downloaded post-upload are binary-compared to the local source. Server-side transcoding (some NIP-96 servers do this) is documented as a server-side caveat, not a regression.

### 9.3 Test asset corpus

Generated via `avifenc` (libavif), `ffmpeg`, and `exiftool`:

- `still-medium.avif` — 800×600 still
- `animated-medium.avif` — animated from GIF source
- `animated-tiny-3frames.avif` — 8×8, 3 frames (committed as test fixture)
- `still-tiny-8x8.avif` — 8×8 still (committed as test fixture)
- `emote-64.avif` — 64×64 animated, for the emoji-pack proof point
- `still-with-gps.avif` — synthetic GPS EXIF for the hardening test
- `corrupted.avif` — truncated for the decoder-failure test
- `large-animated.avif` — >20 MB for OOM/ANR hardening

Tiny fixtures committed under `amethyst/src/androidTest/assets/avif/`. Larger files referenced by local path in the manual checklist only.

### Running the AVIF tests locally

The AVIF test layer is **not wired into CI** — run it locally on an emulator before merging any
AVIF-touching change. See `amethyst/plans/2026-05-27-avif-instrumented-tests-plan.md` for the
full plan.

**JVM unit tests** (no emulator needed):

```bash
./gradlew :amethyst:testPlayDebugUnitTest --tests 'com.vitorpamplona.amethyst.service.uploads.MediaMimeTypesTest'
./gradlew :amethyst:testPlayDebugUnitTest --tests 'com.vitorpamplona.amethyst.service.images.ThumbnailDiskCacheAvifTest'
```

**Instrumented tests** (boot an API 31+ emulator first — API 35 recommended):

```bash
./gradlew :amethyst:connectedPlayDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.package=com.vitorpamplona.amethyst.service.uploads'

./gradlew :amethyst:connectedPlayDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.package=com.vitorpamplona.amethyst.service.images'
```

**Regenerating fixtures** (only if you change the fixtures themselves):

```bash
brew install libavif ffmpeg exiftool   # one-time
# See Task 1 in 2026-05-27-avif-instrumented-tests-plan.md for the full generation recipe.
```

---

## 10. Headline proof artifact

Per §2.5 of the manual test plan: generate `emote-64.avif`, upload via the app to a NIP-96 or Blossom server, build a NIP-30 emoji pack event (kind 30030) referencing it from the **Amethyst tester** account, attach to the account, compose a note using `:emote_name:`, react to a note with it. Record a 30-second screen capture.

This becomes the headline artifact in the PR description — direct answer to RedNumber1's original ask ("animated AVIFs for my emotes").

---

## 11. Known limitations to document in the PR

- **Android API < 31:** AVIF doesn't render — neither `AnimatedImageDecoder` (Android 8.0/API 26 doesn't have it) nor `BitmapFactory` can decode AVIF. Verified on API 26 emulator: posts containing AVIF URLs show the URL as a clickable link inline in the note body, no crash, no UI hang. When scrolling an AVIF post into view, Coil briefly renders the blurhash placeholder (via `BlurHashFetcher`) before the failed decode transitions to the link fallback — that's a small UX win, not a regression. Avatar slots fall back to robohash. No JNI libavif shipped.
- **Android API < 31 picker:** the Android gallery picker greys AVIF out because `MediaStore` only recognises AVIF as a picker-supported image type from API 31+. This is an OS-level filter, not an Amethyst limitation. Workaround for users on older Android: use the **Files** (Storage Access Framework) picker root, which doesn't apply the MIME-handler filter. The upload pipeline itself is API-agnostic — bytes pushed via SAF would upload successfully even on API 26, but the resulting AVIF wouldn't render on the same device's feed for the reason above.
- **Desktop (JVM):** Animated AVIF likely still-frame only. Upload works. Confirm with §4 of manual test plan and document.
- **Desktop AVIF preview metadata:** `commons/src/jvmMain/.../MediaMetadata.kt` uses `javax.imageio.ImageIO.read(file)` which has no AVIF support in the standard JDK. Desktop-posted AVIF notes therefore ship without `blurhash`, `thumbhash`, or `dim` in the `imeta` tag. Bytes round-trip correctly; receivers on Android still decode and render the AVIF — only the loading-placeholder UX is degraded (blank slot until the network fetch completes instead of a blurhash blur and pre-allocated aspect-ratio box). Fixable later either by hand-parsing the ISOBMFF `ispe` box for dimensions, or by adding a JVM AVIF ImageIO plugin to `desktopApp`. Out of scope for this PR.
- **Desktop AVIF display:** Coil-JVM has no AVIF decoder. Posts containing AVIF URLs show no image on Desktop (whether posted from Desktop or Android). Verified by spot-check: a 200x200 single-frame AVIF posted from Amethyst Desktop did not render in the Desktop client either. Android receivers display normally.
- **NIP-96 server transcoding:** If a chosen NIP-96 server transcodes AVIF → JPEG/WebP server-side, that is outside Amethyst's control. PR description must name the servers tested and the observed behavior.
- **`image/avif-sequence` MIME:** Intentionally rejected. RFC 9081 says `image/avif` covers both still and animated. PR #3016 introduced this; we don't.
- **Coil-level frame re-decoding:** If observed under §7 verification, treat as a follow-up issue. Out of scope for this PR.
- **Animated AVIF playback smoothness:** Frame advancement is driven by `android.graphics.drawable.AnimatedImageDrawable` (the platform decoder), not Amethyst code. On most Android devices including Pixel, the AV1 path is software-only (`dav1d`) — the hardware AV1 decoder is wired for video, not for `ImageDecoder`. Per-frame decode cost is orders of magnitude higher than GIF (LZW) or animated WebP (VP8). Expect lower effective frame rates for large source images at high frame counts. Mitigation is encoder-side: small dimensions, low fps, modest frame counts. Not a regression — animated AVIF could not render at all before this PR.
- **Animated AVIF playback machinery:** Coil 3's bundled `AnimatedImageDecoder.Factory` only recognizes HEIF brands (msf1/hevc/hevx) at offset 8 and misses AVIF's `avis`/`avif`/`avo1` brands. We ship `AvifAnimatedDecoderFactory` to plug the gap and an upstream Coil fix is separate. Verified against Coil 3.4.0 and 3.5.0-beta01.
- **Profile picture thumbnail cache:** `ThumbnailDiskCache` skips animated AVIF (`ftyp avis`) so the avatar always re-decodes through the AVIF path. Side effect: animated AVIF avatars don't benefit from the 256x256 JPEG thumbnail cache that other formats enjoy, costing some decode CPU per re-render. Acceptable trade-off vs. permanently freezing the avatar on its first frame.
- **Strip-metadata toggle OFF on AVIF leaks EXIF:** the fail-closed AVIF inspector only runs when the user has strip-metadata enabled (the default). If the user toggles strip-metadata off, AVIF is uploaded as-is — including any GPS / device-serial / datetime EXIF tags it carries. This matches the existing behavior for JPEG/PNG (the toggle universally means "trust me, upload as-is") but is worth knowing: AVIF cannot be safely stripped in-place, so toggle-off is the only way to upload an AVIF whose EXIF the user wants preserved. Users who want privacy guarantees should leave the toggle on.

---

## 12. Implementation sequence (rough)

To be expanded by `writing-plans` into a step-by-step plan. High-level:

1. Branch `feat/avif-support` (done — based on `d1610bf97`).
2. Add `MediaMimeTypes.kt` (new file, helpers only).
3. Patch `MediaCompressor.kt` (early return for AVIF).
4. Patch `MetadataStripper.kt` (fail-closed EXIF inspection).
5. Patch `PreviewMetadataCalculator.kt` (`ImageDecoder` for AVIF on API ≥ 28).
6. Patch `Nip96Uploader.kt` (and Blossom uploader if applicable) for filename fallback.
7. JVM unit tests for steps 2–6.
8. Audit animation-lifecycle code paths (§6.1) — patch or skip per findings.
9. Generate test corpus, commit tiny fixtures.
10. Write `AvifUploadPipelineInstrumentedTest`, `AvifAnimatedDecodeInstrumentedTest`. Run on API 35 + API 28 emulators.
11. Manual on-device verification per `~/docs/amethyst/2026-05-26-avif-manual-test-plan.md`.
12. Record §10 proof artifact.
13. `./gradlew spotlessApply`, run pre-push hook scope, push, open PR with checklist + recording.

---

## 13. Out of scope

- iOS AVIF support (`iosMain/`) — there is iOS work in flight on a separate branch; coordinate later.
- Server-side AVIF transcoding tooling. Server choice belongs to the user.
- JNI libavif for API < 31.
- New abstractions or composables.
- Cleanup of existing AVIF references in `RichTextParser.kt` / `FeedBasis.kt` to use the new `MediaMimeTypes` helpers. Follow-up only.
