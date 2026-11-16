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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.apps

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.profile.header.apps.UserAppRecommendationsFeedViewModel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.profile_app_recommendations_title
import com.vitorpamplona.amethyst.commons.resources.profile_apps_empty_hint
import com.vitorpamplona.amethyst.commons.resources.profile_apps_header
import com.vitorpamplona.amethyst.commons.resources.profile_apps_header_empty
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.apps.AppRecommendationChip
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun DisplayAppRecommendations(
    appRecommendations: UserAppRecommendationsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by appRecommendations.feedState.feedContent.collectAsStateWithLifecycle()

    LaunchedEffect(key1 = Unit) { appRecommendations.invalidateData() }

    val isMe = appRecommendations.user.pubkeyHex == accountViewModel.userProfile().pubkeyHex

    CrossfadeIfEnabled(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
    ) { state ->
        when (state) {
            is FeedState.Loaded -> {
                Recommends(state, isMe, accountViewModel, nav)
            }

            is FeedState.Empty -> {
                // Owners see the section with the edit affordance even before
                // their first recommendation, so the feature is discoverable.
                if (isMe) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        AppsHeader(appCount = 0, isMe = true, nav = nav)
                        Text(
                            text = stringRes(Res.string.profile_apps_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            else -> {}
        }
    }
}

@Composable
private fun AppsHeader(
    appCount: Int,
    isMe: Boolean,
    nav: INav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text =
                if (appCount > 0) {
                    stringRes(Res.string.profile_apps_header, appCount)
                } else {
                    stringRes(Res.string.profile_apps_header_empty)
                },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (isMe) {
            IconButton(
                onClick = { nav.nav(Route.ProfileAppRecommendations) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Settings,
                    contentDescription = stringRes(Res.string.profile_app_recommendations_title),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun Recommends(
    loaded: FeedState.Loaded,
    isMe: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        AppsHeader(appCount = items.list.size, isMe = isMe, nav = nav)

        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.list.forEach { app -> AppRecommendationChip(app, accountViewModel, nav) }
        }
    }
}
