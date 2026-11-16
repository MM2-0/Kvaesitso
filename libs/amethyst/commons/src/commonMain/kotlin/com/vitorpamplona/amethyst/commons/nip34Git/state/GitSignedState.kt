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
package com.vitorpamplona.amethyst.commons.nip34Git.state

import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent

/**
 * The branch tips a repository's maintainers signed (kind 30618), used to tell whether a git server
 * serves what they published. Only a confirmed maintainer's state counts; the newest wins, the lower
 * event id at equal timestamps, as gitworkshop picks it. A state with no refs (a discontinued
 * repository) is ignored.
 */
class GitSignedState(
    val event: GitRepositoryStateEvent,
) {
    /** The branch HEAD names, the one to browse by default. */
    val headBranch: String? = event.head()

    /** The signed tip of [branch], or of [headBranch] when [branch] is null. */
    fun commitFor(branch: String?): String? {
        val name = branch ?: headBranch ?: return null
        return event.branches().firstOrNull { it.shortName == name }?.commit
    }

    companion object {
        /** The newest of the maintainers' signed states [events], ignoring discontinued ones (no refs). */
        fun latest(events: List<GitRepositoryStateEvent>): GitSignedState? =
            events
                .filterNot { it.isDiscontinued() }
                .maxWithOrNull(compareBy<GitRepositoryStateEvent> { it.createdAt }.thenByDescending { it.id })
                ?.let { GitSignedState(it) }
    }
}
