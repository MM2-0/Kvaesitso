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
package com.vitorpamplona.amethyst.commons.notices.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.actions.ConcordPinOutcome
import com.vitorpamplona.amethyst.commons.model.Bolt12ZapFailure
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcSignerState
import com.vitorpamplona.amethyst.commons.notices.ActionFailed
import com.vitorpamplona.amethyst.commons.notices.Bolt12OfferNotice
import com.vitorpamplona.amethyst.commons.notices.CashuRedeemNotice
import com.vitorpamplona.amethyst.commons.notices.ClinkDebitFailed
import com.vitorpamplona.amethyst.commons.notices.ConcordNotice
import com.vitorpamplona.amethyst.commons.notices.EmojiListNotice
import com.vitorpamplona.amethyst.commons.notices.InvoiceNotice
import com.vitorpamplona.amethyst.commons.notices.LnurlFailureDetail
import com.vitorpamplona.amethyst.commons.notices.MarmotNotice
import com.vitorpamplona.amethyst.commons.notices.NoWalletFound
import com.vitorpamplona.amethyst.commons.notices.NoteActionNotice
import com.vitorpamplona.amethyst.commons.notices.NutzapNotice
import com.vitorpamplona.amethyst.commons.notices.NwcFailure
import com.vitorpamplona.amethyst.commons.notices.PowPublishFailed
import com.vitorpamplona.amethyst.commons.notices.ResolvedNotice
import com.vitorpamplona.amethyst.commons.notices.SignerNotice
import com.vitorpamplona.amethyst.commons.notices.UploadNotice
import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.amethyst.commons.notices.UserNoticeResolver
import com.vitorpamplona.amethyst.commons.notices.V4VNotice
import com.vitorpamplona.amethyst.commons.notices.ZapNotice
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.action_failed_description
import com.vitorpamplona.amethyst.commons.resources.action_failed_title
import com.vitorpamplona.amethyst.commons.resources.bolt12_offers
import com.vitorpamplona.amethyst.commons.resources.bolt12_payment_failed
import com.vitorpamplona.amethyst.commons.resources.bolt12_payment_sent
import com.vitorpamplona.amethyst.commons.resources.bolt12_zap_error
import com.vitorpamplona.amethyst.commons.resources.bolt12_zap_invalid_receipt
import com.vitorpamplona.amethyst.commons.resources.bolt12_zap_paid_no_receipt
import com.vitorpamplona.amethyst.commons.resources.callback_url_not_found_in_the_user_s_lightning_address_server_configuration_with_user
import com.vitorpamplona.amethyst.commons.resources.cashu_failed_redemption
import com.vitorpamplona.amethyst.commons.resources.cashu_failed_redemption_explainer_error_msg
import com.vitorpamplona.amethyst.commons.resources.cashu_successful_redemption
import com.vitorpamplona.amethyst.commons.resources.cashu_successful_redemption_explainer
import com.vitorpamplona.amethyst.commons.resources.cashu_unsafe_mint_url
import com.vitorpamplona.amethyst.commons.resources.cashu_unsafe_mint_url_explainer
import com.vitorpamplona.amethyst.commons.resources.clink_debit_no_response
import com.vitorpamplona.amethyst.commons.resources.concord_channel_rotate_key
import com.vitorpamplona.amethyst.commons.resources.concord_channel_rotate_key_done
import com.vitorpamplona.amethyst.commons.resources.concord_channel_rotate_key_failed
import com.vitorpamplona.amethyst.commons.resources.concord_dissolve_community
import com.vitorpamplona.amethyst.commons.resources.concord_dissolve_failed
import com.vitorpamplona.amethyst.commons.resources.concord_kicked_message
import com.vitorpamplona.amethyst.commons.resources.concord_kicked_message_unnamed
import com.vitorpamplona.amethyst.commons.resources.concord_kicked_title
import com.vitorpamplona.amethyst.commons.resources.concord_members_kick_failed
import com.vitorpamplona.amethyst.commons.resources.concord_members_roles_failed
import com.vitorpamplona.amethyst.commons.resources.concord_members_roles_title
import com.vitorpamplona.amethyst.commons.resources.concord_members_title
import com.vitorpamplona.amethyst.commons.resources.concord_pin_failed_generic
import com.vitorpamplona.amethyst.commons.resources.concord_pin_failed_message
import com.vitorpamplona.amethyst.commons.resources.concord_pin_failed_title
import com.vitorpamplona.amethyst.commons.resources.concord_pin_failed_too_large
import com.vitorpamplona.amethyst.commons.resources.concord_pin_failed_too_many
import com.vitorpamplona.amethyst.commons.resources.concord_pin_failed_unavailable
import com.vitorpamplona.amethyst.commons.resources.could_not_assemble_lnurl_from_lightning_address_check_the_user_s_setup
import com.vitorpamplona.amethyst.commons.resources.could_not_fetch_invoice_from_details
import com.vitorpamplona.amethyst.commons.resources.could_not_resolve_check_if_you_are_connected_if_the_server_is_up_and_if_the_lightning_address_is_correct_exception
import com.vitorpamplona.amethyst.commons.resources.draft_note
import com.vitorpamplona.amethyst.commons.resources.emoji_list_not_loaded_description
import com.vitorpamplona.amethyst.commons.resources.emoji_list_not_loaded_title
import com.vitorpamplona.amethyst.commons.resources.emoji_packs_title
import com.vitorpamplona.amethyst.commons.resources.error_dialog_pay_invoice_error
import com.vitorpamplona.amethyst.commons.resources.error_dialog_zap_error
import com.vitorpamplona.amethyst.commons.resources.error_parsing_error_message
import com.vitorpamplona.amethyst.commons.resources.error_parsing_json_from_lightning_address_check_the_user_s_lightning_setup_with_user
import com.vitorpamplona.amethyst.commons.resources.error_parsing_json_from_lightning_address_s_invoice_fetch_check_the_user_s_lightning_setup_with_user
import com.vitorpamplona.amethyst.commons.resources.error_unable_to_fetch_invoice
import com.vitorpamplona.amethyst.commons.resources.incorrect_invoice_amount_sats_from_it_should_have_been
import com.vitorpamplona.amethyst.commons.resources.it_s_not_possible_to_quote_to_a_draft_note
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_boost_posts
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_sign_events
import com.vitorpamplona.amethyst.commons.resources.marmot_emoji_upload_failed
import com.vitorpamplona.amethyst.commons.resources.marmot_group_default_name
import com.vitorpamplona.amethyst.commons.resources.marmot_media_epoch_moved
import com.vitorpamplona.amethyst.commons.resources.missing_lud16
import com.vitorpamplona.amethyst.commons.resources.no_lightning_address_set
import com.vitorpamplona.amethyst.commons.resources.no_wallet_found
import com.vitorpamplona.amethyst.commons.resources.nutzap_failed_no_event
import com.vitorpamplona.amethyst.commons.resources.nutzap_failed_no_recipient
import com.vitorpamplona.amethyst.commons.resources.nutzap_failed_private_note
import com.vitorpamplona.amethyst.commons.resources.nutzap_failed_title
import com.vitorpamplona.amethyst.commons.resources.podcast_value_error_title
import com.vitorpamplona.amethyst.commons.resources.podcast_value_keysend_not_supported
import com.vitorpamplona.amethyst.commons.resources.podcast_value_keysend_requires_nwc
import com.vitorpamplona.amethyst.commons.resources.podcast_value_no_recipients
import com.vitorpamplona.amethyst.commons.resources.pow_publish_failed
import com.vitorpamplona.amethyst.commons.resources.pow_publish_failed_retry
import com.vitorpamplona.amethyst.commons.resources.pow_settings_title
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.resources.reset_marmot_failure
import com.vitorpamplona.amethyst.commons.resources.reset_marmot_state
import com.vitorpamplona.amethyst.commons.resources.reset_marmot_success
import com.vitorpamplona.amethyst.commons.resources.signer_illegal_state_exception_description
import com.vitorpamplona.amethyst.commons.resources.signer_not_found_exception
import com.vitorpamplona.amethyst.commons.resources.signer_not_found_exception_description
import com.vitorpamplona.amethyst.commons.resources.the_receiver_s_lightning_service_at_is_not_available_it_was_calculated_from_the_lightning_address_error_check_if_the_server_is_up_and_if_the_lightning_address_is_correct
import com.vitorpamplona.amethyst.commons.resources.unable_to_create_a_lightning_invoice_before_sending_the_zap_element_pr_not_found_in_the_resulting_json_with_user
import com.vitorpamplona.amethyst.commons.resources.unable_to_create_a_lightning_invoice_before_sending_the_zap_the_receiver_s_lightning_wallet_sent_the_following_error
import com.vitorpamplona.amethyst.commons.resources.unable_to_create_a_lightning_invoice_before_sending_the_zap_the_receiver_s_lightning_wallet_sent_the_following_error_with_user
import com.vitorpamplona.amethyst.commons.resources.unauthorized_exception
import com.vitorpamplona.amethyst.commons.resources.unauthorized_exception_description
import com.vitorpamplona.amethyst.commons.resources.upload_other_server_message
import com.vitorpamplona.amethyst.commons.resources.upload_other_server_message_with_reason
import com.vitorpamplona.amethyst.commons.resources.upload_other_server_title
import com.vitorpamplona.amethyst.commons.resources.user_does_not_have_a_lightning_address_setup_to_receive_sats
import com.vitorpamplona.amethyst.commons.resources.user_x_does_not_have_a_lightning_address_setup_to_receive_sats
import com.vitorpamplona.amethyst.commons.resources.video_compression_failed_message
import com.vitorpamplona.amethyst.commons.resources.video_compression_failed_message_with_reason
import com.vitorpamplona.amethyst.commons.resources.video_compression_failed_title
import com.vitorpamplona.amethyst.commons.resources.video_compression_hdr_message
import com.vitorpamplona.amethyst.commons.resources.video_compression_hdr_title
import com.vitorpamplona.amethyst.commons.resources.video_compression_hevc_unavailable_message
import com.vitorpamplona.amethyst.commons.resources.video_compression_hevc_unavailable_title
import com.vitorpamplona.amethyst.commons.resources.video_compression_not_smaller_message
import com.vitorpamplona.amethyst.commons.resources.video_compression_not_smaller_title
import com.vitorpamplona.amethyst.commons.resources.wallet_connect_no_response_error
import com.vitorpamplona.amethyst.commons.resources.wallet_connect_pay_invoice_error_error
import com.vitorpamplona.amethyst.commons.resources.wallet_connect_unreadable_response_error
import com.vitorpamplona.amethyst.commons.service.HttpStatusMessages
import com.vitorpamplona.amethyst.commons.service.pow.powKindLabelRes
import com.vitorpamplona.amethyst.commons.ui.components.InformationDialog
import com.vitorpamplona.amethyst.commons.ui.components.toasts.ToastSeverity
import com.vitorpamplona.amethyst.commons.ui.loadPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/** A piece of notice text: a literal, or a (plural) string resource whose arguments are texts too. */
sealed interface NoticeTxt {
    class Raw(
        val text: String,
    ) : NoticeTxt

    class Str(
        val res: StringResource,
        val args: List<NoticeTxt> = emptyList(),
    ) : NoticeTxt

    class Plural(
        val res: PluralStringResource,
        val count: Int,
        val args: List<NoticeTxt> = emptyList(),
    ) : NoticeTxt
}

/** The localized parts of a [UserNotice]; [cause] is shown as expandable details. */
class NoticeText(
    val title: StringResource,
    val message: NoticeTxt,
    val cause: Throwable? = null,
)

private fun raw(text: String?) = NoticeTxt.Raw(text.orEmpty())

private fun str(
    res: StringResource,
    vararg args: NoticeTxt,
) = NoticeTxt.Str(res, args.toList())

private fun text(
    title: StringResource,
    message: StringResource,
) = NoticeText(title, str(message))

private fun LnurlFailureDetail.txt(): NoticeTxt =
    when (this) {
        is LnurlFailureDetail.ServerMessage -> raw(text)
        is LnurlFailureDetail.HttpStatus ->
            HttpStatusMessages.resourceIdFor(code)?.let { str(it) }
                ?: raw(reason.ifBlank { null } ?: code.toString())
    }

/** The sentence for a NIP-47 [NwcFailure], shared by every payment surface. */
fun NwcFailure.txt(): NoticeTxt =
    when (this) {
        NwcFailure.Unreadable -> str(Res.string.wallet_connect_unreadable_response_error)
        is NwcFailure.Refused -> message?.let { raw(it) } ?: str(Res.string.error_parsing_error_message)
        NwcFailure.TimedOut ->
            NoticeTxt.Plural(
                Res.plurals.wallet_connect_no_response_error,
                NwcSignerState.NWC_RESPONSE_TIMEOUT_SECONDS,
                listOf(raw(NwcSignerState.NWC_RESPONSE_TIMEOUT_SECONDS.toString())),
            )
    }

/** The one place a [UserNotice] becomes words. Exhaustive: a new notice must be given its text here. */
fun UserNotice.text(): NoticeText =
    when (this) {
        SignerNotice.ReadOnly -> text(Res.string.read_only_user, Res.string.login_with_a_private_key_to_be_able_to_sign_events)
        SignerNotice.UnauthorizedDecryption -> text(Res.string.unauthorized_exception, Res.string.unauthorized_exception_description)
        SignerNotice.SignerNotFound -> text(Res.string.signer_not_found_exception, Res.string.signer_not_found_exception_description)
        is SignerNotice.Failed ->
            NoticeText(Res.string.signer_not_found_exception, str(Res.string.signer_illegal_state_exception_description), cause)
        is ActionFailed -> NoticeText(Res.string.action_failed_title, str(Res.string.action_failed_description), cause)

        EmojiListNotice.NotLoaded -> text(Res.string.emoji_list_not_loaded_title, Res.string.emoji_list_not_loaded_description)

        NoteActionNotice.CannotQuoteDraft -> text(Res.string.draft_note, Res.string.it_s_not_possible_to_quote_to_a_draft_note)
        NoteActionNotice.ReadOnlyCannotBoost -> text(Res.string.read_only_user, Res.string.login_with_a_private_key_to_be_able_to_boost_posts)

        is UploadNotice.VideoCompressionFailed ->
            NoticeText(
                Res.string.video_compression_failed_title,
                reason?.let { str(Res.string.video_compression_failed_message_with_reason, raw(it)) } ?: str(Res.string.video_compression_failed_message),
            )
        UploadNotice.HevcUnavailable -> text(Res.string.video_compression_hevc_unavailable_title, Res.string.video_compression_hevc_unavailable_message)
        UploadNotice.HdrSource -> text(Res.string.video_compression_hdr_title, Res.string.video_compression_hdr_message)
        UploadNotice.CompressionNotSmaller -> text(Res.string.video_compression_not_smaller_title, Res.string.video_compression_not_smaller_message)
        is UploadNotice.UploadedToAnotherServer ->
            NoticeText(
                Res.string.upload_other_server_title,
                reason?.let { str(Res.string.upload_other_server_message_with_reason, raw(chosenServer), raw(usedServer), raw(it)) }
                    ?: str(Res.string.upload_other_server_message, raw(chosenServer), raw(usedServer)),
            )

        is PowPublishFailed -> {
            val kindLabel = str(powKindLabelRes(kind))
            if (willRetryOnRestart) {
                NoticeText(Res.string.pow_settings_title, str(Res.string.pow_publish_failed_retry, kindLabel))
            } else {
                NoticeText(Res.string.pow_settings_title, str(Res.string.pow_publish_failed, kindLabel, raw(message)))
            }
        }

        is ConcordNotice.Kicked -> {
            val name = communityName?.takeIf { it.isNotBlank() }
            if (name != null) {
                NoticeText(Res.string.concord_kicked_title, str(Res.string.concord_kicked_message, raw(name)))
            } else {
                text(Res.string.concord_kicked_title, Res.string.concord_kicked_message_unnamed)
            }
        }
        is ConcordNotice.PinFailed ->
            text(
                Res.string.concord_pin_failed_title,
                when (outcome) {
                    ConcordPinOutcome.LIST_UNAVAILABLE -> Res.string.concord_pin_failed_unavailable
                    ConcordPinOutcome.TOO_MANY_PINS -> Res.string.concord_pin_failed_too_many
                    ConcordPinOutcome.TOO_LARGE -> Res.string.concord_pin_failed_too_large
                    ConcordPinOutcome.MESSAGE_UNAVAILABLE, ConcordPinOutcome.UNVERIFIABLE -> Res.string.concord_pin_failed_message
                    else -> Res.string.concord_pin_failed_generic
                },
            )
        ConcordNotice.DissolveFailed -> text(Res.string.concord_dissolve_community, Res.string.concord_dissolve_failed)
        ConcordNotice.ChannelKeyRotated -> text(Res.string.concord_channel_rotate_key, Res.string.concord_channel_rotate_key_done)
        ConcordNotice.ChannelKeyRotationFailed -> text(Res.string.concord_channel_rotate_key, Res.string.concord_channel_rotate_key_failed)
        ConcordNotice.RolesUpdateFailed -> text(Res.string.concord_members_roles_title, Res.string.concord_members_roles_failed)
        ConcordNotice.KickFailed -> text(Res.string.concord_members_title, Res.string.concord_members_kick_failed)

        Bolt12OfferNotice.PaymentSent -> text(Res.string.bolt12_offers, Res.string.bolt12_payment_sent)
        MarmotNotice.ResetDone -> text(Res.string.reset_marmot_state, Res.string.reset_marmot_success)
        is MarmotNotice.ResetFailed -> NoticeText(Res.string.reset_marmot_state, str(Res.string.reset_marmot_failure, raw(message)))
        MarmotNotice.EmojiNotAttached -> text(Res.string.emoji_packs_title, Res.string.marmot_emoji_upload_failed)
        MarmotNotice.MediaEpochMoved -> text(Res.string.marmot_group_default_name, Res.string.marmot_media_epoch_moved)

        is Bolt12OfferNotice.PaymentFailed -> NoticeText(Res.string.bolt12_offers, str(Res.string.bolt12_payment_failed, raw(detail)))

        NutzapNotice.PrivateNote -> text(Res.string.nutzap_failed_title, Res.string.nutzap_failed_private_note)
        NutzapNotice.NoRecipient -> text(Res.string.nutzap_failed_title, Res.string.nutzap_failed_no_recipient)
        NutzapNotice.NoEvent -> text(Res.string.nutzap_failed_title, Res.string.nutzap_failed_no_event)
        is NutzapNotice.MintFailed -> NoticeText(Res.string.nutzap_failed_title, raw(detail))

        is CashuRedeemNotice.Redeemed ->
            NoticeText(
                Res.string.cashu_successful_redemption,
                str(Res.string.cashu_successful_redemption_explainer, raw(amountSats.toString()), raw(feeSats.toString())),
            )
        is CashuRedeemNotice.Failed ->
            NoticeText(Res.string.cashu_failed_redemption, str(Res.string.cashu_failed_redemption_explainer_error_msg, raw(detail)))
        is CashuRedeemNotice.UnsafeMintUrl ->
            NoticeText(Res.string.cashu_unsafe_mint_url, str(Res.string.cashu_unsafe_mint_url_explainer, raw(detail)))
        is CashuRedeemNotice.NoLightningAddress ->
            NoticeText(
                Res.string.no_lightning_address_set,
                str(Res.string.user_x_does_not_have_a_lightning_address_setup_to_receive_sats, raw(userName)),
            )

        is InvoiceNotice -> NoticeText(Res.string.error_unable_to_fetch_invoice, invoiceMessage())

        is ClinkDebitFailed ->
            NoticeText(Res.string.error_dialog_pay_invoice_error, detail?.let { raw(it) } ?: str(Res.string.clink_debit_no_response))
        NoWalletFound -> text(Res.string.error_dialog_zap_error, Res.string.no_wallet_found)

        is ZapNotice.MissingLnAddress ->
            NoticeText(
                Res.string.missing_lud16,
                if (userName != null) {
                    str(Res.string.user_x_does_not_have_a_lightning_address_setup_to_receive_sats, raw(userName))
                } else {
                    str(Res.string.user_does_not_have_a_lightning_address_setup_to_receive_sats)
                },
            )
        is ZapNotice.PayInvoiceFailed ->
            NoticeText(
                Res.string.error_dialog_pay_invoice_error,
                if (failure == NwcFailure.TimedOut) failure.txt() else str(Res.string.wallet_connect_pay_invoice_error_error, failure.txt()),
            )
        is ZapNotice.Bolt12ReceiptFailed ->
            text(
                Res.string.bolt12_zap_error,
                when (failure) {
                    Bolt12ZapFailure.PaidNoReceipt -> Res.string.bolt12_zap_paid_no_receipt
                    Bolt12ZapFailure.InvalidReceipt -> Res.string.bolt12_zap_invalid_receipt
                },
            )
        is ZapNotice.Bolt12PaymentFailed -> NoticeText(Res.string.bolt12_zap_error, str(Res.string.bolt12_payment_failed, raw(detail)))
        ZapNotice.Bolt12PaymentTimedOut -> NoticeText(Res.string.bolt12_zap_error, str(Res.string.bolt12_payment_failed, NwcFailure.TimedOut.txt()))
        is ZapNotice.FallbackFailed -> NoticeText(Res.string.error_dialog_zap_error, raw(message))

        V4VNotice.NoRecipients -> text(Res.string.podcast_value_error_title, Res.string.podcast_value_no_recipients)
        V4VNotice.KeysendNotSupported -> text(Res.string.podcast_value_error_title, Res.string.podcast_value_keysend_not_supported)
        V4VNotice.KeysendRequiresNwc -> text(Res.string.podcast_value_error_title, Res.string.podcast_value_keysend_requires_nwc)
        is V4VNotice.PaymentFailed -> NoticeText(Res.string.error_dialog_pay_invoice_error, failure.txt())
    }

private fun InvoiceNotice.invoiceMessage(): NoticeTxt =
    when (this) {
        is InvoiceNotice.CannotAssembleLnurl ->
            str(Res.string.could_not_assemble_lnurl_from_lightning_address_check_the_user_s_setup, raw(lnAddress))
        is InvoiceNotice.ServiceUnavailable ->
            str(
                Res.string.the_receiver_s_lightning_service_at_is_not_available_it_was_calculated_from_the_lightning_address_error_check_if_the_server_is_up_and_if_the_lightning_address_is_correct,
                raw(url),
                raw(lnAddress),
                detail.txt(),
            )
        is InvoiceNotice.CannotResolve ->
            str(
                Res.string.could_not_resolve_check_if_you_are_connected_if_the_server_is_up_and_if_the_lightning_address_is_correct_exception,
                raw(url),
                raw(lnAddress),
                raw(cause),
            )
        is InvoiceNotice.CannotFetch -> str(Res.string.could_not_fetch_invoice_from_details, raw(callback), detail.txt())
        is InvoiceNotice.AddressJsonInvalid ->
            str(Res.string.error_parsing_json_from_lightning_address_check_the_user_s_lightning_setup_with_user, raw(lnAddress))
        is InvoiceNotice.CallbackNotFound ->
            str(Res.string.callback_url_not_found_in_the_user_s_lightning_address_server_configuration_with_user, raw(lnAddress))
        is InvoiceNotice.InvoiceJsonInvalid ->
            str(Res.string.error_parsing_json_from_lightning_address_s_invoice_fetch_check_the_user_s_lightning_setup_with_user, raw(lnAddress))
        is InvoiceNotice.WalletRefused ->
            str(
                Res.string.unable_to_create_a_lightning_invoice_before_sending_the_zap_the_receiver_s_lightning_wallet_sent_the_following_error_with_user,
                raw(lnAddress),
                raw(reason),
            )
        is InvoiceNotice.InvoiceMissing ->
            str(
                Res.string.unable_to_create_a_lightning_invoice_before_sending_the_zap_element_pr_not_found_in_the_resulting_json_with_user,
                raw(lnAddress),
            )
        is InvoiceNotice.WrongAmount ->
            str(
                Res.string.incorrect_invoice_amount_sats_from_it_should_have_been,
                raw(invoiceSats.toString()),
                raw(lnAddress),
                raw(expectedSats.toString()),
            )
        is InvoiceNotice.ReceiverFailed ->
            str(
                Res.string.unable_to_create_a_lightning_invoice_before_sending_the_zap_the_receiver_s_lightning_wallet_sent_the_following_error,
                raw(message),
            )
        is InvoiceNotice.Unexpected -> message?.let { raw(it) } ?: str(Res.string.error_parsing_error_message)
    }

suspend fun NoticeTxt.loadText(): String =
    when (this) {
        is NoticeTxt.Raw -> text
        is NoticeTxt.Str -> if (args.isEmpty()) loadStringRes(res) else loadStringRes(res, *args.map { it.loadText() }.toTypedArray())
        is NoticeTxt.Plural -> loadPluralStringRes(res, count, *args.map { it.loadText() }.toTypedArray())
    }

@Composable
fun NoticeTxt.renderText(): String =
    when (this) {
        is NoticeTxt.Raw -> text
        is NoticeTxt.Str -> if (args.isEmpty()) stringRes(res) else stringRes(res, *args.map { it.renderText() }.toTypedArray())
        is NoticeTxt.Plural -> pluralStringRes(res, count, *args.map { it.renderText() }.toTypedArray())
    }

/** Resolves notices for headless code that hands plain text to its callers. */
object ResUserNoticeResolver : UserNoticeResolver {
    override suspend fun resolve(notice: UserNotice): ResolvedNotice {
        val text = notice.text()
        return ResolvedNotice(loadStringRes(text.title), text.message.loadText())
    }
}

/** Shows a [UserNotice] the way the toast dialogs show any other message. */
@Composable
fun NoticeDialog(
    notice: UserNotice,
    severity: ToastSeverity = ToastSeverity.WARNING,
    onDismiss: () -> Unit,
) {
    val text = remember(notice) { notice.text() }
    val moreInfo = remember(text) { text.cause?.let { "\n" + it.stackTraceToString() } }
    InformationDialog(
        title = stringRes(text.title),
        textContent = text.message.renderText(),
        moreInfo = moreInfo,
        severity = severity,
        onDismiss = onDismiss,
    )
}
