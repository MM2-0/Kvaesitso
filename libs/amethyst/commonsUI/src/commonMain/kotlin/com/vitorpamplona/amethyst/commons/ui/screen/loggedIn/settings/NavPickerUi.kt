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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_settings_restore_default
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.NavItemIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SimpleImage35Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size12dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size13dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size14dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size18dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size19Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size22dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size24dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size34dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size40dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size6dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import org.jetbrains.compose.resources.StringResource

/**
 * The shared visual language of the navigation-configuration screens — the Bottom Navigation Bar
 * picker and the Side Menu picker. Both present the same shape (collapsible cards of catalog rows,
 * each row a glyph + label + a pill stating its current state), so the pieces live here once and
 * each screen supplies only its own semantics: the bottom bar pins and reorders entries, the side
 * menu switches rows on and off.
 *
 * [SectionExpand]/[SectionCollapse] reveal expandable sections by unrolling straight down from the
 * top edge (the default AnimatedVisibility enter also expands horizontally from the bottom-end,
 * which reads as a diagonal slide from the top-left).
 */
val SectionExpand = expandVertically(expandFrom = Alignment.Top) + fadeIn()
val SectionCollapse = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()

/**
 * Start padding per nesting depth: 0 = a top-level catalog row, 1 = an item under an expandable
 * category (a favorite, or a relay/community "server" row), 2 = a room nested under its server (a
 * NIP-29 group under its relay, or a Concord channel under its community).
 */
private fun indentPadding(level: Int) =
    when (level) {
        0 -> Size13dp
        1 -> Size24dp
        else -> Size40dp
    }

/**
 * Which collapsible rows of a picker are currently open, keyed by whatever identifies a row (a
 * section id, a string-resource id, a catalog item). Absent means collapsed, so the initial state
 * costs nothing and no list has to be seeded.
 */
@Stable
class ExpandedKeys<K> {
    private val open = mutableStateMapOf<K, Boolean>()

    fun isExpanded(key: K): Boolean = open[key] == true

    fun toggle(key: K) {
        open[key] = !isExpanded(key)
    }
}

@Composable
fun <K> rememberExpandedKeys(): ExpandedKeys<K> = remember { ExpandedKeys() }

@Composable
fun PickerSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = Size20dp, end = Size20dp, top = Size18dp, bottom = Size6dp),
    )
}

/** A category/destination glyph in a soft accent-tinted circle. */
@Composable
fun LeadingGlyph(
    icon: MaterialSymbol,
    imageVector: ImageVector? = null,
) {
    Box(
        modifier = SimpleImage35Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        NavItemIcon(symbol = icon, imageVector = imageVector, contentDescription = null, modifier = Size19Modifier, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun EmptyChildHint(
    textRes: StringResource,
    indentLevel: Int = 1,
) {
    Text(
        text = stringRes(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = indentPadding(indentLevel), end = Size13dp, top = Size6dp, bottom = Size6dp),
    )
}

/**
 * One catalog row: leading visual, label, and a caller-supplied [trailing] state control. Tapping
 * anywhere on the row runs [onToggle]; pass null for a row whose state can't change (a mandatory
 * side-menu item), which also drops the ripple so the row doesn't advertise an action it won't take.
 */
@Composable
fun CatalogRow(
    leading: @Composable () -> Unit,
    label: String,
    onToggle: (() -> Unit)?,
    indentLevel: Int = 0,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (onToggle != null) it.clickable(onClick = onToggle) else it }
                .padding(start = indentPadding(indentLevel), end = Size13dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Size12dp),
    ) {
        leading()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/**
 * The state pill at the end of a catalog row: outlined in the "off" state, filled in the "on" state —
 * so it states both the current state and, by contrast, that it can be changed. Both states share one
 * Row body (only color/border/tint differ) so the pill keeps a constant height and rows stay aligned.
 *
 * [enabled] false renders the pill as a locked, non-interactive badge — used for a row the user isn't
 * allowed to switch off.
 */
@Composable
fun TogglePill(
    on: Boolean,
    label: String,
    icon: MaterialSymbol,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val container = if (enabled) accent else MaterialTheme.colorScheme.surfaceVariant
    val content =
        when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            on -> MaterialTheme.colorScheme.onPrimary
            else -> accent
        }
    Surface(
        shape = CircleShape,
        color = if (on) container else Color.Transparent,
        border = if (on) null else BorderStroke(1.dp, content),
    ) {
        Row(
            modifier =
                Modifier
                    .let { if (enabled) it.clickable(onClick = onClick) else it }
                    .padding(horizontal = Size14dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                symbol = icon,
                contentDescription = null,
                modifier = Size15Modifier,
                tint = content,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}

/**
 * A collapsible card holding catalog rows. [trailing] renders between the title and the chevron —
 * the side menu puts its "n hidden" counter there; the bottom bar leaves it empty.
 */
@Composable
fun CatalogCard(
    icon: MaterialSymbol,
    title: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Size20dp, vertical = 5.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand).padding(Size13dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Size12dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(Size34dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        symbol = icon,
                        contentDescription = null,
                        modifier = Size20Modifier,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                trailing()
                Icon(
                    symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                    contentDescription = null,
                    modifier = Size24Modifier,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded, enter = SectionExpand, exit = SectionCollapse) {
                Column(Modifier.padding(bottom = Size6dp)) { content() }
            }
        }
    }
}

/**
 * The accent-tinted card each picker opens with: a bold title, an optional [trailing] status, and a
 * body. The bottom bar puts its editable preview bar in the body; the side menu puts its description.
 */
@Composable
fun PickerHeroCard(
    title: String,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(Size22dp),
        color = accent.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Size20dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(Size14dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Size10dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                trailing()
            }

            content()
        }
    }
}

/** The end-aligned "Restore Default" action both pickers put under their hero card. */
@Composable
fun RestoreDefaultRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Size20dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onClick) {
            Text(stringRes(Res.string.bottom_bar_settings_restore_default))
        }
    }
}

/**
 * Publishes the picker's pending edits when the screen goes away — either the composable leaves the
 * composition (back out of the screen, account switch) or the app stops.
 *
 * The pickers debounce their NIP-78 publish, and a synced setting has no local copy other than the
 * published event, so an edit still inside the debounce window when the process dies is simply gone.
 * ON_STOP is the last point the app is reliably alive, and it always precedes the ViewModel's
 * onCleared, so between the two arms nothing is left pending.
 */
@Composable
fun FlushPickerEditsOnExit(accountViewModel: AccountViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { accountViewModel.flushPickerPublish() }

    DisposableEffect(accountViewModel) {
        onDispose { accountViewModel.flushPickerPublish() }
    }
}
