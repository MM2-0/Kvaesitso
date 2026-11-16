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
package com.vitorpamplona.amethyst.commons.ui.note.creators.aihelp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ai_tone_correct
import com.vitorpamplona.amethyst.commons.resources.ai_tone_elaborate
import com.vitorpamplona.amethyst.commons.resources.ai_tone_emojify
import com.vitorpamplona.amethyst.commons.resources.ai_tone_friendly
import com.vitorpamplona.amethyst.commons.resources.ai_tone_professional
import com.vitorpamplona.amethyst.commons.resources.ai_tone_rephrase
import com.vitorpamplona.amethyst.commons.resources.ai_tone_shorter
import com.vitorpamplona.amethyst.commons.resources.ai_writing_dismiss
import com.vitorpamplona.amethyst.commons.resources.ai_writing_use_this
import com.vitorpamplona.amethyst.commons.service.ai.WritingResult
import com.vitorpamplona.amethyst.commons.service.ai.WritingTone
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.collections.immutable.ImmutableMap

@Composable
fun AiWritingHelpPanel(
    isVisible: Boolean,
    readyResults: ImmutableMap<WritingTone, WritingResult>,
    selectedResult: WritingResult?,
    onToneSelected: (WritingTone) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
        ) {
            selectedResult?.let {
                AiResultCard(
                    result = it,
                    onApply = onApply,
                    onDismiss = onDismiss,
                )
            }

            ToneChipRow(
                readyTones = readyResults.keys,
                selectedTone = selectedResult?.tone,
                onToneSelected = onToneSelected,
            )
        }
    }
}

@Composable
private fun AiResultCard(
    result: WritingResult,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
    ) {
        Text(
            text = result.transformedText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp)
                    .verticalScroll(rememberScrollState()),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.ai_writing_dismiss))
            }
            OutlinedButton(onClick = onApply) {
                Text(stringRes(Res.string.ai_writing_use_this))
            }
        }
    }
}

@Composable
private fun ToneChipRow(
    readyTones: Set<WritingTone>,
    selectedTone: WritingTone?,
    onToneSelected: (WritingTone) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier =
            Modifier
                .horizontalScroll(scrollState)
                .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WritingTone.entries.forEach { tone ->
            if (tone in readyTones) {
                FilterChip(
                    selected = tone == selectedTone,
                    onClick = { onToneSelected(tone) },
                    label = { Text(toneDisplayName(tone)) },
                )
            }
        }
    }
}

@Composable
private fun toneDisplayName(tone: WritingTone): String =
    when (tone) {
        WritingTone.CORRECT -> stringRes(Res.string.ai_tone_correct)
        WritingTone.REPHRASE -> stringRes(Res.string.ai_tone_rephrase)
        WritingTone.SHORTER -> stringRes(Res.string.ai_tone_shorter)
        WritingTone.ELABORATE -> stringRes(Res.string.ai_tone_elaborate)
        WritingTone.FRIENDLY -> stringRes(Res.string.ai_tone_friendly)
        WritingTone.PROFESSIONAL -> stringRes(Res.string.ai_tone_professional)
        WritingTone.EMOJIFY -> stringRes(Res.string.ai_tone_emojify)
    }
