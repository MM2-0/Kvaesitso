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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.customFeeds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinition
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_delete
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_delete_confirm
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_edit
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_empty
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_new
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_summary_authors
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_summary_relays
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The feeds this user built for Home. Tapping one shows it on Home; each can be edited or deleted,
 * and the button adds a new one. The same feeds appear in the Home feed picker.
 */
@Composable
fun CustomFeedsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val settings = accountViewModel.account.settings
    val feeds by settings.customFeeds.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<FeedDefinition?>(null) }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.custom_feeds_title), nav) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.nav(Route.EditCustomFeed()) },
                icon = { Icon(MaterialSymbols.Add, contentDescription = null) },
                text = { Text(stringRes(Res.string.custom_feeds_new)) },
            )
        },
    ) { padding ->
        if (feeds.isEmpty()) {
            Text(
                text = stringRes(Res.string.custom_feeds_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(24.dp),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            items(feeds, key = { it.id }) { feed ->
                CustomFeedRow(
                    feed = feed,
                    onOpen = {
                        settings.changeDefaultHomeFollowList(TopFilter.CustomFeed(feed.id))
                        nav.newStack(Route.Home)
                    },
                    onEdit = { nav.nav(Route.EditCustomFeed(feed.id)) },
                    onDelete = { toDelete = feed },
                )
                HorizontalDivider()
            }
        }
    }

    toDelete?.let { feed ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            text = { Text(stringRes(Res.string.custom_feeds_delete_confirm, feed.displayName())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        settings.deleteCustomFeed(feed.id)
                        toDelete = null
                    },
                ) { Text(stringRes(Res.string.custom_feeds_delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringRes(Res.string.cancel)) } },
        )
    }
}

fun FeedDefinition.displayName() = "$emoji $name".trim()

@Composable
private fun CustomFeedRow(
    feed: FeedDefinition,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(feed.displayName(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val summary = summaryOf(feed)
            if (summary.isNotEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(MaterialSymbols.Edit, contentDescription = stringRes(Res.string.custom_feeds_edit), modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(MaterialSymbols.Delete, contentDescription = stringRes(Res.string.custom_feeds_delete), modifier = Modifier.size(20.dp))
        }
    }
}

/** "3 people · #bitcoin #nostr · 2 relays" */
@Composable
private fun summaryOf(feed: FeedDefinition): String {
    val source = feed.source as? FeedSource.Filter ?: return ""
    val parts = mutableListOf<String>()
    if (source.authors.isNotEmpty()) parts += pluralStringRes(Res.plurals.custom_feeds_summary_authors, source.authors.size, source.authors.size)
    if (source.hashtags.isNotEmpty()) parts += source.hashtags.joinToString(" ") { "#$it" }
    if (source.relays.isNotEmpty()) parts += pluralStringRes(Res.plurals.custom_feeds_summary_relays, source.relays.size, source.relays.size)
    return parts.joinToString(" · ")
}
