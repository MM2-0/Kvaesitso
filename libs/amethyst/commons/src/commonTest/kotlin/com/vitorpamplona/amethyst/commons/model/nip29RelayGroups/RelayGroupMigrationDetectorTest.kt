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
package com.vitorpamplona.amethyst.commons.model.nip29RelayGroups

import com.vitorpamplona.amethyst.commons.model.preferences.RelayGroupAdminCacheStore
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** NIP-29 §Detecting migrations and forks, over other people's kind-10009 lists. */
class RelayGroupMigrationDetectorTest {
    private val gid = "pizza"
    private val current = GroupId(gid, RelayUrlNormalizer.normalize("wss://old.example.com"))
    private val newRelay = RelayUrlNormalizer.normalize("wss://new.example.com")
    private val forkRelay = RelayUrlNormalizer.normalize("wss://fork.example.com")

    private val admin = "aa".repeat(32)
    private val friend = "bb".repeat(32)
    private val stranger = "cc".repeat(32)
    private val me = "dd".repeat(32)

    private fun list(
        author: String,
        createdAt: Long,
        vararg entries: Pair<String, String>,
    ) = SimpleGroupListEvent(
        "00".repeat(32),
        author,
        createdAt,
        entries.map { (id, relay) -> arrayOf("group", id, relay) }.toTypedArray(),
        "",
        "22".repeat(64),
    )

    @AfterTest
    fun cleanup() = RelayGroupAdminCache.clearForTesting()

    @Test
    fun adminMovedGroupIsReportedAsAMove() {
        val found =
            RelayGroupMigrationDetector.detect(
                current,
                listOf(list(admin, 10, gid to "wss://new.example.com/")),
                admins = setOf(admin),
                friends = emptySet(),
            )

        assertEquals(1, found.size)
        assertEquals(newRelay, found[0].relay)
        assertEquals(setOf(admin), found[0].admins)
        assertTrue(found[0].looksLikeMove)
    }

    @Test
    fun listingBothRelaysReadsAsAFork() {
        val found =
            RelayGroupMigrationDetector.detect(
                current,
                listOf(list(friend, 10, gid to "wss://old.example.com", gid to "wss://fork.example.com")),
                admins = emptySet(),
                friends = setOf(friend),
            )

        assertEquals(forkRelay, found.single().relay)
        assertEquals(setOf(friend), found.single().friends)
        assertFalse(found.single().looksLikeMove)
    }

    @Test
    fun ignoresSameRelayOtherGroupsStrangersSelfAndOlderLists() {
        val found =
            RelayGroupMigrationDetector.detect(
                current,
                listOf(
                    list(admin, 5, gid to "wss://fork.example.com"), // superseded by the newer list below
                    list(admin, 10, gid to "wss://old.example.com/", "other" to "wss://new.example.com"),
                    list(stranger, 10, gid to "wss://new.example.com"),
                    list(me, 10, gid to "wss://new.example.com"),
                ),
                admins = setOf(admin, me),
                friends = setOf(friend),
                self = me,
            )

        assertTrue(found.isEmpty(), "got $found")
    }

    @Test
    fun adminBackedRelaysSortFirst() {
        val friend2 = "ee".repeat(32)
        val found =
            RelayGroupMigrationDetector.detect(
                current,
                listOf(
                    list(friend, 10, gid to "wss://fork.example.com"),
                    list(friend2, 10, gid to "wss://fork.example.com"),
                    list(admin, 10, gid to "wss://new.example.com"),
                ),
                admins = setOf(admin),
                friends = setOf(friend, friend2),
            )

        assertEquals(listOf(newRelay, forkRelay), found.map { it.relay })
        assertEquals(2, found[1].supporters)
    }

    @Test
    fun adminCacheRoundTripsThroughItsDiskEncoding() {
        RelayGroupAdminCache.remember(current, setOf(admin, friend))
        RelayGroupAdminCache.remember(current, emptySet()) // an empty roster never wipes the cache

        val restored = RelayGroupAdminCacheStore.decode(RelayGroupAdminCacheStore.encode(RelayGroupAdminCache.flow.value))
        assertEquals(mapOf(current.toKey() to setOf(admin, friend)), restored)
        assertEquals(setOf(admin, friend), RelayGroupAdminCache.adminsOf(current))
    }

    @Test
    fun restoringFromDiskKeepsRememberCallsThatLandedFirst() {
        val other = GroupId("other", newRelay)
        // The async disk read completes after the UI already recorded a fresher roster.
        RelayGroupAdminCache.remember(current, setOf(friend))

        val merged = RelayGroupAdminCache.restore(mapOf(current.toKey() to setOf(admin), other.toKey() to setOf(stranger)))

        assertEquals(setOf(friend), RelayGroupAdminCache.adminsOf(current), "the in-memory entry is newer and wins")
        assertEquals(setOf(stranger), RelayGroupAdminCache.adminsOf(other), "disk-only entries are restored")
        assertEquals(RelayGroupAdminCache.flow.value, merged)
    }

    @Test
    fun adminCacheIsCappedDroppingTheLeastRecentlyChanged() {
        val first = GroupId("g0", newRelay)
        RelayGroupAdminCache.remember(first, setOf(admin))
        repeat(RelayGroupAdminCache.MAX_GROUPS) { i -> RelayGroupAdminCache.remember(GroupId("g${i + 1}", newRelay), setOf(admin)) }

        assertEquals(RelayGroupAdminCache.MAX_GROUPS, RelayGroupAdminCache.flow.value.size)
        assertTrue(RelayGroupAdminCache.adminsOf(first).isEmpty(), "the oldest entry is evicted")
        assertEquals(setOf(admin), RelayGroupAdminCache.adminsOf(GroupId("g${RelayGroupAdminCache.MAX_GROUPS}", newRelay)))

        // A restore past the cap trims too, keeping the in-memory (newer) entries.
        val fromDisk = (0 until RelayGroupAdminCache.MAX_GROUPS).associate { GroupId("disk$it", newRelay).toKey() to setOf(friend) }
        RelayGroupAdminCache.restore(fromDisk)
        assertEquals(RelayGroupAdminCache.MAX_GROUPS, RelayGroupAdminCache.flow.value.size)
        assertEquals(setOf(admin), RelayGroupAdminCache.adminsOf(GroupId("g${RelayGroupAdminCache.MAX_GROUPS}", newRelay)))
    }
}
