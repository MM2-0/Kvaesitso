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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.model.navigation.stableKey
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_settings
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_settings_add
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_settings_added
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_settings_available
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_settings_expand
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_settings_no_favorites
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_settings_no_groups
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_settings_pinned
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_settings_pinned_empty
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_settings_remove
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_settings_reorder_hint
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.BottomBarCategories
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.GroupEntryAvatar
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.GroupEntryDisplay
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.NavBarCatalog
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.NavBarCategory
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.rememberFavoriteIconModel
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.rememberGroupEntryDisplay
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.GroupTag
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.StringResource

/** The chat catalog items whose picker row expands to a per-item picker (favorites / joined groups). */
private val ExpandableItems =
    setOf(
        NavBarItem.BROWSER,
        NavBarItem.PUBLIC_CHATS,
        NavBarItem.RELAY_GROUPS,
        NavBarItem.CONCORD,
        NavBarItem.GEOHASH_CHATS,
    )

/** Soft guidance, not a hard cap: a Material bottom bar reads best at ~5 tabs. */
private const val RECOMMENDED_SLOTS = 5

@Composable
@Preview(device = "spec:width=2100px,height=2340px,dpi=440")
fun BottomBarSettingsScreenPreview() {
    ThemeComparisonRow {
        BottomBarSettingsScreen(
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
fun BottomBarSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.bottom_bar_settings), nav)
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            BottomBarSettingsContent(accountViewModel)
        }
    }
}

@Composable
fun BottomBarSettingsContent(accountViewModel: AccountViewModel) {
    // Per-account bottom bar, synced through the NIP-78 app-specific data event.
    val bottomBarItemsFlow = accountViewModel.bottomBarItemsFlow()
    val savedItems by bottomBarItemsFlow.collectAsStateWithLifecycle()

    // All pin/unpin/reorder logic lives in the holder (unit-tested); the composable only renders and
    // forwards events. Each persist republishes the account's NIP-78 settings event. syncFrom re-seeds
    // when the saved list changes elsewhere without clobbering a drag.
    //
    // Deliberately unkeyed. The holder captures this `accountViewModel` in its persist lambda, so a
    // holder that outlived an account switch would write account A's edits to account B. It cannot:
    // AccountScopedViewModelStore wraps the whole logged-in tree in `key(account.signer.pubKey)`,
    // so a switch disposes this composable (and the NavController with it) and re-runs this remember
    // against the new account's ViewModel. Keying on accountViewModel here would be a no-op that
    // implies the subtree survives a switch — if that ever becomes true, this comment is the bug.
    val state = remember { BottomBarSettingsState(savedItems) { accountViewModel.changeBottomBarItems(it) } }
    LaunchedEffect(savedItems) { state.syncFrom(savedItems) }

    // Edits publish on a debounce; make sure leaving the screen doesn't strand the last one.
    FlushPickerEditsOnExit(accountViewModel)

    val pinned = state.pinned
    val pinnedKeys = remember(pinned) { state.pinnedKeys() }

    val expandedCategories = rememberExpandedKeys<StringResource>()
    val expandedItems = rememberExpandedKeys<NavBarItem>()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))

        // --- The editable bar: a real preview you drag to reorder and tap ✕ to remove from. ---
        EditableBarCard(state, pinned, accountViewModel)

        RestoreDefaultRow(onClick = { state.restoreDefault() })

        Spacer(Modifier.height(4.dp))

        // --- Available catalogue, grouped into collapsible category cards. ---
        PickerSectionHeader(title = stringRes(Res.string.bottom_bar_settings_available))

        BottomBarCategories.forEach { category ->
            CategoryCard(
                category = category,
                pinnedKeys = pinnedKeys,
                expanded = expandedCategories.isExpanded(category.titleRes),
                onToggleExpand = { expandedCategories.toggle(category.titleRes) },
                expandedItems = expandedItems,
                accountViewModel = accountViewModel,
                onTogglePin = state::togglePin,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------------------------------------
// The editable preview bar — WYSIWYG: this IS the reorder & remove surface.
// ------------------------------------------------------------------------------------------------

@Composable
private fun EditableBarCard(
    state: BottomBarSettingsState,
    pinned: List<BottomBarEntry>,
    accountViewModel: AccountViewModel,
) {
    PickerHeroCard(
        title = stringRes(Res.string.bottom_bar_settings_pinned),
        trailing = {
            Text(
                text = "${pinned.size} / $RECOMMENDED_SLOTS",
                style = MaterialTheme.typography.labelMedium,
                color = if (pinned.size > RECOMMENDED_SLOTS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        },
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (pinned.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringRes(Res.string.bottom_bar_settings_pinned_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                EditableBar(state, pinned, accountViewModel)
            }
        }

        Text(
            text = stringRes(Res.string.bottom_bar_settings_reorder_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun EditableBar(
    state: BottomBarSettingsState,
    pinned: List<BottomBarEntry>,
    accountViewModel: AccountViewModel,
) {
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val widths = remember { mutableStateMapOf<Int, Float>() }

    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pinned.forEachIndexed { index, entry ->
            // Key by identity so a swap MOVES the dragged tab (and its live gesture) instead of
            // recomposing a different entry into this slot — which would restart its pointerInput
            // (keyed on entry) and cancel the drag mid-swap.
            key(entry.stableKey) {
                val dragging = draggedIndex == index
                PreviewTab(
                    entry = entry,
                    // The first tab is where the bar lands on open, so preview it as selected.
                    selected = index == 0,
                    dragging = dragging,
                    dragOffsetX = if (dragging) dragOffsetX else 0f,
                    accountViewModel = accountViewModel,
                    onRemove = { state.togglePin(entry) },
                    onMeasured = { widths[index] = it },
                    onDragStart = {
                        draggedIndex = index
                        dragOffsetX = 0f
                    },
                    onDrag = { dx ->
                        dragOffsetX += dx
                        val current = draggedIndex
                        if (current < 0) return@PreviewTab

                        if (dragOffsetX < 0 && current > 0) {
                            val leftW = widths[current - 1] ?: 0f
                            if (-dragOffsetX > leftW / 2f) {
                                state.moveTransient(current, current - 1)
                                dragOffsetX += leftW
                                draggedIndex = current - 1
                            }
                        }
                        if (dragOffsetX > 0 && current < pinned.lastIndex) {
                            val rightW = widths[current + 1] ?: 0f
                            if (dragOffsetX > rightW / 2f) {
                                state.moveTransient(current, current + 1)
                                dragOffsetX -= rightW
                                draggedIndex = current + 1
                            }
                        }
                    },
                    onDragEnd = {
                        draggedIndex = -1
                        dragOffsetX = 0f
                        state.commit()
                    },
                    onDragCancel = {
                        draggedIndex = -1
                        dragOffsetX = 0f
                    },
                )
            }
        }
    }
}

@Composable
private fun RowScope.PreviewTab(
    entry: BottomBarEntry,
    selected: Boolean,
    dragging: Boolean,
    dragOffsetX: Float,
    accountViewModel: AccountViewModel,
    onRemove: () -> Unit,
    onMeasured: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val visual = rememberPinnedVisual(entry, accountViewModel)
    val lift by animateFloatAsState(if (dragging) 1.12f else 1f, label = "tabLift")

    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .zIndex(if (dragging) 1f else 0f)
                .onGloballyPositioned { onMeasured(it.size.width.toFloat()) }
                .graphicsLayer {
                    translationX = dragOffsetX
                    scaleX = lift
                    scaleY = lift
                }.pointerInput(entry.stableKey) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.x)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragCancel() },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            PreviewTabIcon(visual, selected, accountViewModel)
            if (selected) {
                Text(
                    text = visual.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Editing affordance: a small ✕ removes this tab. This is why the preview isn't just a mirror.
        RemoveBadge(onRemove, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun BoxScope.RemoveBadge(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.Close,
            contentDescription = stringRes(Res.string.bottom_bar_settings_remove),
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The icon block of a preview tab: catalog glyph, the favorite's real favicon, or the group's avatar. */
@Composable
private fun PreviewTabIcon(
    visual: PinnedVisual,
    selected: Boolean,
    accountViewModel: AccountViewModel,
) {
    val accent = MaterialTheme.colorScheme.primary
    val tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            Modifier
                .size(width = 42.dp, height = 28.dp)
                .clip(CircleShape)
                .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        when (visual) {
            is PinnedVisual.Glyph -> Icon(symbol = visual.icon, contentDescription = visual.label, modifier = Modifier.size(21.dp), tint = tint)
            is PinnedVisual.Favorite -> FavoriteAppIcon(app = visual.app, tint = tint, modifier = Modifier.size(21.dp), iconModel = rememberFavoriteIconModel(visual.app))
            is PinnedVisual.Avatar -> GroupEntryAvatar(visual.display, 22.dp, accountViewModel)
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Available catalogue — category cards
// ------------------------------------------------------------------------------------------------

@Composable
private fun CategoryCard(
    category: NavBarCategory,
    pinnedKeys: Set<String>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    expandedItems: ExpandedKeys<NavBarItem>,
    accountViewModel: AccountViewModel,
    onTogglePin: (BottomBarEntry) -> Unit,
) {
    CatalogCard(
        icon = category.icon,
        title = stringRes(category.titleRes),
        expanded = expanded,
        onToggleExpand = onToggleExpand,
    ) {
        category.items.forEach { item ->
            val def = NavBarCatalog[item] ?: return@forEach
            val entry = BottomBarEntry.BuiltIn(item)
            if (item in ExpandableItems) {
                ExpandableAvailableRow(
                    icon = def.icon,
                    label = stringRes(def.labelRes),
                    pinned = entry.stableKey in pinnedKeys,
                    expanded = expandedItems.isExpanded(item),
                    onTogglePin = { onTogglePin(entry) },
                    onToggleExpand = { expandedItems.toggle(item) },
                ) {
                    PickerChildren(item, pinnedKeys, accountViewModel, onTogglePin)
                }
            } else {
                AvailableRow(
                    leading = { LeadingGlyph(def.icon, def.imageVector) },
                    label = stringRes(def.labelRes),
                    pinned = entry.stableKey in pinnedKeys,
                    onToggle = { onTogglePin(entry) },
                )
            }
        }
    }
}

/** Child rows (favorites / joined groups) revealed when an expandable picker row opens. */
@Composable
private fun PickerChildren(
    item: NavBarItem,
    pinnedKeys: Set<String>,
    accountViewModel: AccountViewModel,
    onTogglePin: (BottomBarEntry) -> Unit,
) {
    when (item) {
        NavBarItem.BROWSER -> {
            val favorites by LocalAppServices.current.favoriteApps.favorites
                .collectAsStateWithLifecycle()
            if (favorites.isEmpty()) {
                EmptyChildHint(Res.string.bottom_bar_settings_no_favorites)
            } else {
                favorites.forEach { fav ->
                    val entry = BottomBarEntry.Favorite(fav.id)
                    AvailableRow(
                        leading = { FavoriteLeading(fav) },
                        label = fav.label,
                        pinned = entry.stableKey in pinnedKeys,
                        onToggle = { onTogglePin(entry) },
                        indentLevel = 1,
                    )
                }
            }
        }

        NavBarItem.PUBLIC_CHATS -> {
            val channels by accountViewModel.account.publicChatList.flow
                .collectAsStateWithLifecycle()
            val entries = remember(channels) { channels.map { BottomBarEntry.PublicChat(it.eventId) } }
            GroupChildList(entries, pinnedKeys, accountViewModel, onTogglePin)
        }

        NavBarItem.RELAY_GROUPS -> {
            val groups by accountViewModel.account.relayGroupList.liveRelayGroupList
                .collectAsStateWithLifecycle()
            if (groups.isEmpty()) {
                EmptyChildHint(Res.string.bottom_bar_settings_no_groups)
            } else {
                // Group joined groups by their host relay: the relay itself is addable (opens its home
                // page listing every group on it) with each individual group nested beneath it. NIP-29
                // relays are the container, like Concord communities below.
                val byRelay =
                    remember(groups) {
                        groups
                            .groupBy { it.relayUrl }
                            .toList()
                            .sortedBy { it.first.lowercase() }
                    }
                byRelay.forEach { (relayUrl, relayGroups) ->
                    key(relayUrl) {
                        RelayServerPickerGroup(relayUrl, relayGroups, pinnedKeys, accountViewModel, onTogglePin)
                    }
                }
            }
        }

        NavBarItem.CONCORD -> {
            val communities by accountViewModel.account.concordChannelList.liveCommunities
                .collectAsStateWithLifecycle()
            if (communities.isEmpty()) {
                EmptyChildHint(Res.string.bottom_bar_settings_no_groups)
            } else {
                // Group by community: the community itself is addable (opens its channel list) with each
                // channel nested beneath it — the mirror of the relay-group layout above.
                communities.forEach { community ->
                    key(community.id) {
                        ConcordServerPickerGroup(community, pinnedKeys, accountViewModel, onTogglePin)
                    }
                }
            }
        }

        NavBarItem.GEOHASH_CHATS -> {
            val cells by accountViewModel.account.geohashList.flow
                .collectAsStateWithLifecycle()
            val entries = remember(cells) { cells.sorted().map { BottomBarEntry.Geohash(it) } }
            GroupChildList(entries, pinnedKeys, accountViewModel, onTogglePin)
        }

        else -> {}
    }
}

@Composable
private fun GroupChildList(
    entries: List<BottomBarEntry>,
    pinnedKeys: Set<String>,
    accountViewModel: AccountViewModel,
    onTogglePin: (BottomBarEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyChildHint(Res.string.bottom_bar_settings_no_groups)
        return
    }
    entries.forEach { entry ->
        // Read-only: the picker resolves names/avatars from cache, it must not open a REQ per row.
        val display = rememberGroupEntryDisplay(entry, accountViewModel, subscribe = false) ?: return@forEach
        AvailableRow(
            leading = { GroupEntryAvatar(display, 34.dp, accountViewModel) },
            label = display.label,
            pinned = entry.stableKey in pinnedKeys,
            onToggle = { onTogglePin(entry) },
            indentLevel = 1,
        )
    }
}

/**
 * One NIP-29 host relay in the picker: the relay "server" row (level 1 — pin the whole relay, opening
 * its home page of all joined groups) followed by each individual joined group nested at level 2.
 */
@Composable
private fun RelayServerPickerGroup(
    relayUrl: String,
    relayGroups: List<GroupTag>,
    pinnedKeys: Set<String>,
    accountViewModel: AccountViewModel,
    onTogglePin: (BottomBarEntry) -> Unit,
) {
    val serverEntry = remember(relayUrl) { BottomBarEntry.RelayServer(relayUrl) }
    val serverDisplay = rememberGroupEntryDisplay(serverEntry, accountViewModel, subscribe = false)
    AvailableRow(
        leading = {
            if (serverDisplay != null) {
                GroupEntryAvatar(serverDisplay, 34.dp, accountViewModel)
            } else {
                LeadingGlyph(MaterialSymbols.Dns)
            }
        },
        label = serverDisplay?.label ?: relayUrl,
        pinned = serverEntry.stableKey in pinnedKeys,
        onToggle = { onTogglePin(serverEntry) },
        indentLevel = 1,
    )

    val sorted = remember(relayGroups) { relayGroups.sortedBy { (it.name ?: it.groupId).lowercase() } }
    sorted.forEach { tag ->
        val entry = BottomBarEntry.RelayGroup(tag.groupId, tag.relayUrl)
        val display = rememberGroupEntryDisplay(entry, accountViewModel, subscribe = false) ?: return@forEach
        AvailableRow(
            leading = { GroupEntryAvatar(display, 30.dp, accountViewModel) },
            label = display.label,
            pinned = entry.stableKey in pinnedKeys,
            onToggle = { onTogglePin(entry) },
            indentLevel = 2,
        )
    }
}

/**
 * One Concord community in the picker: the community "server" row (level 1 — pin the whole community,
 * opening its channel list) followed by each folded channel nested at level 2. Channels come from the
 * community session's folded Control Plane; before it folds (or if its relays are dead) the list is
 * empty and only the community itself can be pinned.
 */
@Composable
private fun ConcordServerPickerGroup(
    community: ConcordCommunityListEntry,
    pinnedKeys: Set<String>,
    accountViewModel: AccountViewModel,
    onTogglePin: (BottomBarEntry) -> Unit,
) {
    val serverEntry = remember(community.id, community.relays) { BottomBarEntry.Concord(community.id, community.relays) }
    val serverDisplay = rememberGroupEntryDisplay(serverEntry, accountViewModel, subscribe = false)
    AvailableRow(
        leading = {
            if (serverDisplay != null) {
                GroupEntryAvatar(serverDisplay, 34.dp, accountViewModel)
            } else {
                LeadingGlyph(MaterialSymbols.Group)
            }
        },
        label = serverDisplay?.label ?: community.name.ifBlank { community.id.take(8) },
        pinned = serverEntry.stableKey in pinnedKeys,
        onToggle = { onTogglePin(serverEntry) },
        indentLevel = 1,
    )

    val account = accountViewModel.account
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    val session = remember(community.id, revision) { account.concordSessions.sessionFor(community.id) }
    val state by (session?.state ?: remember { MutableStateFlow(null) }).collectAsStateWithLifecycle()

    val channels =
        remember(state) {
            state
                ?.channels
                ?.values
                ?.toList()
                .orEmpty()
        }
    channels.forEach { channel ->
        val entry = BottomBarEntry.ConcordChannel(community.id, channel.channelIdHex, community.relays)
        val def = channel.definition
        val icon = if (def.private) MaterialSymbols.Lock else MaterialSymbols.Tag
        AvailableRow(
            leading = { LeadingGlyph(icon) },
            label = def.name.ifBlank { channel.channelIdHex.take(8) },
            pinned = entry.stableKey in pinnedKeys,
            onToggle = { onTogglePin(entry) },
            indentLevel = 2,
        )
    }
}

// ------------------------------------------------------------------------------------------------
// Rows & shared bits
// ------------------------------------------------------------------------------------------------

@Composable
private fun AvailableRow(
    leading: @Composable () -> Unit,
    label: String,
    pinned: Boolean,
    onToggle: () -> Unit,
    indentLevel: Int = 0,
) {
    CatalogRow(
        leading = leading,
        label = label,
        onToggle = onToggle,
        indentLevel = indentLevel,
    ) {
        AddPill(added = pinned, onClick = onToggle)
    }
}

@Composable
private fun ExpandableAvailableRow(
    icon: MaterialSymbol,
    label: String,
    pinned: Boolean,
    expanded: Boolean,
    onTogglePin: () -> Unit,
    onToggleExpand: () -> Unit,
    children: @Composable () -> Unit,
) {
    CatalogRow(
        leading = { LeadingGlyph(icon) },
        label = label,
        onToggle = onToggleExpand,
    ) {
        Icon(
            symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = stringRes(Res.string.bottom_bar_settings_expand),
            modifier = Size22Modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AddPill(added = pinned, onClick = onTogglePin)
    }
    AnimatedVisibility(visible = expanded, enter = SectionExpand, exit = SectionCollapse) {
        Column { children() }
    }
}

/** Outlined "Add" that fills to "Added" once pinned — states the action and its result. */
@Composable
private fun AddPill(
    added: Boolean,
    onClick: () -> Unit,
) {
    TogglePill(
        on = added,
        label = stringRes(if (added) Res.string.bottom_bar_settings_added else Res.string.bottom_bar_settings_add),
        icon = if (added) MaterialSymbols.Check else MaterialSymbols.Add,
        onClick = onClick,
    )
}

/** A favorite web-app / nsite / napplet's real favicon in a tinted circle (glyph fallback). */
@Composable
private fun FavoriteLeading(app: FavoriteApp) {
    Box(
        modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        FavoriteAppIcon(
            app = app,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
            iconModel = rememberFavoriteIconModel(app),
        )
    }
}

// ------------------------------------------------------------------------------------------------
// Leading/label resolution for a pinned entry (built-in glyph, favorite icon, or group avatar).
// Computed once so a group's channel is subscribed at most once per row.
// ------------------------------------------------------------------------------------------------

private sealed interface PinnedVisual {
    val label: String

    data class Glyph(
        val icon: MaterialSymbol,
        override val label: String,
    ) : PinnedVisual

    data class Favorite(
        val app: FavoriteApp,
    ) : PinnedVisual {
        override val label: String get() = app.label
    }

    data class Avatar(
        val display: GroupEntryDisplay,
    ) : PinnedVisual {
        override val label: String get() = display.label
    }
}

@Composable
private fun rememberPinnedVisual(
    entry: BottomBarEntry,
    accountViewModel: AccountViewModel,
): PinnedVisual =
    when (entry) {
        is BottomBarEntry.BuiltIn -> {
            val def = NavBarCatalog[entry.item]
            PinnedVisual.Glyph(def?.icon ?: MaterialSymbols.Apps, def?.let { stringRes(it.labelRes) } ?: "")
        }
        is BottomBarEntry.Favorite -> {
            val favorites by LocalAppServices.current.favoriteApps.favorites
                .collectAsStateWithLifecycle()
            val app = favorites.firstOrNull { it.id == entry.favoriteId }
            if (app != null) PinnedVisual.Favorite(app) else PinnedVisual.Glyph(MaterialSymbols.Public, "")
        }
        is BottomBarEntry.PublicChat,
        is BottomBarEntry.RelayGroup,
        is BottomBarEntry.RelayServer,
        is BottomBarEntry.Concord,
        is BottomBarEntry.ConcordChannel,
        is BottomBarEntry.Geohash,
        -> {
            // Read-only: the settings list resolves from cache; the live bar owns the subscription.
            val display = rememberGroupEntryDisplay(entry, accountViewModel, subscribe = false)
            if (display != null) PinnedVisual.Avatar(display) else PinnedVisual.Glyph(MaterialSymbols.Group, "")
        }
    }
