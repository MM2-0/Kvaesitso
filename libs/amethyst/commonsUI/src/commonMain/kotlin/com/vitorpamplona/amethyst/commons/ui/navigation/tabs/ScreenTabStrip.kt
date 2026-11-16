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
package com.vitorpamplona.amethyst.commons.ui.navigation.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.NavRequest
import com.vitorpamplona.amethyst.commons.model.navigation.NavStackEntry
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.TabsModel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.navigation_close_tab
import com.vitorpamplona.amethyst.commons.resources.navigation_new_tab
import com.vitorpamplona.amethyst.commons.resources.navigation_reopen_closed
import com.vitorpamplona.amethyst.commons.resources.navigation_tab_close_opened
import com.vitorpamplona.amethyst.commons.resources.navigation_tab_close_others
import com.vitorpamplona.amethyst.commons.resources.navigation_tab_close_right
import com.vitorpamplona.amethyst.commons.resources.navigation_tab_copy_link
import com.vitorpamplona.amethyst.commons.resources.navigation_tab_duplicate
import com.vitorpamplona.amethyst.commons.resources.navigation_tab_list
import com.vitorpamplona.amethyst.commons.resources.navigation_tab_pin
import com.vitorpamplona.amethyst.commons.resources.navigation_tab_search
import com.vitorpamplona.amethyst.commons.resources.navigation_tab_to_window
import com.vitorpamplona.amethyst.commons.resources.navigation_tab_unpin
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.ObserveAndDrawInnerUserPicture
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

private val StripHeight = 40.dp
private val TabHeight = 32.dp
private val TabGap = 2.dp

// How far a press goes sideways before it drags its tab instead of opening it: about a finger's slop.
private val ReorderSlop = 8.dp
private val PinnedTabWidth = 40.dp
private val MinTabWidth = 120.dp
private val MaxTabWidth = 220.dp
private val TabShape = RoundedCornerShape(8.dp)

/**
 * The tabs layout's strip, above the main screen and the side panel: pinned sections first as
 * icons, then one tab per open screen. Tabs shrink from 220 to 120 dp before the strip scrolls, and
 * the active one always scrolls into view. A click shows a tab; its cross (on the active or hovered
 * tab) or a middle click closes it; a right click opens its menu; dragging moves it. The plus
 * opens a fresh search, and the list at the end finds any open tab by name.
 */
@Composable
fun ScreenTabStrip(
    nav: Nav,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    // The strip is the tabs model's view: nothing to draw in any other layout.
    val stacks = nav.stacks.model as? TabsModel ?: return
    val tabs = stacks.strip()
    val activeId = stacks.top.id
    // Read once here and handed only to the tab it is for, so a flash recomposes that tab alone.
    val attention = stacks.attention
    val colors = MaterialTheme.colorScheme

    Column(modifier.fillMaxWidth().background(colors.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().height(StripHeight).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val pinnedCount = tabs.count { stacks.isPinned(it) }
                val unpinnedCount = (tabs.size - pinnedCount).coerceAtLeast(1)
                val gaps = TabGap * (tabs.size - 1).coerceAtLeast(0)
                val tabWidth = ((maxWidth - PinnedTabWidth * pinnedCount - gaps) / unpinnedCount).coerceIn(MinTabWidth, MaxTabWidth)

                val scroll = rememberScrollState()
                val density = LocalDensity.current
                // The active tab scrolls into view, whichever way it went out of it.
                LaunchedEffect(activeId, tabWidth, tabs.size) {
                    val index = tabs.indexOfFirst { it.id == activeId }
                    if (index < 0) return@LaunchedEffect
                    val before = tabs.take(index).sumOf { (if (stacks.isPinned(it)) PinnedTabWidth else tabWidth).value.toDouble() + TabGap.value }
                    val width = if (stacks.isPinned(tabs[index])) PinnedTabWidth else tabWidth
                    val start = with(density) { before.toFloat().dp.roundToPx() }
                    val end = start + with(density) { width.roundToPx() }
                    val viewport = scroll.viewportSize
                    if (start < scroll.value) {
                        scroll.animateScrollTo(start)
                    } else if (viewport > 0 && end > scroll.value + viewport) {
                        scroll.animateScrollTo(end - viewport)
                    }
                }

                Row(
                    Modifier.fillMaxHeight().horizontalScroll(scroll),
                    horizontalArrangement = Arrangement.spacedBy(TabGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { entry ->
                        key(entry.id) {
                            ScreenTab(
                                entry = entry,
                                width = if (stacks.isPinned(entry)) PinnedTabWidth else tabWidth,
                                selected = entry.id == activeId,
                                flash = attention?.takeIf { it.id == entry.id }?.serial,
                                stacks = stacks,
                                accountViewModel = accountViewModel,
                            )
                        }
                    }
                }
            }
            StripButton(MaterialSymbols.Add, stringRes(Res.string.navigation_new_tab)) {
                stacks.open(NavRequest(Route.Search(), forceNew = true))
            }
            TabListButton(stacks, accountViewModel)
        }
        HorizontalDivider()
    }
}

@Composable
private fun ScreenTab(
    entry: NavStackEntry,
    width: Dp,
    selected: Boolean,
    flash: Int?,
    stacks: TabsModel,
    accountViewModel: AccountViewModel,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // Where the right click landed, in the tab: the menu opens there, not at the tab's corner.
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }

    // A flash when the tab is asked for again while open, or opens in the background.
    val glow = remember { Animatable(0f) }
    LaunchedEffect(flash) {
        if (flash != null) {
            glow.snapTo(1f)
            glow.animateTo(0f, tween(900))
        }
    }

    // A dot for news since the user last looked at this tab.
    val activity = rememberRouteActivity(entry.route, accountViewModel)
    var seen by remember(entry.id) { mutableStateOf(activity) }
    LaunchedEffect(selected, activity) { if (selected) seen = activity }
    val unread = !selected && activity != null && seen != null && activity != seen

    val base =
        when {
            selected -> colors.primary.copy(alpha = 0.18f)
            hovered -> colors.onSurface.copy(alpha = 0.07f)
            else -> Color.Transparent
        }
    val background = colors.primary.copy(alpha = 0.35f * glow.value).compositeOver(base.compositeOver(colors.surfaceContainerLow))
    val accent = colors.primary
    val currentWidth by rememberUpdatedState(width)

    Box(
        Modifier
            .width(width)
            .height(TabHeight)
            .clip(TabShape)
            .background(background)
            .drawBehind {
                if (selected) {
                    val inset = 10.dp.toPx()
                    val thickness = 2.dp.toPx()
                    drawRect(accent, Offset(inset, size.height - thickness), Size(size.width - 2 * inset, thickness))
                }
            }.hoverable(interaction)
            .pointerInput(entry.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Press) continue
                        if (event.buttons.isTertiaryPressed) stacks.close(entry.id)
                        if (event.buttons.isSecondaryPressed) {
                            val at = event.changes.first().position
                            menuAt = IntOffset(at.x.toInt(), at.y.toInt())
                        }
                    }
                }
            }.pointerInput(entry.id) {
                // Dragging sideways moves the tab a place each time it crosses half a tab, once the
                // press has gone [ReorderSlop] sideways. Not Compose's horizontal drag: its slop for a
                // mouse is an eighth of a finger's, about 2 px, so a click that wobbled became a drag
                // and the tab showed its ripple but never opened (measured: still 0 px opened it,
                // 2 px and more never did).
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = ReorderSlop.toPx()
                    var sideways = 0f
                    var dragging = false
                    var dragged = 0f
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.positionChange().x
                        if (!dragging) {
                            sideways += dx
                            if (abs(sideways) < slop) continue
                            dragging = true
                            dragged = sideways
                        } else {
                            dragged += dx
                        }
                        // From here the gesture is a drag: the click it started as is cancelled.
                        change.consume()
                        val step = (currentWidth + TabGap).toPx()
                        if (dragged > step / 2) {
                            stacks.move(entry.id, 1)
                            dragged -= step
                        } else if (dragged < -step / 2) {
                            stacks.move(entry.id, -1)
                            dragged += step
                        }
                    }
                }
            }.clickable { stacks.activate(entry.id) },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (stacks.isPinned(entry)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TabIcon(entry.route, accountViewModel, selected)
            }
        } else {
            Row(Modifier.padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TabIcon(entry.route, accountViewModel, selected)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = routeTitle(entry.route, accountViewModel),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected || hovered) {
                    IconButton(onClick = { stacks.close(entry.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(MaterialSymbols.Close, stringRes(Res.string.navigation_close_tab), Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                    }
                } else {
                    Spacer(Modifier.size(24.dp))
                }
            }
        }
        if (unread) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
            )
        }
        menuAt?.let { at -> TabMenu(entry, stacks, at, onDismiss = { menuAt = null }) }
    }
}

/** The tab's picture: the person on a profile, the author on a note, else the screen's icon. */
@Composable
private fun TabIcon(
    route: Route,
    accountViewModel: AccountViewModel,
    selected: Boolean,
) {
    val pictureOf =
        when (route) {
            is Route.Profile -> route.id
            is Route.Note -> {
                // Follows the note, so a tab restored before its note arrived gets the author's picture.
                val note = remember(route.id) { LocalCache.checkGetOrCreateNote(route.id) }
                if (note != null) {
                    val state by note
                        .flow()
                        .metadata.stateFlow
                        .collectAsState()
                    state.note.author?.pubkeyHex
                } else {
                    null
                }
            }
            else -> null
        }
    if (pictureOf != null) {
        LoadUser(pictureOf) { user ->
            if (user != null) {
                ObserveAndDrawInnerUserPicture(user, 18.dp, accountViewModel, Modifier.size(18.dp))
            } else {
                SymbolIcon(routeIcon(route, accountViewModel), selected)
            }
        }
    } else {
        SymbolIcon(routeIcon(route, accountViewModel), selected)
    }
}

@Composable
private fun SymbolIcon(
    symbol: MaterialSymbol,
    selected: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Icon(symbol, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (selected) colors.primary else colors.onSurfaceVariant)
}

/** A tab's right-click menu, opened at [at]: where the click landed in the tab. */
@Composable
private fun TabMenu(
    entry: NavStackEntry,
    stacks: TabsModel,
    at: IntOffset,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val canMoveToWindow = NavigationLayoutType.WINDOWS in LocalAppPlatform.current.navigationLayouts
    val link = remember(entry.route) { routeLink(entry.route) }
    val opened = stacks.openedBy(entry.id)

    // Material's DropdownMenu would open at the tab's corner and keep 48dp off the window's top
    // edge, which the strip sits on: a long way from the cursor. This opens at the cursor itself.
    val position = remember(at) { CursorMenuPositionProvider(at) }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        CursorMenuSurface {
            MenuItem(MaterialSymbols.Add, stringRes(Res.string.navigation_tab_duplicate), onDismiss) { stacks.duplicate(entry.id) }
            MenuItem(MaterialSymbols.PushPin, stringRes(if (stacks.isPinned(entry)) Res.string.navigation_tab_unpin else Res.string.navigation_tab_pin), onDismiss) {
                stacks.setPinned(entry.id, !stacks.isPinned(entry))
            }
            if (link != null) {
                MenuItem(MaterialSymbols.Link, stringRes(Res.string.navigation_tab_copy_link), onDismiss) { scope.launch { clipboard.setText(link) } }
            }
            if (canMoveToWindow) {
                MenuItem(MaterialSymbols.AutoMirrored.OpenInNew, stringRes(Res.string.navigation_tab_to_window), onDismiss) { stacks.setDetached(entry.id, true) }
            }
            HorizontalDivider()
            MenuItem(MaterialSymbols.Close, stringRes(Res.string.navigation_close_tab), onDismiss) { stacks.close(entry.id) }
            MenuItem(null, stringRes(Res.string.navigation_tab_close_others), onDismiss) { stacks.closeOthers(entry.id) }
            MenuItem(null, stringRes(Res.string.navigation_tab_close_right), onDismiss) { stacks.closeToTheRight(entry.id) }
            if (opened > 0) {
                MenuItem(null, stringRes(Res.string.navigation_tab_close_opened, opened), onDismiss) { stacks.closeOpenedBy(entry.id) }
            }
            if (stacks.canReopen) {
                HorizontalDivider()
                MenuItem(MaterialSymbols.History, stringRes(Res.string.navigation_reopen_closed), onDismiss) { stacks.reopenClosed() }
            }
        }
    }
}

/** Material's menu container (its colors, elevation, 112–280dp width and 8dp ends), for a menu in its own Popup. */
@Composable
private fun CursorMenuSurface(content: @Composable () -> Unit) {
    Surface(
        shape = MenuDefaults.shape,
        color = MenuDefaults.containerColor,
        tonalElevation = MenuDefaults.TonalElevation,
        shadowElevation = MenuDefaults.ShadowElevation,
    ) {
        Column(Modifier.widthIn(min = 112.dp, max = 280.dp).width(IntrinsicSize.Max).padding(vertical = 8.dp)) {
            content()
        }
    }
}

/**
 * Places a context menu with its corner on the cursor, [at] from the anchor's top-left: below and
 * after it when it fits, else above or before it, and always inside the window.
 */
private class CursorMenuPositionProvider(
    private val at: IntOffset,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + at.x
        val y = anchorBounds.top + at.y
        val width = popupContentSize.width
        val height = popupContentSize.height
        val after = if (layoutDirection == LayoutDirection.Ltr) x else x - width
        val before = if (layoutDirection == LayoutDirection.Ltr) x - width else x
        val left = if (after >= 0 && after + width <= windowSize.width) after else before
        val top = if (y + height <= windowSize.height) y else y - height
        return IntOffset(
            left.coerceAtMost(windowSize.width - width).coerceAtLeast(0),
            top.coerceAtMost(windowSize.height - height).coerceAtLeast(0),
        )
    }
}

@Composable
private fun MenuItem(
    symbol: MaterialSymbol?,
    label: String,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { if (symbol != null) Icon(symbol, contentDescription = null, modifier = Modifier.size(18.dp)) else Spacer(Modifier.size(18.dp)) },
        onClick = {
            onDismiss()
            onClick()
        },
    )
}

/** Every open tab in a menu, filtered by what the user types: the way to a tab once the strip scrolls. */
@Composable
private fun TabListButton(
    stacks: TabsModel,
    accountViewModel: AccountViewModel,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        StripButton(MaterialSymbols.ExpandMore, stringRes(Res.string.navigation_tab_list)) { open = true }
        if (open) {
            var query by remember { mutableStateOf("") }
            val searchFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { searchFocus.requestFocus() }
            DropdownMenu(expanded = true, onDismissRequest = { open = false }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringRes(Res.string.navigation_tab_search)) },
                    singleLine = true,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).width(280.dp).focusRequester(searchFocus),
                )
                Column(Modifier.heightIn(max = 420.dp)) {
                    stacks.strip().forEach { entry ->
                        key(entry.id) {
                            val title = routeTitle(entry.route, accountViewModel)
                            if (query.isBlank() || title.contains(query.trim(), ignoreCase = true)) {
                                DropdownMenuItem(
                                    text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = { TabIcon(entry.route, accountViewModel, entry.id == stacks.top.id) },
                                    onClick = {
                                        open = false
                                        stacks.activate(entry.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StripButton(
    symbol: MaterialSymbol,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(symbol, contentDescription = description, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
