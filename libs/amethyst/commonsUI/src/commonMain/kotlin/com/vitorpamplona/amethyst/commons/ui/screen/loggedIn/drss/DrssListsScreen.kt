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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drss

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.drss.DrssTree
import com.vitorpamplona.amethyst.commons.model.drss.DrssTreeEntry
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drss_badge_read
import com.vitorpamplona.amethyst.commons.resources.drss_badge_write
import com.vitorpamplona.amethyst.commons.resources.drss_cancel
import com.vitorpamplona.amethyst.commons.resources.drss_delete
import com.vitorpamplona.amethyst.commons.resources.drss_delete_confirm
import com.vitorpamplona.amethyst.commons.resources.drss_folder_name
import com.vitorpamplona.amethyst.commons.resources.drss_listen_title
import com.vitorpamplona.amethyst.commons.resources.drss_lists_empty
import com.vitorpamplona.amethyst.commons.resources.drss_lists_title
import com.vitorpamplona.amethyst.commons.resources.drss_make_read
import com.vitorpamplona.amethyst.commons.resources.drss_make_write
import com.vitorpamplona.amethyst.commons.resources.drss_merge_into
import com.vitorpamplona.amethyst.commons.resources.drss_move_to
import com.vitorpamplona.amethyst.commons.resources.drss_move_to_new_folder
import com.vitorpamplona.amethyst.commons.resources.drss_none
import com.vitorpamplona.amethyst.commons.resources.drss_open_history
import com.vitorpamplona.amethyst.commons.resources.drss_pick_folder
import com.vitorpamplona.amethyst.commons.resources.drss_picture_title
import com.vitorpamplona.amethyst.commons.resources.drss_picture_url
import com.vitorpamplona.amethyst.commons.resources.drss_read_as_feed
import com.vitorpamplona.amethyst.commons.resources.drss_read_list
import com.vitorpamplona.amethyst.commons.resources.drss_remove_from_folder
import com.vitorpamplona.amethyst.commons.resources.drss_remove_picture
import com.vitorpamplona.amethyst.commons.resources.drss_rename
import com.vitorpamplona.amethyst.commons.resources.drss_save
import com.vitorpamplona.amethyst.commons.resources.drss_set_picture
import com.vitorpamplona.amethyst.commons.resources.drss_untitled
import com.vitorpamplona.amethyst.commons.resources.drss_write_list
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectSingleFromGallery
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.DrssChip
import com.vitorpamplona.amethyst.commons.ui.note.types.DrssCover
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadToDefaultServer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.drss.DrssListPicture
import com.vitorpamplona.quartz.drss.eventList.DrssEventListEvent
import com.vitorpamplona.quartz.drss.feedList.DrssFeedListEvent
import com.vitorpamplona.quartz.drss.listFolder.DrssListFolderEvent
import com.vitorpamplona.quartz.drss.provenance.DrssProvenanceFolderEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.BookmarkSetEvent
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

/** What the organizer is asking the user, if anything. */
private sealed class DrssListsDialog {
    class PickFolder(
        val exclude: Set<Address>,
        val onPicked: (Address) -> Unit,
    ) : DrssListsDialog()

    class Name(
        val initial: String,
        val onDone: (String) -> Unit,
    ) : DrssListsDialog()

    class ConfirmDelete(
        val onConfirm: () -> Unit,
    ) : DrssListsDialog()

    class Picture(
        val initial: String,
        val onDone: (String?) -> Unit,
    ) : DrssListsDialog()
}

/**
 * The user's drss.io lists: which folder the river reads and which one follows and saves go into,
 * then every folder tree, where a list or folder can be read as a feed, renamed, moved, merged,
 * taken out of its folder or deleted, the way drss's Lists organizer does.
 */
@Composable
fun DrssListsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val drss = account.drss
    val me = account.signer.pubKey
    val version by drss.version.collectAsStateWithLifecycle()
    val read by drss.readList.collectAsStateWithLifecycle()
    val write by drss.writeList.collectAsStateWithLifecycle()
    val folders by drss.folders.collectAsStateWithLifecycle()

    val entries =
        remember(version, folders) {
            val roots = drss.rootFolders().map { it.address() }
            roots.flatMap { DrssTree.walk(it) }
        }
    var dialog by remember { mutableStateOf<DrssListsDialog?>(null) }

    fun pickFolder(
        exclude: Set<Address> = emptySet(),
        onPicked: (Address) -> Unit,
    ) {
        dialog = DrssListsDialog.PickFolder(exclude, onPicked)
    }

    DrssScreenFrame(stringRes(Res.string.drss_lists_title), nav) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DrssChip(stringRes(Res.string.drss_listen_title), MaterialSymbols.Headphones) { nav.nav(Route.DrssListen) }
                    DrssChip(stringRes(Res.string.drss_open_history), MaterialSymbols.History) { nav.nav(Route.DrssHistory) }
                }
                DrssRoleRow(stringRes(Res.string.drss_read_list), read, accountViewModel) {
                    pickFolder { picked -> accountViewModel.launchSigner { drss.setReadWriteLists(picked, write ?: picked, account) } }
                }
                DrssRoleRow(stringRes(Res.string.drss_write_list), write, accountViewModel) {
                    pickFolder { picked -> accountViewModel.launchSigner { drss.setReadWriteLists(read ?: picked, picked, account) } }
                }
                HorizontalDivider()
            }
            if (entries.isEmpty()) {
                item { DrssHint(stringRes(Res.string.drss_lists_empty)) }
            }
            items(entries, key = { (it.parent?.toValue() ?: "") + "/" + it.address.toValue() }) { entry ->
                val parentOf = { address: Address -> entries.firstOrNull { it.address == address }?.parent }
                val isMine = entry.address.pubKeyHex == me
                val isFolder = entry.address.kind == DrssListFolderEvent.KIND
                DrssTreeRow(
                    entry = entry,
                    isRead = entry.address == read,
                    isWrite = entry.address == write,
                    accountViewModel = accountViewModel,
                    actions =
                        buildList {
                            if (entry.address.kind in READABLE_KINDS) {
                                add(
                                    stringRes(Res.string.drss_read_as_feed) to {
                                        account.settings.changeDefaultHomeFollowList(TopFilter.PeopleList(entry.address))
                                        nav.newStack(Route.Home)
                                    },
                                )
                            }
                            if (isFolder && isMine) {
                                add(stringRes(Res.string.drss_make_read) to { accountViewModel.launchSigner { drss.setReadWriteLists(entry.address, write ?: entry.address, account) } })
                                add(stringRes(Res.string.drss_make_write) to { accountViewModel.launchSigner { drss.setReadWriteLists(read ?: entry.address, entry.address, account) } })
                                add(
                                    stringRes(Res.string.drss_rename) to {
                                        val current = DrssTree.titleOf(DrssTree.eventOf(entry.address)) ?: ""
                                        dialog = DrssListsDialog.Name(current) { title -> accountViewModel.launchSigner { drss.retitle(entry.address, title, account) } }
                                    },
                                )
                            }
                            val parent = entry.parent
                            if (parent != null && parent.pubKeyHex == me) {
                                add(
                                    stringRes(Res.string.drss_move_to) to {
                                        pickFolder(exclude = descendants(entry.address, entries) + parent) { to ->
                                            accountViewModel.launchSigner { drss.move(entry.address, parent, to, account, parentOf(parent)) }
                                        }
                                    },
                                )
                                add(
                                    stringRes(Res.string.drss_move_to_new_folder) to {
                                        dialog =
                                            DrssListsDialog.Name("") { title ->
                                                accountViewModel.launchSigner {
                                                    drss.createFolder(title, entry.address, parent, account)
                                                    drss.removeFromFolder(parent, entry.address, account, parentOf(parent))
                                                }
                                            }
                                    },
                                )
                                if (isFolder && isMine) {
                                    add(
                                        stringRes(Res.string.drss_merge_into) to {
                                            pickFolder(exclude = descendants(entry.address, entries)) { into ->
                                                accountViewModel.launchSigner { drss.merge(entry.address, into, account, parent) }
                                            }
                                        },
                                    )
                                }
                                add(stringRes(Res.string.drss_remove_from_folder) to { accountViewModel.launchSigner { drss.removeFromFolder(parent, entry.address, account, parentOf(parent)) } })
                            }
                            if (isMine && drss.canHavePicture(entry.address.kind)) {
                                add(
                                    stringRes(Res.string.drss_set_picture) to {
                                        val current = DrssTree.eventOf(entry.address)?.let { DrssListPicture.image(it.tags) } ?: ""
                                        dialog = DrssListsDialog.Picture(current) { image -> accountViewModel.launchSigner { drss.setPicture(entry.address, image, account) } }
                                    },
                                )
                            }
                            if (isMine) {
                                add(
                                    stringRes(Res.string.drss_delete) to {
                                        dialog = DrssListsDialog.ConfirmDelete { accountViewModel.launchSigner { drss.deleteList(entry.address, entry.parent, account) } }
                                    },
                                )
                            }
                        },
                )
            }
        }
    }

    when (val current = dialog) {
        is DrssListsDialog.PickFolder -> {
            DrssFolderPicker(
                folders = folders.map { it.address() }.filter { it !in current.exclude },
                accountViewModel = accountViewModel,
                onPicked = {
                    dialog = null
                    current.onPicked(it)
                },
                onDismiss = { dialog = null },
            )
        }
        is DrssListsDialog.Name -> {
            DrssNameDialog(current.initial, onDone = {
                dialog = null
                current.onDone(it)
            }, onDismiss = { dialog = null })
        }
        is DrssListsDialog.ConfirmDelete -> {
            AlertDialog(
                onDismissRequest = { dialog = null },
                text = { Text(stringRes(Res.string.drss_delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        current.onConfirm()
                    }) { Text(stringRes(Res.string.drss_delete)) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringRes(Res.string.drss_cancel)) } },
            )
        }
        is DrssListsDialog.Picture -> {
            DrssPictureDialog(current.initial, accountViewModel, onDone = {
                dialog = null
                current.onDone(it)
            }, onDismiss = { dialog = null })
        }
        null -> {}
    }
}

/** The kinds the river can read: folders and the lists of people under them. */
private val READABLE_KINDS = setOf(DrssListFolderEvent.KIND, DrssFeedListEvent.KIND, FollowSetEvent.KIND)

/** [address] and everything under it in [entries]: where it cannot move into. */
private fun descendants(
    address: Address,
    entries: List<DrssTreeEntry>,
): Set<Address> {
    val out = mutableSetOf(address)
    var grew = true
    while (grew) {
        grew = false
        entries.forEach { if (it.parent in out && out.add(it.address)) grew = true }
    }
    return out
}

@Composable
private fun DrssRoleRow(
    label: String,
    folder: Address?,
    accountViewModel: AccountViewModel,
    onPick: () -> Unit,
) {
    val note = remember(folder) { folder?.let(LocalCache::getOrCreateAddressableNote) }
    val title = note?.let { observeNoteEvent<Event>(it, accountViewModel).value }?.let(DrssTree::titleOf)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText)
            Text(
                title ?: folder?.dTag ?: stringRes(Res.string.drss_none),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Icon(symbol = MaterialSymbols.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DrssTreeRow(
    entry: DrssTreeEntry,
    isRead: Boolean,
    isWrite: Boolean,
    accountViewModel: AccountViewModel,
    actions: List<Pair<String, () -> Unit>>,
) {
    val note = remember(entry.address) { LocalCache.getOrCreateAddressableNote(entry.address) }
    val event by observeNoteEvent<Event>(note, accountViewModel)
    var menu by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp + (entry.depth * 18).dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val picture = event?.let { DrssListPicture.preview(it.tags) }
        if (picture != null) {
            DrssCover(picture, 28.dp, iconFor(entry.address.kind), accountViewModel)
        } else {
            Icon(symbol = iconFor(entry.address.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =
                    DrssTree.titleOf(event) ?: entry.address.dTag
                        .substringAfterLast('/')
                        .ifBlank { stringRes(Res.string.drss_untitled) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (entry.address.kind == DrssListFolderEvent.KIND) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isRead) DrssRoleBadge(stringRes(Res.string.drss_badge_read))
            if (isWrite) DrssRoleBadge(stringRes(Res.string.drss_badge_write))
        }
        if (actions.isNotEmpty()) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(symbol = MaterialSymbols.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.grayText)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    actions.forEach { (label, action) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            menu = false
                            action()
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun DrssRoleBadge(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier =
            Modifier
                .padding(start = 6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

private fun iconFor(kind: Int): MaterialSymbol =
    when (kind) {
        DrssListFolderEvent.KIND -> MaterialSymbols.Folder
        FollowSetEvent.KIND, DrssFeedListEvent.KIND -> MaterialSymbols.Group
        ArticleCurationSetEvent.KIND -> MaterialSymbols.AutoMirrored.Article
        BookmarkSetEvent.KIND -> MaterialSymbols.Bookmark
        DrssEventListEvent.KIND -> MaterialSymbols.Collections
        DrssProvenanceFolderEvent.KIND -> MaterialSymbols.TravelExplore
        else -> MaterialSymbols.AutoMirrored.Feed
    }

@Composable
private fun DrssFolderPicker(
    folders: List<Address>,
    accountViewModel: AccountViewModel,
    onPicked: (Address) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.drss_pick_folder)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                folders.forEach { address ->
                    val note = remember(address) { LocalCache.getOrCreateAddressableNote(address) }
                    val event by observeNoteEvent<Event>(note, accountViewModel)
                    Row(
                        Modifier.fillMaxWidth().clickable { onPicked(address) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(symbol = MaterialSymbols.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(DrssTree.titleOf(event) ?: address.dTag, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.drss_cancel)) } },
    )
}

/**
 * A list's picture: a link, or a file uploaded to the user's media server. Saving an empty link,
 * or Remove, takes the picture away.
 */
@Composable
private fun DrssPictureDialog(
    initial: String,
    accountViewModel: AccountViewModel,
    onDone: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initial) }
    var isUploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.drss_picture_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (url.isNotBlank()) DrssCover(url.trim(), 96.dp, MaterialSymbols.Image, accountViewModel)
                SpellCheckedOutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text(stringRes(Res.string.drss_picture_url)) },
                    leadingIcon = {
                        SelectSingleFromGallery(
                            isUploading = isUploading,
                            tint = MaterialTheme.colorScheme.grayText,
                            modifier = Modifier.padding(start = 2.dp),
                        ) { media ->
                            scope.launch(Dispatchers.IO) {
                                isUploading = true
                                val uploaded = uploadToDefaultServer(media, accountViewModel.account, accountViewModel.mediaUploader, accountViewModel.toastManager::toast)
                                isUploading = false
                                if (uploaded != null) url = uploaded
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(url.trim().ifBlank { null }) }, enabled = !isUploading) { Text(stringRes(Res.string.drss_save)) }
        },
        dismissButton = {
            Row {
                if (initial.isNotBlank()) TextButton(onClick = { onDone(null) }) { Text(stringRes(Res.string.drss_remove_picture)) }
                TextButton(onClick = onDismiss) { Text(stringRes(Res.string.drss_cancel)) }
            }
        },
    )
}

@Composable
private fun DrssNameDialog(
    initial: String,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.drss_folder_name)) },
        text = {
            SpellCheckedOutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank()) { Text(stringRes(Res.string.drss_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.drss_cancel)) } },
    )
}
