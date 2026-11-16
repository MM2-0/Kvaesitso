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
package com.vitorpamplona.amethyst.commons.model.nip72Communities

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesValidator

/**
 * Returns the most recent [CommunityRulesEvent] whose `a` tag points back at
 * [community], signed by the community owner or a declared moderator.
 *
 * NIP-9B explicitly delegates rules authoring to whichever pubkeys appear in the
 * community's `kind:34550` definition (`p` moderator tags + the owner). Picking
 * the highest-`createdAt` event across that set matches the validator contract:
 * latest rules win.
 *
 * Returns null when the cache hasn't (yet) seen any rules document for this
 * community \u2014 in which case the feed filter behaves like there are no rules,
 * matching pre-9A behaviour.
 */
fun latestCommunityRules(community: CommunityDefinitionEvent): CommunityRulesEvent? {
    val signers = community.moderatorKeys().toSet()
    if (signers.isEmpty()) return null

    val communityAddress = community.addressTag()

    return LocalCache.addressables
        .filter(CommunityRulesEvent.KIND) { _, note ->
            val event = note.event as? CommunityRulesEvent ?: return@filter false
            event.pubKey in signers && event.communityAddress() == communityAddress
        }.asSequence()
        .mapNotNull { it.event as? CommunityRulesEvent }
        .maxByOrNull { it.createdAt }
}

/**
 * Returns true when [note] would violate [rules] under [CommunityRulesValidator].
 *
 * `wot` and per-kind quota inputs are deliberately null-passed here \u2014 those
 * checks are deferred to follow-up issues and the validator skips them cleanly
 * when the callbacks aren't supplied.
 */
fun violatesCommunityRules(
    validator: CommunityRulesValidator,
    note: Note,
): Boolean {
    val event = note.event ?: return false
    return validator.validate(
        author = event.pubKey,
        kind = event.kind,
        sizeBytes = event.sizeInBytes(),
    ) != null
}

/**
 * Approximate event size in bytes used by [CommunityRulesValidator]. Matches the
 * validator's `max_event_size` and per-kind `max-bytes` semantics: total UTF-8
 * size of the content (not the wire-encoded JSON, which the validator can't be
 * specific about across implementations).
 */
private fun Event.sizeInBytes(): Int = content.encodeToByteArray().size
