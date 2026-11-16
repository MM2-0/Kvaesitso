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
package com.vitorpamplona.amethyst.commons.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.ChatLayoutType
import com.vitorpamplona.amethyst.commons.model.FeatureSetType
import com.vitorpamplona.amethyst.commons.model.NoteLayoutType
import com.vitorpamplona.amethyst.commons.state.UiSettingsState

/**
 * The display choices avatars read on almost every card, resolved once near the composition
 * root and handed down through [LocalDisplaySettings], so shared composables do not need the
 * account's ViewModel to follow them.
 *
 * The defaults are what a front end without these settings (previews, desktop today) gets:
 * everything on.
 */
@Immutable
data class DisplaySettings(
    /** Load profile pictures (off on metered connections when the user asked for WIFI_ONLY). */
    val showProfilePictures: Boolean = true,
    /** Performance mode: skip robohashes, crossfades and other decoration. */
    val performanceMode: Boolean = false,
    /** Play videos and animated images (GIF/AVIF avatars) without a tap. */
    val autoPlayVideos: Boolean = true,
    /** How notes place their author and media. */
    val noteLayout: NoteLayoutType = NoteLayoutType.AUTHOR_COLUMN,
    /** Complete UI mode: a note's header adds a second row (NIP-05, status or npub) under the name. */
    val completeUiMode: Boolean = false,
    /** How chat rooms draw their messages: bubbles, compact IRC-style lines, or open text. */
    val chatLayout: ChatLayoutType = ChatLayoutType.BUBBLES,
) {
    /** Robohash fallbacks are skipped in performance mode (a plain face icon instead). */
    val loadRobohash: Boolean get() = !performanceMode

    /** Notes put the author above a full-width body instead of in a picture column beside it. */
    val fullWidthNotes: Boolean get() = noteLayout != NoteLayoutType.AUTHOR_COLUMN

    /** Full-width notes also run their own images and videos out to the screen edges. */
    val edgeToEdgeMedia: Boolean get() = noteLayout == NoteLayoutType.EDGE_TO_EDGE

    /**
     * The author picture of a full-width note's header, sized to the rows beside it: 35dp next to
     * the name and its second row, 30dp next to the name alone, where 35dp would leave the one
     * row floating in a band taller than it needs.
     */
    val fullWidthPictureSize: Dp get() = fullWidthHeaderPictureSize(twoRows = completeUiMode)
}

/** The full-width header's author picture beside [twoRows] header rows, or beside one. */
fun fullWidthHeaderPictureSize(twoRows: Boolean): Dp = if (twoRows) 35.dp else 30.dp

// Not static: showProfilePictures follows the connection type (Wi-Fi only), so this changes when the
// network does, and only its readers should recompose, not everything under the theme.
val LocalDisplaySettings = compositionLocalOf { DisplaySettings() }

/** Collects [settings] into a [DisplaySettings] that follows every change to it. */
@Composable
fun collectDisplaySettings(settings: UiSettingsState): DisplaySettings {
    val showProfilePictures by settings.showProfilePictures.collectAsStateWithLifecycle()
    val featureSet by settings.uiSettingsFlow.featureSet.collectAsStateWithLifecycle()
    val autoPlayVideos by settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    val noteLayout by settings.uiSettingsFlow.noteLayout.collectAsStateWithLifecycle()
    val chatLayout by settings.uiSettingsFlow.chatLayout.collectAsStateWithLifecycle()

    return remember(showProfilePictures, featureSet, autoPlayVideos, noteLayout, chatLayout) {
        DisplaySettings(
            showProfilePictures = showProfilePictures,
            performanceMode = featureSet == FeatureSetType.PERFORMANCE,
            autoPlayVideos = autoPlayVideos,
            noteLayout = noteLayout,
            completeUiMode = featureSet == FeatureSetType.COMPLETE,
            chatLayout = chatLayout,
        )
    }
}
