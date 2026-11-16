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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.navigation.DiscoverTab
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dvm_offline
import com.vitorpamplona.amethyst.commons.resources.favorite_dvms_add_more
import com.vitorpamplona.amethyst.commons.resources.favorite_dvms_empty_cta
import com.vitorpamplona.amethyst.commons.resources.favorite_dvms_empty_headline
import com.vitorpamplona.amethyst.commons.resources.favorite_dvms_empty_step1
import com.vitorpamplona.amethyst.commons.resources.favorite_dvms_empty_step2
import com.vitorpamplona.amethyst.commons.resources.favorite_dvms_explainer
import com.vitorpamplona.amethyst.commons.resources.favorite_dvms_title
import com.vitorpamplona.amethyst.commons.resources.remove_dvm_from_favorites
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms.observeAppDefinition
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms.rememberDvmHeartbeatFresh
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.SimpleImage35Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch

private const val STAR_INLINE_ID = "star"

@Composable
fun FavoriteAlgoFeedsListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val favorites by accountViewModel.account.favoriteAlgoFeedsList.flowNotes
        .collectAsStateWithLifecycle()

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarWithBackButton(
                caption = stringRes(Res.string.favorite_dvms_title),
                nav = nav,
            )
        },
        bottomBar = {
            AppBottomBar(Route.EditFavoriteAlgoFeeds, nav, accountViewModel) { route ->
                if (route == Route.EditFavoriteAlgoFeeds) {
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 16.dp,
                        top = padding.calculateTopPadding(),
                        end = 16.dp,
                        bottom = padding.calculateBottomPadding(),
                    ).consumeWindowInsets(padding),
        ) {
            if (favorites.isEmpty()) {
                Text(
                    text = stringRes(Res.string.favorite_dvms_explainer),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
                FavoriteAlgoFeedsEmptyState(nav)
            } else {
                FavoriteAlgoFeedList(favorites, listState, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun FavoriteAlgoFeedsEmptyState(nav: INav) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringRes(Res.string.favorite_dvms_empty_headline),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FavoriteAlgoFeedsEmptySteps(
                ctaLabel = stringRes(Res.string.favorite_dvms_empty_cta),
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        BrowseAlgosButton(
            label = stringRes(Res.string.favorite_dvms_empty_cta),
            nav = nav,
        )
    }
}

@Composable
private fun ColumnScope.FavoriteAlgoFeedList(
    favorites: List<AddressableNote>,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LazyColumn(
        modifier = Modifier.weight(1f),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = FeedPadding,
    ) {
        items(
            items = favorites,
            key = { it.address.toValue() },
        ) { feedNote ->
            FavoriteAlgoFeedRow(
                feedNote = feedNote,
                accountViewModel = accountViewModel,
                onOpen = { nav.nav(Route.ContentDiscovery(feedNote.idHex)) },
                onRemove = { accountViewModel.unfollowFavoriteAlgoFeed(feedNote.address) },
            )
        }
    }
    BrowseAlgosButton(
        label = stringRes(Res.string.favorite_dvms_add_more),
        nav = nav,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 32.dp),
    )
}

@Composable
private fun BrowseAlgosButton(
    label: String,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { nav.nav(Route.Discover(initialTab = DiscoverTab.ALGOS)) },
        modifier = modifier,
    ) {
        Text(text = label)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteAlgoFeedRow(
    feedNote: AddressableNote,
    accountViewModel: AccountViewModel,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val card = observeAppDefinition(feedNote, accountViewModel)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        card.cover?.let { cover ->
            Box(contentAlignment = BottomStart) {
                MyAsyncImage(
                    imageUrl = cover,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    mainImageModifier = Modifier,
                    loadedImageModifier = SimpleImage35Modifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = {
                        feedNote.author?.let { author ->
                            BannerImage(author, SimpleImage35Modifier, accountViewModel)
                        }
                    },
                    onError = {
                        feedNote.author?.let { author ->
                            BannerImage(author, SimpleImage35Modifier, accountViewModel)
                        }
                    },
                )
            }
        } ?: run {
            feedNote.author?.let { author ->
                BannerImage(author, SimpleImage35Modifier, accountViewModel)
            }
        }

        Spacer(modifier = DoubleHorzSpacer)

        Column(
            modifier = Modifier.weight(1f),
        ) {
            val heartbeatFresh by rememberDvmHeartbeatFresh(feedNote.address, accountViewModel)
            val displayName = card.name.ifBlank { feedNote.dTag() }
            val offlineLabel = stringRes(Res.string.dvm_offline)
            Text(
                text = if (heartbeatFresh) displayName else "$displayName \u2022",
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    if (heartbeatFresh) {
                        Modifier
                    } else {
                        Modifier.semantics { contentDescription = "$displayName, $offlineLabel" }
                    },
            )
            card.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = StdVertSpacer)
                Text(
                    text = it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.grayText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        IconButton(onClick = onRemove) {
            Icon(
                symbol = MaterialSymbols.Delete,
                contentDescription = stringRes(Res.string.remove_dvm_from_favorites),
            )
        }
    }
}

@Composable
private fun FavoriteAlgoFeedsEmptySteps(
    ctaLabel: String,
    modifier: Modifier = Modifier,
) {
    val step2Template = stringRes(Res.string.favorite_dvms_empty_step2)
    val primaryColor = MaterialTheme.colorScheme.primary

    val step2Text =
        remember(step2Template) {
            val parts = step2Template.split("%1\$s", limit = 2)
            buildAnnotatedString {
                append(parts.first())
                appendInlineContent(STAR_INLINE_ID, "star")
                if (parts.size > 1) append(parts[1])
            }
        }

    val starInlineContent =
        remember(primaryColor) {
            mapOf(
                STAR_INLINE_ID to
                    InlineTextContent(
                        Placeholder(
                            width = 18.sp,
                            height = 18.sp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                        ),
                    ) {
                        Icon(
                            symbol = MaterialSymbols.StarBorder,
                            contentDescription = null,
                            tint = primaryColor,
                        )
                    },
            )
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NumberedStep(number = "1.") {
            Text(
                text = stringRes(Res.string.favorite_dvms_empty_step1, ctaLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
        NumberedStep(number = "2.") {
            Text(
                text = step2Text,
                inlineContent = starInlineContent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

@Composable
private fun NumberedStep(
    number: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 8.dp),
        )
        content()
    }
}
