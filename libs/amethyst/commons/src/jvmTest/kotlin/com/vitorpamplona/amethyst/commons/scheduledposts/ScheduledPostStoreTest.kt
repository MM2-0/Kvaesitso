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
package com.vitorpamplona.amethyst.commons.scheduledposts

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class ScheduledPostStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var file: File

    @Before
    fun setUp() {
        file = File(temp.root, "scheduled_posts.json")
    }

    private fun newStore(now: () -> Long = { System.currentTimeMillis() / 1000 }) = ScheduledPostStore(file.path, now)

    private fun samplePost(
        id: String = "id-1",
        publishAtSec: Long = 1_000,
        accountPubkey: String = "pk1",
    ) = ScheduledPost(
        id = id,
        accountPubkey = accountPubkey,
        signedEventJson = "{}",
        relayUrls = listOf("wss://relay.example/"),
        extraEventsJson = emptyList(),
        publishAtSec = publishAtSec,
        createdAtSec = 500,
    )

    @Test
    fun stalePublishingClaimsAreReleasedOnLoad() =
        runTest {
            val store = newStore()
            store.add(samplePost(id = "a"))
            val claimed = store.claimDuePosts(nowSec = 2_000)
            assertEquals(1, claimed.size)
            assertEquals(ScheduledPostStatus.PUBLISHING, store.list().single().status)

            // A fresh store (new process) finds the on-disk PUBLISHING row: the
            // worker that claimed it died with the old process, so the claim
            // must be released for the next cycle to retry.
            val reloaded = newStore().list().single()
            assertEquals(ScheduledPostStatus.PENDING, reloaded.status)
        }

    @Test
    fun add_persists_to_disk() =
        runTest {
            val store = newStore()
            store.add(samplePost())
            assertTrue("storage file should exist after add", file.exists())

            val reloaded = newStore().list()
            assertEquals(1, reloaded.size)
            assertEquals("id-1", reloaded[0].id)
            assertEquals(ScheduledPostStatus.PENDING, reloaded[0].status)
        }

    @Test
    fun persisted_file_is_owner_only() =
        runTest {
            newStore().add(samplePost())

            val perms =
                try {
                    Files.getPosixFilePermissions(file.toPath())
                } catch (e: UnsupportedOperationException) {
                    // Non-POSIX filesystem (e.g. Windows CI) — nothing to assert.
                    return@runTest
                }
            assertTrue("owner should read/write", perms.contains(PosixFilePermission.OWNER_READ))
            assertFalse("group must not read the store", perms.contains(PosixFilePermission.GROUP_READ))
            assertFalse("others must not read the store", perms.contains(PosixFilePermission.OTHERS_READ))
        }

    @Test
    fun claimDuePosts_returns_only_due_pending_posts() =
        runTest {
            val store = newStore()
            store.add(samplePost(id = "due", publishAtSec = 1000))
            store.add(samplePost(id = "future", publishAtSec = 5000))
            store.add(samplePost(id = "also-due", publishAtSec = 999))

            val claimed = store.claimDuePosts(nowSec = 1000)
            val ids = claimed.map { it.id }.toSet()
            assertEquals(setOf("due", "also-due"), ids)
        }

    @Test
    fun claimDuePosts_flips_status_to_publishing() =
        runTest {
            val store = newStore()
            store.add(samplePost(publishAtSec = 1000))

            store.claimDuePosts(nowSec = 1000)

            val all = store.list()
            assertEquals(ScheduledPostStatus.PUBLISHING, all[0].status)
            assertEquals(1, all[0].attemptCount)
            assertEquals(1000L, all[0].lastAttemptAtSec)
        }

    @Test
    fun claimDuePosts_second_call_returns_empty() =
        runTest {
            val store = newStore()
            store.add(samplePost(publishAtSec = 1000))

            val first = store.claimDuePosts(nowSec = 1000)
            val second = store.claimDuePosts(nowSec = 1000)

            assertEquals(1, first.size)
            assertEquals(0, second.size)
        }

    @Test
    fun concurrent_claimDuePosts_only_one_wins() =
        runTest {
            val store = newStore()
            store.add(samplePost(id = "race", publishAtSec = 1000))

            val results =
                (1..10)
                    .map { async { store.claimDuePosts(nowSec = 1000) } }
                    .awaitAll()

            val totalClaimed = results.sumOf { it.size }
            assertEquals("exactly one concurrent caller should claim the post", 1, totalClaimed)
        }

    @Test
    fun markSent_updates_status_and_clears_error() =
        runTest {
            val store = newStore()
            store.add(samplePost(publishAtSec = 1000))
            // markFailed/markSent only transition PUBLISHING rows, so claim first.
            store.claimDuePosts(nowSec = 1000)
            store.markFailed("id-1", "earlier error")
            // Re-claim to get back to PUBLISHING before the successful send.
            store.publishNow("id-1", nowSec = 1000)
            store.claimDuePosts(nowSec = 1000)
            store.markSent("id-1")

            val all = store.list()
            assertEquals(ScheduledPostStatus.SENT, all[0].status)
            assertNull(all[0].lastError)
        }

    @Test
    fun markFailed_records_error() =
        runTest {
            val store = newStore()
            store.add(samplePost(publishAtSec = 1000))
            store.claimDuePosts(nowSec = 1000) // -> PUBLISHING
            store.markFailed("id-1", "boom")

            val all = store.list()
            assertEquals(ScheduledPostStatus.FAILED, all[0].status)
            assertEquals("boom", all[0].lastError)
        }

    @Test
    fun releaseClaim_reverts_publishing_to_pending() =
        runTest {
            val store = newStore()
            store.add(samplePost(publishAtSec = 1000))
            store.claimDuePosts(nowSec = 1000)

            store.releaseClaim("id-1")

            val all = store.list()
            assertEquals(ScheduledPostStatus.PENDING, all[0].status)
        }

    @Test
    fun releaseClaim_does_not_touch_non_publishing() =
        runTest {
            val store = newStore()
            store.add(samplePost())
            store.releaseClaim("id-1")

            assertEquals(ScheduledPostStatus.PENDING, store.list()[0].status)
        }

    @Test
    fun cancel_sets_cancelled_status_and_returns_true() =
        runTest {
            val store = newStore()
            store.add(samplePost())

            val ok = store.cancel("id-1")

            assertTrue(ok)
            assertEquals(ScheduledPostStatus.CANCELLED, store.list()[0].status)
        }

    @Test
    fun cancel_unknown_id_returns_false() =
        runTest {
            val store = newStore()
            assertEquals(false, store.cancel("nope"))
        }

    @Test
    fun listFor_filters_by_account() =
        runTest {
            val store = newStore()
            store.add(samplePost(id = "a", accountPubkey = "pk-a"))
            store.add(samplePost(id = "b", accountPubkey = "pk-b"))
            store.add(samplePost(id = "c", accountPubkey = "pk-a"))

            val filtered = store.listFor("pk-a").map { it.id }.toSet()
            assertEquals(setOf("a", "c"), filtered)
        }

    @Test
    fun cancelled_posts_are_not_claimed() =
        runTest {
            val store = newStore()
            store.add(samplePost(publishAtSec = 1000))
            store.cancel("id-1")

            val claimed = store.claimDuePosts(nowSec = 5000)

            assertEquals(0, claimed.size)
        }

    @Test
    fun missing_file_loads_as_empty() =
        runTest {
            assertTrue("file should not exist before first read", !file.exists())
            val store = newStore()
            assertEquals(0, store.list().size)
        }

    @Test
    fun corrupt_file_loads_as_empty() =
        runTest {
            file.writeText("not valid json {{{")
            val store = newStore()
            assertEquals(0, store.list().size)
        }

    @Test
    fun publishNow_sets_publishAtSec_to_now_and_status_pending() =
        runTest {
            val store = newStore()
            store.add(samplePost(publishAtSec = 9_999_999))

            val ok = store.publishNow("id-1", nowSec = 1234)

            assertTrue(ok)
            val updated = store.list().single()
            assertEquals(ScheduledPostStatus.PENDING, updated.status)
            assertEquals(1234L, updated.publishAtSec)
        }

    @Test
    fun publishNow_clears_failed_state_for_retry() =
        runTest {
            val store = newStore()
            store.add(samplePost(publishAtSec = 1000))
            store.claimDuePosts(nowSec = 1000) // -> PUBLISHING so markFailed applies
            store.markFailed("id-1", "earlier failure")

            store.publishNow("id-1", nowSec = 5000)

            val updated = store.list().single()
            assertEquals(ScheduledPostStatus.PENDING, updated.status)
            assertNull(updated.lastError)
        }

    @Test
    fun publishNow_unknown_id_returns_false() =
        runTest {
            val store = newStore()
            assertEquals(false, store.publishNow("nope"))
        }

    @Test
    fun publishNow_makes_post_immediately_claimable() =
        runTest {
            val store = newStore()
            store.add(samplePost(publishAtSec = 9_999_999))
            assertEquals(0, store.claimDuePosts(nowSec = 1000).size)

            store.publishNow("id-1", nowSec = 1000)

            val claimed = store.claimDuePosts(nowSec = 1000)
            assertEquals(1, claimed.size)
            assertEquals("id-1", claimed[0].id)
        }

    @Test
    fun flow_emits_initial_empty_then_post_after_add() =
        runTest {
            val store = newStore()
            assertEquals(0, store.flow.value.size)

            store.add(samplePost())

            assertEquals(1, store.flow.value.size)
            assertEquals("id-1", store.flow.value[0].id)
        }

    @Test
    fun flow_reflects_status_transitions() =
        runTest {
            val store = newStore()
            store.add(samplePost(publishAtSec = 1000))
            assertEquals(ScheduledPostStatus.PENDING, store.flow.value[0].status)

            store.claimDuePosts(nowSec = 1000)
            assertEquals(ScheduledPostStatus.PUBLISHING, store.flow.value[0].status)

            store.markSent("id-1")
            assertEquals(ScheduledPostStatus.SENT, store.flow.value[0].status)
        }

    @Test
    fun flow_seeded_from_disk_on_first_access() =
        runTest {
            // Pre-populate the file via a first store instance
            newStore().add(samplePost())

            // Second store starts with empty in-memory flow until first access
            val store = newStore()
            assertEquals(0, store.flow.value.size)

            // Triggering any read method causes ensureLoaded() to seed the flow
            store.list()
            assertEquals(1, store.flow.value.size)
        }

    @Test
    fun removeForAccount_removes_all_matching_rows_and_returns_count() =
        runTest {
            val store = newStore()
            store.add(samplePost(id = "a1", accountPubkey = "pk-a"))
            store.add(samplePost(id = "a2", accountPubkey = "pk-a"))
            store.add(samplePost(id = "b1", accountPubkey = "pk-b"))

            val removed = store.removeForAccount("pk-a")

            assertEquals(2, removed)
            val remaining = store.list()
            assertEquals(1, remaining.size)
            assertEquals("b1", remaining[0].id)
        }

    @Test
    fun removeForAccount_no_match_returns_zero_and_does_not_persist() =
        runTest {
            val store = newStore()
            store.add(samplePost(accountPubkey = "pk-a"))
            val bytesBefore = file.readBytes()

            val removed = store.removeForAccount("pk-other")

            assertEquals(0, removed)
            assertEquals(1, store.list().size)
            assertTrue("file should not be rewritten on no-op", bytesBefore.contentEquals(file.readBytes()))
        }

    @Test
    fun removeForAccount_persists_to_disk() =
        runTest {
            val store = newStore()
            store.add(samplePost(id = "a1", accountPubkey = "pk-a"))
            store.add(samplePost(id = "b1", accountPubkey = "pk-b"))

            store.removeForAccount("pk-a")

            val reloaded = newStore().list()
            assertEquals(1, reloaded.size)
            assertEquals("b1", reloaded[0].id)
        }

    @Test
    fun removeForAccount_purges_terminal_states_too() =
        runTest {
            val store = newStore()
            store.add(samplePost(id = "p1", publishAtSec = 1000, accountPubkey = "pk-a"))
            store.add(samplePost(id = "p2", accountPubkey = "pk-a"))
            store.claimDuePosts(nowSec = 1000) // p1 -> PUBLISHING so markSent applies
            store.markSent("p1")
            store.cancel("p2")

            val removed = store.removeForAccount("pk-a")

            assertEquals(2, removed)
            assertEquals(0, store.list().size)
        }

    @Test
    fun cancel_stamps_terminatedAtSec() =
        runTest {
            val clock = 1_700_000_000L
            val store = newStore { clock }
            store.add(samplePost(id = "x"))

            store.cancel("x")

            assertEquals(clock, store.list().single().terminatedAtSec)
        }

    @Test
    fun markSent_stamps_terminatedAtSec() =
        runTest {
            val clock = 1_700_000_000L
            val store = newStore { clock }
            store.add(samplePost(id = "x", publishAtSec = clock))
            store.claimDuePosts(clock)

            store.markSent("x")

            assertEquals(clock, store.list().single().terminatedAtSec)
        }

    @Test
    fun publishNow_clears_terminatedAtSec() =
        runTest {
            val clock = 1_700_000_000L
            val store = newStore { clock }
            store.add(samplePost(id = "x"))
            store.cancel("x") // stamps terminatedAtSec

            store.publishNow("x", nowSec = clock + 5)

            assertNull(store.list().single().terminatedAtSec)
        }

    @Test
    fun ensureLoaded_purges_sent_older_than_seven_days() =
        runTest {
            val createTime = 1_700_000_000L
            newStore { createTime }.also { it.add(samplePost(id = "old-sent", publishAtSec = createTime)) }
            newStore { createTime }.also {
                it.claimDuePosts(createTime)
                it.markSent("old-sent")
            }

            val eightDaysLater = createTime + 8L * 24 * 3600
            val reloaded = newStore { eightDaysLater }
            assertEquals(0, reloaded.list().size)
        }

    @Test
    fun ensureLoaded_keeps_recent_sent() =
        runTest {
            val createTime = 1_700_000_000L
            newStore { createTime }.also { it.add(samplePost(id = "fresh", publishAtSec = createTime)) }
            newStore { createTime }.also {
                it.claimDuePosts(createTime)
                it.markSent("fresh")
            }

            val sixDaysLater = createTime + 6L * 24 * 3600
            val reloaded = newStore { sixDaysLater }
            assertEquals(1, reloaded.list().size)
            assertEquals(ScheduledPostStatus.SENT, reloaded.list().single().status)
        }

    @Test
    fun ensureLoaded_purges_cancelled_older_than_thirty_days() =
        runTest {
            val createTime = 1_700_000_000L
            newStore { createTime }.also {
                it.add(samplePost(id = "old-cancel"))
                it.cancel("old-cancel")
            }

            val thirtyOneDaysLater = createTime + 31L * 24 * 3600
            val reloaded = newStore { thirtyOneDaysLater }
            assertEquals(0, reloaded.list().size)
        }

    @Test
    fun ensureLoaded_keeps_recent_cancelled() =
        runTest {
            val createTime = 1_700_000_000L
            newStore { createTime }.also {
                it.add(samplePost(id = "recent-cancel"))
                it.cancel("recent-cancel")
            }

            val twentyDaysLater = createTime + 20L * 24 * 3600
            val reloaded = newStore { twentyDaysLater }
            assertEquals(1, reloaded.list().size)
            assertEquals(ScheduledPostStatus.CANCELLED, reloaded.list().single().status)
        }

    @Test
    fun ensureLoaded_keeps_failed_indefinitely() =
        runTest {
            val createTime = 1_700_000_000L
            newStore { createTime }.also { it.add(samplePost(id = "fail", publishAtSec = createTime)) }
            newStore { createTime }.also {
                it.claimDuePosts(createTime)
                it.markFailed("fail", "boom")
            }

            val ninetyDaysLater = createTime + 90L * 24 * 3600
            val reloaded = newStore { ninetyDaysLater }
            assertEquals(1, reloaded.list().size)
            assertEquals(ScheduledPostStatus.FAILED, reloaded.list().single().status)
        }

    @Test
    fun ensureLoaded_persists_purge_to_disk() =
        runTest {
            val createTime = 1_700_000_000L
            newStore { createTime }.also { it.add(samplePost(id = "old", publishAtSec = createTime)) }
            newStore { createTime }.also {
                it.claimDuePosts(createTime)
                it.markSent("old")
            }
            val sizeBefore = file.length()

            val eightDaysLater = createTime + 8L * 24 * 3600
            newStore { eightDaysLater }.list() // triggers ensureLoaded + purge + persist

            assertTrue("file should shrink after purge", file.length() < sizeBefore)
        }

    @Test
    fun roundtrip_preserves_all_fields() =
        runTest {
            val original =
                ScheduledPost(
                    id = "roundtrip",
                    accountPubkey = "pk-x",
                    signedEventJson = """{"kind":1,"content":"hi"}""",
                    relayUrls = listOf("wss://a/", "wss://b/"),
                    extraEventsJson = listOf("{}", "{}"),
                    publishAtSec = 1_700_000_000,
                    createdAtSec = 1_699_900_000,
                    status = ScheduledPostStatus.PENDING,
                    lastAttemptAtSec = null,
                    attemptCount = 0,
                    lastError = null,
                )
            newStore().add(original)

            val reloaded = newStore().list().single()
            assertEquals(original, reloaded)
            assertNotNull(reloaded.relayUrls)
            assertEquals(2, reloaded.relayUrls.size)
        }

    // --- Phase 0 store fixes ---

    @Test
    fun stuck_publishing_row_reverts_to_pending_on_load() =
        runTest {
            val claimTime = 1_700_000_000L
            // Claim a post so it becomes PUBLISHING with lastAttemptAtSec = claimTime.
            newStore { claimTime }.also {
                it.add(samplePost(id = "stuck", publishAtSec = claimTime))
                it.claimDuePosts(claimTime)
            }

            // A fresh store instance loads that stuck row 11 minutes later (> CLAIM_TTL of 10m).
            val elevenMinutesLater = claimTime + 11L * 60
            val reloaded = newStore { elevenMinutesLater }

            val row = reloaded.list().single()
            assertEquals(ScheduledPostStatus.PENDING, row.status)
        }

    @Test
    fun stuck_publishing_row_reverts_and_is_reclaimable() =
        runTest {
            val claimTime = 1_700_000_000L
            newStore { claimTime }.also {
                it.add(samplePost(id = "stuck", publishAtSec = claimTime))
                it.claimDuePosts(claimTime)
            }

            // The same store reloaded past the TTL should be able to claim the row again.
            val elevenMinutesLater = claimTime + 11L * 60
            val reloaded = newStore { elevenMinutesLater }

            val claimedAgain = reloaded.claimDuePosts(elevenMinutesLater)
            assertEquals(1, claimedAgain.size)
            assertEquals("stuck", claimedAgain[0].id)
        }

    @Test
    fun fresh_publishing_row_is_not_reverted_before_ttl() =
        runTest {
            val claimTime = 1_700_000_000L
            newStore { claimTime }.also {
                it.add(samplePost(id = "fresh-claim", publishAtSec = claimTime))
                it.claimDuePosts(claimTime)
            }

            // Only 5 minutes elapsed — under the 10 minute TTL, so it stays PUBLISHING.
            val fiveMinutesLater = claimTime + 5L * 60
            val reloaded = newStore { fiveMinutesLater }

            assertEquals(ScheduledPostStatus.PUBLISHING, reloaded.list().single().status)
        }

    @Test
    fun accountScoped_claim_only_returns_matching_account() =
        runTest {
            val store = newStore()
            store.add(samplePost(id = "a-due", publishAtSec = 1000, accountPubkey = "pk-a"))
            store.add(samplePost(id = "b-due", publishAtSec = 1000, accountPubkey = "pk-b"))

            val claimed = store.claimDuePosts(nowSec = 1000, accountPubkey = "pk-a")

            assertEquals(setOf("a-due"), claimed.map { it.id }.toSet())
            // pk-b's due post should remain PENDING, untouched by the scoped claim.
            val bRow = store.list().single { it.id == "b-due" }
            assertEquals(ScheduledPostStatus.PENDING, bRow.status)
        }

    @Test
    fun accountScoped_claim_leaves_other_account_claimable() =
        runTest {
            val store = newStore()
            store.add(samplePost(id = "a-due", publishAtSec = 1000, accountPubkey = "pk-a"))
            store.add(samplePost(id = "b-due", publishAtSec = 1000, accountPubkey = "pk-b"))

            store.claimDuePosts(nowSec = 1000, accountPubkey = "pk-a")
            val bClaim = store.claimDuePosts(nowSec = 1000, accountPubkey = "pk-b")

            assertEquals(setOf("b-due"), bClaim.map { it.id }.toSet())
        }

    // --- Fix 1: cross-process reload before mutation ---

    @Test
    fun claim_adopts_external_sent_written_by_another_process() =
        runTest {
            // Two store instances over the same file model the running app + the headless
            // publisher. Instance A loads the store, then instance B (the "other process")
            // claims and marks the post SENT — writing that to disk. When A next claims, it
            // must reload B's SENT write and NOT re-claim the already-published post.
            val storeA = newStore()
            val storeB = newStore()

            storeA.add(samplePost(id = "shared", publishAtSec = 1000))
            // Pull A's in-memory state in (simulates A having loaded earlier).
            storeA.list()

            // B claims and publishes the post out from under A.
            val bClaim = storeB.claimDuePosts(nowSec = 1000)
            assertEquals(1, bClaim.size)
            storeB.markSent("shared")

            // A now claims; reload picks up B's SENT so nothing due remains.
            val aClaim = storeA.claimDuePosts(nowSec = 1000)
            assertEquals("A must not re-claim a post another process already SENT", 0, aClaim.size)
            assertEquals(ScheduledPostStatus.SENT, storeA.list().single().status)
        }

    // --- Fix 5: status guards ---

    @Test
    fun cancel_on_publishing_row_is_noop_and_returns_false() =
        runTest {
            val store = newStore()
            store.add(samplePost(publishAtSec = 1000))
            store.claimDuePosts(nowSec = 1000) // -> PUBLISHING

            val ok = store.cancel("id-1")

            assertFalse("cancel must not touch a PUBLISHING row", ok)
            assertEquals(ScheduledPostStatus.PUBLISHING, store.list().single().status)
        }

    @Test
    fun cancel_on_failed_row_succeeds() =
        runTest {
            val store = newStore()
            store.add(samplePost(publishAtSec = 1000))
            store.claimDuePosts(nowSec = 1000)
            store.markFailed("id-1", "boom")

            val ok = store.cancel("id-1")

            assertTrue(ok)
            assertEquals(ScheduledPostStatus.CANCELLED, store.list().single().status)
        }

    @Test
    fun markSent_only_transitions_from_publishing() =
        runTest {
            val store = newStore()
            store.add(samplePost()) // stays PENDING, never claimed

            val ok = store.markSent("id-1")

            assertFalse("markSent must only transition a PUBLISHING row", ok)
            assertEquals(ScheduledPostStatus.PENDING, store.list().single().status)
        }

    @Test
    fun markFailed_only_transitions_from_publishing() =
        runTest {
            val store = newStore()
            store.add(samplePost()) // PENDING

            val ok = store.markFailed("id-1", "boom")

            assertFalse("markFailed must only transition a PUBLISHING row", ok)
            assertEquals(ScheduledPostStatus.PENDING, store.list().single().status)
        }

    @Test
    fun publishNow_on_publishing_row_is_noop_and_returns_false() =
        runTest {
            val store = newStore()
            store.add(samplePost(publishAtSec = 1000))
            store.claimDuePosts(nowSec = 1000) // -> PUBLISHING

            val ok = store.publishNow("id-1", nowSec = 2000)

            assertFalse("publishNow must not touch a PUBLISHING row", ok)
            assertEquals(ScheduledPostStatus.PUBLISHING, store.list().single().status)
        }
}
