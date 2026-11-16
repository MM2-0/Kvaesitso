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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.bookmarkgroups.membershipManagement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip51Lists.BookmarkType
import com.vitorpamplona.amethyst.commons.model.nip51Lists.bookmarkSets.BookmarkSet
import com.vitorpamplona.amethyst.commons.nip51Lists.ui.BookmarkGroupManagementItem
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.article_bookmark_management_title
import com.vitorpamplona.amethyst.commons.resources.bookmarks_title
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.list.NewListButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark

@Composable
fun ArticleBookmarkListManagementScreen(
    articleAddress: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address = articleAddress) {
        it?.let {
            ListManagementView(
                modifier = Modifier.fillMaxSize().recalculateWindowInsets(),
                note = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun ListManagementView(
    modifier: Modifier = Modifier,
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopBarWithBackButton(caption = stringRes(Res.string.article_bookmark_management_title), nav)
        },
        floatingActionButton = {
            FabBottomBarPadded(nav) {
                NewListButton { nav.nav(Route.BookmarkGroupMetadataEdit()) }
            }
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .padding(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding(),
                    ).consumeWindowInsets(contentPadding)
                    .imePaddingSafe(),
        ) {
            ListManagementViewBody(note, accountViewModel, nav)
        }
    }
}

@Composable
private fun ListManagementViewBody(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val bookmarkGroups by accountViewModel.account.bookmarkSets.listFeedFlow
        .collectAsStateWithLifecycle()

    val defaultBookmarks by accountViewModel.account.bookmarkState.bookmarks
        .collectAsStateWithLifecycle()

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            val maybePublicBookmark = defaultBookmarks.public.contains(note)
            val maybePrivateBookmark = defaultBookmarks.private.contains(note)

            BookmarkGroupManagementItem(
                modifier = Modifier.fillMaxWidth().animateItem(),
                listTitle = stringRes(Res.string.bookmarks_title),
                isPublicMemberBookmark = maybePublicBookmark,
                isPrivateMemberBookmark = maybePrivateBookmark,
                totalPostBookmarkSize = defaultBookmarks.public.size + defaultBookmarks.private.size,
                totalArticleBookmarkSize = 0,
                onClick = {
                    nav.nav(Route.Bookmarks)
                },
                onAddBookmarkToGroup = { shouldBePrivate ->
                    accountViewModel.launchSigner {
                        accountViewModel.account.addBookmark(note, shouldBePrivate)
                    }
                },
                onRemoveBookmarkFromGroup = {
                    accountViewModel.launchSigner {
                        accountViewModel.account.removeBookmark(note)
                    }
                },
            )
        }

        itemsIndexed(items = bookmarkGroups, key = { _: Int, item: BookmarkSet -> item.identifier }) { _, bookmarkList ->
            val maybePublicBookmark = bookmarkList.publicArticleBookmarks.firstOrNull { it.address == note.address }
            val maybePrivateBookmark = bookmarkList.privateArticleBookmarks.firstOrNull { it.address == note.address }
            BookmarkGroupManagementItem(
                modifier = Modifier.fillMaxWidth().animateItem(),
                listTitle = bookmarkList.title,
                isPublicMemberBookmark = maybePublicBookmark != null,
                isPrivateMemberBookmark = maybePrivateBookmark != null,
                totalPostBookmarkSize = bookmarkList.publicPostBookmarks.size + bookmarkList.privatePostBookmarks.size,
                totalArticleBookmarkSize = bookmarkList.publicArticleBookmarks.size + bookmarkList.privateArticleBookmarks.size,
                onClick = { nav.nav(Route.BookmarkGroupView(bookmarkList.identifier, BookmarkType.ArticleBookmark)) },
                onAddBookmarkToGroup = { shouldBePrivate ->
                    accountViewModel.launchSigner {
                        accountViewModel.account.bookmarkSets.addBookmarkToList(
                            bookmark = AddressBookmark(address = note.address, relayHint = note.relayHintUrl()),
                            bookmarkListIdentifier = bookmarkList.identifier,
                            isBookmarkPrivate = shouldBePrivate,
                            account = accountViewModel.account,
                        )
                    }
                },
                onRemoveBookmarkFromGroup = {
                    accountViewModel.launchSigner {
                        accountViewModel.account.bookmarkSets.removeBookmarkFromList(
                            bookmark = AddressBookmark(address = note.address),
                            bookmarkListIdentifier = bookmarkList.identifier,
                            isBookmarkPrivate = maybePrivateBookmark != null,
                            account = accountViewModel.account,
                        )
                    }
                },
            )
        }
    }
}
