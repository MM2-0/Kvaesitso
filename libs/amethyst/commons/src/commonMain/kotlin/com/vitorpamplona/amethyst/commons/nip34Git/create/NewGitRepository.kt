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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip34Git.grasp.GraspServer
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/**
 * Assembles a brand-new NIP-34 repository announcement (kind 30617) from what a
 * user typed, the way `ngit init` does for a fresh repository:
 *
 *  - each GRASP server contributes its clone URL (`https://<server>/<npub>/<id>.git`)
 *    after any other clone URLs, and its relay (`wss://<server>`) before any other
 *    relays, so the first GRASP server is the first relay hint;
 *  - relays are written as ngit writes them, without the bare trailing slash;
 *  - the author is the implicit sole maintainer, so no `maintainers` tag is written;
 *  - hashtags are lowercased without a leading `#`.
 *
 * A phone has no checkout to read the root commit from, so the earliest-unique-commit
 * is whatever the user pastes (`git rev-list --max-parents=0 HEAD`), or nothing.
 */
object NewGitRepository {
    class Draft(
        val name: String,
        val identifier: String,
        val description: String = "",
        val graspServers: List<String> = emptyList(),
        val cloneUrls: List<String> = emptyList(),
        val webUrls: List<String> = emptyList(),
        val relays: List<String> = emptyList(),
        val earliestUniqueCommit: String = "",
        val hashtags: List<String> = emptyList(),
    )

    private val COMMIT_ID = Regex("^([0-9a-f]{40}|[0-9a-f]{64})$")

    /** A full SHA-1 or SHA-256 commit id, in any case. Blank is allowed: the tag is optional. */
    fun isValidCommitId(commit: String): Boolean = commit.isBlank() || COMMIT_ID.matches(commit.trim().lowercase())

    fun isValidGraspServer(server: String): Boolean = GraspServer.normalize(server) != null

    fun isValidRelay(relay: String): Boolean = RelayUrlNormalizer.normalizeOrNull(relay) != null

    /** Splits a free-text list field on newlines, commas and spaces; trims and dedupes. */
    fun splitList(raw: String): List<String> =
        raw
            .split('\n', ',', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun build(
        draft: Draft,
        owner: HexKey,
    ): EventTemplate<GitRepositoryEvent> {
        val identifier = draft.identifier.trim()
        val npub = owner.hexToByteArray().toNpub()

        val graspClones = draft.graspServers.mapNotNull { GraspServer.cloneUrl(it, npub, identifier) }
        val graspRelays = draft.graspServers.mapNotNull { GraspServer.relayUrl(it) }
        val extraRelays = draft.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it)?.url?.let(::withoutRootSlash) }

        return GitRepositoryEvent.build(
            name = draft.name.trim(),
            description = draft.description.trim().ifBlank { null },
            webUrls = draft.webUrls.distinct(),
            cloneUrls = (draft.cloneUrls + graspClones).distinct(),
            relays = (graspRelays + extraRelays).distinctBy { RelayUrlNormalizer.normalizeOrNull(it) ?: it },
            maintainers = emptyList(),
            hashtags =
                draft.hashtags
                    .map { it.trim().removePrefix("#").lowercase() }
                    .filter { it.isNotEmpty() }
                    .distinct(),
            earliestUniqueCommit =
                draft.earliestUniqueCommit
                    .trim()
                    .lowercase()
                    .ifBlank { null },
            dTag = identifier,
        )
    }

    /** `wss://host/` → `wss://host`; a URL with a real path keeps it. */
    private fun withoutRootSlash(url: String): String {
        val hostStart = url.indexOf("://").let { if (it < 0) 0 else it + 3 }
        return if (url.indexOf('/', hostStart) == url.length - 1) url.dropLast(1) else url
    }
}
