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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserAssertionsScore
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.profile_in_network
import com.vitorpamplona.amethyst.commons.resources.profile_not_scored
import com.vitorpamplona.amethyst.commons.resources.profile_outside_network
import com.vitorpamplona.amethyst.commons.resources.profile_trust_score
import com.vitorpamplona.amethyst.commons.resources.profile_you_follow
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.wot.network.TrustVerdict

/**
 * Whether [user] is in the account's Web of Trust network, under their name on the profile.
 * Nothing while no network is active and nothing on the user's own profile. Tapping it opens
 * the Web of Trust settings, where the minimum score lives.
 */
@Composable
fun ProfileTrustBadge(
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val verdicts by accountViewModel.account.trustVerdicts.collectAsStateWithLifecycle()
    if (!verdicts.isActive) return

    // Also asks the provider for this person's card, so a newer score shows up here.
    val rank by observeUserAssertionsScore(user, accountViewModel)
    val verdict = remember(user, verdicts) { verdicts.explain(user.pubkeyHex) }

    TrustVerdictBadge(verdict, rank, onClick = { nav.nav(Route.WebOfTrust) })
}

/** The badge itself: "In your network · Score 42", "Outside your network · Not scored", … */
@Composable
fun TrustVerdictBadge(
    verdict: TrustVerdict,
    rank: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isKnown = verdict.isKnown ?: return
    if (verdict == TrustVerdict.SELF) return

    val scheme = MaterialTheme.colorScheme
    val title = stringRes(if (isKnown) Res.string.profile_in_network else Res.string.profile_outside_network)
    val detail =
        when {
            rank != null && rank > 0 -> stringRes(Res.string.profile_trust_score, rank)
            verdict == TrustVerdict.FOLLOW -> stringRes(Res.string.profile_you_follow)
            else -> stringRes(Res.string.profile_not_scored)
        }
    val tint = if (isKnown) scheme.primary else scheme.onSurfaceVariant

    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (isKnown) scheme.primary.copy(alpha = 0.12f) else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Shield,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint,
            filled = isKnown,
        )
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tint)
        Text("·", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        Text(detail, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
    }
}
