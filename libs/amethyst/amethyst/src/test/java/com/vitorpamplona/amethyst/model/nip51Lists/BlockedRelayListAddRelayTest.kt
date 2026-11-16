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
package com.vitorpamplona.amethyst.model.nip51Lists

import android.os.Looper
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.blockedRelays.BlockedRelayListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.blockedRelays.BlockedRelayListState
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives the real kind-10006 mutation behind the NOTIFY dialog's "Block Relay" button.
 *
 * The button hands a single relay to [BlockedRelayListState.addRelay], which read-modify-writes the
 * whole list — and [BlockedRelayListEvent.updateRelayList] *replaces* every relay tag with what it
 * is given. So "does blocking work" is really two questions this pins: the new relay lands, and the
 * relays already blocked survive. A regression here silently wipes the user's block list on their
 * next tap, which no amount of UI testing would make obvious.
 *
 * Each case uses a fresh keypair so its kind-10006 lives at its own address in the process-wide
 * [LocalCache].
 */
class BlockedRelayListAddRelayTest {
    /**
     * Handed to every [Fixture] so [tearDown] can cancel it. [BlockedRelayListState] starts an
     * eager `stateIn` and a forever `collect` on whatever scope it is handed, so a scope left alive
     * here outlives the test: its coroutines keep decrypting on [Dispatchers.IO] with no
     * [kotlinx.coroutines.CoroutineExceptionHandler] in context, and anything they throw reaches
     * the JVM-wide handler — where `runTest` reports it against whichever *unrelated* test class
     * happens to start next, as `UncaughtExceptionsBeforeTest`.
     */
    private val stateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val paid = RelayUrlNormalizer.normalize("wss://paid.example.com")
    private val alsoPaid = RelayUrlNormalizer.normalize("wss://other.example.com")

    @Before
    fun setup() {
        // LocalCache.consume refuses the main thread; plain JVM tests have no Looper,
        // where null == null reads as "main". Distinct mocks make it a worker thread.
        mockkStatic(Looper::class)
        every { Looper.myLooper() } returns mockk<Looper>()
        every { Looper.getMainLooper() } returns mockk<Looper>()
    }

    @After
    fun tearDown() {
        // Before unmocking: a collector still running after Looper goes back to its
        // unmocked stub reads `null == null` as "the main thread" and throws.
        stateScope.cancel()
        unmockkStatic(Looper::class)
    }

    private class Fixture(
        scope: CoroutineScope,
    ) {
        val keyPair = KeyPair()
        val signer = NostrSignerInternal(keyPair)
        val decryptionCache = BlockedRelayListDecryptionCache(signer)

        // Stubbed rather than real: AccountSettings reaches for Resources.getSystem() to read the
        // user's spoken languages, which is null outside an Android runtime. The state only asks it
        // for the backup list (none here) and hands it updates to persist.
        val settings = mockk<AccountSettings>(relaxed = true) { every { backupBlockedRelayList } returns null }

        val state =
            BlockedRelayListState(
                signer = signer,
                cache = LocalCache,
                decryptionCache = decryptionCache,
                scope = scope,
                settings = settings,
            )

        /** Mirrors what the publish path does: the signed event goes into the cache it came from. */
        fun land(event: BlockedRelayListEvent) = LocalCache.justConsumeMyOwnEvent(event)

        suspend fun blockedIn(event: BlockedRelayListEvent) = BlockedRelayListDecryptionCache(signer).relays(event)
    }

    @Test
    fun blockingTheFirstRelayCreatesTheList() =
        runTest {
            val f = Fixture(stateScope)

            val event = f.state.addRelay(paid)

            assertEquals(BlockedRelayListEvent.KIND, event.kind)
            assertEquals(setOf(paid), f.blockedIn(event))
        }

    /** The one that matters: a second tap must not throw away the first block. */
    @Test
    fun blockingASecondRelayKeepsTheFirst() =
        runTest {
            val f = Fixture(stateScope)
            f.land(f.state.addRelay(paid))

            val event = f.state.addRelay(alsoPaid)

            assertEquals(setOf(paid, alsoPaid), f.blockedIn(event))
        }

    @Test
    fun blockingAnAlreadyBlockedRelayDoesNotDuplicateIt() =
        runTest {
            val f = Fixture(stateScope)
            f.land(f.state.addRelay(paid))

            val event = f.state.addRelay(paid)

            assertEquals(setOf(paid), f.blockedIn(event))
            assertEquals(1, BlockedRelayListDecryptionCache(f.signer).relays(event).size)
        }

    /**
     * Blocked relays are private tags. Leaking them to public tags would publish which paid relays
     * the user walked away from to anyone who reads their kind-10006.
     */
    @Test
    fun blockedRelaysStayInEncryptedPrivateTags() =
        runTest {
            val f = Fixture(stateScope)
            f.land(f.state.addRelay(paid))

            val event = f.state.addRelay(alsoPaid)

            assertTrue("blocked relays must not appear in public tags", event.publicRelays().isEmpty())
            assertTrue(
                "no relay url may appear in the cleartext tag array",
                event.tags.none { tag -> tag.any { it.contains("paid.example.com") || it.contains("other.example.com") } },
            )
        }
}
