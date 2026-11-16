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

import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.QuorumRead
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleEdit
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleRelays
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.SyncedSchedule
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.planScheduleEdit
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverParams
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskClaims
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Observer's settings as the schedule's `param` tags: the model and key the print view picks
 * written into the event (a settings-only one when there is no schedule), read back with the
 * plan's fallbacks, the per-install model migrated once, and every unknown param kept.
 */
class FrontPageSettingsTest {
    private val now = 1_794_394_800L
    private val signer = NostrSignerInternal(KeyPair())
    private val ny = "America/New_York"
    private val task = ObserverSchedule.new(ny, 0).withEnabled(true, 0).withCron("0 6 * * *", ny, 0)
    private val on = FrontPageSchedule.daily(true, 7, 30)

    private val openRouter = AgentKeyTag("sk-or-v1-aaaa1111", "https://openrouter.ai/api/v1")
    private val openRouterTwo = AgentKeyTag("sk-or-v1-bbbb2222", "https://openrouter.ai/api/v1")
    private val anthropic = AgentKeyTag("sk-ant-cccc3333", "https://api.anthropic.com/v1")

    /** One write relay in memory, holding [initial] (or nothing). */
    private inner class Relays(
        initial: ScheduledTask?,
    ) : ScheduleRelays {
        var newest: ScheduledTaskEvent? = null
        var writes = 0

        init {
            if (initial != null) runBlocking { put(initial) }
        }

        suspend fun put(task: ScheduledTask) {
            newest = signer.sign(ObserverSchedule.build(task, signer, newest, now))
            writes++
        }

        suspend fun task(): ScheduledTask? = newest?.task(signer)

        override suspend fun readQuorum() = QuorumRead(newest, newest?.let { taskOf(it) }, true, setOf(NormalizedRelayUrl("wss://write.example/")))

        override suspend fun taskOf(event: ScheduledTaskEvent): ScheduledTask? = event.task(signer)

        override suspend fun publish(
            previous: ScheduledTaskEvent?,
            task: ScheduledTask,
            relays: Set<NormalizedRelayUrl>,
        ): Pair<ScheduledTaskEvent, Boolean> {
            put(task)
            return newest!! to true
        }

        override fun watch(
            relays: Set<NormalizedRelayUrl>,
            onSubscribed: (NormalizedRelayUrl) -> Unit,
        ): Flow<List<ScheduledTaskEvent>> = emptyFlow()

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

    private suspend fun readable(task: ScheduledTask): SyncedSchedule.Readable {
        val event: ScheduledTaskEvent = signer.sign(ObserverSchedule.build(task, signer, previous = null, now = now))
        return SyncedSchedule.Readable(event, task)
    }

    @Test
    fun theModelAndKeyPickedAreWrittenAsParams() =
        runTest {
            val relays = Relays(task)
            assertIs<ScheduleEdit.Saved>(editMigrating(relays, now, null) { withObserverSettings(it, "anthropic/claude-sonnet-4.5", openRouterTwo) })
            val written = relays.task()!!
            assertEquals("anthropic/claude-sonnet-4.5", ObserverParams.model(written))
            // The key's endpoint and its last 4 characters, never the key.
            assertEquals(listOf("https://openrouter.ai/api/v1", "2222"), written.paramValues(ObserverParams.KEY))
            assertFalse(relays.newest!!.content.contains("sk-or-v1"))
            assertFalse(written.params.any { p -> p.values.any { it.contains("bbbb") } })
            // The schedule itself is as it was.
            assertEquals("0 6 * * *", written.cron)
            // Picking it again writes nothing.
            assertEquals(ScheduleEdit.Unchanged, editMigrating(relays, now + 1, null) { withObserverSettings(it, "anthropic/claude-sonnet-4.5", openRouterTwo) })
            assertEquals(2, relays.writes)
        }

    @Test
    fun withNoScheduleTheSettingsGoInAnEventThatNeverRuns() =
        runTest {
            val relays = Relays(null)
            assertIs<ScheduleEdit.Saved>(editMigrating(relays, now, null) { withObserverSettings(it, "openai/gpt-5", anthropic) })
            val settings = relays.task()!!
            assertTrue(isSettingsOnly(settings))
            assertNull(settings.cron)
            assertEquals(ObserverSchedule.TASK, settings.task)
            assertEquals(ObserverSchedule.D_TAG, relays.newest!!.dTag())
            assertEquals("openai/gpt-5", ObserverParams.model(settings))
            // Never run: no job here, no claim, and the screen shows the schedule off, not unreadable.
            assertFalse(runsHereAtAll(settings))
            assertFalse(devicesPrint(settings))
            assertFalse(scheduleForThisDevice(readable(settings), on, canPrintHere = true)!!.enabled)
            val view = viewOf(readable(settings), on, canPrintHere = true, now = now, deviceId = ny)
            assertEquals(ScheduleState.NOT_SET_UP, view.state)
            assertTrue(view.settingsOnly)
            assertFalse(view.enabled)

            // Turning the schedule on keeps the settings and adds the time.
            val seed = ObserverSchedule.new(ny, now).withEnabled(true, now)
            assertIs<ScheduleEdit.Saved>(editMigrating(relays, now + 1, null) { current -> current?.let { withScheduleOn(it, seed, on = true, now + 1) } })
            val scheduled = relays.task()!!
            assertFalse(isSettingsOnly(scheduled))
            assertTrue(scheduled.enabled)
            assertEquals(ScheduledTask.DEFAULT_CRON, scheduled.cron)
            assertEquals("openai/gpt-5", ObserverParams.model(scheduled))
            assertTrue(devicesPrint(scheduled))
            // Turning a settings-only event "off" changes nothing.
            assertNull(withScheduleOn(settings, seed, on = false, now))
        }

    @Test
    fun theSettingsAreReadBackWithThePlansFallbacks() {
        val keys = listOf(openRouter, openRouterTwo, anthropic)
        // No `key`: NIP-FA's default, the first usable key.
        assertEquals(openRouter, scheduledKey(task, keys))
        assertEquals(openRouter, scheduledKey(null, keys))
        // The URL and suffix pick one of two keys at one endpoint.
        assertEquals(openRouterTwo, scheduledKey(ObserverParams.withKey(task, openRouterTwo), keys))
        // A suffix that no longer matches: the first key at that URL.
        assertEquals(openRouter, scheduledKey(task.withParam(ObserverParams.KEY, "https://openrouter.ai/api/v1/", "zzzz"), keys))
        // An endpoint the reader no longer has a key for: the default.
        assertEquals(openRouter, scheduledKey(task.withParam(ObserverParams.KEY, "https://example.com/v1", "3333"), keys))
        assertNull(scheduledKey(task, emptyList()))
        // No `model`: the app's default for the endpoint (null here); a blank one is none.
        assertNull(ObserverParams.model(task))
        assertNull(ObserverParams.model(task.withParam(ObserverParams.MODEL, " ")))
    }

    @Test
    fun thisInstallsRememberedModelMovesInOnItsFirstEditUnlessTheEventNamesOne() =
        runTest {
            assertEquals("m/remembered", ObserverParams.model(withMigratedModel(task, "m/remembered")))
            assertEquals("m/shared", ObserverParams.model(withMigratedModel(ObserverParams.withModel(task, "m/shared"), "m/remembered")))
            assertEquals(task, withMigratedModel(task, null))
            assertEquals(task, withMigratedModel(task, ""))

            // Folded into the edit this install makes, whatever it changes.
            val relays = Relays(task)
            assertIs<ScheduleEdit.Saved>(editMigrating(relays, now, "m/remembered") { it?.withEnabled(false, now) })
            val migrated = relays.task()!!
            assertTrue(migrated.paused)
            assertEquals("m/remembered", ObserverParams.model(migrated))
            // An edit that changes nothing writes nothing, migration or not.
            val untouched = Relays(task)
            assertEquals(ScheduleEdit.Unchanged, editMigrating(untouched, now, "m/remembered") { null })
            assertEquals(1, untouched.writes)
            // Another device's model since then is kept.
            val shared = Relays(ObserverParams.withModel(task, "m/shared"))
            editMigrating(shared, now, "m/remembered") { it?.withEnabled(false, now) }
            assertEquals("m/shared", ObserverParams.model(shared.task()!!))
        }

    @Test
    fun theMemoryRemembersThatThisInstallMigrated() =
        runTest {
            val memory =
                object : FrontPageModelMemory {
                    override suspend fun get(url: String): String? = null

                    override suspend fun set(
                        url: String,
                        model: String,
                    ) {}
                }
            // A memory that keeps no flag migrates (it never says it did).
            assertFalse(memory.isMigrated(signer.pubKey))
        }

    @Test
    fun everyUnknownParamIsKeptAndStopsTheScheduleFromRunningHere() =
        runTest {
            val withSize = task.withParam("size", "broadsheet").withAddedParam("extra", listOf("1"))
            val relays = Relays(withSize)
            editMigrating(relays, now, "m/remembered") { withObserverSettings(it, "m/picked", anthropic) }
            val written = relays.task()!!
            assertEquals(listOf("broadsheet"), written.paramValues("size"))
            assertEquals(listOf("1"), written.paramValues("extra"))
            assertEquals("m/picked", ObserverParams.model(written))
            assertEquals(listOf("size", "extra"), ObserverParams.unknownParams(written))
            // Listed and editable, never run on schedule here (NIP-FB, `param`).
            assertFalse(devicesPrint(written))
            assertFalse(scheduleForThisDevice(readable(written), on, canPrintHere = true)!!.enabled)
            val view = viewOf(readable(written), on, true, now = now, deviceId = ny)
            assertEquals(ScheduleState.READY, view.state)
            assertEquals(listOf("size", "extra"), view.unknownParams)
            // The runner choice keeps them too.
            val runner = NostrSignerInternal(KeyPair()).pubKey
            assertEquals(written.params, written.withRunner(runner, 0).params)
            assertNotNull(ScheduledTaskClaims.winner(listOfNotNull(relays.newest)))
        }
}
