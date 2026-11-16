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
package com.vitorpamplona.amethyst.commons.ui.note.elements

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuFirstRowInset
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuItemModifier
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSurface
import com.vitorpamplona.amethyst.commons.ui.components.SubmenuPositionProvider
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import kotlinx.coroutines.delay
import androidx.compose.runtime.key as composeKey

private val MenuItemHeight = 36.dp

/**
 * How long the pointer may rest on another row before an open submenu closes. Moving diagonally
 * from a submenu's row to one of its lower items crosses the rows below; this keeps the submenu
 * open on the way.
 */
private const val SUBMENU_CLOSE_DELAY_MS = 300L

/** One row of the dropdown: an action, or a group of actions folded into a submenu. */
@Immutable
private sealed interface MenuEntry {
    class Single(
        val action: NoteAction,
    ) : MenuEntry

    class Submenu(
        val group: NoteActionGroup,
        val actions: List<NoteAction>,
    ) : MenuEntry
}

/**
 * Folds each run of consecutive actions that share a [NoteActionGroup] into one submenu. A group
 * with a single visible action stays a plain row: a submenu of one only adds a hover.
 */
private fun foldGroups(section: List<NoteAction>): List<MenuEntry> {
    val entries = mutableListOf<MenuEntry>()
    var i = 0
    while (i < section.size) {
        val group = section[i].group
        var end = i + 1
        if (group != null) {
            while (end < section.size && section[end].group == group) end++
        }
        if (group != null && end - i > 1) {
            entries.add(MenuEntry.Submenu(group, section.subList(i, end)))
        } else {
            entries.add(MenuEntry.Single(section[i]))
        }
        i = end
    }
    return entries
}

/** Which submenu is open, and whether a click or a key opened it (and so should take focus). */
@Immutable
private data class OpenSubmenu(
    val group: NoteActionGroup,
    val byKeyOrClick: Boolean,
)

/**
 * The note actions as a dropdown attached to the ⋮ button, for large windows (the rail and
 * docked-drawer tiers), where a centered dialog covering the feed reads as a phone pattern.
 *
 * Renders the same [sections] the dialog and the chat long-press sheet do, each in a rounded group.
 * Actions of one [NoteActionGroup] fold into a submenu, so the full inventory fits under the
 * button. Hovering its row opens it for the pointer; a click, Enter or the arrow key toward it
 * opens it with focus on its first item, for the keyboard.
 */
@Composable
fun NoteActionDropdownMenu(
    sections: List<List<NoteAction>>,
    onDismiss: () -> Unit,
) {
    var open by remember { mutableStateOf<OpenSubmenu?>(null) }
    // Folded once per inventory, not on every hover that opens or closes a submenu.
    val entries = remember(sections) { sections.map(::foldGroups) }

    RoundedDropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
    ) {
        entries.forEach { section ->
            RoundedMenuSection {
                section.forEach { entry ->
                    when (entry) {
                        is MenuEntry.Single -> {
                            ActionItem(
                                label = entry.action.label,
                                symbol = entry.action.symbol,
                                isDestructive = entry.action.isDestructive,
                                onHover = { open = null },
                                hoverDelayMs = SUBMENU_CLOSE_DELAY_MS,
                                onClick = entry.action.onClick,
                            )
                        }

                        is MenuEntry.Submenu -> {
                            SubmenuItem(
                                group = entry.group,
                                actions = entry.actions,
                                open = open?.takeIf { it.group == entry.group },
                                onOpen = { byKeyOrClick -> open = OpenSubmenu(entry.group, byKeyOrClick) },
                                onClose = { if (open?.group == entry.group) open = null },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmenuItem(
    group: NoteActionGroup,
    actions: List<NoteAction>,
    open: OpenSubmenu?,
    onOpen: (byKeyOrClick: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val towardSubmenu = if (LocalLayoutDirection.current == LayoutDirection.Ltr) Key.DirectionRight else Key.DirectionLeft

    Box {
        ActionItem(
            label = stringRes(group.label),
            symbol = group.symbol,
            isDestructive = false,
            onHover = { onOpen(false) },
            onClick = { onOpen(true) },
            trailing = MaterialSymbols.AutoMirrored.KeyboardArrowRight,
            modifier =
                Modifier.onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == towardSubmenu) {
                        onOpen(true)
                        true
                    } else {
                        false
                    }
                },
        )

        if (open != null) {
            Submenu(actions, takeFocus = open.byKeyOrClick, onClose = onClose)
        }
    }
}

/**
 * The submenu beside its row. Opened by the pointer it is not focusable: a focusable popup takes
 * the pointer, so hovering another row of the parent menu could not close it. Opened by a click
 * or a key it is focusable and starts on its first item, so the keyboard can reach its actions.
 */
@Composable
private fun Submenu(
    actions: List<NoteAction>,
    takeFocus: Boolean,
    onClose: () -> Unit,
) {
    val insetPx = with(LocalDensity.current) { RoundedMenuFirstRowInset.roundToPx() }
    val positionProvider = remember(insetPx) { SubmenuPositionProvider(insetPx) }
    val awayFromSubmenu = if (LocalLayoutDirection.current == LayoutDirection.Ltr) Key.DirectionLeft else Key.DirectionRight

    // Keyed, so switching between a hover-opened and a key-opened submenu rebuilds the popup with
    // the matching focus behaviour.
    composeKey(takeFocus) {
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onClose,
            properties = PopupProperties(focusable = takeFocus),
        ) {
            val focusManager = LocalFocusManager.current
            val firstItem = remember { FocusRequester() }

            RoundedMenuSurface {
                RoundedMenuSection {
                    actions.forEachIndexed { index, action ->
                        ActionItem(
                            label = action.label,
                            symbol = action.symbol,
                            isDestructive = action.isDestructive,
                            onHover = {},
                            onClick = action.onClick,
                            modifier =
                                (if (index == 0) Modifier.focusRequester(firstItem) else Modifier)
                                    .onPreviewKeyEvent {
                                        if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when (it.key) {
                                            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Next)
                                            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Previous)
                                            awayFromSubmenu -> {
                                                onClose()
                                                true
                                            }
                                            else -> false
                                        }
                                    },
                        )
                    }
                }
            }

            if (takeFocus) {
                LaunchedEffect(Unit) { firstItem.requestFocus() }
            }
        }
    }
}

@Composable
private fun ActionItem(
    label: String,
    symbol: MaterialSymbol,
    isDestructive: Boolean,
    onHover: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hoverDelayMs: Long = 0,
    trailing: MaterialSymbol? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val currentOnHover by rememberUpdatedState(onHover)
    // Restarted when the pointer leaves, so a row it only crosses never fires.
    LaunchedEffect(hovered) {
        if (hovered) {
            if (hoverDelayMs > 0) delay(hoverDelayMs)
            currentOnHover()
        }
    }

    val colors =
        if (isDestructive) {
            MenuDefaults.itemColors(
                textColor = MaterialTheme.colorScheme.error,
                leadingIconColor = MaterialTheme.colorScheme.error,
            )
        } else {
            MenuDefaults.itemColors()
        }

    DropdownMenuItem(
        // One line: the rows have a fixed height.
        text = { Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        modifier = modifier.then(RoundedMenuItemModifier).height(MenuItemHeight),
        leadingIcon = { Icon(symbol = symbol, contentDescription = null, modifier = Size20Modifier) },
        trailingIcon =
            trailing?.let {
                { Icon(symbol = it, contentDescription = null, modifier = Size20Modifier) }
            },
        colors = colors,
        interactionSource = interactionSource,
    )
}
