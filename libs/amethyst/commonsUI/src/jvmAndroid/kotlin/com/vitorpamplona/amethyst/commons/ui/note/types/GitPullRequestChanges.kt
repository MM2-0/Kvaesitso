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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip34Git.GitPrivateRepositoryAuth
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitCardButtonHeight
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitCardButtonPadding
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitReviewHooks
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_pr_changes_retry
import com.vitorpamplona.amethyst.commons.resources.git_pr_loading_changes
import com.vitorpamplona.amethyst.commons.resources.git_pr_no_changes
import com.vitorpamplona.amethyst.commons.resources.git_pr_view_changes
import com.vitorpamplona.amethyst.commons.ui.note.GitDiffView
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.git.GitHttpClient
import com.vitorpamplona.quartz.nip34Git.patch.ParsedPatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private sealed interface ChangesState {
    object Idle : ChangesState

    object Loading : ChangesState

    class Loaded(
        val patch: ParsedPatch,
    ) : ChangesState

    object Failed : ChangesState
}

/**
 * "View changes" section of a pull-request card. A NIP-34 PR references a clone
 * URL + commit instead of embedding a patch, so the actual diff is computed on
 * demand over the git smart-HTTP client (base = merge base, head = current
 * commit) and rendered with the shared [GitDiffView].
 */
@Composable
fun GitPullRequestChanges(
    cloneUrls: List<String>,
    headCommit: String?,
    mergeBase: String?,
    accountViewModel: AccountViewModel,
    review: GitReviewHooks? = null,
) {
    val candidates = remember(cloneUrls) { candidateUrls(cloneUrls) }
    if (candidates.isEmpty() || headCommit.isNullOrBlank()) return

    // Whether the user asked for the diff survives leaving the screen; the diff itself is kept by
    // commit, so coming back shows it again without a fetch.
    var requested by rememberSaveable(headCommit, mergeBase) { mutableStateOf(false) }
    var state by remember(headCommit, mergeBase) {
        mutableStateOf<ChangesState>(PullRequestDiffs.get(headCommit, mergeBase)?.let { ChangesState.Loaded(it) } ?: ChangesState.Idle)
    }
    val scope = rememberCoroutineScope()

    fun load() {
        requested = true
        state = ChangesState.Loading
        scope.launch {
            state =
                try {
                    val patch = loadDiff(accountViewModel, candidates, headCommit, mergeBase)
                    PullRequestDiffs.put(headCommit, mergeBase, patch)
                    ChangesState.Loaded(patch)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ChangesState.Failed
                }
        }
    }
    // Asked for before, but no longer in memory: fetch it again rather than show the bar.
    LaunchedEffect(headCommit, mergeBase) { if (requested && state == ChangesState.Idle) load() }

    when (val s = state) {
        ChangesState.Idle ->
            // A bordered "Files changed" bar, the shape the diff takes once it loads, rather than
            // a button: the diff is fetched on demand, so its stats aren't known until then.
            Row(
                modifier =
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .gitCard()
                        .clickable { load() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(MaterialSymbols.Code, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.grayText)
                Text(
                    text = stringRes(Res.string.git_pr_view_changes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(MaterialSymbols.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.grayText)
            }

        ChangesState.Loading ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text(stringRes(Res.string.git_pr_loading_changes), style = MaterialTheme.typography.bodySmall)
            }

        is ChangesState.Loaded ->
            if (s.patch.hasDiff) {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    GitDiffView(s.patch, Modifier.fillMaxWidth(), review?.onLineClick, review?.lineAnnotations, review?.hasAnnotations ?: { _, _ -> false })
                }
            } else {
                Text(
                    text = stringRes(Res.string.git_pr_no_changes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

        ChangesState.Failed ->
            FilledTonalButton(
                onClick = { load() },
                modifier = Modifier.padding(top = 8.dp).then(GitCardButtonHeight),
                contentPadding = GitCardButtonPadding,
            ) {
                Icon(MaterialSymbols.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringRes(Res.string.git_pr_changes_retry), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
            }
    }
}

/**
 * The last few diffs computed, by head and base commit. Both are content addresses, so an entry
 * never goes stale; a handful is enough to come back to a pull request without fetching it again.
 */
private object PullRequestDiffs {
    private const val MAX = 4
    private val diffs = LinkedHashMap<Pair<String, String?>, ParsedPatch>(MAX, 0.75f, true)

    fun get(
        head: String,
        base: String?,
    ): ParsedPatch? = synchronized(diffs) { diffs[head to base] }

    fun put(
        head: String,
        base: String?,
        patch: ParsedPatch,
    ) = synchronized(diffs) {
        diffs[head to base] = patch
        while (diffs.size > MAX) diffs.remove(diffs.keys.first())
    }
}

private suspend fun loadDiff(
    accountViewModel: AccountViewModel,
    candidates: List<String>,
    headCommit: String,
    mergeBase: String?,
): ParsedPatch =
    withContext(Dispatchers.IO) {
        val client = GitHttpClient(accountViewModel.httpClientBuilder::okHttpClientForPreview, GitPrivateRepositoryAuth(accountViewModel.account.signer))
        var lastError: Exception? = null
        for (url in candidates) {
            try {
                return@withContext client.computeDiff(url, headCommit, mergeBase?.takeIf { it.isNotBlank() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("no usable clone URL")
    }

private fun candidateUrls(cloneUrls: List<String>): List<String> {
    val out = LinkedHashSet<String>()
    for (raw in cloneUrls) {
        val url = raw.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) continue
        out.add(url)
        if (!url.removeSuffix("/").endsWith(".git")) out.add(url.removeSuffix("/") + ".git")
    }
    return out.toList()
}
