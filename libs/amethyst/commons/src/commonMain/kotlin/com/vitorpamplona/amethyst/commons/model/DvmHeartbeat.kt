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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.nip90DVMs.DvmHeartbeatRegistry
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.DvmContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/** The cache slot a DVM's heartbeat lives in: the announcement's own address, kind 11998. */
fun LocalCache.dvmHeartbeatOf(appDef: AppDefinitionEvent): DvmHeartbeatEvent? = getAddressableNoteIfExists(Address(DvmHeartbeatEvent.KIND, appDef.pubKey, appDef.dTag()))?.event as? DvmHeartbeatEvent

/**
 * A DVM counts as alive only if its latest heartbeat is at most 900s old. The registry (not the
 * WeakReference-held beat note) is the freshness source: beat notes have no strong holder on the
 * Discover screen, and a GC sweep cleared them all at once, collapsing the list.
 */
fun LocalCache.hasFreshDvmHeartbeat(
    appDef: AppDefinitionEvent,
    now: Long = TimeUtils.now(),
): Boolean =
    DvmHeartbeatRegistry
        .latestAt(Address(DvmHeartbeatEvent.KIND, appDef.pubKey, appDef.dTag()))
        ?.let { it >= now - DvmHeartbeatEvent.MAX_AGE_SECONDS } == true

/**
 * Every cached content-discovery announcement, WITHOUT the freshness gate — this is the source the
 * heartbeat outbox fetcher must use. Sourcing from the gated feed list would drop a DVM the moment
 * its beat went stale, remove it from the fetch batch, and make the drop permanent (the fetcher
 * could only ever help DVMs that were already visible). Applies the gate's other eligibility
 * checks (a real content-discovery DVM, not a paid subscription app), newest first, capped.
 */
fun LocalCache.cachedDvmAnnouncements(limit: Int = 100): List<AppDefinitionEvent> =
    addressables
        .filterIntoSet(AppDefinitionEvent.KIND) { _, note ->
            (note.event as? AppDefinitionEvent)?.let {
                it.appMetaData()?.subscription != true && it.includeKind(DvmContentDiscoveryRequestEvent.KIND)
            } == true
        }.mapNotNull { it.event as? AppDefinitionEvent }
        .sortedByDescending { it.createdAt }
        .take(limit)
