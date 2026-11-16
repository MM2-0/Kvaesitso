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
package com.vitorpamplona.amethyst.commons.model.cache

import androidx.collection.LruCache
import com.vitorpamplona.amethyst.commons.model.toNIP19OrFallback
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.njumpLink
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow

data class Spammer(
    val pubkeyHex: HexKey,
    var duplicatedEventIds: Set<HexKey>,
    var duplicatedEventAddresses: Set<Address>,
) {
    fun shouldHide() = duplicatedEventIds.size >= 5 || duplicatedEventAddresses.size >= 5
}

class AntiSpamFilter(
    /**
     * Where duplicate reports go. Read on each hit rather than captured, because the cache that
     * owns this filter is built long before the app shell installs its [LocalCacheHost].
     */
    private val host: () -> LocalCacheHost = { LocalCacheHost },
) {
    val recentEventIds = LruCache<Int, String>(2000)
    val recentAddressables = LruCache<Int, Address>(2000)
    val spamMessages = LruCache<Int, Spammer>(1000)

    var active: Boolean = true

    fun spamHashCode(event: Event): Int = 31 * event.content.hashCode() + event.tags.contentDeepHashCode()

    fun isSpam(
        event: Event,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        if (!active) return false

        // if short message, ok
        // The idea here is to avoid considering repeated "GM" messages spam.
        if (event.content.length < 60) return false

        // if the message is actually short but because it cites a user/event, the nostr: string is
        // really long, make it ok.
        // The idea here is to avoid considering repeated "@Bot, command" messages spam, while still
        // blocking repeated "lnbc..." invoices or fishing urls
        if (event.content.length < 180 && event.content.startsWith("nostr:") && Nip19Parser.hasAny(event.content)) return false

        // double list strategy:
        // if duplicated, it goes into spam. 1000 spam messages are saved into the spam list.

        // Considers tags so that same replies to different people don't count.
        val hash = spamHashCode(event)

        // ignores multiple versions of the same addressable.
        if (event is AddressableEvent) {
            val address = event.address()

            // normal event
            // existingAddress may be null if the first duplicate was evicted from the LRU cache
            // while the spammer record still matches this hash.
            val existingAddress = recentAddressables[hash]
            val knownSpammer = spamMessages[hash]
            if (
                (existingAddress != null && existingAddress != address) ||
                (knownSpammer != null && !knownSpammer.duplicatedEventAddresses.contains(address))
            ) {
                // Built only when read: at DEBUG, or when the spammer is hidden. The d-tag is the
                // sender's, so an over-long one must not throw on ingest.
                fun link2() = njumpLink(address.toNIP19OrFallback(relay, event))

                fun link1() = existingAddress?.let { njumpLink(it.toNIP19OrFallback(relay, null)) } ?: link2()

                // Debug, not warn: a duplicate detection is this filter working, not a fault, and
                // it is already reported where it can be acted on — relayStats.newSpam below and
                // the flowSpam emission that drives the UI. On a normal boot this fires ~34 times
                // with a pair of njump links each, which is the widest line in the log and says
                // nothing the spam counters don't. Keep the links at DEBUG for when you need to
                // open the two events and compare them.
                Log.d("Duplicated/SPAM") { "${relay?.url} ${link1()} ${link2()}" }

                // Log down offenders
                val spammer = logOffender(hash, event)

                if (spammer.shouldHide() && relay != null) {
                    host()
                        .relayStats
                        ?.get(relay)
                        ?.newSpam(link1(), link2())
                }

                flowSpam.tryEmit(AntiSpamState(this))

                return true
            }

            recentAddressables.put(hash, address)
        } else {
            // normal event
            val existingEvent = recentEventIds[hash]
            val knownSpammer = spamMessages[hash]
            if (
                (existingEvent != null && existingEvent != event.id) ||
                (knownSpammer != null && !knownSpammer.duplicatedEventIds.contains(event.id))
            ) {
                // Built only when read: at DEBUG, or when the spammer is hidden.
                fun link2() = njumpLink(NEvent.create(event.id, null, null, relay))

                // existingEvent may be null if the first duplicate was evicted from the
                // LRU cache while the spammer record still matches this hash.
                fun link1() = existingEvent?.let { njumpLink(NEvent.create(it, null, null, relay)) } ?: link2()

                // Debug, not warn: a duplicate detection is this filter working, not a fault, and
                // it is already reported where it can be acted on — relayStats.newSpam below and
                // the flowSpam emission that drives the UI. On a normal boot this fires ~34 times
                // with a pair of njump links each, which is the widest line in the log and says
                // nothing the spam counters don't. Keep the links at DEBUG for when you need to
                // open the two events and compare them.
                Log.d("Duplicated/SPAM") { "${relay?.url} ${link1()} ${link2()}" }

                // Log down offenders
                val spammer = logOffender(hash, event)

                if (spammer.shouldHide() && relay != null) {
                    host()
                        .relayStats
                        ?.get(relay)
                        ?.newSpam(link1(), link2())
                }

                flowSpam.tryEmit(AntiSpamState(this))

                return true
            }

            recentEventIds.put(hash, event.id)
        }

        return false
    }

    // Was @Synchronized, which is a JVM-only annotation: same mutual exclusion, spelled
    // for every target. Only this method touches spamMessages for a given hash.
    private val offenderLock = KmpLock()

    private fun logOffender(
        hashCode: Int,
        event: Event,
    ): Spammer =
        offenderLock.withLock {
            val spammer = spamMessages.get(hashCode)

            if (spammer == null) {
                val newSpammer =
                    if (event is AddressableEvent) {
                        Spammer(
                            pubkeyHex = event.pubKey,
                            duplicatedEventIds = setOf(),
                            duplicatedEventAddresses = setOfNotNull(recentAddressables[hashCode], event.address()),
                        )
                    } else {
                        Spammer(
                            pubkeyHex = event.pubKey,
                            duplicatedEventIds = setOfNotNull(recentEventIds[hashCode], event.id),
                            duplicatedEventAddresses = setOf(),
                        )
                    }
                spamMessages.put(hashCode, newSpammer)
                newSpammer
            } else {
                if (event is AddressableEvent) {
                    spammer.duplicatedEventAddresses += event.address()
                } else {
                    spammer.duplicatedEventIds += event.id
                }
                spammer
            }
        }

    val flowSpam = MutableStateFlow(AntiSpamState(this))
}

class AntiSpamState(
    val cache: AntiSpamFilter,
)
