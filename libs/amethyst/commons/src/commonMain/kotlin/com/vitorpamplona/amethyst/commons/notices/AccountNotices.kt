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
package com.vitorpamplona.amethyst.commons.notices

import com.vitorpamplona.amethyst.commons.actions.ConcordPinOutcome

/** Why the signer refused to sign or decrypt. */
sealed interface SignerNotice : UserNotice {
    /** The account has no private key (npub login). */
    data object ReadOnly : SignerNotice

    data object UnauthorizedDecryption : SignerNotice

    /** The external signer app is gone. */
    data object SignerNotFound : SignerNotice

    /** The signer failed in a way it doesn't classify; [cause] is shown as details. */
    data class Failed(
        val cause: Throwable,
    ) : SignerNotice
}

/**
 * An action failed on data it could not use (a malformed event, list or reply from another app
 * or relay), not on the signer; [cause] is shown as details.
 */
data class ActionFailed(
    val cause: Throwable,
) : UserNotice

/** The kind 10030 emoji list could not be updated. */
sealed interface EmojiListNotice : UserNotice {
    /** The list has not arrived from any relay, so writing it now could replace it. */
    data object NotLoaded : EmojiListNotice
}

/** A note action the user asked for that can't be done. */
sealed interface NoteActionNotice : UserNotice {
    data object CannotQuoteDraft : NoteActionNotice

    data object ReadOnlyCannotBoost : NoteActionNotice
}

/** A proof-of-work mined post that failed to sign or broadcast after the composer closed. */
data class PowPublishFailed(
    val kind: Int,
    val willRetryOnRestart: Boolean,
    val message: String?,
) : UserNotice

sealed interface ConcordNotice : UserNotice {
    /** An honored Kick removed us from a community (CORD-04 §6). */
    data class Kicked(
        val communityName: String?,
    ) : ConcordNotice

    /** A pin write that did not publish, for an [outcome] the user has to hear about. */
    data class PinFailed(
        val outcome: ConcordPinOutcome,
    ) : ConcordNotice

    data object DissolveFailed : ConcordNotice

    data object ChannelKeyRotated : ConcordNotice

    data object ChannelKeyRotationFailed : ConcordNotice

    data object RolesUpdateFailed : ConcordNotice

    data object KickFailed : ConcordNotice
}

/** A plain BOLT12 offer payment over NWC (not a zap). */
sealed interface MarmotNotice : UserNotice {
    /** Every Marmot group, key and message on this device was wiped. */
    data object ResetDone : MarmotNotice

    /** The wipe stopped with an error; [message] is its detail. */
    data class ResetFailed(
        val message: String?,
    ) : MarmotNotice

    /**
     * A custom emoji could not be attached as an encrypted picture (its image
     * could not be fetched or no blob server took it), so it went out as its
     * `:shortcode:` text.
     */
    data object EmojiNotAttached : MarmotNotice

    /**
     * The group moved to a new epoch while a file was uploading, so the message carrying it was not
     * sent: no member could open media encrypted under the old epoch's key. It has to be sent again.
     */
    data object MediaEpochMoved : MarmotNotice
}

sealed interface Bolt12OfferNotice : UserNotice {
    data object PaymentSent : Bolt12OfferNotice

    data class PaymentFailed(
        val detail: String,
    ) : Bolt12OfferNotice
}

sealed interface NutzapNotice : UserNotice {
    /** A public kind:9321 on a private rumor would leak its id. */
    data object PrivateNote : NutzapNotice

    data object NoRecipient : NutzapNotice

    data object NoEvent : NutzapNotice

    data class MintFailed(
        val detail: String,
    ) : NutzapNotice
}

/** Redeeming a Cashu token to the account's own lightning address. */
sealed interface CashuRedeemNotice : UserNotice {
    data class Redeemed(
        val amountSats: Long,
        val feeSats: Int,
    ) : CashuRedeemNotice

    data class Failed(
        val detail: String?,
    ) : CashuRedeemNotice

    /** The mint URL was refused before any request went out. */
    data class UnsafeMintUrl(
        val detail: String?,
    ) : CashuRedeemNotice

    data class NoLightningAddress(
        val userName: String,
    ) : CashuRedeemNotice
}
