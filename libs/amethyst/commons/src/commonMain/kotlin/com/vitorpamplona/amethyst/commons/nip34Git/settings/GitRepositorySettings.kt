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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/** A field of the settings form that could not be read: the entries the user should fix. */
class GitSettingsProblem(
    val field: Field,
    val entries: List<String>,
) {
    enum class Field { RELAYS, MAINTAINERS }
}

/**
 * The owner's edit of a repository announcement (kind 30617), as gitworkshop's settings make it:
 * the fields the form shows are replaced, everything else (the euc, `M`/`m`/`o` role tags,
 * `private`, `blossoms`, tags of other clients) is kept, and it is dated after the version it
 * replaces so relays keep the new one.
 */
object GitRepositorySettings {
    /** One entry per line or comma, trimmed, blanks and repeats dropped. */
    fun splitEntries(raw: String): List<String> =
        raw
            .split('\n', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Each maintainer as hex, from an npub, nprofile or hex key; the entries that are none of those go to [invalid]. */
    fun parseMaintainers(
        raw: String,
        invalid: MutableList<String>,
    ): List<HexKey> =
        splitEntries(raw)
            .mapNotNull { entry ->
                decodePublicKeyAsHexOrNull(entry.removePrefix("nostr:")).also { if (it == null) invalid.add(entry) }
            }.distinct()

    /**
     * Each relay as typed (with `wss://` added when it has no scheme), so an untouched list is
     * republished as it was; the entries that are not relay URLs go to [invalid].
     */
    fun parseRelays(
        raw: String,
        invalid: MutableList<String>,
    ): List<String> =
        splitEntries(raw)
            .mapNotNull { entry ->
                val url = if ("://" in entry) entry else "wss://$entry"
                if (RelayUrlNormalizer.normalizeOrNull(url) == null) {
                    invalid.add(entry)
                    null
                } else {
                    url
                }
            }.distinct()

    /** The problems of [relays] and [maintainers] as typed, empty when both read cleanly. */
    fun problems(
        relays: String,
        maintainers: String,
    ): List<GitSettingsProblem> {
        val badRelays = ArrayList<String>()
        val badMaintainers = ArrayList<String>()
        parseRelays(relays, badRelays)
        parseMaintainers(maintainers, badMaintainers)
        return buildList {
            if (badRelays.isNotEmpty()) add(GitSettingsProblem(GitSettingsProblem.Field.RELAYS, badRelays))
            if (badMaintainers.isNotEmpty()) add(GitSettingsProblem(GitSettingsProblem.Field.MAINTAINERS, badMaintainers))
        }
    }

    /**
     * [previous] with the form's fields replaced. [maintainers] are the others; the owner keeps
     * the place in the tag they had.
     */
    fun edit(
        previous: GitRepositoryEvent,
        name: String,
        description: String,
        webUrls: String,
        cloneUrls: String,
        relays: String,
        maintainers: String,
        topics: List<String>,
        now: Long = TimeUtils.now(),
    ) = GitRepositoryEvent.build(
        name = name.trim(),
        description = description.trim().ifBlank { null },
        webUrls = splitEntries(webUrls),
        cloneUrls = splitEntries(cloneUrls),
        relays = parseRelays(relays, ArrayList()),
        // The form lists the others; the owner stays first when the announcement listed them (ngit does).
        maintainers =
            (if (previous.pubKey in previous.maintainers()) listOf(previous.pubKey) else emptyList()) +
                parseMaintainers(maintainers, ArrayList()).filter { it != previous.pubKey },
        hashtags = topics.map { it.removePrefix("#") }.filter { it.isNotBlank() && it != GitRepositoryEvent.PERSONAL_FORK }.distinct(),
        earliestUniqueCommit = previous.earliestUniqueCommit(),
        personalFork = previous.isPersonalFork(),
        dTag = previous.dTag(),
        createdAt = maxOf(now, previous.createdAt + 1),
    ) {
        previous.unmanagedTags().forEach { add(it) }
    }

    /** True when [template] says what [previous] already says (same tags, in any order): there is nothing to republish. */
    fun isUnchanged(
        previous: GitRepositoryEvent,
        template: EventTemplate<GitRepositoryEvent>,
    ): Boolean {
        fun key(tags: Array<Array<String>>) = tags.map { it.joinToString("\u0000") }.sorted()
        return previous.content == template.content && key(previous.tags) == key(template.tags)
    }
}
