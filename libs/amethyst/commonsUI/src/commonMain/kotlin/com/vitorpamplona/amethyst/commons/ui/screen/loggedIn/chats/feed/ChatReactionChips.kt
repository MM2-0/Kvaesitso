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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.marmotGroups.marmotGroupIdFor
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteMinichatReplyCount
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReactions
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteZaps
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chat_minichat_reply_count
import com.vitorpamplona.amethyst.commons.ui.components.AnimatedBorderTextCornerRadius
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LikedIcon
import com.vitorpamplona.amethyst.commons.ui.note.ObserveZapAmountText
import com.vitorpamplona.amethyst.commons.ui.note.ZappedIcon
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.richtext.InLineIconRenderer
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Font12SP
import com.vitorpamplona.amethyst.commons.ui.theme.Size14Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.util.customEmojiUrlOrNull
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.appComponents.EncryptedMediaV2Cipher
import com.vitorpamplona.marmot.foundation.appEvents.MarmotCustomEmoji
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class ReactionChip(
    val type: String,
    val count: Int,
    val includesMe: Boolean,
    /** What the chip draws; differs from [type] only for a Marmot custom emoji that cannot be shown as a picture yet. */
    val glyph: String = type,
)

private val ChipGlyphFontSize = 13.sp

/**
 * Whether [ChatReactionChips] would render anything for this message — a reaction, a
 * zap, a pending zap, or a minichat-reply count. Mirrors the early-return gate in
 * [RenderChatReactionChips] and keeps the same live subscriptions, so a message that
 * gains its first reaction still flips this on. The bubble uses it to decide whether to
 * even mount the chip row (and reserve overlap space beneath the text) instead of always
 * paying for an empty row on every message.
 */
@Composable
fun hasChatEngagement(
    baseNote: Note,
    accountViewModel: AccountViewModel,
): Boolean {
    val reactionsState by observeNoteReactions(baseNote, accountViewModel)
    val hasReactions by
        remember(reactionsState) {
            derivedStateOf { (reactionsState?.note ?: baseNote).reactions.isNotEmpty() }
        }

    val zapsState by observeNoteZaps(baseNote, accountViewModel)
    val hasZaps by
        remember(zapsState) {
            derivedStateOf { (zapsState?.note ?: baseNote).zaps.isNotEmpty() }
        }

    val supportsMinichat =
        remember(baseNote) {
            baseNote.inGatherers?.any { it is ConcordChannel || it is PublicChatChannel || it is RelayGroupChannel } == true
        }
    val minichatCount by if (supportsMinichat) observeNoteMinichatReplyCount(baseNote, accountViewModel) else remember { mutableStateOf(0) }

    val zapsInFlight by accountViewModel.zapsInFlightFlow.collectAsStateWithLifecycle()
    val isZapping = zapsInFlight.contains(baseNote.idHex)

    return hasReactions || hasZaps || minichatCount > 0 || isZapping
}

/**
 * Messenger-style pills under a chat bubble summarizing who engaged with the message:
 * one chip per reaction emoji (with count, highlighted when the logged-in user is
 * among the reactors — tapping toggles the same reaction back) plus a sats chip when
 * the message was zapped. Renders nothing when the message has no engagement.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatReactionChips(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // A geohash chat provides these so reactions stay under its anonymous per-cell identity
    // (react + own-highlight); null (every other chat) keeps the standard account behavior.
    val onReact = LocalChatReactOverride.current
    val myIdentities = LocalChatActingIdentities.current
    // Deliberate cost: this observes reactions/zaps for EVERY visible message
    // (the ids fold into the batched EventFinder relay filters — one shared REQ,
    // not one per note), which is exactly what ReactionsRow already does for
    // every note in the main feeds. Gating on already-known engagement would be
    // cheaper but would never DISCOVER reactions for messages nobody expanded.
    val reactionsState by observeNoteReactions(baseNote, accountViewModel)

    // In a Marmot group a custom-emoji reaction's picture is an encrypted
    // attachment of the reaction itself (MDK 0.12). Until its key is known the
    // chip shows the `:shortcode:` as text, and a URL no attachment backs is
    // never fetched at all.
    val marmotEmoji = rememberMarmotReactionEmoji(baseNote, reactionsState?.note ?: baseNote, accountViewModel)

    val chips by
        remember(reactionsState, myIdentities, marmotEmoji) {
            derivedStateOf {
                val note = reactionsState?.note ?: baseNote
                val me = accountViewModel.userProfile()
                // In a group each custom-emoji reaction carries its own copy of the picture under its
                // own URL, so one chip per shortcode; elsewhere one per reaction key, as before.
                note.reactions.entries
                    .groupBy({ (type, _) -> marmotEmoji?.chipKey(type) ?: type }, { it.toPair() })
                    .map { (_, entries) ->
                        val reactors = entries.flatMap { it.second }
                        val mine =
                            if (myIdentities != null) {
                                reactors.any { it.author?.pubkeyHex in myIdentities }
                            } else {
                                reactors.any { it.author == me }
                            }
                        val type = entries.first().first
                        ReactionChip(type, reactors.size, mine, marmotEmoji?.glyphFor(entries.map { it.first }) ?: type)
                    }.sortedByDescending { it.count }
                    .toImmutableList()
            }
        }

    var showDetails by remember { mutableStateOf(false) }

    if (showDetails) {
        ChatEngagementDetailSheet(
            baseNote = baseNote,
            onDismiss = { showDetails = false },
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    // Kind-1111 thread ("minichat") replies. Only public chats wire them up (Concord,
    // NIP-28, NIP-29); NIP-17 DMs deliberately don't, so most clients wouldn't render
    // a thread there. An inline reply is an ordinary message and isn't counted.
    val supportsMinichat =
        remember(baseNote) {
            baseNote.inGatherers?.any { it is ConcordChannel || it is PublicChatChannel || it is RelayGroupChannel } == true
        }
    val minichatCount by if (supportsMinichat) observeNoteMinichatReplyCount(baseNote, accountViewModel) else remember { mutableStateOf(0) }

    // Optimistic feedback for a zap the user just fired from the drawer: the receipt (and
    // its amount) only reach relays a moment later, so show a pending zap chip until then.
    // The set changes only at zap start/end, so this rarely re-runs the row.
    val zapsInFlight by accountViewModel.zapsInFlightFlow.collectAsStateWithLifecycle()
    val isZapping = zapsInFlight.contains(baseNote.idHex)

    ObserveZapAmountText(baseNote, accountViewModel) { zapAmount ->
        RenderChatReactionChips(
            chips = chips,
            zapAmount = zapAmount,
            minichatCount = minichatCount,
            // Once the receipt's amount arrives the real sats chip takes over, so the
            // pending chip only shows while the amount is still blank.
            isZapping = isZapping && zapAmount.isBlank(),
            onToggleReaction = { onReact?.invoke(baseNote, it) ?: accountViewModel.reactToOrDelete(baseNote, it) },
            onOpenDetails = { showDetails = true },
            onOpenMinichat = { nav.nav(Route.ChatMinichat(baseNote.idHex)) },
        )
    }
}

/**
 * The edge of an engagement chip: a ring in the screen's background color rather than a
 * hairline. Riding the bubble's border, the ring cuts the chip out of the bubble cleanly;
 * the outlined pill it replaces drew one more line on every bubble that had a reaction.
 * A chip I'm part of says so with its accent fill alone.
 */
@Composable
fun chatChipRing(): BorderStroke = BorderStroke(2.dp, MaterialTheme.colorScheme.background)

/**
 * The strip of engagement chips that rides a chat bubble's bottom border. Shared so
 * every chat surface lays its chips out identically — [ChatReactionChips] fills it from
 * a [Note]'s reactions and zaps, cordn fills it from its own annotation fold.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatChipFlowRow(content: @Composable () -> Unit) {
    FlowRow(
        // Inset from the bubble's edge so overlapping chips ride the border without
        // poking past the bubble's rounded corners.
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

@Composable
private fun RenderChatReactionChips(
    chips: ImmutableList<ReactionChip>,
    zapAmount: String,
    minichatCount: Int,
    isZapping: Boolean,
    onToggleReaction: (String) -> Unit,
    onOpenDetails: () -> Unit,
    onOpenMinichat: () -> Unit,
) {
    if (chips.isEmpty() && zapAmount.isBlank() && minichatCount <= 0 && !isZapping) return

    ChatChipFlowRow {
        if (zapAmount.isNotBlank()) {
            ZapChip(zapAmount, onClick = onOpenDetails)
        } else if (isZapping) {
            PendingZapChip()
        }

        chips.forEach { chip ->
            ReactionChipView(
                chip = chip,
                onClick = { onToggleReaction(chip.type) },
                onLongClick = onOpenDetails,
            )
        }

        if (minichatCount > 0) {
            MinichatChip(minichatCount, onClick = onOpenMinichat)
        }
    }
}

/**
 * "N replies" thread entry, styled like the reaction/zap chips so the engagement row
 * reads as one strip under the bubble. Tapping opens the message's minichat thread.
 */
@Composable
private fun MinichatChip(
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        shape = ButtonBorder,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = chatChipRing(),
        // Plain clickable (not the Surface onClick overload) so the chip keeps its
        // content height instead of being padded to the 48dp minimum touch target,
        // which would drop it below the bubble border the reaction chips ride.
        modifier = Modifier.clip(ButtonBorder).clickable(onClick = onClick),
    ) {
        ChipContentRow {
            Icon(
                symbol = MaterialSymbols.Forum,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.grayText,
                modifier = Size14Modifier,
            )
            Text(
                text = pluralStringRes(Res.plurals.chat_minichat_reply_count, count, count),
                fontSize = Font12SP,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReactionChipView(
    chip: ReactionChip,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val background =
        if (chip.includesMe) {
            MaterialTheme.colorScheme.primary
                .copy(alpha = 0.20f)
                .compositeOver(MaterialTheme.colorScheme.background)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    Surface(
        shape = ButtonBorder,
        color = background,
        border = chatChipRing(),
        modifier =
            Modifier
                .clip(ButtonBorder)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        ChipContentRow {
            ChipReactionGlyph(chip.glyph)
            if (chip.count > 1) {
                Text(
                    text = chip.count.toString(),
                    fontSize = Font12SP,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ZapChip(
    amount: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = ButtonBorder,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = chatChipRing(),
        // Plain clickable (not the Surface onClick overload) so the chip keeps its
        // content height instead of being padded to the 48dp minimum touch target,
        // which would drop it below the bubble border the reaction chips ride.
        modifier = Modifier.clip(ButtonBorder).clickable(onClick = onClick),
    ) {
        ChipContentRow {
            ZappedIcon(Size14Modifier)
            Text(
                text = amount,
                fontSize = Font12SP,
                fontWeight = FontWeight.Bold,
                // Match the lightning glyph's bitcoin orange (theme-aware for contrast).
                color = MaterialTheme.colorScheme.bitcoinColor,
                maxLines = 1,
            )
        }
    }
}

/**
 * Optimistic placeholder shown while a zap the user just fired is still being paid:
 * the lightning glyph next to a small spinner. Replaced by the real sats [ZapChip]
 * as soon as the receipt's amount arrives.
 */
@Composable
private fun PendingZapChip() {
    Surface(
        shape = ButtonBorder,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = chatChipRing(),
    ) {
        ChipContentRow {
            ZappedIcon(Size14Modifier)
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

@Composable
private fun ChipContentRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

@Composable
internal fun ChipReactionGlyph(reactionType: String) {
    // A bare `:shortcode:` (its event had no emoji tag) has no image: it falls to the text branch.
    val renderable =
        remember(reactionType) {
            reactionType.customEmojiUrlOrNull()?.let { persistentListOf(CustomEmoji.ImageUrlType(it)) }
        }
    if (renderable != null) {
        InLineIconRenderer(
            renderable,
            style = SpanStyle(color = MaterialTheme.colorScheme.onBackground),
            fontSize = ChipGlyphFontSize,
            maxLines = 1,
        )
    } else {
        when (reactionType) {
            "+" -> LikedIcon(Size14Modifier)
            "-" -> Text(text = "👎", fontSize = ChipGlyphFontSize, maxLines = 1)
            else -> {
                if (EmojiCoder.isCoded(reactionType)) {
                    AnimatedBorderTextCornerRadius(reactionType, fontSize = ChipGlyphFontSize)
                } else {
                    Text(text = reactionType, fontSize = ChipGlyphFontSize, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The custom-emoji pictures of a Marmot message's reactions that can be shown:
 * each reaction's own `encrypted-media-v2` attachment, registered with the
 * download layer under the media key of the epoch that carried the reaction.
 */
class MarmotReactionEmoji(
    private val resolvedUrls: Set<String>,
) {
    /** [type] itself when its picture can be drawn, else its bare `:shortcode:`. */
    fun glyphFor(type: String): String {
        val url = type.customEmojiUrlOrNull() ?: return type
        return if (url in resolvedUrls) type else type.substringBefore(url)
    }

    /** One chip's glyph for reactions that share a shortcode: the first whose picture can be drawn. */
    fun glyphFor(types: List<String>): String = types.firstOrNull { it.customEmojiUrlOrNull() in resolvedUrls } ?: glyphFor(types.first())

    /** What makes two reactions one chip: a custom emoji's bare `:shortcode:`, anything else itself. */
    fun chipKey(type: String): String = type.customEmojiUrlOrNull()?.let { type.substringBefore(it) } ?: type
}

/** Null when [baseNote] is not a Marmot message (its reactions then render as before). */
@Composable
fun rememberMarmotReactionEmoji(
    baseNote: Note,
    note: Note,
    accountViewModel: AccountViewModel,
): MarmotReactionEmoji? {
    // Every chat row calls this; only a Marmot message pays for a producer.
    val groupId = remember(baseNote) { baseNote.marmotGroupIdFor(accountViewModel.account) } ?: return null
    val reactions = note.reactions
    val resolved by
        produceState(emptySet<String>(), groupId, reactions) {
            val urls = HashSet<String>()
            reactions.values.flatten().forEach { reactor ->
                val event = reactor.event ?: return@forEach
                val matched = MarmotCustomEmoji.matchedEmojiAttachments(event)
                if (matched.isEmpty()) return@forEach
                val secret = accountViewModel.marmotMediaSecretForMessage(groupId, reactor.idHex) ?: return@forEach
                matched.forEach { (url, reference) ->
                    if (url.startsWith("https://") || url.startsWith("http://")) {
                        accountViewModel.account.encryptionKeyCache.add(url, EncryptedMediaV2Cipher(secret, reference), reference.mediaType)
                        urls.add(url)
                    }
                }
            }
            value = urls
        }
    return remember(resolved) { MarmotReactionEmoji(resolved) }
}
