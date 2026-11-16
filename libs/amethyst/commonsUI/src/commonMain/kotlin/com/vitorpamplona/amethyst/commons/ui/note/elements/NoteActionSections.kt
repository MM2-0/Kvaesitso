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

import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_hashtag_label
import com.vitorpamplona.amethyst.commons.resources.add_to_emoji_list
import com.vitorpamplona.amethyst.commons.resources.add_to_music_playlist
import com.vitorpamplona.amethyst.commons.resources.add_to_private_bookmarks
import com.vitorpamplona.amethyst.commons.resources.add_to_public_bookmarks
import com.vitorpamplona.amethyst.commons.resources.article
import com.vitorpamplona.amethyst.commons.resources.block_report
import com.vitorpamplona.amethyst.commons.resources.bookmarks
import com.vitorpamplona.amethyst.commons.resources.broadcast
import com.vitorpamplona.amethyst.commons.resources.concord_ban_user
import com.vitorpamplona.amethyst.commons.resources.concord_ban_user_body
import com.vitorpamplona.amethyst.commons.resources.concord_ban_user_title
import com.vitorpamplona.amethyst.commons.resources.concord_make_admin
import com.vitorpamplona.amethyst.commons.resources.concord_remove_admin
import com.vitorpamplona.amethyst.commons.resources.copy
import com.vitorpamplona.amethyst.commons.resources.copy_note_id
import com.vitorpamplona.amethyst.commons.resources.copy_raw_json
import com.vitorpamplona.amethyst.commons.resources.copy_text
import com.vitorpamplona.amethyst.commons.resources.copy_user_pubkey
import com.vitorpamplona.amethyst.commons.resources.devtools_inspect_event
import com.vitorpamplona.amethyst.commons.resources.drss_menu_collect_to
import com.vitorpamplona.amethyst.commons.resources.drss_menu_follow_into
import com.vitorpamplona.amethyst.commons.resources.drss_menu_record_provenance
import com.vitorpamplona.amethyst.commons.resources.drss_menu_save_to
import com.vitorpamplona.amethyst.commons.resources.edit_article
import com.vitorpamplona.amethyst.commons.resources.edit_draft
import com.vitorpamplona.amethyst.commons.resources.edit_post
import com.vitorpamplona.amethyst.commons.resources.follow
import com.vitorpamplona.amethyst.commons.resources.follow_set_add_author_from_note_action
import com.vitorpamplona.amethyst.commons.resources.highlight_action
import com.vitorpamplona.amethyst.commons.resources.manage_bookmark_label
import com.vitorpamplona.amethyst.commons.resources.marmot_admin_delete_message
import com.vitorpamplona.amethyst.commons.resources.pin_to_profile
import com.vitorpamplona.amethyst.commons.resources.post
import com.vitorpamplona.amethyst.commons.resources.propose_an_edit
import com.vitorpamplona.amethyst.commons.resources.quick_action_mute_thread
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.resources.quick_action_unmute_thread
import com.vitorpamplona.amethyst.commons.resources.remove_from_emoji_list
import com.vitorpamplona.amethyst.commons.resources.remove_from_private_bookmarks
import com.vitorpamplona.amethyst.commons.resources.remove_from_public_bookmarks
import com.vitorpamplona.amethyst.commons.resources.request_deletion
import com.vitorpamplona.amethyst.commons.resources.room_dismiss_reports
import com.vitorpamplona.amethyst.commons.resources.room_remove_for_everyone_body
import com.vitorpamplona.amethyst.commons.resources.room_remove_for_everyone_title
import com.vitorpamplona.amethyst.commons.resources.timestamp_it
import com.vitorpamplona.amethyst.commons.resources.timestamp_pending
import com.vitorpamplona.amethyst.commons.resources.unfollow
import com.vitorpamplona.amethyst.commons.resources.unpin_from_profile
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.QuickActionAlertDialogOneButton
import com.vitorpamplona.amethyst.commons.ui.note.copyNoteTextAction
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.LightRedColor
import com.vitorpamplona.amethyst.commons.util.toPrettyJson
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource

/** One note action, rendered as a menu row or an action-sheet tile. */
@Immutable
data class NoteAction(
    val symbol: MaterialSymbol,
    val label: String,
    val isDestructive: Boolean = false,
    /** Sibling actions that share a group fold into one submenu in the dropdown menu; other surfaces ignore it. */
    val group: NoteActionGroup? = null,
    val onClick: () -> Unit,
)

/** A submenu of related actions in the note dropdown menu: its label and icon. */
enum class NoteActionGroup(
    val label: StringResource,
    val symbol: MaterialSymbol,
) {
    COPY(Res.string.copy, MaterialSymbols.ContentCopy),
    BOOKMARK(Res.string.bookmarks, MaterialSymbols.Bookmark),
    SHARE(Res.string.quick_action_share, MaterialSymbols.Share),
}

/**
 * Callbacks for actions whose UI is owned by the rendering surface (dialogs,
 * sheets), so the shared inventory stays surface-agnostic.
 */
@Immutable
data class NoteActionHandlers(
    /**
     * Opens the surface's own Share UI (the chat sheet's Share drawer) from a single Share
     * action. Null lists the Share options in the menu itself, as the [NoteActionGroup.SHARE]
     * group (a submenu in the dropdown).
     */
    val onShare: (() -> Unit)?,
    val onEditPost: () -> Unit,
    val onEditDraft: () -> Unit,
    val onAddLabel: () -> Unit,
    val onReport: () -> Unit,
    val onDeleteRequest: () -> Unit,
    // Banning a Concord member re-keys the community for everyone, so the surface
    // owns a confirmation dialog (like delete). Null where a caller never renders
    // Concord messages (they simply never see the Ban tile anyway).
    val onConcordBan: (() -> Unit)? = null,
    // Removing someone else's message for every member cannot be undone, so it is
    // confirmed by the surface too. Null where a caller never renders room messages.
    val onRemoveForEveryone: (() -> Unit)? = null,
    val onDismiss: () -> Unit,
)

/**
 * The shared note-action inventory behind both the 3-dot menu (NoteDropDownMenu)
 * and the chat long-press sheet: one list of sections, each a list of actions with
 * their visibility gating applied, so the two surfaces can never drift.
 *
 * [noteVersionToCopy] lets the menu copy the latest edit of a versioned post; every
 * other caller passes [note].
 */
@Composable
fun noteActionSections(
    note: Note,
    noteVersionToCopy: Note,
    state: DropDownParams,
    handlers: NoteActionHandlers,
    accountViewModel: AccountViewModel,
    nav: INav,
): List<List<NoteAction>> {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // Unsealed rumors (private replies/posts received in gift wraps) are
    // unsigned and must never be referenced by a public event: hide every
    // action that would publish an e-tag of this note (broadcast, edit,
    // OTS timestamp, pin, label, public bookmark).
    val isPrivateRumor = note.isPrivateRumor()

    // drss.io readers keep a read-and-write preference; the drss actions are theirs.
    val hasDrssLists = accountViewModel.account.drss.preference.value != null
    val hasDrssEventLists =
        remember {
            accountViewModel.account.drss
                .eventLists()
                .isNotEmpty()
        }

    val author =
        buildList {
            if (!state.isLoggedUser) {
                if (!state.isFollowingAuthor) {
                    add(
                        NoteAction(MaterialSymbols.PersonAdd, stringRes(Res.string.follow)) {
                            note.author?.let { accountViewModel.follow(it) }
                            handlers.onDismiss()
                        },
                    )
                } else {
                    add(
                        NoteAction(MaterialSymbols.PersonRemove, stringRes(Res.string.unfollow)) {
                            note.author?.let { accountViewModel.unfollow(it) }
                            handlers.onDismiss()
                        },
                    )
                }

                // Your own posts: there is no "author" to follow or file into a list.
                add(
                    NoteAction(MaterialSymbols.AutoMirrored.PlaylistAdd, stringRes(Res.string.follow_set_add_author_from_note_action)) {
                        note.author?.pubkeyHex?.let { nav.nav(Route.PeopleListManagement(it)) }
                        handlers.onDismiss()
                    },
                )

                // A drss.io reader follows feeds into the follow sets under their write folder.
                if (hasDrssLists) {
                    add(
                        NoteAction(MaterialSymbols.AutoMirrored.Feed, stringRes(Res.string.drss_menu_follow_into)) {
                            note.author?.pubkeyHex?.let { nav.nav(Route.DrssFollowInto(it)) }
                            handlers.onDismiss()
                        },
                    )
                }
            }
        }

    // When the rendered note was translated, Copy Text opens a chooser (Copy
    // Original / Copy Translated) on top of the menu; the menu dismisses only
    // after the flow resolves so the chooser survives in composition.
    val copyNoteText =
        copyNoteTextAction(
            accountViewModel = accountViewModel,
            onCopied = handlers.onDismiss,
            onDismiss = handlers.onDismiss,
        )

    val copyAndShare =
        buildList {
            add(
                NoteAction(MaterialSymbols.ContentCopy, stringRes(Res.string.copy_text), group = NoteActionGroup.COPY) {
                    copyNoteText(note, noteVersionToCopy)
                },
            )
            add(
                NoteAction(MaterialSymbols.AlternateEmail, stringRes(Res.string.copy_user_pubkey), group = NoteActionGroup.COPY) {
                    note.author?.let {
                        scope.launch(Dispatchers.IO) {
                            clipboardManager.setText("nostr:${it.pubkeyNpub()}")
                            handlers.onDismiss()
                        }
                    }
                },
            )
            add(
                NoteAction(MaterialSymbols.FormatQuote, stringRes(Res.string.copy_note_id), group = NoteActionGroup.COPY) {
                    scope.launch(Dispatchers.IO) {
                        clipboardManager.setText(note.toNostrUri())
                        handlers.onDismiss()
                    }
                },
            )
            add(
                NoteAction(MaterialSymbols.ContentCopy, stringRes(Res.string.copy_raw_json), group = NoteActionGroup.COPY) {
                    val event = note.event
                    if (event != null) {
                        scope.launch {
                            val json = withContext(Dispatchers.Default) { event.toPrettyJson() }
                            clipboardManager.setText(json)
                            handlers.onDismiss()
                        }
                    } else {
                        handlers.onDismiss()
                    }
                },
            )
            // A rumor is unsigned by design, so the inspector would only report a bad signature.
            if (!isPrivateRumor && note.event != null) {
                add(
                    NoteAction(MaterialSymbols.Code, stringRes(Res.string.devtools_inspect_event), group = NoteActionGroup.COPY) {
                        nav.nav(Route.DevToolsEventInspector(note.idHex))
                        handlers.onDismiss()
                    },
                )
            }
            // Highlight this note/article as its source: opens the NIP-84 composer with the
            // nostr source pre-tagged (`a` for an addressable article, else `e`) plus the author,
            // and the passage left for the user to type or paste. Prose kinds only, and never a
            // private rumor (a public highlight would e-tag the unsigned rumor onto relays).
            if (!isPrivateRumor && (note.event is TextNoteEvent || note.event is LongFormContentEvent)) {
                add(
                    NoteAction(MaterialSymbols.FormatQuote, stringRes(Res.string.highlight_action)) {
                        val author = note.author?.pubkeyHex
                        val route =
                            if (note is AddressableNote) {
                                Route.NewHighlight(sourceAddress = note.address.toValue(), author = author)
                            } else {
                                Route.NewHighlight(sourceEventId = note.idHex, author = author)
                            }
                        nav.nav(route)
                        handlers.onDismiss()
                    },
                )
            }
            if (!isPrivateRumor) {
                if (handlers.onShare != null) {
                    add(NoteAction(MaterialSymbols.Share, stringRes(Res.string.quick_action_share), onClick = handlers.onShare))
                } else {
                    addAll(shareNoteActions(note, nav, handlers.onDismiss))
                }
            }
        }

    val editAndBroadcast =
        buildList {
            if (state.isLoggedUser && note.isDraft()) {
                add(NoteAction(MaterialSymbols.Edit, stringRes(Res.string.edit_draft), onClick = handlers.onEditDraft))
            }
            if (!note.isDraft() && !isPrivateRumor) {
                if (note.event is TextNoteEvent) {
                    add(
                        NoteAction(
                            MaterialSymbols.Edit,
                            stringRes(if (state.isLoggedUser) Res.string.edit_post else Res.string.propose_an_edit),
                            onClick = handlers.onEditPost,
                        ),
                    )
                } else if (note.event is LongFormContentEvent && state.isLoggedUser) {
                    add(
                        NoteAction(MaterialSymbols.Edit, stringRes(Res.string.edit_article)) {
                            nav.nav { Route.NewLongFormPost(version = note.idHex) }
                        },
                    )
                }
            }
            // Rumors are rebroadcast as their delivering gift wrap; hidden when the
            // wrap is unknown (the unsigned rumor must never be published).
            if (accountViewModel.canBroadcast(note)) {
                add(
                    NoteAction(MaterialSymbols.CellTower, stringRes(Res.string.broadcast)) {
                        accountViewModel.broadcast(note)
                        handlers.onDismiss()
                    },
                )
            }
        }

    val organize =
        buildList {
            if (!isPrivateRumor) {
                if (accountViewModel.account.otsState.hasPendingAttestations(note)) {
                    add(NoteAction(MaterialSymbols.Schedule, stringRes(Res.string.timestamp_pending)) { handlers.onDismiss() })
                } else {
                    add(
                        NoteAction(MaterialSymbols.Schedule, stringRes(Res.string.timestamp_it)) {
                            accountViewModel.timestamp(note)
                            handlers.onDismiss()
                        },
                    )
                }
            }
            if (state.isLoggedUser && !isPrivateRumor) {
                add(
                    NoteAction(
                        MaterialSymbols.PushPin,
                        stringRes(if (state.isPinnedNote) Res.string.unpin_from_profile else Res.string.pin_to_profile),
                    ) {
                        if (state.isPinnedNote) {
                            accountViewModel.removePin(note)
                        } else {
                            accountViewModel.addPin(note)
                        }
                        handlers.onDismiss()
                    },
                )
            }
            if (!isPrivateRumor) {
                add(NoteAction(MaterialSymbols.Tag, stringRes(Res.string.add_hashtag_label), onClick = handlers.onAddLabel))
            }

            // drss.io saves articles into collections and records the feeds found through them.
            if (hasDrssLists && !isPrivateRumor && note.event is LongFormContentEvent && note is AddressableNote) {
                add(
                    NoteAction(MaterialSymbols.CollectionsBookmark, stringRes(Res.string.drss_menu_save_to)) {
                        nav.nav(Route.DrssSaveTo(note.address.toValue()))
                        handlers.onDismiss()
                    },
                )
                add(
                    NoteAction(MaterialSymbols.TravelExplore, stringRes(Res.string.drss_menu_record_provenance)) {
                        nav.nav(Route.DrssRecordProvenance(note.address.toValue()))
                        handlers.onDismiss()
                    },
                )
            } else if (hasDrssEventLists && !isPrivateRumor && note.event != null) {
                // Anything else goes in the collections drss kept by event id (kind 30102).
                add(
                    NoteAction(MaterialSymbols.CollectionsBookmark, stringRes(Res.string.drss_menu_collect_to)) {
                        nav.nav(Route.DrssCollectTo(note.event?.id ?: note.idHex))
                        handlers.onDismiss()
                    },
                )
            }

            // Pick exactly one curation flow per kind: music tracks go to playlists,
            // emoji packs go to the emoji list, everything else gets the standard
            // bookmark rows. No bookmark/playlist/emoji-list rows for private rumors:
            // those lists reference the note by id, which other devices can't resolve
            // from relays and public lists would leak.
            when {
                isPrivateRumor -> {}

                note.event is MusicTrackEvent && note is AddressableNote -> {
                    add(
                        NoteAction(MaterialSymbols.AutoMirrored.PlaylistAdd, stringRes(Res.string.add_to_music_playlist)) {
                            nav.nav(Route.AddToMusicPlaylist(note.address.toValue()))
                            handlers.onDismiss()
                        },
                    )
                }

                note.event is EmojiPackEvent -> {
                    val emojiText =
                        if (state.isEmojiPackInMyList) {
                            stringRes(Res.string.remove_from_emoji_list)
                        } else {
                            stringRes(Res.string.add_to_emoji_list)
                        }
                    add(
                        NoteAction(MaterialSymbols.EmojiEmotions, emojiText) {
                            val address = (note as AddressableNote).address
                            nav.nav(Route.EmojiPackSelection(kind = EmojiPackEvent.KIND, pubKeyHex = address.pubKeyHex, dTag = address.dTag))
                            handlers.onDismiss()
                        },
                    )
                }

                else -> {
                    val noteBookmarkType = if (note.event is LongFormContentEvent) stringRes(Res.string.article) else stringRes(Res.string.post)
                    add(
                        NoteAction(MaterialSymbols.BookmarkAdd, stringRes(Res.string.manage_bookmark_label, noteBookmarkType), group = NoteActionGroup.BOOKMARK) {
                            if (note.event is LongFormContentEvent) {
                                nav.nav(Route.ArticleBookmarkManagement((note as AddressableNote).address))
                            } else {
                                nav.nav(Route.PostBookmarkManagement(note.idHex))
                            }
                            handlers.onDismiss()
                        },
                    )
                    if (state.isPrivateBookmarkNote) {
                        add(
                            NoteAction(MaterialSymbols.LockOpen, stringRes(Res.string.remove_from_private_bookmarks), group = NoteActionGroup.BOOKMARK) {
                                accountViewModel.removePrivateBookmark(note)
                                handlers.onDismiss()
                            },
                        )
                    } else {
                        add(
                            NoteAction(MaterialSymbols.Lock, stringRes(Res.string.add_to_private_bookmarks), group = NoteActionGroup.BOOKMARK) {
                                accountViewModel.addPrivateBookmark(note)
                                handlers.onDismiss()
                            },
                        )
                    }
                    if (state.isPublicBookmarkNote) {
                        add(
                            NoteAction(MaterialSymbols.BookmarkRemove, stringRes(Res.string.remove_from_public_bookmarks), group = NoteActionGroup.BOOKMARK) {
                                accountViewModel.removePublicBookmark(note)
                                handlers.onDismiss()
                            },
                        )
                    } else {
                        add(
                            NoteAction(MaterialSymbols.Bookmark, stringRes(Res.string.add_to_public_bookmarks), group = NoteActionGroup.BOOKMARK) {
                                accountViewModel.addPublicBookmark(note)
                                handlers.onDismiss()
                            },
                        )
                    }
                }
            }
        }

    val moderation =
        buildList {
            val isThreadMuted = accountViewModel.isThreadMutedFor(note)
            // Every NIP-28 message shares one thread root — the channel — so "Mute thread" on a
            // public chat can only ever hide that channel's ENTIRE content, and invisibly: the
            // Messages row has no such check, so the room still lists while its messages vanish.
            // "Mute notifications" owns silencing a public chat now. Unmute stays reachable so
            // anyone already caught by a legacy mute can escape from the message in front of them.
            if (note.event !is ChannelMessageEvent || isThreadMuted) {
                add(
                    NoteAction(
                        MaterialSymbols.AutoMirrored.VolumeOff,
                        stringRes(if (isThreadMuted) Res.string.quick_action_unmute_thread else Res.string.quick_action_mute_thread),
                    ) {
                        if (isThreadMuted) {
                            accountViewModel.unmuteThread(note)
                        } else {
                            accountViewModel.muteThread(note)
                        }
                        handlers.onDismiss()
                    },
                )
            }

            // Own messages always get a delete affordance (the surface routes private
            // rumors through the gift-wrapped deletion); reporting yourself never
            // makes sense, so Report is others-only.
            if (state.isLoggedUser) {
                add(NoteAction(MaterialSymbols.Delete, stringRes(Res.string.request_deletion), isDestructive = true, onClick = handlers.onDeleteRequest))
            } else {
                add(NoteAction(MaterialSymbols.Report, stringRes(Res.string.block_report), isDestructive = true, onClick = handlers.onReport))
            }

            // Concord channel moderation, gated by this account's authority over the
            // message's author (both return null unless it's a Concord message this
            // account may act on). Promote/demote is instant; a ban re-keys the
            // community, so it defers to the surface's confirmation dialog.
            val concordAdmin = accountViewModel.account.concord.concordAdminTarget(note)
            if (concordAdmin != null) {
                val isAdmin = concordAdmin.third
                add(
                    NoteAction(
                        MaterialSymbols.Shield,
                        stringRes(if (isAdmin) Res.string.concord_remove_admin else Res.string.concord_make_admin),
                    ) {
                        accountViewModel.toggleConcordAdmin(note)
                        handlers.onDismiss()
                    },
                )
            }
            if (handlers.onConcordBan != null && accountViewModel.account.concord.concordBanTarget(note) != null) {
                add(NoteAction(MaterialSymbols.Gavel, stringRes(Res.string.concord_ban_user), isDestructive = true, onClick = handlers.onConcordBan))
            }
            // A room admin may remove any member's message for everyone (a Marmot group sends a
            // kind 4891; receivers check the sender was an admin when it was sent).
            if (handlers.onRemoveForEveryone != null && !state.isLoggedUser && accountViewModel.canRemoveForEveryone(note)) {
                add(
                    NoteAction(
                        MaterialSymbols.Delete,
                        stringRes(Res.string.marmot_admin_delete_message),
                        isDestructive = true,
                        onClick = handlers.onRemoveForEveryone,
                    ),
                )
            }
            // A room admin may dismiss the reports members made on a message, their own included.
            // It neither hides nor deletes the message; the reports just stop counting.
            val toDismiss = accountViewModel.reportsToDismiss(note).size
            if (toDismiss > 0) {
                add(
                    NoteAction(MaterialSymbols.DoneAll, pluralStringRes(Res.plurals.room_dismiss_reports, toDismiss, toDismiss)) {
                        accountViewModel.dismissReports(note)
                        handlers.onDismiss()
                    },
                )
            }
        }

    return listOf(author, copyAndShare, editAndBroadcast, organize, moderation).filter { it.isNotEmpty() }
}

/**
 * Confirmation for an admin's "Delete for everyone": every member's app removes the message,
 * which cannot be undone. Shared by both action surfaces (sheet + 3-dot menu). [onClose]
 * dismisses the dialog; [onRemoved] fires after the removal is sent.
 */
@Composable
fun RemoveForEveryoneConfirmationDialog(
    note: Note,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
    onRemoved: () -> Unit,
) {
    QuickActionAlertDialogOneButton(
        title = stringRes(Res.string.room_remove_for_everyone_title),
        textContent = stringRes(Res.string.room_remove_for_everyone_body),
        buttonIcon = MaterialSymbols.Delete,
        buttonText = stringRes(Res.string.marmot_admin_delete_message),
        buttonColors =
            ButtonDefaults.buttonColors(
                containerColor = LightRedColor,
                contentColor = Color.White,
            ),
        onClickDoOnce = {
            accountViewModel.removeForEveryone(note)
            onRemoved()
        },
        onDismiss = onClose,
    )
}

/**
 * Confirmation for a Concord ban — banning re-keys the community for every remaining
 * member (CORD-06 refounding), so it's a heavier action than a mute and gets its own
 * dialog. Shared by both action surfaces (sheet + 3-dot menu). [onClose] dismisses the
 * dialog; [onBanned] fires after the ban is issued (so the host can also close itself).
 */
@Composable
fun ConcordBanConfirmationDialog(
    note: Note,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
    onBanned: () -> Unit,
) {
    QuickActionAlertDialogOneButton(
        title = stringRes(Res.string.concord_ban_user_title),
        textContent = stringRes(Res.string.concord_ban_user_body),
        buttonIcon = MaterialSymbols.Gavel,
        buttonText = stringRes(Res.string.concord_ban_user),
        buttonColors =
            ButtonDefaults.buttonColors(
                containerColor = LightRedColor,
                contentColor = Color.White,
            ),
        onClickDoOnce = {
            accountViewModel.banConcordMember(note)
            onBanned()
        },
        onDismiss = onClose,
    )
}
