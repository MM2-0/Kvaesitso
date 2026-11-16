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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.model.ConcordInviteResult
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.concord_invite_card_join
import com.vitorpamplona.amethyst.commons.resources.concord_invite_card_subtitle
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed_banned
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed_expired
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed_incompatible
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed_invalid
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed_not_saved
import com.vitorpamplona.amethyst.commons.resources.concord_invite_failed_revoked
import com.vitorpamplona.amethyst.commons.resources.concord_invite_preview_explainer
import com.vitorpamplona.amethyst.commons.resources.concord_invite_preview_relays
import com.vitorpamplona.amethyst.commons.resources.concord_invite_preview_unknown_name
import com.vitorpamplona.amethyst.commons.resources.concord_redeeming_invite
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_sign_events
import com.vitorpamplona.amethyst.commons.resources.retry
import com.vitorpamplona.amethyst.commons.ui.components.ConcordInvitePreviewRow
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord05Invites.ParsedInviteLink
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import org.jetbrains.compose.resources.StringResource

private sealed interface RedeemState {
    /** Showing the local preview, waiting for the user to tap Join. Nothing has been sent. */
    data object AwaitingConsent : RedeemState

    data object Working : RedeemState

    data class Done(
        val communityId: String,
    ) : RedeemState

    /**
     * The redeem failed. [messageRes] explains why; [canRetry] is true only for a
     * transient miss — an invalid or incompatible link can never succeed, so we don't
     * offer a retry that would just loop back onto the same spinner.
     */
    data class Failed(
        val messageRes: StringResource,
        val canRetry: Boolean,
    ) : RedeemState
}

/**
 * Redeems a Concord invite link (deep-link target for [Route.ConcordInvite]).
 *
 * **This screen must never act before the user consents.** It is reachable from any
 * `https://amethyst.social/invite/…` link on any web page, in any QR code, or in a
 * push — i.e. from a URL the user may never have meant to open. Redeeming is a
 * side-effecting act: it connects to up to three relay URLs *chosen by whoever minted
 * the link* (disclosing the user's IP to them), publishes a Guestbook JOIN signed by
 * the user's own identity to those relays, and writes the community into the user's
 * private kind-13302 list. Doing that on arrival turned any link into a one-click
 * deanonymize-and-enroll primitive, so the screen now opens on a local-only preview
 * and only calls [com.vitorpamplona.amethyst.commons.model.Account.joinConcordViaInvite] from
 * the Join button.
 *
 * Everything shown before that tap comes from decoding the URL itself
 * ([ConcordActions.parseInviteLink] — pure base64 + NIP-19, no I/O): the link's
 * signer key and the bootstrap relays it would contact. The community's *name* lives
 * inside the kind-33301 bundle, which only those relays can serve, so it is
 * deliberately left unknown rather than fetched — fetching it is precisely the IP
 * disclosure this screen exists to gate.
 */
@Composable
fun ConcordInviteScreen(
    link: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Local decode only: base64 fragment + NIP-19 naddr. No relay is contacted here.
    val parsed = remember(link) { ConcordActions.parseInviteLink(link) }

    var state by
        remember(link) {
            mutableStateOf<RedeemState>(
                if (parsed == null) {
                    RedeemState.Failed(Res.string.concord_invite_failed_invalid, canRetry = false)
                } else {
                    RedeemState.AwaitingConsent
                },
            )
        }

    LaunchedEffect(link, state) {
        if (state is RedeemState.Working) {
            val result =
                try {
                    accountViewModel.account.concord.joinConcordViaInvite(link)
                } catch (_: SignerExceptions.ReadOnlyException) {
                    // An npub login can never join: say why instead of offering a retry that loops.
                    state = RedeemState.Failed(Res.string.login_with_a_private_key_to_be_able_to_sign_events, canRetry = false)
                    return@LaunchedEffect
                } catch (_: SignerExceptions.ManuallyUnauthorizedException) {
                    // The user said no in their signer app: back to the Join button, nothing was sent.
                    state = RedeemState.AwaitingConsent
                    return@LaunchedEffect
                } catch (_: SignerExceptions) {
                    // Timed out, signer missing, ...: nothing joined, so let them retry.
                    null
                }
            state =
                when (result) {
                    null -> RedeemState.Failed(Res.string.concord_invite_failed, canRetry = true)
                    is ConcordInviteResult.Joined -> RedeemState.Done(result.communityId)
                    is ConcordInviteResult.InvalidLink ->
                        RedeemState.Failed(Res.string.concord_invite_failed_invalid, canRetry = false)
                    is ConcordInviteResult.Incompatible ->
                        RedeemState.Failed(Res.string.concord_invite_failed_incompatible, canRetry = false)
                    is ConcordInviteResult.Revoked ->
                        RedeemState.Failed(Res.string.concord_invite_failed_revoked, canRetry = false)
                    is ConcordInviteResult.Banned ->
                        RedeemState.Failed(Res.string.concord_invite_failed_banned, canRetry = false)
                    is ConcordInviteResult.Expired ->
                        RedeemState.Failed(Res.string.concord_invite_failed_expired, canRetry = false)
                    is ConcordInviteResult.NotReachable ->
                        RedeemState.Failed(Res.string.concord_invite_failed, canRetry = true)
                    is ConcordInviteResult.NotSaved ->
                        RedeemState.Failed(Res.string.concord_invite_failed_not_saved, canRetry = true)
                    // Only a Direct Invite accept reports this; a link redeem never does.
                    is ConcordInviteResult.InProgress ->
                        RedeemState.Failed(Res.string.concord_invite_failed, canRetry = true)
                }
        }
    }

    LaunchedEffect(state) {
        (state as? RedeemState.Done)?.let { done ->
            // Replace this invite screen with the community, dropping it from the back stack. If it
            // stayed, Back from the community would land on a consent screen for a community the
            // user has already joined — a dead end offering to re-do what just happened.
            nav.popUpTo(Route.ConcordServer(done.communityId), Route.ConcordInvite::class)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is RedeemState.AwaitingConsent ->
                parsed?.let {
                    ConcordInviteConsent(
                        parsed = it,
                        accountViewModel = accountViewModel,
                        onJoin = { state = RedeemState.Working },
                    )
                }

            is RedeemState.Working -> {
                CircularProgressIndicator()
                Text(
                    stringRes(Res.string.concord_redeeming_invite),
                    modifier = Modifier.padding(top = 16.dp),
                    // Explicit: this Column sits on the bare window background with no Surface
                    // above it, so LocalContentColor is still the M3 default black — which renders
                    // every one of these labels invisible in the dark theme.
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
            }

            is RedeemState.Failed -> {
                val failed = state as RedeemState.Failed
                Text(
                    stringRes(failed.messageRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                if (failed.canRetry) {
                    Button(
                        onClick = { state = RedeemState.Working },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(stringRes(Res.string.retry))
                    }
                }
            }

            is RedeemState.Done -> Unit
        }
    }
}

/**
 * The pre-consent preview. Renders only what the URL itself decodes to — the link
 * signer (used as the avatar seed) and the bootstrap relays the join would contact —
 * plus a plain-language statement of what tapping Join will do. It performs **no**
 * network I/O: the community name would require fetching the bundle from those very
 * relays, which is the IP disclosure the consent gate exists to prevent, so it shows
 * an explicit "name unknown until you join" instead.
 */
@Composable
private fun ConcordInviteConsent(
    parsed: ParsedInviteLink,
    accountViewModel: AccountViewModel,
    onJoin: () -> Unit,
) {
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    val relayList = remember(parsed) { parsed.fragment.relays.joinToString(", ") }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        ConcordInvitePreviewRow(
            robotSeed = parsed.linkSignerPubKey,
            title = stringRes(Res.string.concord_invite_card_subtitle),
            subtitle = stringRes(Res.string.concord_invite_preview_unknown_name),
            accountViewModel = accountViewModel,
            autoPlayGif = autoPlayGif,
        )
    }

    Text(
        stringRes(Res.string.concord_invite_preview_explainer),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 20.dp),
    )

    if (relayList.isNotEmpty()) {
        Text(
            stringRes(Res.string.concord_invite_preview_relays, relayList),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }

    Button(
        onClick = onJoin,
        modifier = Modifier.padding(top = 24.dp),
    ) {
        Text(stringRes(Res.string.concord_invite_card_join))
    }
}
