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
package com.vitorpamplona.amethyst.commons.browser.ui.pill

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.Action
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_pill_edit_address
import com.vitorpamplona.amethyst.commons.ui.stringRes

/** Shapes and sizes shared by every pill surface, so they read as one family. */
object PillDefaults {
    val SheetShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    val CardShape = RoundedCornerShape(20.dp)
    val TileShape = RoundedCornerShape(18.dp)
    val SheetPadding = 16.dp
    val TileHeight = 88.dp
    val FieldHeight = 48.dp

    /** The edge every floating pill surface draws, so it stays visible where shadows don't (black theme). */
    @Composable
    fun hairline(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/**
 * A site's stand-in icon: its first letter on a tonal rounded square. Real favicons can drop in later
 * (the launcher already stores them per host); the monogram keeps the header from ever being empty.
 */
@Composable
fun SiteMonogram(
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val letter = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * The origin as a read-only field: security badge in its signal colour, host, and a trailing pencil that
 * says "this is where the address lives". Tapping edits the address; long-pressing copies the link.
 * Sandboxed apps get a non-editable version with no pencil.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OriginField(
    ui: BrowserPillUi,
    onEdit: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onSecurityTap: (() -> Unit)? = null,
) {
    val security = ui.security
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = PillDefaults.FieldHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(
                enabled = onEdit != null || onLongPress != null,
                role = Role.Button,
                onClick = { onEdit?.invoke() },
                onLongClick = onLongPress,
            ).padding(start = if (onSecurityTap != null) 4.dp else 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Chrome's page-info entry point: the connection badge itself.
        if (onSecurityTap != null) {
            IconButton(onClick = onSecurityTap) { SecurityIcon(security, size = 20.dp) }
            Spacer(Modifier.width(2.dp))
        } else {
            SecurityIcon(security, size = 18.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                if (ui.chrome.isSandbox) stringRes(securityLabel(security)) else ui.host,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Only the states worth a second look get words: plain HTTP and Tor.
            if (!ui.chrome.isSandbox && (security == BrowserChrome.Security.HTTP || security == BrowserChrome.Security.TOR)) {
                Text(
                    stringRes(securityLabel(security)),
                    style = MaterialTheme.typography.labelSmall,
                    color = securityTint(security),
                    maxLines = 1,
                )
            }
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(MaterialSymbols.Edit, contentDescription = stringRes(Res.string.browser_pill_edit_address), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}

/**
 * The navigation capsule: Chrome's back · forward · reload/stop · star · share on one rounded bar. Back
 * and forward dim when unavailable; the star fills when pinned; reload turns into stop, ringed by the load.
 */
@Composable
fun NavigationCapsule(
    ui: BrowserPillUi,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrowserChrome.iconRow(ui.chrome).forEach { action ->
            val enabled = BrowserChrome.isEnabled(ui.chrome, action)
            val pinned = action == Action.FAVORITE && ui.isFavorite
            IconButton(
                onClick = { onAction(action) },
                enabled = enabled,
                colors = IconButtonDefaults.iconButtonColors(disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (action == Action.STOP && ui.loadProgress != null) {
                        CircularProgressIndicator(
                            progress = { ui.loadProgress },
                            modifier = Modifier.size(34.dp),
                            strokeWidth = 2.dp,
                            trackColor = Color.Transparent,
                        )
                    }
                    PillActionIcon(
                        action = action,
                        tint =
                            when {
                                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                pinned -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        size = if (action == Action.STOP) 20.dp else 24.dp,
                        filled = pinned,
                        contentDescription = stringRes(pillLabelFor(action, ui.isFavorite)),
                    )
                }
            }
        }
    }
}

/**
 * A page-action tile: icon over a two-line label on a rounded card. A toggle tile ([selected] non-null)
 * fills with the secondary container while on, so its state reads without a switch.
 */
@Composable
fun ActionTile(
    symbol: MaterialSymbol,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    description: String = label,
) {
    val on = selected == true
    Surface(
        onClick = onClick,
        modifier = modifier.height(PillDefaults.TileHeight).semantics(mergeDescendants = true) { contentDescription = description },
        shape = PillDefaults.TileShape,
        color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            // Top-aligned so every icon in a row sits on one line, whether its label takes one line or two.
            Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Icon(symbol, contentDescription = null, modifier = Modifier.size(24.dp), filled = on)
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A rounded group of rows (the privacy card), with an optional heading above it. */
@Composable
fun GroupCard(
    heading: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (heading != null) {
            Text(
                heading,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
            )
        }
        Surface(shape = PillDefaults.CardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

/** A divider inset past a [GroupRow]'s leading icon. */
@Composable
fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/**
 * One row of a [GroupCard]: a tinted icon circle, a title with a supporting line that states the
 * consequence, and a trailing control (switch, chevron, badge).
 */
@Composable
fun GroupRow(
    icon: @Composable () -> Unit,
    iconContainer: Color,
    title: String,
    supporting: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(iconContainer), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = supportingColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** A small count badge (console errors). */
@Composable
fun CountBadge(
    count: Int,
    container: Color = MaterialTheme.colorScheme.errorContainer,
    content: Color = MaterialTheme.colorScheme.onErrorContainer,
) {
    Box(
        Modifier
            .heightIn(min = 22.dp)
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/** Dims content that is shown but not usable. */
fun Modifier.dimmed(enabled: Boolean): Modifier = if (enabled) this else this.alpha(0.38f)
