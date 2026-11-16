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

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.rememberSharedMediaResolver
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.nip22Comments.CommentPostViewModel
import com.vitorpamplona.amethyst.commons.ui.note.nip22Comments.GenericCommentPostScreen
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip73ExternalIds.urls.UrlId
import kotlinx.collections.immutable.persistentListOf

@Composable
fun UrlPostScreen(
    url: String? = null,
    message: String? = null,
    attachment: String? = null,
    replyId: HexKey? = null,
    quoteId: HexKey? = null,
    draftId: HexKey? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: CommentPostViewModel = rememberViewModel { CommentPostViewModel() }
    postViewModel.init(accountViewModel)

    val mediaResolver = rememberSharedMediaResolver()

    LaunchedEffect(postViewModel, accountViewModel) {
        url?.let {
            UrlId.toScopeOrNull(it)?.let { normalizedUrl ->
                postViewModel.newPostFor(UrlId(normalizedUrl))
            }
        }
        replyId?.let { LocalCache.getNoteIfExists(it) }?.let {
            postViewModel.reply(it)
        }
        draftId?.let { LocalCache.getNoteIfExists(it) }?.let {
            postViewModel.editFromDraft(it)
        }
        quoteId?.let { LocalCache.getNoteIfExists(it) }?.let {
            postViewModel.quote(it)
        }
        message?.ifBlank { null }?.let {
            postViewModel.message.setTextAndPlaceCursorAtEnd(it)
            postViewModel.onMessageChanged()
        }
        mediaResolver.resolve(attachment)?.let {
            postViewModel.selectImage(persistentListOf(it))
        }
        if (draftId == null) {
            postViewModel.applySignature()
        }
    }

    GenericCommentPostScreen(postViewModel, accountViewModel, nav)
}
