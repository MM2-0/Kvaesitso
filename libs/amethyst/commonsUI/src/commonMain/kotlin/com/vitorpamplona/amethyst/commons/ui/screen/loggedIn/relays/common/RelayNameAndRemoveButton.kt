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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ic_tor
import com.vitorpamplona.amethyst.commons.resources.paid_relay
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.resources.tor_relay
import com.vitorpamplona.amethyst.commons.resources.tor_waiting
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.amethyst.commons.tor.isHoldingRelays
import com.vitorpamplona.amethyst.commons.tor.waitingForTor
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font12SP
import com.vitorpamplona.amethyst.commons.ui.theme.LightRedColor
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RelayNameAndRemoveButton(
    item: BasicRelaySetupInfo,
    onClick: () -> Unit,
    onDelete: ((BasicRelaySetupInfo) -> Unit)?,
    modifier: Modifier,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.relay.displayUrl(),
                // Ellipsizes a long url instead of pushing the badges after it out of the row.
                modifier =
                    Modifier.weight(1f, fill = false).combinedClickable(
                        onClick = onClick,
                        onLongClick = {
                            scope.launch {
                                clipboardManager.setText(item.relay.url)
                            }
                        },
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.paidRelay) {
                Icon(
                    symbol = MaterialSymbols.Paid,
                    contentDescription = stringRes(id = Res.string.paid_relay),
                    modifier =
                        Modifier
                            .padding(start = 5.dp)
                            .size(14.dp),
                    tint = MaterialTheme.colorScheme.allGoodColor,
                )
            }

            if (item.forcesTor) {
                TorRelayBadge()
            }
        }

        if (onDelete != null) {
            IconButton(
                modifier = Modifier.size(30.dp),
                onClick = { onDelete(item) },
            ) {
                Icon(
                    symbol = MaterialSymbols.Cancel,
                    contentDescription = stringRes(id = Res.string.remove),
                    modifier =
                        Modifier
                            .padding(start = 10.dp)
                            .size(15.dp),
                    tint = LightRedColor,
                )
            }
        }
    }
}

/**
 * Marks a relay that is reached through Tor. While Tor cannot carry traffic the relay is not dialed
 * at all (the rule in [waitingForTor]), so the badge greys out and says so: otherwise the relay just
 * looks quiet, with nothing to tie it to Tor.
 */
@Composable
private fun TorRelayBadge() {
    val appServices = LocalAppServices.current
    // Unknown until the first status arrives, so a ready Tor never flashes "waiting" on open.
    val torStatus by remember(appServices) { appServices.torStatus }
        .collectAsStateWithLifecycle<TorServiceStatus?>(null)
    val waiting = torStatus?.isHoldingRelays == true

    Icon(
        painter = painterRes(Res.drawable.ic_tor, 2),
        contentDescription = stringRes(id = Res.string.tor_relay),
        modifier =
            Modifier
                .padding(start = 5.dp)
                .size(14.dp),
        tint = if (waiting) MaterialTheme.colorScheme.placeholderText else MaterialTheme.colorScheme.allGoodColor,
    )

    if (waiting) {
        Text(
            text = stringRes(Res.string.tor_waiting),
            modifier = Modifier.padding(start = 4.dp),
            color = MaterialTheme.colorScheme.placeholderText,
            fontSize = Font12SP,
            maxLines = 1,
        )
    }
}
