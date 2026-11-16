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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12Bech32
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12Offer
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12PayerProof
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.builder.Bolt12ZapBuilder
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.intent.Bolt12ZapIntentEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.verify.Bolt12ZapValidation
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.verify.Bolt12ZapValidator
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.utils.Hex

/**
 * NIP-B1 BOLT12 zap building, decoding, and validation — the shared, UI-free
 * surface a non-Android caller (amy CLI, interop harnesses) drives.
 *
 * Assembly-only: it re-exposes [Bolt12ZapBuilder] / [Bolt12ZapValidator] and the
 * BOLT12 codecs. Obtaining a settled `lnp` payer proof (the wallet's job over
 * NIP-47/nwc#2) lives outside these builders — a caller supplies the proof.
 * Pattern matches [ZapActions].
 */
object Bolt12ZapActions {
    fun satsToMillisats(sats: Long): Long = sats * 1000L

    /** The canonical raw offer if [raw] is a well-formed BOLT12 offer (`lno1…`), else null. */
    fun canonicalOfferOrNull(raw: String): String? {
        val canonical = Bolt12Bech32.canonicalize(raw)
        return if (Bolt12Bech32.isOffer(canonical)) canonical else null
    }

    /**
     * Decode a BOLT12 offer (`lno1…`) to its interesting fields, or null when unparseable.
     * Individual field reads degrade to null on malformed values (e.g. an amount longer
     * than 8 bytes), so a structurally-valid offer still decodes without its bad field;
     * the extraction stays guarded as defense-in-depth against any future throwing read.
     */
    fun decodeOffer(raw: String): Map<String, Any?>? {
        val offer = Bolt12Offer.parse(raw) ?: return null
        return runCatching {
            buildMap {
                put("canonical", Bolt12Bech32.canonicalize(raw))
                offer.amount()?.let { put("amount_msat", it) }
                offer.currency()?.let { put("currency", it) }
                offer.description()?.let { put("description", it) }
                offer.issuerId()?.let { put("issuer_id", Hex.encode(it)) }
                put("has_paths", offer.hasPaths())
            }
        }.getOrNull()
    }

    /**
     * Decode a BOLT12 payer proof (`lnp1…`) to its interesting fields, or null when
     * unparseable. Guarded like [decodeOffer] against a malformed-but-encoded field.
     */
    fun decodeProof(raw: String): Map<String, Any?>? {
        val proof = Bolt12PayerProof.parse(raw) ?: return null
        return runCatching {
            buildMap {
                put("has_all_required_fields", proof.hasAllCryptoFields())
                put("compressed", proof.isCompressed())
                proof.invreqPayerNote()?.let { put("invreq_payer_note", it) }
                proof.invreqPayerId()?.let { put("invreq_payer_id", Hex.encode(it)) }
                proof.invoiceAmount()?.let { put("invoice_amount_msat", it) }
                proof.invoicePaymentHash()?.let { put("invoice_payment_hash", Hex.encode(it)) }
                proof.invoiceNodeId()?.let { put("invoice_node_id", Hex.encode(it)) }
                proof.offerIssuerId()?.let { put("offer_issuer_id", Hex.encode(it)) }
            }
        }.getOrNull()
    }

    /** The value a payer MUST put in the BOLT12 `invreq_payer_note` to bind a payment to [intent]. */
    fun payerNote(intent: Bolt12ZapIntentEvent): String = Bolt12ZapBuilder.payerNote(intent)

    /** Sign a kind:9737 zap intent targeting [recipientPubKey]'s profile. */
    suspend fun buildProfileIntent(
        signer: NostrSigner,
        recipientPubKey: HexKey,
        amountMillisats: Long,
        offer: String,
        comment: String = "",
    ): Bolt12ZapIntentEvent = Bolt12ZapBuilder.buildProfileIntent(signer, recipientPubKey, amountMillisats, offer, comment)

    /** Sign a kind:9737 zap intent targeting a specific [zappedEvent]. */
    suspend fun buildEventIntent(
        signer: NostrSigner,
        recipientPubKey: HexKey,
        amountMillisats: Long,
        offer: String,
        zappedEvent: Event,
        comment: String = "",
    ): Bolt12ZapIntentEvent = Bolt12ZapBuilder.buildIntent(signer, recipientPubKey, amountMillisats, offer, EventHintBundle(zappedEvent), comment)

    /**
     * Wrap a signed [intent] and a settled [payerProof] into a signed kind:9736 zap.
     * When [anonymous], [signer] MUST be the same ephemeral key that signed [intent].
     */
    suspend fun buildZap(
        signer: NostrSigner,
        intent: Bolt12ZapIntentEvent,
        payerProof: String,
        anonymous: Boolean = false,
    ): Bolt12ZapEvent = Bolt12ZapBuilder.buildZap(signer, intent, payerProof, anonymous)

    /** Validate a kind:9736 BOLT12 zap (structure + intent match + payer-proof binding + crypto). */
    fun validate(zap: Bolt12ZapEvent): Bolt12ZapValidation = Bolt12ZapValidator().validate(zap)

    /** Sign a kind:10058 BOLT12 offer list publishing [offers] (canonical `lno1…` strings). */
    suspend fun buildOfferList(
        signer: NostrSigner,
        offers: List<String>,
    ): Bolt12OfferListEvent = Bolt12OfferListEvent.create(offers, signer)
}
