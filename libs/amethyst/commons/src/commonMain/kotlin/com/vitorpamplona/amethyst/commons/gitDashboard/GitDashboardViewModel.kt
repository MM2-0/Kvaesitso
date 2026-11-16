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
package com.vitorpamplona.amethyst.commons.gitDashboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlin.reflect.KClass

/** Everything the dashboard shows at once, so its sections never disagree about an item. */
@Immutable
class GitDashboardState(
    val myRepositories: List<GitDashboardRepository>,
    val followedRepositories: List<GitDashboardRepository>,
    val inbox: List<GitInboxEntry>,
) {
    val unreadCount: Int get() = inbox.count { it.unread > 0 }
}

/**
 * The git dashboard of [account]: its repositories, the ones it follows and an inbox of their
 * issues and PRs, recomputed from [LocalCache] whenever a git event or a comment arrives, the
 * follow list changes or a read marker moves. Read markers are the account's last-read routes:
 * one cutoff for "mark all read" and one per item, set when its thread opens.
 */
@OptIn(FlowPreview::class)
class GitDashboardViewModel(
    val account: Account,
) : ViewModel() {
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val state: StateFlow<GitDashboardState?> =
        merge(
            LocalCache.live.newEventBundles
                .filter { bundle -> bundle.any { isDashboardEvent(it.event) } }
                .map { },
            account.gitRepositoryListState.repositoryAddressSet.map { },
            refreshes,
        ).onStart { emit(Unit) }
            .debounce(300)
            .map { compute() }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Recompute now, e.g. when the screen comes back after the reader opened an item. */
    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    /** Marks everything in the inbox as read as of now. */
    fun markAllRead() {
        account.markAsRead(INBOX_ROUTE, TimeUtils.now())
        // The cutoff now covers every item's own marker: forget them, so they don't pile up.
        account.settings.dropLastRead { it.startsWith(ITEM_ROUTE_PREFIX) }
        refresh()
    }

    private fun compute(): GitDashboardState {
        val me = account.signer.pubKey
        val mine = GitDashboard.myRepositories(me, LocalCache)
        val followed = GitDashboard.followedRepositories(account.gitRepositoryListState.repositoryAddressSet.value, LocalCache)
        val watched = (mine + followed).map { it.note.address }
        // A reader new to the inbox starts with the last week unread, not every item ever filed.
        val cutoff = account.loadLastRead(INBOX_ROUTE).takeIf { it > 0 } ?: (TimeUtils.now() - FIRST_RUN_WINDOW).also { account.markAsRead(INBOX_ROUTE, it) }
        val inbox = GitDashboard.inbox(me, watched, LocalCache, lastRead = { maxOf(cutoff, account.loadLastRead(itemReadRoute(it))) })
        return GitDashboardState(
            myRepositories = mine,
            followedRepositories = followed.filter { f -> mine.none { it.note.address == f.note.address } },
            inbox = inbox,
        )
    }

    class Factory(
        val account: Account,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = GitDashboardViewModel(account) as T
    }

    companion object {
        const val INBOX_ROUTE = "GitInbox"

        private const val ITEM_ROUTE_PREFIX = "GitItem/"

        private val GIT_ITEM_KINDS = setOf(GitIssueEvent.KIND.toString(), GitPullRequestEvent.KIND.toString(), GitPatchEvent.KIND.toString())

        /** How far back a first-time reader's inbox counts as unread. */
        private const val FIRST_RUN_WINDOW = 7 * 24 * 60 * 60L

        /** The last-read route of one issue, patch or PR: its thread sets it when opened. */
        fun itemReadRoute(item: Note): String = ITEM_ROUTE_PREFIX + item.idHex

        /** Marks [item] read as of now, as its thread opens. */
        fun markItemRead(
            account: Account,
            item: Note,
        ) {
            account.markAsRead(itemReadRoute(item), TimeUtils.now())
        }

        private fun isDashboardEvent(event: Event?): Boolean =
            event is GitIssueEvent ||
                event is GitPullRequestEvent ||
                event is GitPatchEvent ||
                event is GitStatusEvent ||
                event is GitPullRequestUpdateEvent ||
                event is GitCoverNoteEvent ||
                event is GitReplyEvent ||
                event is GitRepositoryEvent ||
                event is LabelEvent ||
                // NIP-22 comments are everywhere in the app; only ones in a git item's thread count.
                (event is CommentEvent && event.rootKinds().any { it.getOrNull(1) in GIT_ITEM_KINDS })
    }
}
