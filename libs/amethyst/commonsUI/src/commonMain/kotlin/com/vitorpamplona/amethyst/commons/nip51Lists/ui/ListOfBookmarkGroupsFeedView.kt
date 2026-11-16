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
package com.vitorpamplona.amethyst.commons.nip51Lists.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip51Lists.BookmarkListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.BookmarkType
import com.vitorpamplona.amethyst.commons.model.nip51Lists.GitRepositoryListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.OldBookmarkListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.PinListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.bookmarkSets.BookmarkSet
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.bookmark_list_icon_label
import com.vitorpamplona.amethyst.commons.resources.bookmarks_explainer
import com.vitorpamplona.amethyst.commons.resources.bookmarks_title
import com.vitorpamplona.amethyst.commons.resources.old_bookmarks_explainer
import com.vitorpamplona.amethyst.commons.resources.old_bookmarks_title
import com.vitorpamplona.amethyst.commons.resources.pinned_notes
import com.vitorpamplona.amethyst.commons.resources.pinned_notes_explainer
import com.vitorpamplona.amethyst.commons.resources.podcast_bookmarks
import com.vitorpamplona.amethyst.commons.resources.podcast_bookmarks_explainer
import com.vitorpamplona.amethyst.commons.resources.repository_bookmarks
import com.vitorpamplona.amethyst.commons.resources.repository_bookmarks_explainer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size40Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.isPodcastEvent
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ListOfBookmarkGroupsFeedView(
    defaultBookmarks: BookmarkListState,
    oldBookmarks: OldBookmarkListState,
    pinnedNotes: PinListState,
    repositories: GitRepositoryListState,
    groupListFeedSource: StateFlow<List<BookmarkSet>>,
    openDefaultBookmarks: () -> Unit,
    openOldBookmarks: () -> Unit,
    openPinnedNotes: () -> Unit,
    openRepositories: () -> Unit,
    openPodcasts: () -> Unit,
    onOpenItem: (String, BookmarkType) -> Unit,
    onRenameItem: (targetBookmarkGroup: BookmarkSet) -> Unit,
    onItemDescriptionChange: (bookmarkGroup: BookmarkSet) -> Unit,
    onItemClone: (bookmarkGroup: BookmarkSet, customName: String?, customDesc: String?) -> Unit,
    onDeleteItem: (bookmarkGroup: BookmarkSet) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val bookmarkGroupFeedState by groupListFeedSource.collectAsStateWithLifecycle()

    LazyColumn(
        state = listState,
        contentPadding = FeedPadding,
    ) {
        item {
            DefaultBookmarkList(defaultBookmarks, openDefaultBookmarks)
            HorizontalDivider(thickness = DividerThickness)
        }

        item {
            OldBookmarkList(oldBookmarks, openOldBookmarks)
            HorizontalDivider(thickness = DividerThickness)
        }

        item {
            PinnedNotesList(pinnedNotes, openPinnedNotes)
            HorizontalDivider(thickness = DividerThickness)
        }

        item {
            RepositoriesBookmarkList(repositories, openRepositories)
            HorizontalDivider(thickness = DividerThickness)
        }

        item {
            PodcastsBookmarkList(defaultBookmarks, openPodcasts)
            HorizontalDivider(thickness = DividerThickness)
        }

        itemsIndexed(
            bookmarkGroupFeedState,
            key = { _: Int, item: BookmarkSet -> item.identifier },
        ) { _, groupItem ->
            BookmarkGroupItem(
                modifier = Modifier.fillMaxSize().animateItem(),
                bookmarkList = groupItem,
                onClick = { bookmarkType -> onOpenItem(groupItem.identifier, bookmarkType) },
                onRename = { onRenameItem(groupItem) },
                onDescriptionChange = { onItemDescriptionChange(groupItem) },
                onClone = { cloneName, cloneDescription -> onItemClone(groupItem, cloneName, cloneDescription) },
                onDelete = { onDeleteItem(groupItem) },
            )
            HorizontalDivider(thickness = DividerThickness)
        }
    }
}

@Composable
fun DefaultBookmarkList(
    defaultBookmarks: BookmarkListState,
    openDefaultBookmarks: () -> Unit,
) {
    val bookmarkState by defaultBookmarks.bookmarks.collectAsStateWithLifecycle()

    ListItem(
        modifier = Modifier.clickable(onClick = openDefaultBookmarks),
        headlineContent = {
            Text(stringRes(Res.string.bookmarks_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringRes(Res.string.bookmarks_explainer),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                )
            }
        },
        leadingContent = {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    symbol = MaterialSymbols.BookmarkBorder,
                    contentDescription = stringRes(Res.string.bookmark_list_icon_label),
                    modifier = Size40Modifier,
                )
                Spacer(StdVertSpacer)
                BookmarkMembershipStatusAndNumberDisplay(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    postBookmarksSize = bookmarkState.public.size + bookmarkState.private.size,
                    articleBookmarksSize = 0,
                )
            }
        },
    )
}

@Composable
fun PinnedNotesList(
    pinnedNotes: PinListState,
    openPinnedNotes: () -> Unit,
) {
    val pinState by pinnedNotes.pinnedNotesList.collectAsStateWithLifecycle()

    ListItem(
        modifier = Modifier.clickable(onClick = openPinnedNotes),
        headlineContent = {
            Text(stringRes(Res.string.pinned_notes), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringRes(Res.string.pinned_notes_explainer),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                )
            }
        },
        leadingContent = {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    symbol = MaterialSymbols.PushPin,
                    contentDescription = stringRes(Res.string.bookmark_list_icon_label),
                    modifier = Size40Modifier,
                )
                Spacer(StdVertSpacer)
                BookmarkMembershipStatusAndNumberDisplay(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    postBookmarksSize = pinState.size,
                    articleBookmarksSize = 0,
                )
            }
        },
    )
}

@Composable
fun RepositoriesBookmarkList(
    repositories: GitRepositoryListState,
    openRepositories: () -> Unit,
) {
    val repositoryAddresses by repositories.repositoryAddressSet.collectAsStateWithLifecycle()

    ListItem(
        modifier = Modifier.clickable(onClick = openRepositories),
        headlineContent = {
            Text(stringRes(Res.string.repository_bookmarks), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringRes(Res.string.repository_bookmarks_explainer),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                )
            }
        },
        leadingContent = {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    symbol = MaterialSymbols.Code,
                    contentDescription = stringRes(Res.string.bookmark_list_icon_label),
                    modifier = Size40Modifier,
                )
                Spacer(StdVertSpacer)
                BookmarkMembershipStatusAndNumberDisplay(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    postBookmarksSize = repositoryAddresses.size,
                    articleBookmarksSize = 0,
                )
            }
        },
    )
}

@Composable
fun PodcastsBookmarkList(
    defaultBookmarks: BookmarkListState,
    openPodcasts: () -> Unit,
) {
    val bookmarkState by defaultBookmarks.bookmarks.collectAsStateWithLifecycle()

    // Podcasts live in the same kind:10003 list as everything else, so count the podcast subset.
    val podcastCount =
        (bookmarkState.public + bookmarkState.private).count { isPodcastEvent(it.event) }

    ListItem(
        modifier = Modifier.clickable(onClick = openPodcasts),
        headlineContent = {
            Text(stringRes(Res.string.podcast_bookmarks), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringRes(Res.string.podcast_bookmarks_explainer),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                )
            }
        },
        leadingContent = {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    symbol = MaterialSymbols.Podcasts,
                    contentDescription = stringRes(Res.string.bookmark_list_icon_label),
                    modifier = Size40Modifier,
                )
                Spacer(StdVertSpacer)
                BookmarkMembershipStatusAndNumberDisplay(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    postBookmarksSize = podcastCount,
                    articleBookmarksSize = 0,
                )
            }
        },
    )
}

@Composable
fun OldBookmarkList(
    oldBookmarks: OldBookmarkListState,
    openOldBookmarks: () -> Unit,
) {
    val bookmarkState by oldBookmarks.bookmarks.collectAsStateWithLifecycle()

    ListItem(
        modifier = Modifier.clickable(onClick = openOldBookmarks),
        headlineContent = {
            Text(stringRes(Res.string.old_bookmarks_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringRes(Res.string.old_bookmarks_explainer),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                )
            }
        },
        leadingContent = {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    symbol = MaterialSymbols.BookmarkBorder,
                    contentDescription = stringRes(Res.string.bookmark_list_icon_label),
                    modifier = Size40Modifier,
                )
                Spacer(StdVertSpacer)
                BookmarkMembershipStatusAndNumberDisplay(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    postBookmarksSize = bookmarkState.public.size + bookmarkState.private.size,
                    articleBookmarksSize = 0,
                )
            }
        },
    )
}
