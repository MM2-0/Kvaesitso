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
package com.vitorpamplona.amethyst.commons.nip34Git.create

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewGitRepositoryTest {
    private val owner = "aa".repeat(32)
    private val npub = owner.hexToByteArray().toNpub()
    private val root = "8f9e1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b"

    private fun parse(tags: Array<Array<String>>) = GitRepositoryEvent("00", owner, 0, tags, "", "00")

    /**
     * The tags ngit's `RepoRef::to_event` writes for a fresh `ngit init` on two GRASP servers
     * (its default `relay.ngit.dev` + `gitnostr.com`) with an explicit web URL: one multi-value
     * `clone`/`web`/`relays` tag each, the GRASP relays first, the `euc` `r` tag, ngit's `alt`,
     * lowercase `t` tags, and no `maintainers` tag for an implicit sole maintainer. Compared with
     * ngit's events on relay.ngit.dev, down to relays written without a trailing slash.
     */
    @Test
    fun matchesTheAnnouncementNgitInitWrites() {
        val tmpl =
            NewGitRepository.build(
                NewGitRepository.Draft(
                    name = "Amethyst",
                    identifier = "amethyst",
                    description = "Nostr client",
                    graspServers = listOf("relay.ngit.dev", "gitnostr.com"),
                    webUrls = listOf("https://example.com/amethyst"),
                    earliestUniqueCommit = root.uppercase(),
                    hashtags = listOf("#Kotlin", "nostr"),
                ),
                owner,
            )

        val ngit =
            setOf(
                listOf("d", "amethyst"),
                listOf("r", root, "euc"),
                listOf("name", "Amethyst"),
                listOf("description", "Nostr client"),
                listOf("clone", "https://relay.ngit.dev/$npub/amethyst.git", "https://gitnostr.com/$npub/amethyst.git"),
                listOf("web", "https://example.com/amethyst"),
                listOf("relays", "wss://relay.ngit.dev", "wss://gitnostr.com"),
                listOf("alt", "git repository: Amethyst"),
                listOf("t", "kotlin"),
                listOf("t", "nostr"),
            )

        assertEquals(GitRepositoryEvent.KIND, tmpl.kind)
        assertEquals("", tmpl.content)
        assertEquals(ngit, tmpl.tags.map { it.toList() }.toSet())
        assertEquals(ngit.size, tmpl.tags.size, "no duplicated or extra tags")

        // And Amethyst reads its own output back.
        val read = parse(tmpl.tags)
        assertEquals("amethyst", read.dTag())
        assertEquals(root, read.earliestUniqueCommit())
        assertEquals(emptyList(), read.maintainers())
        assertEquals(listOf("kotlin", "nostr"), read.hashtags())
    }

    @Test
    fun otherClonesComeBeforeGraspClonesAndGraspRelaysBeforeOtherRelays() {
        val tmpl =
            NewGitRepository.build(
                NewGitRepository.Draft(
                    name = "x",
                    identifier = "x",
                    graspServers = listOf("relay.ngit.dev"),
                    cloneUrls = listOf("https://github.com/me/x.git"),
                    relays = listOf("wss://nos.lol", "wss://relay.ngit.dev"),
                ),
                owner,
            )
        val read = parse(tmpl.tags)
        assertEquals(listOf("https://github.com/me/x.git", "https://relay.ngit.dev/$npub/x.git"), read.clones())
        assertEquals(listOf("wss://relay.ngit.dev", "wss://nos.lol"), read.relays())
    }

    @Test
    fun withoutGraspServersOrOptionalFieldsOnlyTheBasicsAreWritten() {
        val tmpl = NewGitRepository.build(NewGitRepository.Draft(name = " My Repo ", identifier = " my-repo "), owner)
        assertEquals(
            setOf(listOf("d", "my-repo"), listOf("name", "My Repo"), listOf("alt", "git repository: My Repo")),
            tmpl.tags.map { it.toList() }.toSet(),
        )
        assertNull(parse(tmpl.tags).earliestUniqueCommit())
    }

    @Test
    fun validatesCommitIdsAndServers() {
        assertTrue(NewGitRepository.isValidCommitId(""))
        assertTrue(NewGitRepository.isValidCommitId(root))
        assertTrue(NewGitRepository.isValidCommitId("ab".repeat(32)))
        assertFalse(NewGitRepository.isValidCommitId("8f9e1a2"))
        assertFalse(NewGitRepository.isValidCommitId("zz".repeat(20)))

        assertTrue(NewGitRepository.isValidGraspServer("relay.ngit.dev"))
        assertFalse(NewGitRepository.isValidGraspServer("https://"))
        assertTrue(NewGitRepository.isValidRelay("wss://nos.lol"))
    }

    @Test
    fun splitsListsOnNewlinesCommasAndSpaces() {
        assertEquals(listOf("a", "b", "c"), NewGitRepository.splitList("a\nb, c\n\na"))
    }
}
