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
package com.vitorpamplona.amethyst.commons.marmot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.vitorpamplona.marmot.appComponents.agentTextStream.PreviewStatus
import com.vitorpamplona.marmot.client.AgentStreamPreview

/**
 * The live agent-preview row, shown between the transcript and the composer.
 *
 * The whole point of this row is that it is NOT the transcript. Preview text
 * is provisional until the durable kind:9 lands and its transcript hash agrees
 * with what we folded — every record can open individually and the stream
 * still be wrong, if one was dropped, reordered or injected. So it renders in
 * italic on a tinted surface with an explicit label, and it disappears the
 * moment the real message arrives (confirmed) or is contradicted (dropped).
 *
 * `ProgressDelta` and `Status` records never reach [AgentStreamPreview.text] —
 * the spec keeps them out of preview text, notifications, indexes and
 * automation input — so they render here only as a separate, quieter line.
 */
@Composable
fun AgentStreamPreviewBanner(
    preview: AgentStreamPreview?,
    modifier: Modifier = Modifier,
) {
    // An aborted preview produces no durable text at all: the publisher
    // withdrew it, so there is nothing honest left to show.
    val visible = preview != null && preview.status != PreviewStatus.ABORTED && !preview.isConfirmed

    AnimatedVisibility(visible = visible) {
        if (preview == null) return@AnimatedVisibility
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text =
                        when (preview.status) {
                            PreviewStatus.UNVERIFIABLE -> "Live preview (incomplete)"
                            else -> "Live preview"
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                preview.statusLabel?.let {
                    Text(
                        text = " · $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (preview.text.isNotEmpty()) {
                Text(
                    text = preview.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            preview.progressLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
