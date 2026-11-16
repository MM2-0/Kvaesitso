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
package com.vitorpamplona.amethyst.commons.account.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.hashtags.Amethyst
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_name
import com.vitorpamplona.amethyst.commons.resources.connect_via_tor2
import com.vitorpamplona.amethyst.commons.resources.ic_tor
import com.vitorpamplona.amethyst.commons.resources.login_or
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.CardSurfaces
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.tor.ConnectTorDialog
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/** The two ends of the logo's gradient: the logged-off screens glow in them. */
val AmethystPurple = Color(0xFF652D80)
val AmethystBlue = Color(0xFF2598CF)

/** Below this width the brand sits above the form; from it, the two share the window side by side. */
private val TwoPaneMinWidth = 840.dp
private val FormMaxWidth = 420.dp

/** True where the brand stands beside the form rather than right above it. */
private val LocalBrandBesideForm = staticCompositionLocalOf { false }

/**
 * The frame of the logged-off screens (login, sign-up): the brand, the [form] and a [footer] that
 * switches between them, over a soft glow in the logo's colors. A window wide enough splits into
 * the brand on one side and the form, on a card, on the other.
 */
@Composable
fun LoggedOffScaffold(
    torSettingsFlow: TorSettingsFlow,
    onTorError: (String) -> Unit,
    footer: @Composable () -> Unit,
    form: @Composable ColumnScope.() -> Unit,
) {
    val glow = if (MaterialTheme.colorScheme.isLight) 0.16f else 0.30f

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                // Built once per size and theme, not on every redraw of the screen.
                val purple =
                    Brush.radialGradient(
                        listOf(AmethystPurple.copy(alpha = glow), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.05f),
                        radius = size.maxDimension * 0.7f,
                    )
                val blue =
                    Brush.radialGradient(
                        listOf(AmethystBlue.copy(alpha = glow * 0.8f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.95f),
                        radius = size.maxDimension * 0.6f,
                    )
                onDrawBehind {
                    drawRect(purple)
                    drawRect(blue)
                }
            }
            // The glow runs under the system bars (the app draws edge to edge); the form keeps
            // clear of them, or it slides under them once it is taller than the window.
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
    ) {
        val twoPane = maxWidth >= TwoPaneMinWidth
        // One call site for the form at every width: crossing TwoPaneMinWidth (a phone turned to
        // landscape, a foldable opened, a window resized) only moves the brand and dresses the
        // form as a card. Two branches each holding the form would rebuild it at the crossing,
        // dropping the keyboard and closing its open dialogs (QR scanner, Tor, remote signer).
        Row(Modifier.fillMaxSize()) {
            if (twoPane) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BrandMark(logoSize = 180.dp, large = true)
                }
            }
            CompositionLocalProvider(LocalBrandBesideForm provides twoPane) {
                BrandAboveForm(
                    showBrand = !twoPane,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .imePaddingSafe()
                            .verticalScroll(rememberScrollState())
                            .padding(if (twoPane) PaddingValues(32.dp) else PaddingValues(24.dp)),
                ) {
                    FormColumn(
                        torSettingsFlow,
                        onTorError,
                        footer,
                        if (twoPane) {
                            Modifier
                                .widthIn(max = FormMaxWidth + 64.dp)
                                .clip(FormCardShape)
                                // Frosted over the glow, not a grey slab on it.
                                .background(CardSurfaces.frosted)
                                .border(CardSurfaces.frostedBorder, FormCardShape)
                                .padding(32.dp)
                        } else {
                            Modifier.widthIn(max = FormMaxWidth)
                        },
                        form,
                    )
                }
            }
        }
    }
}

private val FormCardShape = RoundedCornerShape(28.dp)

private enum class BrandSlot { Form, Full, Compact }

/**
 * The brand over the [form], centered in the window. The full brand (logo over the name) where
 * both fit the window's height; otherwise the logo shrinks beside the name, so a small phone or
 * an open keyboard keeps the ways in on screen instead of the logo.
 */
@Composable
private fun BrandAboveForm(
    showBrand: Boolean,
    modifier: Modifier,
    form: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        // Inside the scroll the height is unbounded; its minimum is the window's.
        val window = constraints.minHeight
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val formPlaceables = subcompose(BrandSlot.Form, form).map { it.measure(loose) }
        val formHeight = formPlaceables.sumOf { it.height }

        val brand: List<Placeable>
        val gap: Int
        if (showBrand) {
            val fullGap = 32.dp.roundToPx()
            val full = subcompose(BrandSlot.Full) { BrandMark(logoSize = 96.dp, large = false) }.map { it.measure(loose) }
            val fits = full.sumOf { it.height } + fullGap + formHeight <= window
            brand = if (fits) full else subcompose(BrandSlot.Compact) { CompactBrandMark() }.map { it.measure(loose) }
            gap = if (fits) fullGap else 20.dp.roundToPx()
        } else {
            // The brand stands beside the form instead.
            brand = emptyList()
            gap = 0
        }

        val contentHeight = brand.sumOf { it.height } + gap + formHeight
        val width = constraints.maxWidth
        val height = maxOf(contentHeight, window)
        layout(width, height) {
            var y = (height - contentHeight) / 2
            (brand + formPlaceables).forEachIndexed { index, placeable ->
                if (index == brand.size && index > 0) y += gap
                placeable.placeRelative((width - placeable.width) / 2, y)
                y += placeable.height
            }
        }
    }
}

@Composable
private fun FormColumn(
    torSettingsFlow: TorSettingsFlow,
    onTorError: (String) -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier,
    form: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        form()
        Spacer(Modifier.height(4.dp))
        footer()
        TorSettingsChip(torSettingsFlow, onTorError)
    }
}

@Composable
private fun BrandMark(
    logoSize: Dp,
    large: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            imageVector = CustomHashTagIcons.Amethyst,
            // The name beside it already says it: a screen reader would read it twice.
            contentDescription = null,
            modifier = Modifier.size(logoSize),
            contentScale = ContentScale.Inside,
        )
        Spacer(Modifier.height(if (large) 24.dp else 12.dp))
        Text(
            text = stringRes(Res.string.app_name),
            style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The logo beside the name, small: the brand where the full one would push the form off screen. */
@Composable
private fun CompactBrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            imageVector = CustomHashTagIcons.Amethyst,
            // The name beside it already says it: a screen reader would read it twice.
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            contentScale = ContentScale.Inside,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringRes(Res.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The form's headline, on the card beside the brand. Left out where the brand sits right above
 * the form: a second heading under the logo and name only crowds it.
 */
@Composable
fun LoggedOffTitle(text: String) {
    if (!LocalBrandBesideForm.current) return
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "or", between the main way in and the alternatives. */
@Composable
fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            text = stringRes(Res.string.login_or),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

/** An on/off choice of the form: its icon and label, and a switch at the end. The whole row toggles. */
@Composable
fun LoggedOffSwitchRow(
    symbol: MaterialSymbol,
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        SymbolIcon(symbol, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // Null: the row is the toggle, so the switch must not take the click for itself.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Something the form should know about how keys are kept here, in a tinted box with its icon, and
 * the one action that fixes it ([actionText]) when there is one.
 */
@Composable
fun LoggedOffBanner(
    symbol: MaterialSymbol,
    text: String,
    isError: Boolean = false,
    actionText: String? = null,
    onAction: () -> Unit = {},
) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val onContainer = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(shape = RoundedCornerShape(16.dp), color = container, contentColor = onContainer, modifier = Modifier.fillMaxWidth()) {
        // One row: the action sits at the end of the line it fixes, not on a line of its own.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = if (actionText != null) 4.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        ) {
            SymbolIcon(symbol, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            if (actionText != null) {
                TextButton(onClick = onAction) {
                    Text(actionText, fontWeight = FontWeight.SemiBold, color = onContainer)
                }
            }
        }
    }
}

/** "Don't have a Nostr account? Sign Up": a question and the link that answers it. */
@Composable
fun SwitchScreenFooter(
    question: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            // Measured after the link: a long translation or a large font wraps the question
            // instead of squeezing the link out of the row.
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = onClick) {
            Text(action, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TorSettingsChip(
    torSettingsFlow: TorSettingsFlow,
    onError: (String) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }

    AssistChip(
        onClick = { dialogOpen = true },
        label = { Text(stringRes(Res.string.connect_via_tor2)) },
        leadingIcon = {
            Icon(
                painter = painterRes(Res.drawable.ic_tor, 1),
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        shape = RoundedCornerShape(50),
    )

    if (dialogOpen) {
        ConnectTorDialog(
            torSettings = torSettingsFlow.toSettings(),
            onClose = { dialogOpen = false },
            onPost = { torSettings ->
                dialogOpen = false
                torSettingsFlow.update(torSettings)
            },
            onError = onError,
        )
    }
}
