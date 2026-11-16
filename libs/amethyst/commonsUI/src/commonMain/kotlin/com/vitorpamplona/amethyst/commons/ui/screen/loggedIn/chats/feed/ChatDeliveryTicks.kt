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

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.chatDelivery.ChatDelivery
import com.vitorpamplona.amethyst.commons.relayClient.chatDelivery.ChatSendState
import com.vitorpamplona.amethyst.commons.relayClient.chatDelivery.RecipientDelivery
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.broadcast
import com.vitorpamplona.amethyst.commons.resources.chat_delivery_accepted
import com.vitorpamplona.amethyst.commons.resources.chat_delivery_delivered_all
import com.vitorpamplona.amethyst.commons.resources.chat_delivery_details_title
import com.vitorpamplona.amethyst.commons.resources.chat_delivery_failed
import com.vitorpamplona.amethyst.commons.resources.chat_delivery_no_relay_info
import com.vitorpamplona.amethyst.commons.resources.chat_delivery_pending
import com.vitorpamplona.amethyst.commons.resources.chat_delivery_rejected
import com.vitorpamplona.amethyst.commons.resources.chat_delivery_sending
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.ui.components.ClickableBox
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.timeAbsolute
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font12SP
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import org.jetbrains.compose.resources.StringResource

/**
 * The timestamp on a chat message, as a single tap target that opens the relay /
 * delivery detail — "where did this message come from" and, for our own messages, how
 * far it got. It's always tappable so the relay list is one tap away on every message.
 *
 * Own messages additionally render the send/relay-acceptance glyph next to the time when
 * we have delivery data:
 * - pulsing clock: still being encrypted, wrapped or handed to the relay pool
 * - warning: the send failed before it reached the relay pool; tap it to retry
 * - clock: published, no relay has accepted yet
 * - single check: accepted somewhere (at least one relay OK / seen-on relay)
 * - double check (green): every recipient's / target relay accepted
 *
 * Old messages we didn't track this session simply show no tick, but the time still
 * opens the dialog (which lists the relays it was seen on, if any).
 */
@Composable
fun ChatTimeWithDelivery(
    baseNote: Note,
    isLoggedInUser: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val delivery by
        remember(baseNote) { accountViewModel.account.chatDeliveryTracker.deliveryFlow(baseNote.idHex) }
            .collectAsStateWithLifecycle()

    val seenOnState by
        remember(baseNote) { baseNote.flow().relays.stateFlow }
            .collectAsStateWithLifecycle()

    val seenOnRelays = seenOnState.note.relays
    val seenSomewhere = seenOnRelays.isNotEmpty()

    var showDetails by remember { mutableStateOf(false) }

    ClickableBox(onClick = { showDetails = true }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 2.dp),
        ) {
            ChatTimeAgo(baseNote)
            if (isLoggedInUser && (delivery != null || seenSomewhere)) {
                Spacer(StdHorzSpacer)
                RenderDeliveryTicks(
                    delivery = delivery,
                    seenSomewhere = seenSomewhere,
                    onRetry = { accountViewModel.retryChatSend(baseNote.idHex) },
                )
            }
        }
    }

    if (showDetails) {
        ChatDeliveryDetailDialog(
            baseNote = baseNote,
            delivery = delivery,
            seenOnRelays = seenOnRelays,
            onDismiss = { showDetails = false },
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

/**
 * Opens the relay / delivery detail for [baseNote] from anywhere (e.g. the long-press
 * action sheet), independent of the timestamp affordance — pulls our own delivery
 * tracking when present and always shows the relays the message was seen on.
 */
@Composable
fun ChatMessageDeliveryDialog(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    val delivery by
        remember(baseNote) { accountViewModel.account.chatDeliveryTracker.deliveryFlow(baseNote.idHex) }
            .collectAsStateWithLifecycle()

    val seenOnState by
        remember(baseNote) { baseNote.flow().relays.stateFlow }
            .collectAsStateWithLifecycle()

    ChatDeliveryDetailDialog(
        baseNote = baseNote,
        delivery = delivery,
        seenOnRelays = seenOnState.note.relays,
        onDismiss = onDismiss,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/**
 * Per-recipient (DMs) or per-relay (rooms) acceptance detail behind the tick,
 * with a re-broadcast escape hatch for messages stuck on pending relays.
 */
@Composable
private fun ChatDeliveryDetailDialog(
    baseNote: Note,
    delivery: ChatDelivery?,
    seenOnRelays: List<NormalizedRelayUrl>,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.chat_delivery_details_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Absolute timestamp header (the chat time no longer toggles to absolute
                // on tap — it opens this dialog instead).
                Text(
                    text = timeAbsolute(baseNote.createdAt(), prefix = "").trim(),
                    color = MaterialTheme.colorScheme.placeholderText,
                    fontSize = Font12SP,
                )

                // Encrypted-channel messages (Concord/Armada) are decrypted locally with no
                // per-relay attribution, so seen-on is empty — fall back to the channel's own
                // relays as "where this message lives".
                val channelRelays =
                    remember(baseNote) {
                        baseNote.inGatherers?.firstNotNullOfOrNull { (it as? Channel)?.relays()?.takeIf { r -> r.isNotEmpty() } } ?: emptySet()
                    }

                val recipients = delivery?.recipients
                when {
                    !recipients.isNullOrEmpty() ->
                        recipients.forEach { recipient ->
                            RecipientDeliveryRow(recipient, accountViewModel, nav)
                        }

                    delivery != null && delivery.targetRelays.isNotEmpty() ->
                        delivery.targetRelays.sortedBy { it.url }.forEach { relay ->
                            RelayDeliveryRow(
                                relay = relay,
                                accepted = relay in delivery.acceptedRelays || relay in seenOnRelays,
                                rejection = delivery.rejectedRelays[relay],
                            )
                        }

                    seenOnRelays.isNotEmpty() ->
                        // Untracked (sent before a restart): only the seen-on set is known.
                        seenOnRelays.sortedBy { it.url }.forEach { relay ->
                            RelayDeliveryRow(relay = relay, accepted = true)
                        }

                    channelRelays.isNotEmpty() ->
                        channelRelays.sortedBy { it.url }.forEach { relay ->
                            RelayDeliveryRow(relay = relay, accepted = true)
                        }

                    else ->
                        // Nothing to list: our own just-sent message no relay has acknowledged
                        // yet (pending), or a received/old message with no recorded relays.
                        Text(
                            text =
                                stringRes(
                                    if (delivery != null) {
                                        Res.string.chat_delivery_pending
                                    } else {
                                        Res.string.chat_delivery_no_relay_info
                                    },
                                ),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                }
            }
        },
        confirmButton = {
            if (accountViewModel.canBroadcast(baseNote)) {
                TextButton(
                    onClick = {
                        accountViewModel.broadcast(baseNote)
                        onDismiss()
                    },
                ) {
                    Text(stringRes(Res.string.broadcast))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.close))
            }
        },
    )
}

@Composable
private fun RecipientDeliveryRow(
    recipient: RecipientDelivery,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        LoadUser(baseUserHex = recipient.recipient) { user ->
            if (user != null) {
                UserPicture(user, Size20dp, Modifier, accountViewModel, nav)
                Row(modifier = Modifier.weight(1f)) {
                    UsernameDisplay(baseUser = user, accountViewModel = accountViewModel)
                }
            } else {
                Text(text = recipient.recipient.take(8), modifier = Modifier.weight(1f), maxLines = 1)
            }
        }

        DeliveryStatusTick(recipient.isDelivered)
    }
}

@Composable
private fun RelayDeliveryRow(
    relay: NormalizedRelayUrl,
    accepted: Boolean,
    rejection: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = relay.displayUrl(),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )

            if (!accepted && rejection != null) {
                TickIcon(MaterialSymbols.Block, Res.string.chat_delivery_rejected, MaterialTheme.colorScheme.error)
            } else {
                DeliveryStatusTick(accepted)
            }
        }
        // The relay's own words: the only clue to WHY a message will never arrive.
        if (!accepted && rejection != null) {
            Text(
                text = rejection.ifBlank { stringRes(Res.string.chat_delivery_rejected) },
                color = MaterialTheme.colorScheme.error,
                fontSize = Font12SP,
            )
        }
    }
}

@Composable
private fun DeliveryStatusTick(delivered: Boolean) {
    if (delivered) {
        TickIcon(MaterialSymbols.Done, Res.string.chat_delivery_accepted, MaterialTheme.colorScheme.allGoodColor)
    } else {
        TickIcon(MaterialSymbols.Schedule, Res.string.chat_delivery_pending, MaterialTheme.colorScheme.placeholderText)
    }
}

@Composable
private fun RenderDeliveryTicks(
    delivery: ChatDelivery?,
    seenSomewhere: Boolean,
    onRetry: () -> Unit,
) {
    val pendingColor = MaterialTheme.colorScheme.placeholderText

    // The send's own lifecycle outranks relay acceptance: until the event has
    // been handed to the relay pool there is nothing for a relay to have
    // accepted, and a failed send must not read as merely "not accepted yet".
    when (delivery?.sendState) {
        ChatSendState.SENDING -> {
            SendingTick()
            return
        }

        ChatSendState.FAILED -> {
            FailedTick(onRetry)
            return
        }

        else -> Unit
    }

    // Every targeted relay refused it: it will never arrive. Not "pending" — the detail dialog
    // behind this tick shows each relay's reason.
    if (delivery?.isRejected == true) {
        TickIcon(MaterialSymbols.Block, Res.string.chat_delivery_rejected, MaterialTheme.colorScheme.error)
        return
    }

    if (delivery == null) {
        // Untracked (sent before a restart): the seen-on relay set is the only signal.
        if (seenSomewhere) {
            TickIcon(MaterialSymbols.Done, Res.string.chat_delivery_accepted, pendingColor)
        } else {
            TickIcon(MaterialSymbols.Schedule, Res.string.chat_delivery_pending, pendingColor)
        }
        return
    }

    // "Delivered to everyone" and the k/n count describe the OTHER participants;
    // the sender's own self-copy wrap only shows in the detail dialog.
    val others = delivery.otherRecipients
    if (others != null && others.size > 1) {
        // Group DM: a double check once everyone got it. The k/n count only shows while
        // someone is still missing it: "6/6" on every bubble restated the double check in a
        // bright color, while "4/6" is the one case worth reading.
        val deliveredCount = others.count { it.isDelivered }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeliveryLadderTick(
                pending = deliveredCount == 0 && !seenSomewhere,
                fullyAccepted = delivery.isFullyAccepted,
            )
            if (!delivery.isFullyAccepted) {
                Text(
                    text = "$deliveredCount/${others.size}",
                    fontSize = Font12SP,
                    color = pendingColor,
                    maxLines = 1,
                )
            }
        }
        return
    }

    // 1:1 DMs and public rooms share the classic tick ladder.
    val acceptedSomewhere = seenSomewhere || delivery.acceptedRelays.isNotEmpty()
    DeliveryLadderTick(
        pending = !acceptedSomewhere,
        fullyAccepted = delivery.isFullyAccepted,
    )
}

/** The shared pending -> accepted-somewhere -> fully-accepted tick selection. */
@Composable
private fun DeliveryLadderTick(
    pending: Boolean,
    fullyAccepted: Boolean,
) {
    when {
        pending ->
            TickIcon(MaterialSymbols.Schedule, Res.string.chat_delivery_pending, MaterialTheme.colorScheme.placeholderText)

        // Same caption gray as the time beside it: the single vs double check already says
        // how far it got, and a green glyph on every bubble was a third accent on the screen.
        // Color is kept for the states that need the reader: rejected and failed.
        fullyAccepted ->
            TickIcon(MaterialSymbols.DoneAll, Res.string.chat_delivery_delivered_all, MaterialTheme.colorScheme.placeholderText)

        else ->
            TickIcon(MaterialSymbols.Done, Res.string.chat_delivery_accepted, MaterialTheme.colorScheme.placeholderText)
    }
}

/**
 * The message is on screen but has not left the device yet. A slow pulse says
 * work is still happening without claiming a percentage we don't have — the
 * encrypt/wrap/publish steps report nothing until they're done.
 */
@Composable
private fun SendingTick() {
    val transition = rememberInfiniteTransition(label = "chatSending")
    // Deliberately NOT read with `by`: this is a chat list, and a composition
    // read here would recompose the row on every animation frame. Reading the
    // state inside graphicsLayer defers it to the draw phase, so the pulse
    // costs a redraw of one glyph and nothing above it.
    val alpha =
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 700),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "chatSendingAlpha",
        )

    Icon(
        symbol = MaterialSymbols.Schedule,
        contentDescription = stringRes(Res.string.chat_delivery_sending),
        modifier = Modifier.size(14.dp).graphicsLayer { this.alpha = alpha.value },
        tint = MaterialTheme.colorScheme.placeholderText,
    )
}

/**
 * The send never reached the relay pool. The bubble keeps the text — losing what
 * someone typed is worse than any marker — and the glyph is the retry affordance,
 * so a failed message is recoverable with one tap instead of being retyped.
 */
@Composable
private fun FailedTick(onRetry: () -> Unit) {
    ClickableBox(onClick = onRetry) {
        Icon(
            symbol = MaterialSymbols.ErrorOutline,
            contentDescription = stringRes(Res.string.chat_delivery_failed),
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun TickIcon(
    symbol: MaterialSymbol,
    contentDescription: StringResource,
    tint: Color,
) {
    Icon(
        symbol = symbol,
        contentDescription = stringRes(contentDescription),
        modifier = Modifier.size(14.dp),
        tint = tint,
    )
}
