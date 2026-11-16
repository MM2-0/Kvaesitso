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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.podcasts.fetchPreviewText
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_transcript
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The episode's transcript (from the off-event file referenced by the `transcript` tag), fetched and
 * shown in a collapsible, scrollable panel. VTT/SRT scaffolding (the `WEBVTT` header, cue indices,
 * and `-->` timing lines) is stripped so it reads as flowing text; plain-text transcripts pass
 * through unchanged.
 */
@Composable
fun PodcastTranscriptView(
    transcriptUrl: String,
    accountViewModel: AccountViewModel,
) {
    val transcript by produceState(initialValue = null as String?, transcriptUrl) {
        val body = accountViewModel.httpClientBuilder.fetchPreviewText(transcriptUrl)
        value = body?.let { cleanTranscript(it) }?.takeIf { it.isNotBlank() }
    }

    val text = transcript ?: return

    var expanded by remember(transcriptUrl) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        CollapsibleHeader(
            symbol = MaterialSymbols.Description,
            title = stringRes(Res.string.podcast_transcript),
            expanded = expanded,
            onToggle = { expanded = !expanded },
        )

        if (expanded) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
            )
        }
    }
}

/**
 * Strips VTT/SRT caption scaffolding and joins the remaining caption lines into readable prose.
 * Leaves plain-text transcripts effectively untouched. Falls back to the raw body if the cleanup
 * removed everything (e.g. an unexpected format).
 */
private fun cleanTranscript(raw: String): String {
    val out = StringBuilder()
    for (line in raw.lineSequence()) {
        val t = line.trim()
        if (t.isEmpty()) continue
        if (t == "WEBVTT") continue
        if (t.startsWith("NOTE ")) continue
        if (t.contains("-->")) continue // cue timing line
        if (t.toIntOrNull() != null) continue // SRT cue index
        out.append(t).append(' ')
    }
    return out.toString().trim().ifEmpty { raw.trim() }
}
