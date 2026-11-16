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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.kc_proto_bolt12_zap_title
import com.vitorpamplona.amethyst.commons.resources.kc_proto_cashu_history_title
import com.vitorpamplona.amethyst.commons.resources.kc_proto_cashu_mint_quote_title
import com.vitorpamplona.amethyst.commons.resources.kc_proto_cashu_redeemed_count
import com.vitorpamplona.amethyst.commons.resources.kc_proto_encryption_current
import com.vitorpamplona.amethyst.commons.resources.kc_proto_encryption_keys_title
import com.vitorpamplona.amethyst.commons.resources.kc_proto_encryption_older_count
import com.vitorpamplona.amethyst.commons.resources.kc_proto_key_request_code
import com.vitorpamplona.amethyst.commons.resources.kc_proto_key_request_title
import com.vitorpamplona.amethyst.commons.resources.kc_proto_keypackage_legacy
import com.vitorpamplona.amethyst.commons.resources.kc_proto_keypackage_title
import com.vitorpamplona.amethyst.commons.resources.kc_proto_mls_version_meta
import com.vitorpamplona.amethyst.commons.resources.kc_proto_nutzap_senders
import com.vitorpamplona.amethyst.commons.resources.kc_proto_nwc_encryption
import com.vitorpamplona.amethyst.commons.resources.kc_proto_nwc_extensions
import com.vitorpamplona.amethyst.commons.resources.kc_proto_nwc_method_count
import com.vitorpamplona.amethyst.commons.resources.kc_proto_nwc_methods
import com.vitorpamplona.amethyst.commons.resources.kc_proto_nwc_notifications
import com.vitorpamplona.amethyst.commons.resources.kc_proto_nwc_title
import com.vitorpamplona.amethyst.commons.resources.kc_proto_offer
import com.vitorpamplona.amethyst.commons.resources.kc_proto_relays
import com.vitorpamplona.amethyst.commons.resources.kc_proto_zap_request_title
import com.vitorpamplona.amethyst.commons.resources.kc_proto_zap_to
import com.vitorpamplona.amethyst.commons.resources.kind_card_private_pill
import com.vitorpamplona.amethyst.commons.resources.zap_type_anonymous
import com.vitorpamplona.amethyst.commons.resources.zap_type_private
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ActivityAmountRow
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.PlatformNumberFormatter
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.mip01Groups.MlsCiphersuite
import com.vitorpamplona.quartz.experimental.decoupling.setup.EncryptionKeyListEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.request.EncryptionKeyRequestEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.LegacyKeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.PublishedKeyPackage
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip01Core.tags.references.HttpUrlFormatter
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcInfoEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.intent.Bolt12ZapIntentEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.tags.AmountTag

/*
 * Cards for the protocol-plumbing kinds: payment requests and wallet capabilities, MLS key
 * packages and the other records that carry public fields worth reading. Kinds whose payload is
 * only ciphertext are drawn by [RenderPrivateKindCard] straight from the dispatch.
 */

/** NIP-57 zap request (kind 9734): the amount, who is zapped, the public comment and the target. */
@Composable
fun RenderZapRequest(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ZapRequestEvent ?: return
    val card =
        remember(event) {
            ZapRequestCard(
                amountSats =
                    event.tags
                        .firstNotNullOfOrNull(AmountTag::parse)
                        ?.div(1000)
                        ?.takeIf { it > 0 },
                recipients = event.zappedAuthor(),
                zappedEvent = event.zappedPost().firstOrNull(),
                zappedAddress = event.zappedAddresses().firstOrNull()?.let { Address.parse(it) },
                isPrivate = event.isPrivateZap(),
                isAnonymous = event.hasAnonTag() && !event.isPrivateZap(),
            )
        }

    // A public zap is the default, so only the private and anonymous kinds earn a pill.
    val typeLabel =
        when {
            card.isPrivate -> stringRes(Res.string.zap_type_private)
            card.isAnonymous -> stringRes(Res.string.zap_type_anonymous)
            else -> null
        }

    KindCard(
        icon = MaterialSymbols.Bolt,
        title = stringRes(Res.string.kc_proto_zap_request_title),
        tone = KindTone.Money,
        status = typeLabel,
        statusTone = KindTone.Neutral,
    ) {
        ZapAmount(card.amountSats)
        ZapRecipients(card.recipients, accountViewModel, nav)
        // A private zap keeps its message encrypted in the `anon` tag; content is not the comment.
        if (!card.isPrivate) {
            KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
        }
        ZapTarget(card.zappedEvent, card.zappedAddress, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

private class ZapRequestCard(
    val amountSats: Long?,
    val recipients: List<HexKey>,
    val zappedEvent: HexKey?,
    val zappedAddress: Address?,
    val isPrivate: Boolean,
    val isAnonymous: Boolean,
)

/** NIP-B1 BOLT12 zap intent (kind 9737): the amount, recipient, offer, comment and target. */
@Composable
fun RenderBolt12ZapIntent(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? Bolt12ZapIntentEvent ?: return
    val amountSats = remember(event) { event.amount()?.div(1000)?.takeIf { it > 0 } }
    val offer = remember(event) { event.offer()?.toShortDisplay() }
    val recipients = remember(event) { listOfNotNull(event.recipient()) }
    val zappedEvent = remember(event) { event.zappedEvent() }
    val zappedAddress = remember(event) { event.zappedAddress()?.let { Address.parse(it) } }

    KindCard(
        icon = MaterialSymbols.Bolt,
        title = stringRes(Res.string.kc_proto_bolt12_zap_title),
        tone = KindTone.Money,
    ) {
        ZapAmount(amountSats)
        ZapRecipients(recipients, accountViewModel, nav)
        KindCardField(stringRes(Res.string.kc_proto_offer), offer, monospace = true, maxLines = 1)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
        ZapTarget(zappedEvent, zappedAddress, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/** The zap's amount in the big type of the zap receipts, so a request reads like the payment it asks for. */
@Composable
private fun ZapAmount(amountSats: Long?) {
    if (amountSats == null) return
    val formatted = remember(amountSats) { PlatformNumberFormatter().format(amountSats) }
    ActivityAmountRow(formatted, KindTone.Money.color())
}

/** The people a zap pays, under a "To" heading. */
@Composable
private fun ZapRecipients(
    recipients: List<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (recipients.isEmpty()) return
    KindCardSection(stringRes(Res.string.kc_proto_zap_to))
    KindCardUsers(recipients, accountViewModel, nav)
}

/** Quotes the event a zap points at: the `e` event first, else the `a` address. */
@Composable
private fun ZapTarget(
    eventId: HexKey?,
    address: Address?,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (eventId != null) {
        KindCardQuoteEvent(eventId, quotesLeft, backgroundColor, accountViewModel, nav)
    } else if (address != null) {
        KindCardQuoteAddress(address, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/** NIP-47 wallet service info (kind 13194): the methods, notifications and encryptions it supports. */
@Composable
fun RenderNwcInfo(baseNote: Note) {
    val event = baseNote.event as? NwcInfoEvent ?: return
    val methods = remember(event) { event.capabilities().map { readableToken(it, capitalize = false) } }
    val notifications = remember(event) { event.notificationTypes().map { readableToken(it, capitalize = false) } }
    val encryptions = remember(event) { event.encryptionSchemes().joinToString(", ").ifBlank { null } }
    val extensions = remember(event) { event.extensions() }

    KindCard(
        icon = MaterialSymbols.AccountBalanceWallet,
        title = stringRes(Res.string.kc_proto_nwc_title),
        subtitle = methods.size.takeIf { it > 0 }?.let { pluralStringRes(Res.plurals.kc_proto_nwc_method_count, it, it) },
        tone = KindTone.Money,
    ) {
        KindCardSectionChips(stringRes(Res.string.kc_proto_nwc_methods), methods)
        KindCardSectionChips(stringRes(Res.string.kc_proto_nwc_notifications), notifications)
        KindCardSectionChips(stringRes(Res.string.kc_proto_nwc_extensions), extensions)
        KindCardField(stringRes(Res.string.kc_proto_nwc_encryption), encryptions, monospace = true, maxLines = 1)
    }
}

/**
 * Marmot MLS KeyPackage, both the addressable kind 30443 and the legacy kind 443: the
 * ciphersuite it offers, the app that made it and where it can be fetched. The content is the
 * serialized KeyPackage and the extension codes are for software, so neither is shown.
 */
@Composable
fun RenderMarmotKeyPackage(baseNote: Note) {
    val event = baseNote.event as? PublishedKeyPackage ?: return
    val ciphersuite = remember(event) { event.mlsCiphersuite()?.let(::ciphersuiteShortName) }
    val version = remember(event) { event.mlsProtocolVersion() }
    // Text only, nothing is dialed: shown as published.
    val relays = remember(event) { event.allRelays()?.map { it.displayUrl() } ?: emptyList() }
    val client = remember(event) { event.clientName() }
    val isLegacy = event is LegacyKeyPackageEvent

    KindCard(
        icon = MaterialSymbols.Key,
        title = stringRes(Res.string.kc_proto_keypackage_title),
        subtitle = ciphersuite,
        tone = if (isLegacy) KindTone.Neutral else KindTone.Primary,
        status = if (isLegacy) stringRes(Res.string.kc_proto_keypackage_legacy) else null,
    ) {
        KindCardMeta(listOf(client, version?.let { stringRes(Res.string.kc_proto_mls_version_meta, it) }))
        KindCardSectionChips(stringRes(Res.string.kc_proto_relays), relays)
    }
}

/**
 * The ciphersuite as its key exchange, cipher and signature ("X25519 · AES-128-GCM · Ed25519")
 * instead of the RFC 9420 constant. An unknown code is shown as it came.
 */
private fun ciphersuiteShortName(code: String): String =
    when (MlsCiphersuite.fromCode(code)) {
        MlsCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519 -> "X25519 · AES-128-GCM · Ed25519"
        MlsCiphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 -> "P-256 · AES-128-GCM · P-256"
        MlsCiphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_ED25519 -> "X25519 · ChaCha20-Poly1305 · Ed25519"
        MlsCiphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_ED448 -> "X448 · AES-256-GCM · Ed448"
        MlsCiphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 -> "P-521 · AES-256-GCM · P-521"
        MlsCiphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_ED448 -> "X448 · ChaCha20-Poly1305 · Ed448"
        MlsCiphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 -> "P-384 · AES-256-GCM · P-384"
        null -> code
    }

/** NIP-60 mint quote (kind 7374): the quote id is encrypted, the mint it was opened at is public. */
@Composable
fun RenderCashuMintQuote(baseNote: Note) {
    val event = baseNote.event as? CashuMintQuoteEvent ?: return
    val mint = remember(event) { event.mint()?.let { HttpUrlFormatter.displayUrl(it) } }

    KindCard(
        icon = MaterialSymbols.Paid,
        title = stringRes(Res.string.kc_proto_cashu_mint_quote_title),
        subtitle = mint,
        tone = KindTone.Money,
        status = stringRes(Res.string.kind_card_private_pill),
        statusTone = KindTone.Neutral,
    )
}

/**
 * NIP-60 spending history (kind 7376): the amount and direction are encrypted; the redeemed
 * nutzaps and their senders are public.
 */
@Composable
fun RenderCashuSpendingHistory(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CashuSpendingHistoryEvent ?: return
    val senders = remember(event) { event.nutzapSenders().distinct() }
    val redeemed = remember(event) { event.redeemedReferences().size }

    KindCard(
        icon = MaterialSymbols.History,
        title = stringRes(Res.string.kc_proto_cashu_history_title),
        subtitle = redeemed.takeIf { it > 0 }?.let { pluralStringRes(Res.plurals.kc_proto_cashu_redeemed_count, it, it) },
        tone = KindTone.Money,
        status = stringRes(Res.string.kind_card_private_pill),
        statusTone = KindTone.Neutral,
    ) {
        if (senders.isNotEmpty()) {
            KindCardSection(stringRes(Res.string.kc_proto_nutzap_senders))
            KindCardUsers(senders, accountViewModel, nav)
        }
    }
}

/**
 * NIP-4E encryption key list (kind 10044): the key peers encrypt to. The first `n` is the current
 * key; any others are older ones kept for decrypting past messages, shown only as a count.
 */
@Composable
fun RenderEncryptionKeyList(baseNote: Note) {
    val event = baseNote.event as? EncryptionKeyListEvent ?: return
    val keys = remember(event) { event.keys().map { it.pubkey } }
    val current = remember(keys) { keys.firstOrNull()?.toShortDisplay() }
    val older = keys.size - 1

    KindCard(
        icon = MaterialSymbols.Key,
        title = stringRes(Res.string.kc_proto_encryption_keys_title),
        tone = KindTone.Neutral,
        body =
            if (current == null) {
                null
            } else {
                {
                    KindCardField(stringRes(Res.string.kc_proto_encryption_current), current, monospace = true, maxLines = 1)
                    if (older > 0) {
                        KindCardMeta(listOf(pluralStringRes(Res.plurals.kc_proto_encryption_older_count, older, older)))
                    }
                }
            },
    )
}

/**
 * NIP-4E encryption key request (kind 4454): a new device of this account asking for the
 * encryption key. The device's name and the code to compare before approving are what matter.
 */
@Composable
fun RenderEncryptionKeyRequest(baseNote: Note) {
    val event = baseNote.event as? EncryptionKeyRequestEvent ?: return
    val client = remember(event) { event.clientName() }
    val code = remember(event) { event.authorizationCode() }

    KindCard(
        icon = MaterialSymbols.PhonelinkLock,
        title = stringRes(Res.string.kc_proto_key_request_title),
        subtitle = client,
        tone = KindTone.Neutral,
        body =
            if (code == null) {
                null
            } else {
                { KindCardField(stringRes(Res.string.kc_proto_key_request_code), code, monospace = true, maxLines = 1) }
            },
    )
}
