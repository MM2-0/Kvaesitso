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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.onchain_zap_badge
import com.vitorpamplona.amethyst.commons.resources.onchain_zap_bitcoin
import com.vitorpamplona.amethyst.commons.resources.onchain_zap_confirmed_block
import com.vitorpamplona.amethyst.commons.resources.onchain_zap_confirmed_conf
import com.vitorpamplona.amethyst.commons.resources.onchain_zap_copy_tx_link
import com.vitorpamplona.amethyst.commons.resources.onchain_zap_in_mempool
import com.vitorpamplona.amethyst.commons.resources.onchain_zap_tx
import com.vitorpamplona.amethyst.commons.resources.onchain_zap_verifying
import com.vitorpamplona.amethyst.commons.resources.wallet_sats
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.commons.util.PlatformNumberFormatter
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.BitcoinTx
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MEMPOOL_TX_URL = "https://mempool.space/tx/"

@Composable
fun RenderOnchainZap(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? OnchainZapEvent ?: return
    val sender = note.author?.pubkeyHex ?: event.pubKey
    val recipient = event.recipient() ?: return

    val sats = event.claimedAmountInSats() ?: 0L
    val txid = event.txid()
    val message = event.content.takeIf { it.isNotBlank() }

    val orange = MaterialTheme.colorScheme.bitcoinColor

    // The embedded note sits on the orange wash, so it must draw transparent and
    // let that show through. Handing it the parent feed / MultiSetCard background
    // instead would paint it black (the app background) or flash it with the
    // new-note highlight, breaking the card's solid tint.
    val cardBackground = remember { mutableStateOf<Color>(Color.Transparent) }

    // Async chain lookup so we can show a real "Confirmed at block N" or
    // "In mempool…" pill rather than a sender-claimed status. Null = backend
    // not configured, or fetch failed — we just show the sender-claimed sats
    // and skip the pill.
    var tx by remember(txid) { mutableStateOf<BitcoinTx?>(null) }
    if (txid != null) {
        LaunchedEffect(txid) {
            val backend = LocalCache.onchainBackend ?: return@LaunchedEffect
            runCatchingCancellable {
                withContext(Dispatchers.IO) { backend.getTx(txid) }
            }.onSuccess { tx = it }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                orange.copy(alpha = 0.16f),
                                orange.copy(alpha = 0.04f),
                            ),
                    ),
                ).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderRow(
                sender = sender,
                recipient = recipient,
                orange = orange,
                accountViewModel = accountViewModel,
                nav = nav,
            )

            RenderTargetNote(note, quotesLeft, cardBackground, accountViewModel, nav)

            AmountRow(sats = sats, orange = orange)

            ConfirmationPill(tx = tx, hasTxid = txid != null, orange = orange)

            if (txid != null) {
                TxidRow(txid = txid, orange = orange)
            }

            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun HeaderRow(
    sender: String,
    recipient: String,
    orange: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PulsingBitcoinBadge(orange)
        UserPicture(sender, Size25dp, Modifier, accountViewModel, nav)
        Icon(
            symbol = MaterialSymbols.AutoMirrored.ArrowForwardIos,
            contentDescription = null,
            tint = orange,
            modifier = Size16Modifier,
        )
        UserPicture(recipient, Size25dp, Modifier, accountViewModel, nav)

        Spacer(Modifier.weight(1f))

        OnchainPill(orange)
    }
}

@Composable
private fun PulsingBitcoinBadge(orange: Color) {
    val pulse = rememberInfiniteTransition(label = "btcPulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "btcScale",
    )
    val glow by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "btcGlow",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(32.dp)
                .drawBehind {
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(orange.copy(alpha = glow), Color.Transparent),
                            ),
                        radius = size.minDimension / 2f,
                    )
                },
    ) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(orange),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = MaterialSymbols.CurrencyBitcoin,
                contentDescription = stringRes(Res.string.onchain_zap_bitcoin),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun OnchainPill(orange: Color) {
    val infinite = rememberInfiniteTransition(label = "pillDash")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "pillPhase",
    )

    Box(
        modifier =
            Modifier
                .drawBehind {
                    val brush =
                        Brush.sweepGradient(
                            colors =
                                listOf(
                                    orange,
                                    orange.copy(alpha = 0.4f),
                                    orange,
                                ),
                        )
                    drawRoundRect(
                        brush = brush,
                        style =
                            Stroke(
                                width = 1.4.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), phase),
                            ),
                        cornerRadius =
                            androidx.compose.ui.geometry
                                .CornerRadius(10.dp.toPx()),
                    )
                }.padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = stringRes(Res.string.onchain_zap_badge),
            style = MaterialTheme.typography.labelSmall,
            color = orange,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AmountRow(
    sats: Long,
    orange: Color,
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = PlatformNumberFormatter().format(sats),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = orange,
        )
        Text(
            text = stringRes(Res.string.wallet_sats),
            style = MaterialTheme.typography.titleSmall,
            color = orange,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun ConfirmationPill(
    tx: BitcoinTx?,
    hasTxid: Boolean,
    orange: Color,
) {
    AnimatedVisibility(visible = hasTxid) {
        val confirmations = tx?.confirmations ?: -1
        val (label, color) =
            when {
                tx == null -> stringRes(Res.string.onchain_zap_verifying) to orange.copy(alpha = 0.75f)
                confirmations <= 0 -> stringRes(Res.string.onchain_zap_in_mempool) to orange
                confirmations < 6 -> stringRes(Res.string.onchain_zap_confirmed_conf, confirmations) to orange
                else ->
                    (
                        tx.blockHeight
                            ?.let { stringRes(Res.string.onchain_zap_confirmed_block, it) }
                            ?: stringRes(Res.string.onchain_zap_confirmed_conf, confirmations)
                    ) to MaterialTheme.colorScheme.primary
            }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (tx == null || confirmations <= 0) {
                PulsingDot(color)
            } else {
                Icon(
                    symbol = MaterialSymbols.Block,
                    contentDescription = null,
                    tint = color,
                    modifier = Size16Modifier,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infinite = rememberInfiniteTransition(label = "dotPulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "dotAlpha",
    )
    Box(
        modifier =
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha)),
    )
}

@Composable
private fun TxidRow(
    txid: String,
    orange: Color,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val short = remember(txid) { "${txid.take(10)}…${txid.takeLast(8)}" }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    scope.launch { clipboard.setText("$MEMPOOL_TX_URL$txid") }
                }.padding(vertical = 2.dp),
    ) {
        Text(
            text = stringRes(Res.string.onchain_zap_tx),
            style = MaterialTheme.typography.labelSmall,
            color = orange,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = short,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            symbol = MaterialSymbols.ContentCopy,
            contentDescription = stringRes(Res.string.onchain_zap_copy_tx_link),
            tint = orange,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.height(0.dp))
    }
}
