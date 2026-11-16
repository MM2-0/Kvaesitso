# QR Reader Overhaul

**Status:** phases 1-5 implemented (see §7 for what shipped and what did not); phase 0 outstanding
**Goal:** make scanning a QR code in Amethyst fast and near-certain — codes that are small,
far, dim, glossy, tilted, inverted, on a screen, or already sitting in the gallery should all
resolve on the first try, and a code the app *can't* route should say so instead of silently
dropping the user back where they started.

**Scope:** the reader (`amethyst/ui/screen/loggedIn/qrcode/QrCodeScanner.kt` and the four
screens that call it), plus a short second section on the *display* side, because half of
"scan this npub" is how legibly we draw the code for the other phone.

---

## 1. What we have today

`SimpleQrCodeScanner` is 30 lines that hand the whole job to
[zxing-android-embedded](https://github.com/journeyapps/zxing-android-embedded) 4.3.0 via
`ScanContract`, which launches its own `CaptureActivity`:

```kotlin
ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    setPrompt(stringRes(id = Res.string.point_to_the_qr_code))
    setBeepEnabled(false)
    setOrientationLocked(false)
    addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
}
```

Four call sites:

| Call site | Payload expected |
| --- | --- |
| `ShowQRScreen.kt:182` (`NIP19QrCodeScanner`) | any `nostr:` / NIP-19 entity → `Route` |
| `KeyTextField.kt:106` | `nsec` / `ncryptsec` / bunker login |
| `AddNwcWalletScreen.kt:207`, `AddClinkDebitWalletScreen.kt:172` | `nostr+walletconnect://` |
| `Nip46SignerScreen.kt:174` | `bunker://` |

`libs.zxing` (ZXing **core**) is used only for *encoding* (`QrCodeDrawer.kt`,
desktop `QrCodeCanvas.kt`). The decoder we actually run is the one bundled inside
zxing-android-embedded.

### 1.1 Why it is hard to scan — verified causes

Each of these was checked against the library's source (4.x `master`) or our own code, not
recalled:

1. **It is a Camera1 app.** `com.journeyapps.barcodescanner.camera.CameraManager` imports
   `android.hardware.Camera`. Everything downstream inherits Camera1's limits: legacy-HAL
   preview sizes, no per-frame 3A control, and no zoom API wired up at all.
2. **Autofocus is a 2-second timer, not continuous.** `CameraSettings` defaults to
   `focusMode = FocusMode.AUTO` (`continuousFocusEnabled = false`), and `AutoFocusManager`
   re-triggers `Camera.autoFocus()` on `AUTO_FOCUS_INTERVAL_MS = 2000L`. Between triggers the
   frame can stay out of focus for up to two seconds — this is the "hold it still… still…
   still…" feel, and it is fatal for close-up codes where the lens needs to rack to macro.
3. **`MIXED_SCAN` halves our decode rate.** We pass `Intents.Scan.MIXED_SCAN`, which selects
   `MixedDecoder`, whose `toBitmap()` flips a boolean and inverts *alternate frames*. So for a
   normal dark-on-light QR — i.e. essentially every code we meet — half of all frames are spent
   decoding an inverted image that can never match.
4. **No zoom, at all.** Not pinch, not a button, not automatic. A code on a laptop screen across
   a desk, or a small printed code on a sticker, simply never resolves enough modules.
5. **No discoverable torch.** The stock `CaptureActivity` layout has no torch button;
   `DecoratedBarcodeView` only maps it to the volume keys. In a bar or a meetup hallway — the
   exact place people swap npubs — there is no way to light the code.
6. **The decode is cropped.** `BarcodeView` sets `decoderThread.setCropRect(getPreviewFramingRect())`,
   and `CameraPreview` defaults to `marginFraction = 0.1d`, so anything outside the centred
   viewfinder box is discarded even though the user can see it in the preview.
7. **One decode attempt per frame, no retry ladder.** `DecoderThread.decode()` builds a single
   `HybridBinarizer` over the cropped luminance and calls the reader once. No `TRY_HARDER`, no
   rotation retry, no multi-scale, no denoise. ZXing-Java's detector needs three clean finder
   patterns; glare, a crease, or a ~15° tilt past its tolerance and the frame is simply lost.
8. **It leaves the app.** `ScanContract` starts a separate activity with its own theme — no
   Material3, no edge-to-edge, a visible cold-start hitch, and `setOrientationLocked(false)`
   means rotating the phone recreates that activity and restarts the camera mid-scan.
9. **Failure is silent and ambiguous.** `NIP19QrCodeScanner` maps both "user cancelled" and
   "decoded fine, but `uriToRoute` returned null" to `onScan(null)`, and `ShowQRBody` reacts by
   flipping `presenting = true`. The user sees the QR screen again with no message. A decoded
   but unroutable payload (a bare hex pubkey, an `nsec1…`, a plain `https://` link, another
   app's code) is indistinguishable from "the camera never read it" — which is very likely part
   of why the reader *feels* broken even in cases where the decode succeeded. This is the same
   complaint as [#417](https://github.com/vitorpamplona/amethyst/issues/417), which was closed
   in 2023 but is still the current behaviour.
10. **No way in except the live camera.** You cannot scan a QR you were *sent* — a screenshot, a
    photo in the gallery, an image in a DM — nor paste one from the clipboard. Today the only
    path is to get a second screen to display it and point the phone at it.

### 1.2 One happy accident

`amethyst/build.gradle.kts:622-626` already declares `camera-core`, `camera-camera2`,
`camera-lifecycle`, `camera-view` and `camera-extensions`, and **nothing in the repo imports
`androidx.camera`** (grep across `.kt`/`.xml` returns only a `PreviewView` line in the generated
baseline profile). CameraX is already paid for in APK size and in the licence audit; we just
haven't used it. This plan finally does.

---

## 2. Target architecture

Replace the external-activity scanner with an in-app Compose screen:

```
Route.QrScanner  ──►  QrScannerScreen (Compose, Material3, edge-to-edge)
                        ├─ CameraX Preview (PreviewView)  ─────┐
                        ├─ CameraX ImageAnalysis ──► BarcodeDecoder ──► ScanResult
                        ├─ ScannerOverlay (cutout, hit boxes, torch, zoom, gallery)
                        └─ ScanOutcomeSheet (routes, or explains why it can't)
                                                               │
 gallery / clipboard / shared image ──► BarcodeDecoder.decode(Bitmap) ┘
```

**`BarcodeDecoder`** is a small interface with two entry points —
`decode(ImageProxy): List<ScanResult>` and `decode(Bitmap, cropRect, rotation): List<ScanResult>`
— so the camera plumbing, the UI, and the tests are all independent of which engine sits behind
it.

### 2.1 Decoder choice: `io.github.zxing-cpp:android`

**Recommended: `io.github.zxing-cpp:android:3.1.1`, replacing `com.journeyapps:zxing-android-embedded`.**

Licence (per CLAUDE.md, verified against the published POM at
`repo1.maven.org/maven2/io/github/zxing-cpp/android/3.1.1/android-3.1.1.pom`):
**Apache License 2.0 → permissive → OK, proceed.** No GPL family, no linking-exception question.

Why it is the right engine here:

- Its `Options` are exactly the knobs our failure modes need — `tryHarder`, `tryRotate`,
  `tryInvert`, `tryDownscale`, `tryDenoise`, `binarizer`, `maxNumberOfSymbols`. In particular
  `tryInvert` does inverted detection **inside one call as a fallback pass**, which is what
  `MIXED_SCAN` was reaching for without throwing away half our frames.
- `read(image: ImageProxy)` reads the Y plane straight out of the CameraX buffer
  (`planes[0].buffer`, `rowStride`, `cropRect`, `rotationDegrees`) — no YUV→RGB copy, no
  `Bitmap` per frame.
- Every `Result` carries a `Position` (four corners + orientation), which we need for the
  tappable multi-code overlay and for the auto-zoom heuristic.
- `sequenceId` / `sequenceIndex` / `sequenceSize` give us Structured Append (multi-part QR) for
  free — the door to large payloads later.
- `minSdkVersion 21` (from the AAR manifest) against our `minSdk = 26`. Fine.

Cost: a prebuilt `.so` per ABI — `arm64-v8a` 1.75 MB, `armeabi-v7a` 1.23 MB, `x86` 1.85 MB,
`x86_64` 1.82 MB uncompressed. With `splits.abi` already enabled, an arm64 APK grows by roughly
0.8 MB compressed, partly offset by dropping zxing-android-embedded. We already ship prebuilt
JNI (`secp256k1-kmp-jni-android`), so F-Droid precedent exists — but **confirm with the F-Droid
packaging before landing**, since a new prebuilt binary is the kind of thing their build
metadata cares about.

**Why not ML Kit barcode scanning:** it is proprietary and GMS-shaped, so it could only ship in
the `play` flavor and `fdroid` would need a FOSS decoder anyway — meaning we'd build the
zxing-cpp path regardless and then maintain two. Because `BarcodeDecoder` is an interface, an
ML Kit implementation in `amethyst/src/play/` (the pattern `MLKitImageLabelService.kt` already
uses in both flavor trees) stays a cheap, later, measurement-driven option. Phase 0 exists to
decide whether it's ever needed.

**Fallback if the native lib is rejected:** keep `libs.zxing` (already present, ZXing-Java, zero
new bytes) behind the same interface. Phases 1, 3 and 4 below — the camera stack, the feedback
model, and image import — are decoder-independent and carry most of the win on their own.

---

## 3. Phases

### Phase 0 — Build the ruler before cutting (do this first)

Nothing here ships; it exists so every later claim is measured rather than asserted.

- [ ] Assemble a fixture corpus under `amethyst/src/androidTest/assets/qr/`: ~60 PNGs of real
      Amethyst payloads (`npub`, `nprofile` with relay hints, `nevent`, `naddr`,
      `nostr+walletconnect://`, `bunker://`, a Concord invite) rendered by our own
      `QrCodeDrawer` and then degraded — gaussian blur, 8/15/30° tilt, perspective, 30 %/10 %
      contrast, inverted, JPEG artefacts, partial glare, photographed-off-a-screen moiré, and
      a "small in frame" set at 120/80/48 px across.
- [ ] `QrDecodeCorpusTest` (androidTest): decode every fixture with (a) today's ZXing-Java +
      `HybridBinarizer` path and (b) zxing-cpp with the Phase 2 options. Report a pass rate per
      category and per-image median decode time.
- [ ] Record the baseline numbers in this file. **Gate:** if zxing-cpp does not clearly beat
      ZXing-Java on the degraded sets, drop §2.1 and keep ZXing-Java behind `BarcodeDecoder`.

### Phase 1 — In-app CameraX scanner (the structural fix)

- [ ] `BarcodeDecoder` interface + `ScanResult` (text, bytes, format, corner positions,
      sequence info) in `amethyst/.../ui/screen/loggedIn/qrcode/decode/`.
- [ ] `QrScannerScreen` — `PreviewView` + `ImageAnalysis` bound to `LocalLifecycleOwner` via
      `ProcessCameraProvider`, `STRATEGY_KEEP_ONLY_LATEST`, `OUTPUT_IMAGE_FORMAT_YUV_420_888`,
      analysis resolution requested at 1280×720 with a 1920×1080 fallback via
      `ResolutionSelector`. Analysis runs on a single background executor; results hop back to
      the main thread through a `StateFlow`.
- [ ] Register `Route.QrScanner` in `Routes.kt` / `AppNavigation.kt` and move the four call
      sites onto it. `SimpleQrCodeScanner` keeps its `(String?) -> Unit` signature as a thin
      wrapper so the wallet/bunker/login screens change by one import, not a rewrite.
- [ ] Camera permission with `accompanist-permissions` (already a dependency, used by
      `TakePicture.kt`): inline rationale, and an "Open settings" path when permanently denied —
      today a denied permission just bounces the user out of `CaptureActivity` with no
      explanation.
- [ ] Focus: rely on CameraX's continuous AF, plus tap-to-focus through
      `PreviewView.meteringPointFactory` → `FocusMeteringAction` with AF+AE, auto-cancel on.
      This alone removes cause §1.1.2.
- [ ] Remove `com.journeyapps:zxing-android-embedded` from `libs.versions.toml` and
      `amethyst/build.gradle.kts`. **Keep `libs.zxing`** — encoding still needs it.
- [ ] Delete the decorative crop: analyse the full frame (or a ≥90 % ROI), and let the
      viewfinder cutout be purely visual. Fixes §1.1.6.

### Phase 2 — Make each frame count

- [ ] Decoder options: `formats = {QR_CODE, MICRO_QR_CODE, RMQR_CODE}`, `tryInvert = true`,
      `tryRotate = true`, `tryDownscale = true`, `binarizer = LOCAL_AVERAGE`,
      `maxNumberOfSymbols = 5`. `tryHarder` on the still/import path always; on the live path
      alternate a cheap pass with a `tryHarder`/`tryDenoise` pass every Nth frame so the frame
      rate stays interactive.
- [ ] **Zoom.** Pinch-to-zoom (`CameraControl.setZoomRatio` driven by a
      `TransformableState`) plus a 1×/2× quick toggle. Then auto-zoom: after ~1.2 s with no
      decode, ramp the zoom 1.0 → 2.0 → back over a couple of seconds; if a code *is* decoded
      but its `Position` spans less than ~25 % of the frame's short side, zoom so it fills
      ~60 % and re-read. Fixes §1.1.4, the single biggest "it just won't read" cause.
- [ ] **Torch.** A visible toggle in the overlay, plus auto-suggest: the analysis frame's Y plane
      gives mean luminance for free — under a threshold for ~1 s, surface the torch button
      prominently (never auto-fire it; that's rude in a bar and worse in a meeting).
- [ ] Dedupe: ignore an identical payload re-decoded within 1.5 s so a held-steady code doesn't
      fire the handler repeatedly.
- [ ] Structured Append: accumulate parts keyed by `sequenceId`, show "2 of 3 captured", emit
      once complete, time out after 30 s. Groundwork for large/animated payloads.

### Phase 3 — Tell the user what happened

This is the cheapest phase and probably the largest perceived improvement.

- [ ] Split today's single `null` into an explicit outcome type: `Cancelled`,
      `PermissionDenied`, `Decoded(text) → Route`, and `Decoded(text) → unroutable`.
- [ ] On a decode: haptic tick (`HapticFeedbackType.LongPress`, matching `SwipeToDelete.kt` and
      the wallet wizard) plus a brief highlight drawn over the code's four corners.
- [ ] On **unroutable** content, show a bottom sheet with the decoded text, what we think it is,
      and actions — *Open link* for `http(s)`, *Copy*, *Search*, *Try again*. Closes the
      ambiguity behind §1.1.9 and [#417](https://github.com/vitorpamplona/amethyst/issues/417).
- [ ] When ≥2 codes are in frame, draw a tappable box over each and let the user pick, instead
      of silently taking whichever ZXing found first.
- [ ] Extract payload classification out of `MainActivity.uriToRoute` into a pure, JVM-testable
      `classifyScannedPayload(text): ScannedPayload` (nostr entity / wallet connect / bunker /
      nostrconnect / http(s) / lightning / unknown). `uriToRoute` keeps routing; the scanner
      gets a testable "what is this?" answer, and a bare 64-char hex pubkey — issue #417's
      original case — becomes trivial to accept.

### Phase 4 — Scan things that aren't in front of the camera

- [ ] **From the gallery:** a picker button in the overlay
      (`ActivityResultContracts.PickVisualMedia`) → decode the bitmap with the full
      `tryHarder + tryRotate + tryInvert + tryDenoise` option set, and on failure retry at
      2× and 0.5× scale before giving up.
- [ ] **From the clipboard:** if the clipboard holds an image, offer "Scan copied image"; if it
      holds text that `classifyScannedPayload` recognises, offer to use it directly. Covers the
      overwhelmingly common "someone sent me a screenshot of their npub" flow.
- [ ] **Shared in:** accept `ACTION_SEND` with an `image/*` MIME type into the scanner, so
      "Share → Amethyst" from a gallery or chat app resolves the code. (The manifest already has
      an `ACTION_SEND` image target for new posts — this needs to be a distinct, explicitly
      labelled entry, not a hijack of that one.)

### Phase 5 — The display side

Half of a scan is the code on the *other* screen.

- [ ] `ShowQRScreen` does **not** boost brightness, while `ShareNoteAsQrScreen.kt:238-249`
      already does. Extract that into a `KeepScreenBrightAndOn()` composable (brightness 1f +
      `FLAG_KEEP_SCREEN_ON`, restoring the previous value on dispose) and use it on both. On a
      dim OLED in dark mode this is the difference between scannable and not.
- [ ] `QrCodeDrawer.kt` hardcodes `CornerRadius(20f)` for the finder patterns regardless of how
      many pixels a module is — at small draw sizes that rounds a meaningful fraction of the
      finder away. Scale the radius with module size (cap around 20 % of a module).
- [ ] Error correction is fixed at `ErrorCorrectionLevel.Q`. For long payloads (an `nprofile`
      with two relay hints, a Concord invite) Q pushes the version up, so modules get smaller —
      and small modules, not error correction, are what actually defeats a camera at arm's
      length. Measure Q vs M across our real payload lengths on the Phase 0 corpus and pick per
      length rather than globally.
- [ ] Verify the `.clip(QuoteBorder)` 15 dp rounding never eats into the 4-module quiet zone at
      the sizes we actually render.

---

## 4. Testing

| Level | What |
| --- | --- |
| JVM unit | `classifyScannedPayload` over every payload we claim to accept, plus junk, plus the bare-hex and `nsec` cases; Structured Append accumulator (ordering, duplicates, timeout). |
| androidTest | `QrDecodeCorpusTest` (Phase 0) as a permanent regression gate — pass rate per degradation category, asserted against the recorded baseline so a decoder or option change can't quietly regress. |
| androidTest | Bitmap-import path end to end: fixture → `decode(Bitmap)` → `ScannedPayload`. |
| Manual matrix | Printed sticker at 10/30/60 cm; phone screen at 30/60/100 cm; laptop screen across a desk; dark room with and without torch; behind glossy glass; 15°/30°/45° tilt; two codes in frame; inverted (light-on-dark) code. Record pass/fail before and after. |
| Macrobenchmark | Time from tapping "Scan QR" to first preview frame — the current external-activity cold start is part of what the change should erase. |

## 5. Risks

- **APK size** — ~0.8 MB compressed on arm64 for the native decoder, partly returned by
  dropping zxing-android-embedded. Phase 0's gate is what justifies it.
- **F-Droid** — a new prebuilt `.so` from Maven Central; check with the F-Droid packaging before
  landing rather than after. The ZXing-Java fallback keeps this from being a dead end.
- **Camera device variance** — CameraX is far more uniform than Camera1, but zoom ratio ranges
  and torch availability still vary; every control must degrade to hidden rather than broken.
- **Scope creep into the display side** — Phase 5 is deliberately last and independent.
- **Desktop** — `desktopApp` has no scanner today and this plan doesn't add one. If webcam
  scanning is ever wanted, `BarcodeDecoder` + the payload classifier are the reusable halves;
  the CameraX screen is not.

## 6. Out of scope

Animated / BC-UR multi-frame QR *generation*, NFC handoff, and any change to what the four
existing call sites do with a successful payload.


---

## 7. What shipped, and what did not

Implemented in this branch:

| Phase | State |
| --- | --- |
| 0 — measurement corpus | **Half done.** The corpus and the ZXing-Java baseline are built and measured (see §8); the zxing-cpp half needs a device and is written but unrun, so the gate is armed rather than passed. |
| 1 — in-app CameraX scanner | Done. |
| 2 — per-frame decode quality | Done. |
| 3 — explicit outcomes | Done. |
| 4 — gallery / clipboard import | Done, minus the `ACTION_SEND` share-in target. |
| 5 — display side | Done. |

### Deviations from the plan above

- **A dialog, not a `Route`.** §2 proposed registering `Route.QrScanner`. One of the four call
  sites is `KeyTextField` on the *logged-out* login screen, which lives outside the navigation
  graph entirely, so a route could not serve it. `QrCodeScannerDialog` is a full-screen
  `Dialog` instead, which also let all four call sites keep their existing
  `SimpleQrCodeScanner { }` shape — the diff at each is one import.
- **`ScanOutcome` instead of "close, then explain".** For the sheet in §3 to appear, the scanner
  has to still be open when the caller decides it cannot use the payload. So the callback returns
  `ScanOutcome.Handled` / `ScanOutcome.NotSupported` rather than `Unit`, and the camera keeps
  running through the explanation.
- **No "found but too small" auto-zoom branch.** §2 listed zooming toward a code whose
  `Position` spans too little of the frame. That branch is unreachable: if the code decoded, we
  are done with it. Auto-zoom now only sweeps while *nothing* is decoding, which is the case
  that actually fails.
- **`ACTION_SEND` image share-in not wired.** The manifest already has an `ACTION_SEND` image
  target aimed at new posts; adding a second, distinctly-labelled one is a manifest and routing
  change that belongs with its own testing rather than bolted onto this branch.

### Still to do

1. **Phase 0, retroactively.** Build the corpus, run `QrDecodeCorpusTest` on a device, record
   the numbers here, and confirm the engine choice against them. Until that happens, "zxing-cpp
   beats ZXing-Java on degraded codes" is a well-founded expectation, not a measurement.
2. **The manual matrix in §4.** None of it has been run — there is no camera in this
   environment. Every camera-facing behaviour in phases 1, 2 and 4 (binding, focus, torch,
   auto-zoom, the overlay's coordinate mapping, the photo picker) is compile-verified and
   reasoned-through only.
3. **F-Droid packaging sign-off** on the new prebuilt `.so`, per §5.
4. **The ECC level question** in §5 — still open, and still wants the corpus to answer it.

---

## 8. Phase 0: the baseline

`QrCorpus` renders three payloads — an `npub`, an `nprofile` with relay hints, and an `nevent`
(69, ~330 and ~140 characters, so three different symbol versions) — at 4 pixels per module, then
degrades each one sixteen ways. Every degradation is expressed as a fraction of a *module*, so
changing the render scale cannot quietly re-tune the corpus's difficulty.

`QrCorpusBaselineTest` measures **ZXing-Java**, the decoder the old zxing-android-embedded scanner
used, over that corpus on the JVM. Measured 2026-09-15:

| category | ZXing-Java | what it stands for |
| --- | --- | --- |
| clean | 3/3 | sanity — if this ever fails the corpus is broken |
| blur | 3/3 | a quarter-module out of focus |
| blur-heavy | **0/3** | half a module out of focus |
| tilt15 / tilt30 / tilt45 | 2/3 each | held at an angle |
| perspective | **0/3** | seen off-axis — a code on a wall or table, photographed from the side |
| low-contrast | 3/3 | a dim screen |
| very-low-contrast | **0/3** | a very dim screen, or worn print |
| inverted | 3/3 | light-on-dark |
| glare | 3/3 | a highlight burning out one corner |
| moire | 3/3 | photographed off another screen |
| noise | 2/3 | sensor noise in poor light |
| far-3px | 3/3 | three pixels per module |
| far-2px | 2/3 | two pixels per module |
| far-1.5px | **0/3** | one and a half pixels per module |
| **TOTAL** | **31/48** | |

Read it as a map of where the old reader gave up. **Perspective is a total loss** — an off-axis
code, one of the most ordinary framings there is, was simply unreadable. So are heavy blur, very
low contrast, and anything under two pixels per module.

Two caveats on the number, both of which make 31/48 *flattering* to the old scanner:

- It decodes the **full image** and retries **inverted**. The shipped scanner cropped to a
  viewfinder rect and alternated inversion across frames, so it had strictly fewer chances.
- It measures a decoder on a still. It says nothing about focus, zoom or torch, which is where
  most of this branch's other work went.

So a win measured here is a floor on the real-world difference, not the whole of it.

**The gate is not yet passed.** `QrDecodeCorpusTest` runs zxing-cpp over the identical committed
images and fails if it reads fewer in any category. It needs a device. Until someone runs it, the
decoder swap rests on the reasoning in §2.1 — this section just means the measurement is now one
command away instead of unbuilt.

The corpus costs 776 KB in `amethyst/src/androidTest/assets/qr/` (test APK only, never shipped).
Regenerate it with:

```bash
./gradlew :amethyst:testFdroidDebugUnitTest --tests '*QrCorpusBaselineTest*' \
    -Pamethyst.qr.corpus.export=true
```
