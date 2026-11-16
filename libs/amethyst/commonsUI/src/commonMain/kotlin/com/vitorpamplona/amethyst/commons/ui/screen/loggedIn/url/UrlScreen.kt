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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.url

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.external_content_title
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewCard
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedLoaded
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.nip22Comments.LocalCurrentExternalScope
import com.vitorpamplona.amethyst.commons.ui.note.platform.rememberUrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.url.datasource.UrlFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.MaxWidthWithHorzPadding
import com.vitorpamplona.amethyst.commons.url.dal.UrlFeedViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip73ExternalIds.urls.UrlId

@Composable
fun UrlScreen(
    route: Route.Url,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val url = UrlId.toScopeOrNull(route.url) ?: return

    PrepareViewModelsUrlScreen(url, accountViewModel, nav)
}

@Suppress("StateFlowValueCalledInComposition")
@Composable
fun PrepareViewModelsUrlScreen(
    url: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val urlFeedViewModel: UrlFeedViewModel =
        rememberViewModel(
            key = url + "UrlFeedViewModel",
            factory =
                UrlFeedViewModel.Factory(
                    url,
                    accountViewModel.account.followOutboxesOrProxy.flow.value,
                    accountViewModel.account,
                ),
        )

    UrlScreen(url, urlFeedViewModel, accountViewModel, nav)
}

@Composable
fun UrlScreen(
    url: String,
    feedViewModel: UrlFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedViewModel)
    UrlFilterAssemblerSubscription(url, accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    Text(stringRes(Res.string.external_content_title))
                },
                nav = nav,
            )
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewUrlPostButton(url, accountViewModel, nav)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        CompositionLocalProvider(LocalCurrentExternalScope provides url) {
            RefresheableFeedView(
                feedViewModel,
                null,
                accountViewModel = accountViewModel,
                nav = nav,
                onLoaded = { state, listState ->
                    FeedLoaded(
                        state,
                        listState,
                        null,
                        accountViewModel,
                        nav,
                        header = { UrlScreenPreview(url, accountViewModel) },
                    )
                },
            )
        }
    }
}

@Composable
fun UrlScreenPreview(
    url: String,
    accountViewModel: AccountViewModel,
) {
    // Respect the data-saver/privacy gate: fetching the preview reaches out to the
    // third-party page, so when previews are off this stays the plain URL header.
    if (!accountViewModel.settings.showUrlPreview()) {
        DisplayUrlHeader(url, MaxWidthWithHorzPadding)
        return
    }

    when (val state = rememberUrlPreviewState(url, accountViewModel)) {
        is UrlPreviewState.Loaded -> {
            UrlPreviewCard(url, state.previewInfo)
        }

        else -> {
            DisplayUrlHeader(url, MaxWidthWithHorzPadding)
        }
    }
}

@Composable
fun DisplayUrlHeader(
    url: String,
    modifier: Modifier,
) {
    Text(
        url,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
