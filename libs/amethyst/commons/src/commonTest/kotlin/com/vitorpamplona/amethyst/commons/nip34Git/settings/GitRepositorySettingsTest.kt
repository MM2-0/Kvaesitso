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
package com.vitorpamplona.amethyst.commons.nip34Git.settings

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitRepositorySettingsTest {
    private val owner: HexKey = "a".repeat(64)
    private val other: HexKey = "b".repeat(64)

    private fun signedLike(
        tags: Array<Array<String>>,
        createdAt: Long,
    ) = GitRepositoryEvent("0".repeat(64), owner, createdAt, tags, "", "")

    private val previous =
        signedLike(
            arrayOf(
                arrayOf("d", "repo"),
                arrayOf("name", "Repo"),
                arrayOf("r", "c".repeat(40), "euc"),
                arrayOf("relays", "wss://old.relay/"),
                arrayOf("maintainers", owner),
                arrayOf("M", owner),
                arrayOf("private", "true"),
                arrayOf("blossoms", "https://blossom.example/"),
            ),
            createdAt = 5_000,
        )

    @Test
    fun editKeepsWhatTheFormDoesNotShow() {
        val template =
            GitRepositorySettings.edit(
                previous,
                name = "Repo 2",
                description = "",
                webUrls = "",
                cloneUrls = "https://git.example/repo.git",
                relays = "relay.one\nwss://relay.two",
                maintainers = "${Hex.decode(owner).toNpub()}\n$other",
                topics = listOf("#kotlin"),
                now = 1_000,
            )
        val edited = signedLike(template.tags, template.createdAt)
        assertEquals(5_001, edited.createdAt)
        assertEquals("Repo 2", edited.name())
        assertEquals(listOf("wss://relay.one", "wss://relay.two"), edited.relays())
        // The owner keeps their place; the form only lists the others.
        assertEquals(listOf(owner, other), edited.maintainers())
        assertEquals("c".repeat(40), edited.earliestUniqueCommit())
        assertTrue(edited.isPrivate())
        assertTrue(edited.tags.any { it[0] == "M" && it[1] == owner })
        assertTrue(edited.tags.any { it[0] == "blossoms" })
        assertEquals(listOf("kotlin"), edited.hashtags())
    }

    @Test
    fun problemsNameTheEntries() {
        val problems = GitRepositorySettings.problems("wss://ok.relay\nnot a relay url", "npub1nope\n$other")
        assertEquals(listOf(GitSettingsProblem.Field.RELAYS, GitSettingsProblem.Field.MAINTAINERS), problems.map { it.field })
        assertEquals(listOf("npub1nope"), problems[1].entries)
    }

    @Test
    fun savingWhatIsThereChangesNothing() {
        fun save(
            from: GitRepositoryEvent,
            name: String,
        ) = GitRepositorySettings.edit(from, name, "", "", "https://git.example/repo.git", "wss://old.relay/", Hex.decode(other).toNpub(), listOf("kotlin"))
        val saved = save(previous, "Repo").let { signedLike(it.tags, it.createdAt) }
        assertTrue(GitRepositorySettings.isUnchanged(saved, save(saved, "Repo")))
        assertTrue(!GitRepositorySettings.isUnchanged(saved, save(saved, "Other")))
    }
}
