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
package com.vitorpamplona.amethyst.commons.ui.note.creators.polls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_deadline_days
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_deadline_hours
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_no_deadline
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_remove_option
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.CardSurfaces
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.quartz.utils.TimeUtils

// The pieces both poll composers draw with: the one in New Post and the group-chat sheet. The
// options look like the rows a voter will tap in the poll card, and the settings sit together
// in one card under them.

private val OptionShape = RoundedCornerShape(16.dp)
private val SettingsShape = RoundedCornerShape(20.dp)

/** The mark a voter taps: round for one answer, square for several. It morphs as the mode changes. */
@Composable
fun PollChoiceGlyph(
    multiple: Boolean,
    active: Boolean,
) {
    val corner by animateDpAsState(if (multiple) 5.dp else 10.dp)
    val color by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    Box(Modifier.size(20.dp).border(2.dp, color, RoundedCornerShape(corner)))
}

/**
 * The frame of one option being written: [leading] (the glyph), the field in [content], and a
 * remove button when [onRemove] is set, centred on each other. [below] goes under that row,
 * lined up with the text (a picture the option links to). A [trailing] row is the empty one at
 * the end that the next option is typed into: it shows a + and sits on the page instead of the well.
 */
@Composable
fun PollOptionFrame(
    focused: Boolean,
    error: Boolean,
    trailing: Boolean,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    below: (@Composable () -> Unit)? = null,
    leading: @Composable () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val borderColor by animateColorAsState(
        when {
            error -> MaterialTheme.colorScheme.error
            focused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
    )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(OptionShape)
                .background(if (trailing) CardSurfaces.color else CardSurfaces.well)
                .border(1.dp, borderColor, OptionShape)
                .padding(start = 16.dp, end = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            if (trailing) {
                Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.placeholderText)
            } else {
                leading()
            }
            Spacer(Modifier.width(12.dp))
            content()
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(
                        MaterialSymbols.Close,
                        contentDescription = stringRes(Res.string.marmot_poll_remove_option),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
        if (below != null) {
            // Past the glyph (20) and its gap (12), and clear of the remove button on the right.
            Box(Modifier.padding(start = 32.dp, end = 12.dp, bottom = 14.dp)) { below() }
        }
    }
}

/** The card the poll's settings share, one [PollSettingRow] each, split by [PollSettingsDivider]. */
@Composable
fun PollSettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(SettingsShape)
                .background(CardSurfaces.well)
                .border(CardSurfaces.border, SettingsShape),
        content = content,
    )
}

@Composable
fun PollSettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
fun PollSettingRow(
    symbol: MaterialSymbol,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(symbol, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

/** What goes under a [PollSettingRow]: lined up with its title, not its icon. */
@Composable
fun PollSettingDetail(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 52.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** A row of deadline chips that scrolls sideways when the window is too narrow for all of them. */
@Composable
fun PollDeadlineChips(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        content = content,
    )
}

@Composable
fun PollDeadlineChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        label = { Text(label) },
    )
}

/** "Never", "1 hour", "7 days": how long a poll stays open, as its chip says it. */
@Composable
fun pollDeadlineLabel(seconds: Long?): String =
    when {
        seconds == null -> stringRes(Res.string.marmot_poll_no_deadline)
        seconds < TimeUtils.ONE_DAY -> (seconds / TimeUtils.ONE_HOUR).toInt().let { pluralStringRes(Res.plurals.marmot_poll_deadline_hours, it, it) }
        else -> (seconds / TimeUtils.ONE_DAY).toInt().let { pluralStringRes(Res.plurals.marmot_poll_deadline_days, it, it) }
    }
