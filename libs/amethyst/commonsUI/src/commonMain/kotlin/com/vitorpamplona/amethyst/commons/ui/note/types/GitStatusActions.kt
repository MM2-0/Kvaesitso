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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.status.publishGitStatus
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitStatusButtons
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitRepositoryAnnouncements
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitStatus
import com.vitorpamplona.amethyst.commons.nip34Git.ui.statusKind
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent

/**
 * NIP-34 status controls for an issue, patch or pull request. Visible only to the people whose
 * status counts ([GitAuthority.memberActorsOf]): the item's author and the repository's confirmed
 * members. "Merge" ([onMerge], when the host can merge in the app) and "Mark merged" are for
 * confirmed maintainers only, as gitworkshop offers them.
 * The item's replies carry the published status everywhere it shows.
 */
@Composable
fun GitStatusActions(
    note: Note,
    accountViewModel: AccountViewModel,
    onMerge: (() -> Unit)? = null,
) {
    val event = note.event ?: return
    // A read-only login (an npub) can't sign a status: offer nothing it would fail at.
    if (!accountViewModel.account.isWriteable()) return
    if (GitAuthority.repositoriesOf(event).isEmpty()) return
    val isPatchOrPr = event is GitPatchEvent || event is GitPullRequestEvent

    val announcements = observeGitRepositoryAnnouncements(event)
    val me = accountViewModel.account.signer.pubKey
    val canSetStatus = remember(event, announcements, me) { me in LocalCache.gitAuthority.memberActorsOf(event) }
    if (!canSetStatus) return
    val canMerge = remember(event, announcements, me) { LocalCache.gitAuthority.isRepositoryMaintainer(me, event) }

    val current = observeGitStatus(note)?.statusKind() ?: StatusKind.OPEN

    GitStatusButtons(
        current = current,
        isPatchOrPr = isPatchOrPr,
        canMerge = canMerge,
        onSetStatus = { sendStatus(accountViewModel, note, it) },
        onMerge = onMerge.takeIf { canMerge },
    )
}

private fun sendStatus(
    accountViewModel: AccountViewModel,
    note: Note,
    target: StatusKind,
) {
    val kind =
        when (target) {
            StatusKind.OPEN -> GitStatusEvent.KIND_OPEN
            StatusKind.CLOSED -> GitStatusEvent.KIND_CLOSED
            StatusKind.APPLIED -> GitStatusEvent.KIND_APPLIED
            StatusKind.DRAFT -> GitStatusEvent.KIND_DRAFT
        }
    accountViewModel.launchSigner { accountViewModel.account.publishGitStatus(note, kind) }
}

/** The repository coordinate an issue, patch or PR is filed against (its first). */
internal fun repositoryAddressOf(event: Event): Address? = GitAuthority.repositoriesOf(event).firstOrNull()
