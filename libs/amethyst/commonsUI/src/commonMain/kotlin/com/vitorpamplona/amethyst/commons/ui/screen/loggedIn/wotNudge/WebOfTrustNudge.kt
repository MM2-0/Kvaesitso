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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wotNudge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.wot.setupErrorText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.wot.setupStepText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.WebOfTrustSetup
import com.vitorpamplona.amethyst.commons.viewmodels.WebOfTrustViewModel
import com.vitorpamplona.amethyst.commons.wot.nudge.WebOfTrustNudgeRules
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderHttp
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderOnboarding
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Whether Home shows its invitation to turn on web of trust, and the view model running it: the
 * circle card ([WebOfTrustNudge]) is offered to an account that can sign, follows someone and has
 * no trust provider, and stays through a sign-up started from it until the user puts it away.
 * Null when it does not show, so the feed only adds a row for it when there is one.
 */
@Composable
fun rememberWebOfTrustNudge(accountViewModel: AccountViewModel): WebOfTrustViewModel? {
    val account = accountViewModel.account
    // Signing up needs a signer, and a front end that can reach the provider's API.
    if (!account.isWriteable() || accountViewModel.host.trustProviderHttp === TrustProviderHttp.Unsupported) return null

    val viewModel: WebOfTrustViewModel =
        rememberViewModel(key = "wot-nudge-${account.signer.pubKey}", factory = WebOfTrustViewModel.Factory(account, accountViewModel.host.trustProviderHttp, accountViewModel.nip05ClientBuilder()))
    val provider by account.trustProviderList.liveUserRankProvider.collectAsStateWithLifecycle()
    val follows by account.kind3FollowList.flow.collectAsStateWithLifecycle()
    val hiddenUntil by account.settings.webOfTrustNudgeHiddenUntil.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val completed by viewModel.completed.collectAsStateWithLifecycle()

    // A new account's follow list holds the user itself.
    val followCount = follows.authors.count { it != account.signer.pubKey }
    val offered = WebOfTrustNudgeRules.shouldOffer(followCount, provider != null, hiddenUntil, TimeUtils.now())
    return if (offered || setup != WebOfTrustSetup.Idle || completed != null) viewModel else null
}

/** The circle card for [viewModel] from [rememberWebOfTrustNudge]. */
@Composable
fun WebOfTrustNudge(
    viewModel: WebOfTrustViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val account = accountViewModel.account
    val follows by account.kind3FollowList.flow.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    val others = remember(follows) { follows.authors - account.signer.pubKey }

    // Faces with a picture first: reads every follow in the cache.
    val faces by produceState(emptyList<HexKey>(), others) {
        value =
            withContext(Dispatchers.Default) {
                val withPicture = others.filter { account.cache.getUserIfExists(it)?.profilePicture() != null }
                (withPicture + others).distinct().take(6)
            }
    }

    val providers =
        remember(viewModel) {
            viewModel.providers.map { onboarding ->
                CircleProvider(onboarding.id, onboarding.name) { size -> ProviderPicture(onboarding, viewModel, size, accountViewModel, nav) }
            }
        }
    if (providers.isEmpty()) return
    var selectedId by rememberSaveable { mutableStateOf(providers.first().id) }
    val selected = providers.firstOrNull { it.id == selectedId } ?: providers.first()
    val onboarding = viewModel.providers.first { it.id == selected.id }

    val activation =
        when (val current = setup) {
            is WebOfTrustSetup.Running, is WebOfTrustSetup.Saving -> CircleActivation.Working(setupStepText(current).orEmpty())
            is WebOfTrustSetup.Failed -> CircleActivation.Failed(setupErrorText(current, selected.name))
            WebOfTrustSetup.Idle -> if (completed != null) CircleActivation.Activated else CircleActivation.Offer
        }
    val working = activation is CircleActivation.Working

    val hide = { until: Long ->
        viewModel.acknowledge()
        account.settings.hideWebOfTrustNudgeUntil(until)
    }

    CircleHeroCard(
        CircleNudge(
            self = userFace(account.signer.pubKey, accountViewModel, nav),
            faces = faces.map { key -> userFace(key, accountViewModel, nav) },
            followCount = others.size,
            // The circle the provider will compute, on average: nothing here counts it.
            extendedCount = maxOf(WebOfTrustNudgeRules.TYPICAL_CIRCLE, others.size) - others.size,
            providers = providers,
            selected = selected,
            activation = activation,
            onSelect = { selectedId = it.id },
            onActivate = { viewModel.setUp(onboarding) },
            onNotNow = { hide(TimeUtils.now() + WebOfTrustNudgeRules.SNOOZE_SECONDS) },
            // The sign-up runs in the view model: putting the card away mid-way would hide its progress.
            onDismiss = { if (!working) hide(WebOfTrustNudgeRules.FOREVER) },
        ),
        modifier = modifier,
    )
}

private fun userFace(
    key: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
): NudgeFace = { size -> UserPicture(key, size, accountViewModel = accountViewModel, nav = nav) }

/** The provider's own account picture when it has one, else a plain tile. */
@Composable
private fun ProviderPicture(
    onboarding: TrustProviderOnboarding,
    viewModel: WebOfTrustViewModel,
    size: Dp,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val key by produceState<HexKey?>(null, onboarding.id) { value = viewModel.houseObserverKey(onboarding) }
    val house = key
    if (house != null) {
        UserPicture(house, size, accountViewModel = accountViewModel, nav = nav)
    } else {
        Box(Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(MaterialSymbols.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
