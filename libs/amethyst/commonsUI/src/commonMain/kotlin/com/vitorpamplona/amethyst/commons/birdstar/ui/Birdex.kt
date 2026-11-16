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
package com.vitorpamplona.amethyst.commons.birdstar.ui

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.note.BirdDetectionCard
import com.vitorpamplona.amethyst.commons.ui.note.BirdexCard
import com.vitorpamplona.quartz.experimental.birdstar.BirdDetectionEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent

/** Entry: decodes a Birdstar Birdex [Note] and renders the shared commons [BirdexCard]. */
@Composable
fun RenderBirdex(baseNote: Note) {
    val noteEvent = baseNote.event as? BirdexEvent ?: return

    BirdexCard(noteEvent)
}

/** Entry: decodes a Birdstar detection [Note] and renders the shared commons [BirdDetectionCard]. */
@Composable
fun RenderBirdDetection(baseNote: Note) {
    val noteEvent = baseNote.event as? BirdDetectionEvent ?: return

    BirdDetectionCard(noteEvent)
}
