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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip01Core.UserInfo
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.bolt12_lightning_offer
import com.vitorpamplona.amethyst.commons.resources.clink_lightning_offer
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copy_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.send_payment_method_cashu
import com.vitorpamplona.amethyst.commons.resources.send_payment_method_lightning
import com.vitorpamplona.amethyst.commons.resources.send_payment_method_onchain
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.Bolt12OffersDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.abbreviateBolt12Offer
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.payment.ProfilePaymentMethod
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.payment.rememberProfileClinkOffer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTarget
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTargetsEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon as M3Icon

private val CashuPurple = Color(0xFFA855F7)

/**
 * One FlowRow of tappable chips for every way to pay this profile: Lightning
 * (lud16, long-press copies the address), the CLINK offer, the NIP-B1 BOLT12
 * offers (one chip each), the NIP-BC on-chain wallet, Cashu nutzaps (shown only
 * when the logged-in user's cashu wallet shares a mint the recipient accepts),
 * and the NIP-A3 payment-target chips.
 * A single FlowRow so the chips wrap together with uniform spacing instead of
 * stacking as separately padded rows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayPaymentRailChips(
    baseUser: User,
    lud16: String?,
    userInfo: UserInfo,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val clinkOffer = rememberProfileClinkOffer(userInfo, accountViewModel)
    // Re-peeked when the profile data refreshes — the recipient's kind:10019
    // often lands moments after the profile opens.
    val cashuMintUrl =
        remember(baseUser.pubkeyHex, userInfo) {
            accountViewModel.account.cashuWalletState
                .peekNutzapFunding(baseUser.pubkeyHex)
                ?.target
                ?.mintUrl
        }
    val showOnchainWallet by accountViewModel.settings.uiSettingsFlow.showOnchainWallet
        .collectAsStateWithLifecycle()
    val onchainAvailable = showOnchainWallet && LocalCache.onchainBackend != null

    val targetsAddress =
        remember(baseUser.pubkeyHex) {
            PaymentTargetsEvent.createAddress(baseUser.pubkeyHex)
        }
    val bolt12Address =
        remember(baseUser.pubkeyHex) {
            Bolt12OfferListEvent.createAddress(baseUser.pubkeyHex)
        }

    LoadAddressableNote(targetsAddress) { targetsNote ->
        val targets =
            if (targetsNote != null) {
                EventFinderFilterAssemblerSubscription(targetsNote, accountViewModel)
                val event by observeNoteEvent<PaymentTargetsEvent>(targetsNote, accountViewModel)
                remember(event) { event?.paymentTargets() ?: emptyList() }
            } else {
                emptyList()
            }

        LoadAddressableNote(bolt12Address) { bolt12Note ->
            val bolt12Offers =
                if (bolt12Note != null) {
                    EventFinderFilterAssemblerSubscription(bolt12Note, accountViewModel)
                    val event by observeNoteEvent<Bolt12OfferListEvent>(bolt12Note, accountViewModel)
                    remember(event) { event?.offers() ?: emptyList() }
                } else {
                    emptyList()
                }

            if (lud16.isNullOrEmpty() &&
                clinkOffer == null &&
                bolt12Offers.isEmpty() &&
                !onchainAvailable &&
                cashuMintUrl == null &&
                targets.isEmpty()
            ) {
                return@LoadAddressableNote
            }

            RailAndTargetChips(
                baseUser = baseUser,
                lud16 = lud16,
                clinkOffer = clinkOffer,
                bolt12Offers = bolt12Offers,
                onchainAvailable = onchainAvailable,
                cashuMintUrl = cashuMintUrl,
                targets = targets,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RailAndTargetChips(
    baseUser: User,
    lud16: String?,
    clinkOffer: NOffer?,
    bolt12Offers: List<String>,
    onchainAvailable: Boolean,
    cashuMintUrl: String?,
    targets: List<PaymentTarget>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    fun openSendPayment(method: ProfilePaymentMethod) {
        nav.nav(Route.SendPayment(baseUser.pubkeyHex, method.routeKey))
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        if (!lud16.isNullOrEmpty()) {
            ProfilePaymentChip(
                color = BitcoinOrange,
                label = stringRes(Res.string.send_payment_method_lightning),
                detail = lud16,
                copyValue = lud16,
                onClick = { openSendPayment(ProfilePaymentMethod.LIGHTNING) },
            ) {
                Icon(
                    symbol = MaterialSymbols.Bolt,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Size16Modifier,
                )
            }
        }

        if (clinkOffer != null) {
            ProfilePaymentChip(
                color = BitcoinOrange,
                label = stringRes(Res.string.clink_lightning_offer),
                copyValue = remember(clinkOffer) { clinkOffer.encode() },
                onClick = { openSendPayment(ProfilePaymentMethod.CLINK) },
            ) {
                Icon(
                    symbol = MaterialSymbols.Bolt,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Size16Modifier,
                )
            }
        }

        bolt12Offers.forEach { offer -> Bolt12OfferChip(offer, accountViewModel) }

        if (onchainAvailable) {
            ProfilePaymentChip(
                color = BitcoinOrange,
                label = stringRes(Res.string.send_payment_method_onchain),
                // The NIP-BC destination derived from the profile's pubkey.
                copyValue = remember(baseUser.pubkeyHex) { TaprootAddress.fromPubKey(baseUser.pubkeyHex) },
                onClick = { openSendPayment(ProfilePaymentMethod.ONCHAIN) },
            ) {
                Icon(
                    symbol = MaterialSymbols.CurrencyBitcoin,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Size16Modifier,
                )
            }
        }

        if (cashuMintUrl != null) {
            ProfilePaymentChip(
                color = CashuPurple,
                label = stringRes(Res.string.send_payment_method_cashu),
                copyValue = cashuMintUrl,
                onClick = { openSendPayment(ProfilePaymentMethod.CASHU) },
            ) {
                // The Cashu vector is a monochrome outline drawn in black,
                // meant to be tinted like a Material Symbol — untinted it
                // disappears on dark backgrounds.
                M3Icon(
                    imageVector = CustomHashTagIcons.Cashu,
                    contentDescription = null,
                    tint = CashuPurple,
                    modifier = Size16Modifier,
                )
            }
        }

        targets.forEach { target ->
            PaymentTargetChip(baseUser, target, accountViewModel, nav)
        }
    }
}

/**
 * A BOLT12 offer as a payment chip: tapping opens the pay/copy dialog, long-pressing copies it.
 * Shared by the profile's payment rail and the BOLT12 offer list card in the feed.
 */
@Composable
fun Bolt12OfferChip(
    offer: String,
    accountViewModel: AccountViewModel,
) {
    var showDialog by remember { mutableStateOf(false) }

    ProfilePaymentChip(
        color = BitcoinOrange,
        label = stringRes(Res.string.bolt12_lightning_offer),
        detail = remember(offer) { abbreviateBolt12Offer(offer) },
        copyValue = offer,
        onClick = { showDialog = true },
    ) {
        Icon(
            symbol = MaterialSymbols.Bolt,
            contentDescription = null,
            tint = BitcoinOrange,
            modifier = Size16Modifier,
        )
    }

    if (showDialog) {
        Bolt12OffersDialog(
            offers = listOf(offer),
            accountViewModel = accountViewModel,
            onDismiss = { showDialog = false },
        )
    }
}

/**
 * Pill chip shared by the profile's payment rails: tinted border + label, an
 * optional ellipsized detail (the lightning address), and an optional
 * long-press copy of [copyValue]. Same visual language as the NIP-A3
 * payment-target chips so the whole payments block reads as one set.
 */
@Composable
fun ProfilePaymentChip(
    color: Color,
    label: String,
    onClick: () -> Unit,
    detail: String? = null,
    copyValue: String? = null,
    icon: @Composable () -> Unit,
) {
    val shortNotice = rememberShortNotice()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringRes(Res.string.copy_to_clipboard)
    val copiedMessage = stringRes(Res.string.copied_to_clipboard)

    val clickModifier =
        if (copyValue != null) {
            Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = {
                    scope.launch {
                        clipboard.setText(copyValue)
                        shortNotice.show(copiedMessage)
                    }
                },
                onLongClickLabel = copyLabel,
            )
        } else {
            Modifier.clickable(onClick = onClick)
        }

    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = clickModifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            icon()
            Text(
                text = label,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                // Narrow hosts (the payment-target dialog's row) would otherwise
                // wrap a long type label into a two-line pill.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            }
        }
    }
}
