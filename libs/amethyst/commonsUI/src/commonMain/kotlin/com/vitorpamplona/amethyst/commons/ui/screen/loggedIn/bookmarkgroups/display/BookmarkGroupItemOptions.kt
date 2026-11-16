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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.bookmarkgroups.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeEditDraftTo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.block_report
import com.vitorpamplona.amethyst.commons.resources.bookmark_item_actions_dialog_title
import com.vitorpamplona.amethyst.commons.resources.bookmark_remove_action_label
import com.vitorpamplona.amethyst.commons.resources.broadcast
import com.vitorpamplona.amethyst.commons.resources.copy_note_id
import com.vitorpamplona.amethyst.commons.resources.copy_text
import com.vitorpamplona.amethyst.commons.resources.copy_user_pubkey
import com.vitorpamplona.amethyst.commons.resources.edit_article
import com.vitorpamplona.amethyst.commons.resources.edit_draft
import com.vitorpamplona.amethyst.commons.resources.edit_post
import com.vitorpamplona.amethyst.commons.resources.follow
import com.vitorpamplona.amethyst.commons.resources.move_bookmark_to_private_label
import com.vitorpamplona.amethyst.commons.resources.move_bookmark_to_public_label
import com.vitorpamplona.amethyst.commons.resources.propose_an_edit
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.resources.quick_action_share_browser_link
import com.vitorpamplona.amethyst.commons.resources.request_deletion
import com.vitorpamplona.amethyst.commons.resources.timestamp_it
import com.vitorpamplona.amethyst.commons.resources.timestamp_pending
import com.vitorpamplona.amethyst.commons.resources.unfollow
import com.vitorpamplona.amethyst.commons.ui.actions.EditPostView
import com.vitorpamplona.amethyst.commons.ui.components.ClickableBox
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.commons.ui.note.copyNoteTextAction
import com.vitorpamplona.amethyst.commons.ui.note.elements.DropDownParams
import com.vitorpamplona.amethyst.commons.ui.note.elements.observeBookmarksFollowsAndAccount
import com.vitorpamplona.amethyst.commons.ui.note.externalLinkForNote
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.report.ReportNoteDialog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Composable
fun BookmarkGroupItemOptions(
    baseNote: Note,
    isBookmarkItemPrivate: Boolean,
    onMoveBookmarkToPublic: () -> Unit,
    onMoveBookmarkToPrivate: () -> Unit,
    onDeleteBookmarkItem: () -> Unit,
    editState: State<GenericLoadable<EditState>>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val popupExpanded = remember { mutableStateOf(false) }

    ClickableBox(
        modifier = Size24Modifier,
        onClick = { popupExpanded.value = true },
    ) {
        VerticalDotsIcon()
    }

    if (popupExpanded.value) {
        BookmarkGroupItemOptionsMenu(
            note = baseNote,
            isBookmarkItemPrivate = isBookmarkItemPrivate,
            onDismiss = { popupExpanded.value = false },
            onMoveBookmarkToPublic = onMoveBookmarkToPublic,
            onMoveBookmarkToPrivate = onMoveBookmarkToPrivate,
            onDeleteBookmarkItem = onDeleteBookmarkItem,
            editState = editState,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun BookmarkGroupItemOptionsMenu(
    note: Note,
    isBookmarkItemPrivate: Boolean,
    onDismiss: () -> Unit,
    onMoveBookmarkToPublic: () -> Unit,
    onMoveBookmarkToPrivate: () -> Unit,
    onDeleteBookmarkItem: () -> Unit,
    editState: State<GenericLoadable<EditState>>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val quickActionShareBrowserLinkStr = stringRes(Res.string.quick_action_share_browser_link)
    val quickActionShareStr = stringRes(Res.string.quick_action_share)
    var reportDialogShowing by remember { mutableStateOf(false) }

    val state by observeBookmarksFollowsAndAccount(note, accountViewModel).collectAsStateWithLifecycle(
        DropDownParams(
            isFollowingAuthor = false,
            isPrivateBookmarkNote = false,
            isPublicBookmarkNote = false,
            isPinnedNote = false,
            isLoggedUser = false,
            isSensitive = false,
            showSensitiveContent = null,
        ),
    )

    val wantsToEditPost =
        remember {
            mutableStateOf(false)
        }

    if (wantsToEditPost.value) {
        // avoids changing while drafting a note and a new event shows up.
        val versionLookingAt =
            remember {
                (editState?.value as? GenericLoadable.Loaded)?.loaded?.modificationToShow?.value
            }

        EditPostView(
            onClose = {
                onDismiss()
                wantsToEditPost.value = false
            },
            edit = note,
            versionLookingAt = versionLookingAt,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    M3ActionDialog(
        title = stringRes(Res.string.bookmark_item_actions_dialog_title),
        onDismiss = onDismiss,
    ) {
        val clipboardManager = LocalClipboard.current
        val sharer = rememberTextSharer()
        val scope = rememberCoroutineScope()

        // Bookmark Management section
        M3ActionSection {
            M3ActionRow(
                icon = if (isBookmarkItemPrivate) MaterialSymbols.LockOpen else MaterialSymbols.Lock,
                text = stringRes(if (isBookmarkItemPrivate) Res.string.move_bookmark_to_public_label else Res.string.move_bookmark_to_private_label),
            ) {
                if (isBookmarkItemPrivate) onMoveBookmarkToPublic() else onMoveBookmarkToPrivate()
            }
            M3ActionRow(
                icon = MaterialSymbols.BookmarkRemove,
                text = stringRes(Res.string.bookmark_remove_action_label),
                isDestructive = true,
            ) {
                onDeleteBookmarkItem()
                onDismiss()
            }
        }

        // Follow section
        M3ActionSection {
            if (!state.isFollowingAuthor) {
                M3ActionRow(icon = MaterialSymbols.PersonAdd, text = stringRes(Res.string.follow)) {
                    val author = note.author ?: return@M3ActionRow
                    accountViewModel.follow(author)
                    onDismiss()
                }
            } else {
                M3ActionRow(icon = MaterialSymbols.PersonRemove, text = stringRes(Res.string.unfollow)) {
                    val author = note.author ?: return@M3ActionRow
                    accountViewModel.unfollow(author)
                    onDismiss()
                }
            }
        }

        // When the rendered note was translated, Copy Text opens a chooser (Copy
        // Original / Copy Translated) on top of the menu; the menu dismisses only
        // after the flow resolves so the chooser survives in composition.
        val copyNoteText =
            copyNoteTextAction(
                accountViewModel = accountViewModel,
                onCopied = onDismiss,
                onDismiss = onDismiss,
            )

        // Copy & Share section
        M3ActionSection {
            M3ActionRow(icon = MaterialSymbols.ContentCopy, text = stringRes(Res.string.copy_text)) {
                val lastNoteVersion = (editState?.value as? GenericLoadable.Loaded)?.loaded?.modificationToShow?.value ?: note
                copyNoteText(note, lastNoteVersion)
            }
            M3ActionRow(icon = MaterialSymbols.ContentCopy, text = stringRes(Res.string.copy_user_pubkey)) {
                note.author?.let {
                    scope.launch(Dispatchers.IO) {
                        clipboardManager.setText("nostr:${it.pubkeyNpub()}")
                        onDismiss()
                    }
                }
            }
            M3ActionRow(icon = MaterialSymbols.ContentCopy, text = stringRes(Res.string.copy_note_id)) {
                scope.launch(Dispatchers.IO) {
                    clipboardManager.setText(note.toNostrUri())
                    onDismiss()
                }
            }
            M3ActionRow(icon = MaterialSymbols.Share, text = stringRes(Res.string.quick_action_share)) {
                sharer.share(externalLinkForNote(note), quickActionShareBrowserLinkStr, quickActionShareStr)
                onDismiss()
            }
        }

        // Edit & Broadcast section
        M3ActionSection {
            if (state.isLoggedUser && note.isDraft()) {
                M3ActionRow(icon = MaterialSymbols.Edit, text = stringRes(Res.string.edit_draft)) {
                    nav.nav {
                        routeEditDraftTo(note, accountViewModel.account)
                    }
                }
            }
            if (!note.isDraft()) {
                if (note.event is TextNoteEvent) {
                    if (state.isLoggedUser) {
                        M3ActionRow(icon = MaterialSymbols.Edit, text = stringRes(Res.string.edit_post)) {
                            wantsToEditPost.value = true
                        }
                    } else {
                        M3ActionRow(icon = MaterialSymbols.Edit, text = stringRes(Res.string.propose_an_edit)) {
                            wantsToEditPost.value = true
                        }
                    }
                } else if (note.event is LongFormContentEvent && state.isLoggedUser) {
                    M3ActionRow(icon = MaterialSymbols.Edit, text = stringRes(Res.string.edit_article)) {
                        nav.nav { Route.NewLongFormPost(version = note.idHex) }
                    }
                }
            }
            M3ActionRow(icon = MaterialSymbols.CellTower, text = stringRes(Res.string.broadcast)) {
                accountViewModel.broadcast(note)
                onDismiss()
            }
        }

        // Timestamp & Moderation section
        M3ActionSection {
            if (accountViewModel.account.otsState.hasPendingAttestations(note)) {
                M3ActionRow(icon = MaterialSymbols.Schedule, text = stringRes(Res.string.timestamp_pending)) {
                    onDismiss()
                }
            } else {
                M3ActionRow(icon = MaterialSymbols.Schedule, text = stringRes(Res.string.timestamp_it)) {
                    accountViewModel.timestamp(note)
                    onDismiss()
                }
            }
            if (state.isLoggedUser) {
                M3ActionRow(icon = MaterialSymbols.Delete, text = stringRes(Res.string.request_deletion), isDestructive = true) {
                    accountViewModel.delete(note)
                    onDismiss()
                }
            } else {
                M3ActionRow(icon = MaterialSymbols.Report, text = stringRes(Res.string.block_report), isDestructive = true) {
                    reportDialogShowing = true
                }
            }
        }
    }

    if (reportDialogShowing) {
        ReportNoteDialog(note = note, accountViewModel = accountViewModel) {
            reportDialogShowing = false
            onDismiss()
        }
    }
}
