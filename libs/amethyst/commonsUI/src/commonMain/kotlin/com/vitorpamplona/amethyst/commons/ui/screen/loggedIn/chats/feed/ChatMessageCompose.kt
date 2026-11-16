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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.chats.ui.ChatBubbleLayout
import com.vitorpamplona.amethyst.commons.chats.ui.ChatGroupPosition
import com.vitorpamplona.amethyst.commons.chats.ui.jumboEmojiCount
import com.vitorpamplona.amethyst.commons.model.ChatLayoutType
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.privateChats.isSubjectOnlyChatMessage
import com.vitorpamplona.amethyst.commons.notifications.NotificationContent
import com.vitorpamplona.amethyst.commons.ui.components.LocalInlineQuoteRenderer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.RenderZapRaiser
import com.vitorpamplona.amethyst.commons.ui.note.WatchBlockAndReport
import com.vitorpamplona.amethyst.commons.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.DisplayZapSplits
import com.vitorpamplona.amethyst.commons.ui.note.hovercard.UserHoverCardAnchor
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPoll
import com.vitorpamplona.amethyst.commons.ui.note.types.observeChatMessageEdit
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderBuzzActivityRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderBuzzDiff
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderBuzzEditedNote
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderBuzzForumVote
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderBuzzSystemMessage
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderChannelAdminSystemMessage
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderChatClip
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderChatRaid
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderChatSubjectChange
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderChatZap
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderConcordTimerNotice
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderDraftEvent
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderEditedNote
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderEncryptedFile
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderEncryptedMediaV2
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderMarmotEncryptedMedia
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderRegularTextNote
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.hasEncryptedMediaV2
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.hasMip04Media
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.isBuzzActivityRow
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowZapraiser
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.foundation.appEvents.MarmotAppEvent
import com.vitorpamplona.quartz.buzz.forum.ForumVoteEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageDiffEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChatEditEvent
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordTimerNoticeEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip10Notes.BaseNoteEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip53LiveActivities.raid.LiveActivitiesRaidEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.hasZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent

@Composable
fun ChatroomMessageCompose(
    baseNote: Note,
    routeForLastRead: String?,
    innerQuote: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    onScrollToNote: ((Note) -> Unit)? = null,
    shouldHighlight: Boolean = false,
    onHighlightFinished: (() -> Unit)? = null,
    groupPosition: ChatGroupPosition = ChatGroupPosition.SINGLE,
    // The id of the message rendered directly above this one in the feed. A reply
    // quote that targets it is redundant (the reader can see the parent right
    // there), so the reply row is skipped.
    previousNoteId: HexKey? = null,
    // Replaces the generic "post not found" blank while baseNote's event hasn't loaded. Used for
    // reply quotes inside a DM, where the target is simply older than the loaded window (see
    // LoadingReplyNote). Null keeps the default blank for every other caller.
    onBlank: (@Composable () -> Unit)? = null,
    // Edit my own chat message on surfaces that support it (Buzz kind-9 or legacy 40002 → 40003,
    // Concord kind-9 → 1010). Null for chat surfaces without message editing, which hides
    // the action.
    onWantsToEditChatMessage: ((Note) -> Unit)? = null,
) {
    // Re-skin inline `nostr:...` quotes for everything inside this bubble: a quoted
    // chat message renders with the chat reply design instead of the quoted-note card.
    val inlineQuoteRenderer =
        remember(onWantsToReply, onWantsToEditDraft, onScrollToNote) {
            chatInlineQuoteRenderer(onWantsToReply, onWantsToEditDraft, onScrollToNote)
        }

    val onFound: @Composable () -> Unit = {
        WatchBlockAndReport(
            note = baseNote,
            showHiddenWarning = false,
            modifier = Modifier.fillMaxWidth(),
            accountViewModel = accountViewModel,
            nav = nav,
        ) { canPreview ->
            // Advance the room's last-read marker for whatever this row turns out to be. This used
            // to live inside NormalChatNote — the `else` of the branch below — so a row rendered by
            // any of the specialised paths (Buzz system lines and activity rows, diffs, forum votes,
            // NIP-28 admin lines, zaps) never marked itself read. A channel whose newest events are
            // system messages therefore kept its unread badge no matter how often it was opened,
            // which on a Buzz relay is most channels: joins and role changes are system messages.
            if (routeForLastRead != null) {
                LaunchedEffect(key1 = routeForLastRead, key2 = baseNote.idHex) {
                    accountViewModel.loadAndMarkAsRead(routeForLastRead, baseNote.createdAt(), dismissNotificationId = baseNote.idHex)
                }
            }

            val event = baseNote.event
            if (event is ZapReceiptEvent) {
                RenderChatZap(baseNote, accountViewModel, nav)
            } else if (event is LiveActivitiesRaidEvent) {
                RenderChatRaid(baseNote, accountViewModel, nav)
            } else if (event is LiveActivitiesClipEvent) {
                RenderChatClip(baseNote, accountViewModel, nav)
            } else if (event is ChannelCreateEvent || event is ChannelMetadataEvent) {
                RenderChannelAdminSystemMessage(baseNote, accountViewModel, nav)
            } else if (event is SystemMessageEvent) {
                // Buzz kind-40099: relay-signed room narration (join/leave/topic).
                RenderBuzzSystemMessage(baseNote, accountViewModel, nav)
            } else if (event is StreamMessageDiffEvent) {
                // Buzz kind-40008: a code/text diff pushed into the channel.
                RenderBuzzDiff(baseNote)
            } else if (event is ForumVoteEvent) {
                // Buzz kind-45002: a forum up/down vote.
                RenderBuzzForumVote(baseNote, accountViewModel)
            } else if (event is ConcordTimerNoticeEvent) {
                // Concord kind-1740: "Alice set disappearing messages to 30 days" (CORD-08 §4).
                RenderConcordTimerNotice(baseNote, accountViewModel, nav)
            } else if (event?.isSubjectOnlyChatMessage() == true) {
                // NIP-17 rename with no text: a system line under the subject divider, not an empty bubble.
                RenderChatSubjectChange(baseNote, innerQuote, accountViewModel, nav)
            } else if (isBuzzActivityRow(event)) {
                // Buzz agent-job (43xxx) and huddle (48xxx) lifecycle narration. Huddles
                // especially must be caught here — their content is JSON, not chat text.
                RenderBuzzActivityRow(baseNote, accountViewModel)
            } else {
                NormalChatNote(
                    baseNote,
                    innerQuote,
                    canPreview,
                    parentBackgroundColor,
                    accountViewModel,
                    nav,
                    onWantsToReply,
                    onWantsToEditDraft,
                    onScrollToNote,
                    shouldHighlight,
                    onHighlightFinished,
                    groupPosition,
                    previousNoteId,
                    onWantsToEditChatMessage,
                )
            }
        }
    }

    CompositionLocalProvider(LocalInlineQuoteRenderer provides inlineQuoteRenderer) {
        if (onBlank != null) {
            WatchNoteEvent(baseNote = baseNote, onNoteEventFound = onFound, onBlank = onBlank, accountViewModel = accountViewModel)
        } else {
            WatchNoteEvent(baseNote = baseNote, accountViewModel = accountViewModel, nav = nav, onNoteEventFound = onFound)
        }
    }
}

@Composable
fun NormalChatNote(
    note: Note,
    innerQuote: Boolean = false,
    canPreview: Boolean = true,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    onScrollToNote: ((Note) -> Unit)? = null,
    shouldHighlight: Boolean = false,
    onHighlightFinished: (() -> Unit)? = null,
    groupPosition: ChatGroupPosition = ChatGroupPosition.SINGLE,
    previousNoteId: HexKey? = null,
    onWantsToEditChatMessage: ((Note) -> Unit)? = null,
) {
    // A geohash chat renders "as" its anonymous per-cell identity (and the account, when posting as
    // self); LocalChatActingIdentities lets the renderer treat those pubkeys as "me" (alignment,
    // delivery ticks, own-highlight) without swapping the whole AccountViewModel. Null = the account.
    val actingIdentities = LocalChatActingIdentities.current
    val isLoggedInUser =
        remember(note.author, actingIdentities) {
            if (actingIdentities != null) {
                note.author?.pubkeyHex in actingIdentities
            } else {
                accountViewModel.isLoggedUser(note.author)
            }
        }

    // A geohash chat asks own messages to still show the author line (which identity posted), so the
    // usual "hide the name on my own bubbles" shortcut is opt-out there.
    val showSelfAuthorName = LocalChatShowSelfAuthorName.current
    val isOneOnOne = LocalChatIsOneOnOne.current

    val drawAuthorInfo by
        remember(note, isLoggedInUser, showSelfAuthorName, isOneOnOne) {
            derivedStateOf {
                val noteEvent = note.event
                when {
                    // Own messages: normally no name; in a multi-identity chat, show it (unless a DM,
                    // which never draws the user's own author info).
                    isLoggedInUser -> showSelfAuthorName && noteEvent !is EncryptedDmEvent

                    // The screen knows it's just the two of you.
                    isOneOnOne -> false

                    // never shows the user's pictures
                    noteEvent is EncryptedDmEvent -> false

                    // one-on-one, never shows it.
                    // only shows in a group chat.
                    noteEvent is ChatroomKeyable -> noteEvent.chatroomKey(accountViewModel.userProfile().pubkeyHex).users.size > 1

                    else -> true
                }
            }
        }

    // Emoji-only messages render as bare jumbo emoji, no bubble fill. Ciphertext
    // never passes the emoji-only check, so the cheap synchronous read decides
    // most cases; the decrypt callback updates the flag when an encrypted
    // message's plaintext arrives after first composition, keeping the bubble
    // transparency in agreement with the jumbo text rendering downstream.
    var isJumboEmoji by
        remember(note.event?.id) {
            val noteEvent = note.event
            val content =
                if (noteEvent is DraftWrapEvent) {
                    null
                } else {
                    accountViewModel.cachedDecrypt(note) ?: noteEvent?.content
                }
            mutableStateOf(content != null && jumboEmojiCount(content) > 0)
        }

    if (note.event !is DraftWrapEvent) {
        LaunchedEffect(note.event?.id) {
            accountViewModel.decrypt(note) { content ->
                val jumbo = jumboEmojiCount(content) > 0
                if (jumbo != isJumboEmoji) {
                    isJumboEmoji = jumbo
                }
            }
        }
    }

    // The footer shows on the last message of a run (for the time) and on any message
    // carrying per-message metadata (expiration, geohash, PoW, legacy-DM marker). In a geohash
    // room every message repeats the room's own cell, so that geohash is suppressed and doesn't,
    // by itself, force a footer row (see LocalChatSuppressGeohash).
    val suppressGeohash = LocalChatSuppressGeohash.current
    val footerHasMeta = remember(note.event, suppressGeohash) { chatFooterHasMeta(note, suppressGeohash) }

    // Only mount the reaction/zap chip row when the message actually has engagement.
    // The chips overlap the bubble's bottom edge, so the bubble reserves space beneath
    // the last line of text for them — without this gate every footerless bubble would
    // reserve that space for an empty row, leaving a dead gap under the text.
    val hasEngagement = !innerQuote && hasChatEngagement(note, accountViewModel)

    // In the OPEN layout someone else's message is bare text, and a message's own blank lines are
    // as tall as the gap to the next message, so nothing but its time shows where it ends. Every
    // one of their messages carries it, not just the last of the run.
    val timeOnEveryMessage = LocalDisplaySettings.current.chatLayout == ChatLayoutType.OPEN && !isLoggedInUser
    val showTime = groupPosition.isLastOfGroup || timeOnEveryMessage

    ChatBubbleLayout(
        isLoggedInUser = isLoggedInUser,
        isDraft = note.event is DraftWrapEvent,
        innerQuote = innerQuote,
        drawAuthorInfo = drawAuthorInfo && groupPosition.isFirstOfGroup,
        groupPosition = groupPosition,
        transparentBubble = isJumboEmoji,
        parentBackgroundColor = parentBackgroundColor,
        shouldHighlight = shouldHighlight,
        onHighlightFinished = onHighlightFinished,
        onClick = {
            if (innerQuote && onScrollToNote != null) {
                onScrollToNote(note)
                true
            } else {
                false
            }
        },
        onDoubleTap = {
            // Double-tap to send the default reaction; only when it can actually
            // sign one (reactToOrDelete assumes a non-empty choice list).
            if (!note.isDraft() && accountViewModel.isWriteable() && accountViewModel.reactionChoices().isNotEmpty()) {
                accountViewModel.reactToOrDelete(note)
            }
        },
        onSwipeReply =
            if (!innerQuote && !note.isDraft()) {
                { onWantsToReply(note) }
            } else {
                null
            },
        onAuthorClick = {
            note.author?.let {
                nav.nav(routeFor(it))
            }
        },
        actionMenu = { onDismiss ->
            ChatMessageActionSheet(
                note = note,
                onWantsToReply = onWantsToReply,
                onWantsToEditDraft = onWantsToEditDraft,
                onDismiss = onDismiss,
                accountViewModel = accountViewModel,
                nav = nav,
                onWantsToEditChatMessage = onWantsToEditChatMessage,
            )
        },
        reactionsRow =
            if (hasEngagement) {
                { ChatReactionChips(note, accountViewModel, nav) }
            } else {
                null
            },
        footerRow =
            if (!innerQuote && (showTime || footerHasMeta)) {
                {
                    ChatMessageFooter(
                        note = note,
                        isLoggedInUser = isLoggedInUser,
                        showTime = showTime,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            } else {
                null
            },
        drawAuthorLine = {
            DrawAuthorInfo(
                note,
                accountViewModel,
                nav,
            )
        },
        // Group chats put each author's face beside their run; one-on-one chats and my own
        // messages have none (drawAuthorInfo is already false for both).
        authorAvatar =
            if (drawAuthorInfo && !isLoggedInUser && !innerQuote) {
                { size ->
                    note.author?.let { author ->
                        UserHoverCardAnchor(author, accountViewModel, nav) {
                            ChatAuthorAvatar(author.pubkeyHex, accountViewModel, size) { nav.nav(routeFor(author)) }
                        }
                    }
                }
            } else {
                null
            },
    ) { bgColor ->
        MessageBubbleLines(
            note,
            innerQuote,
            bgColor,
            onWantsToReply,
            onWantsToEditDraft,
            canPreview,
            accountViewModel,
            nav,
            onScrollToNote,
            previousNoteId,
        )
    }
}

@Composable
fun LoadAndDisplayClickableZapraiser(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val zapraiserAmount = baseNote.event?.zapraiserAmount() ?: 0
    if (zapraiserAmount > 0) {
        val wantsToSeeReactions = rememberSaveable(baseNote) { mutableStateOf(false) }
        Spacer(StdVertSpacer)
        Box(
            modifier =
                ReactionRowZapraiser.clickable(
                    onClick = { wantsToSeeReactions.value = !wantsToSeeReactions.value },
                ),
            contentAlignment = CenterStart,
        ) {
            RenderZapRaiser(baseNote, zapraiserAmount, wantsToSeeReactions.value, accountViewModel)
        }
    }
}

@Composable
private fun MessageBubbleLines(
    baseNote: Note,
    innerQuote: Boolean,
    bgColor: MutableState<Color>,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    canPreview: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    onScrollToNote: ((Note) -> Unit)? = null,
    previousNoteId: HexKey? = null,
) {
    if (baseNote.event !is DraftWrapEvent) {
        RenderReplyRow(
            note = baseNote,
            innerQuote = innerQuote,
            bgColor = bgColor,
            accountViewModel = accountViewModel,
            nav = nav,
            onWantsToReply = onWantsToReply,
            onWantsToEditDraft = onWantsToEditDraft,
            onScrollToNote = onScrollToNote,
            previousNoteId = previousNoteId,
        )
    }

    NoteRow(
        note = baseNote,
        canPreview = canPreview,
        innerQuote = innerQuote,
        onWantsToReply = onWantsToReply,
        onWantsToEditDraft = onWantsToEditDraft,
        bgColor = bgColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    if (!innerQuote) {
        val noteEvent = baseNote.event
        val zapSplits = remember(noteEvent) { noteEvent?.hasZapSplitSetup() ?: false }
        if (zapSplits && noteEvent != null) {
            Column {
                DisplayZapSplits(noteEvent, false, accountViewModel, nav)

                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        // Zapraiser goal bar (self-hides when the message has no zapraiser). Used to live
        // in the tap-to-expand detail row; now always shown inline under the message.
        LoadAndDisplayClickableZapraiser(baseNote, accountViewModel)
    }
}

/**
 * Id of a note whose reply-to preview should be suppressed on a message row. Set by a
 * thread view that already pins that note at the top (the minichat pins its root), so each
 * reply doesn't redundantly re-render the same parent as an inner quote. Null everywhere else.
 */
val LocalSuppressReplyToNoteId = compositionLocalOf<String?> { null }

/**
 * The pubkeys the chat renderer should treat as "me" instead of the logged-in account — a geohash
 * chat provides its anonymous per-cell identity (plus the account, for "post as self") so own
 * messages align/highlight correctly without a second AccountViewModel. Null everywhere else.
 */
val LocalChatActingIdentities = compositionLocalOf<Set<HexKey>?> { null }

/**
 * How to react in this chat, overriding the account's default react/delete — a geohash chat signs
 * the kind-7 with its per-cell key so a reaction stays anonymous. Null uses the account default.
 */
val LocalChatReactOverride = compositionLocalOf<((Note, String) -> Unit)?> { null }

/**
 * Resolves a per-message display name that overrides the author's profile name — a geohash chat
 * returns the message's Bitchat `n` nickname, since throwaway per-cell keys have no kind-0 profile.
 * Null everywhere else (authors render from their profile as usual).
 */
val LocalChatDisplayNameResolver = compositionLocalOf<((Note) -> String?)?> { null }

/**
 * Whether to draw the author line on the user's OWN (right-aligned) messages. Normally false — in a
 * regular chat your own bubbles carry no name because it's obviously you. A geohash chat sets it true:
 * there you may post under two identities (the anonymous per-cell key or your real account), so the
 * bubble shows which name/nickname each message went out under.
 */
val LocalChatShowSelfAuthorName = compositionLocalOf { false }

/**
 * Whether the conversation is known to be one-on-one even though its messages don't say so.
 * A NIP-17 message carries its participants ([ChatroomKeyable]) and hides the other person's
 * name and picture when there is only one; a Marmot group's inner kind:9 carries nothing, so the
 * screen, which knows the member count, says it here.
 */
val LocalChatIsOneOnOne = compositionLocalOf { false }

/**
 * The geohash of the location room currently open, or null outside one. Every message in that room
 * repeats the room's own cell in its `g` tag, so the bubble footer suppresses this one geohash —
 * showing it on every message is redundant noise. Any other geohash still renders.
 */
val LocalChatSuppressGeohash = compositionLocalOf<String?> { null }

@Composable
fun RenderReplyRow(
    note: Note,
    innerQuote: Boolean,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    onScrollToNote: ((Note) -> Unit)? = null,
    previousNoteId: HexKey? = null,
) {
    val replyTo = note.replyTo?.lastOrNull()
    // Suppress the reply-to preview when it would just repeat a parent the reader can already
    // see: either the message rendered directly above this one in the feed (previousNoteId), or
    // a parent that a thread view has pinned at the top (LocalSuppressReplyToNoteId — the
    // minichat pins its root).
    val suppressId = LocalSuppressReplyToNoteId.current
    if (!innerQuote &&
        replyTo != null &&
        replyTo.idHex != previousNoteId &&
        replyTo.idHex != suppressId &&
        !isCitedInContent(note, replyTo)
    ) {
        RenderReply(note, bgColor, accountViewModel, nav, onWantsToReply, onWantsToEditDraft, onScrollToNote, previousNoteId)
    }
}

/**
 * Whether the reply target is already mentioned (`nostr:...` citation) in the middle of
 * the message text. If it is, the inline quote renderer draws it at that spot, so the
 * reply row would show the same message twice. Happens in MLS kind-9 chats, where the
 * `q`-tagged parent of a quote is also cited inline — `Note.replyTo` keeps both replies
 * and quotes for that kind.
 */
@Composable
private fun isCitedInContent(
    note: Note,
    replyTo: Note,
): Boolean =
    remember(note.event, replyTo) {
        (note.event as? BaseNoteEvent)?.findCitations()?.contains(replyTo.idHex) == true
    }

@Composable
private fun RenderReply(
    note: Note,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    onScrollToNote: ((Note) -> Unit)? = null,
    previousNoteId: HexKey? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        @Suppress("ProduceStateDoesNotAssignValue")
        val replyTo =
            produceState(initialValue = note.replyTo?.lastOrNull()) {
                accountViewModel.unwrapIfNeeded(value) {
                    value = it
                }
            }

        // Unwrapping can map a gift-wrap id to its rumor, so the suppression checks
        // (feed neighbour + thread-pinned root) have to be re-run on the resolved note.
        val suppressId = LocalSuppressReplyToNoteId.current
        replyTo.value?.takeIf { it.idHex != previousNoteId && it.idHex != suppressId }?.let { replyNote ->
            // For a DM, a reply target that hasn't arrived isn't lost — it's older than the loaded
            // window (and for gift wraps can't be fetched by id). Swap the generic blank for one that
            // walks history backward until it surfaces. Pick the pager by the PARENT's protocol; leave
            // public chats / marmot groups (not a DM event here) on the default blank.
            val replyBlank: (@Composable () -> Unit)? =
                when (note.event) {
                    is ChatMessageEvent, is ChatMessageEncryptedFileHeaderEvent -> {
                        { LoadingReplyNote(DmReplyProtocol.NIP17, accountViewModel) }
                    }
                    is EncryptedDmEvent -> {
                        { LoadingReplyNote(DmReplyProtocol.NIP04, accountViewModel) }
                    }
                    else -> null
                }
            ChatroomMessageCompose(
                baseNote = replyNote,
                routeForLastRead = null,
                innerQuote = true,
                parentBackgroundColor = bgColor,
                accountViewModel = accountViewModel,
                nav = nav,
                onWantsToReply = onWantsToReply,
                onWantsToEditDraft = onWantsToEditDraft,
                onScrollToNote = onScrollToNote,
                onBlank = replyBlank,
            )
        }
    }
}

@Composable
fun NoteRow(
    note: Note,
    canPreview: Boolean,
    innerQuote: Boolean,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            note.event is DraftWrapEvent -> RenderDraftEvent(note, canPreview, innerQuote, onWantsToReply, onWantsToEditDraft, bgColor, accountViewModel, nav)
            note.event is ChatMessageEncryptedFileHeaderEvent -> RenderEncryptedFile(note, bgColor, accountViewModel, nav)
            hasMip04Media(note.event) -> RenderMarmotEncryptedMedia(note, bgColor, accountViewModel, nav)
            // `encrypted-media-v2` is a separate branch because its imeta has
            // no `url` field for the NIP-92 parser above to anchor on.
            hasEncryptedMediaV2(note.event) ->
                Column {
                    RenderEncryptedMediaV2(note, bgColor, accountViewModel, nav)
                    // The content of a media message is its caption (MDK and our own sender
                    // both put it there), and it may carry custom emoji of its own.
                    if (note.event?.content?.isNotBlank() == true) {
                        RenderRegularTextNote(note, canPreview, innerQuote, bgColor, accountViewModel, nav)
                    }
                }
            // A poll renders like a poll anywhere else; its room, when it has one, carries the votes.
            // Quoted in a reply it is a preview, not a ballot.
            note.event is PollEvent -> RenderPoll(note, innerQuote, canPreview, if (innerQuote) 0 else 1, bgColor, accountViewModel, nav)
            else -> {
                // Concord, Buzz and Marmot all overlay edits on their messages (kinds 3302,
                // 40003 and 1009): when one exists, render the winning edit's content instead of
                // the stale original. One observer for all three — a message is only ever one
                // kind, so a single edits-flow collector per row covers them (and is null for
                // other surfaces).
                val edit = observeChatEdit(note)
                val editEvent = edit?.event
                when {
                    editEvent is ConcordChatEditEvent -> RenderEditedNote(note, edit, canPreview, innerQuote, bgColor, accountViewModel, nav)
                    editEvent is StreamMessageEditEvent -> RenderBuzzEditedNote(note, edit, canPreview, innerQuote, bgColor, accountViewModel, nav)
                    // A Marmot edit is a plain inner app event, not a typed
                    // class, so it is matched on its kind rather than its type.
                    editEvent != null && editEvent.kind == MarmotAppEvent.KIND_EDIT ->
                        RenderEditedNote(note, edit, canPreview, innerQuote, bgColor, accountViewModel, nav)
                    else -> RenderRegularTextNote(note, canPreview, innerQuote, bgColor, accountViewModel, nav)
                }
            }
        }
    }
}

/**
 * The text a one-line chat preview (room list, workspace rows) shows for [note]: the latest edit's
 * text when there is one — the room shows the edit, so the list must too — with `nostr:` user
 * references rewritten to `@Name`, as the bubble renders them.
 */
@Composable
fun observeChatPreviewText(
    note: Note,
    max: Int = 200,
): String {
    val content = observeChatEdit(note)?.event?.content ?: note.event?.content ?: ""
    return remember(content, max) { NotificationContent.resolveMentions(content, max).text }
}

/**
 * The newest edit overlaying a chat message [note] (Concord, Buzz or Marmot), or null when unedited,
 * recomposing whenever an edit is added or removed.
 */
@Composable
fun observeChatEdit(note: Note): Note? = observeChatMessageEdit(note)
