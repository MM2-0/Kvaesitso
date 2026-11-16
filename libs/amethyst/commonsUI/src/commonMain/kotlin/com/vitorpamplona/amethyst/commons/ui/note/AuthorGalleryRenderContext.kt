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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The notification galleries (reactions, zaps, nutzaps, boosts) render up to 30
 * author avatars each. A couple of the inputs to every avatar are *account-global*
 * — the auto-play-gif setting and the logged-in user's follow set — yet the avatar
 * code used to collect each of them **once per author**. With dozens of authors per
 * card that produced dozens of redundant Flow collectors / coroutine launches every
 * time a card scrolled into view, a measurable chunk of the per-card composition cost.
 *
 * Hoisting those reads to a single collection per gallery and handing the snapshot
 * down through [LocalAuthorGalleryRenderContext] lets each avatar read plain values
 * and stay skippable. When the local is absent the avatar falls back to its old
 * per-author behaviour, so callers that don't provide a context keep working.
 */
@Immutable
class AuthorGalleryRenderContext(
    val autoPlayGif: Boolean,
    val follows: Set<String>,
)

val LocalAuthorGalleryRenderContext = compositionLocalOf<AuthorGalleryRenderContext?> { null }

/**
 * Collects the account-global render inputs a single time. Provide the result via
 * [LocalAuthorGalleryRenderContext] around a gallery's author list.
 */
@Composable
fun rememberAuthorGalleryRenderContext(accountViewModel: AccountViewModel): AuthorGalleryRenderContext {
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    val follows by accountViewModel.account.allFollows.flow
        .collectAsStateWithLifecycle()

    val followAuthors = follows.authors
    return remember(autoPlayGif, followAuthors) {
        AuthorGalleryRenderContext(autoPlayGif, followAuthors)
    }
}
