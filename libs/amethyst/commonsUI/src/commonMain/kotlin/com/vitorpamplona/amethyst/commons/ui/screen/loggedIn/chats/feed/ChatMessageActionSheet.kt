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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.isBuzzEditableBy
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_edit_message
import com.vitorpamplona.amethyst.commons.resources.chat_delivery_details_title
import com.vitorpamplona.amethyst.commons.resources.edit_draft
import com.vitorpamplona.amethyst.commons.resources.edit_message
import com.vitorpamplona.amethyst.commons.resources.more_options
import com.vitorpamplona.amethyst.commons.resources.quick_action_delete_dialog_btn
import com.vitorpamplona.amethyst.commons.resources.quick_action_request_deletion_alert_title
import com.vitorpamplona.amethyst.commons.resources.relay_group_pin_message
import com.vitorpamplona.amethyst.commons.resources.relay_group_unpin_message
import com.vitorpamplona.amethyst.commons.resources.reply_description
import com.vitorpamplona.amethyst.commons.resources.show_less
import com.vitorpamplona.amethyst.commons.ui.actions.EditPostView
import com.vitorpamplona.amethyst.commons.ui.components.ClickableBox
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ChangeReactionIcon
import com.vitorpamplona.amethyst.commons.ui.note.QuickActionAlertDialog
import com.vitorpamplona.amethyst.commons.ui.note.RenderReaction
import com.vitorpamplona.amethyst.commons.ui.note.deletionRequestBody
import com.vitorpamplona.amethyst.commons.ui.note.elements.AddHashtagLabelDialog
import com.vitorpamplona.amethyst.commons.ui.note.elements.ConcordBanConfirmationDialog
import com.vitorpamplona.amethyst.commons.ui.note.elements.DropDownParams
import com.vitorpamplona.amethyst.commons.ui.note.elements.NoteActionHandlers
import com.vitorpamplona.amethyst.commons.ui.note.elements.RemoveForEveryoneConfirmationDialog
import com.vitorpamplona.amethyst.commons.ui.note.elements.ShareOptionsBottomSheet
import com.vitorpamplona.amethyst.commons.ui.note.elements.noteActionSections
import com.vitorpamplona.amethyst.commons.ui.note.elements.observeBookmarksFollowsAndAccount
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.QuickZapAmountRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordExpiringPinDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.report.ReportNoteDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.OnchainZapSendDialog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Size28Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.SmallishBorder
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.reactionBox
import com.vitorpamplona.amethyst.commons.ui.theme.selectedReactionBoxModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.foundation.appEvents.MarmotCustomEmoji
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordDisappearing
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

// null amount = open the on-chain dialog with no prefill.
@Immutable
private data class OnchainZapRequest(
    val amountSats: Long?,
)

/**
 * Long-press surface for a chat message: a quick-reaction row and the unpacked
 * zap amount presets on top, chat-only tiles (reply, edit draft), then the shared
 * note-action inventory (noteActionSections — the same one behind the 3-dot menu,
 * so the two surfaces can never drift) rendered as rows of icon tiles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMessageActionSheet(
    note: Note,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToEditChatMessage: ((Note) -> Unit)? = null,
) {
    var showShareSheet by remember { mutableStateOf(false) }
    var wantsToEditPost by remember { mutableStateOf(false) }
    var reportDialogShowing by remember { mutableStateOf(false) }
    var addLabelDialogShowing by remember { mutableStateOf(false) }
    var deleteConfirmationShowing by remember { mutableStateOf(false) }
    var concordBanConfirming by remember { mutableStateOf(false) }
    var removeForEveryoneConfirming by remember { mutableStateOf(false) }
    var showDeliveryDialog by remember { mutableStateOf(false) }

    val performDelete = { accountViewModel.deleteOwn(note) }

    if (deleteConfirmationShowing) {
        QuickActionAlertDialog(
            title = stringRes(Res.string.quick_action_request_deletion_alert_title),
            textContent = stringRes(deletionRequestBody(note, accountViewModel)),
            buttonIcon = MaterialSymbols.Delete,
            buttonText = stringRes(Res.string.quick_action_delete_dialog_btn),
            onClickDoOnce = {
                performDelete()
                onDismiss()
            },
            onClickDontShowAgain = {
                performDelete()
                accountViewModel.account.settings.setHideDeleteRequestDialog()
                onDismiss()
            },
            onDismiss = { deleteConfirmationShowing = false },
        )
    }

    // On-chain zaps need a dialog that outlives the sheet, so the request swaps
    // the sheet for the dialog. Share works the same way (see NoteDropDownMenu).
    var onchainZapRequest by remember { mutableStateOf<OnchainZapRequest?>(null) }

    onchainZapRequest?.let { request ->
        val zappedEventHint = remember(note) { note.toEventHint<Event>() }
        OnchainZapSendDialog(
            accountViewModel = accountViewModel,
            onDismiss = onDismiss,
            recipientPubKey = note.author?.pubkeyHex,
            zappedEvent = zappedEventHint,
            prefillAmountSats = request.amountSats,
        )
        return
    }

    if (showShareSheet) {
        ShareOptionsBottomSheet(
            note = note,
            nav = nav,
            onDismiss = onDismiss,
        )
        return
    }

    if (showDeliveryDialog) {
        ChatMessageDeliveryDialog(
            baseNote = note,
            accountViewModel = accountViewModel,
            nav = nav,
            onDismiss = onDismiss,
        )
        return
    }

    if (wantsToEditPost) {
        EditPostView(
            onClose = onDismiss,
            edit = note,
            versionLookingAt = null,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    if (reportDialogShowing) {
        ReportNoteDialog(note = note, accountViewModel = accountViewModel) {
            reportDialogShowing = false
            onDismiss()
        }
    }

    if (addLabelDialogShowing) {
        AddHashtagLabelDialog(
            note = note,
            accountViewModel = accountViewModel,
            onDismiss = {
                addLabelDialogShowing = false
                onDismiss()
            },
        )
    }

    if (concordBanConfirming) {
        ConcordBanConfirmationDialog(
            note = note,
            accountViewModel = accountViewModel,
            onClose = { concordBanConfirming = false },
            onBanned = onDismiss,
        )
    }

    if (removeForEveryoneConfirming) {
        RemoveForEveryoneConfirmationDialog(
            note = note,
            accountViewModel = accountViewModel,
            onClose = { removeForEveryoneConfirming = false },
            onRemoved = onDismiss,
        )
    }

    val state by observeBookmarksFollowsAndAccount(note, accountViewModel).collectAsStateWithLifecycle(
        DropDownParams(
            isFollowingAuthor = false,
            isPrivateBookmarkNote = false,
            isPublicBookmarkNote = false,
            isPinnedNote = false,
            // Seeded from the note so the first frame doesn't flash author actions on your own message.
            isLoggedUser = accountViewModel.isLoggedUser(note.author),
            isSensitive = false,
            showSensitiveContent = null,
        ),
    )

    var showAllActions by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (!note.isDraft()) {
                QuickReactionRow(note, onDismiss, accountViewModel, nav)

                QuickZapAmountRow(
                    note = note,
                    onDismiss = onDismiss,
                    onOnchainRequest = { onchainZapRequest = OnchainZapRequest(it) },
                    accountViewModel = accountViewModel,
                    nav = nav,
                )

                SectionDivider()
            }

            // Stage one: the primary chat action (reply / edit draft) is always shown.
            ChatOnlyRow(note, state, onWantsToReply, onWantsToEditDraft, onDismiss)

            // Editing a chat message, gated by where it lives:
            //  - Buzz: kind-9 message in a Buzz-dialect relay group (or a legacy kind-40002 one)
            //    → a kind-40003 edit, by its author or the author's agent owner.
            //  - A room (Concord channel, Marmot group): the room says which of its messages its
            //    composer can edit, and sends the edit inside itself.
            // A note is only ever one of the two, so at most one tile shows and both route
            // through the same edit callback.
            // Buzz lets the owner of an agent edit the agent's messages too, and credits a
            // relay-signed message to the member it names, so its own rule decides.
            val canEditBuzz =
                onWantsToEditChatMessage != null &&
                    (
                        note.event is StreamMessageV2Event ||
                            (
                                note.event is ChatEvent &&
                                    note.inGatherers?.any { it is RelayGroupChannel && BuzzRelayDialect.isBuzz(it.groupId.relayUrl) } == true
                            )
                    ) &&
                    note.isBuzzEditableBy(accountViewModel.userProfile().pubkeyHex)
            // A room (Concord channel, Marmot group) decides which of its messages its composer can edit.
            val canEditInRoom = onWantsToEditChatMessage != null && accountViewModel.canEditInRoom(note)
            if (canEditBuzz || canEditInRoom) {
                SectionDivider()
                TileRow {
                    val label = if (canEditBuzz) Res.string.buzz_edit_message else Res.string.edit_message
                    ActionTile(MaterialSymbols.Edit, stringRes(label)) {
                        onWantsToEditChatMessage(note)
                        onDismiss()
                    }
                }
            }

            val handlers =
                NoteActionHandlers(
                    onShare = { showShareSheet = true },
                    onEditPost = { wantsToEditPost = true },
                    onEditDraft = {
                        onWantsToEditDraft(note)
                        onDismiss()
                    },
                    onAddLabel = { addLabelDialogShowing = true },
                    onReport = { reportDialogShowing = true },
                    onDeleteRequest = {
                        if (accountViewModel.account.settings.hideDeleteRequestDialog) {
                            performDelete()
                            onDismiss()
                        } else {
                            deleteConfirmationShowing = true
                        }
                    },
                    onConcordBan = { concordBanConfirming = true },
                    onRemoveForEveryone = { removeForEveryoneConfirming = true },
                    onDismiss = onDismiss,
                )

            // Stage two: the full shared note-action inventory (the same one behind the
            // 3-dot menu, so the two surfaces never drift). Tucked behind a toggle so the
            // sheet opens compact — react, zap, reply — and only grows to full height
            // when the user asks for the rest.
            val sections =
                noteActionSections(
                    note = note,
                    noteVersionToCopy = note,
                    state = state,
                    handlers = handlers,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )

            if (sections.isNotEmpty()) {
                SectionDivider()
                MoreActionsToggle(expanded = showAllActions, onToggle = { showAllActions = !showAllActions })

                if (showAllActions) {
                    sections.forEach { section ->
                        SectionDivider()
                        TileRow {
                            section.forEach { action ->
                                ActionTile(action.symbol, action.label, action.isDestructive, action.onClick)
                            }
                        }
                    }

                    // Chat-specific: which relays carry this message (and, for our own,
                    // their acceptance). A secondary action, so it lives under Show More.
                    if (!note.isDraft()) {
                        SectionDivider()
                        TileRow {
                            ActionTile(MaterialSymbols.DoneAll, stringRes(Res.string.chat_delivery_details_title)) {
                                showDeliveryDialog = true
                            }
                        }
                    }

                    // NIP-29 moderator action: pin/unpin this message in its relay group.
                    // Only rendered (and only subscribes) when the note belongs to a group.
                    val relayGroup = remember(note) { note.inGatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel } }
                    if (relayGroup != null && !note.isDraft()) {
                        RelayGroupPinTile(note, relayGroup, onDismiss, accountViewModel)
                    }

                    // Concord (CORD-04 §7): pin/unpin into the channel's Pin List. Only offered to a
                    // PIN_MESSAGES holder who can write the Control Plane (null otherwise).
                    // Read off the main thread: it verifies the channel's whole Pin List.
                    val concordPinState by produceState<Boolean?>(null, note) {
                        value = withContext(Dispatchers.IO) { accountViewModel.account.concord.concordPinState(note) }
                    }
                    val concordPinned = concordPinState
                    if (concordPinned != null && !note.isDraft()) {
                        var confirmExpiringPin by remember(note) { mutableStateOf(false) }
                        SectionDivider()
                        TileRow {
                            val label = if (concordPinned) Res.string.relay_group_unpin_message else Res.string.relay_group_pin_message
                            ActionTile(MaterialSymbols.PushPin, stringRes(label)) {
                                // Pinning a disappearing message (CORD-08) keeps its words past the timer: ask first.
                                val expires = note.event?.let { ConcordDisappearing.expirationOf(it) } != null
                                if (!concordPinned && expires) {
                                    confirmExpiringPin = true
                                } else {
                                    accountViewModel.toggleConcordPin(note)
                                    onDismiss()
                                }
                            }
                        }
                        if (confirmExpiringPin) {
                            ConcordExpiringPinDialog(
                                onConfirm = {
                                    confirmExpiringPin = false
                                    accountViewModel.toggleConcordPin(note)
                                    onDismiss()
                                },
                                onDismiss = { confirmExpiringPin = false },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * NIP-29 moderator-only tile that pins/unpins this message in its relay group.
 * Observes the group's live roster + pin list, so it shows the right verb and
 * hides itself entirely for members who can't moderate. Pinning replaces the whole
 * kind-9010 list; the relay republishes the kind-39005 pins the bar reads.
 */
@Composable
private fun RelayGroupPinTile(
    note: Note,
    baseChannel: RelayGroupChannel,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? RelayGroupChannel ?: baseChannel

    val canModerate = channel.membershipOf(accountViewModel.userProfile().pubkeyHex).canModerate()
    if (!canModerate) return

    val pinned = channel.isPinned(note.idHex)

    SectionDivider()
    TileRow {
        if (pinned) {
            ActionTile(MaterialSymbols.PushPin, stringRes(Res.string.relay_group_unpin_message)) {
                accountViewModel.unpinRelayGroupMessage(channel, note)
                onDismiss()
            }
        } else {
            ActionTile(MaterialSymbols.PushPin, stringRes(Res.string.relay_group_pin_message)) {
                accountViewModel.pinRelayGroupMessage(channel, note)
                onDismiss()
            }
        }
    }
}

/**
 * The expand/collapse control between the always-visible quick actions and the full
 * action inventory. Keeps the sheet short on open (the common case is a reaction, a
 * zap, or a reply) and reveals everything else on demand.
 */
@Composable
fun MoreActionsToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(SmallishBorder)
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(if (expanded) Res.string.show_less else Res.string.more_options),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- Action tile sections ----------

/** Chat-specific tiles (reply, edit draft) that have no 3-dot menu equivalent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatOnlyRow(
    note: Note,
    state: DropDownParams,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    onDismiss: () -> Unit,
) {
    if (note.isDraft() && !state.isLoggedUser) return
    if (note.isDraft() && state.isLoggedUser) {
        TileRow {
            ActionTile(MaterialSymbols.Edit, stringRes(Res.string.edit_draft)) {
                onWantsToEditDraft(note)
                onDismiss()
            }
        }
        return
    }

    TileRow {
        ActionTile(MaterialSymbols.AutoMirrored.Chat, stringRes(Res.string.reply_description)) {
            onWantsToReply(note)
            onDismiss()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TileRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
}

@Composable
fun ActionTile(
    symbol: MaterialSymbol,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier =
            Modifier
                .width(80.dp)
                .clip(SmallishBorder)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val SECTION_DIVIDER_ALPHA = 0.5f

@Composable
fun SectionDivider() {
    HorizontalDivider(
        thickness = DividerThickness,
        color = MaterialTheme.colorScheme.placeholderText.copy(alpha = SECTION_DIVIDER_ALPHA),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

// ---------- Quick reaction + zap rows ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickReactionRow(
    note: Note,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // A geohash chat provides these so a reaction (and its "selected" highlight) stays under the
    // anonymous per-cell identity; null (every other chat) keeps the standard account behavior.
    val onReactOverride = LocalChatReactOverride.current
    val actingIdentities = LocalChatActingIdentities.current
    val reactions by accountViewModel.reactionChoicesFlow().collectAsStateWithLifecycle()
    val myReactions =
        remember(note, actingIdentities) {
            if (actingIdentities != null) {
                note.reactions
                    .filterValues { reactors -> reactors.any { it.author?.pubkeyHex in actingIdentities } }
                    .keys
                    .toImmutableSet()
            } else {
                note.allReactionsByAuthor(accountViewModel.userProfile()).toImmutableSet()
            }
        }
    // A group's custom-emoji reaction is keyed by its own encrypted copy's URL, never the pack's,
    // so a pick is "already mine" by shortcode.
    val myShortcodes = remember(myReactions) { myReactions.mapNotNullTo(HashSet()) { MarmotCustomEmoji.reactionShortcode(it) } }

    // Wrap onto as many rows as needed (like the zap grid) rather than a single
    // horizontally-scrolling strip, so long custom-reaction sets stay reachable.
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    ) {
        reactions.forEach { reactionType ->
            ClickableBox(
                modifier =
                    if (reactionType in myReactions || MarmotCustomEmoji.reactionShortcode(reactionType) in myShortcodes) {
                        MaterialTheme.colorScheme.selectedReactionBoxModifier
                    } else {
                        reactionBox
                    },
                onClick = {
                    onReactOverride?.invoke(note, reactionType) ?: accountViewModel.reactToOrDelete(note, reactionType)
                    onDismiss()
                },
            ) {
                RenderReaction(reactionType)
            }
        }

        ClickableBox(
            modifier = reactionBox,
            onClick = {
                nav.nav(Route.UpdateReactionType)
                onDismiss()
            },
        ) {
            ChangeReactionIcon(Size28Modifier, MaterialTheme.colorScheme.placeholderText)
        }
    }
}
