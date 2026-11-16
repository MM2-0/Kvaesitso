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
package com.vitorpamplona.amethyst.commons.nip34Git.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.note.GitStatusPill
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent

fun GitStatusEvent.statusKind(): StatusKind =
    when (this) {
        is GitStatusAppliedEvent -> StatusKind.APPLIED
        is GitStatusClosedEvent -> StatusKind.CLOSED
        is GitStatusDraftEvent -> StatusKind.DRAFT
        is GitStatusOpenEvent -> StatusKind.OPEN
        else -> StatusKind.OPEN
    }

/**
 * Entry point: the [GitStatusPill] for the status [item] shows ([observeGitStatus]), or
 * [defaultIfMissing] when it has none; nothing when it has none and no default is given.
 */
@Composable
fun GitStatusPill(
    item: Note,
    modifier: Modifier = Modifier,
    defaultIfMissing: StatusKind? = null,
    /** A 1631 on an issue reads Resolved, on a patch or PR Merged. */
    forIssue: Boolean = false,
) {
    val kind = observeGitStatus(item)?.statusKind() ?: defaultIfMissing ?: return
    GitStatusPill(kind, modifier, forIssue)
}
