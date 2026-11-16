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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.dvms.dal.DvmContentDiscoveryFeedViewModel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.payments.PaymentSource
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteAndMap
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.clink_debit_no_response
import com.vitorpamplona.amethyst.commons.resources.dvm_looking_for_app
import com.vitorpamplona.amethyst.commons.resources.dvm_pay_amount_to_dvm
import com.vitorpamplona.amethyst.commons.resources.dvm_pay_invoice_from_dvm
import com.vitorpamplona.amethyst.commons.resources.dvm_requesting_job
import com.vitorpamplona.amethyst.commons.resources.dvm_waiting_status
import com.vitorpamplona.amethyst.commons.resources.dvm_waiting_to_confirm_payment
import com.vitorpamplona.amethyst.commons.resources.no_wallet_found
import com.vitorpamplona.amethyst.commons.resources.nwc_payment_request
import com.vitorpamplona.amethyst.commons.resources.wallet_connect_pay_invoice_error_error
import com.vitorpamplona.amethyst.commons.service.nwc.nwcFailureDetail
import com.vitorpamplona.amethyst.commons.service.nwc.nwcTimeoutMessage
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.ReusableZapButton
import com.vitorpamplona.amethyst.commons.ui.components.ScopedViewModelStore
import com.vitorpamplona.amethyst.commons.ui.components.ZapButtonConfig
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.commons.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.commons.ui.screen.RenderFeedState
import com.vitorpamplona.amethyst.commons.ui.screen.SaveableFeedState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip90DVMs.DVMCard
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms.DvmOfflineBanner
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms.rememberDvmHeartbeatFresh
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.SimpleImage75Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.wallet.payInvoice
import com.vitorpamplona.amethyst.commons.ui.wallet.rememberWalletAppLauncher
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppMetadata
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.DvmContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.DvmStatusEvent
import com.vitorpamplona.quartz.utils.toLongValue
import kotlinx.coroutines.launch

@Composable
fun DvmContentDiscoveryScreen(
    appDefinitionEventId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (appDefinitionEventId == null) return

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            DvmTopBar(appDefinitionEventId, accountViewModel, nav)
        },
        accountViewModel = accountViewModel,
    ) {
        LoadNote(baseNoteHex = appDefinitionEventId) { note ->
            note?.let { baseNote ->
                WatchNoteEvent(
                    baseNote,
                    onNoteEventFound = {
                        DvmContentDiscoveryScreen(baseNote, accountViewModel, nav)
                    },
                    onBlank = {
                        FeedEmptyWithStatus(baseNote, stringRes(Res.string.dvm_looking_for_app), accountViewModel, nav)
                    },
                    accountViewModel,
                )
            }
        }
    }
}

@Composable
fun DvmContentDiscoveryScreen(
    appDefinition: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteAuthor = appDefinition.author ?: return

    val appDef = appDefinition.event as? AppDefinitionEvent
    val heartbeatFresh = if (appDef != null) rememberDvmHeartbeatFresh(appDef.address(), accountViewModel) else null

    var requestEventID by
        remember(appDefinition) {
            mutableStateOf<Note?>(null)
        }

    val onRefresh = {
        accountViewModel.requestDVMContentDiscovery(noteAuthor) {
            requestEventID = it
        }
    }

    LaunchedEffect(key1 = appDefinition) {
        val existingResult = accountViewModel.cachedDVMContentDiscovery(noteAuthor.pubkeyHex)
        if (existingResult == null) {
            onRefresh()
        } else {
            requestEventID = existingResult
        }
    }

    RefresheableBox(onRefresh = onRefresh) {
        Box(modifier = Modifier.fillMaxSize()) {
            val myRequestEventID = requestEventID
            if (myRequestEventID != null) {
                ObserverContentDiscoveryResponse(appDefinition, myRequestEventID, onRefresh, accountViewModel, nav)
            } else {
                // TODO: Make a good splash screen with loading animation for this DVM.
                FeedEmptyWithStatus(appDefinition, stringRes(Res.string.dvm_requesting_job), accountViewModel, nav)
            }
            if (heartbeatFresh?.value == false) {
                DvmOfflineBanner(modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
fun ObserverContentDiscoveryResponse(
    appDefinition: Note,
    dvmRequestId: Note,
    onRefresh: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteAuthor = appDefinition.author ?: return

    EventFinderFilterAssemblerSubscription(dvmRequestId, accountViewModel)

    val resultFlow =
        remember(dvmRequestId) {
            accountViewModel.account.cache
                .observeLatestEvent<DvmContentDiscoveryResponseEvent>(
                    Filter(
                        kinds = listOf(DvmContentDiscoveryResponseEvent.KIND),
                        tags = mapOf("e" to listOf(dvmRequestId.idHex)),
                        limit = 1,
                    ),
                )
        }

    val latestResponse by resultFlow.collectAsStateWithLifecycle(null)
    val myResponse = latestResponse

    if (myResponse != null) {
        PrepareViewContentDiscoveryModels(
            noteAuthor,
            dvmRequestId.idHex,
            myResponse,
            onRefresh,
            accountViewModel,
            nav,
        )
    } else {
        ObserverDvmStatusResponse(
            appDefinition,
            dvmRequestId.idHex,
            accountViewModel,
            nav,
        )
    }
}

@Composable
fun ObserverDvmStatusResponse(
    appDefinition: Note,
    dvmRequestId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val statusFlow =
        remember(dvmRequestId) {
            accountViewModel.account.cache
                .observeLatestEvent<DvmStatusEvent>(
                    Filter(
                        kinds = listOf(DvmStatusEvent.KIND),
                        tags = mapOf("e" to listOf(dvmRequestId)),
                        limit = 1,
                    ),
                )
        }

    val latestStatus by statusFlow.collectAsStateWithLifecycle(null)

    // TODO: Make a good splash screen with loading animation for this DVM.
    if (latestStatus != null) {
        // TODO: Make a good splash screen with loading animation for this DVM.
        latestStatus?.let {
            FeedDVM(appDefinition, it, accountViewModel, nav)
        }
    } else {
        // TODO: Make a good splash screen with loading animation for this DVM.
        FeedEmptyWithStatus(appDefinition, stringRes(Res.string.dvm_waiting_status), accountViewModel, nav)
    }
}

@Composable
fun PrepareViewContentDiscoveryModels(
    dvm: User,
    dvmRequestId: String,
    latestResponse: DvmContentDiscoveryResponseEvent,
    onRefresh: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Every refresh is a new request id: a store per request clears the previous request's feed
    // model, which the screen's own store would otherwise keep, still collecting, until it is popped.
    // The store outlives a screen pushed on top (RouteScopedViewModelsUiTest pins that).
    key(dvmRequestId) {
        ScopedViewModelStore {
            val resultFeedViewModel: DvmContentDiscoveryFeedViewModel =
                rememberViewModel(
                    key = "NostrNIP90ContentDiscoveryFeedViewModel${dvm.pubkeyHex}$dvmRequestId",
                    factory = DvmContentDiscoveryFeedViewModel.Factory(accountViewModel.account, dvmKey = dvm.pubkeyHex, requestId = dvmRequestId),
                )

            LaunchedEffect(key1 = dvmRequestId, latestResponse.id) {
                resultFeedViewModel.invalidateData()
            }

            RenderNostrDvmContentDiscoveryScreen(resultFeedViewModel, onRefresh, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderNostrDvmContentDiscoveryScreen(
    resultFeedViewModel: DvmContentDiscoveryFeedViewModel,
    onRefresh: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    SaveableFeedState(resultFeedViewModel.feedState, null) { listState ->
        // TODO (Optional) Instead of a like reaction, do a Kind 31989 NIP89 App recommendation
        RenderFeedState(
            resultFeedViewModel,
            accountViewModel,
            listState,
            nav,
            null,
            onEmpty = {
                FeedEmpty {
                    onRefresh()
                }
            },
        )
    }
}

@Composable
fun FeedDVM(
    appDefinitionNote: Note,
    latestStatus: DvmStatusEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val status = latestStatus.status() ?: return

    var currentStatus by remember {
        mutableStateOf(status.description)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val card = observeAppDefinition(appDefinitionNote, accountViewModel)

        card.cover?.let {
            Box(contentAlignment = BottomStart) {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    mainImageModifier = Modifier,
                    loadedImageModifier = SimpleImage75Modifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = {
                        appDefinitionNote.author?.let { author ->
                            BannerImage(author, SimpleImage75Modifier, accountViewModel)
                        }
                    },
                    onError = {
                        appDefinitionNote.author?.let { author ->
                            BannerImage(author, SimpleImage75Modifier, accountViewModel)
                        }
                    },
                )
            }
        } ?: run {
            appDefinitionNote.author?.let { author ->
                Box(contentAlignment = BottomStart) {
                    BannerImage(author, SimpleImage75Modifier, accountViewModel)
                }
            }
        }

        Spacer(modifier = DoubleVertSpacer)

        Text(
            text = card.name,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = DoubleVertSpacer)
        Text(currentStatus, textAlign = TextAlign.Center)

        DvmPaymentActions(
            latestStatus = latestStatus,
            accountViewModel = accountViewModel,
            nav = nav,
            onStatusUpdate = { currentStatus = it },
        )
    }
}

@Composable
fun DvmPaymentActions(
    latestStatus: DvmStatusEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
    onStatusUpdate: (String) -> Unit,
) {
    val noWalletFoundStr = stringRes(Res.string.no_wallet_found)
    val clinkDebitNoResponseStr = stringRes(Res.string.clink_debit_no_response)
    // The NIP-47 callbacks below are invoked by the relay dispatcher, not by
    // composition, so their bodies run in this scope to read their messages.
    val scope = rememberCoroutineScope()
    val status = latestStatus.status() ?: return

    if (status.code != "payment-required") return

    val amountTag = latestStatus.firstAmount()
    val amount = amountTag?.amount
    val invoice = amountTag?.lnInvoice

    val thankYou = stringRes(id = Res.string.dvm_waiting_to_confirm_payment)
    val nwcPaymentRequest = stringRes(id = Res.string.nwc_payment_request)

    if (invoice != null) {
        val walletApp = rememberWalletAppLauncher()
        Button(onClick = {
            when (val source = accountViewModel.account.settings.defaultPaymentSource()) {
                is PaymentSource.ClinkDebit -> {
                    onStatusUpdate(nwcPaymentRequest)
                    accountViewModel.payInvoiceViaClinkDebit(source.wallet.pointer, invoice) { response ->
                        onStatusUpdate(
                            if (response?.isOk() == true) {
                                thankYou
                            } else {
                                response?.error?.takeIf { it.isNotBlank() }
                                    ?: clinkDebitNoResponseStr
                            },
                        )
                    }
                }

                is PaymentSource.Nwc -> {
                    accountViewModel.sendZapPaymentRequestFor(
                        bolt11 = invoice,
                        zappedNote = null,
                        onSent = {
                            onStatusUpdate(nwcPaymentRequest)
                        },
                        onTimeout = { scope.launch { onStatusUpdate(nwcTimeoutMessage()) } },
                        onResponse = { response ->
                            scope.launch {
                                onStatusUpdate(
                                    response.nwcFailureDetail()?.let { detail ->
                                        loadStringRes(Res.string.wallet_connect_pay_invoice_error_error, detail)
                                    } ?: thankYou,
                                )
                            }
                        },
                    )
                }

                null -> {
                    walletApp.payInvoice(
                        invoice,
                        noWalletFoundStr,
                        onPaid = {
                            onStatusUpdate(thankYou)
                        },
                        onError = {
                            onStatusUpdate(it)
                        },
                    )
                }
            }
        }) {
            val amountInInvoice =
                try {
                    LnInvoiceUtil.getAmountInSats(invoice).toLongValue()
                } catch (_: Exception) {
                    null
                }

            if (amountInInvoice != null) {
                Text(text = stringRes(Res.string.dvm_pay_amount_to_dvm, amountInInvoice.toString()))
            } else {
                Text(text = stringRes(Res.string.dvm_pay_invoice_from_dvm))
            }
        }
    } else if (amount != null) {
        LoadNote(baseNoteHex = latestStatus.id) { stateNote ->
            stateNote?.let {
                ZapDVMButton(
                    baseNote = it,
                    amount = amount,
                    grayTint = MaterialTheme.colorScheme.onPrimary,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
fun ZapDVMButton(
    baseNote: Note,
    amount: Long,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    iconSize: Dp = Size35dp,
    nav: INav,
) {
    val config =
        remember(grayTint, iconSize, amount) {
            ZapButtonConfig(
                grayTint = grayTint,
                iconSize = iconSize,
                showUserFinderSubscription = true,
                zapAmountChoices = listOf(amount / 1000),
                buttonText = "Zap ${(amount / 1000)} sats to the DVM",
            )
        }

    ReusableZapButton(
        baseNote = baseNote,
        accountViewModel = accountViewModel,
        nav = nav,
        config = config,
    )
}

@Composable
fun FeedEmptyWithStatus(
    appDefinitionNote: Note,
    status: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val card = observeAppDefinition(appDefinitionNote, accountViewModel)

        card.cover?.let {
            Box(contentAlignment = BottomStart) {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    mainImageModifier = Modifier,
                    loadedImageModifier = SimpleImage75Modifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = {
                        appDefinitionNote.author?.let { author ->
                            BannerImage(author, SimpleImage75Modifier, accountViewModel)
                        }
                    },
                    onError = {
                        appDefinitionNote.author?.let { author ->
                            BannerImage(author, SimpleImage75Modifier, accountViewModel)
                        }
                    },
                )
            }
        } ?: run {
            appDefinitionNote.author?.let { author ->
                Box(contentAlignment = BottomStart) {
                    BannerImage(author, SimpleImage75Modifier, accountViewModel)
                }
            }
        }

        Spacer(modifier = DoubleVertSpacer)

        Text(
            text = card.name,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = DoubleVertSpacer)

        Text(status)
    }
}

fun convertAppMetadataToCard(metadata: AppMetadata?): DVMCard {
    if (metadata == null) {
        return DVMCard(
            name = "",
            description = "",
            cover = null,
            amount = "",
            personalized = false,
        )
    }

    return with(metadata) {
        DVMCard(
            name = this.name ?: "",
            description = this.about ?: "",
            cover = this.profilePicture()?.ifBlank { null },
            amount = this.amount ?: "",
            personalized = this.personalized ?: false,
        )
    }
}

@Composable
fun observeAppDefinition(
    appDefinitionNote: Note,
    accountViewModel: AccountViewModel,
): DVMCard {
    val card by observeNoteAndMap(appDefinitionNote, accountViewModel) {
        convertAppMetadataToCard((it.event as? AppDefinitionEvent)?.appMetaData())
    }
    return card
}
