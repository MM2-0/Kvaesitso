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
@file:OptIn(ExperimentalUuidApi::class)

package com.vitorpamplona.amethyst.commons.ui.broadcast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.broadcast_results
import com.vitorpamplona.amethyst.commons.resources.broadcasts_number
import com.vitorpamplona.amethyst.commons.resources.collapse
import com.vitorpamplona.amethyst.commons.resources.dismiss
import com.vitorpamplona.amethyst.commons.resources.expand
import com.vitorpamplona.amethyst.commons.resources.retry
import com.vitorpamplona.amethyst.commons.resources.retry_failed_number
import com.vitorpamplona.amethyst.commons.resources.retrying
import com.vitorpamplona.amethyst.commons.resources.share_of
import com.vitorpamplona.amethyst.commons.resources.timeout
import com.vitorpamplona.amethyst.commons.service.broadcast.BroadcastEvent
import com.vitorpamplona.amethyst.commons.service.broadcast.BroadcastStatus
import com.vitorpamplona.amethyst.commons.service.broadcast.RelayResult
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val MAX_EXPANDED_SECTIONS = 2

/**
 * Modal bottom sheet showing detailed relay results for multiple broadcasts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiBroadcastDetailsSheet(
    broadcasts: ImmutableList<BroadcastEvent>,
    onDismiss: () -> Unit = {},
    onRetryRelay: (BroadcastEvent, NormalizedRelayUrl) -> Unit = { _, _ -> },
    onRetryAllFailed: (BroadcastEvent) -> Unit = {},
    sheetState: SheetState =
        rememberModalSheetState(
            skipPartiallyExpanded = true,
        ),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        MultiBroadcastDetailsSheetContent(
            broadcasts = broadcasts,
            onDismiss = onDismiss,
            onRetryRelay = onRetryRelay,
            onRetryAllFailed = onRetryAllFailed,
        )
    }
}

@Composable
fun MultiBroadcastDetailsSheetContent(
    broadcasts: ImmutableList<BroadcastEvent>,
    onDismiss: () -> Unit = {},
    onRetryRelay: (BroadcastEvent, NormalizedRelayUrl) -> Unit = { _, _ -> },
    onRetryAllFailed: (BroadcastEvent) -> Unit = {},
) {
    // Synced rotation animation for all pending/retrying icons
    val infiniteTransition = rememberInfiniteTransition(label = "pendingRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "rotation",
    )

    // Track which sections are expanded (by broadcast ID)
    var expandedSections by remember {
        // Sort: in-progress first, then by start time
        val sortedBroadcasts =
            broadcasts.sortedWith(
                compareBy { it.startedAt },
            )

        // First 2 get shown expanded by default (unless completed)
        val visibleBroadcasts = sortedBroadcasts.take(MAX_EXPANDED_SECTIONS)

        mutableStateOf(visibleBroadcasts.map { it.id }.toSet())
    }

    Column(
        verticalArrangement = spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Text(
            text = if (broadcasts.size == 1) stringRes(Res.string.broadcast_results) else stringRes(Res.string.broadcasts_number, broadcasts.size),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(8.dp))

        // Visible broadcast sections
        broadcasts.forEachIndexed { index, broadcast ->
            BroadcastSection(
                broadcast = broadcast,
                isExpanded = broadcast.id in expandedSections,
                onToggleExpand = {
                    expandedSections =
                        if (broadcast.id in expandedSections) {
                            expandedSections - broadcast.id
                        } else {
                            expandedSections + broadcast.id
                        }
                },
                onRetryRelay = { relay -> onRetryRelay(broadcast, relay) },
                onRetryAllFailed = { onRetryAllFailed(broadcast) },
                rotationAngle = rotationAngle,
            )

            if (index < broadcasts.lastIndex) {
                HorizontalDivider()
            }
        }

        Spacer(Modifier.height(8.dp))

        // Dismiss button
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringRes(Res.string.dismiss))
        }
    }
}

@Composable
private fun BroadcastSection(
    broadcast: BroadcastEvent,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRetryRelay: (NormalizedRelayUrl) -> Unit,
    onRetryAllFailed: () -> Unit,
    rotationAngle: Float,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section header (clickable to expand/collapse)
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
            color = Color.Transparent,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                StatusIcon(broadcast.status, rotationAngle)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = broadcast.event.toKindName(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = stringRes(Res.string.share_of, broadcast.successCount, broadcast.totalRelays),
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor(broadcast.status),
                )

                Icon(
                    symbol = if (isExpanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                    contentDescription = if (isExpanded) stringRes(Res.string.collapse) else stringRes(Res.string.expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Expandable relay list
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                broadcast.targetRelays.forEach { relay ->
                    val result = broadcast.results[relay] ?: RelayResult.Pending
                    RelayResultRow(
                        relay = relay,
                        result = result,
                        onRetry = { onRetryRelay(relay) },
                        rotationAngle = rotationAngle,
                    )
                }

                // Retry all button
                if (broadcast.failedRelays.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onRetryAllFailed,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        Icon(
                            symbol = MaterialSymbols.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringRes(Res.string.retry_failed_number, broadcast.failedRelays.size))
                    }
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: BroadcastStatus): Color =
    when (status) {
        BroadcastStatus.SUCCESS -> MaterialTheme.colorScheme.allGoodColor
        BroadcastStatus.PARTIAL -> MaterialTheme.colorScheme.warningColor
        BroadcastStatus.FAILED -> MaterialTheme.colorScheme.error
        BroadcastStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
    }

@Composable
private fun StatusIcon(
    status: BroadcastStatus,
    rotationAngle: Float = 0f,
) {
    val (icon, tint, shouldRotate) =
        when (status) {
            BroadcastStatus.SUCCESS -> Triple(MaterialSymbols.CheckCircle, MaterialTheme.colorScheme.allGoodColor, false)
            BroadcastStatus.PARTIAL -> Triple(MaterialSymbols.Error, MaterialTheme.colorScheme.warningColor, false)
            BroadcastStatus.FAILED -> Triple(MaterialSymbols.Error, MaterialTheme.colorScheme.error, false)
            BroadcastStatus.IN_PROGRESS -> Triple(MaterialSymbols.HourglassEmpty, MaterialTheme.colorScheme.primary, true)
        }

    Icon(
        symbol = icon,
        contentDescription = status.name,
        tint = tint,
        modifier =
            Modifier
                .size(24.dp)
                .then(
                    if (shouldRotate) {
                        Modifier.graphicsLayer { rotationZ = rotationAngle }
                    } else {
                        Modifier
                    },
                ),
    )
}

@Composable
private fun RelayResultRow(
    relay: NormalizedRelayUrl,
    result: RelayResult,
    onRetry: () -> Unit,
    rotationAngle: Float = 0f,
) {
    val successColor = MaterialTheme.colorScheme.allGoodColor
    val errorColor = MaterialTheme.colorScheme.error
    val warningColor = MaterialTheme.colorScheme.warningColor

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status icon
        val (icon, tint, shouldRotate) =
            when (result) {
                is RelayResult.Success -> Triple(MaterialSymbols.CheckCircle, successColor, false)
                is RelayResult.Error -> Triple(MaterialSymbols.Error, errorColor, false)
                is RelayResult.Timeout -> Triple(MaterialSymbols.Error, warningColor, false)
                is RelayResult.Pending -> Triple(MaterialSymbols.HourglassEmpty, MaterialTheme.colorScheme.onSurfaceVariant, true)
                is RelayResult.Retrying -> Triple(MaterialSymbols.HourglassEmpty, MaterialTheme.colorScheme.primary, true)
            }

        Icon(
            symbol = icon,
            contentDescription = null,
            tint = tint,
            modifier =
                Modifier
                    .size(20.dp)
                    .then(
                        if (shouldRotate) {
                            Modifier.graphicsLayer { rotationZ = rotationAngle }
                        } else {
                            Modifier
                        },
                    ),
        )

        Spacer(Modifier.width(12.dp))

        // Relay URL
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = relay.displayUrl(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Error message if present
            when (result) {
                is RelayResult.Error -> {
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = errorColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is RelayResult.Timeout -> {
                    Text(
                        text = stringRes(Res.string.timeout),
                        style = MaterialTheme.typography.bodySmall,
                        color = warningColor,
                    )
                }

                is RelayResult.Retrying -> {
                    Text(
                        text = stringRes(Res.string.retrying),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                else -> {}
            }
        }

        // Retry button for failed relays (not when already retrying)
        if (result is RelayResult.Error || result is RelayResult.Timeout) {
            IconButton(
                onClick = onRetry,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Refresh,
                    contentDescription = stringRes(Res.string.retry),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(device = "spec:width=1500px,height=2340px,dpi=440")
@Composable
fun BroadcastBannerSingleEventSheetInProgressStartPreview() {
    val repost =
        RepostEvent(
            id = "",
            pubKey = "",
            createdAt = TimeUtils.now(),
            tags = emptyArray(),
            content = "",
            sig = "",
        )
    ThemeComparisonRow {
        MultiBroadcastDetailsSheetContent(
            persistentListOf(
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                ),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(device = "spec:width=1500px,height=2340px,dpi=440")
@Composable
fun BroadcastBannerSingleEventSheetInProgressOneSuccessPreview() {
    val repost =
        RepostEvent(
            id = "",
            pubKey = "",
            createdAt = TimeUtils.now(),
            tags = emptyArray(),
            content = "",
            sig = "",
        )
    ThemeComparisonRow {
        MultiBroadcastDetailsSheetContent(
            persistentListOf(
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                    results = mapOf(Constants.mom to RelayResult.Success),
                ),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(device = "spec:width=1500px,height=2340px,dpi=440")
@Composable
fun BroadcastBannerSingleEventSheetSuccessPreview() {
    val repost =
        RepostEvent(
            id = "",
            pubKey = "",
            createdAt = TimeUtils.now(),
            tags = emptyArray(),
            content = "",
            sig = "",
        )
    ThemeComparisonRow {
        MultiBroadcastDetailsSheetContent(
            persistentListOf(
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                    results =
                        mapOf(
                            Constants.antiprimal to RelayResult.Success,
                            Constants.mom to RelayResult.Success,
                            Constants.nos to RelayResult.Success,
                        ),
                ),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(device = "spec:width=1500px,height=2340px,dpi=440")
@Composable
fun BroadcastBannerSingleEventSheetPartialPreview() {
    val repost =
        RepostEvent(
            id = "",
            pubKey = "",
            createdAt = TimeUtils.now(),
            tags = emptyArray(),
            content = "",
            sig = "",
        )
    ThemeComparisonRow {
        MultiBroadcastDetailsSheetContent(
            persistentListOf(
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                    results =
                        mapOf(
                            Constants.antiprimal to RelayResult.Error("code"),
                            Constants.mom to RelayResult.Success,
                            Constants.nos to RelayResult.Success,
                        ),
                ),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(device = "spec:width=1500px,height=2340px,dpi=440")
@Composable
fun BroadcastBannerSingleEventSheetErrorsPreview() {
    val repost =
        RepostEvent(
            id = "",
            pubKey = "",
            createdAt = TimeUtils.now(),
            tags = emptyArray(),
            content = "",
            sig = "",
        )
    ThemeComparisonRow {
        MultiBroadcastDetailsSheetContent(
            persistentListOf(
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                    results =
                        mapOf(
                            Constants.antiprimal to RelayResult.Error("code"),
                            Constants.mom to RelayResult.Error("code"),
                            Constants.nos to RelayResult.Error("code"),
                        ),
                ),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(device = "spec:width=1500px,height=2340px,dpi=440")
@Composable
fun BroadcastBannerDoubleEventSheetInProgressStartPreview() {
    val repost =
        RepostEvent(
            id = "",
            pubKey = "",
            createdAt = TimeUtils.now(),
            tags = emptyArray(),
            content = "",
            sig = "",
        )
    ThemeComparisonRow {
        MultiBroadcastDetailsSheetContent(
            persistentListOf(
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                ),
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.mom, Constants.nos),
                ),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(device = "spec:width=1500px,height=2340px,dpi=440")
@Composable
fun BroadcastBannerDoubleEventSheetInProgressMiddlePreview() {
    val repost =
        RepostEvent(
            id = "",
            pubKey = "",
            createdAt = TimeUtils.now(),
            tags = emptyArray(),
            content = "",
            sig = "",
        )
    ThemeComparisonRow {
        MultiBroadcastDetailsSheetContent(
            persistentListOf(
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                    results = mapOf(Constants.mom to RelayResult.Success),
                ),
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.mom, Constants.nos),
                    results = mapOf(Constants.mom to RelayResult.Success),
                ),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(device = "spec:width=1500px,height=2340px,dpi=440")
@Composable
fun BroadcastBannerDoubleEventSheetInProgressEndPreview() {
    val repost =
        RepostEvent(
            id = "",
            pubKey = "",
            createdAt = TimeUtils.now(),
            tags = emptyArray(),
            content = "",
            sig = "",
        )
    ThemeComparisonRow {
        MultiBroadcastDetailsSheetContent(
            persistentListOf(
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                    results =
                        mapOf(
                            Constants.antiprimal to RelayResult.Success,
                            Constants.mom to RelayResult.Success,
                            Constants.nos to RelayResult.Success,
                        ),
                ),
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.mom, Constants.nos),
                    results =
                        mapOf(
                            Constants.mom to RelayResult.Success,
                            Constants.nos to RelayResult.Success,
                        ),
                ),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(device = "spec:width=1500px,height=2340px,dpi=440")
@Composable
fun BroadcastBannerDoubleEventSheetDoneAllGoodPreview() {
    val repost =
        RepostEvent(
            id = "",
            pubKey = "",
            createdAt = TimeUtils.now(),
            tags = emptyArray(),
            content = "",
            sig = "",
        )
    ThemeComparisonRow {
        MultiBroadcastDetailsSheetContent(
            persistentListOf(
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                    results =
                        mapOf(
                            Constants.antiprimal to RelayResult.Success,
                            Constants.mom to RelayResult.Success,
                            Constants.nos to RelayResult.Success,
                        ),
                ),
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.mom, Constants.nos),
                    results =
                        mapOf(
                            Constants.mom to RelayResult.Success,
                            Constants.nos to RelayResult.Success,
                        ),
                ),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(device = "spec:width=1500px,height=2340px,dpi=440")
@Composable
fun BroadcastBannerDoubleEventSheetDoneOneGoodPreview() {
    val repost =
        RepostEvent(
            id = "",
            pubKey = "",
            createdAt = TimeUtils.now(),
            tags = emptyArray(),
            content = "",
            sig = "",
        )
    ThemeComparisonRow {
        MultiBroadcastDetailsSheetContent(
            persistentListOf(
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                    results =
                        mapOf(
                            Constants.antiprimal to RelayResult.Success,
                            Constants.mom to RelayResult.Success,
                            Constants.nos to RelayResult.Success,
                        ),
                ),
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.mom, Constants.nos),
                    results =
                        mapOf(
                            Constants.mom to RelayResult.Error("code"),
                            Constants.nos to RelayResult.Success,
                        ),
                ),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(device = "spec:width=1500px,height=2340px,dpi=440")
@Composable
fun BroadcastBannerDoubleEventSheetDonePreview() {
    val repost =
        RepostEvent(
            id = "",
            pubKey = "",
            createdAt = TimeUtils.now(),
            tags = emptyArray(),
            content = "",
            sig = "",
        )
    ThemeComparisonRow {
        MultiBroadcastDetailsSheetContent(
            persistentListOf(
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                    results =
                        mapOf(
                            Constants.antiprimal to RelayResult.Success,
                            Constants.mom to RelayResult.Error("code"),
                            Constants.nos to RelayResult.Success,
                        ),
                ),
                BroadcastEvent(
                    id = Uuid.random().toString(),
                    event = repost,
                    targetRelays = listOf(Constants.mom, Constants.nos),
                    results =
                        mapOf(
                            Constants.mom to RelayResult.Timeout,
                            Constants.nos to RelayResult.Success,
                        ),
                ),
            ),
        )
    }
}
