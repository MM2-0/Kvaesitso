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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.marmot_retention_1d
import com.vitorpamplona.amethyst.commons.resources.marmot_retention_1h
import com.vitorpamplona.amethyst.commons.resources.marmot_retention_1w
import com.vitorpamplona.amethyst.commons.resources.marmot_retention_footer
import com.vitorpamplona.amethyst.commons.resources.marmot_retention_off
import com.vitorpamplona.amethyst.commons.resources.marmot_retention_title
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * How long messages live in a new group — component `0x8005`,
 * `marmot.group.message-retention.v1`.
 *
 * A fixed set rather than a free-form duration, because the value is committed
 * into group state that every member's client reads: an arbitrary number buys
 * nothing and gives a reader one more shape to render.
 */
enum class MarmotRetentionChoice(
    val seconds: ULong?,
) {
    OFF(null),
    ONE_HOUR(3_600uL),
    ONE_DAY(86_400uL),
    ONE_WEEK(604_800uL),
}

/**
 * The picker, shown only at group creation.
 *
 * Retention is chosen once and not changed later, and that is a limitation
 * rather than a policy: making a component required after epoch 0 takes two
 * commits — install the state, then promote it — and this screen makes one.
 */
@Composable
fun MarmotRetentionPicker(
    selected: MarmotRetentionChoice,
    onSelect: (MarmotRetentionChoice) -> Unit,
    enabled: Boolean,
) {
    Column {
        Text(
            stringRes(Res.string.marmot_retention_title),
            style = MaterialTheme.typography.titleSmall,
        )
        MarmotRetentionChoice.entries.forEach { choice ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .selectable(
                            selected = choice == selected,
                            enabled = enabled,
                            onClick = { onSelect(choice) },
                        ).padding(vertical = 2.dp),
            ) {
                RadioButton(
                    selected = choice == selected,
                    onClick = { onSelect(choice) },
                    enabled = enabled,
                )
                Text(stringRes(choice.label()))
            }
        }
        Text(
            stringRes(Res.string.marmot_retention_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun MarmotRetentionChoice.label() =
    when (this) {
        MarmotRetentionChoice.OFF -> Res.string.marmot_retention_off
        MarmotRetentionChoice.ONE_HOUR -> Res.string.marmot_retention_1h
        MarmotRetentionChoice.ONE_DAY -> Res.string.marmot_retention_1d
        MarmotRetentionChoice.ONE_WEEK -> Res.string.marmot_retention_1w
    }
