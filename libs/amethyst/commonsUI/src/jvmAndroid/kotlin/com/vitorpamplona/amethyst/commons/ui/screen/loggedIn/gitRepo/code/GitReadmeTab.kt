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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.code

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.nip34Git.GitBrowseState
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepositoryBrowserViewModel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_repo_code_loading
import com.vitorpamplona.amethyst.commons.resources.git_repo_file_load_error
import com.vitorpamplona.amethyst.commons.resources.git_repo_readme_missing
import com.vitorpamplona.amethyst.commons.ui.components.RichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.git.GitRepoSnapshot
import com.vitorpamplona.quartz.nip34Git.git.GitTreeEntry
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlin.coroutines.cancellation.CancellationException

/**
 * The repository's README as embeddable column content — no scroll container or
 * scaffold padding of its own, so the project home screen renders it inline below
 * the repository facts inside a single scroll. Falls back to the announcement's own
 * description when no README file can be fetched, so the section is never empty.
 */
@Composable
fun GitReadmeSection(
    state: GitBrowseState,
    viewModel: GitRepositoryBrowserViewModel,
    event: GitRepositoryEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val snapshot = (state as? GitBrowseState.Loaded)?.snapshot
    val readme = remember(snapshot) { snapshot?.let { findReadme(it.rootEntries()) } }

    if (snapshot != null && readme != null) {
        ReadmeContent(snapshot, viewModel, readme, accountViewModel, nav)
    } else {
        ReadmeFallback(
            event = event,
            loading = state is GitBrowseState.Loading,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun ReadmeContent(
    snapshot: GitRepoSnapshot,
    viewModel: GitRepositoryBrowserViewModel,
    readme: GitTreeEntry,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val content by
        produceState<String?>(null, readme.oid) {
            value =
                try {
                    viewModel.readBlob(snapshot, readme.oid).decodeToString()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ""
                }
        }

    when (val text = content) {
        null -> StatusLine(stringRes(Res.string.git_repo_code_loading))
        "" -> StatusLine(stringRes(Res.string.git_repo_file_load_error))
        else -> {
            val background = MaterialTheme.colorScheme.background
            val backgroundColor = remember { mutableStateOf(background) }
            RichTextViewer(
                content = text,
                canPreview = true,
                quotesLeft = 1,
                modifier = Modifier.fillMaxWidth(),
                tags = EmptyTagList,
                backgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun ReadmeFallback(
    event: GitRepositoryEvent,
    loading: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val description = event.description()?.takeIf { it.isNotBlank() }
    if (description == null) {
        StatusLine(
            if (loading) stringRes(Res.string.git_repo_code_loading) else stringRes(Res.string.git_repo_readme_missing),
        )
        return
    }

    val background = MaterialTheme.colorScheme.background
    val backgroundColor = remember { mutableStateOf(background) }
    Column(Modifier.fillMaxWidth()) {
        RichTextViewer(
            content = description,
            canPreview = true,
            quotesLeft = 1,
            modifier = Modifier.fillMaxWidth(),
            tags = EmptyTagList,
            backgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        if (loading) {
            StatusLine(stringRes(Res.string.git_repo_code_loading), topPadding = 16.dp)
        }
    }
}

@Composable
private fun StatusLine(
    text: String,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = topPadding),
    )
}

/** Picks the most README-like file in the root, preferring markdown. */
private fun findReadme(entries: List<GitTreeEntry>): GitTreeEntry? {
    val files = entries.filter { !it.isFolder }
    val readmes = files.filter { it.name.substringBeforeLast('.').equals("readme", ignoreCase = true) || it.name.equals("readme", ignoreCase = true) }
    if (readmes.isEmpty()) return null
    val byPreference = listOf("readme.md", "readme.markdown", "readme.mdown", "readme", "readme.txt", "readme.rst")
    for (preferred in byPreference) {
        readmes.firstOrNull { it.name.equals(preferred, ignoreCase = true) }?.let { return it }
    }
    return readmes.first()
}
