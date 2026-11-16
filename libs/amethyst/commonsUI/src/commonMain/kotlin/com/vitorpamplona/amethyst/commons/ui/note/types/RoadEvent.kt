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
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.note.RoadEventConfirmationCard
import com.vitorpamplona.amethyst.commons.ui.note.RoadEventReportCard
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocationPreviewMap
import com.vitorpamplona.quartz.experimental.roadstr.confirmation.RoadEventConfirmationEvent
import com.vitorpamplona.quartz.experimental.roadstr.report.RoadEventReportEvent

/**
 * Entry for a Roadstr road event report (kind 1315): decodes the [Note] and renders the
 * shared commons [RoadEventReportCard], supplying the native map hero as its slot.
 */
@Composable
fun RenderRoadEventReport(baseNote: Note) {
    val noteEvent = baseNote.event as? RoadEventReportEvent ?: return

    RoadEventReportCard(noteEvent) { latitude, longitude, pinColor, pinEmoji, pinAlpha ->
        LocationPreviewMap(
            latitude = latitude,
            longitude = longitude,
            pinColor = pinColor,
            pinEmoji = pinEmoji,
            pinAlpha = pinAlpha,
        )
    }
}

/**
 * Entry for a Roadstr confirmation/denial (kind 1316): decodes the [Note] and renders the
 * shared commons [RoadEventConfirmationCard], supplying the native map hero as its slot.
 */
@Composable
fun RenderRoadEventConfirmation(baseNote: Note) {
    val noteEvent = baseNote.event as? RoadEventConfirmationEvent ?: return

    RoadEventConfirmationCard(noteEvent) { latitude, longitude, pinColor, pinEmoji, pinAlpha ->
        LocationPreviewMap(
            latitude = latitude,
            longitude = longitude,
            pinColor = pinColor,
            pinEmoji = pinEmoji,
            pinAlpha = pinAlpha,
        )
    }
}
