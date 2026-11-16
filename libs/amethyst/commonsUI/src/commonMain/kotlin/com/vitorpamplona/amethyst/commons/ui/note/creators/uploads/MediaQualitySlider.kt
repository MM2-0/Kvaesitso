/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.ui.note.creators.uploads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.media_compression_images_up_to
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_compressed
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_original
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_tap_compressed
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_tap_original
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_tap_zoom
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_uncompressed
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_zoom_hint
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_high
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_low
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_medium
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_uncompressed
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_very_high
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_very_low
import com.vitorpamplona.amethyst.commons.resources.media_compression_video_target
import com.vitorpamplona.amethyst.commons.resources.media_compression_video_target_example
import com.vitorpamplona.amethyst.commons.resources.video_compression_needs_ffmpeg
import com.vitorpamplona.amethyst.commons.resources.video_hdr_converted
import com.vitorpamplona.amethyst.commons.resources.video_hdr_converted_codec
import com.vitorpamplona.amethyst.commons.resources.video_hdr_converted_device
import com.vitorpamplona.amethyst.commons.resources.video_hdr_kept
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.HdrUploadOutcome
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCompressionPreview
import com.vitorpamplona.amethyst.commons.service.uploads.ImageFileStats
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.VideoAwareSliderSteps
import com.vitorpamplona.amethyst.commons.service.uploads.VideoUploadControls
import com.vitorpamplona.amethyst.commons.service.uploads.hdrUploadOutcome
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoCompressionRules
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoHdrInfo
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoInfo
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoStandard
import com.vitorpamplona.amethyst.commons.ui.components.edgeToEdgeDialogProperties
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.util.formatFileSize
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import net.engawapg.lib.zoomable.MouseWheelZoom
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import coil3.size.Size as CoilSize

/**
 * The media-quality slider, one stop per [CompressorQuality.sliderSteps] level. With a video in
 * [media] on a platform that compresses video, only the levels with a video rule are offered (the
 * phone layout) and the label describes what the picked video becomes; a still image adds the
 * size images shrink to. When [media] holds a still image the platform re-encodes, the slider also
 * shows that image compressed at the chosen level, with its size and pixel dimensions beside the
 * original's; tapping the preview opens both full screen, zoomable, to compare the detail.
 */
@Composable
fun MediaQualitySlider(
    position: Int,
    onPositionChange: (Int) -> Unit,
    media: MultiOrchestrator?,
    uploader: MediaUploader,
    modifier: Modifier = Modifier,
) {
    // Not hasVideo() alone: where the platform will not compress the video, the slider governs
    // only the images, keeps their stops and promises nothing about the video.
    val videoMode = VideoUploadControls(media, uploader.videoCompression, position).compressesVideo
    val allSteps = CompressorQuality.sliderSteps
    val steps = remember(videoMode) { VideoAwareSliderSteps.stepsFor(allSteps, videoMode) }
    val platformPosition = position.coerceIn(0, allSteps.lastIndex)
    val quality = CompressorQuality.fromSlider(platformPosition)

    // Entering video mode on a level without a video rule moves to the one the encoder would use.
    LaunchedEffect(videoMode) {
        if (videoMode) VideoAwareSliderSteps.snapForVideo(allSteps, platformPosition).let { if (it != platformPosition) onPositionChange(it) }
    }

    val video = if (videoMode) media?.firstVideo() else null
    val info by produceState<VideoInfo?>(null, video) { value = video?.let { uploader.probeVideo(it.uri) } }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = qualityLabel(quality, hasCompressibleImage = media?.hasCompressibleImage() == true, videoMode = videoMode, info = info),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Slider(
            value = VideoAwareSliderSteps.toDisplayedIndex(allSteps, platformPosition, videoMode).toFloat(),
            // Rounded, not truncated: a tick's value comes from the track geometry, and with four stops
            // the thirds land a hair under their integer, so truncation skipped a stop.
            onValueChange = { onPositionChange(VideoAwareSliderSteps.fromDisplayedIndex(allSteps, it.roundToInt().coerceIn(0, steps.lastIndex), videoMode)) },
            valueRange = 0f..steps.lastIndex.toFloat(),
            steps = (steps.size - 2).coerceAtLeast(0),
        )

        // Read each time: removing an image changes the same orchestrator, so a remembered pick goes stale.
        val image = media?.let { firstStillImage(it) }
        // Stays composed on the uncompressed level too, so its cached previews survive a trip there and back.
        if (image != null) {
            CompressionPreview(image, quality, uploader)
        }
    }
}

/** `High`, `High · images up to 1920 px`, `High · 4K video → 2160p, 8 Mbps`, or all three for a mixed post. */
@Composable
private fun qualityLabel(
    quality: CompressorQuality,
    hasCompressibleImage: Boolean,
    videoMode: Boolean,
    info: VideoInfo?,
): String {
    val name =
        when (quality) {
            CompressorQuality.VERY_LOW -> stringRes(Res.string.media_compression_quality_very_low)
            CompressorQuality.LOW -> stringRes(Res.string.media_compression_quality_low)
            CompressorQuality.MEDIUM -> stringRes(Res.string.media_compression_quality_medium)
            CompressorQuality.HIGH -> stringRes(Res.string.media_compression_quality_high)
            CompressorQuality.VERY_HIGH -> stringRes(Res.string.media_compression_quality_very_high)
            CompressorQuality.UNCOMPRESSED -> stringRes(Res.string.media_compression_quality_uncompressed)
        }
    val imagePart = quality.imageMaxDimension?.takeIf { hasCompressibleImage }?.let { stringRes(Res.string.media_compression_images_up_to, it) }
    val videoPart = if (videoMode && quality != CompressorQuality.UNCOMPRESSED) videoPhrase(quality, info) else null
    return listOfNotNull(name, imagePart, videoPart).joinToString(" · ")
}

/** The picked video's own standard and target; the 1080p row, named as an example, when it could not be probed. */
@Composable
private fun videoPhrase(
    quality: CompressorQuality,
    info: VideoInfo?,
): String? {
    val standard = info?.resolution?.getStandard()
    if (standard != null) {
        VideoCompressionRules.ruleFor(quality, standard)?.let { rule ->
            return stringRes(Res.string.media_compression_video_target, standard.label, rule.shortSide, mbps(rule.bitrateMbps))
        }
    }
    val rule = VideoCompressionRules.ruleFor(quality, VideoStandard.FHD_1080P) ?: return null
    return stringRes(Res.string.media_compression_video_target_example, rule.shortSide, mbps(rule.bitrateMbps))
}

/** The one line a dialog shows in place of the video controls when the platform needs ffmpeg to compress; nothing otherwise. */
@Composable
fun VideoCompressionNeedsFfmpegHint(
    controls: VideoUploadControls,
    modifier: Modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
) {
    if (!controls.needsFfmpeg) return
    UploadHintLine(stringRes(Res.string.video_compression_needs_ffmpeg), modifier)
}

/**
 * The line under the H.265 switch saying what compression does to a picked HDR video; nothing when
 * no picked video is HDR, or the platform cannot tell. Every picked video is probed, so a post
 * with an SDR clip first and an HDR clip second still gets the line. [offersH265] is false where
 * the dialog has no codec switch, so the line doesn't tell the user to turn one on.
 */
@Composable
fun VideoHdrHint(
    media: MultiOrchestrator?,
    uploader: MediaUploader,
    useH265: Boolean,
    offersH265: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
) {
    // Read each time, like the slider's image: removing a video changes the same orchestrator.
    val videos = media?.videos().orEmpty()
    val probes by
        produceState(emptyList<VideoHdrInfo>(), videos) {
            value = coroutineScope { videos.map { async { uploader.probeHdr(it.uri) } }.awaitAll().filterNotNull() }
        }
    val outcome = hdrUploadOutcome(probes, useH265) ?: return
    val text =
        when (outcome) {
            HdrUploadOutcome.KEPT -> Res.string.video_hdr_kept
            HdrUploadOutcome.CONVERTED_BY_CODEC -> if (offersH265) Res.string.video_hdr_converted_codec else Res.string.video_hdr_converted
            HdrUploadOutcome.CONVERTED_BY_DEVICE -> Res.string.video_hdr_converted_device
        }
    UploadHintLine(stringRes(text), modifier)
}

/** The small grey line the upload dialogs use for a note about the picked video. */
@Composable
private fun UploadHintLine(
    text: String,
    modifier: Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Color.Gray,
        modifier = modifier,
    )
}

/** `2`, `0.75`, `8`: no trailing `.0`. */
private fun mbps(value: Float): String = value.toString().removeSuffix(".0")

private fun firstStillImage(media: MultiOrchestrator): SelectedMedia? =
    (0 until media.size())
        .asSequence()
        .map { media.get(it).media }
        .firstOrNull { it.isImage() == true && it.isCompressible() }

private val PreviewHeight = 260.dp

/**
 * The longest edge the zoomed original decodes to: above the largest compressed size (2560), so
 * the original always shows the detail the compression loses, and small enough that even a 16-bit
 * PNG (8 bytes a pixel, 75 MB at this size) stays under Android's 100 MB limit on a drawn bitmap.
 */
private const val MAX_ZOOM_DECODE_EDGE = 3072

private fun ImageFileStats.aspectRatio(): Float? {
    val w = width ?: return null
    val h = height ?: return null
    return if (w > 0 && h > 0) w.toFloat() / h else null
}

private fun ImageFileStats.longEdge(): Int? {
    val w = width ?: return null
    val h = height ?: return null
    return max(w, h)
}

/**
 * Compresses [image] at [quality] in the background, after the slider has rested for a moment, and
 * keeps each result for as long as the composer shows this image, so moving back to a level shows
 * it at once. The temp files go when the composer drops the image.
 *
 * Once a first preview is on screen the block keeps its size: the image box has a fixed height, the
 * last result stays up (under a spinner) while the next level compresses, and the uncompressed level
 * shows the original in the same box instead of removing it. Tapping the picture opens the
 * comparison full screen.
 */
@Composable
private fun CompressionPreview(
    image: SelectedMedia,
    quality: CompressorQuality,
    uploader: MediaUploader,
) {
    // A level maps to null when the platform could not compress the image.
    val results = remember(image) { mutableStateMapOf<CompressorQuality, ImageCompressionPreview?>() }
    var lastPreview by remember(image) { mutableStateOf<ImageCompressionPreview?>(null) }
    var zoomed by remember(image) { mutableStateOf(false) }

    DisposableEffect(image) {
        onDispose {
            results.values.forEach { preview -> preview?.let { uploader.discardTempFile(it.compressedUri) } }
            results.clear()
        }
    }

    val compresses = quality.imageMaxDimension != null

    LaunchedEffect(image, quality) {
        if (!compresses || results.containsKey(quality)) return@LaunchedEffect
        delay(300)
        // Cancelled when the slider moves on or the composer closes, so an abandoned preview stops
        // decoding (on desktop it would hold up the upload behind it); the uploader deletes what it
        // wrote when cancelled.
        results[quality] = uploader.previewImageCompression(image.uri, image.mimeType, quality)
    }

    val loading = compresses && !results.containsKey(quality)
    val current = if (compresses) results[quality] else null
    if (current != null && current !== lastPreview) {
        SideEffect { lastPreview = current }
    }

    // Nothing to compare: the platform can't compress this image, or no level was ever previewed.
    if (compresses && !loading && current == null) return
    if (!compresses && lastPreview == null) return

    val stats = current ?: lastPreview
    // While a level compresses, the previous level's result stays up so the box doesn't empty.
    val compressedUri = if (compresses) stats?.compressedUri?.toString() else null
    val originalUri = image.uri.toString()
    val zoomLabel = stringRes(Res.string.media_compression_preview_tap_zoom)

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // A fixed height, so the levels never change the layout; the inner box hugs the picture so
        // its rounded corners and tap target are the picture's, not the letterboxing's.
        Box(Modifier.fillMaxWidth().height(PreviewHeight), contentAlignment = Alignment.Center) {
            val ratio = stats?.original?.aspectRatio()
            Box(
                modifier =
                    Modifier
                        .then(if (ratio != null) Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true) else Modifier.fillMaxSize())
                        .clip(QuoteBorder)
                        .clickable(enabled = stats != null, onClickLabel = zoomLabel) { zoomed = true },
                contentAlignment = Alignment.Center,
            ) {
                ComparisonImages(originalUri, compressedUri, showOriginal = compressedUri == null, modifier = Modifier.fillMaxSize())

                if (loading) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    }
                }
            }
        }

        ComparisonLegend(
            hint =
                when {
                    !compresses -> stringRes(Res.string.media_compression_preview_uncompressed)
                    // The first level is still compressing: keep the line so the layout doesn't grow when it lands.
                    stats == null -> ""
                    else -> zoomLabel
                },
            original = stats?.original,
            compressed = if (compresses) stats?.compressed else null,
            showOriginal = compressedUri == null,
            stale = loading,
            hintColor = MaterialTheme.colorScheme.primary,
            highlightColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    if (zoomed) {
        CompressionZoomDialog(
            originalUri = originalUri,
            compressedUri = compressedUri,
            original = stats?.original,
            compressed = if (compresses) stats?.compressed else null,
            stale = loading,
            onDismiss = { zoomed = false },
        )
    }
}

/**
 * The original and the compressed image stacked in one box, so flipping between them is instant and
 * both sit at exactly the same place and scale. The original also covers for the compressed image
 * while a new level's file is still decoding, so the box never flashes empty.
 */
@Composable
private fun ComparisonImages(
    originalUri: String,
    compressedUri: String?,
    showOriginal: Boolean,
    modifier: Modifier = Modifier,
    fullResolution: Boolean = false,
    originalLongEdge: Int? = null,
) {
    var compressedReady by remember(compressedUri) { mutableStateOf(false) }

    // Coil decodes to the size on screen, so zooming would only magnify that resampled bitmap and
    // hide the very detail being compared. Zoomed, both decode at their real pixels: the compressed
    // file is small by construction, the original is capped so a huge photo can't run out of memory.
    val context = LocalPlatformContext.current
    val originalModel: Any =
        if (!fullResolution) {
            originalUri
        } else {
            remember(originalUri, originalLongEdge) {
                val edge = min(originalLongEdge ?: MAX_ZOOM_DECODE_EDGE, MAX_ZOOM_DECODE_EDGE)
                ImageRequest
                    .Builder(context)
                    .data(originalUri)
                    .size(edge, edge)
                    .precision(Precision.EXACT)
                    .build()
            }
        }
    val compressedModel: Any? =
        if (!fullResolution || compressedUri == null) {
            compressedUri
        } else {
            remember(compressedUri) {
                ImageRequest
                    .Builder(context)
                    .data(compressedUri)
                    .size(CoilSize.ORIGINAL)
                    .build()
            }
        }

    Box(modifier) {
        AsyncImage(
            model = originalModel,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().alpha(if (showOriginal || !compressedReady) 1f else 0f),
        )
        if (compressedModel != null) {
            AsyncImage(
                model = compressedModel,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { compressedReady = true },
                modifier = Modifier.fillMaxSize().alpha(if (showOriginal || !compressedReady) 0f else 1f),
            )
        }
    }
}

/**
 * The hint and the two stats lines. Every line is always laid out, empty when there is nothing to
 * say yet, so the block keeps its height across levels. [stale] dims the stats while they still
 * describe the previous level.
 */
@Composable
private fun ComparisonLegend(
    hint: String,
    original: ImageFileStats?,
    compressed: ImageFileStats?,
    showOriginal: Boolean,
    stale: Boolean,
    hintColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = hintColor,
            textAlign = TextAlign.Center,
        )
        StatsLine(
            label = stringRes(Res.string.media_compression_preview_compressed),
            stats = compressed,
            color = if (!showOriginal && !stale) highlightColor else Color.Gray,
        )
        StatsLine(
            label = stringRes(Res.string.media_compression_preview_original),
            stats = original,
            color = if (showOriginal) highlightColor else Color.Gray,
        )
    }
}

@Composable
private fun StatsLine(
    label: String,
    stats: ImageFileStats?,
    color: Color,
) {
    val parts =
        listOfNotNull(
            if (stats?.width != null && stats.height != null) "${stats.width} × ${stats.height}" else null,
            stats?.bytes?.let { formatFileSize(it) },
        )
    Text(
        text = if (stats == null) "" else "$label: " + parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

/**
 * The comparison full screen: pinch, scroll or double-tap to zoom into the detail compression costs,
 * tap to flip between compressed and original. One zoom state drives both images, so the flip keeps
 * the zoomed-in region in place.
 */
@Composable
private fun CompressionZoomDialog(
    originalUri: String,
    compressedUri: String?,
    original: ImageFileStats?,
    compressed: ImageFileStats?,
    stale: Boolean,
    onDismiss: () -> Unit,
) {
    var flipped by remember { mutableStateOf(false) }
    // Uncompressed uploads the original as is: there is nothing to flip to.
    val showOriginal = flipped || compressedUri == null

    Dialog(
        onDismissRequest = onDismiss,
        properties = edgeToEdgeDialogProperties(),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val zoomState = rememberZoomState()

            // Pan limits follow the picture, not the letterboxed screen around it.
            LaunchedEffect(original) {
                val width = original?.width
                val height = original?.height
                if (width != null && height != null) {
                    zoomState.setContentSize(Size(width.toFloat(), height.toFloat()))
                }
            }

            ComparisonImages(
                originalUri = originalUri,
                compressedUri = compressedUri,
                showOriginal = showOriginal,
                fullResolution = true,
                originalLongEdge = original?.longEdge(),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zoomable(
                            zoomState = zoomState,
                            mouseWheelZoom = MouseWheelZoom.Enabled,
                            onTap = { if (compressedUri != null) flipped = !flipped },
                        ),
            )

            IconButton(
                onClick = onDismiss,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
            ) {
                Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.close), tint = Color.White)
            }

            if (stale) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(24.dp)
                            .size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            }

            ComparisonLegend(
                hint =
                    listOfNotNull(
                        stringRes(Res.string.media_compression_preview_zoom_hint),
                        when {
                            compressedUri == null -> null
                            showOriginal -> stringRes(Res.string.media_compression_preview_tap_compressed)
                            else -> stringRes(Res.string.media_compression_preview_tap_original)
                        },
                    ).joinToString("\n"),
                original = original,
                compressed = compressed,
                showOriginal = showOriginal,
                stale = stale,
                hintColor = Color.White,
                highlightColor = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
