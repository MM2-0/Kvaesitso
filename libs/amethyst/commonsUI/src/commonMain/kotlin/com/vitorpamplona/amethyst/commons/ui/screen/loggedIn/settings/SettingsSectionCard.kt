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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import androidx.compose.material3.Icon as Material3Icon

@Composable
fun SettingsSection(
    title: StringResource,
    isDanger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringRes(title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color =
                if (isDanger) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors =
                CardDefaults.cardColors(
                    // surfaceContainer (one step up from …Low) so the card separates from the page by
                    // tone rather than a hard outline. The neutral surface ramp puts the page at
                    // background #FDFDFD, where …Low #F7F7F7 was nearly invisible; #F2F2F2 reads as a
                    // soft card in light and #252525 stands clear of black in dark.
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Navigation row: icon + title + optional [trailing] (count badge, etc.) + chevron. */
@Composable
fun SettingsItem(
    title: StringResource,
    icon: MaterialSymbol,
    isDanger: Boolean = false,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    SettingsItemRow(
        title = title,
        isDanger = isDanger,
        trailing = trailing,
        onClick = onClick,
    ) { tint ->
        Icon(
            symbol = icon,
            contentDescription = stringRes(title),
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}

/** Navigation row variant taking a drawable painter as the leading icon. */
@Composable
fun SettingsItem(
    title: StringResource,
    iconPainter: DrawableResource,
    iconPainterRef: Int,
    isDanger: Boolean = false,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    val painter: Painter = painterRes(iconPainter, iconPainterRef)
    SettingsItemRow(
        title = title,
        isDanger = isDanger,
        trailing = trailing,
        onClick = onClick,
    ) { tint ->
        Material3Icon(
            painter = painter,
            contentDescription = stringRes(title),
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}

@Composable
private fun SettingsItemRow(
    title: StringResource,
    isDanger: Boolean,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
    leadingIcon: @Composable (tint: Color) -> Unit,
) {
    val containerColor =
        if (isDanger) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
    val iconTint =
        if (isDanger) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
    val textColor =
        if (isDanger) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBox(containerColor) { leadingIcon(iconTint) }
        Text(
            text = stringRes(title),
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
        )
        trailing()
        Icon(
            symbol = MaterialSymbols.ChevronRight,
            contentDescription = null,
            modifier = Modifier.padding(start = 8.dp).size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Control row: icon + title + description, with an inline [trailing] control (switch, stepper).
 * When [onClick] is non-null the whole row becomes clickable — useful for `Switch` rows where
 * tapping anywhere should toggle the state.
 */
@Composable
fun SettingsControlRow(
    icon: MaterialSymbol,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBox(MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                symbol = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

/** A [SettingsControlRow] whose trailing control is a [Switch]; tapping anywhere toggles. */
@Composable
fun SettingsSwitchTile(
    icon: MaterialSymbol,
    title: StringResource,
    description: StringResource,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsControlRow(
        icon = icon,
        title = stringRes(title),
        description = stringRes(description),
        onClick = { onCheckedChange(!checked) },
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Compact switch row: icon + title + trailing [Switch], with no description line — for
 * self-explanatory single-line toggles (e.g. "Profile Badges", "New Threads"). Tapping
 * anywhere on the row toggles. When [enabled] is false the row dims and stops responding
 * (used to lock the last remaining item in a "keep at least one on" group).
 */
@Composable
fun SettingsSwitchTile(
    icon: MaterialSymbol,
    title: StringResource,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (enabled) it.clickable { onCheckedChange(!checked) } else it }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBox(MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                symbol = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = alpha),
            )
        }
        Text(
            text = stringRes(title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 12.dp),
        )
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

/**
 * Sub-row variant of [SettingsControlRow]: indented in place of a leading icon,
 * used for controls hierarchically grouped under the row above (e.g. a threshold
 * that only matters when the parent toggle is on). Text dims when [enabled] is false.
 */
@Composable
fun SettingsSubControlRow(
    title: String,
    description: String,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 68.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        trailing()
    }
}

/**
 * Tile where the control sits below the title + description (e.g. a full-width
 * SegmentedButtonRow). Use when the control would crowd a trailing slot.
 */
@Composable
fun SettingsBlockTile(
    icon: MaterialSymbol,
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SettingsIconBox(MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                symbol = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

/** Pill-shaped count for [SettingsItem.trailing]. Renders a space placeholder when zero. */
@Composable
internal fun SettingsCountBadge(count: Int) {
    Box(
        modifier = Modifier.widthIn(min = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (count > 0) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * Numeric stepper: `-` value `+`. Display is clamped to `[min, max]`, and `-`/`+`
 * operate on the raw `value` then re-clamp, so a model that starts out below `min`
 * lifts to `min` on the first tap of `+` (no skipped step). [unsetLabel] is shown
 * when `value <= 0` (typically the model uses `0` to mean "unlimited"); pass
 * `null` to always show the number.
 */
@Composable
fun SettingsStepper(
    value: Int,
    min: Int,
    max: Int,
    enabled: Boolean = true,
    unsetLabel: String? = null,
    onValueChange: (Int) -> Unit,
) {
    val clamped = value.coerceIn(min, max)
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            enabled = enabled && clamped > min,
            onClick = { onValueChange((value - 1).coerceIn(min, max)) },
        ) {
            Icon(
                symbol = MaterialSymbols.Remove,
                contentDescription = "−",
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = if (unsetLabel != null && value <= 0) unsetLabel else clamped.toString(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp),
        )
        IconButton(
            enabled = enabled && clamped < max,
            onClick = { onValueChange((value + 1).coerceIn(min, max)) },
        ) {
            Icon(
                symbol = MaterialSymbols.Add,
                contentDescription = "+",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SettingsIconBox(
    containerColor: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(containerColor),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
