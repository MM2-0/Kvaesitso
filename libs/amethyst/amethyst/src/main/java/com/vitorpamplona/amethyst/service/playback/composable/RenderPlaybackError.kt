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
package com.vitorpamplona.amethyst.service.playback.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.error_video_open_in_browser
import com.vitorpamplona.amethyst.commons.resources.error_video_playback_failed
import com.vitorpamplona.amethyst.commons.resources.error_video_playback_failed_description
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.utils.Log

/**
 * Overlay shown on top of the video surface when the MediaController reports an unrecoverable
 * playback error (codec init failure, unsupported format, decoder error, etc).
 *
 * Offers an "Open in browser" fallback that hands the URL to the system browser via
 * [LocalUriHandler] so the user can still consume the content even if the on-device codec
 * stack can't.
 *
 * Reads error state from [MediaControllerState.playbackError]; populated by [WatchPlaybackErrors].
 */
@Composable
fun RenderPlaybackError(
    controllerState: MediaControllerState,
    videoUri: String,
    modifier: Modifier = Modifier,
) {
    val error by controllerState.playbackError
    val current = error ?: return

    val uriHandler = LocalUriHandler.current
    val errorCodeName = remember(current) { current.errorCodeName }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
    ) {
        // The button is the overlay's whole point, so it must never be the child that starves —
        // and a Column starves whoever is measured last. In the feed a 16:9 media box can be as
        // small as 322dp x 181dp, which used to squeeze the button (last in the stack) to 0.38dp.
        // Putting everything above it in one weighted block makes the Column measure the button
        // first and hand only the leftover to the text, so the squeeze structurally cannot land
        // on the button at any box height, font scale, or locale. The thresholds below only
        // decide how the text block degrades while it yields.
        val hasRoomForIcon = maxHeight >= MIN_HEIGHT_FOR_ICON * LocalDensity.current.fontScale
        val hasRoomForDescription = maxHeight >= MIN_HEIGHT_FOR_DESCRIPTION
        val padding = if (maxHeight >= MIN_HEIGHT_FOR_FULL_PADDING) 16.dp else 8.dp

        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (hasRoomForIcon) {
                    Icon(
                        symbol = MaterialSymbols.VideocamOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White,
                    )

                    Spacer(Modifier.height(12.dp))
                }

                Text(
                    text = stringRes(Res.string.error_video_playback_failed),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )

                if (hasRoomForDescription) {
                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = stringRes(Res.string.error_video_playback_failed_description, errorCodeName),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            FilledTonalButton(
                onClick = {
                    runCatching { uriHandler.openUri(videoUri) }
                        .onFailure { Log.w("RenderPlaybackError", "openUri failed for $videoUri", it) }
                },
            ) {
                Icon(
                    symbol = MaterialSymbols.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringRes(Res.string.error_video_open_in_browser))
            }
        }
    }
}

/**
 * Smallest overlay height that still fits the 48dp icon and its 12dp spacer on top of the title,
 * one line of description, the 16dp gap and the 40dp button, inside 16dp of padding. Below this
 * the icon is the first thing to go, because it is the only part carrying no information.
 *
 * Multiplied by the current `fontScale` at the call site, because everything it is budgeting
 * against is text. The icon is non-weighted and declared first, so within the weighted block it is
 * measured before the title — left unscaled, a box of 190dp to 199dp at fontScale 2 kept the icon
 * and sliced the title to pay for it. Scaling over-corrects slightly, since the icon and paddings
 * are fixed dp, but this threshold is cosmetic-only now: erring towards dropping decoration is the
 * harmless direction.
 */
private val MIN_HEIGHT_FOR_ICON = 190.dp

/**
 * Below this the 16dp padding is itself crowding out the text — a very wide, short video (a
 * panorama, or anything past about 2.5:1) leaves barely more height than the title and button
 * need. Halving the padding there gives the title room to draw whole.
 */
private val MIN_HEIGHT_FOR_FULL_PADDING = 150.dp

/**
 * Below this the description cannot fit even one full line, and a weighted Text handed less than a
 * line's height draws it sliced in half rather than dropping it. Hide it instead: the title still
 * says what went wrong and the button still offers the way out.
 */
private val MIN_HEIGHT_FOR_DESCRIPTION = 120.dp
