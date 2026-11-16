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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.captions_off
import com.vitorpamplona.amethyst.commons.resources.captions_unnamed_track
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

/**
 * The text tracks a player has, in the order the media declared them.
 *
 * A NIP-71 video can carry a `text-track` per language, so "captions" is not one thing to switch
 * on — it is a list to choose from. Groups the player reports as unsupported are dropped: offering
 * a language the decoder will refuse is worse than not offering it.
 */
@OptIn(UnstableApi::class)
internal fun getTextTrackChoices(tracks: Tracks): ImmutableList<CaptionChoice> {
    val choices = mutableListOf<CaptionChoice>()
    tracks.groups
        .filter { it.type == C.TRACK_TYPE_TEXT }
        .forEach { group ->
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                choices.add(
                    CaptionChoice(
                        group = group,
                        trackIndex = i,
                        language = format.language,
                        label = format.label,
                        isSelected = group.isTrackSelected(i),
                    ),
                )
            }
        }
    return choices.toImmutableList()
}

internal data class CaptionChoice(
    val group: Tracks.Group,
    val trackIndex: Int,
    val language: String?,
    val label: String?,
    val isSelected: Boolean,
) {
    /**
     * What to print for this track.
     *
     * The publisher's own label wins when there is one. Otherwise the language code is expanded
     * into the reader's locale — "en" is not a word to most people, "English" is — and only a
     * track that names neither falls back to a generic term.
     */
    fun displayName(fallback: String): String =
        label?.takeIf { it.isNotBlank() }
            ?: language?.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
            ?: fallback
}

@OptIn(UnstableApi::class)
internal fun selectTextTrack(
    player: Player,
    choice: CaptionChoice,
) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            // Clearing the type disable as well: picking a language is a request to see captions,
            // so it has to undo an earlier "off" rather than silently select a track nothing draws.
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(choice.group.mediaTrackGroup, choice.trackIndex))
            .build()
}

/**
 * The language menu, shown in place of a plain on/off toggle when a video offers more than one
 * caption track.
 */
@Composable
internal fun CaptionLanguagePopup(
    choices: ImmutableList<CaptionChoice>,
    captionsEnabled: Boolean,
    onSelectOff: () -> Unit,
    onSelectTrack: (CaptionChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val baseColors = ButtonDefaults.textButtonColors()
        val contentColor = MaterialTheme.colorScheme.onBackground
        val colors = remember(baseColors, contentColor) { baseColors.copy(contentColor = contentColor) }
        val unnamed = stringRes(Res.string.captions_unnamed_track)

        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextButton(colors = colors, onClick = onSelectOff) {
                Text(
                    stringRes(Res.string.captions_off),
                    fontWeight = if (!captionsEnabled) FontWeight(1000) else FontWeight(400),
                )
            }

            choices.forEach { choice ->
                TextButton(colors = colors, onClick = { onSelectTrack(choice) }) {
                    Text(
                        choice.displayName(unnamed),
                        fontWeight = if (captionsEnabled && choice.isSelected) FontWeight(1000) else FontWeight(400),
                    )
                }
            }
        }
    }
}
