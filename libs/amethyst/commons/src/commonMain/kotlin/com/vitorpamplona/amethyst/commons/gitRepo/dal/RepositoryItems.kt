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
package com.vitorpamplona.amethyst.commons.gitRepo.dal

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent

/**
 * The repository coordinates whose issues, patches and PRs a repository screen lists: the viewed
 * announcement's plus every confirmed co-maintainer's and moderator's. An item is filed with one
 * `a` per maintainer, and one filed against any confirmed member belongs to the same repository.
 */
fun repositoryCoordinateSet(
    viewed: Address,
    authority: GitAuthority = LocalCache.gitAuthority,
): Set<String> {
    val membership = authority.membership(viewed)
    return buildSet {
        add(viewed.toValue())
        membership?.confirmedCoordinates()?.forEach { add(it.toValue()) }
    }
}

/** Whether [item] is filed against any of [coordinates]. */
fun isFiledUnder(
    item: Event,
    coordinates: Set<String>,
): Boolean = GitAuthority.repositoriesOf(item).any { it.toValue() in coordinates }

/**
 * Whether a patch starts a series, and so gets a row of its own: tagged `["t", "root"]`, and not a
 * revision of another series (`root-revision`). The rest of a series lives in its root's thread.
 * gitworkshop lists exactly these, so an untagged patch from an older client is not a row here
 * either: listing it would make the two clients' open counts disagree.
 */
fun GitPatchEvent.isSeriesRoot(): Boolean = isRoot() && !isRootRevision()
