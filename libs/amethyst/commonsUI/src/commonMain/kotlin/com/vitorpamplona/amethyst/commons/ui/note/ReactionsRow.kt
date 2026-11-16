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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.audio.FloatingRecordingIndicator
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ReactionRowAction
import com.vitorpamplona.amethyst.commons.model.ReactionRowItem
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeReplyTo
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.payments.PaymentTargetTypes
import com.vitorpamplona.amethyst.commons.model.zap.CashuRailStatus
import com.vitorpamplona.amethyst.commons.model.zap.RailCapability
import com.vitorpamplona.amethyst.commons.model.zap.RailCapabilityResolver
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReactionCount
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReactions
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReferences
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReplyCount
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteRepostCount
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReposts
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteRepostsBy
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteZaps
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.nwc.NWCFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.boost
import com.vitorpamplona.amethyst.commons.resources.close_all_reactions_to_this_post
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.draft_note
import com.vitorpamplona.amethyst.commons.resources.error_dialog_zap_error
import com.vitorpamplona.amethyst.commons.resources.fork
import com.vitorpamplona.amethyst.commons.resources.it_s_not_possible_to_react_to_a_draft_note
import com.vitorpamplona.amethyst.commons.resources.it_s_not_possible_to_reply_to_a_draft_note
import com.vitorpamplona.amethyst.commons.resources.it_s_not_possible_to_zap_to_a_draft_note
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_reply
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_send_zaps
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_like_posts
import com.vitorpamplona.amethyst.commons.resources.no_payment_app_found_for_type
import com.vitorpamplona.amethyst.commons.resources.no_reaction_type_setup_long_press_to_change
import com.vitorpamplona.amethyst.commons.resources.no_reactions_setup
import com.vitorpamplona.amethyst.commons.resources.no_wallet_found
import com.vitorpamplona.amethyst.commons.resources.open_all_reactions_to_this_post
import com.vitorpamplona.amethyst.commons.resources.payment_targets
import com.vitorpamplona.amethyst.commons.resources.payto_amount_set_in_app
import com.vitorpamplona.amethyst.commons.resources.quick_zap_amounts
import com.vitorpamplona.amethyst.commons.resources.quote
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.commons.ui.components.AnimatedBorderTextCornerRadius
import com.vitorpamplona.amethyst.commons.ui.components.ClickableBox
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.toasts.multiline.UserBasedErrorMessage
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.layouts.NoteHorizontalPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.CASHU_PREFERRED_BELOW_SATS
import com.vitorpamplona.amethyst.commons.ui.note.ChangeReactionIcon
import com.vitorpamplona.amethyst.commons.ui.note.CommentIcon
import com.vitorpamplona.amethyst.commons.ui.note.ExpandLessIcon
import com.vitorpamplona.amethyst.commons.ui.note.ExpandMoreIcon
import com.vitorpamplona.amethyst.commons.ui.note.LikeIcon
import com.vitorpamplona.amethyst.commons.ui.note.LikedIcon
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.ONCHAIN_PREFERRED_ABOVE_SATS
import com.vitorpamplona.amethyst.commons.ui.note.ObserveZapAmountText
import com.vitorpamplona.amethyst.commons.ui.note.OutlinedZapIcon
import com.vitorpamplona.amethyst.commons.ui.note.RenderBoostGallery
import com.vitorpamplona.amethyst.commons.ui.note.RenderLikeGallery
import com.vitorpamplona.amethyst.commons.ui.note.RenderReaction
import com.vitorpamplona.amethyst.commons.ui.note.RenderZapGallery
import com.vitorpamplona.amethyst.commons.ui.note.RenderZapRaiser
import com.vitorpamplona.amethyst.commons.ui.note.RepostIcon
import com.vitorpamplona.amethyst.commons.ui.note.RepostedIcon
import com.vitorpamplona.amethyst.commons.ui.note.ShareIcon
import com.vitorpamplona.amethyst.commons.ui.note.VoiceReplyIcon
import com.vitorpamplona.amethyst.commons.ui.note.ZapIcon
import com.vitorpamplona.amethyst.commons.ui.note.ZapRail
import com.vitorpamplona.amethyst.commons.ui.note.ZapRailIcon
import com.vitorpamplona.amethyst.commons.ui.note.ZappedIcon
import com.vitorpamplona.amethyst.commons.ui.note.elements.ShareDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocalNotePlatform
import com.vitorpamplona.amethyst.commons.ui.note.platform.MAX_VOICE_RECORD_SECONDS
import com.vitorpamplona.amethyst.commons.ui.note.platform.RecordAudioBox
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.richtext.InLineIconRenderer
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.PaymentTargetsDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.paymentTargetStyleFor
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.OnchainZapSendDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.navigateToReloadMint
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Font14SP
import com.vitorpamplona.amethyst.commons.ui.theme.HalfDoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.commons.ui.theme.ModifierWidth3dp
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowExpandButton
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowExpandButtonAtEnd
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowHeight
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowHeightAlignedToText
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowHeightWithPadding
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowZapraiser
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowZapraiserAlignedToText
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowZapraiserWithPadding
import com.vitorpamplona.amethyst.commons.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.commons.ui.theme.Size14Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size18dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size19Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size28Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.defaultTweenDuration
import com.vitorpamplona.amethyst.commons.ui.theme.defaultTweenFloatSpec
import com.vitorpamplona.amethyst.commons.ui.theme.defaultTweenIntOffsetSpec
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.reactionBox
import com.vitorpamplona.amethyst.commons.ui.theme.ripple24dp
import com.vitorpamplona.amethyst.commons.ui.theme.selectedReactionBoxModifier
import com.vitorpamplona.amethyst.commons.ui.wallet.WalletAppLauncher
import com.vitorpamplona.amethyst.commons.ui.wallet.payInvoice
import com.vitorpamplona.amethyst.commons.ui.wallet.rememberWalletAppLauncher
import com.vitorpamplona.amethyst.commons.util.customEmojiUrlOrNull
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.util.showCount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.BaseVoiceEvent
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTarget
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTargetsEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Composable
fun ReactionsRow(
    baseNote: Note,
    showReactionDetail: Boolean,
    addPadding: Boolean,
    editState: State<GenericLoadable<EditState>>?,
    accountViewModel: AccountViewModel,
    nav: INav,
    alignToText: Boolean = false,
) {
    val wantsToSeeReactions = rememberSaveable(baseNote) { mutableStateOf(false) }

    InnerReactionRow(baseNote, showReactionDetail, addPadding, alignToText, wantsToSeeReactions, editState, accountViewModel, nav)

    LoadAndDisplayZapraiser(baseNote, showReactionDetail, wantsToSeeReactions, accountViewModel, alignToText)

    if (showReactionDetail && wantsToSeeReactions.value) {
        ReactionDetailGallery(baseNote, nav, accountViewModel)
        Spacer(modifier = HalfDoubleVertSpacer)
    }
}

@Composable
private fun InnerReactionRow(
    baseNote: Note,
    showReactionDetail: Boolean,
    addPadding: Boolean,
    alignToText: Boolean,
    wantsToSeeReactions: MutableState<Boolean>,
    editState: State<GenericLoadable<EditState>>?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val voiceRecordingState = remember(baseNote.idHex) { mutableStateOf(false) }
    val reactionRowItems by accountViewModel.reactionRowItemsFlow().collectAsStateWithLifecycle()

    // Unsealed rumors (private replies/posts) must not receive public
    // reposts or quotes: each would e-tag the private rumor id onto
    // public relays. Replies, reactions, and zaps stay enabled because
    // the composer locks private mode for rumor parents, ReactionAction
    // gift-wraps reactions to empty-sig targets, and zaps are forced to
    // the PRIVATE type with public rails suppressed. Read once per row,
    // not once per reaction item.
    val isPrivateRumor = baseNote.isPrivateRumor()
    // A NIP-29/Buzz relay-group message (including a Buzz DM) must not be publicly reposted or
    // shared: the reference points at a membership-gated group event that non-members can't fetch,
    // and a DM shouldn't be rebroadcast at all. Reply/like/zap stay (reply routes into the group).
    val isRelayGroupMessage = baseNote.inGatherers?.any { it is RelayGroupChannel } == true

    GenericInnerReactionRow(
        showReactionDetail = showReactionDetail,
        addPadding = addPadding,
        alignToText = alignToText,
        weightTwo = if (voiceRecordingState.value) 2f else 1f,
        one = {
            WatchReactionsZapsBoostsAndDisplayIfExists(baseNote, accountViewModel) {
                RenderShowIndividualReactionsButton(wantsToSeeReactions, accountViewModel)
            }
        },
        reactions = reactionRowItems,
        renderReaction = { item ->
            when (item.action) {
                ReactionRowAction.Reply -> {
                    ReplyReactionWithDialog(
                        baseNote,
                        MaterialTheme.colorScheme.placeholderText,
                        accountViewModel,
                        nav,
                        showCounter = item.showCounter,
                        voiceRecordingState = voiceRecordingState,
                    )
                }

                ReactionRowAction.Boost -> {
                    val isDM = baseNote.event is ChatroomKeyable
                    if (!isDM && !isPrivateRumor && !isRelayGroupMessage) {
                        BoostWithDialog(
                            baseNote,
                            editState,
                            MaterialTheme.colorScheme.placeholderText,
                            accountViewModel,
                            nav,
                            showCounter = item.showCounter,
                        )
                    }
                }

                ReactionRowAction.Like -> {
                    LikeReaction(
                        baseNote,
                        MaterialTheme.colorScheme.placeholderText,
                        accountViewModel,
                        nav,
                        showCounter = item.showCounter,
                    )
                }

                ReactionRowAction.Zap -> {
                    // Zaps stay enabled on private rumors: AccountViewModel.zap
                    // forces the PRIVATE zap type, and the public nutzap/onchain
                    // rails are suppressed for them.
                    ZapReaction(
                        baseNote,
                        MaterialTheme.colorScheme.placeholderText,
                        accountViewModel,
                        nav = nav,
                        showCounter = item.showCounter,
                    )
                }

                ReactionRowAction.Share -> {
                    if (!isPrivateRumor && !isRelayGroupMessage) {
                        ShareReaction(
                            note = baseNote,
                            nav = nav,
                            grayTint = MaterialTheme.colorScheme.placeholderText,
                        )
                    }
                }

                ReactionRowAction.Pay -> {
                    PayReaction(
                        baseNote = baseNote,
                        grayTint = MaterialTheme.colorScheme.placeholderText,
                        accountViewModel = accountViewModel,
                    )
                }
            }
        },
    )
}

@Composable
fun ShareReaction(
    note: Note,
    nav: INav,
    grayTint: Color,
    barChartModifier: Modifier = Size19Modifier,
) {
    var showShareMenu by remember { mutableStateOf(false) }

    // The Box is the anchor the menu drops down from.
    Box {
        ClickableBox(
            modifier = barChartModifier,
            onClick = { showShareMenu = true },
        ) {
            ShareIcon(barChartModifier, grayTint)
        }

        if (showShareMenu) {
            ShareDropdownMenu(note, nav, onDismiss = { showShareMenu = false })
        }
    }
}

@Composable
fun PayReaction(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    iconSizeModifier: Modifier = Size20Modifier,
) {
    val authorPubkey = baseNote.author?.pubkeyHex ?: return
    val address = remember(authorPubkey) { PaymentTargetsEvent.createAddress(authorPubkey) }

    LoadAddressableNote(address) { note ->
        var expanded by remember { mutableStateOf(false) }

        ClickableBox(
            modifier = iconSizeModifier,
            onClick = { expanded = true },
        ) {
            Icon(
                symbol = MaterialSymbols.AccountBalanceWallet,
                contentDescription = stringRes(Res.string.payment_targets),
                tint = grayTint,
                modifier = iconSizeModifier,
            )
        }

        if (expanded && note != null) {
            EventFinderFilterAssemblerSubscription(note, accountViewModel)
            val event by observeNoteEvent<PaymentTargetsEvent>(note, accountViewModel)
            val targets = remember(event) { event?.paymentTargets() ?: emptyList() }

            PaymentTargetsDialog(targets = targets, onDismiss = { expanded = false })
        }
    }
}

@Composable
private fun GenericInnerReactionRow(
    showReactionDetail: Boolean,
    addPadding: Boolean,
    alignToText: Boolean,
    weightTwo: Float = 1f,
    one: @Composable () -> Unit,
    reactions: ImmutableList<ReactionRowItem>,
    renderReaction: @Composable (ReactionRowItem) -> Unit,
) {
    val enabledReactions = remember(reactions) { reactions.filter { it.enabled } }
    val lastIndex = enabledReactions.lastIndex

    Row(
        verticalAlignment = CenterVertically,
        horizontalArrangement = RowColSpacing,
        modifier =
            when {
                !addPadding -> ReactionRowHeight
                alignToText -> ReactionRowHeightAlignedToText
                else -> ReactionRowHeightWithPadding
            },
    ) {
        // With no author column to sit under, the expand button moves to the end of the row.
        if (showReactionDetail && !alignToText) {
            Row(
                verticalAlignment = CenterVertically,
                modifier = ReactionRowExpandButton,
            ) {
                one()
            }
        }

        enabledReactions.forEachIndexed { index, item ->
            // Skip the weighted slice for the rightmost item only when it has no counter
            // (e.g. Share / Pay) — those are icon-only, so letting them collapse to natural
            // width pins them flush against the right padding. If the rightmost item carries
            // a counter (Zap / Like / etc.), keep it weighted so its icon stays at the left
            // of an equal-width slice and the counter doesn't get pushed to the edge.
            val isLastIconOnly = index == lastIndex && !item.showCounter
            val itemWeight = if (item.action == ReactionRowAction.Reply) weightTwo else 1f
            val mod = if (isLastIconOnly) Modifier else Modifier.weight(itemWeight)
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = RowColSpacing,
                modifier = mod,
            ) {
                renderReaction(item)
            }
        }

        if (showReactionDetail && alignToText) {
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = ReactionRowExpandButtonAtEnd,
            ) {
                one()
            }
        }
    }
}

@Composable
fun LoadAndDisplayZapraiser(
    baseNote: Note,
    showReactionDetail: Boolean,
    wantsToSeeReactions: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
    alignToText: Boolean = false,
) {
    val event = baseNote.event
    val zapraiserAmount = remember(event) { event?.zapraiserAmount() ?: 0 }
    if (zapraiserAmount > 0) {
        Box(
            modifier =
                when {
                    !showReactionDetail -> ReactionRowZapraiser
                    alignToText -> ReactionRowZapraiserAlignedToText
                    else -> ReactionRowZapraiserWithPadding
                },
            contentAlignment = CenterStart,
        ) {
            RenderZapRaiser(baseNote, zapraiserAmount, wantsToSeeReactions.value, accountViewModel)
        }
    }
}

@Composable
private fun WatchReactionsZapsBoostsAndDisplayIfExists(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    content: @Composable () -> Unit,
) {
    val hasReactions by observeNoteReferences(baseNote, accountViewModel)
    val hasRelays by observeNoteHasRelays(baseNote)

    val hasZapraiser = (baseNote.event?.zapraiserAmount() ?: 0) > 0

    // The gallery always carries an "accepted by relays" line when the note has been
    // seen on any relay, so the expand button must be reachable in that case too.
    if (hasReactions || hasZapraiser || hasRelays) {
        content()
    }
}

@Composable
private fun RenderShowIndividualReactionsButton(
    wantsToSeeReactions: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
) {
    ClickableBox(
        onClick = { wantsToSeeReactions.value = !wantsToSeeReactions.value },
        modifier = Size20Modifier,
    ) {
        CrossfadeIfEnabled(
            targetState = wantsToSeeReactions.value,
            label = "RenderShowIndividualReactionsButton",
        ) {
            if (it) {
                ExpandLessIcon(modifier = Size22Modifier, Res.string.close_all_reactions_to_this_post)
            } else {
                ExpandMoreIcon(modifier = Size22Modifier, Res.string.open_all_reactions_to_this_post)
            }
        }
    }
}

@Composable
private fun ReactionDetailGallery(
    baseNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val defaultBackgroundColor = MaterialTheme.colorScheme.background
    val backgroundColor = remember { mutableStateOf(defaultBackgroundColor) }

    val hasReactions by observeNoteReferences(baseNote, accountViewModel)
    val relays by observeNoteRelays(baseNote)

    // The gallery shows whenever there is anything to display: the "accepted by relays"
    // line (relays are almost always present once a note is seen) or any zap/boost/
    // reaction line. Guarding here keeps the padded Row from rendering an empty strip
    // for a note that has neither.
    if (hasReactions || relays.isNotEmpty()) {
        // Full width: the icon column lines up with the picture slot of the note's header.
        val startPadding = if (LocalDisplaySettings.current.fullWidthNotes) NoteHorizontalPadding else 10.dp
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier.padding(start = startPadding, top = 5.dp),
        ) {
            Column {
                WatchZapAndRenderGallery(baseNote, backgroundColor, nav, accountViewModel)
                WatchNutzapsAndRenderGallery(baseNote, nav, accountViewModel)
                WatchOnchainZapsAndRenderGallery(baseNote, nav, accountViewModel)
                WatchBolt12ZapsAndRenderGallery(baseNote, nav, accountViewModel)
                WatchBoostsAndRenderGallery(baseNote, nav, accountViewModel)
                WatchReactionsAndRenderGallery(baseNote, nav, accountViewModel)
                if (relays.isNotEmpty()) {
                    RenderAcceptedByRelaysGallery(relays, nav, accountViewModel)
                }
            }
        }
    }
}

@Composable
private fun WatchBoostsAndRenderGallery(
    baseNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val boostsEvents by observeNoteReposts(baseNote, accountViewModel)

    boostsEvents?.let {
        if (it.note.boosts.isNotEmpty()) {
            RenderBoostGallery(
                it,
                nav,
                accountViewModel,
            )
        }
    }
}

@Composable
private fun WatchReactionsAndRenderGallery(
    baseNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val reactionsState by observeNoteReactions(baseNote, accountViewModel)
    val reactionEvents = reactionsState?.note?.reactions ?: return

    if (reactionEvents.isNotEmpty()) {
        reactionEvents.forEach {
            // Keyed on the list too: a new reaction with the same emoji replaces it under the same key.
            val reactions = remember(it.key, it.value) { it.value.toImmutableList() }
            RenderLikeGallery(
                it.key,
                reactions,
                nav,
                accountViewModel,
            )
        }
    }
}

@Composable
private fun WatchZapAndRenderGallery(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val zapsState by observeNoteZaps(baseNote, accountViewModel)

    var zapEvents by
        remember(zapsState) {
            mutableStateOf(
                accountViewModel.cachedDecryptAmountMessageInGroup(baseNote),
            )
        }

    LaunchedEffect(key1 = zapsState) {
        accountViewModel.decryptAmountMessageInGroup(baseNote) { zapEvents = it }
    }

    if (zapEvents.isNotEmpty()) {
        RenderZapGallery(
            zapEvents,
            backgroundColor,
            accountViewModel,
            nav,
        )
    }
}

@Composable
private fun BoostWithDialog(
    baseNote: Note,
    editState: State<GenericLoadable<EditState>>?,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
    showCounter: Boolean = true,
) {
    BoostReaction(
        baseNote,
        grayTint,
        accountViewModel,
        showCounter = showCounter,
        onQuotePress = {
            nav.nav {
                Route.NewShortNote(
                    quote = baseNote.idHex,
                    version =
                        (editState?.value as? GenericLoadable.Loaded)
                            ?.loaded
                            ?.modificationToShow
                            ?.value
                            ?.idHex,
                )
            }
        },
        onForkPress = {
            nav.nav {
                val forkEvent = baseNote.event
                val replyTo =
                    if (forkEvent is BaseThreadedEvent) {
                        baseNote.replyTo?.firstOrNull { it.event?.id == forkEvent.replyingTo() }
                    } else {
                        null
                    }

                Route.NewShortNote(
                    baseReplyTo = replyTo?.idHex,
                    fork = baseNote.idHex,
                    version =
                        (editState?.value as? GenericLoadable.Loaded)
                            ?.loaded
                            ?.modificationToShow
                            ?.value
                            ?.idHex,
                )
            }
        },
    )
}

@Composable
private fun ReplyReactionWithDialog(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
    showCounter: Boolean = true,
    voiceRecordingState: MutableState<Boolean>? = null,
) {
    // Without a recorder (no RecordAudioBox on this platform) a voice note gets the text reply.
    if (baseNote.event is BaseVoiceEvent && LocalNotePlatform.current.canRecordAudio) {
        ReplyViaVoiceReaction(
            baseNote,
            grayTint,
            accountViewModel,
            nav,
            showCounter = showCounter,
            voiceRecordingState = voiceRecordingState,
        )
    } else {
        ReplyReaction(baseNote, grayTint, accountViewModel, showCounter = showCounter) {
            nav.nav { routeReplyTo(baseNote, accountViewModel.account) }
        }
    }
}

@Composable
fun ReplyViaVoiceReaction(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
    showCounter: Boolean = true,
    iconSizeModifier: Modifier = Size19Modifier,
    voiceRecordingState: MutableState<Boolean>? = null,
) {
    RecordAudioBox(
        modifier = Modifier,
        onRecordTaken = { audio ->
            nav.nav {
                Route.VoiceReply(
                    replyToNoteId = baseNote.idHex,
                    recordingFilePath = audio.file.toString(),
                    mimeType = audio.mimeType,
                    duration = audio.duration,
                    amplitudes = Json.encodeToString(audio.amplitudes),
                )
            }
        },
        maxDurationSeconds = MAX_VOICE_RECORD_SECONDS,
    ) { isRecording, elapsedSeconds, onStop ->
        if (voiceRecordingState != null) {
            SideEffect {
                if (voiceRecordingState.value != isRecording) {
                    voiceRecordingState.value = isRecording
                }
            }
        }
        if (isRecording) {
            FloatingRecordingIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                isRecording = true,
                elapsedSeconds = elapsedSeconds,
                isCompact = true,
                onClick = onStop,
            )
        } else {
            VoiceReplyIcon(iconSizeModifier, grayTint)
        }
    }

    if (showCounter) {
        ReplyCounter(baseNote, grayTint, accountViewModel)
    }
}

@Composable
fun ReplyReaction(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    showCounter: Boolean = true,
    iconSizeModifier: Modifier = Size19Modifier,
    onPress: () -> Unit,
) {
    ClickableBox(
        modifier = iconSizeModifier,
        onClick = {
            if (baseNote.isDraft()) {
                accountViewModel.toastManager.toast(
                    Res.string.draft_note,
                    Res.string.it_s_not_possible_to_reply_to_a_draft_note,
                )
            } else {
                if (accountViewModel.isWriteable()) {
                    onPress()
                } else {
                    accountViewModel.toastManager.toast(
                        Res.string.read_only_user,
                        Res.string.login_with_a_private_key_to_be_able_to_reply,
                    )
                }
            }
        },
    ) {
        CommentIcon(iconSizeModifier, grayTint)
    }

    if (showCounter) {
        ReplyCounter(baseNote, grayTint, accountViewModel)
    }
}

@Composable
fun ReplyCounter(
    baseNote: Note,
    textColor: Color,
    accountViewModel: AccountViewModel,
) {
    val repliesState by observeNoteReplyCount(baseNote, accountViewModel)

    SlidingAnimationCount(repliesState, textColor, accountViewModel)
}

@Composable
private fun SlidingAnimationCount(
    baseCount: Int,
    textColor: Color,
    accountViewModel: AccountViewModel,
) {
    if (accountViewModel.settings.isPerformanceMode()) {
        TextCount(baseCount, textColor)
    } else {
        DeferredAnimatedContent(baseCount, "SlidingAnimationCount") { count ->
            TextCount(count, textColor)
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
private fun <S> AnimatedContentTransitionScope<S>.transitionSpec(): ContentTransform = slideAnimation

@ExperimentalAnimationApi
val slideAnimation: ContentTransform =
    (
        slideInVertically(animationSpec = tween(durationMillis = 100)) { height -> height } +
            fadeIn(
                animationSpec = tween(durationMillis = 100),
            )
    ).togetherWith(
        slideOutVertically(animationSpec = tween(durationMillis = 100)) { height -> -height } +
            fadeOut(
                animationSpec = tween(durationMillis = 100),
            ),
    )

/** Latches the first time an animated counter's value moves off the one it was composed with. */
private class CountChangeLatch {
    var changed = false
}

/**
 * An [AnimatedContent] that does not build its transition until the value actually changes.
 *
 * Same reasoning as `DeferredCrossfade`: `AnimatedContent` builds a transition plus its content map
 * and size animation on first composition, but first composition has nothing to animate. A reaction
 * counter only slides when the count moves, which practically never happens in the second a card
 * spends on screen during a scroll — so the apparatus was built and thrown away, once per counter
 * per card.
 *
 * Rendering the bare content until the first change, then seeding a [MutableTransitionState] at the
 * original value, keeps that first change animated exactly as before.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun <T> DeferredAnimatedContent(
    targetState: T,
    label: String,
    content: @Composable (T) -> Unit,
) {
    val initial = remember { targetState }
    val latch = remember { CountChangeLatch() }
    if (targetState != initial) latch.changed = true

    if (!latch.changed) {
        content(targetState)
    } else {
        val transitionState = remember { MutableTransitionState(initial) }
        transitionState.targetState = targetState
        val transition = rememberTransition(transitionState, label)
        transition.AnimatedContent(
            transitionSpec = { transitionSpec() },
        ) { value ->
            content(value)
        }
    }
}

@Composable
fun TextCount(
    count: Int,
    textColor: Color,
) {
    Text(
        text = showCount(count),
        fontSize = Font14SP,
        color = textColor,
        maxLines = 1,
    )
}

@Composable
fun SlidingAnimationAmount(
    amount: String,
    textColor: Color,
    accountViewModel: AccountViewModel,
) {
    if (accountViewModel.settings.isPerformanceMode()) {
        Text(
            text = amount,
            fontSize = Font14SP,
            color = textColor,
            maxLines = 1,
        )
    } else {
        DeferredAnimatedContent(amount, "SlidingAnimationAmount") { count ->
            Text(
                text = count,
                fontSize = Font14SP,
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun BoostReaction(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    iconSizeModifier: Modifier = Size19Modifier,
    iconSize: Dp = Size20dp,
    showCounter: Boolean = true,
    onQuotePress: () -> Unit,
    onForkPress: () -> Unit,
) {
    var wantsToBoost by remember { mutableStateOf(false) }

    ClickableBox(
        modifier = iconSizeModifier,
        onClick = {
            accountViewModel.tryBoost(baseNote) { wantsToBoost = true }
        },
    ) {
        ObserveBoostIcon(baseNote, accountViewModel) { hasBoosted ->
            if (hasBoosted) {
                RepostedIcon(iconSizeModifier)
            } else {
                RepostIcon(iconSizeModifier, grayTint)
            }
        }

        if (wantsToBoost) {
            BoostTypeChoicePopup(
                baseNote,
                iconSize,
                accountViewModel,
                onDismiss = { wantsToBoost = false },
                onQuote = {
                    wantsToBoost = false
                    onQuotePress()
                },
                onRepost = {
                    accountViewModel.boost(baseNote)
                },
                onFork = {
                    wantsToBoost = false
                    onForkPress()
                },
            )
        }
    }

    if (showCounter) {
        BoostText(baseNote, grayTint, accountViewModel)
    }
}

@Composable
fun ObserveBoostIcon(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (Boolean) -> Unit,
) {
    val hasBoosted by observeNoteRepostsBy(baseNote, accountViewModel.userProfile(), accountViewModel)

    inner(hasBoosted)
}

@Composable
fun BoostText(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
) {
    val boostState by observeNoteRepostCount(baseNote, accountViewModel)

    SlidingAnimationCount(boostState, grayTint, accountViewModel)
}

@Composable
fun LikeReaction(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
    iconSize: Dp = Size18dp,
    heartSizeModifier: Modifier = Size18Modifier,
    iconFontSize: TextUnit = Font14SP,
    showCounter: Boolean = true,
) {
    var wantsToReact by remember { mutableStateOf(false) }

    ClickableBox(
        onClick = {
            likeClick(
                accountViewModel,
                baseNote,
                onMultipleChoices = { wantsToReact = true },
                onWantsToSignReaction = { accountViewModel.reactToOrDelete(baseNote) },
            )
        },
        onLongClick = { nav.nav(Route.UpdateReactionType) },
    ) {
        ObserveLikeIcon(baseNote, accountViewModel) { reactionType ->
            CrossfadeIfEnabled(targetState = reactionType, contentAlignment = Center, label = "LikeIcon") {
                if (reactionType != null) {
                    RenderReactionType(reactionType, heartSizeModifier, iconFontSize)
                } else {
                    LikeIcon(heartSizeModifier, grayTint)
                }
            }
        }

        if (wantsToReact) {
            ReactionChoicePopup(
                baseNote,
                iconSize,
                accountViewModel,
                onDismiss = { wantsToReact = false },
                onChangeAmount = {
                    wantsToReact = false
                    nav.nav(Route.UpdateReactionType)
                },
            )
        }
    }

    if (showCounter) {
        ObserveLikeText(baseNote, accountViewModel) { reactionCount -> SlidingAnimationCount(reactionCount, grayTint, accountViewModel) }
    }
}

@Composable
fun ObserveLikeIcon(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (String?) -> Unit,
) {
    val reactionsState by observeNoteReactions(baseNote, accountViewModel)

    // A plain lookup, so it is read in composition: starting from null made every liked note draw an
    // empty heart for a frame and then crossfade to the liked one each time it scrolled in.
    val reactionType = remember(reactionsState) { accountViewModel.loadReactionTo(reactionsState?.note ?: baseNote) }

    inner(reactionType)
}

@Composable
private fun RenderReactionType(
    reactionType: String,
    iconSizeModifier: Modifier = Size20Modifier,
    iconFontSize: TextUnit,
) {
    // A bare `:shortcode:` (its event had no emoji tag) has no image: it falls to the text branch.
    val renderable =
        remember(reactionType) {
            reactionType.customEmojiUrlOrNull()?.let { persistentListOf(CustomEmoji.ImageUrlType(it)) }
        }

    if (renderable != null) {
        InLineIconRenderer(
            renderable,
            style = SpanStyle(color = Color.White),
            fontSize = iconFontSize,
            maxLines = 1,
        )
    } else {
        when (reactionType) {
            "+" -> {
                LikedIcon(iconSizeModifier)
            }

            "-" -> {
                Text(text = "\uD83D\uDC4E", maxLines = 1, fontSize = iconFontSize)
            }

            else -> {
                if (EmojiCoder.isCoded(reactionType)) {
                    AnimatedBorderTextCornerRadius(
                        reactionType,
                        fontSize = iconFontSize,
                    )
                } else {
                    Text(text = reactionType, maxLines = 1, fontSize = iconFontSize)
                }
            }
        }
    }
}

@Composable
fun ObserveLikeText(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (Int) -> Unit,
) {
    val reactionCount by observeNoteReactionCount(baseNote, accountViewModel)

    inner(reactionCount)
}

private fun likeClick(
    accountViewModel: AccountViewModel,
    baseNote: Note,
    onMultipleChoices: () -> Unit,
    onWantsToSignReaction: () -> Unit,
) {
    if (baseNote.isDraft()) {
        accountViewModel.toastManager.toast(
            Res.string.draft_note,
            Res.string.it_s_not_possible_to_react_to_a_draft_note,
        )
        return
    }

    val choices = accountViewModel.reactionChoices()
    when {
        choices.isEmpty() -> {
            accountViewModel.toastManager.toast(
                Res.string.no_reactions_setup,
                Res.string.no_reaction_type_setup_long_press_to_change,
            )
        }

        !accountViewModel.isWriteable() -> {
            accountViewModel.toastManager.toast(
                Res.string.read_only_user,
                Res.string.login_with_a_private_key_to_like_posts,
            )
        }

        choices.size == 1 -> {
            onWantsToSignReaction()
        }

        choices.size > 1 -> {
            onMultipleChoices()
        }
    }
}

@Immutable
private data class OnchainZapRequest(
    val amountSats: Long?,
)

/**
 * Pays a single payable via a wallet intent, or routes multiple payables (zap
 * splits) to the manual payment screen. The shared tail of every zap flow.
 */
@OptIn(ExperimentalUuidApi::class)
fun payViaIntentOrManualSplit(
    payables: ImmutableList<ZapPaymentHandler.Payable>,
    walletLauncher: WalletAppLauncher,
    noWalletFound: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    onPaymentError: () -> Unit = {},
) {
    if (payables.size == 1) {
        val payable = payables.first()
        walletLauncher.payInvoice(payable.invoice, noWalletFound, { }) { error ->
            onPaymentError()
            accountViewModel.toastManager.toast(Res.string.error_dialog_zap_error, UserBasedErrorMessage(error, payable.info.user))
        }
    } else {
        val uid = Uuid.random().toString()
        accountViewModel.tempManualPaymentCache.put(uid, payables)
        nav.nav(Route.ManualZapSplitPayment(uid))
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalUuidApi::class)
fun ZapReaction(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    iconSize: Dp = Size20dp,
    iconSizeModifier: Modifier = Size20Modifier,
    animationModifier: Modifier = Size14Modifier,
    showCounter: Boolean = true,
    nav: INav,
) {
    val noWalletFoundStr = stringRes(Res.string.no_wallet_found)
    var wantsToZap by remember { mutableStateOf(false) }
    var wantsToSetCustomZap by remember { mutableStateOf(false) }
    // null = closed; OnchainZapRequest(amount=null) = open with no prefill;
    // OnchainZapRequest(amount=N) = open prefilled to N sats.
    var onchainZapRequest by remember { mutableStateOf<OnchainZapRequest?>(null) }

    val walletLauncher = rememberWalletAppLauncher()
    val scope = rememberCoroutineScope()

    var zappingProgress by remember { mutableFloatStateOf(0f) }
    var zapStartingTime by remember { mutableLongStateOf(0L) }

    Row(
        verticalAlignment = CenterVertically,
        modifier =
            iconSizeModifier.combinedClickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple24dp,
                onClick = {
                    scope.launch {
                        zapClick(
                            baseNote,
                            accountViewModel,
                            onZapStarts = { zapStartingTime = TimeUtils.now() },
                            onZappingProgress = { progress: Float -> scope.launch { zappingProgress = progress } },
                            onMultipleChoices = {
                                scope.launch {
                                    wantsToZap = true
                                }
                            },
                            onError = { _, message, user ->
                                scope.launch {
                                    zappingProgress = 0f
                                    accountViewModel.toastManager.toast(Res.string.error_dialog_zap_error, message, user)
                                }
                            },
                            onPayViaIntent = {
                                payViaIntentOrManualSplit(it, walletLauncher, noWalletFoundStr, accountViewModel, nav, onPaymentError = { zappingProgress = 0f })
                            },
                            onCustomAmount = {
                                wantsToSetCustomZap = true
                            },
                        )
                    }
                },
                onLongClick = { nav.nav(Route.UpdateZapAmount()) },
                onDoubleClick = { wantsToSetCustomZap = true },
            ),
    ) {
        if (wantsToZap) {
            ZapAmountChoicePopup(
                baseNote = baseNote,
                popupYOffset = iconSize,
                accountViewModel = accountViewModel,
                onZapStarts = { zapStartingTime = TimeUtils.now() },
                onDismiss = {
                    wantsToZap = false
                    zappingProgress = 0f
                },
                onChangeAmount = {
                    scope.launch {
                        wantsToZap = false
                        nav.nav(Route.UpdateZapAmount())
                    }
                },
                onOnchainAmount =
                    if (baseNote.isPrivateRumor()) {
                        // Onchain zap events are public and would e-tag the rumor id.
                        null
                    } else {
                        { amount ->
                            wantsToZap = false
                            onchainZapRequest = OnchainZapRequest(amount)
                        }
                    },
                onError = { _, message, user ->
                    scope.launch {
                        zappingProgress = 0f
                        accountViewModel.toastManager.toast(Res.string.error_dialog_zap_error, message, user)
                    }
                },
                onProgress = { scope.launch(Dispatchers.Main) { zappingProgress = it } },
                onPayViaIntent = {
                    payViaIntentOrManualSplit(it, walletLauncher, noWalletFoundStr, accountViewModel, nav, onPaymentError = { zappingProgress = 0f })
                },
                onReloadNutzap = { amount ->
                    wantsToZap = false
                    navigateToReloadMint(accountViewModel, nav, baseNote, amount)
                },
            )
        }

        onchainZapRequest?.let { request ->
            val zappedEventHint = remember(baseNote) { baseNote.toEventHint<Event>() }
            OnchainZapSendDialog(
                accountViewModel = accountViewModel,
                onDismiss = { onchainZapRequest = null },
                recipientPubKey = baseNote.author?.pubkeyHex,
                zappedEvent = zappedEventHint,
                prefillAmountSats = request.amountSats,
            )
        }

        if (wantsToSetCustomZap) {
            ZapCustomDialog(
                onZapStarts = { zapStartingTime = TimeUtils.now() },
                onClose = { wantsToSetCustomZap = false },
                onError = { _, message, user ->
                    scope.launch {
                        zappingProgress = 0f
                        accountViewModel.toastManager.toast(Res.string.error_dialog_zap_error, message, user)
                    }
                },
                onProgress = { scope.launch(Dispatchers.Main) { zappingProgress = it } },
                onPayViaIntent = {
                    payViaIntentOrManualSplit(it, walletLauncher, noWalletFoundStr, accountViewModel, nav, onPaymentError = { zappingProgress = 0f })
                },
                accountViewModel = accountViewModel,
                baseNote = baseNote,
            )
        }

        if (zappingProgress > 0.00001 && zappingProgress < 0.99999) {
            Spacer(ModifierWidth3dp)

            val animatedProgress by animateFloatAsState(
                targetValue = zappingProgress,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                label = "ZapIconIndicator",
            )

            ObserveZapIconState(
                baseNote,
                accountViewModel,
                zapStartingTime,
            ) { zapIconState ->
                CrossfadeIfEnabled(targetState = zapIconState, label = "ZapIcon") {
                    if (it.wasZappedByLoggedInUser) {
                        ZappedIcon(iconSizeModifier)
                    } else {
                        TwoStageZapProgressIcon(animatedProgress, animationModifier, grayTint)
                    }
                }
            }
        } else {
            ObserveZapIconState(
                baseNote,
                accountViewModel,
            ) { zapIconState ->
                CrossfadeIfEnabled(targetState = zapIconState, label = "ZapIcon") {
                    if (it.wasZappedByLoggedInUser) {
                        ZappedIcon(iconSizeModifier)
                    } else if (it.hasPendingPaymentRequest) {
                        ZapIcon(iconSizeModifier, MaterialTheme.colorScheme.primary)
                    } else {
                        OutlinedZapIcon(iconSizeModifier, grayTint)
                    }
                }
            }
        }
    }

    if (showCounter) {
        ObserveZapAmountText(baseNote, accountViewModel) { zapAmountTxt ->
            SlidingAnimationAmount(zapAmountTxt, grayTint, accountViewModel)
        }
    }
}

fun zapClick(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    onZapStarts: () -> Unit,
    onZappingProgress: (Float) -> Unit,
    onMultipleChoices: () -> Unit,
    onCustomAmount: () -> Unit,
    onError: (String, String, User?) -> Unit,
    onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
) {
    if (baseNote.isDraft()) {
        accountViewModel.toastManager.toast(
            Res.string.draft_note,
            Res.string.it_s_not_possible_to_zap_to_a_draft_note,
        )
        return
    }

    val choices = accountViewModel.zapAmountChoices()

    when {
        choices.isEmpty() -> {
            onCustomAmount()
        }

        !accountViewModel.isWriteable() -> {
            accountViewModel.toastManager.toast(
                Res.string.error_dialog_zap_error,
                Res.string.login_with_a_private_key_to_be_able_to_send_zaps,
            )
        }

        choices.size == 1 -> {
            // One-tap fast path is Lightning-only. If the recipient can't
            // receive Lightning (no lud16/lud06, and no BOLT12 offer our wallet
            // can pay), firing a zap here would just fail — open the picker
            // instead so the rail-aware chip can route to cashu / on-chain / reload.
            val caps =
                RailCapabilityResolver.peek(
                    baseNote,
                    accountViewModel.account.cashuWalletState,
                    bolt12Payable = accountViewModel.account.zaps.canZapViaBolt12(),
                )
            if (caps.hasLightning) {
                onZapStarts()
                accountViewModel.zap(
                    baseNote,
                    choices.first() * 1000,
                    null,
                    "",
                    onError = onError,
                    onProgress = { onZappingProgress(it) },
                    onPayViaIntent = onPayViaIntent,
                )
            } else {
                onMultipleChoices()
            }
        }

        choices.size > 1 -> {
            onMultipleChoices()
        }
    }
}

@Composable
private fun TwoStageZapProgressIcon(
    progress: Float,
    modifier: Modifier,
    invoiceTint: Color,
) {
    val isPaymentRequestStage = progress >= 0.75f
    val stageProgress =
        if (isPaymentRequestStage) {
            ((progress - 0.75f) / 0.25f).coerceIn(0f, 1f)
        } else {
            (progress / 0.75f).coerceIn(0f, 1f)
        }
    val stageTint = if (isPaymentRequestStage) MaterialTheme.colorScheme.primary else invoiceTint

    Box(
        modifier = modifier,
        contentAlignment = Center,
    ) {
        CircularProgressIndicator(
            progress = { stageProgress },
            modifier = modifier,
            strokeWidth = 2.dp,
            color = stageTint,
        )

        if (isPaymentRequestStage) {
            ZapIcon(Modifier.size(10.dp), stageTint)
        } else {
            OutlinedZapIcon(Modifier.size(10.dp), stageTint)
        }
    }
}

@Immutable
data class ZapIconState(
    val wasZappedByLoggedInUser: Boolean = false,
    val hasPendingPaymentRequest: Boolean = false,
)

@Composable
fun ObserveZapIconState(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    afterTimeInSeconds: Long = 0,
    inner: @Composable (ZapIconState) -> Unit,
) {
    val zapIconState = remember { mutableStateOf(ZapIconState()) }

    if (!zapIconState.value.wasZappedByLoggedInUser) {
        val zapsState by observeNoteZaps(baseNote, accountViewModel)
        val hasPendingPaymentRequest =
            zapsState?.note?.zapPayments?.any { it.value == null } == true

        zapsState?.note?.zapPayments?.forEach {
            if (it.value == null) {
                NWCFinderFilterAssemblerSubscription(it.key, accountViewModel)
            }
        }

        LaunchedEffect(key1 = zapsState, key2 = hasPendingPaymentRequest) {
            val hasZapData =
                zapsState?.note?.zapPayments?.isNotEmpty() == true ||
                    zapsState?.note?.zaps?.isNotEmpty() == true ||
                    zapsState?.note?.nutzaps?.isNotEmpty() == true ||
                    zapsState?.note?.onchainZaps?.isNotEmpty() == true ||
                    zapsState?.note?.bolt12Zaps?.isNotEmpty() == true
            val wasZapped =
                if (hasZapData) {
                    accountViewModel.calculateIfNoteWasZappedByAccount(baseNote, afterTimeInSeconds)
                } else {
                    false
                }
            val newState =
                ZapIconState(
                    wasZappedByLoggedInUser = wasZapped,
                    hasPendingPaymentRequest = hasPendingPaymentRequest && !wasZapped,
                )
            if (zapIconState.value != newState) {
                zapIconState.value = newState
            }
        }
    }

    inner(zapIconState.value)
}

@Composable
fun ObserveZapIcon(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    afterTimeInSeconds: Long = 0,
    inner: @Composable (MutableState<Boolean>) -> Unit,
) {
    val wasZappedByLoggedInUser = remember { mutableStateOf(false) }

    if (!wasZappedByLoggedInUser.value) {
        val zapsState by observeNoteZaps(baseNote, accountViewModel)

        zapsState?.note?.zapPayments?.forEach {
            if (it.value == null) {
                NWCFinderFilterAssemblerSubscription(it.key, accountViewModel)
            }
        }

        LaunchedEffect(key1 = zapsState) {
            if (zapsState?.note?.zapPayments?.isNotEmpty() == true ||
                zapsState?.note?.zaps?.isNotEmpty() == true ||
                zapsState?.note?.nutzaps?.isNotEmpty() == true ||
                zapsState?.note?.onchainZaps?.isNotEmpty() == true ||
                zapsState?.note?.bolt12Zaps?.isNotEmpty() == true
            ) {
                val newWasZapped = accountViewModel.calculateIfNoteWasZappedByAccount(baseNote, afterTimeInSeconds)
                if (wasZappedByLoggedInUser.value != newWasZapped) {
                    wasZappedByLoggedInUser.value = newWasZapped
                }
            }
        }
    }

    inner(wasZappedByLoggedInUser)
}

@Composable
private fun BoostTypeChoicePopup(
    baseNote: Note,
    iconSize: Dp,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
    onQuote: () -> Unit,
    onRepost: () -> Unit,
    onFork: () -> Unit,
) {
    val visibilityState = rememberVisibilityState(onDismiss)
    BoostTypeChoicePopup(
        baseNote,
        iconSize,
        accountViewModel,
        visibilityState,
        onQuote,
        onRepost,
        onFork,
    )
}

@Composable
private fun BoostTypeChoicePopup(
    baseNote: Note,
    iconSize: Dp,
    accountViewModel: AccountViewModel,
    visibilityState: MutableTransitionState<Boolean>,
    onQuote: () -> Unit,
    onRepost: () -> Unit,
    onFork: () -> Unit,
) {
    val iconSizePx = with(LocalDensity.current) { -iconSize.toPx().toInt() }

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, iconSizePx),
        onDismissRequest = { visibilityState.targetState = false },
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = popupAnimationEnter,
            exit = popupAnimationExit,
        ) {
            BoostTypeChoicePopupContent(
                // removes the option to fork for now because we do not have screens for
                // LongForm, Wiki and NIP posting.
                showFork = baseNote.event is TextNoteEvent,
                onBoost = {
                    if (accountViewModel.isWriteable()) {
                        accountViewModel.boost(baseNote)
                    } else {
                        onRepost()
                    }
                    visibilityState.targetState = false
                },
                onQuote = {
                    onQuote()
                    visibilityState.targetState = false
                },
                onFork = {
                    onFork()
                    visibilityState.targetState = false
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoostTypeChoicePopupContent(
    showFork: Boolean,
    onBoost: () -> Unit,
    onQuote: () -> Unit,
    onFork: () -> Unit,
) {
    Box(HalfPadding, contentAlignment = Center) {
        ElevatedCard(
            shape = SmallBorder,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
                itemVerticalAlignment = CenterVertically,
            ) {
                BoostActionChip(
                    label = stringRes(Res.string.boost),
                    onClick = onBoost,
                ) { tint ->
                    RepostedIcon(Size18Modifier, tint)
                }

                BoostActionChip(
                    label = stringRes(Res.string.quote),
                    onClick = onQuote,
                ) { tint ->
                    Icon(
                        symbol = MaterialSymbols.FormatQuote,
                        contentDescription = null,
                        modifier = Size18Modifier,
                        tint = tint,
                    )
                }

                if (showFork) {
                    BoostActionChip(
                        label = stringRes(Res.string.fork),
                        onClick = onFork,
                    ) { tint ->
                        Icon(
                            symbol = MaterialSymbols.EditNote,
                            contentDescription = null,
                            modifier = Size18Modifier,
                            tint = tint,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single quote/repost action rendered as a pill: a coloured leading icon next
 * to its label, wrapped in the same soft-outlined surface the zap rails use so
 * the popups read as one family.
 */
@Composable
private fun BoostActionChip(
    label: String,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    Surface(
        shape = ButtonBorder,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .clip(ButtonBorder)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = CenterVertically,
        ) {
            icon(MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Composable
fun BoostTypeChoicePopupPreview() {
    ThemeComparisonColumn {
        Column {
            // With the fork action (e.g. a plain text note).
            BoostTypeChoicePopupContent(
                showFork = true,
                onBoost = {},
                onQuote = {},
                onFork = {},
            )

            // Without the fork action (e.g. long-form, wiki, …).
            BoostTypeChoicePopupContent(
                showFork = false,
                onBoost = {},
                onQuote = {},
                onFork = {},
            )
        }
    }
}

val popupAnimationEnter: EnterTransition =
    slideInVertically(
        animationSpec = defaultTweenIntOffsetSpec,
        initialOffsetY = { it / 2 },
    ) + fadeIn(animationSpec = defaultTweenFloatSpec)

val popupAnimationExit: ExitTransition =
    slideOutVertically(
        animationSpec = defaultTweenIntOffsetSpec,
        targetOffsetY = { it / 2 },
    ) + fadeOut(animationSpec = defaultTweenFloatSpec)

@Composable
fun rememberVisibilityState(onDismiss: () -> Unit): MutableTransitionState<Boolean> {
    // Prevent multiple calls to onDismiss()
    var dismissed by remember { mutableStateOf(false) }

    val visibilityState = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(visibilityState.targetState) {
        if (!visibilityState.targetState && !dismissed) {
            delay(defaultTweenDuration.toLong())
            dismissed = true
            onDismiss()
        }
    }

    return visibilityState
}

@Composable
fun ReactionChoicePopup(
    baseNote: Note,
    iconSize: Dp,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
    onChangeAmount: () -> Unit,
) {
    val visibilityState = rememberVisibilityState(onDismiss)
    ReactionChoicePopup(baseNote, iconSize, accountViewModel, visibilityState, onChangeAmount)
}

@Composable
fun ReactionChoicePopup(
    baseNote: Note,
    iconSize: Dp,
    accountViewModel: AccountViewModel,
    visibilityState: MutableTransitionState<Boolean>,
    onChangeAmount: () -> Unit,
) {
    val iconSizePx = with(LocalDensity.current) { -iconSize.toPx().toInt() }

    val reactions by accountViewModel.reactionChoicesFlow().collectAsStateWithLifecycle()
    val toRemove = remember { baseNote.allReactionsByAuthor(accountViewModel.userProfile()).toImmutableSet() }

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, iconSizePx),
        onDismissRequest = { visibilityState.targetState = false },
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = popupAnimationEnter,
            exit = popupAnimationExit,
        ) {
            ReactionChoicePopupContent(
                reactions,
                toRemove = toRemove,
                onClick = { reactionType ->
                    accountViewModel.reactToOrDelete(
                        baseNote,
                        reactionType,
                    )
                    visibilityState.targetState = false
                },
                onChangeAmount,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionChoicePopupContent(
    listOfReactions: ImmutableList<String>,
    toRemove: ImmutableSet<String>,
    onClick: (reactionType: String) -> Unit,
    onChangeAmount: () -> Unit,
) {
    Box(HalfPadding, contentAlignment = Center) {
        ElevatedCard(
            shape = SmallBorder,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
            ) {
                listOfReactions.forEach { reactionType ->
                    ActionableReactionButton(
                        reactionType = reactionType,
                        onClick = { onClick(reactionType) },
                        onChangeAmount = onChangeAmount,
                        toRemove = toRemove,
                    )
                }

                ClickableBox(modifier = reactionBox, onClick = onChangeAmount) {
                    ChangeReactionIcon(modifier = Size28Modifier, MaterialTheme.colorScheme.placeholderText)
                }
            }
        }
    }
}

private const val EMOJI_ROCKET = "\uD83D\uDE80"
private const val EMOJI_THINKING = "\uD83E\uDD14"
private const val EMOJI_SCREAM = "\uD83D\uDE31"
private const val EMOJI_PARTY = "\uD83C\uDF89"

@Preview()
@Composable
fun ReactionChoicePopupPeeview() {
    ThemeComparisonColumn {
        ReactionChoicePopupContent(
            persistentListOf(
                EMOJI_ROCKET,
                "\uD83E\uDEC2",
                "\uD83D\uDC40",
                "\uD83D\uDE02",
                EMOJI_PARTY,
                EMOJI_THINKING,
                EMOJI_SCREAM,
                EMOJI_THINKING,
                EMOJI_SCREAM,
                "+",
                "-",
                EMOJI_PARTY,
                EMOJI_THINKING,
                EMOJI_SCREAM,
                EMOJI_THINKING,
                EMOJI_SCREAM,
                "\uD83D\uDE80\uDB40\uDD58\uDB40\uDD64\uDB40\uDD64\uDB40\uDD60\uDB40\uDD63\uDB40\uDD2A\uDB40\uDD1F\uDB40\uDD1F\uDB40\uDD53\uDB40\uDD54\uDB40\uDD5E\uDB40\uDD1E\uDB40\uDD63\uDB40\uDD51\uDB40\uDD64\uDB40\uDD55\uDB40\uDD5C\uDB40\uDD5C\uDB40\uDD59\uDB40\uDD64\uDB40\uDD55\uDB40\uDD1E\uDB40\uDD55\uDB40\uDD51\uDB40\uDD62\uDB40\uDD64\uDB40\uDD58\uDB40\uDD1F\uDB40\uDD29\uDB40\uDD24\uDB40\uDD27\uDB40\uDD55\uDB40\uDD24\uDB40\uDD51\uDB40\uDD52\uDB40\uDD22\uDB40\uDD54\uDB40\uDD23\uDB40\uDD21\uDB40\uDD21\uDB40\uDD25\uDB40\uDD52\uDB40\uDD55\uDB40\uDD25\uDB40\uDD26\uDB40\uDD25\uDB40\uDD51\uDB40\uDD24\uDB40\uDD29\uDB40\uDD53\uDB40\uDD56\uDB40\uDD25\uDB40\uDD54\uDB40\uDD52\uDB40\uDD20\uDB40\uDD22\uDB40\uDD25\uDB40\uDD25\uDB40\uDD29\uDB40\uDD56\uDB40\uDD23\uDB40\uDD21\uDB40\uDD20\uDB40\uDD53\uDB40\uDD51\uDB40\uDD20\uDB40\uDD51\uDB40\uDD26\uDB40\uDD54\uDB40\uDD54\uDB40\uDD56\uDB40\uDD54\uDB40\uDD54\uDB40\uDD52\uDB40\uDD54\uDB40\uDD24\uDB40\uDD52\uDB40\uDD54\uDB40\uDD28\uDB40\uDD53\uDB40\uDD52\uDB40\uDD55\uDB40\uDD53\uDB40\uDD24\uDB40\uDD24\uDB40\uDD29\uDB40\uDD29\uDB40\uDD25\uDB40\uDD53\uDB40\uDD22\uDB40\uDD55\uDB40\uDD27\uDB40\uDD1E\uDB40\uDD67\uDB40\uDD55\uDB40\uDD52\uDB40\uDD60",
            ),
            onClick = {},
            onChangeAmount = {},
            toRemove = persistentSetOf(EMOJI_ROCKET, EMOJI_THINKING, EMOJI_SCREAM),
        )
    }
}

@Composable
private fun ActionableReactionButton(
    reactionType: String,
    onClick: () -> Unit,
    onChangeAmount: () -> Unit,
    toRemove: ImmutableSet<String>,
) {
    ClickableBox(
        modifier = if (reactionType in toRemove) MaterialTheme.colorScheme.selectedReactionBoxModifier else reactionBox,
        onClick = onClick,
        onLongClick = onChangeAmount,
    ) {
        RenderReaction(reactionType)
    }
}

@Composable
fun ZapAmountChoicePopup(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    popupYOffset: Dp,
    onZapStarts: () -> Unit,
    onDismiss: () -> Unit,
    onChangeAmount: () -> Unit,
    onError: (title: String, text: String, user: User?) -> Unit,
    onProgress: (percent: Float) -> Unit,
    onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
    onOnchainAmount: ((Long?) -> Unit)? = null,
    onReloadNutzap: (Long) -> Unit = {},
) {
    val zapAmountChoices by
        accountViewModel.account.settings.syncedSettings.zaps.zapAmountChoices
            .collectAsStateWithLifecycle()

    ZapAmountChoicePopup(
        baseNote = baseNote,
        zapAmountChoices = zapAmountChoices,
        accountViewModel = accountViewModel,
        popupYOffset = popupYOffset,
        onZapStarts = onZapStarts,
        onDismiss = onDismiss,
        onChangeAmount = onChangeAmount,
        onError = onError,
        onProgress = onProgress,
        onPayViaIntent = onPayViaIntent,
        onOnchainAmount = onOnchainAmount ?: {},
        onchainSupported = onOnchainAmount != null,
        onReloadNutzap = onReloadNutzap,
    )
}

/**
 * The recipient/sender rail availability (lightning, cashu, on-chain) for zapping
 * [baseNote], recomputed as the async inputs (recipient kind:0 / kind:10019, the
 * local cashu wallet) load. Each observe call also subscribes the relay fetch, so
 * missing inputs are pulled in while the caller is on screen.
 */
@Composable
fun observeZapRailCapability(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    onchainSupported: Boolean,
): RailCapability {
    val cashuState = accountViewModel.account.cashuWalletState
    val author = baseNote.author
    // Every value below up to `showOnchainWallet` is deliberately read only to drive
    // the recompute — do NOT delete them as "unused". Each observe* call ALSO
    // subscribes the relay fetch (so a not-yet-seen kind:0 / kind:10019 / kind:10058
    // gets pulled in while the popup is open), and each value is a remember() key so
    // railCapability recomputes when it arrives. RailCapabilityResolver.peek re-reads
    // everything itself; these just say *when* to re-run it.
    val cashuMints by cashuState.mints.collectAsStateWithLifecycle()
    val cashuEntries by cashuState.tokenEntries.collectAsStateWithLifecycle()
    val recipientInfo = author?.let { observeUserInfo(it, accountViewModel).value }
    val nutzapInfo = author?.let { observeNoteEvent<NutzapInfoEvent>(it.nutzapInfoNote, accountViewModel).value }
    // BOLT12 route inputs, same contract: the recipient's kind:10058 offer list
    // (rides in UserMetadataForKeyKinds beside kind:0) and our default NWC wallet,
    // whose `pay` support decides whether that offer makes them Lightning-payable.
    val bolt12OfferList = author?.let { observeNoteEvent<Bolt12OfferListEvent>(it.bolt12OfferListNote, accountViewModel).value }
    val nip47State = accountViewModel.account.nip47SignerState
    val defaultWalletUri by nip47State.defaultWalletUri.collectAsStateWithLifecycle()
    // The wallet's kind:13194 info (its `pay` support) is a plain cache read inside
    // canZapViaBolt12(); this counter is what recomputes when it lands after opening.
    val walletInfoUpdates by remember(nip47State) { nip47State.infoCache?.updates ?: MutableStateFlow(0) }
        .collectAsStateWithLifecycle()
    // Honors the user's "show on-chain wallet" preference: off hides the on-chain
    // rail from the zap chips too, matching the wallet screen, profile chips, and
    // Send Payment screen.
    val showOnchainWallet by accountViewModel.settings.uiSettingsFlow.showOnchainWallet
        .collectAsStateWithLifecycle()

    // Pay-to hand-off inputs. Same "read only to drive the recompute" contract as
    // the four above: the recipient's kind:10133 already rides in
    // UserMetadataForKeyKinds beside kind:0, so observing it here costs no extra
    // round-trip and only says *when* to re-run the resolver.
    val showPayToChip by accountViewModel.settings.uiSettingsFlow.showPayToZapChip
        .collectAsStateWithLifecycle()
    val recipientPayTo = author?.let { observeNoteEvent<PaymentTargetsEvent>(it.paymentTargetsNote, accountViewModel).value }
    val payToProbe = LocalAppPlatform.current.payToApps
    val payToApps by payToProbe.flow.collectAsStateWithLifecycle()

    // The probe set is this one author's target list — a handful of entries, and
    // only for the author whose picker is open. It runs when the picker opens,
    // never while scrolling.
    val iconPx = with(LocalDensity.current) { PayToIconSize.roundToPx() }
    LaunchedEffect(recipientPayTo, showPayToChip) {
        val targets = recipientPayTo?.paymentTargets().orEmpty()
        if (showPayToChip && targets.isNotEmpty()) {
            withContext(Dispatchers.IO) { payToProbe.warm(targets, iconPx) }
        }
    }

    // Our own on-chain balance, so an amount we can't fund isn't offered at all.
    // Cached account-wide and refreshed at most once a minute, so opening the
    // picker again is instant; while it is still unknown (first open, explorer
    // down) the rail stays offered — see [RailCapability.canPayOnchain].
    val onchainWallet = accountViewModel.account.onchainWalletState
    val onchainEnabled = onchainSupported && showOnchainWallet
    LaunchedEffect(onchainEnabled) {
        if (onchainEnabled) onchainWallet.refresh()
    }
    val onchainFunds by onchainWallet.funds.collectAsStateWithLifecycle()

    return remember(
        baseNote,
        onchainEnabled,
        onchainFunds,
        cashuMints,
        cashuEntries,
        recipientInfo,
        nutzapInfo,
        bolt12OfferList,
        defaultWalletUri,
        walletInfoUpdates,
        showPayToChip,
        recipientPayTo,
        payToApps,
    ) {
        val rc =
            RailCapabilityResolver.peek(
                baseNote,
                cashuState,
                showPayToChip,
                bolt12Payable = accountViewModel.account.zaps.canZapViaBolt12(),
                payToResolves = { payToApps[PaymentTargetTypes.probeKeyFor(it)]?.resolves == true },
            )
        if (onchainEnabled) {
            rc.copy(onchainMaxSpendableSats = onchainFunds?.maxSpendableSats)
        } else {
            rc.copy(hasOnchain = false)
        }
    }
}

@Composable
fun ZapAmountChoicePopup(
    baseNote: Note,
    zapAmountChoices: ImmutableList<Long>,
    accountViewModel: AccountViewModel,
    popupYOffset: Dp,
    onZapStarts: () -> Unit,
    onDismiss: () -> Unit,
    onChangeAmount: () -> Unit,
    onError: (title: String, text: String, user: User?) -> Unit,
    onProgress: (percent: Float) -> Unit,
    onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
    onOnchainAmount: (Long?) -> Unit = {},
    onchainSupported: Boolean = true,
    onReloadNutzap: (Long) -> Unit = {},
) {
    // One chip per amount; the chip itself shows which rails can pay it.
    // [zapAmountChoices] is already the single merged+sorted preset list (the
    // on-chain amounts were folded in at the settings layer), so rail
    // availability per amount is decided in [UnifiedZapAmountChip] from
    // [railCapability] rather than by keeping separate per-rail lists.
    //
    // [railCapability] gates which rails the recipient can receive on (plus the
    // sender's cashu balance). The inputs load asynchronously, so observe them and
    // recompute as they arrive — the chip's Lightning logo appears once the
    // recipient's lnAddress (kind:0) loads, and the cashu logo once their nutzap
    // info (kind:10019) and our cashu wallet (mints + proofs) load. The observers
    // also trigger the relay fetch, so a not-yet-seen lnAddress / kind:10019 is
    // pulled in while the popup is open. A caller that can't drive the on-chain
    // dialog (onchainSupported == false) masks that rail off here.
    val railCapability = observeZapRailCapability(baseNote, accountViewModel, onchainSupported)
    val amountChoices =
        remember(zapAmountChoices) {
            // Keep the user's saved order (already de-duped at the settings
            // layer); don't re-sort, that would override a deliberate ordering.
            zapAmountChoices.distinct().toImmutableList()
        }
    val visibilityState = rememberVisibilityState(onDismiss)
    ZapAmountChoicePopup(
        baseNote,
        amountChoices,
        railCapability,
        accountViewModel,
        popupYOffset,
        visibilityState,
        onZapStarts,
        onChangeAmount,
        onOnchainAmount,
        onError,
        onProgress,
        onPayViaIntent,
        onReloadNutzap,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ZapAmountChoicePopup(
    baseNote: Note,
    amountChoices: ImmutableList<Long>,
    railCapability: RailCapability,
    accountViewModel: AccountViewModel,
    popupYOffset: Dp,
    visibilityState: MutableTransitionState<Boolean>,
    onZapStarts: () -> Unit,
    onChangeAmount: () -> Unit,
    onOnchainAmount: (Long?) -> Unit,
    onError: (title: String, text: String, user: User?) -> Unit,
    onProgress: (percent: Float) -> Unit,
    onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
    onReloadNutzap: (Long) -> Unit = {},
) {
    val yOffset = with(LocalDensity.current) { -popupYOffset.toPx().toInt() }

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, yOffset),
        onDismissRequest = { visibilityState.targetState = false },
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = popupAnimationEnter,
            exit = popupAnimationExit,
        ) {
            ZapAmountChoicePopupContent(
                amountChoices = amountChoices,
                railCapability = railCapability,
                onLightningZap = { amountInSats ->
                    onZapStarts()
                    accountViewModel.zap(
                        baseNote,
                        amountInSats * 1000,
                        null,
                        "",
                        true,
                        onError,
                        onProgress,
                        onPayViaIntent,
                    )
                    visibilityState.targetState = false
                },
                onNutzap = { amountInSats ->
                    onZapStarts()
                    // Instant click feedback. Without this initial nudge
                    // the bar stays flat for ~1s until scrubLocallyStale-
                    // Proofs returns; the user thinks the tap was lost.
                    onProgress(0.05f)
                    accountViewModel.sendNutzap(
                        baseNote = baseNote,
                        amountSats = amountInSats,
                        message = "",
                        onError = onError,
                        onProgress = onProgress,
                    )
                    visibilityState.targetState = false
                },
                onOnchainAmount = { amount ->
                    onOnchainAmount(amount)
                    visibilityState.targetState = false
                },
                onReloadNutzap = { amount ->
                    onReloadNutzap(amount)
                    visibilityState.targetState = false
                },
                onChangeAmount = onChangeAmount,
                // The hand-off sends the user to another app; leaving the popup
                // stacked behind it would be waiting for a tap that never comes.
                onHandedOff = { visibilityState.targetState = false },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZapAmountChoicePopupContent(
    amountChoices: ImmutableList<Long>,
    railCapability: RailCapability,
    onLightningZap: (Long) -> Unit,
    onNutzap: (Long) -> Unit,
    onOnchainAmount: (Long?) -> Unit,
    onChangeAmount: () -> Unit,
    onReloadNutzap: (Long) -> Unit = {},
    onHandedOff: () -> Unit = {},
) {
    Box(HalfPadding, contentAlignment = Center) {
        ElevatedCard(
            shape = SmallBorder,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            ZapAmountChoiceGrid(
                amountChoices = amountChoices,
                railCapability = railCapability,
                onLightningZap = onLightningZap,
                onNutzap = onNutzap,
                onOnchainAmount = onOnchainAmount,
                onChangeAmount = onChangeAmount,
                onReloadNutzap = onReloadNutzap,
                onHandedOff = onHandedOff,
            )
        }
    }
}

/**
 * The bare zap-amount chip grid (one rail-aware pill per preset amount plus the
 * preset editor button), without the popup card — embeddable in any surface, e.g.
 * the chat long-press sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZapAmountChoiceGrid(
    amountChoices: ImmutableList<Long>,
    railCapability: RailCapability,
    onLightningZap: (Long) -> Unit,
    onNutzap: (Long) -> Unit,
    onOnchainAmount: (Long?) -> Unit,
    onChangeAmount: () -> Unit,
    onReloadNutzap: (Long) -> Unit = {},
    onHandedOff: () -> Unit = {},
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = CenterVertically,
    ) {
        amountChoices.forEach { amountInSats ->
            UnifiedZapAmountChip(
                amountInSats = amountInSats,
                railCapability = railCapability,
                onLightningZap = onLightningZap,
                onNutzap = onNutzap,
                onOnchainAmount = onOnchainAmount,
                onReloadNutzap = onReloadNutzap,
                onChangeAmount = onChangeAmount,
            )
        }
        // Rendered once, beside the amount pills rather than inside each one's rail
        // toggle: it carries no amount, so repeating it per preset would say the
        // same thing four times — and keeping it out of the toggle leaves ZapRail a
        // plain enum instead of a data-carrying sealed interface.
        railCapability.payToTargets.forEach { target ->
            PayToHandoffChip(target = target, onHandedOff = onHandedOff)
        }

        ClickableBox(
            modifier =
                Modifier
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .size(32.dp)
                    .padding(7.dp),
            onClick = onChangeAmount,
        ) {
            Icon(
                symbol = MaterialSymbols.Tune,
                contentDescription = stringRes(Res.string.quick_zap_amounts),
                modifier = Size18Modifier,
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }
}

/** The size the hand-off mark draws at, and the size its bitmap is decoded to. */
internal val PayToIconSize = 18.dp

/**
 * The NIP-A3 hand-off chip: pay this person through a protocol you both publish,
 * in the app that owns it.
 *
 * Three things deliberately set it apart from the amount pills beside it, because
 * it is not a zap and must not read as one:
 *  - **No amount.** Presets are sats and there is no rate to turn them into a
 *    Venmo or IBAN figure, so no number is shown and no RFC-8905 `amount=` is
 *    emitted — the receiving app asks.
 *  - **[MaterialSymbols.OpenInNew], not the send arrow** every amount segment
 *    ends in. This leaves Amethyst.
 *  - **No zap receipt.** Nothing is published, so the note's zap counter will not
 *    move. Nothing here touches the zap progress state.
 *
 * The mark is the installed app's own icon when one app owns the URI — the same
 * `ResolveInfo` the availability probe already keeps — masked round the way a
 * launcher draws it. It falls back to the brand-coloured glyph
 * [paymentTargetStyleFor] already assigns when the hand-off would open a chooser
 * or merely a browser.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PayToHandoffChip(
    target: PaymentTarget,
    onHandedOff: () -> Unit,
) {
    val style = remember(target.type) { paymentTargetStyleFor(target.type) }
    val uri = remember(target) { PaymentTargetTypes.uriFor(target.type, target.authority) }

    // Collected, not peeked once: a web target is offered before the probe has run
    // (any browser opens https), so a snapshot taken at first composition would pin
    // the fallback glyph and the real app icon would never arrive until the picker
    // was closed and reopened.
    val apps by LocalAppPlatform.current.payToApps.flow
        .collectAsStateWithLifecycle()
    val app = remember(apps, target.type) { apps[PaymentTargetTypes.probeKeyFor(target.type)] }

    val uriHandler = LocalUriHandler.current
    val shortNotice = rememberShortNotice()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringRes(Res.string.copied_to_clipboard)
    val noAppMessage = stringRes(Res.string.no_payment_app_found_for_type, style.label)
    val amountElsewhere = stringRes(Res.string.payto_amount_set_in_app, app?.label ?: style.label)

    Surface(
        shape = ButtonBorder,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .combinedClickable(
                            onClickLabel = amountElsewhere,
                            onClick = {
                                // The probe can go stale between warming and this tap
                                // (the app was uninstalled), so keep the catch.
                                runCatching { uriHandler.openUri(uri) }
                                    .onSuccess { onHandedOff() }
                                    .onFailure { shortNotice.show(noAppMessage) }
                            },
                            // NOT onChangeAmount: a sat-preset editor means nothing
                            // here. Copies the authority, like the profile chip does.
                            onLongClick = {
                                scope.launch {
                                    clipboard.setText(target.authority)
                                    shortNotice.show(copiedMessage)
                                }
                            },
                        ).padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = CenterVertically,
            ) {
                val icon = app?.icon
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.size(PayToIconSize).clip(CircleShape),
                    )
                } else {
                    Icon(
                        symbol = style.symbol,
                        contentDescription = null,
                        tint = style.color,
                        modifier = Modifier.size(PayToIconSize),
                    )
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    text = style.label,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(3.dp))
                Icon(
                    symbol = MaterialSymbols.AutoMirrored.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * One pill per amount, showing a tappable logo for every rail that can pay it:
 *  - **Cashu** as a solid logo when a single shared mint already covers the
 *    amount (FUNDED), or as a dimmed logo with a "+" badge when funds exist but
 *    in the wrong mint (NEEDS_RELOAD) — tapping the latter opens the Reload Mint
 *    screen. Cashu is the one rail whose balance is free to read.
 *  - **Lightning** optimistically whenever the recipient can receive — an
 *    external wallet can pay any invoice, so we don't gate on a sender balance.
 *  - **On-chain** when a backend is configured, the amount clears
 *    [MIN_ONCHAIN_ZAP_SATS], and our own Taproot balance covers it plus the
 *    miner fee. That last check uses the cached balance, so it is skipped
 *    while the balance is still unknown; the send path re-validates for real.
 *
 * Tapping the amount fires the amount-tiered default rail: under
 * [CASHU_PREFERRED_BELOW_SATS] cashu, over [ONCHAIN_PREFERRED_ABOVE_SATS]
 * on-chain, and Lightning in between (falling back through the other rails when
 * the tier's rail isn't available). That default logo sits to the left of the
 * amount in colour; the alternatives follow on the right in monochrome and can
 * be tapped to force their rail. Long-press opens the amount-preset editor. A
 * pill with no payable rail is not rendered.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnifiedZapAmountChip(
    amountInSats: Long,
    railCapability: RailCapability,
    onLightningZap: (Long) -> Unit,
    onNutzap: (Long) -> Unit,
    onOnchainAmount: (Long?) -> Unit,
    onReloadNutzap: (Long) -> Unit,
    onChangeAmount: () -> Unit,
) {
    val cashuStatus = railCapability.cashuStatus(amountInSats)
    val cashuReady = cashuStatus == CashuRailStatus.FUNDED
    val cashuReloadable = cashuStatus == CashuRailStatus.NEEDS_RELOAD
    val lightningReady = railCapability.hasLightning
    val onchainReady = railCapability.canPayOnchain(amountInSats)

    if (!cashuReady && !cashuReloadable && !lightningReady && !onchainReady) return

    // Default rail is amount-tiered: tiny zaps lean on cashu (Lightning min/fees
    // make sub-10-sat payments awkward), large zaps settle on-chain, and the
    // middle ground defaults to Lightning, with the remaining rails as fallbacks.
    val present =
        buildList {
            if (cashuReady) add(ZapRail.CASHU)
            if (cashuReloadable) add(ZapRail.RELOAD)
            if (lightningReady) add(ZapRail.LIGHTNING)
            if (onchainReady) add(ZapRail.ONCHAIN)
        }
    val preferred =
        when {
            amountInSats < CASHU_PREFERRED_BELOW_SATS && cashuReady -> ZapRail.CASHU
            amountInSats < CASHU_PREFERRED_BELOW_SATS && cashuReloadable -> ZapRail.RELOAD
            amountInSats > ONCHAIN_PREFERRED_ABOVE_SATS && onchainReady -> ZapRail.ONCHAIN
            lightningReady -> ZapRail.LIGHTNING
            cashuReady -> ZapRail.CASHU
            cashuReloadable -> ZapRail.RELOAD
            else -> ZapRail.ONCHAIN
        }
    // The rails are one connected segmented toggle (shared surfaceVariant track).
    // Tapping a rail only *selects* it — switching never moves money. The selected
    // segment is the only one that shows the amount (+ a send arrow) and is the
    // only thing that, when tapped, actually sends. Starts on the amount-tier
    // default.
    var selectedRail by remember(preferred, present) {
        mutableStateOf(if (preferred in present) preferred else present.first())
    }

    Surface(
        shape = ButtonBorder,
        color = MaterialTheme.colorScheme.surfaceVariant,
        // A soft outline around the whole component so it reads as one toggle
        // control rather than a few loose icons (outlineVariant — between a hard
        // outline and none).
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = CenterVertically,
        ) {
            present.forEach { rail ->
                val isSelected = rail == selectedRail
                // The "thumb" fill slides to the selected segment (colour animates
                // in/out per segment), showing both the grouping and the selection.
                val thumb by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    label = "zapRailThumb",
                )
                val send: () -> Unit = {
                    when (rail) {
                        ZapRail.CASHU -> onNutzap(amountInSats)
                        ZapRail.RELOAD -> onReloadNutzap(amountInSats)
                        ZapRail.LIGHTNING -> onLightningZap(amountInSats)
                        ZapRail.ONCHAIN -> onOnchainAmount(amountInSats)
                    }
                }
                Row(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(thumb)
                            .combinedClickable(
                                onClick = { if (isSelected) send() else selectedRail = rail },
                                onLongClick = onChangeAmount,
                            ).padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = CenterVertically,
                ) {
                    ZapRailIcon(rail, colored = isSelected)
                    // The amount lives only on the selected segment — it expands in
                    // here and shrinks away on the old one, so it reads as travelling
                    // to the icon you picked.
                    AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                    ) {
                        Row(verticalAlignment = CenterVertically) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = showAmount(BigDecimal(amountInSats)),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                symbol = MaterialSymbols.AutoMirrored.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Exercises the chip across rail combinations and amount bands so the default
 * (coloured, left of the amount) and the alternative circular buttons can be
 * eyeballed. Amounts span the tiers: 5 (cashu), 100/5k (Lightning), 100k
 * (on-chain).
 */
@Preview
@Composable
fun ZapAmountChoicePopupPreview() {
    val amounts = persistentListOf(5L, 100L, 5_000L, 100_000L)
    ThemeComparisonColumn {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ZapChipPreviewRow(
                "All rails · cashu funded",
                RailCapability(hasCashu = true, hasLightning = true, hasOnchain = true, cashuBestSingleMintSats = 1_000_000L, cashuTotalWalletSats = 1_000_000L),
                amounts,
            )
            ZapChipPreviewRow(
                "Cashu only",
                RailCapability(hasCashu = true, hasLightning = false, hasOnchain = false, cashuBestSingleMintSats = 1_000_000L, cashuTotalWalletSats = 1_000_000L),
                amounts,
            )
            ZapChipPreviewRow(
                "Lightning only",
                RailCapability(hasCashu = false, hasLightning = true, hasOnchain = false, cashuBestSingleMintSats = 0L, cashuTotalWalletSats = 0L),
                amounts,
            )
            ZapChipPreviewRow(
                "Reload (funds split across mints)",
                RailCapability(hasCashu = true, hasLightning = true, hasOnchain = true, cashuBestSingleMintSats = 10L, cashuTotalWalletSats = 1_000_000L),
                amounts,
            )
            // The pay-to hand-off sits beside the amount pills, not inside them, so
            // it renders once however many presets there are. No app is installed in
            // a preview, so this also shows the brand-coloured glyph fallback the
            // chip uses when no single app owns the URI.
            ZapChipPreviewRow(
                "Lightning + two pay-to hand-offs",
                RailCapability(
                    hasCashu = false,
                    hasLightning = true,
                    hasOnchain = false,
                    payToTargets =
                        listOf(
                            PaymentTarget("venmo", "vitorpamplona"),
                            PaymentTarget("monero", "4AdUndXHHZ6cfufTMvppY6JwXNouMBzSkbLYfpAV5Usx3skxNgYeYTRJ5AmD9"),
                        ),
                ),
                amounts,
            )
        }
    }
}

@Composable
private fun ZapChipPreviewRow(
    label: String,
    caps: RailCapability,
    amounts: ImmutableList<Long>,
) {
    Text(label, style = MaterialTheme.typography.labelSmall)
    ZapAmountChoicePopupContent(
        amountChoices = amounts,
        railCapability = caps,
        onLightningZap = {},
        onNutzap = {},
        onOnchainAmount = {},
        onReloadNutzap = {},
        onChangeAmount = {},
    )
}
