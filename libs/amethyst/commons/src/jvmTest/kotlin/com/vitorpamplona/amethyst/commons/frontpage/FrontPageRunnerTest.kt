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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.Decrypted
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ObserverScheduleState
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.QuorumRead
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleEdit
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleRelays
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.SyncedSchedule
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.planScheduleEdit
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverEdition
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSite
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskClaims
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The reader's Brainstorm Assistant as the schedule's runner (NIP-FB, Runner), chosen in
 * Brainstorm, never here: Amethyst can only hand the schedule back to the reader's devices, which
 * re-encrypts it to the reader. While a runner is named the devices hold no job and never claim,
 * and the status line and missed-run warning read both Observer sites.
 */
class FrontPageRunnerTest {
    // 2026-11-11T11:00:00Z = 06:00 in New York.
    private val six = 1_794_394_800L
    private val reader = NostrSignerInternal(KeyPair())
    private val assistant = NostrSignerInternal(KeyPair())
    private val stranger = NostrSignerInternal(KeyPair())
    private val relay = NormalizedRelayUrl("wss://write.example/")
    private val ny = "America/New_York"

    private val task = ObserverSchedule.new(ny, 0).withEnabled(true, 0).withCron("0 6 * * *", ny, 0)
    private val on = FrontPageSchedule.daily(true, 7, 30)

    /** The reader's write relays, in memory: every read answers, every write lands. */
    private inner class Relays(
        initial: ScheduledTask?,
    ) : ScheduleRelays {
        val events = MutableStateFlow<List<ScheduledTaskEvent>>(emptyList())

        fun newestEvent() = ScheduledTaskClaims.winner(events.value)

        suspend fun put(task: ScheduledTask): ScheduledTaskEvent {
            val event: ScheduledTaskEvent = reader.sign(ObserverSchedule.build(task, reader, newestEvent(), six))
            events.value = events.value + event
            return event
        }

        init {
            initial?.let { runBlocking { put(it) } }
        }

        override suspend fun readQuorum(): QuorumRead {
            val newest = newestEvent()
            return QuorumRead(newest, newest?.let { taskOf(it) }, true, setOf(relay))
        }

        override suspend fun taskOf(event: ScheduledTaskEvent): ScheduledTask? = event.task(reader)

        override suspend fun publish(
            previous: ScheduledTaskEvent?,
            task: ScheduledTask,
            relays: Set<NormalizedRelayUrl>,
        ): Pair<ScheduledTaskEvent, Boolean> = put(task) to true

        override fun watch(
            relays: Set<NormalizedRelayUrl>,
            onSubscribed: (NormalizedRelayUrl) -> Unit,
        ): Flow<List<ScheduledTaskEvent>> = events.onStart { relays.forEach(onSubscribed) }

        override suspend fun edit(
            now: Long,
            resend: Boolean,
            transform: (ScheduledTask?) -> ScheduledTask?,
        ): ScheduleEdit {
            val read = readQuorum()
            val (next, outcome) = planScheduleEdit(read.event != null, read.task, read.quorum, transform)
            if (next != null) put(next)
            return outcome
        }
    }

    private class NoStore : FrontPageScheduleStore {
        override fun flow(account: HexKey): Flow<FrontPageSchedule> = emptyFlow()

        override suspend fun set(
            account: HexKey,
            schedule: FrontPageSchedule,
        ) {}

        override suspend fun enabled(): Map<HexKey, FrontPageSchedule> = emptyMap()
    }

    private class NoInstalls : FrontPageInstallStore {
        override suspend fun id(account: HexKey) = "here"

        override suspend fun forget(account: HexKey) {}
    }

    /** What the platform's scheduler was told, in order. */
    private class Recorder : FrontPageScheduler {
        val calls = mutableListOf<FrontPageSchedule>()
        var runs = 0

        override fun reschedule(
            account: HexKey,
            schedule: FrontPageSchedule,
        ) {
            calls += schedule
        }

        override fun runNow(account: HexKey) {
            runs++
        }
    }

    private suspend fun readable(task: ScheduledTask): SyncedSchedule.Readable {
        val event: ScheduledTaskEvent = reader.sign(ObserverSchedule.build(task, reader, previous = null, now = six))
        return SyncedSchedule.Readable(event, task)
    }

    private fun edition(
        printedAt: Long,
        sha: Char = 'a',
    ) = ObserverEdition(day = ObserverSite.utcDay(printedAt), path = ObserverSite.dayPath(ObserverSite.utcDay(printedAt)), sha256 = sha.toString().repeat(64), printedAt = printedAt)

    @Test
    fun printingFromMyDevicesDropsTheRunnerAndEncryptsToTheReaderAgain() =
        runTest {
            // Brainstorm named the Assistant: the public `p`, the private tags NIP-44 between the reader and it.
            val relays = Relays(task.withParam("model", "anthropic/claude-sonnet-4.5").withRunner(assistant.pubKey, 0))
            val named = relays.newestEvent()!!
            assertEquals(listOf(assistant.pubKey), named.runners())
            assertEquals("anthropic/claude-sonnet-4.5", named.task(assistant)?.param("model")?.value())

            // "Print from my devices instead": no `p`, encrypted to the reader alone again, everything else kept.
            assertIs<ScheduleEdit.Saved>(editMigrating(relays, six + 1, null) { withoutRunner(it, six + 1) })
            val cleared = relays.newestEvent()!!
            assertTrue(cleared.runners().isEmpty())
            assertNull(cleared.task(assistant))
            assertNull(cleared.task(stranger))
            val asRead = assertNotNull(cleared.task(reader))
            assertNull(asRead.runner)
            assertEquals("0 6 * * *", asRead.cron)
            // The schedule restarts at the change (Quartz's withRunner): no device claims a morning
            // that was the Assistant's to print before it was cleared.
            assertEquals(six + 1, asRead.cronSetAt)
            assertNull(ScheduledTaskClaims.occurrence(asRead, assertNotNull(FrontPageClock.cronZone(ny)), six + 60))
            assertEquals("anthropic/claude-sonnet-4.5", asRead.param("model")?.value())
            // Already the reader's devices: nothing to write.
            assertEquals(ScheduleEdit.Unchanged, editMigrating(relays, six + 2, null) { withoutRunner(it, six + 2) })
            assertEquals(2, relays.events.value.size)
        }

    @Test
    fun amethystHasNoWayToNameARunner() {
        // Picking the Assistant is the provider's call, made in Brainstorm: no entry point here takes a key to name.
        for (type in listOf(FrontPageMorning::class.java, FrontPageViewModel::class.java)) {
            assertTrue(type.methods.none { it.name == "setRunner" }, "${type.simpleName}.setRunner")
            val naming = type.methods.filter { !it.isSynthetic && it.name.contains("runner", ignoreCase = true) && it.parameterTypes.any { p -> p == String::class.java } }
            assertTrue(naming.isEmpty(), "${type.simpleName} can name a runner: $naming")
        }
        // The only runner edit: clear-only.
        assertNotNull(FrontPageViewModel::class.java.methods.singleOrNull { it.name == "printFromMyDevices" && it.parameterCount == 0 })
        assertNotNull(FrontPageMorning::class.java.methods.singleOrNull { it.name == "printFromMyDevices" })
        // The rewrite it makes never adds a `p`, whatever the schedule held.
        val conflict = ScheduledTask(task.publicTags + arrayOf(PTag.assemble(assistant.pubKey, null), PTag.assemble(stranger.pubKey, null)), task.privateTags)
        for (named in listOf(task.withRunner(assistant.pubKey, 0), conflict, task.withRunner("not-a-key", 0))) {
            assertTrue(assertNotNull(withoutRunner(named, six)).runners.isEmpty())
        }
        assertNull(withoutRunner(task, six))
        assertNull(withoutRunner(null, six))
    }

    @Test
    fun theReadersDeviceOpensAScheduleEncryptedToTheAssistant() =
        runTest {
            val state = ObserverScheduleState(reader, LocalCache, backgroundScope, EmptyNostrClient(), MutableStateFlow(emptySet()))
            val named = readable(task.withRunner(assistant.pubKey, 0)).event
            val opened = assertIs<Decrypted.Task>(state.decrypt(named))
            assertEquals(assistant.pubKey, opened.task.runner)
            assertEquals("0 6 * * *", opened.task.cron)
        }

    /** A remote signer whose reader says no to every decryption, counting how often they were asked. */
    private class RefusingSigner(
        private val inner: NostrSignerInternal,
    ) : NostrSigner(inner.pubKey) {
        var asked = 0

        override fun isWriteable() = true

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T = inner.sign(createdAt, kind, tags, content)

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip04Encrypt(plaintext, toPublicKey)

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = inner.nip04Decrypt(ciphertext, fromPublicKey)

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip44Encrypt(plaintext, toPublicKey)

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ): String {
            asked++
            throw SignerExceptions.ManuallyUnauthorizedException("The reader said no")
        }

        override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = inner.decryptZapEvent(event)

        override suspend fun deriveKey(nonce: HexKey) = inner.deriveKey(nonce)

        override suspend fun signPsbt(psbtHex: String) = inner.signPsbt(psbtHex)

        override fun hasForegroundSupport() = false
    }

    @Test
    fun aSignerThatRefusesIsAskedOnceNotOncePerPeer() =
        runTest {
            // A conflict: the reader's device would try the reader and both keys in a public `p`.
            val conflict = ScheduledTask(task.publicTags + arrayOf(PTag.assemble(assistant.pubKey, null), PTag.assemble(stranger.pubKey, null)), task.privateTags)
            val event = readable(conflict).event
            assertEquals(3, event.privateTagsPeers(reader.pubKey).size)
            val refusing = RefusingSigner(reader)
            val state = ObserverScheduleState(refusing, LocalCache, backgroundScope, EmptyNostrClient(), MutableStateFlow(emptySet()))
            assertEquals(Decrypted.SignerFailed(retryable = true), state.decrypt(event))
            assertEquals(1, refusing.asked)
            // Not kept: the next open asks again, once.
            assertEquals(Decrypted.SignerFailed(retryable = true), state.decrypt(event))
            assertEquals(2, refusing.asked)
        }

    @Test
    fun theJobGoesWhileARunnerIsNamedAndComesBackWhenItIsCleared() =
        runTest {
            val synced = MutableStateFlow<SyncedSchedule>(readable(task))
            val scheduler = Recorder()
            backgroundScope.launch {
                FrontPageScheduleWatcher.follow(reader.pubKey, synced.map { scheduleForThisDevice(it, on, canPrintHere = true) }, scheduler) { false }
            }
            runCurrent()
            assertTrue(scheduler.calls.last().enabled)

            // The Assistant prints: this device's timer or WorkManager job is cancelled.
            synced.value = readable(task.withRunner(assistant.pubKey, 0))
            runCurrent()
            assertFalse(scheduler.calls.last().enabled)

            // Two runners named (a conflict): nobody prints, this device neither.
            synced.value = readable(ScheduledTask(task.publicTags + arrayOf(PTag.assemble(assistant.pubKey, null), PTag.assemble(stranger.pubKey, null)), task.privateTags))
            runCurrent()
            assertFalse(scheduler.calls.last().enabled)

            // Back to the reader's devices: the job is back, at the schedule's time, as this device's own choice had it.
            synced.value = readable(withoutRunner(task.withRunner(assistant.pubKey, 0), six)!!)
            runCurrent()
            assertEquals(FrontPageSchedule(true, "0 6 * * *", ny), scheduler.calls.last())
            assertEquals(listOf(true, false, true), scheduler.calls.map { it.enabled })
        }

    @Test
    fun aDeviceNeverClaimsWhileARunnerIsNamedOrAParamIsUnknown() =
        runTest {
            val morning = FrontPageMorning(NoStore(), NoInstalls(), FrontPageInstallInfo("Desktop (Linux)", "test"), now = { six + 30 }, pause = {})
            for (blocked in listOf(task.withRunner(assistant.pubKey, 0), task.withParam("size", "large"))) {
                val relays = Relays(blocked)
                val outcome =
                    morning.claimRun(relays, "here", "Desktop (Linux)", alreadyDone = { _, _ -> false }) { _, _ -> fail("printed on schedule") }
                assertEquals(ScheduledOutcome.NOT_RUNNER, outcome)
                // No claim was written.
                assertEquals(1, relays.events.value.size)
                assertFalse(scheduleForThisDevice(readable(blocked), on, canPrintHere = true)!!.enabled)
            }
            // The same schedule without them is claimed and printed here.
            val relays = Relays(task)
            assertEquals(ScheduledOutcome.PRINTED, morning.claimRun(relays, "here", "Desktop (Linux)", alreadyDone = { _, _ -> false }) { _, _ -> ScheduledOutcome.PRINTED })
        }

    @Test
    fun theStatusLineSaysTheAssistantPrintedFromItsSite() =
        runTest {
            val named = readable(task.withRunner(assistant.pubKey, 0))
            val sites = ObserverSites(reader = listOf(edition(six - 86_400, 'b')), runner = listOf(edition(six + 120)))
            val view = viewOf(named, on, canPrintHere = true, now = six + 3_600, deviceId = ny, sites = sites)
            assertEquals(ScheduleState.READY, view.state)
            assertEquals(assistant.pubKey, view.runner)
            assertTrue(view.namesRunner)
            assertFalse(view.runnerConflict)
            assertTrue(view.lastByRunner)
            assertNull(view.lastBy)
            assertEquals(six + 120, view.lastRan)
            assertEquals(3_480L / 60 * 60, view.lastRanAgo)
            assertNull(view.missedSince)

            // A manual print by the reader that morning is newer: the line names no Assistant.
            val manual = viewOf(named, on, true, now = six + 3_600, deviceId = ny, sites = sites.copy(reader = listOf(edition(six + 600, 'c'))))
            assertFalse(manual.lastByRunner)
            assertEquals(six + 600, manual.lastRan)
        }

    @Test
    fun theMissedRunWarningReadsBothSitesWithARunner() =
        runTest {
            val named = readable(task.withRunner(assistant.pubKey, 0))
            val later = six + 4 * 3_600
            // Yesterday's paper by the Assistant, nothing today, more than three hours after six: missed.
            val yesterday = ObserverSites(runner = listOf(edition(six - 86_400 + 60)))
            val missed = viewOf(named, on, true, now = later, deviceId = ny, sites = yesterday)
            assertEquals(six - 86_400 + 60, missed.missedSince)
            assertTrue(missed.lastByRunner)
            // Today's paper on either site: fine.
            assertNull(viewOf(named, on, true, now = later, deviceId = ny, sites = yesterday.copy(runner = yesterday.runner + edition(six + 60, 'd'))).missedSince)
            assertNull(viewOf(named, on, true, now = later, deviceId = ny, sites = yesterday.copy(reader = listOf(edition(six + 300, 'e')))).missedSince)
            // Within the three hours: not yet.
            assertNull(viewOf(named, on, true, now = six + 3_600, deviceId = ny, sites = yesterday).missedSince)
            // Never printed by anyone: the warning has no day to name, so it says the Assistant hasn't printed.
            val never = viewOf(named, on, true, now = later, deviceId = ny)
            assertNull(never.missedSince)
            assertNotNull(never.missedDue)
            // Before the Assistant's site was read, nothing is called missed.
            val unread = viewOf(named, on, true, now = later, deviceId = ny, sites = ObserverSites(runnerRead = false))
            assertNull(unread.missedSince)
            assertNull(unread.missedDue)
            // Without a runner the device warning stays NIP-FB's, from `ran` alone.
            assertNull(viewOf(readable(task), on, true, now = later, deviceId = ny, sites = yesterday).missedDue)
        }

    @Test
    fun aConflictOffersOnlyTheReadersDevices() =
        runTest {
            val both = ScheduledTask(task.publicTags + arrayOf(PTag.assemble(assistant.pubKey, null), PTag.assemble(stranger.pubKey, null)), task.privateTags)
            val view = viewOf(readable(both), on, true, now = six, deviceId = ny)
            assertTrue(view.runnerConflict)
            assertTrue(view.nobodyPrints)
            assertNull(view.runner)
            assertEquals(listOf(assistant.pubKey, stranger.pubKey), view.runners)
            // No runner to name, so no "Printed by" line: only the devices are offered.
            assertNull(printedByOf(view.runner, "Brainstorm", assistant.pubKey))
            // The fix drops every `p`; it never keeps one.
            assertTrue(withoutRunner(both, six)!!.runners.isEmpty())
        }

    @Test
    fun aPThatIsNotAKeyIsTreatedLikeAConflict() =
        runTest {
            val broken = task.withRunner("npub-not-hex", 0)
            val view = viewOf(readable(broken), on, true, now = six, deviceId = ny)
            assertEquals(ScheduleState.READY, view.state)
            assertTrue(view.namesRunner)
            assertFalse(view.runnerConflict)
            assertNull(view.runner)
            assertTrue(view.nobodyPrints)
            assertNull(printedByOf(view.runner, null, assistant.pubKey))
            // The devices don't print it either, until the reader takes it back.
            assertFalse(scheduleForThisDevice(readable(broken), on, canPrintHere = true)!!.enabled)
            assertTrue(scheduleForThisDevice(readable(withoutRunner(broken, six)!!), on, canPrintHere = true)!!.enabled)
        }

    @Test
    fun printedByNamesTheAssistantOrTheRunnersProfile() =
        runTest {
            // The reader's `30382:rank` provider is labelled as their Brainstorm Assistant, whatever its profile says.
            assertEquals(PrintedBy(assistant.pubKey, "Brainstorm", isAssistant = true), printedByOf(assistant.pubKey, "Brainstorm", assistant.pubKey))
            // Another runner: its kind-0 name, or none yet (the screen then shows a short npub).
            assertEquals(PrintedBy(stranger.pubKey, "Bob's bot", isAssistant = false), printedByOf(stranger.pubKey, "Bob's bot", assistant.pubKey))
            assertEquals(PrintedBy(stranger.pubKey, null, isAssistant = false), printedByOf(stranger.pubKey, "  ", assistant.pubKey))
            // No rank provider: never the Assistant.
            assertFalse(printedByOf(assistant.pubKey, null, null)!!.isAssistant)
            // No runner: nothing to label.
            assertNull(printedByOf(null, "Brainstorm", assistant.pubKey))
            // A schedule with no runner names nobody, so nothing is shown or offered.
            val view = viewOf(readable(task), on, true, now = six, deviceId = ny)
            assertFalse(view.namesRunner)
            assertFalse(view.nobodyPrints)
        }
}
