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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeToMessage
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.polls.PollNoteViewModel
import com.vitorpamplona.amethyst.commons.polls.PollOption
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteZaps
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.error_dialog_zap_error
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_send_zaps
import com.vitorpamplona.amethyst.commons.resources.no_wallet_found
import com.vitorpamplona.amethyst.commons.resources.one_vote_per_user_on_atomic_votes
import com.vitorpamplona.amethyst.commons.resources.poll_author_no_vote
import com.vitorpamplona.amethyst.commons.resources.poll_is_closed_explainer
import com.vitorpamplona.amethyst.commons.resources.poll_unable_to_vote
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.resources.zaps
import com.vitorpamplona.amethyst.commons.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.commons.ui.components.ScopedViewModelStore
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.toasts.StringToastMsg
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BigPadding
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Font14SP
import com.vitorpamplona.amethyst.commons.ui.theme.Size14Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.SmallishBorder
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.ripple24dp
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.ui.wallet.payInvoice
import com.vitorpamplona.amethyst.commons.ui.wallet.rememberWalletAppLauncher
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockVitorAccountViewModel
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Preview
@Composable
fun ZapZapPollNotePreview() {
    val event =
        ZapPollEvent(
            id = "6ff9bc13d27490f6e3953325260bd996901a143de89886a0608c39e7d0160a72",
            pubKey = "f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a",
            createdAt = 1729186078,
            content = "Testing polls again",
            sig = "540101837a8826e2ae28401ee5f4fd8606def8501bec92a74a9e05264bb2c67558b927edf23bb085f5b5f0d91a61c65a25c37a3b92075bf10c9be03dbbe8e94e",
            tags =
                arrayOf(
                    arrayOf("poll_option", "0", "OP1"),
                    arrayOf("poll_option", "1", "OP2"),
                    arrayOf("poll_option", "2", "OP3"),
                    arrayOf("value_maximum", "2"),
                    arrayOf("value_minimum", "2"),
                    arrayOf("alt", "Poll event"),
                ),
        )

    val zapVote =
        ZapReceiptEvent(
            id = "2a17fdcd0e387d1623c7313d7aa2848e18dde8a942cfe8a2d6b686ea5f68f01a",
            pubKey = "79f00d3f5a19ec806189fcab03c1be4ff81d18ee4f653c88fac41fe03570f432",
            createdAt = 1729186293,
            content = "Testing polls again",
            sig = "819cbd8daccd173bc411f71deb4dc0fd7d281797f7f0318d2fbc6c5076e4ebe0aa52c16fd113ca5fcc3ee133cd0c4a11c6d3f77e4fa957d5401da1aff07028d0",
            tags =
                arrayOf(
                    arrayOf("p", "f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a"),
                    arrayOf("e", "6ff9bc13d27490f6e3953325260bd996901a143de89886a0608c39e7d0160a72"),
                    arrayOf("P", "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                    arrayOf("bolt11", "lnbc20n1pn3zj05dqqnp4qtfc238rdkzsj26waa3l8zgag9damzltzsqcrlscj9gvpc7ch2qs2pp5nz0dn8hg5d8z5z9cgd3x8uhnv68hkgg03v3phqvdm26r0trcm27ssp5qxgfplfnfl9q6lh96stly8t90zgkyy4emlgdhadgp8n60eh8ghzs9qyysgqcqpcxqyz5vqrzjqvdnqyc82a9maxu6c7mee0shqr33u4z9z04wpdwhf96gxzpln8jcrapyqqqqqqp2rcqqqqlgqqqqqzsq2qrzjqw9fu4j39mycmg440ztkraa03u5qhtuc5zfgydsv6ml38qd4azymlapyqqqqqqqp9sqqqqlgqqqq86qqjqrzjq26922n6s5n5undqrf78rjjhgpcczafws45tx8237y7pzx3fg8wwxrgayyqq2mgqqqqqqqqqqqqqqqqq2qzkp9q0nyx5508kumhsa8c5x82c96nuccvszlmann6mzf7qvjagwsmmft3acvvqz5q92uf5et4yr53zcgmvphtg5xephcpe5lqle26kcp530mr3"),
                    arrayOf("preimage", "a2ea9951a3ced7bc9d6099ce28a12f027bb1c2116106a6baf7001122c3e08891"),
                    arrayOf("description", "{\"id\":\"c37b692b2bb23cd543b7643962d336fe3ae96a5330769ea047808b20f80b0c6f\",\"pubkey\":\"460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c\",\"created_at\":1729186292,\"kind\":9734,\"tags\":[[\"e\",\"6ff9bc13d27490f6e3953325260bd996901a143de89886a0608c39e7d0160a72\"],[\"p\",\"f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a\"],[\"relays\",\"wss://nos.lol/\",\"wss://vitor.nostr1.com/\"],[\"alt\",\"Zap request\"],[\"poll_option\",\"0\"]],\"content\":\"\",\"sig\":\"83f2243e27d804bad13443937ddf71de7c049e85f71264eaf3a1b751a04844a65ec4bb5916722a1cdf9248f46dc5e03a92be9de543f03d022fb3e7ea08d1cf00\"}"),
                ),
        )

    val accountViewModel = mockVitorAccountViewModel()
    val nav = EmptyNav()
    val baseNote: Note?

    runBlocking {
        withContext(Dispatchers.IO) {
            LocalCache.justConsume(event, null, false)
            LocalCache.consume(zapVote.zapRequest!!, null, false)
            LocalCache.justConsume(zapVote, null, false)
            baseNote = LocalCache.getOrCreateNote("6ff9bc13d27490f6e3953325260bd996901a143de89886a0608c39e7d0160a72")
        }
    }

    val color = MaterialTheme.colorScheme.background

    if (baseNote != null) {
        ThemeComparisonColumn(
            toPreview = {
                Column(
                    Modifier.padding(10.dp),
                ) {
                    ZapPollNote(
                        baseNote = baseNote,
                        true,
                        remember { mutableStateOf(color) },
                        accountViewModel,
                        nav,
                    )
                }
            },
        )
    }
}

@Preview
@Composable
fun ZapZapPollNotePreview2() {
    val event =
        ZapPollEvent(
            id = "3064bf97800a4b04b612fc0fd498936eae75fffbdca5bbd09d19a6dc598530ab",
            pubKey = "f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a",
            createdAt = 1729191389,
            content = "Test",
            sig = "fc7f83e4dc84082a9a473338d9df2fdb876c59fbc94d49fdbce1e0bfd85cc7d50b14d4f0d08e8a84ef46f99a4e889e261cc898e2caeca4a153d8925254c5a761",
            tags =
                arrayOf(
                    arrayOf("poll_option", "0", "Pesquisa em portugues"),
                    arrayOf("poll_option", "1", "Pesquisa em ingles"),
                    arrayOf("value_maximum", "2"),
                    arrayOf("value_minimum", "2"),
                ),
        )

    val accountViewModel = mockAccountViewModel()
    val nav = EmptyNav()
    val baseNote: Note?

    runBlocking {
        withContext(Dispatchers.IO) {
            LocalCache.justConsume(event, null, false)
            baseNote = LocalCache.getOrCreateNote("3064bf97800a4b04b612fc0fd498936eae75fffbdca5bbd09d19a6dc598530ab")
        }
    }

    val color = MaterialTheme.colorScheme.background

    if (baseNote != null) {
        ThemeComparisonColumn(
            toPreview = {
                Column(
                    Modifier.padding(10.dp),
                ) {
                    ZapPollNote(
                        baseNote = baseNote,
                        true,
                        remember { mutableStateOf(color) },
                        accountViewModel,
                        nav,
                    )
                }
            },
        )
    }
}

@Composable
fun ZapPollNote(
    baseNote: Note,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // A store per card: in the screen's store, every poll scrolled past kept its model (holding the
    // note and the account) until the screen was popped, which for Home is never.
    key(baseNote.idHex) {
        ScopedViewModelStore {
            val pollViewModel: PollNoteViewModel = rememberViewModel(key = "PollNoteViewModel${baseNote.idHex}") { PollNoteViewModel() }

            pollViewModel.init(accountViewModel.account)
            pollViewModel.load(baseNote)

            ZapPollNote(
                baseNote = baseNote,
                pollViewModel = pollViewModel,
                canPreview = canPreview,
                backgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun ZapPollNote(
    baseNote: Note,
    pollViewModel: PollNoteViewModel,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchZapsAndUpdateTallies(baseNote, pollViewModel, accountViewModel)

    pollViewModel.tallies.forEach { option ->
        OptionNote(
            option,
            pollViewModel,
            baseNote,
            accountViewModel,
            canPreview,
            backgroundColor,
            nav,
        )
    }
}

@Composable
private fun WatchZapsAndUpdateTallies(
    baseNote: Note,
    pollViewModel: PollNoteViewModel,
    accountViewModel: AccountViewModel,
) {
    val zapsState by observeNoteZaps(baseNote, accountViewModel)

    LaunchedEffect(key1 = zapsState) { pollViewModel.refreshTallies() }
}

@Composable
private fun OptionNote(
    poolOption: PollOption,
    pollViewModel: PollNoteViewModel,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    nav: INav,
) {
    val tags = remember(baseNote) { baseNote.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp),
    ) {
        if (!pollViewModel.canZap.value) {
            ZapVote(
                baseNote,
                poolOption,
                pollViewModel = pollViewModel,
                nonClickablePrepend = {
                    RenderOptionAfterVote(
                        baseNote,
                        poolOption,
                        poolOption.consensusThreadhold.value,
                        canPreview,
                        tags,
                        backgroundColor,
                        accountViewModel,
                        nav,
                    )
                },
                clickablePrepend = {},
                accountViewModel = accountViewModel,
                nav = nav,
            )
        } else {
            ZapVote(
                baseNote,
                poolOption,
                pollViewModel = pollViewModel,
                nonClickablePrepend = {},
                clickablePrepend = {
                    RenderOptionBeforeVote(
                        baseNote,
                        poolOption.descriptor,
                        canPreview,
                        tags,
                        backgroundColor,
                        accountViewModel,
                        nav,
                    )
                },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun RenderOptionAfterVote(
    baseNote: Note,
    poolOption: PollOption,
    isWinning: Boolean,
    canPreview: Boolean,
    tags: ImmutableListOfLists<String>,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Box(
        Modifier
            .fillMaxWidth(0.75f)
            .clip(SmallishBorder)
            .border(
                width = 1.dp,
                color =
                    if (isWinning) {
                        MaterialTheme.colorScheme.allGoodColor
                    } else {
                        MaterialTheme.colorScheme.grayText
                    },
                shape = SmallishBorder,
            ).background(
                if (isWinning) {
                    MaterialTheme.colorScheme.allGoodColor.copy(0.2f)
                } else {
                    MaterialTheme.colorScheme.subtleBorder
                },
            ),
    ) {
        DisplayProgress(poolOption, isWinning, modifier = Modifier.matchParentSize())

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier =
                    remember {
                        Modifier
                            .padding(horizontal = 10.dp)
                            .width(45.dp)
                    },
            ) {
                TallyText(poolOption)
            }

            Column(
                modifier =
                    remember {
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 15.dp, horizontal = 10.dp)
                    },
            ) {
                TranslatableRichTextViewer(
                    poolOption.descriptor,
                    canPreview,
                    quotesLeft = 1,
                    Modifier,
                    tags,
                    backgroundColor,
                    baseNote.idHex + poolOption.descriptor,
                    baseNote.toNostrUri(),
                    baseNote.author?.pubkeyHex,
                    accountViewModel,
                    nav,
                )
            }
        }
    }
}

@Composable
private fun DisplayProgress(
    poolOption: PollOption,
    isWinning: Boolean,
    modifier: Modifier,
) {
    // Animate the progress bar when a vote is cast
    val animatedProgress by animateFloatAsState(
        targetValue = poolOption.tally.value,
        animationSpec = tween(durationMillis = 800),
    )

    val progressBarColor = if (isWinning) MaterialTheme.colorScheme.allGoodColor else MaterialTheme.colorScheme.primary

    Box(
        modifier =
            modifier
                .alpha(0.32f)
                .drawWithContent {
                    // Clip the drawing area to show only the progress amount
                    clipRect(right = size.width * animatedProgress) {
                        drawRect(progressBarColor)
                    }
                    drawContent()
                },
    )
}

@Composable
private fun TallyText(poolOption: PollOption) {
    val state = poolOption.tally
    val progressTxt by
        remember {
            derivedStateOf {
                "${(state.value * 100).roundToInt()}%"
            }
        }

    Text(
        text = progressTxt,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun RenderOptionBeforeVote(
    baseNote: Note,
    description: String,
    canPreview: Boolean,
    tags: ImmutableListOfLists<String>,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Box(
        Modifier
            .fillMaxWidth(0.75f)
            .clip(SmallishBorder)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.placeholderText,
                shape = SmallishBorder,
            ),
    ) {
        Column(BigPadding) {
            TranslatableRichTextViewer(
                content = description,
                canPreview = canPreview,
                quotesLeft = 1,
                modifier = Modifier,
                tags = tags,
                backgroundColor = backgroundColor,
                id = baseNote.idHex + description,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalUuidApi::class)
fun ZapVote(
    baseNote: Note,
    poolOption: PollOption,
    modifier: Modifier = Modifier,
    pollViewModel: PollNoteViewModel,
    nonClickablePrepend: @Composable () -> Unit,
    clickablePrepend: @Composable () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noWalletFoundStr = stringRes(Res.string.no_wallet_found)
    val errorDialogZapErrorStr = stringRes(Res.string.error_dialog_zap_error)
    val walletLauncher = rememberWalletAppLauncher()
    val isLoggedUser by remember { derivedStateOf { accountViewModel.isLoggedUser(baseNote.author) } }

    var wantsToZap by remember { mutableStateOf(false) }

    var zappingProgress by remember { mutableFloatStateOf(0f) }

    var showErrorMessageDialog by remember { mutableStateOf<StringToastMsg?>(null) }

    val scope = rememberCoroutineScope()

    nonClickablePrepend()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.combinedClickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple24dp,
                onClick = {
                    when {
                        !accountViewModel.isWriteable() -> {
                            accountViewModel.toastManager.toast(
                                Res.string.read_only_user,
                                Res.string.login_with_a_private_key_to_be_able_to_send_zaps,
                            )
                        }

                        pollViewModel.isPollClosed() -> {
                            accountViewModel.toastManager.toast(
                                Res.string.poll_unable_to_vote,
                                Res.string.poll_is_closed_explainer,
                            )
                        }

                        isLoggedUser -> {
                            accountViewModel.toastManager.toast(
                                Res.string.poll_unable_to_vote,
                                Res.string.poll_author_no_vote,
                            )
                        }

                        pollViewModel.isVoteAmountAtomic() && poolOption.zappedByLoggedIn.value -> {
                            // only allow one vote per option when min==max, i.e. atomic vote amount specified
                            accountViewModel.toastManager.toast(
                                Res.string.poll_unable_to_vote,
                                Res.string.one_vote_per_user_on_atomic_votes,
                            )
                            return@combinedClickable
                        }

                        accountViewModel.zapAmountChoices().size == 1 &&
                            pollViewModel.isValidInputVoteAmount(accountViewModel.zapAmountChoices().first()) -> {
                            accountViewModel.zap(
                                baseNote,
                                accountViewModel.zapAmountChoices().first() * 1000,
                                poolOption.option,
                                "",
                                onError = { title, message, user ->
                                    zappingProgress = 0f
                                    showErrorMessageDialog = StringToastMsg(title, message)
                                },
                                onProgress = { scope.launch(Dispatchers.Main) { zappingProgress = it } },
                                onPayViaIntent = {
                                    if (it.size == 1) {
                                        val payable = it.first()
                                        walletLauncher.payInvoice(payable.invoice, noWalletFoundStr, { }) {
                                            zappingProgress = 0f
                                            showErrorMessageDialog = StringToastMsg(errorDialogZapErrorStr, it)
                                        }
                                    } else {
                                        val uid = Uuid.random().toString()
                                        accountViewModel.tempManualPaymentCache.put(uid, it)
                                        nav.nav(Route.ManualZapSplitPayment(uid))
                                    }
                                },
                            )
                        }

                        else -> {
                            wantsToZap = true
                        }
                    }
                },
            ),
    ) {
        if (wantsToZap) {
            FilteredZapAmountChoicePopup(
                baseNote,
                accountViewModel,
                pollViewModel,
                poolOption.option,
                onZapStarts = { },
                onDismiss = {
                    wantsToZap = false
                    zappingProgress = 0f
                },
                onChangeAmount = { wantsToZap = false },
                onError = { title, message, _ ->
                    showErrorMessageDialog = StringToastMsg(title, message)
                    zappingProgress = 0f
                },
                onProgress = { scope.launch(Dispatchers.Main) { zappingProgress = it } },
                onPayViaIntent = {
                    if (it.size == 1) {
                        val payable = it.first()
                        walletLauncher.payInvoice(payable.invoice, noWalletFoundStr, { }) {
                            zappingProgress = 0f
                            showErrorMessageDialog = StringToastMsg(errorDialogZapErrorStr, it)
                        }
                    } else {
                        val uid = Uuid.random().toString()
                        accountViewModel.tempManualPaymentCache.put(uid, it)
                        nav.nav(Route.ManualZapSplitPayment(uid))
                    }
                },
            )
        }

        showErrorMessageDialog?.let { toast ->
            ErrorMessageDialog(
                title = toast.title,
                textContent = toast.msg,
                onClickStartMessage = {
                    baseNote.author?.let {
                        nav.nav {
                            routeToMessage(it, toast.msg, accountViewModel = accountViewModel)
                        }
                    }
                },
                onDismiss = { showErrorMessageDialog = null },
            )
        }

        clickablePrepend()

        if (poolOption.zappedByLoggedIn.value) {
            zappingProgress = 1f
            Icon(
                symbol = MaterialSymbols.Bolt,
                contentDescription = stringRes(Res.string.zaps),
                modifier = Modifier.size(20.dp),
                tint = BitcoinOrange,
            )
        } else {
            if (zappingProgress < 0.1 || zappingProgress > 0.99) {
                Icon(
                    symbol = MaterialSymbols.Bolt,
                    contentDescription = stringRes(id = Res.string.zaps),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            } else {
                Spacer(Modifier.width(3.dp))

                CircularProgressIndicator(
                    progress = { zappingProgress },
                    modifier = Size14Modifier,
                    strokeWidth = 2.dp,
                )
            }
        }
    }

    // only show tallies after a user has zapped note
    if (!pollViewModel.canZap.value) {
        val amountStr = remember(poolOption.zappedValue.value) { showAmount(poolOption.zappedValue.value) }
        Text(
            text = amountStr,
            fontSize = Font14SP,
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun FilteredZapAmountChoicePopup(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    pollViewModel: PollNoteViewModel,
    pollOption: Int,
    onZapStarts: () -> Unit,
    onDismiss: () -> Unit,
    onChangeAmount: () -> Unit,
    onError: (title: String, text: String, toUser: User?) -> Unit,
    onProgress: (percent: Float) -> Unit,
    onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
) {
    // TODO: Move this to the viewModel
    val zapPaymentChoices by accountViewModel.account.settings.syncedSettings.zaps.zapAmountChoices
        .collectAsStateWithLifecycle()

    val zapMessage = ""

    val sortedOptions =
        remember(zapPaymentChoices) { pollViewModel.createZapOptionsThatMatchThePollingParameters(zapPaymentChoices) }

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, -100),
        onDismissRequest = { onDismiss() },
    ) {
        FlowRow(horizontalArrangement = Arrangement.Center) {
            sortedOptions.forEach { amountInSats ->
                val zapAmount = remember { "⚡ ${showAmount(BigDecimal(amountInSats))}" }

                Button(
                    modifier = Modifier.padding(horizontal = 3.dp),
                    onClick = {
                        onZapStarts()
                        accountViewModel.zap(
                            baseNote,
                            amountInSats * 1000,
                            pollOption,
                            zapMessage,
                            true,
                            onError,
                            onProgress,
                            onPayViaIntent,
                        )
                        onDismiss()
                    },
                    shape = ButtonBorder,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    Text(
                        text = zapAmount,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier.combinedClickable(
                                onClick = {
                                    onZapStarts()
                                    accountViewModel.zap(
                                        baseNote,
                                        amountInSats * 1000,
                                        pollOption,
                                        zapMessage,
                                        true,
                                        onError,
                                        onProgress,
                                        onPayViaIntent,
                                    )
                                    onDismiss()
                                },
                                onLongClick = { onChangeAmount() },
                            ),
                    )
                }
            }
        }
    }
}
