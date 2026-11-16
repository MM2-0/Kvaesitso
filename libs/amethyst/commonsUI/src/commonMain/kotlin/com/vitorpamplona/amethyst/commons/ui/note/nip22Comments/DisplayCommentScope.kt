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
package com.vitorpamplona.amethyst.commons.ui.note.nip22Comments

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip72ModCommunities.topLevelCommunityAddress
import com.vitorpamplona.quartz.nip73ExternalIds.scope

/**
 * The context card above a comment whose parent isn't a note.
 *
 * A NIP-22 comment can be scoped to something that never resolves to an in-cache parent: an
 * external identifier (NIP-73 `I` tag -- a url, hashtag or geohash) or, for a top-level post in a
 * NIP-72 community, the community itself. Both render here so the two call sites -- the feed row
 * and the thread's own master note -- don't each repeat the choice.
 *
 * Renders nothing when the screen is already dedicated to this exact scope (see
 * [LocalCurrentExternalScope]), which would otherwise repeat the same preview on every row.
 */
@Composable
fun DisplayCommentScope(
    noteEvent: CommentEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val community = remember(noteEvent) { noteEvent.topLevelCommunityAddress() }

    if (community != null) {
        val scopeKey = remember(community) { community.toValue() }
        if (scopeKey != LocalCurrentExternalScope.current) {
            DisplayCommunityScope(community, accountViewModel, nav)
            Spacer(modifier = StdVertSpacer)
        }
        return
    }

    val scope = remember(noteEvent) { noteEvent.scope() }
    if (scope != null && scope.toScope() != LocalCurrentExternalScope.current) {
        DisplayExternalId(scope, accountViewModel, nav)
        Spacer(modifier = StdVertSpacer)
    }
}
