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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta

/**
 * Extracts AudioMeta from an event's IMeta tags if it has audio content with waveform.
 * Returns the first audio IMeta that has a waveform, or null if none found.
 */
fun Event.getAudioMetaWithWaveform(): AudioMeta? {
    val audioMetas = imetas().map { AudioMeta.parse(it) }
    return audioMetas.firstOrNull { meta ->
        meta.waveform != null &&
            (meta.mimeType == null || meta.mimeType?.startsWith("audio/") == true)
    }
}

/**
 * Checks if the event content is primarily an audio attachment (content is just the audio URL).
 */
fun Event.isAudioOnlyContent(): Boolean {
    val audioMeta = getAudioMetaWithWaveform() ?: return false
    return content.trim() == audioMeta.url
}
