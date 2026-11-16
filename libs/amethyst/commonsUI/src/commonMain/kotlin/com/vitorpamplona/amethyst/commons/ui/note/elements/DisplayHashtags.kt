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
package com.vitorpamplona.amethyst.commons.ui.note.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.FollowedHashtagLabel
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.firstIsTaggedHashes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip73ExternalIds.topics.HashtagId

@Composable
fun DisplayFollowingHashtagsInPost(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val userFollowState by accountViewModel.account.allFollows.flow
        .collectAsStateWithLifecycle()
    // Computed in composition, keyed on both inputs. An effect keyed on the follows alone never
    // re-ran for a new note (a draft is a new Note on every edit), so the label vanished for good,
    // and on every card it arrived one frame late, re-laying out the first row on each scroll-in.
    val firstTag =
        remember(baseNote, userFollowState) {
            val noteEvent = baseNote.event
            if (noteEvent is CommentEvent) {
                noteEvent.firstTaggedScopeIn(userFollowState.hashtagScopes)?.let { HashtagId.parse(it) } ?: noteEvent.firstIsTaggedHashes(userFollowState.hashtags)
            } else {
                noteEvent?.firstIsTaggedHashes(userFollowState.hashtags)
            }
        }

    firstTag?.let { tag ->
        FollowedHashtagLabel(tag) { nav.nav(Route.Hashtag(tag.lowercase())) }
    }
}
