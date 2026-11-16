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
package com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.Nip05State
import com.vitorpamplona.amethyst.commons.relayClient.searchCommand.UserSearchDataSourceSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.user_suggestion_in_this_chat
import com.vitorpamplona.amethyst.commons.ui.layouts.listItem.SlimListItem
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.ObserveAndRenderNIP05VerifiedSymbol
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Font14SP
import com.vitorpamplona.amethyst.commons.ui.theme.NIP05IconSize
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.ui.theme.nip05
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

/** The dropdown's breathing room under the composer it floats over. */
private val SuggestionListPadding = PaddingValues(top = 10.dp)

@Composable
fun ShowUserSuggestionList(
    userSuggestions: UserSuggestionState,
    onSelect: (User) -> Unit,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onEmpty: @Composable () -> Unit = {},
    trailingContent: (@Composable (User) -> Unit)? = null,
    // Defaults suit this list's usual home: a dropdown floating over a composer, where an opaque
    // row and a divider per entry are what separate it from the text underneath. Inside a container
    // that already provides its own surface — a dialog — that chrome reads as a black box bolted on,
    // so those callers pass a transparent row and drop the dividers.
    itemColors: ListItemColors = ListItemDefaults.colors(),
    showDividers: Boolean = true,
    contentPadding: PaddingValues = SuggestionListPadding,
) {
    UserSearchDataSourceSubscription(userSuggestions, accountViewModel)

    val listState = rememberLazyListState()

    AnimateOnNewSearch(userSuggestions, listState)

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect {
                userSuggestions.invalidateData()
            }
        }
        launch(Dispatchers.IO) {
            LocalCache.live.deletedEventBundles.collect {
                userSuggestions.invalidateData()
            }
        }
    }

    WatchResponses(userSuggestions, listState, onSelect, accountViewModel, modifier, onEmpty, trailingContent, itemColors, showDividers, contentPadding)
}

@Composable
fun AnimateOnNewSearch(
    userSuggestions: UserSuggestionState,
    listState: LazyListState,
) {
    val searchTerm by userSuggestions.searchTerm.collectAsStateWithLifecycle("")

    LaunchedEffect(searchTerm) {
        listState.animateScrollToItem(0)
    }
}

@Composable
fun WatchResponses(
    userSuggestions: UserSuggestionState,
    listState: LazyListState,
    onSelect: (User) -> Unit,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onEmpty: @Composable () -> Unit = {},
    trailingContent: (@Composable (User) -> Unit)? = null,
    itemColors: ListItemColors = ListItemDefaults.colors(),
    showDividers: Boolean = true,
    contentPadding: PaddingValues = SuggestionListPadding,
) {
    val suggestions by userSuggestions.results.collectAsStateWithLifecycle(emptyList())

    if (suggestions.isNotEmpty()) {
        // Snapshot once per result list, not per row.
        val priority = remember(suggestions) { userSuggestions.priorityPubkeys() }

        LazyColumn(
            contentPadding = contentPadding,
            modifier = modifier,
            state = listState,
        ) {
            itemsIndexed(suggestions, key = { _, item -> item.pubkeyHex }) { _, item ->
                val trailing =
                    trailingContent
                        ?: if (item.pubkeyHex in priority) {
                            { InThisChatChip() }
                        } else {
                            null
                        }
                UserLine(item, accountViewModel, trailing, itemColors) { onSelect(item) }
                if (showDividers) {
                    HorizontalDivider(
                        thickness = DividerThickness,
                    )
                }
            }
        }
    } else {
        onEmpty()
    }
}

@Composable
private fun InThisChatChip() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = stringRes(Res.string.user_suggestion_in_this_chat),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun UserLine(
    baseUser: User,
    accountViewModel: AccountViewModel,
    trailingContent: (@Composable (User) -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(),
    onClick: () -> Unit,
) {
    SlimListItem(
        colors = colors,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = {
            ClickableUserPicture(baseUser, Size55dp, accountViewModel = accountViewModel, onClick = null)
        },
        headlineContent = {
            UsernameDisplay(baseUser, accountViewModel = accountViewModel)
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                WatchAndDisplayNip05Row(baseUser, accountViewModel)
            }
        },
        trailingContent = trailingContent?.let { { it(baseUser) } },
    )
}

@Composable
private fun WatchAndDisplayNip05Row(
    user: User,
    accountViewModel: AccountViewModel,
) {
    val nip05StateMetadata by user.nip05State().flow.collectAsStateWithLifecycle()

    when (val nip05State = nip05StateMetadata) {
        is Nip05State.Exists -> {
            NonClickableObserveAndDisplayNIP05(nip05State, accountViewModel)
        }

        else -> {
            Text(
                text = user.pubkeyDisplayHex(),
                fontSize = Font14SP,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NonClickableObserveAndDisplayNIP05(
    nip05State: Nip05State.Exists,
    accountViewModel: AccountViewModel,
) {
    if (nip05State.nip05.hasLocalPart()) {
        Text(
            text = remember(nip05State) { AnnotatedString(nip05State.nip05.name) },
            fontSize = Font14SP,
            color = MaterialTheme.colorScheme.nip05,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    ObserveAndRenderNIP05VerifiedSymbol(nip05State, 1, NIP05IconSize, accountViewModel)

    Text(
        text = nip05State.nip05.domain,
        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.nip05, fontSize = Font14SP),
        maxLines = 1,
        overflow = TextOverflow.Visible,
    )
}
