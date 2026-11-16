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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.connection_success_rate_description
import com.vitorpamplona.amethyst.commons.resources.errors
import com.vitorpamplona.amethyst.commons.resources.read_from_relay
import com.vitorpamplona.amethyst.commons.resources.read_from_relay_description
import com.vitorpamplona.amethyst.commons.resources.spam
import com.vitorpamplona.amethyst.commons.resources.spam_description
import com.vitorpamplona.amethyst.commons.resources.uptime
import com.vitorpamplona.amethyst.commons.resources.write_to_relay
import com.vitorpamplona.amethyst.commons.resources.write_to_relay_description
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font12SP
import com.vitorpamplona.amethyst.commons.ui.theme.HalfStartPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.redColorOnSecondSurface
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.amethyst.commons.util.countToHumanReadable
import com.vitorpamplona.amethyst.commons.util.countToHumanReadableBytes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStat
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl

@Composable
@Preview
fun RelayStatusRowPreview() {
    RelayStatusRow(
        BasicRelaySetupInfo(
            "nos.lol".normalizeRelayUrl(),
            relayStat =
                RelayStat(
                    receivedBytes = 10000,
                    sentBytes = 10000,
                    spamCounter = 3,
                    errorCounter = 4,
                    pingInMs = 300,
                    compression = true,
                    connectionTentatives = 20,
                    connectionCompleted = 10,
                ),
            paidRelay = true,
            forcesTor = true,
            users = listOf(),
        ),
        onClick = {},
        modifier = Modifier,
        accountViewModel = mockAccountViewModel(),
    )
}

@Composable
fun RelayStatusRow(
    item: BasicRelaySetupInfo,
    onClick: () -> Unit,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier.weight(1f).pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            onClick()
                        },
                        onLongPress = {
                            accountViewModel.toastManager.toast(
                                Res.string.read_from_relay,
                                Res.string.read_from_relay_description,
                            )
                        },
                    )
                },
        ) {
            Icon(
                symbol = MaterialSymbols.Download,
                contentDescription = stringRes(Res.string.read_from_relay),
                modifier = Size15Modifier,
                tint = MaterialTheme.colorScheme.allGoodColor,
            )

            Text(
                text = countToHumanReadableBytes(item.relayStat.receivedBytes),
                maxLines = 1,
                fontSize = Font12SP,
                modifier = HalfStartPadding,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }

        Row(
            modifier =
                Modifier.weight(1f).pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            onClick()
                        },
                        onLongPress = {
                            accountViewModel.toastManager.toast(
                                Res.string.write_to_relay,
                                Res.string.write_to_relay_description,
                            )
                        },
                    )
                },
        ) {
            Icon(
                symbol = MaterialSymbols.Upload,
                stringRes(Res.string.write_to_relay),
                modifier = Size15Modifier,
                tint = MaterialTheme.colorScheme.allGoodColor,
            )

            Text(
                text = countToHumanReadableBytes(item.relayStat.sentBytes),
                maxLines = 1,
                fontSize = Font12SP,
                modifier = HalfStartPadding,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }

        Row(
            modifier =
                Modifier.weight(1.3f).pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            onClick()
                        },
                        onLongPress = {
                            accountViewModel.toastManager.toast(
                                Res.string.errors,
                                Res.string.connection_success_rate_description,
                            )
                        },
                    )
                },
        ) {
            val successRate = ((item.relayStat.connectionCompleted / item.relayStat.connectionTentatives.toFloat()) * 100).toInt()

            Icon(
                symbol = MaterialSymbols.SyncProblem,
                stringRes(Res.string.errors),
                modifier = Size15Modifier,
                tint =
                    if (successRate < 10) {
                        MaterialTheme.colorScheme.redColorOnSecondSurface
                    } else if (successRate < 60) {
                        MaterialTheme.colorScheme.warningColor
                    } else {
                        MaterialTheme.colorScheme.allGoodColor
                    },
            )

            Text(
                text = stringRes(Res.string.uptime, successRate),
                maxLines = 1,
                fontSize = Font12SP,
                modifier = HalfStartPadding,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }

        Row(
            modifier =
                Modifier.weight(0.9f).padding(end = 5.dp).pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            onClick()
                        },
                        onLongPress = {
                            accountViewModel.toastManager.toast(
                                Res.string.spam,
                                Res.string.spam_description,
                            )
                        },
                    )
                },
            horizontalArrangement = Arrangement.End,
        ) {
            Icon(
                symbol = MaterialSymbols.DeleteSweep,
                stringRes(Res.string.spam),
                modifier = Size15Modifier,
                tint =
                    if (item.relayStat.spamCounter > 0) {
                        MaterialTheme.colorScheme.warningColor
                    } else {
                        MaterialTheme.colorScheme.allGoodColor
                    },
            )

            Text(
                text = countToHumanReadable(item.relayStat.spamCounter, "spam"),
                maxLines = 1,
                fontSize = Font12SP,
                modifier = HalfStartPadding,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }
}
