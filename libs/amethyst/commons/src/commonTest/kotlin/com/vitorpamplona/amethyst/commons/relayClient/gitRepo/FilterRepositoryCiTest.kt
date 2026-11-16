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
package com.vitorpamplona.amethyst.commons.relayClient.gitRepo

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip34Git.ci.jobResult.CiJobResultEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterRepositoryCiTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example")!!
    private val owner = "1".repeat(64)
    private val maintainer = "2".repeat(64)

    private val repository =
        GitRepositoryEvent(
            "9".repeat(64),
            owner,
            1,
            arrayOf(arrayOf("d", "repo"), arrayOf("maintainers", owner, maintainer)),
            "",
            "0".repeat(128),
        )

    @Test
    fun asksForRunsUnderEveryMaintainerCoordinate() {
        val filter = filterRepositoryCi(relay, repository, null).filter

        assertEquals(listOf("30617:$owner:repo", "30617:$maintainer:repo"), filter.tags?.get("a"))
        assertEquals(listOf(CiWorkflowResultEvent.KIND, CiWorkflowProgressEvent.KIND), filter.kinds)
        // Log tails stay out of the repository-wide fetch.
        assertFalse(filter.kinds!!.contains(CiJobResultEvent.KIND))
    }

    @Test
    fun ciKindsDoNotShareTheIssueAndPrLimit() {
        assertFalse(RepositoryItemKinds.any { it in RepositoryCiKinds })
        assertFalse(RepositoryActivityKinds.any { it in RepositoryCiKinds })
    }

    @Test
    fun statusesDoNotShareTheItemsLimit() {
        // On ngit one filter for both, at 500, left a third of the issues unloaded.
        assertFalse(RepositoryItemKinds.any { it in RepositoryActivityKinds })
        assertEquals(listOf(RepositoryItemKinds, RepositoryActivityKinds), filterRepositoryContent(relay, repository, null).map { it.filter.kinds })
    }

    @Test
    fun asksForEveryItemsEssentialsByIdInChunks() {
        val ids = (1..ITEM_ESSENTIALS_IDS_PER_FILTER + 1).map { it.toString().padStart(64, '0') }
        val filters = filterRepositoryItemEssentials(relay, ids, null)

        // Two chunks, each asking by `#e` for statuses, labels and cover notes and by `#E` for PR updates.
        assertEquals(4, filters.size)
        assertEquals(ids.toSet(), filters.filter { it.filter.tags?.containsKey("e") == true }.flatMap { it.filter.tags!!.getValue("e") }.toSet())
        assertEquals(ids.toSet(), filters.filter { it.filter.tags?.containsKey("E") == true }.flatMap { it.filter.tags!!.getValue("E") }.toSet())
        assertTrue(
            filters.all {
                it.filter.tags!!
                    .values
                    .single()
                    .size <= ITEM_ESSENTIALS_IDS_PER_FILTER
            },
        )
    }

    @Test
    fun asksForItemsUnderEveryNamedCoordinate() {
        // Alex's `din` announcement: chad's maintainer role there has ended, but items filed under
        // chad's coordinate are still this repository's to list.
        val alex = "0461fcbecc4c3374439932d6b8f11269ccdb7cc973ad7a50ae362db135a474dd"
        val soapbox = "781a1527055f74c1f70230f10384609b34548f8ab6a0a6caa74025827f9fdae5"
        val chad = "86184109eae937d8d6f980b4a0b46da4ef0d983eade403ee1b4c0b6bde238b47"
        val din =
            GitRepositoryEvent(
                "573aab8572fbb42cbc143a4d3594a02e4b3bc86da3d3d67adb6f8840f71ea554",
                alex,
                1793647335,
                arrayOf(
                    arrayOf("d", "din"),
                    arrayOf("maintainers", alex, soapbox),
                    arrayOf("m", alex),
                    arrayOf("M", soapbox, "1793647289"),
                    arrayOf("m", soapbox, "1793647060", "1793647289"),
                    arrayOf("m", chad, "0", "1793647255", "1793647255", "defer"),
                ),
                "",
                "0".repeat(128),
            )

        filterRepositoryContent(relay, din, null).forEach {
            assertEquals(
                setOf(alex, soapbox, chad).map { "30617:$it:din" }.toSet(),
                it.filter.tags
                    ?.get("a")!!
                    .toSet(),
            )
        }
    }
}
