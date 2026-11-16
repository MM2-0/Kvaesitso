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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.bookmarkgroups.display

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.bookmarkgroups.display.BookmarkGroupViewModel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun RenderPostList(
    bookmarkGroupViewModel: BookmarkGroupViewModel,
    pagerState: PagerState,
    accountViewModel: AccountViewModel,
    movePostBookmark: (postId: String, fromPrivate: Boolean) -> Unit,
    deletePostBookmark: (postId: String, isPrivate: Boolean) -> Unit,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val privatePosts by bookmarkGroupViewModel.privatePosts.collectAsStateWithLifecycle()
    val publicPosts by bookmarkGroupViewModel.publicPosts.collectAsStateWithLifecycle()

    HorizontalPager(pagerState, modifier) { page ->
        when (page) {
            0 -> {
                PostList(
                    modifier = Modifier.fillMaxSize(),
                    posts = publicPosts,
                    isPostBookmarkPrivate = false,
                    onMoveBookmarkToPrivate = { postId ->
                        movePostBookmark(postId, false)
                    },
                    onDeletePostBookmark = { postId ->
                        deletePostBookmark(postId, false)
                    },
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            1 -> {
                PostList(
                    modifier = Modifier.fillMaxSize(),
                    posts = privatePosts,
                    isPostBookmarkPrivate = true,
                    onMoveBookmarkToPublic = { postId ->
                        movePostBookmark(postId, true)
                    },
                    onDeletePostBookmark = { postId ->
                        deletePostBookmark(postId, true)
                    },
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
private fun PostList(
    modifier: Modifier = Modifier,
    posts: List<Note>,
    isPostBookmarkPrivate: Boolean,
    onMoveBookmarkToPublic: (postId: String) -> Unit = {},
    onMoveBookmarkToPrivate: (postId: String) -> Unit = {},
    onDeletePostBookmark: (postId: String) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier,
        contentPadding = FeedPadding,
        state = listState,
    ) {
        itemsIndexed(posts, key = { _, item -> item.idHex }) { _, item ->
            NoteCompose(
                baseNote = item,
                modifier = Modifier.animateItem().animateContentSize(),
                quotesLeft = 3,
                accountViewModel = accountViewModel,
                nav = nav,
                moreOptions = {
                    BookmarkGroupItemOptions(
                        baseNote = item,
                        isBookmarkItemPrivate = isPostBookmarkPrivate,
                        onMoveBookmarkToPublic = { onMoveBookmarkToPublic(item.idHex) },
                        onMoveBookmarkToPrivate = { onMoveBookmarkToPrivate(item.idHex) },
                        onDeleteBookmarkItem = { onDeletePostBookmark(item.idHex) },
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                },
            )
        }
    }
}
