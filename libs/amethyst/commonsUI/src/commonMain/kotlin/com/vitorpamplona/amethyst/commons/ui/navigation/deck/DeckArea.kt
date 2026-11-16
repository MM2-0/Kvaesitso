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
package com.vitorpamplona.amethyst.commons.ui.navigation.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.deck.DeckColumn
import com.vitorpamplona.amethyst.commons.model.deck.DeckLayout
import com.vitorpamplona.amethyst.commons.model.navigation.NavBackStacks
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.deck_add_column
import com.vitorpamplona.amethyst.commons.resources.deck_close_column
import com.vitorpamplona.amethyst.commons.resources.deck_column_articles
import com.vitorpamplona.amethyst.commons.resources.deck_column_bookmarks
import com.vitorpamplona.amethyst.commons.resources.deck_column_chess
import com.vitorpamplona.amethyst.commons.resources.deck_column_custom_feeds
import com.vitorpamplona.amethyst.commons.resources.deck_column_discover
import com.vitorpamplona.amethyst.commons.resources.deck_column_drafts
import com.vitorpamplona.amethyst.commons.resources.deck_column_follow_packs
import com.vitorpamplona.amethyst.commons.resources.deck_column_hashtag
import com.vitorpamplona.amethyst.commons.resources.deck_column_home
import com.vitorpamplona.amethyst.commons.resources.deck_column_messages
import com.vitorpamplona.amethyst.commons.resources.deck_column_my_profile
import com.vitorpamplona.amethyst.commons.resources.deck_column_notifications
import com.vitorpamplona.amethyst.commons.resources.deck_column_relays
import com.vitorpamplona.amethyst.commons.resources.deck_column_search
import com.vitorpamplona.amethyst.commons.resources.deck_column_settings
import com.vitorpamplona.amethyst.commons.resources.deck_column_wallet
import com.vitorpamplona.amethyst.commons.resources.deck_delete_workspace
import com.vitorpamplona.amethyst.commons.resources.deck_import_notice
import com.vitorpamplona.amethyst.commons.resources.deck_move_left
import com.vitorpamplona.amethyst.commons.resources.deck_move_right
import com.vitorpamplona.amethyst.commons.resources.deck_reset_column
import com.vitorpamplona.amethyst.commons.resources.deck_save_workspace
import com.vitorpamplona.amethyst.commons.resources.deck_workspace_name
import com.vitorpamplona.amethyst.commons.resources.deck_workspace_number
import com.vitorpamplona.amethyst.commons.resources.deck_workspaces
import com.vitorpamplona.amethyst.commons.resources.dismiss
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.NavigationStyle
import com.vitorpamplona.amethyst.commons.ui.layouts.ScreenLayoutSpec
import com.vitorpamplona.amethyst.commons.ui.navigation.host.LocalNavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavigationHost
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPanelHost
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import org.jetbrains.compose.resources.StringResource

/** Screens inside a column lay out as a rail-tier pane: no bottom bar, no docked panels of their own. */
private val ColumnScreenLayout = ScreenLayoutSpec(NavigationStyle.NAV_RAIL, hasRoomForNotificationPanel = false)

/**
 * The deck: the active workspace's columns to the right of the main screen, in a horizontal scroll.
 * Each column is a [Route] with its own back stack, so every shared screen works as a column. A
 * strip on the left switches workspaces and adds columns; each column's header moves, resets and
 * closes it, and the handle on its left edge resizes it. Columns draw from the main window's
 * [LocalNavDestinations], each screen with its column's navigation.
 */
@Composable
fun DeckArea(
    accountViewModel: AccountViewModel,
    mainNav: INav,
    modifier: Modifier = Modifier,
) {
    val settings = accountViewModel.account.settings
    val layout by settings.deck.collectAsState()
    val columns = layout.current.columns
    val scroll = rememberScrollState()
    // Read through state: the command collector below outlives a move to a screen of another density.
    val density by rememberUpdatedState(LocalDensity.current)

    var picking by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }

    val currentColumns by rememberUpdatedState(columns)
    LaunchedEffect(settings) {
        DeckCommandBus.commands.collect { command ->
            // Closing and moving act on the column the user is in, never on a guess.
            val focused = currentColumns.firstOrNull { it.id == DeckFocus.columnId }
            when (command) {
                DeckCommand.AddColumn -> picking = true
                DeckCommand.CloseColumn -> focused?.let { col -> settings.updateDeck { it.removeColumn(col.id) } }
                is DeckCommand.MoveColumn -> focused?.let { col -> settings.updateDeck { it.moveColumn(col.id, command.delta) } }
                is DeckCommand.FocusColumn -> {
                    currentColumns.getOrNull(command.index)?.let { col ->
                        DeckFocus.columnId = col.id
                        // Each column is preceded by its resize handle.
                        val before = currentColumns.take(command.index).sumOf { it.width.toDouble() + ResizeHandleWidth.value }.toFloat()
                        scroll.animateScrollTo(with(density) { before.dp.roundToPx() })
                    }
                }
                DeckCommand.SaveWorkspace -> naming = true
            }
        }
    }

    // The toolbar and the resize handles paint nothing themselves: without this surface the bare
    // window shows through them (light grey on desktop, even in dark mode) and icons lose their tint.
    Surface(modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxHeight()) {
            VerticalDivider(thickness = DividerThickness)
            DeckToolbar(layout, settings, onAddColumn = { picking = true }, onSaveWorkspace = { naming = true })

            Row(Modifier.weight(1f, fill = false).fillMaxHeight().horizontalScroll(scroll)) {
                columns.forEach { column ->
                    key(column.id) {
                        ResizeHandle(column, settings)
                        DeckColumnPane(
                            column = column,
                            focused = column.id == DeckFocus.columnId,
                            onFocus = { DeckFocus.columnId = column.id },
                            settings = settings,
                            mainNav = mainNav,
                        )
                    }
                }
            }
        }
    }

    if (picking) {
        AddColumnDialog(
            myPubKey = accountViewModel.account.signer.pubKey,
            onPick = { route ->
                settings.updateDeck { it.addColumn(route, afterId = DeckFocus.columnId) }
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    if (naming) {
        WorkspaceNameDialog(
            initial = "",
            onSave = { name ->
                settings.updateDeck { it.saveAsWorkspace(name) }
                naming = false
            },
            onDismiss = { naming = false },
        )
    }

    if (layout.importNotice.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { settings.updateDeck { it.copy(importNotice = emptyList()) } },
            text = { Text(stringRes(Res.string.deck_import_notice, layout.importNotice.joinToString(", "))) },
            confirmButton = {
                TextButton(onClick = { settings.updateDeck { it.copy(importNotice = emptyList()) } }) { Text(stringRes(Res.string.dismiss)) }
            },
        )
    }
}

@Composable
private fun DeckToolbar(
    layout: DeckLayout,
    settings: AccountSettings,
    onAddColumn: () -> Unit,
    onSaveWorkspace: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.width(48.dp).fillMaxHeight().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(MaterialSymbols.Dashboard, contentDescription = stringRes(Res.string.deck_workspaces), modifier = Modifier.size(22.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                layout.workspaces.forEachIndexed { index, workspace ->
                    DropdownMenuItem(
                        text = { Text(workspaceName(workspace.name, index)) },
                        leadingIcon = {
                            if (index == layout.active) Icon(MaterialSymbols.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            settings.updateDeck { it.switchTo(index) }
                            menu = false
                        },
                    )
                }
                HorizontalDivider()
                if (layout.workspaces.size < DeckLayout.MAX_WORKSPACES) {
                    DropdownMenuItem(
                        text = { Text(stringRes(Res.string.deck_save_workspace)) },
                        onClick = {
                            menu = false
                            onSaveWorkspace()
                        },
                    )
                }
                if (layout.workspaces.size > 1) {
                    DropdownMenuItem(
                        text = { Text(stringRes(Res.string.deck_delete_workspace, workspaceName(layout.current.name, layout.active))) },
                        onClick = {
                            settings.updateDeck { it.deleteWorkspace(it.active) }
                            menu = false
                        },
                    )
                }
            }
        }
        IconButton(onClick = onAddColumn) {
            Icon(MaterialSymbols.Add, contentDescription = stringRes(Res.string.deck_add_column), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun workspaceName(
    name: String,
    index: Int,
) = name.ifBlank { stringRes(Res.string.deck_workspace_number, index + 1) }

/** The column's left edge: dragging it sideways changes the column's width. */
@Composable
private fun ResizeHandle(
    column: DeckColumn,
    settings: AccountSettings,
) {
    val density = LocalDensity.current
    val id = column.id
    Box(
        Modifier
            .width(ResizeHandleWidth)
            .fillMaxHeight()
            .draggable(
                orientation = Orientation.Horizontal,
                state =
                    rememberDraggableState { delta ->
                        val dp = with(density) { delta.toDp().value }
                        // Dragging the left edge to the left widens the column.
                        settings.updateDeck { layout ->
                            val width =
                                layout.current.columns
                                    .firstOrNull { it.id == id }
                                    ?.width ?: return@updateDeck layout
                            layout.resizeColumn(id, width - dp)
                        }
                    },
            ),
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider(thickness = DividerThickness)
    }
}

@Composable
private fun DeckColumnPane(
    column: DeckColumn,
    focused: Boolean,
    onFocus: () -> Unit,
    settings: AccountSettings,
    mainNav: INav,
) {
    val scope = rememberCoroutineScope()
    val inner = remember(column.id) { Nav(NavBackStacks(column.root), scope) }
    val destinations = checkNotNull(LocalNavDestinations.current)

    // Every column keeps its own ViewModels: entry keys restart at nav-0 in each back stack, and
    // under one owner two columns would share them.
    val owner = remember(column.id) { DeckViewModelStoreOwner() }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }

    // Back and Esc go to this column's own history only while it is the focused one; otherwise they
    // reach the main screen, whose handler a column registered later would otherwise shadow.
    val backOwner = rememberNavigationEventDispatcherOwner(enabled = focused)
    val currentOnFocus by rememberUpdatedState(onFocus)

    val border = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    Column(
        Modifier
            .width(column.width.dp)
            .fillMaxHeight()
            .border(1.dp, border)
            // Any press inside focuses the column, without taking the event from what was pressed.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) currentOnFocus()
                    }
                }
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onFocus),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderButton(MaterialSymbols.Home, Res.string.deck_reset_column) {
                onFocus()
                inner.newStack(column.root)
            }
            Spacer(Modifier.weight(1f))
            HeaderButton(MaterialSymbols.AutoMirrored.KeyboardArrowLeft, Res.string.deck_move_left) { settings.updateDeck { it.moveColumn(column.id, -1) } }
            HeaderButton(MaterialSymbols.AutoMirrored.KeyboardArrowRight, Res.string.deck_move_right) { settings.updateDeck { it.moveColumn(column.id, 1) } }
            HeaderButton(MaterialSymbols.Close, Res.string.deck_close_column) { settings.updateDeck { it.removeColumn(column.id) } }
        }
        HorizontalDivider(thickness = DividerThickness)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides owner,
                LocalScreenLayout provides ColumnScreenLayout,
                LocalNavigationEventDispatcherOwner provides backOwner,
            ) {
                // A composer in this column docks the emoji panel here, under the column. Back reaches
                // this column only while it is focused, so the panel closes when it loses focus.
                EmojiPanelHost(active = focused) {
                    NavigationHost(inner, destinations, navFor = { DeckColumnNav(inner.forEntry(it), mainNav) })
                }
            }
        }
    }
}

private val ResizeHandleWidth = 6.dp

private class DeckViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

@Composable
private fun HeaderButton(
    symbol: MaterialSymbol,
    description: StringResource,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(symbol, contentDescription = stringRes(description), modifier = Modifier.size(18.dp))
    }
}

/** What a new column can show. */
private fun columnChoices(myPubKey: String): List<Pair<StringResource, Route>> =
    listOf(
        Res.string.deck_column_home to Route.Home,
        Res.string.deck_column_notifications to Route.Notification(),
        Res.string.deck_column_messages to Route.Message,
        Res.string.deck_column_discover to Route.Discover(),
        Res.string.deck_column_search to Route.Search(),
        Res.string.deck_column_articles to Route.Articles,
        Res.string.deck_column_bookmarks to Route.Bookmarks,
        Res.string.deck_column_my_profile to Route.Profile(myPubKey),
        Res.string.deck_column_custom_feeds to Route.CustomFeeds,
        Res.string.deck_column_drafts to Route.Drafts,
        Res.string.deck_column_wallet to Route.Wallet,
        Res.string.deck_column_chess to Route.Chess,
        Res.string.deck_column_follow_packs to Route.FollowPacks,
        Res.string.deck_column_relays to Route.EditRelays,
        Res.string.deck_column_settings to Route.AllSettings,
    )

/**
 * The hashtag a typed [input] names, lowercased like every other way into a hashtag feed, or null
 * when it is not one word: "a b" or "#a #b" would make a column that matches nothing.
 */
internal fun columnHashtag(input: String): String? {
    val tag = input.trim().removePrefix("#").lowercase()
    return tag.takeIf { it.isNotEmpty() && it.none { c -> c.isWhitespace() || c == '#' } }
}

@Composable
private fun AddColumnDialog(
    myPubKey: String,
    onPick: (Route) -> Unit,
    onDismiss: () -> Unit,
) {
    var hashtag by remember { mutableStateOf("") }
    val tag = columnHashtag(hashtag)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.deck_add_column)) },
        text = {
            Column {
                columnChoices(myPubKey).forEach { (label, route) ->
                    Text(
                        stringRes(label),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(route) }
                                .padding(vertical = 10.dp),
                    )
                }
                OutlinedTextField(
                    value = hashtag,
                    onValueChange = { hashtag = it },
                    label = { Text(stringRes(Res.string.deck_column_hashtag)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (tag != null) onPick(Route.Hashtag(tag)) }),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (tag != null) onPick(Route.Hashtag(tag)) }, enabled = tag != null) { Text(stringRes(Res.string.deck_add_column)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

@Composable
private fun WorkspaceNameDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.deck_save_workspace)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringRes(Res.string.deck_workspace_name)) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringRes(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}
