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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.ui.CHAT_GROUP_WINDOW_SECONDS
import com.vitorpamplona.amethyst.commons.chats.ui.ChatBubbleLayout
import com.vitorpamplona.amethyst.commons.chats.ui.ChatDivisor
import com.vitorpamplona.amethyst.commons.chats.ui.ChatGroupPosition
import com.vitorpamplona.amethyst.commons.chats.ui.chatJumboEmojiAllowed
import com.vitorpamplona.amethyst.commons.chats.ui.jumboEmojiCount
import com.vitorpamplona.amethyst.commons.chats.ui.jumboEmojiFontSize
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.ChatLayoutType
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copy_text
import com.vitorpamplona.amethyst.commons.resources.cordn_action_delete
import com.vitorpamplona.amethyst.commons.resources.cordn_action_edit
import com.vitorpamplona.amethyst.commons.resources.cordn_action_go_to_message
import com.vitorpamplona.amethyst.commons.resources.cordn_action_pin
import com.vitorpamplona.amethyst.commons.resources.cordn_action_reply
import com.vitorpamplona.amethyst.commons.resources.cordn_action_unpin
import com.vitorpamplona.amethyst.commons.resources.cordn_chat_unread_divider
import com.vitorpamplona.amethyst.commons.resources.cordn_delete_confirm_body
import com.vitorpamplona.amethyst.commons.resources.cordn_delete_confirm_title
import com.vitorpamplona.amethyst.commons.resources.cordn_delivery_accepted
import com.vitorpamplona.amethyst.commons.resources.cordn_message_deleted
import com.vitorpamplona.amethyst.commons.resources.cordn_message_details_coordinator
import com.vitorpamplona.amethyst.commons.resources.cordn_message_details_cursor
import com.vitorpamplona.amethyst.commons.resources.cordn_message_details_sent_at
import com.vitorpamplona.amethyst.commons.resources.cordn_message_details_title
import com.vitorpamplona.amethyst.commons.resources.cordn_message_edited
import com.vitorpamplona.amethyst.commons.resources.cordn_reactions_title
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.resources.today
import com.vitorpamplona.amethyst.commons.resources.yesterday
import com.vitorpamplona.amethyst.commons.ui.components.ClickableBox
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.QuickActionAlertDialog
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.chatDayLabelWith
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ActionTile
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatAuthorAvatar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatAuthorNameSize
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatAuthorNameWeight
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatChipFlowRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatTimestamp
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.MoreActionsToggle
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ReactionChip
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ReactionChipView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.SectionDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.TileRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.authorNameColorFor
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.coordinatorDisplayName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font12SP
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.cordn.appEncryptedMedia.CordnMediaTag
import com.vitorpamplona.cordn.client.CordnMentions
import com.vitorpamplona.cordn.spec02Envelopes.CordnAnnotationIndex
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageReferences
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * A cordn message, drawn as an Amethyst chat bubble.
 *
 * Everything structural here is the app's shared chat furniture — [ChatBubbleLayout]
 * for the bubble, its grouping shapes and its whole gesture vocabulary (long-press for
 * the action sheet, double-tap to react, swipe toward the centre to reply),
 * [ReactionChipView] for the engagement strip, [ActionTile] for the sheet. A cordn room
 * used to draw its own flat rows, which meant the one chat in the app where a tap
 * opened a menu and nothing could be swiped.
 *
 * What cordn cannot share is the *content* of those slots. The shared fillings
 * ([com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatReactionChips],
 * `ChatMessageActionSheet`, `ChatMessageFooter`, `DrawAuthorInfo`) all take a `Note`,
 * and a `Note` comes from `LocalCache` — which nothing in a cordn room may enter, see
 * the screen's KDoc. So the slots are filled from the envelope and the annotation fold
 * instead, and the layout above them is the same one every other chat uses.
 */
@Composable
fun CordnMessageRow(
    message: CordnDeliveredMessage,
    room: CordnGroupChatroom,
    annotations: CordnAnnotationIndex,
    me: HexKey,
    groupPosition: ChatGroupPosition,
    text: String?,
    isEdited: Boolean,
    shouldHighlight: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    onHighlightFinished: () -> Unit,
    onScrollToMessage: (HexKey) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onReact: (String) -> Unit,
) {
    val isMine = message.envelope.pubKey == me
    val isPinned = annotations.isPinned(message.envelope.id)
    val reactions = annotations.reactions[message.envelope.id].orEmpty()

    // A deleted message keeps its place in the conversation but stops accepting
    // annotations: replying to, editing or reacting to a withdrawal is meaningless,
    // and the manager would refuse most of it anyway.
    val isLive = text != null

    // A mention is the one reason to pick a message out of a wall of them. Read from
    // the content rather than from a `p` tag: a tag is a claim the sender makes about
    // who they addressed, while the text is what everyone in the room actually sees.
    val mentionsMe = remember(text, me) { text != null && me in CordnMentions.mentioned(text) }
    // The compact layout keeps emoji inline, as RenderRegularTextNote does for every other chat:
    // a 48sp emoji tripled the height of a one-line-per-message layout. Decided here, before
    // ChatBubbleLayout draws the row, so it reads the setting rather than the layout's local.
    val jumboAllowed = chatJumboEmojiAllowed()
    val jumboCount = remember(text, jumboAllowed) { if (text != null && jumboAllowed) jumboEmojiCount(text) else 0 }

    val mentionTint = MaterialTheme.colorScheme.primary.copy(alpha = MENTION_TINT_ALPHA)

    // As in NormalChatNote: in the OPEN layout someone else's message is bare text, and only its
    // time shows where it ends, so each of their messages carries one.
    val showTime = groupPosition.isLastOfGroup || (LocalDisplaySettings.current.chatLayout == ChatLayoutType.OPEN && !isMine)

    Box(if (mentionsMe) Modifier.fillMaxWidth().background(mentionTint) else Modifier.fillMaxWidth()) {
        ChatBubbleLayout(
            isLoggedInUser = isMine,
            isDraft = false,
            innerQuote = false,
            // Your own bubbles are right-aligned and tinted, so naming yourself over
            // every burst of them is noise. Everyone else is named once per burst.
            drawAuthorInfo = groupPosition.isFirstOfGroup && !isMine,
            groupPosition = groupPosition,
            transparentBubble = jumboCount > 0,
            shouldHighlight = shouldHighlight,
            onHighlightFinished = onHighlightFinished,
            // A plain tap is a no-op, exactly as in every other Amethyst chat.
            onClick = { false },
            // The account's own first choice, as `reactToOrDelete` uses in every other
            // chat — a hardcoded thumb ignored the palette the user configured, and
            // disagreed with cordn's own action sheet, which already reads it.
            onDoubleTap =
                if (isLive) {
                    { onReact(accountViewModel.reactionChoices().firstOrNull() ?: DEFAULT_REACTION) }
                } else {
                    null
                },
            onSwipeReply =
                if (isLive) {
                    onReply
                } else {
                    null
                },
            onAuthorClick = { nav.nav(Route.Profile(message.envelope.pubKey)) },
            actionMenu = { onDismiss ->
                CordnMessageActionSheet(
                    body = text.orEmpty(),
                    isMine = isMine,
                    isPinned = isPinned,
                    isLive = isLive,
                    accountViewModel = accountViewModel,
                    onDismiss = onDismiss,
                    onReply = onReply,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onTogglePin = onTogglePin,
                    onReact = onReact,
                )
            },
            reactionsRow =
                if (reactions.isEmpty()) {
                    null
                } else {
                    { CordnReactionChips(reactions, me, accountViewModel, nav, onReact) }
                },
            // Mirrors chatFooterHasMeta: the footer earns its row on the last message
            // of a burst (for the time) or on any message carrying a marker of its own.
            footerRow =
                if (showTime || isEdited || isPinned) {
                    {
                        CordnMessageFooter(
                            message = message,
                            isMine = isMine,
                            coordinatorPubKey = room.coordinatorPubKey,
                            accountViewModel = accountViewModel,
                            isEdited = isEdited,
                            isPinned = isPinned,
                            showTime = showTime,
                        )
                    }
                } else {
                    null
                },
            drawAuthorLine = { CordnAuthorLine(message.envelope.pubKey, accountViewModel, nav) },
            // Everyone else's face sits beside their run, as in every other group chat.
            authorAvatar =
                if (isMine) {
                    null
                } else {
                    { size -> ChatAuthorAvatar(message.envelope.pubKey, accountViewModel, size) { nav.nav(Route.Profile(message.envelope.pubKey)) } }
                },
        ) { bgColor ->
            CordnBubbleContents(
                message = message,
                room = room,
                annotations = annotations,
                me = me,
                text = text,
                jumboCount = jumboCount,
                bubbleColor = bgColor,
                accountViewModel = accountViewModel,
                nav = nav,
                onScrollToMessage = onScrollToMessage,
            )
        }
    }
}

/** What a double-tap sends, and the fallback when the account lists no reaction choices. */
private const val DEFAULT_REACTION = "👍"

/** How strongly a message that mentions you tints its row. */
private const val MENTION_TINT_ALPHA = 0.10f

@Composable
private fun CordnBubbleContents(
    message: CordnDeliveredMessage,
    room: CordnGroupChatroom,
    annotations: CordnAnnotationIndex,
    me: HexKey,
    text: String?,
    jumboCount: Int,
    bubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onScrollToMessage: (HexKey) -> Unit,
) {
    val thread = remember(message.envelope.id) { CordnMessageReferences.thread(message.envelope.tags) }
    val parent = thread?.let { annotations.byId[it.parentId] }
    if (parent != null) {
        CordnQuotedMessage(
            parent = parent,
            annotations = annotations,
            me = me,
            accountViewModel = accountViewModel,
            nav = nav,
            parentBackgroundColor = bubbleColor,
            onClick = { onScrollToMessage(parent.envelope.id) },
        )
    }

    when {
        text == null ->
            Text(
                text = stringRes(Res.string.cordn_message_deleted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

        // Emoji-only messages render bare and large, over a transparent bubble.
        jumboCount > 0 -> Text(text = text.trim(), fontSize = jumboEmojiFontSize(jumboCount))

        // The app's own message renderer, which is what every other chat's bubble
        // reaches through RenderRegularTextNote. Cordn hand-rolled plain Text plus a
        // row of mention spans, so a link, an image URL, a hashtag, a `nostr:` entity
        // and — since the composer started inserting them — a custom emoji's URL all
        // arrived as raw text. Nothing here needed a Note: the viewer takes a String,
        // the bubble's colour and an id.
        //
        // `tags` is EmptyTagList deliberately. A cordn envelope's tags are cordn's own
        // (§4 media, references), not NIP-92/NIP-30, so handing them over would ask the
        // viewer to read them as something they are not.
        //
        // canPreview = true is what an unblocked note gets in a DM, which is equally
        // end-to-end encrypted. It does not force previews: whether one is fetched is
        // still `automaticallyShowUrlPreview`, the account's own Tor-aware setting.
        else ->
            TranslatableRichTextViewer(
                content = text,
                canPreview = true,
                quotesLeft = 1,
                modifier = Modifier,
                tags = EmptyTagList,
                backgroundColor = bubbleColor,
                id = message.envelope.id,
                authorPubKey = message.envelope.pubKey,
                accountViewModel = accountViewModel,
                nav = nav,
            )
    }

    // Only on a live message: a deleted one must not keep offering its attachment,
    // and the blob is still on the host either way.
    if (text != null) {
        CordnMediaTag.parseAll(message.envelope.tags).forEach { attachment ->
            CordnAttachment(attachment, room, accountViewModel)
        }
    }
}

/**
 * Name on the first bubble of a burst, styled like a DM sender's, colour included — the
 * face sits beside the burst ([ChatAuthorAvatar]). [authorNameColorFor] derives a stable
 * hue from the pubkey, which is what makes authors scannable in a fast-moving room.
 *
 * [observeUserNameByHex] falls back to a hex prefix until the profile arrives. Looking a
 * sender up for a display name is safe: a profile is public relay data the cache already
 * holds, and reading one puts no part of this conversation into it.
 */
@Composable
private fun CordnAuthorLine(
    pubKey: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val name = observeUserNameByHex(pubKey, accountViewModel)
    val isLightTheme = MaterialTheme.colorScheme.isLight
    val nameColor = remember(pubKey, isLightTheme) { authorNameColorFor(pubKey, isLightTheme) }

    Text(
        text = name,
        color = nameColor,
        fontWeight = ChatAuthorNameWeight,
        fontSize = ChatAuthorNameSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The engagement strip riding the bubble's bottom border.
 *
 * A reaction is a set of pubkeys per emoji in the fold, so the count is the set size — a
 * member who reacted twice with the same emoji counts once, which is what the index
 * already guarantees and what a naive message count would get wrong on a re-sync.
 */
@Composable
private fun CordnReactionChips(
    reactions: Map<String, Set<HexKey>>,
    me: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    onReact: (String) -> Unit,
) {
    val chips =
        remember(reactions, me) {
            reactions
                .map { (emoji, who) -> ReactionChip(emoji, who.size, me in who) }
                .sortedByDescending { it.count }
        }

    var showWho by remember { mutableStateOf(false) }

    if (showWho) {
        CordnReactionDetailSheet(
            reactions = reactions,
            accountViewModel = accountViewModel,
            nav = nav,
            onDismiss = { showWho = false },
        )
    }

    ChatChipFlowRow {
        chips.forEach { chip ->
            ReactionChipView(
                chip = chip,
                onClick = { onReact(chip.type) },
                // Long press opens who reacted, as the DM strip does.
                onLongClick = { showWho = true },
            )
        }
    }
}

/**
 * Who reacted, and with what.
 *
 * The fold keys reactions by emoji to a *set* of senders, so this is the whole truth
 * the room holds about them — there is no separate receipt to open, and no count that
 * could disagree with the list under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CordnReactionDetailSheet(
    reactions: Map<String, Set<HexKey>>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    // Most-reacted first, matching the order of the chips that opened this.
    val groups = remember(reactions) { reactions.entries.sortedByDescending { it.value.size } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                text = stringRes(Res.string.cordn_reactions_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            groups.forEach { (emoji, who) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = who.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                who.forEach { pubKey ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 36.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UserPicture(
                            userHex = pubKey,
                            size = Size20dp,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                        Text(
                            text = observeUserNameByHex(pubKey, accountViewModel),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The message a reply is answering, drawn above it as a nested bubble.
 *
 * The same [ChatBubbleLayout] in `innerQuote` mode that every other chat uses for a
 * quote, so what a reply answers reads identically in a cordn room and a DM: the
 * quoted author's own bubble shape and tint, their name in their colour, and a tap
 * that takes you to the message. Cordn drew a bespoke accent-bar card here, which was
 * the one place in the app where a quote did not look like a quote.
 *
 * [parentBackgroundColor] is the enclosing bubble's fill. The layout composites the
 * nested bubble over it, which is what keeps a quote legible inside both a sent and a
 * received bubble instead of being tinted against the screen background.
 */
@Composable
fun CordnQuotedMessage(
    parent: CordnDeliveredMessage,
    annotations: CordnAnnotationIndex,
    me: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    parentBackgroundColor: MutableState<Color>? = null,
    onClick: (() -> Unit)? = null,
) {
    val isMine = parent.envelope.pubKey == me

    val body =
        if (annotations.isDeleted(parent.envelope.id)) {
            stringRes(Res.string.cordn_message_deleted)
        } else {
            annotations.contentOf(parent.envelope.id).orEmpty()
        }

    ChatBubbleLayout(
        isLoggedInUser = isMine,
        isDraft = false,
        innerQuote = true,
        // Same rule as the bubbles: your own quoted message needs no name on it.
        drawAuthorInfo = !isMine,
        parentBackgroundColor = parentBackgroundColor,
        // Returning true marks the tap as handled, which is how the shared layout
        // distinguishes a quote (goes somewhere) from a bubble (a tap does nothing).
        onClick = {
            onClick?.invoke()
            onClick != null
        },
        onAuthorClick = { nav.nav(Route.Profile(parent.envelope.pubKey)) },
        // The shared layout does not gate long-press on innerQuote, so a quote that
        // offered no menu would swallow the gesture. Only what makes sense on a
        // preview: where it came from, and its text.
        actionMenu = { onDismiss ->
            CordnQuoteActionSheet(
                body = body,
                onDismiss = onDismiss,
                onGoToMessage = onClick,
            )
        },
        drawAuthorLine = { CordnAuthorLine(parent.envelope.pubKey, accountViewModel, nav) },
    ) { _ ->
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Long-press on a quote: go to what it quotes, or take its text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CordnQuoteActionSheet(
    body: String,
    onDismiss: () -> Unit,
    onGoToMessage: (() -> Unit)?,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            TileRow {
                if (onGoToMessage != null) {
                    ActionTile(MaterialSymbols.ArrowUpward, stringRes(Res.string.cordn_action_go_to_message)) {
                        onGoToMessage()
                        onDismiss()
                    }
                }
                CopyTextTile(body, onDismiss)
            }
        }
    }
}

/**
 * The line where the messages you have already read end.
 *
 * Drawn from a cursor snapshotted when the room opened: `markRead()` runs on open, so a
 * divider read from the live cursor would vanish the moment it became useful.
 */
@Composable
fun UnreadDivider() {
    ChatDivisor(stringRes(Res.string.cordn_chat_unread_divider), MaterialTheme.colorScheme.primary)
}

/**
 * The bubble's bottom-corner footer: the markers this message carries, then the time on
 * the last bubble of a burst. Until this existed a cordn room showed no per-message
 * time at all — only the day separator — so nothing said when anything was said.
 */
@Composable
private fun CordnMessageFooter(
    message: CordnDeliveredMessage,
    isMine: Boolean,
    coordinatorPubKey: HexKey,
    accountViewModel: AccountViewModel,
    isEdited: Boolean,
    isPinned: Boolean,
    showTime: Boolean,
) {
    var showDetails by remember(message.envelope.id) { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isPinned) {
            Icon(
                symbol = MaterialSymbols.PushPin,
                contentDescription = stringRes(Res.string.cordn_action_pin),
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(StdHorzSpacer)
        }

        if (isEdited) {
            Text(
                text = stringRes(Res.string.cordn_message_edited),
                fontSize = Font12SP,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
            )
            Spacer(StdHorzSpacer)
        }

        if (showTime) {
            ClickableBox(onClick = { showDetails = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The shared chat stamp, as every other chat: the time of day under
                    // the room's pinned day (pill or docked bar), the day spelled out
                    // in the open layout. It was relative ("3d"), which under a pill
                    // reading "Nov 2" said the same thing twice in two ways. The tap
                    // belongs to the details sheet, as it does in the shared footer.
                    ChatTimestamp(message.envelope.createdAt)

                    // Anything of yours that is in this list was accepted by the
                    // coordinator: `post` returns the cursor it was filed under, and
                    // nothing enters the room until it does. So this says what the
                    // shared ticks say, for the one hop cordn has.
                    if (isMine) {
                        Spacer(StdHorzSpacer)
                        Icon(
                            symbol = MaterialSymbols.Done,
                            contentDescription = stringRes(Res.string.cordn_delivery_accepted),
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.allGoodColor,
                        )
                    }
                }
            }
        }
    }

    if (showDetails) {
        CordnMessageDetailsSheet(
            message = message,
            isMine = isMine,
            coordinatorPubKey = coordinatorPubKey,
            accountViewModel = accountViewModel,
            onDismiss = { showDetails = false },
        )
    }
}

/**
 * Where a message came from and when, behind a tap on its timestamp.
 *
 * The shared footer puts relay acceptance here. Cordn has one hop instead of a relay
 * set — the coordinator that filed it — and the cursor it was filed under, which is the
 * only ordering the room has and the thing to quote when a message looks out of place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CordnMessageDetailsSheet(
    message: CordnDeliveredMessage,
    isMine: Boolean,
    coordinatorPubKey: HexKey,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val sentAt =
        remember(message.envelope.createdAt) {
            ZonedDateTime
                .ofInstant(Instant.ofEpochSecond(message.envelope.createdAt), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringRes(Res.string.cordn_message_details_title),
                style = MaterialTheme.typography.titleSmall,
            )
            DetailLine(stringRes(Res.string.cordn_message_details_sent_at), sentAt)
            DetailLine(stringRes(Res.string.cordn_message_details_cursor), message.cursor.toString())
            DetailLine(
                stringRes(Res.string.cordn_message_details_coordinator),
                coordinatorDisplayName(coordinatorPubKey, label = null, accountViewModel = accountViewModel),
            )
            if (isMine) {
                Text(
                    text = stringRes(Res.string.cordn_delivery_accepted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.allGoodColor,
                )
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Copies [text] and says so, the way every other copy affordance in the app does. */
@Composable
private fun CopyTextTile(
    text: String,
    onDismiss: () -> Unit,
) {
    val shortNotice = rememberShortNotice()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringRes(Res.string.copied_to_clipboard)

    ActionTile(MaterialSymbols.ContentCopy, stringRes(Res.string.copy_text)) {
        scope.launch {
            clipboard.setText(text)
            shortNotice.show(copied)
        }
        onDismiss()
    }
}

/**
 * The long-press surface, built from the same [ActionTile]s as the DM sheet.
 *
 * It opens with the account's own reaction palette, because the strip below a bubble now
 * appears only once a message *has* a reaction — without the palette, double-tap would
 * be the only way to leave the first one, and an undiscoverable gesture is not an
 * affordance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CordnMessageActionSheet(
    body: String,
    isMine: Boolean,
    isPinned: Boolean,
    isLive: Boolean,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onReact: (String) -> Unit,
) {
    var showAllActions by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // Same gate the shared sheet uses, and the same setting, so someone who has
    // already said "don't ask again" is not asked again here either.
    val performDelete = {
        onDelete()
        onDismiss()
    }

    if (confirmingDelete) {
        QuickActionAlertDialog(
            title = stringRes(Res.string.cordn_delete_confirm_title),
            textContent = stringRes(Res.string.cordn_delete_confirm_body),
            buttonIcon = MaterialSymbols.Delete,
            buttonText = stringRes(Res.string.cordn_action_delete),
            onClickDoOnce = performDelete,
            onClickDontShowAgain = {
                accountViewModel.account.settings.setHideDeleteRequestDialog()
                performDelete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isLive) {
                val choices by accountViewModel.reactionChoicesFlow().collectAsStateWithLifecycle()
                val palette = choices.ifEmpty { listOf(DEFAULT_REACTION) }

                // Never drawn as "mine": cordn has no un-react, so this row adds a
                // reaction rather than toggling one, and highlighting a choice you
                // already used would promise a second tap that takes it back.
                ChatChipFlowRow {
                    palette.forEach { emoji ->
                        ReactionChipView(
                            chip = ReactionChip(emoji, 1, false),
                            onClick = {
                                onReact(emoji)
                                onDismiss()
                            },
                            onLongClick = {},
                        )
                    }
                }

                SectionDivider()
            }

            TileRow {
                if (isLive) {
                    ActionTile(MaterialSymbols.AutoMirrored.Chat, stringRes(Res.string.cordn_action_reply)) {
                        onReply()
                        onDismiss()
                    }
                }

                // Pinning is any member's (spec/01.md §5.1), so it is offered on every
                // message rather than only on your own.
                ActionTile(
                    MaterialSymbols.PushPin,
                    stringRes(if (isPinned) Res.string.cordn_action_unpin else Res.string.cordn_action_pin),
                ) {
                    onTogglePin()
                    onDismiss()
                }

                // Edit and delete are author-only, and the manager refuses them for
                // anyone else. Hiding them here is the same rule, stated where it stops
                // being a surprise.
                if (isMine && isLive) {
                    ActionTile(MaterialSymbols.Edit, stringRes(Res.string.cordn_action_edit)) {
                        onEdit()
                        onDismiss()
                    }
                    ActionTile(MaterialSymbols.Delete, stringRes(Res.string.cordn_action_delete), isDestructive = true) {
                        // A withdrawal cannot be taken back and the coordinator keeps
                        // the ciphertext either way, so it is worth one question —
                        // unless the account has already opted out of being asked.
                        if (accountViewModel.account.settings.hideDeleteRequestDialog) {
                            performDelete()
                        } else {
                            confirmingDelete = true
                        }
                    }
                }
            }

            // Stage two, exactly as the shared sheet splits it: the actions native to
            // this chat sit up front, and the generic what-you-can-do-with-any-message
            // inventory lives behind the toggle so the sheet opens compact.
            SectionDivider()
            MoreActionsToggle(expanded = showAllActions, onToggle = { showAllActions = !showAllActions })

            if (showAllActions) {
                SectionDivider()
                TileRow {
                    CopyTextTile(body, onDismiss)
                    ShareTextTile(body, onDismiss)
                }
            }
        }
    }
}

/**
 * Hands the message text to the system share sheet.
 *
 * The shared sheet shares a note by its nostr address, which a cordn message does not
 * have — it lives on a coordinator, not a relay, and there is no URI that would resolve
 * for anyone else. So this shares the text itself, which is what the reader can
 * actually pass on.
 */
@Composable
private fun ShareTextTile(
    text: String,
    onDismiss: () -> Unit,
) {
    val sharer = rememberTextSharer()
    val shareLabel = stringRes(Res.string.quick_action_share)

    ActionTile(MaterialSymbols.Share, shareLabel) {
        sharer.share(text, title = null, chooserTitle = shareLabel)
        onDismiss()
    }
}

/**
 * Where [message] sits inside a run of consecutive bubbles by the same sender, in the
 * shared [ChatGroupPosition] vocabulary — so a cordn burst gets the same squared-off
 * corners and tightened spacing as a DM burst, not just a hidden author line.
 *
 * The feed is reverse-laid-out: [newer] is the message rendered below, [older] above.
 */
fun cordnGroupPositionFor(
    newer: CordnDeliveredMessage?,
    message: CordnDeliveredMessage,
    older: CordnDeliveredMessage?,
): ChatGroupPosition {
    val connectedAbove = older != null && groupsWith(message, older)
    val connectedBelow = newer != null && groupsWith(newer, message)

    return when {
        connectedAbove && connectedBelow -> ChatGroupPosition.MIDDLE
        connectedAbove -> ChatGroupPosition.BOTTOM
        connectedBelow -> ChatGroupPosition.TOP
        else -> ChatGroupPosition.SINGLE
    }
}

/**
 * Whether [newer] continues the run [older] started — same sender, close in time, same
 * day. Time as well as sender, because a reply hours later to your own last message is a
 * new thought, and joining it to the run reads as though the conversation never paused.
 *
 * The window is the shared [CHAT_GROUP_WINDOW_SECONDS], so a cordn burst and a DM burst
 * break in the same place.
 */
private fun groupsWith(
    newer: CordnDeliveredMessage,
    older: CordnDeliveredMessage,
): Boolean {
    if (newer.envelope.pubKey != older.envelope.pubKey) return false
    if (abs(newer.envelope.createdAt - older.envelope.createdAt) > CHAT_GROUP_WINDOW_SECONDS) return false
    // A day separator between the two breaks the run, exactly as a date divisor does in
    // the DM feed.
    return newer.sameDayAs(older)
}

/** Whether both fall on the same local calendar day. A null [older] is a new day. */
fun CordnDeliveredMessage.sameDayAs(older: CordnDeliveredMessage?): Boolean {
    if (older == null) return false
    return localDayOf(envelope.createdAt) == localDayOf(older.envelope.createdAt)
}

private fun localDayOf(epochSeconds: Long): LocalDate = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * The day a run of messages belongs to.
 *
 * Without one, a conversation is an undivided column and "yesterday evening" and "this
 * morning" sit flush against each other.
 */
@Composable
fun DaySeparator(
    createdAt: Long,
    today: LocalDate,
) {
    // The labels every other chat's dividers use ("Today", "Yesterday", "Aug 20", "Jan 5, 2024",
    // in the locale's own order), so this divider and the pinned day pill or bar above the room
    // always read alike. The year is left out when it is this one. [today] only keys the label:
    // it changes at midnight and relabels "Today" as "Yesterday".
    val todayLabel = stringRes(Res.string.today)
    val yesterdayLabel = stringRes(Res.string.yesterday)
    val label = remember(createdAt, today, todayLabel, yesterdayLabel) { chatDayLabelWith(createdAt, TimeUtils.nowMillis(), todayLabel, yesterdayLabel) }

    ChatDivisor(label)
}

/** Upper bound on how long a stale "Today" can survive a clock correction. */
private const val TODAY_POLL_MS = 60_000L

/**
 * Today, as a value that stops being today when it stops being today.
 *
 * [DaySeparator] used to hold `remember { LocalDate.now() }` of its own. That is a
 * snapshot of the wall clock with nothing to invalidate it, and each separator keeps a
 * separate one, so they can disagree: a separator composed before midnight goes on
 * saying "Today" while the one for the new day says it too. Seen on the tablet — one
 * room, two "Today" dividers.
 *
 * Polling rather than a single sleep to the next midnight, because a device clock does
 * not only advance: it is corrected, and the tablet this was found on jumped nine hours
 * in one step. Re-assigning an equal [LocalDate] is not a change, so a quiet minute
 * costs no recomposition.
 */
@Composable
fun rememberToday(): LocalDate {
    val zone = remember { ZoneId.systemDefault() }
    return produceState(LocalDate.now(zone), zone) {
        while (true) {
            val now = ZonedDateTime.now(zone)
            val untilMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone)).toMillis()
            delay(untilMidnight.coerceIn(1_000L, TODAY_POLL_MS))
            value = LocalDate.now(zone)
        }
    }.value
}
