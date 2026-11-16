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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_invite_after_browser
import com.vitorpamplona.amethyst.commons.resources.buzz_invite_body
import com.vitorpamplona.amethyst.commons.resources.buzz_invite_continue
import com.vitorpamplona.amethyst.commons.resources.buzz_invite_expired
import com.vitorpamplona.amethyst.commons.resources.buzz_invite_heading
import com.vitorpamplona.amethyst.commons.resources.buzz_invite_invalid
import com.vitorpamplona.amethyst.commons.resources.buzz_invite_open_workspace
import com.vitorpamplona.amethyst.commons.resources.buzz_invite_role
import com.vitorpamplona.amethyst.commons.resources.buzz_invite_title
import com.vitorpamplona.amethyst.commons.resources.buzz_invite_workspace
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.AppLauncher
import com.vitorpamplona.amethyst.commons.ui.platform.rememberAppLauncher
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.invite.BuzzInviteLink
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.launch

/**
 * Landing screen for a Buzz workspace invite link (`https://<host>/invite/<token>`), reached
 * from the deep-link / tapped-link / search interceptors instead of the external browser.
 *
 * A Buzz invite is redeemed over HTTP (accept the join policy, then a NIP-98-signed claim) —
 * a flow the Buzz web app already implements and drives through `window.nostr`. So rather than
 * re-implement the legally-sensitive age/privacy consent natively, this confirms the workspace,
 * marks its relay as a Buzz dialect, and hands the URL to the in-app `window.nostr` browser
 * ([AppLauncher.launchUrl] → the sandboxed WebView), where the SPA signs the claim with
 * the user's key. The user returns here (or to the app) once enrolled.
 */
@Composable
fun BuzzInviteScreen(
    link: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val invite = remember(link) { BuzzInviteLink.parse(link) }
    val appLauncher = rememberAppLauncher()
    val scope = rememberCoroutineScope()
    var launched by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.buzz_invite_title), nav) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (invite == null) {
                Text(stringRes(Res.string.buzz_invite_invalid), style = MaterialTheme.typography.bodyLarge)
                return@Column
            }

            val expired = remember(invite) { invite.isExpired(TimeUtils.now()) }

            Spacer(Modifier.size(8.dp))
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp),
                )
            }

            Text(
                text = stringRes(Res.string.buzz_invite_heading),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InviteRow(stringRes(Res.string.buzz_invite_workspace), invite.host)
                    InviteRow(stringRes(Res.string.buzz_invite_role), invite.role)
                }
            }

            Text(
                text = stringRes(Res.string.buzz_invite_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (expired) {
                Text(
                    text = stringRes(Res.string.buzz_invite_expired),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.weight(1f))

            if (launched) {
                // After the browser hand-off: gently point back to the workspace, which now
                // authenticates + discovers its channels (the claim granted membership).
                Text(
                    text = stringRes(Res.string.buzz_invite_after_browser),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(4.dp))
                Button(
                    onClick = {
                        // Open the relay's group screen, which now lists the channels the claim just
                        // granted you membership to (importable into Amethyst).
                        RelayUrlNormalizer.normalizeOrNull(invite.relayUrl())?.let { relay ->
                            nav.newStack(Route.RelayGroupServer(relay.url))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(symbol = MaterialSymbols.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringRes(Res.string.buzz_invite_open_workspace))
                }
            } else {
                Button(
                    onClick = {
                        // Remember the workspace as joined (persisted; marks it a Buzz dialect) and
                        // pre-approve NIP-42 auth to its relay so the read-only channel/DM discovery
                        // authenticates without a prompt, then hand off to the in-app window.nostr
                        // browser to accept terms + sign the claim.
                        RelayUrlNormalizer.normalizeOrNull(invite.relayUrl())?.let { relay ->
                            accountViewModel.account.buzzWorkspaces.join(relay)
                            scope.launch { accountViewModel.account.relayAuthLedger.setDecision(relay.url, RelayAuthDecision.ALLOW) }
                        }
                        appLauncher.launchUrl(link, nav = nav)
                        launched = true
                    },
                    enabled = !expired,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(symbol = MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringRes(Res.string.buzz_invite_continue))
                }
            }
        }
    }
}

@Composable
private fun InviteRow(
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
