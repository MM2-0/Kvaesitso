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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.RenderAudioWithWaveform
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nipA0VoiceMessages.BaseVoiceEvent

@Composable
fun RenderVoiceTrack(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? BaseVoiceEvent ?: return

    VoiceHeader(noteEvent, note, accountViewModel, nav)
}

@Composable
fun VoiceHeader(
    noteEvent: BaseVoiceEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val media =
        remember(noteEvent) {
            noteEvent.content.ifBlank { null } ?: noteEvent.iMetaTags().firstOrNull()?.url
        }

    if (media == null) return

    val waveform =
        remember(noteEvent) {
            noteEvent
                .iMetaTags()
                .firstOrNull()
                ?.waveform
                ?.let { WaveformData(it) }
        }

    RenderAudioWithWaveform(
        mediaUrl = media,
        title = noteEvent.subject(),
        mimeType = null,
        waveform = waveform,
        note = note,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/**
 * Renders audio with waveform for any event type that has audio IMeta attachment.
 * This allows KIND 1 (TextNoteEvent) and KIND 1111 (CommentEvent) with voice
 * attachments to display the same waveform UI as VoiceEvent.
 */
@Composable
fun RenderAudioFromIMeta(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event ?: return

    val audioMeta = remember(noteEvent) { noteEvent.getAudioMetaWithWaveform() } ?: return

    val waveform =
        remember(audioMeta) {
            audioMeta.waveform?.let { WaveformData(it) }
        }

    RenderAudioWithWaveform(
        mediaUrl = audioMeta.url,
        title = null,
        mimeType = audioMeta.mimeType,
        waveform = waveform,
        note = note,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}
