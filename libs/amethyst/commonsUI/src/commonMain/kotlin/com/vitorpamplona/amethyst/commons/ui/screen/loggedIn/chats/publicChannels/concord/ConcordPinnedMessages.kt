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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.actions.ConcordChannelPins
import com.vitorpamplona.amethyst.commons.actions.ConcordPinnedMessage
import com.vitorpamplona.amethyst.commons.actions.ConcordPinning
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip92IMeta.appendMissingImetaUrls
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.concord_pin_expiring_body
import com.vitorpamplona.amethyst.commons.resources.concord_pin_expiring_confirm
import com.vitorpamplona.amethyst.commons.resources.concord_pin_expiring_title
import com.vitorpamplona.amethyst.commons.resources.concord_pinned_budget
import com.vitorpamplona.amethyst.commons.resources.concord_pinned_empty
import com.vitorpamplona.amethyst.commons.resources.concord_pinned_open_hint
import com.vitorpamplona.amethyst.commons.resources.concord_pinned_title
import com.vitorpamplona.amethyst.commons.resources.concord_pinned_unavailable
import com.vitorpamplona.amethyst.commons.resources.message_edited
import com.vitorpamplona.amethyst.commons.resources.relay_group_pinned_content_description
import com.vitorpamplona.amethyst.commons.resources.relay_group_unpin_message
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChatEditEvent
import com.vitorpamplona.quartz.concord.cord04Roles.pins.ConcordPins
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource

/**
 * [channelId]'s verified pins (CORD-04 §7), re-read whenever the Control Plane seats a new Pin List
 * head, the fold changes or finishes draining, a delete / Edit naming a pinned message lands in the
 * cache (a held delete hides its entry at once; a held newer Edit marks it edited), or a pinned
 * message's disappearing-message deadline passes (CORD-08 §3). Null until the community has folded
 * the channel.
 *
 * The session is looked up again on every session-set change: it may not exist at first
 * composition, and a Refounding replaces it — a captured one would read the dead epoch forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun rememberConcordChannelPins(
    communityId: String,
    channelId: String,
    accountViewModel: AccountViewModel,
): State<ConcordChannelPins?> {
    val account = accountViewModel.account
    return produceState<ConcordChannelPins?>(null, account, communityId, channelId) {
        account.concordSessions.revision
            .map { account.concordSessions.sessionFor(communityId) }
            .distinctUntilChanged { a, b -> a === b }
            .collectLatest { session ->
                if (session == null) {
                    value = null
                    return@collectLatest
                }
                // Only deletes and Edits that name a message this list carries can change the read.
                val evidence =
                    account.cache.live.newEventBundles.filter { notes ->
                        val carried = value?.rumorIds ?: return@filter true
                        notes.any { note ->
                            when (val event = note.event) {
                                is DeletionRequestEvent -> event.deletesAnyEventIn(carried)
                                is ConcordChatEditEvent -> event.editedMessageId() in carried
                                else -> false
                            }
                        }
                    }
                merge(session.pinHeads.map { }, session.state.map { }, session.controlDrained.map { }, evidence.map { })
                    .conflate()
                    .collectLatest {
                        // Re-read, then sleep until the next pinned message expires: an expired one
                        // leaves the list, and nothing else would trigger that re-read. A new trigger
                        // cancels the wait.
                        while (true) {
                            val pins = withContext(Dispatchers.IO) { account.concord.concordChannelPins(communityId, channelId) }
                            value = pins
                            val now = TimeUtils.now()
                            val next = pins?.nextExpiry(now) ?: break
                            delay((next - now) * 1000 + 250)
                        }
                    }
            }
    }
}

/**
 * [pins] as a reader should see them: entries by a banned author (CORD-04 §4 — every client declines
 * to show their posts) or by someone this account mutes or blocks are left out.
 */
@Composable
fun rememberVisibleConcordPins(
    communityId: String,
    pins: ConcordChannelPins,
    accountViewModel: AccountViewModel,
): List<ConcordPinnedMessage> {
    val account = accountViewModel.account
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    val hidden by account.hiddenUsers.flow.collectAsStateWithLifecycle()
    return remember(pins, revision, hidden) {
        val authority =
            account.concordSessions
                .sessionFor(communityId)
                ?.state
                ?.value
                ?.authority
        ConcordPinning.visible(pins, isBanned = { authority?.isBanned(it) == true }, isHidden = { account.isHidden(it) })
    }
}

/**
 * The Pin action on a disappearing message (one carrying a CORD-08 `expiration`) asks first: the pin
 * carries the message's words in its proof, so it keeps them readable after the timer erased the
 * message everywhere else.
 */
@Composable
fun ConcordExpiringPinDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.concord_pin_expiring_title)) },
        text = { Text(stringRes(Res.string.concord_pin_expiring_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringRes(Res.string.concord_pin_expiring_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

/** The channel header's pinned-messages entry point: a pin with a count badge. Hidden when there is nothing to show. */
@Composable
fun ConcordPinnedButton(
    communityId: String,
    pins: ConcordChannelPins?,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    if (pins == null) return
    val count = rememberVisibleConcordPins(communityId, pins, accountViewModel).size
    if (count == 0 && !pins.sealedUnavailable) return
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (count > 0) Badge { Text(count.toString()) }
            },
        ) {
            Icon(symbol = MaterialSymbols.PushPin, contentDescription = stringRes(Res.string.relay_group_pinned_content_description))
        }
    }
}

/**
 * The pinned-messages sheet: each verified pin with its author, time and words (marked edited when
 * revised) rendered like the chat feed renders the message — links, mentions and its `imeta`
 * attachments included — an "unavailable" notice when the list is sealed under a key this account
 * never held, a jump to the message when it resolves locally, and Unpin for those who may write pins.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordPinnedMessagesSheet(
    communityId: String,
    channelId: String,
    pins: ConcordChannelPins,
    accountViewModel: AccountViewModel,
    nav: INav,
    onJumpToMessage: (HexKey) -> Unit,
    onDismiss: () -> Unit,
) {
    val canPin = remember(pins) { accountViewModel.account.concord.canPinConcord(communityId) }
    val revision by accountViewModel.account.concordSessions.revision
        .collectAsStateWithLifecycle()
    val session = remember(communityId, revision) { accountViewModel.account.concordSessions.sessionFor(communityId) }
    // Banned authors and muted/blocked users stay out of the sheet, as they do from the feed.
    val shown = rememberVisibleConcordPins(communityId, pins, accountViewModel)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringRes(Res.string.concord_pinned_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (canPin && !pins.sealedUnavailable) {
                // Bytes, not the count, are the real ceiling for a sealed list (§7 Limits): surface both.
                val bytes =
                    remember(pins) {
                        pins.head
                            ?.content
                            ?.encodeToByteArray()
                            ?.size ?: 0
                    }
                Text(
                    text = stringRes(Res.string.concord_pinned_budget, pins.count, ConcordPins.MAX_ENTRIES, bytes * 100 / ConcordPins.MAX_CONTENT_BYTES),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (shown.isNotEmpty()) {
                Text(
                    text = stringRes(Res.string.concord_pinned_open_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(8.dp))

            if (pins.sealedUnavailable) {
                PinNotice(Res.string.concord_pinned_unavailable, MaterialSymbols.Lock)
            } else if (shown.isEmpty() && pins.complete) {
                // Only a drained fold may say "no pins"; before that the list may simply not be served yet.
                PinNotice(Res.string.concord_pinned_empty, MaterialSymbols.PushPin)
            }

            LazyColumn {
                items(shown, key = { it.rumorId }) { pinned ->
                    val jumpable = remember(pinned.rumorId, session) { session?.holdsRumor(pinned.rumorId) == true }
                    PinnedRow(
                        pinned = pinned,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onClick =
                            if (jumpable) {
                                {
                                    onJumpToMessage(pinned.rumorId)
                                    onDismiss()
                                }
                            } else {
                                null
                            },
                        onUnpin = if (canPin) ({ accountViewModel.unpinConcordRumor(communityId, channelId, pinned.rumorId) }) else null,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PinNotice(
    text: StringResource,
    symbol: MaterialSymbol,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(symbol = symbol, contentDescription = null, tint = MaterialTheme.colorScheme.placeholderText, modifier = Modifier.size(20.dp))
        Text(text = stringRes(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.placeholderText)
    }
}

@Composable
private fun PinnedRow(
    pinned: ConcordPinnedMessage,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClick: (() -> Unit)?,
    onUnpin: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = rememberPinAuthorName(pinned.author, accountViewModel),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = timeAgoNoDot(pinned.pin.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                )
                if (pinned.edited) {
                    // §7: a revised message is never shown as if its words were the original, current ones.
                    Text(
                        text = stringRes(Res.string.message_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                }
            }
            PinnedContent(pinned, accountViewModel, nav)
        }
        if (onUnpin != null) {
            IconButton(onClick = onUnpin) {
                Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.relay_group_unpin_message), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * The pinned message's words and attachments through the chat feed's own pipeline: the rumor rebuilt
 * from the proof (its encrypted attachments' keys registered), an attachment-only message given its
 * `imeta` URLs as text exactly as the feed does, and the shared rich-text viewer rendering the media.
 */
@Composable
private fun PinnedContent(
    pinned: ConcordPinnedMessage,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val rumor = remember(pinned) { accountViewModel.account.concord.concordPinnedRumor(pinned) }
    val tags = remember(rumor) { rumor.tags.toImmutableListOfLists() }
    val content = remember(rumor) { appendMissingImetaUrls(rumor.content, rumor) }
    val background = MaterialTheme.colorScheme.surface
    val backgroundColor = remember(background) { mutableStateOf(background) }
    TranslatableRichTextViewer(
        content = content,
        canPreview = true,
        quotesLeft = 0,
        tags = tags,
        backgroundColor = backgroundColor,
        id = pinned.rumorId,
        authorPubKey = pinned.author,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/** [hex]'s best display name, reactively, falling back to a short hex. */
@Composable
private fun rememberPinAuthorName(
    hex: HexKey,
    accountViewModel: AccountViewModel,
): String {
    val user = remember(hex) { LocalCache.checkGetOrCreateUser(hex) } ?: return remember(hex) { hex.take(8) }
    val info by observeUserInfo(user, accountViewModel)
    return info?.info?.bestName() ?: remember(user) { user.pubkeyDisplayHex() }
}
