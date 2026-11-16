# Hand-drawn posts

Draw by hand in the composer and post the drawing as a looping GIF that replays the strokes.

## What the user gets

- A **Draw** button in the short-note composer opens a full-window editor (`DrawingEditorDialog`)
  in two steps:
  - **Draw**: the paper fills the window. A floating dock (`DrawingDock`) holds a tray of
    illustrated pens tipped with the current ink (the one in hand springs up), mirror, undo/redo,
    the inks and a size button with a live stroke preview. A chip in the top bar opens the paper
    sheet (`PaperSheet`), which shows real samples of every paper and the user's saved pictures.
  - **Preview**: the drawing replays exactly as the posted GIF will (`ReplayPreview`, using
    `DrawingReplay.replayTimeAt`). The alt text and "Add to post" live here.
- Tools: brush (follows stylus pressure), pencil, spray, eraser, mirror; eight inks; pen size.
- Backgrounds: plain paper, four sticky notes (square, with a glue strip), a notebook page,
  grid paper, a dot grid, a chalkboard, or **the user's own picture** to draw on. Pictures are
  kept on the device (`DrawingBackgroundStore`) and come back in the row next time, so a user can
  reuse their own "stationery".
- An alt-text field in the preview step, since a picture of handwriting is unreadable to a screen
  reader without it. Where the platform has an on-device describer (`AppServices.createAltTextSuggester`:
  Gemini Nano on the Play build), the preview fills it with a description of the finished drawing,
  marked "AI-suggested" like the upload screen; the user's own words are never overwritten.
- **Add to post** renders the replay GIF and a PNG of the finished drawing, uploads both and puts
  the GIF's URL in the note, with an `imeta` the uploader could not have built on its own.

## Why a GIF

The post has to work in every client, not only in Amethyst. GIF is the one animated format that every
Nostr client and browser plays inline, with no player controls and no buffering. Amethyst decodes it
on Android (`GifDecoder`) and on desktop (`SkiaGifDecoder`); animated WebP only animates on Android
here, and an MP4 would show up as a video player. Drawings fit GIF's limits: ink on paper needs far
fewer than 256 colours, and each replay frame changes a small patch of the picture.

What was considered and set aside:

- **SVG**: Amethyst renders it, but most other clients and many media servers refuse SVG.
- **Stroke data inside the event** (replay, remix, draw-on-reply): it needs a new kind or tag, which
  is a protocol change for the maintainer to approve, and other clients would still need an image.
  The editor keeps strokes in a form (`DrawingStroke`) that could be serialised later.
- **Simple Nostr Objects (kind 33331)** in `lines` mode: one polyline with no pen lifts, no width, no
  timing, and other clients would show it as a 3D object.

## Pieces

| Piece | Module | Notes |
|---|---|---|
| `DrawingStroke`, `DrawingPaper`, `DrawingBackground` | commons | Points as fractions of the canvas width, with timestamps |
| `DrawingEditorState` | commons | Snapshot state: background, pen, strokes, undo/redo |
| `DrawingReplay` | commons | Replay timing: cuts pauses (150 ms) and gaps (300 ms), fits ~3.2 s, 0.4 s lead-in, 2.5 s hold |
| `AnimatedGifEncoder`, `GifPalette`, `GifLzw` | commons | Pure Kotlin GIF89a writer. Later frames store only the changed rectangle, with unchanged pixels transparent |
| `DrawingRenderer` (`drawDrawing`, `BackgroundPainter`) | commonsUI | One renderer for the live canvas, every GIF frame and the PNG |
| `ReplayFrameRenderer` | commonsUI | Replay frames in constant time: finished strokes in a layer that only grows, the background drawn once, one half-drawn stroke on top. Used by the export and the preview |
| `DrawingExporter` | commonsUI | Renders frames offscreen (`ImageBitmap` + `CanvasDrawScope` + `readPixels`) at 540 px wide |
| `DrawingBackgroundStore` | commons | Picked pictures as cropped PNGs, one folder per account under the host's `drawingBackgroundsDir`, newest 12 kept |
| `PhotoBackgroundLoader` | commonsUI | Coil decode (upright on Android), centre crop to 4:3..2:3, 1080 px wide |
| `encodeArgbAsPng` | commonsUI | `Bitmap.compress` on Android, Skia on desktop/iOS |
| `ShortNotePostViewModel.uploadDrawing` | commonsUI | Uploads both files, builds the GIF's `imeta` |

### Strokes over backgrounds

Strokes are drawn on their own layer above the background (`inStrokeLayer`, a `saveLayer`), and the
eraser is `BlendMode.Clear` in that layer. That is what lets the eraser reveal a photo rather than
paint over it. The live canvas keeps finished strokes in a transparent bitmap that only repaints on
undo, clear or resize, and draws the stroke under the finger on top.

### Palette

`GifPalette.Builder` counts the colours of the finished drawing plus about 12 frames sampled across
the replay, because ink that is erased or painted over never reaches the last frame. When it has at most 255 colours (5-bit buckets), the
palette is exact and nothing is dithered. Over a photo it uses median cut plus a 4x4 Bayer dither.
The dither depends only on a pixel's position and colour, so an unchanged pixel maps to the same
index in every frame and the per-frame patches stay small.

### Upload

The composer's batch upload is not used, for three reasons:

- It would re-encode the PNG as JPEG at the default quality.
- On Android it sends the GIF through EXIF stripping, which fails on GIF.
- It measures blurhash and dims from the first frame, which is blank paper.

`uploadDrawing` instead uploads each file `UNCOMPRESSED` with `stripMetadata = false`. It then
writes the GIF's `imeta` with:

- the dims and the blurhash/thumbhash of the finished drawing;
- `image` = the PNG URL (NIP-94's preview field), for clients that show a still;
- `alt`.

A failed PNG upload does not block the post. NIP-95 servers get the GIF as an embedded event, as
other uploads do.

Measured on desktop: a two-stroke sticky note replays in 61 frames as a 16 KB GIF (the PNG is 27 KB).
A drawing over a 1080 px photo makes a 140 KB GIF (the PNG is 390 KB).

## Saved backgrounds

A picture the user draws on is saved, already cropped and scaled, as
`<drawingBackgroundsDir>/<account pubkey>/<saved at ms>_<sha256 prefix>.png`:

- **Location:** `filesDir/drawing_backgrounds` on Android, the desktop data dir on desktop.
- **One folder per account,** so one account's pictures never show in another account's editor on
  the same device.
- **Name:** the timestamp orders the row and the hash avoids duplicates. Only the newest 12 are kept.
- **Removing:** a badge on the selected thumbnail removes the picture.
- **Device only:** saved pictures are never uploaded unless they end up in a posted drawing.

## Not done yet

- Syncing saved backgrounds across devices (e.g. on the user's Blossom server plus a list event),
  which needs a NIP decision.

- Reading `imeta image` as a still preview for GIFs in Amethyst's own feed (it already animates them).
- Stroke data in the event (see above), which needs a NIP decision.
- Saving an unfinished drawing; closing the editor asks before discarding.
