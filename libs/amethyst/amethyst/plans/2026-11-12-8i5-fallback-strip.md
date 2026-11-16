# Brief: 8i5, a compression fallback must still honour the privacy strip

Bead: dkamy-8i5 (P1, bug; discovered from dkamy-dsr). Same branch as dsr: `feat/mp4-sanitizer` in
`/Users/david/StudioProjects/amethyst-wt1`, on top of `cf4ee6433c`. One or two commits, no version
change, no push.

## Why

`ImageVideoDescription.kt:446` passes `effectiveStripMetadata = !isVideoWithCompression && stripMetadata`
and hides the strip toggle for a video that will be compressed (commit `127079ede3`: the encoder's
fresh container is the strip). That holds only when the encode succeeds. `VideoCompressionHelper.compressVideo`
returns the **original** on the 60 s timeout, on "compressed not smaller than original", on an
encoder failure and on cancel. `AndroidMediaUploader.stripAfterCompression` then sees
`stripMetadata = false`, skips the stripper, and the camera file goes up with its GPS (`udta ©xyz`),
model/manufacturer/capture tags, C2PA `uuid` and `mett` track — for a user whose
`stripLocationOnUpload` is on and who was shown no toggle to say otherwise.

The other two dialogs already do the right thing: `NewMediaView.kt:259` and
`ChatFileUploadDialog.kt:270` hide the toggle on `controls.videoWillBeCompressed` but pass the model's
`stripMetadata` through unchanged (`NewMediaModel.kt:133`, `ChatFileUploadState.stripMetadata`).
Desktop is not affected: `FfmpegVideoCompressor` remuxes-and-strips the original on failure regardless
of the flag. So this is one dialog out of step with its siblings.

Passing the user's choice through costs nothing on success: `stripAfterCompression` (lines ~640–672)
already trusts a successful compression (`isVideo && compressionApplied → StrippingResult(uri, true)`),
so the stripper runs only when the encode fell back — and with dsr landed, that output is a fast-start
`Mp4Sanitizer` file. This is the "encode fell back + strip on" row of the dsr table, which the dsr
report notes as unreachable from the main dialog today.

## Changes

1. **`commonsUI/.../ui/note/creators/uploads/ImageVideoDescription.kt`**: the `onClick` passes
   `stripMetadata` (the state initialised from `account.settings.stripLocationOnUpload`, line 170)
   instead of `effectiveStripMetadata`; delete the local. Keep the toggle hidden when
   `isVideoWithCompression` — the comment at line ~357 stays true, but extend it: "the choice is still
   passed through, because the encode can fall back to the original and the stripper is then the only
   strip." The hidden toggle keeps whatever the user last set while it was visible, exactly as the
   other two dialogs do.
2. **`commons/.../service/uploads/VideoUploadControls.kt`**: the KDoc on `videoWillBeCompressed` says
   "the strip toggle is moot". Reword: the toggle is hidden, not moot — the chosen value must still
   reach the uploader because a failed encode uploads the original.
3. **The decision in `stripAfterCompression`, made testable.** Extract the pure rule into commons,
   `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/service/uploads/StripDecision.kt`:

   ```kotlin
   /** Whether the uploader runs the metadata stripper on what the compressor handed back. */
   object StripDecision {
       /**
        * A video the compressor really re-encoded already lost its metadata; anything else that the
        * user asked to strip — an image, a video uploaded as is, or a video whose encode fell back to
        * the original — still goes through the stripper.
        */
       fun shouldStrip(stripMetadata: Boolean, isVideo: Boolean, compressionApplied: Boolean): Boolean =
           stripMetadata && !(isVideo && compressionApplied)
   }
   ```

   `stripAfterCompression` keeps its shape and calls it: `if (!stripMetadata) return compressed.uri`
   stays as the early exit (or folds into the call — your choice, say which), the
   `compressionApplied` computation stays where it is, and the `if (isVideo && compressionApplied)`
   branch becomes `if (!StripDecision.shouldStrip(...))`. No behaviour change on any existing path.
   `cli` must not gain a dependency; `commons` is already where this belongs (headless rule, no
   Android types).

Not in scope: changing which dialogs show the toggle, the dialog's `stripMetadata` default, desktop,
`MediaCompressor`, `VideoCompressionHelper`'s fallbacks themselves, strings.

## Tests

- `commons/src/jvmTest/.../service/uploads/StripDecisionTest.kt` (kotlin.test, like
  `VideoUploadControlsTest`): strip on + video + applied → false (compressor is the strip); strip on +
  video + **not** applied (the fallback) → true; strip on + image + applied → true; strip off → false
  for every combination.
- `VideoUploadControlsTest`: no behaviour change; the existing `uncompressedKeepsTheStripToggle` still
  passes. Add nothing unless you touch the class's logic.
- No Compose test for the dialog: the project has no Compose UI test harness for `commonsUI`, and the
  change is one identifier. Say so in the report.
- Static: `./gradlew spotlessApply :commons:jvmTest --tests "*StripDecision*" --tests "*VideoUploadControls*"
  :amethyst:testPlayDebugUnitTest --tests "*MetadataStripper*" :amethyst:compilePlayDebugKotlin
  :amethyst:lintPlayDebug`. Do not run `./gradlew --stop` (shared daemons; another session may be
  running the desktop app).
- Device (optional, stop and ask the user first — their phone): the "not smaller" fallback is easy to
  trigger. Take a small, already low-bitrate clip (e.g. a 240p/300 kbps MP4 a few seconds long), post it
  at **HIGH** with strip on from the benchmark build: the encode comes out larger, the app toasts
  "Compressed file larger than original. Using original.", and with this fix the blob on the local
  haven (`~/code/haven/blossom/`) is `ftyp, moov, …` with no `udta`/location; before the fix it is the
  source verbatim. `ffprobe -v error -show_entries format_tags -of flat <blob>` must show no
  `location`. If no device/user is at hand, skip and say so.

## Decisions for the implementer session

1. **Extract `StripDecision` vs. test nothing.** Recommended: extract. Without it the bug has no test,
   and the rule is three booleans. If you find the extraction touches more than the one `if` in
   `stripAfterCompression`, stop and say what it drags along.
2. **Hidden toggle keeps the last visible value vs. resets to the account default when hidden.**
   Recommended: keep the last visible value — it is what `NewMediaView` and `ChatFileUploadDialog`
   do, and it honours an explicit choice made moments earlier in the same dialog.
3. **Commit split.** Recommended: one `fix(uploads):` commit for 1–3 and the tests together; a
   separate `refactor` commit only if `/simplify` finds something.

## Report

Append a section "8i5: compression fallback honours the strip" to
`amethyst/plans/2026-11-09-dsr-sanitizer-delegation-report.md` (same branch, same PR), or write
`2026-11-12-8i5-fallback-strip-report.md` beside this brief if it grows past a page: commits, the three
decisions, test counts, the device result if run, and remove the dsr report's sentence that row 4 is
unreachable from the main dialog (it now is reachable — say so in its Findings instead).
