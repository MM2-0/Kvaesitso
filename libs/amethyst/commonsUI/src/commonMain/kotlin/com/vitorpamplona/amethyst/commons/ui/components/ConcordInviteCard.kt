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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.concord_home_title
import com.vitorpamplona.amethyst.commons.resources.concord_invite_card_join
import com.vitorpamplona.amethyst.commons.resources.concord_invite_card_subtitle
import com.vitorpamplona.amethyst.commons.ui.components.ClickableConcordInviteLink
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord05Invites.CommunityInvite
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The rich card form of a Concord invite link in note content — the analog of
 * NIP-29's `RelayGroupCard`. Tapping the card opens the redeem/join flow
 * ([Route.ConcordInvite], which keeps the full URL so the fragment token
 * survives). It fetches + unlocks the kind-33301 bundle in the background (via
 * [com.vitorpamplona.amethyst.commons.model.Account.peekConcordInvite]) to fill in the
 * community name; until then it shows a stable placeholder so layout never jumps.
 *
 * Degrades to [ClickableConcordInviteLink] (a plain link) if the URL doesn't parse.
 */
@Composable
fun ConcordInviteCard(
    linkText: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val parsed = remember(linkText) { ConcordActions.parseInviteLink(linkText) }
    if (parsed == null) {
        ClickableConcordInviteLink(linkText, nav)
        return
    }

    // Peek the bundle once per link to reveal the community name (null until it resolves).
    val invite by produceState<CommunityInvite?>(initialValue = null, linkText) {
        value = accountViewModel.account.concord.peekConcordInvite(linkText)
    }

    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    // Robohash seed: the community id once known (stable), else the link signer.
    val robotSeed = invite?.communityId ?: parsed.linkSignerPubKey
    val title = invite?.name?.takeIf { it.isNotBlank() } ?: stringRes(Res.string.concord_home_title)

    ElevatedCard(
        onClick = { nav.nav(Route.ConcordInvite(linkText)) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        ConcordInvitePreviewRow(
            robotSeed = robotSeed,
            title = title,
            subtitle = stringRes(Res.string.concord_invite_card_subtitle),
            accountViewModel = accountViewModel,
            autoPlayGif = autoPlayGif,
        ) {
            SymbolIcon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = stringRes(Res.string.concord_invite_card_join),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The avatar + title/subtitle row shared by [ConcordInviteCard] (in note content) and
 * the deep-link consent screen, so both render an invite identically. Purely
 * presentational — it performs no I/O, which is what lets the deep-link screen show a
 * preview without contacting the link's (attacker-supplied) relays before the user
 * consents.
 */
@Composable
fun ConcordInvitePreviewRow(
    robotSeed: String,
    title: String,
    subtitle: String,
    accountViewModel: AccountViewModel,
    autoPlayGif: Boolean,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RobohashFallbackAsyncImage(
            robot = robotSeed,
            model = null,
            contentDescription = title,
            modifier =
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
            autoPlayGif = autoPlayGif,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}
