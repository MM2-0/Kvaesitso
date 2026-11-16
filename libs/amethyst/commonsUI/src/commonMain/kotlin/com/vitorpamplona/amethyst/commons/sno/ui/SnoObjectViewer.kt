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
package com.vitorpamplona.amethyst.commons.sno.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.blurhash.PlatformImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.sno_light_off
import com.vitorpamplona.amethyst.commons.resources.sno_light_on
import com.vitorpamplona.amethyst.commons.service.image.toComposeImageBitmap
import com.vitorpamplona.amethyst.commons.sno.SnoLighting
import com.vitorpamplona.amethyst.commons.sno.SnoRasterScratch
import com.vitorpamplona.amethyst.commons.sno.SnoRasterizer
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoMode
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** How many degrees a one-finger drag of one pixel turns the object. */
private const val DEGREES_PER_PIXEL = 0.5f

/** Past straight up or straight down there is nothing more to see. */
private const val MAX_PITCH = 89f

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 8f

/**
 * The raster size while a gesture is in flight.
 *
 * Turning an object is the thing this view is for, so the frames during a turn
 * are the ones that must not stutter. They are also the ones nobody is
 * inspecting closely: the sharp frame is the one you stop on. So the raster
 * drops while a finger is down and goes back to full size when it lifts.
 */
private const val GESTURE_RASTER_PX = 384

/**
 * The largest raster produced when the object is at rest, before zoom.
 *
 * A real object at 1080px costs under 5ms, so full resolution is affordable on
 * anything a phone will ask for; this only bounds the memory a very large
 * window or a deep zoom could otherwise demand.
 */
private const val MAX_RESTING_RASTER_PX = 1440

/** The light switch, small enough to sit over a corner of the object. */
private val TOGGLE_SIZE = 32.dp
private val TOGGLE_INSET = 4.dp

/**
 * A Simple Nostr Object the reader can turn, move and zoom.
 *
 * One finger turns it, two move and scale it, and a double tap puts it back.
 *
 * Only turning needs a new raster — it changes which faces point at you.
 * Moving and scaling are a [graphicsLayer] transform of the frame already
 * drawn, which costs nothing per frame; when the gesture ends the object is
 * redrawn at the resolution the zoom now deserves, so it sharpens where it
 * settled rather than staying an upscaled bitmap.
 *
 * A solid object also offers a light switch, because turning something over to
 * understand its shape is the one case DECK-0003 §4 has in mind when it allows
 * a client to light an object: unlit, a cube of a single colour is a flat
 * hexagon however far you turn it. The feed keeps §4's unlit default, so an
 * object looks the same everywhere it is quoted, and the switch starts off.
 * See [SnoLighting] for what it does and what it costs.
 *
 * Inside a feed card it is [zoomable] `false`: every drag turns the object,
 * since pinching or moving it there would only push it out of its own card.
 * Which way a drag goes is settled by where it starts. One that starts on the
 * object turns it and is consumed from its first move, so the feed never sees
 * enough of it to scroll; one that starts outside belongs to the feed, which
 * keeps scrolling even as it passes over the object, because a pointer's
 * events go to what was under it when it went down.
 *
 * Unlike [SnoThumbnail] this does not go through Coil: a turn produces a new
 * angle every frame and each would become its own cache entry, evicting the
 * image cache within a second. Nothing here is worth caching — the object is
 * the event, and redrawing it is cheaper than remembering it.
 */
@Composable
fun SnoObjectViewer(
    payload: SnoPayload,
    eventId: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    background: Color = Color.Transparent,
    zoomable: Boolean = true,
    onTap: (() -> Unit)? = null,
) {
    var yaw by remember(eventId) { mutableFloatStateOf(SnoRasterizer.DEFAULT_YAW_DEGREES) }
    var pitch by remember(eventId) { mutableFloatStateOf(SnoRasterizer.DEFAULT_PITCH_DEGREES) }
    var zoom by remember(eventId) { mutableFloatStateOf(1f) }
    var panX by remember(eventId) { mutableFloatStateOf(0f) }
    var panY by remember(eventId) { mutableFloatStateOf(0f) }
    var gesturing by remember(eventId) { mutableStateOf(false) }
    var lit by remember(eventId) { mutableStateOf(false) }
    // Winding the faces outward walks a ray from every face against every other
    // one, so it is worth its own cache: it depends on the object alone and not
    // on the angle, and a turn would otherwise pay for it sixty times a second.
    val lighting = remember(payload) { LightingCache() }
    val backgroundArgb = if (background == Color.Transparent) 0 else background.toArgb()
    val currentOnTap by rememberUpdatedState(onTap)
    val hasTap = onTap != null

    BoxWithConstraints(
        modifier =
            modifier
                .clipToBounds()
                .pointerInput(eventId, zoomable) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        gesturing = true
                        try {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val moved = event.changes.any { it.positionChanged() }
                                if (moved) {
                                    if (zoomable && event.changes.size > 1) {
                                        // Two fingers move and scale the object.
                                        val scale = event.calculateZoom()
                                        if (scale != 1f) zoom = (zoom * scale).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                        val pan = event.calculatePan()
                                        panX += pan.x
                                        panY += pan.y
                                    } else {
                                        // One finger turns it.
                                        val pan = event.calculatePan()
                                        yaw = wrapDegrees(yaw + pan.x * DEGREES_PER_PIXEL)
                                        pitch = (pitch - pan.y * DEGREES_PER_PIXEL).coerceIn(-MAX_PITCH, MAX_PITCH)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            gesturing = false
                        }
                    }
                }.pointerInput(eventId, hasTap) {
                    detectTapGestures(
                        // Only when there is somewhere to go, so a tap with nothing to
                        // do is not delayed waiting to rule out a double tap.
                        onTap = if (hasTap) ({ currentOnTap?.invoke() }) else null,
                        onDoubleTap = {
                            yaw = SnoRasterizer.DEFAULT_YAW_DEGREES
                            pitch = SnoRasterizer.DEFAULT_PITCH_DEGREES
                            zoom = 1f
                            panX = 0f
                            panY = 0f
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        val onScreenPx = with(LocalDensity.current) { minOf(maxWidth, maxHeight).roundToPx() }
        if (onScreenPx <= 0) return@BoxWithConstraints

        // Coarse while a finger is down, full — and scaled by how far in the
        // reader has zoomed — once it lifts.
        val restingPx = minOf((onScreenPx * zoom).toInt(), MAX_RESTING_RASTER_PX)
        val rasterPx = if (gesturing) minOf(onScreenPx, GESTURE_RASTER_PX) else restingPx

        // Held across angles rather than recomputed in composition: the previous
        // frame stays on screen while the next is drawn, so a heavy object makes
        // the turn coarse instead of freezing the gesture.
        var bitmap by remember(eventId) { mutableStateOf<ImageBitmap?>(null) }

        // The buffers every frame of the turn draws into. Held here rather than
        // allocated per frame: see [SnoRasterScratch] — a drag that allocates
        // them spends more of the device on collecting the last frame's pair
        // than on drawing the next one.
        val scratch = remember(eventId) { SnoRasterScratch() }

        // One collector, and angles conflated into it. A finger produces a new
        // angle far faster than this device draws one, and `render` runs to
        // completion whether or not anyone still wants it, so an effect
        // restarted per angle would leave several frames in flight at once —
        // all but one of them discarded, and all of them writing over each
        // other's `scratch`. Conflating keeps only the newest angle waiting, so
        // exactly one frame is ever being drawn and the turn still lands on
        // wherever the finger actually stopped.
        LaunchedEffect(eventId, payload, rasterPx, backgroundArgb) {
            snapshotFlow { Triple(yaw, pitch, lit) }
                .conflate()
                .collect { (atYaw, atPitch, isLit) ->
                    bitmap =
                        withContext(Dispatchers.Default) {
                            val pixels =
                                SnoRasterizer.render(
                                    payload = payload,
                                    width = rasterPx,
                                    height = rasterPx,
                                    yawDegrees = atYaw,
                                    pitchDegrees = atPitch,
                                    background = backgroundArgb,
                                    lighting = if (isLit) lighting.of(payload) else null,
                                    scratch = scratch,
                                )
                            // Copies into the bitmap, so `scratch` is free to be
                            // drawn over by the next frame.
                            PlatformImage.create(pixels, rasterPx, rasterPx).toComposeImageBitmap()
                        }
                }
        }

        bitmap?.let { drawn ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = drawn,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoom,
                                scaleY = zoom,
                                translationX = panX,
                                translationY = panY,
                            ),
                )
            }
        }

        // Only a filled object has faces for a light to fall on; points and
        // wireframes look the same lit or not, so they are not offered a switch.
        if (payload.mode == SnoMode.SOLID && payload.faceCount > 0) {
            IconButton(
                onClick = { lit = !lit },
                modifier = Modifier.align(Alignment.TopEnd).padding(TOGGLE_INSET).size(TOGGLE_SIZE),
            ) {
                Icon(
                    MaterialSymbols.BrightnessMedium,
                    contentDescription = stringResource(if (lit) Res.string.sno_light_on else Res.string.sno_light_off),
                    tint =
                        if (lit) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    filled = lit,
                )
            }
        }
    }
}

/**
 * One object's lighting, kept until the object changes.
 *
 * Not Compose state: it is written from the drawing coroutine and read only
 * there, and making it state would recompose the view for a value nothing in
 * composition looks at.
 */
private class LightingCache {
    private var lighting: SnoLighting? = null

    fun of(payload: SnoPayload): SnoLighting = lighting ?: SnoLighting.of(payload).also { lighting = it }
}

private fun wrapDegrees(value: Float): Float {
    var wrapped = value % 360f
    if (wrapped < 0f) wrapped += 360f
    return wrapped
}
