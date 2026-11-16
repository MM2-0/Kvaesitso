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
package com.vitorpamplona.amethyst.commons.nip34Git

import com.vitorpamplona.amethyst.commons.gitRepo.dal.isFiledUnder
import com.vitorpamplona.amethyst.commons.gitRepo.dal.isSeriesRoot
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.status.GitStatuses
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ngit's repository as relay.ngit.dev and gitnostr.com held it on 2026-11-04: its announcements,
 * every issue, patch and PR filed under it, and their statuses, labels and withdrawals (content
 * and signatures stripped to keep the file small; nothing here reads either).
 *
 * gitworkshop showed it the same day as 29 open / 67 resolved / 33 closed issues and 11 open /
 * 2 draft / 115 merged / 39 closed PRs and patches. One resolved issue (9ba75a2f) was on no relay
 * this corpus could reach, so the issue counts here are 29 / 66 / 33; the rest must match.
 */
class NgitInteropCorpusTest {
    private val events: List<Event> =
        GZIPInputStream(javaClass.getResourceAsStream("/nip34/ngit-2026-11-04.jsonl.gz")!!)
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }
            .map { Event.fromJson(it) }

    private val lead = Address(GitRepositoryEvent.KIND, "a008def15796fba9a0d6fab04e8fd57089285d9fd505da5a83fe8aad57a3564d", "ngit")

    private fun stateCounts(): Map<String, Int> {
        val announcements = events.filterIsInstance<GitRepositoryEvent>()
        val authority = GitAuthority.of(mapOf("ngit" to announcements.groupBy { it.pubKey }.mapValues { (_, v) -> v.maxBy { it.createdAt } }))
        val coordinates = authority.coordinatesToTag(lead).map { it.toValue() }.toSet()

        // A status its own author withdrew (kind 5) never reaches the cache.
        val byId = events.associateBy { it.id }
        val withdrawn =
            events.filterIsInstance<DeletionRequestEvent>().flatMapTo(HashSet()) { deletion ->
                deletion.deleteEventIds().filter { byId[it]?.pubKey == deletion.pubKey }
            }
        val statuses = events.filterIsInstance<GitStatusEvent>().filter { it.id !in withdrawn }

        val items = events.filter { it is GitIssueEvent || it is GitPullRequestEvent || it is GitPatchEvent }
        val latest = GitStatuses.latestByItem(statuses, items.associateBy { it.id }, authority)

        return items
            .filter { (it !is GitPatchEvent || it.isSeriesRoot()) && isFiledUnder(it, coordinates) }
            .groupingBy { item ->
                val type = if (item is GitIssueEvent) "issue" else "pr"
                val state =
                    when (latest[item.id]?.kind) {
                        null, GitStatusEvent.KIND_OPEN -> "open"
                        GitStatusEvent.KIND_APPLIED -> "applied"
                        GitStatusEvent.KIND_CLOSED -> "closed"
                        GitStatusEvent.KIND_DRAFT -> "draft"
                        else -> "?"
                    }
                "$type/$state"
            }.eachCount()
    }

    @Test
    fun countsMatchGitworkshop() {
        assertEquals(
            mapOf(
                "issue/open" to 29,
                "issue/applied" to 66,
                "issue/closed" to 33,
                "pr/open" to 11,
                "pr/draft" to 2,
                "pr/applied" to 115,
                "pr/closed" to 39,
            ),
            stateCounts(),
        )
    }
}
