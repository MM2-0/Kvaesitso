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

import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.OBSERVER
import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.event
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FrontPagePressTest {
    private val relay = RelayUrlNormalizer.normalize("wss://search.brainstorm.world")

    /** A search relay that answers by who is asking: the observer, include:spam, or names. */
    private class ScriptedRelay(
        val ranked: (Filter) -> List<Event>,
        val anonymous: (Filter) -> List<Event>,
    ) : INostrClient by EmptyNostrClient() {
        val filters = mutableListOf<Filter>()

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            filters.forEach { (relay, list) ->
                list.forEach { filter ->
                    synchronized(this.filters) { this.filters.add(filter) }
                    val answer = if (filter.search.orEmpty().startsWith("observer:")) ranked(filter) else anonymous(filter)
                    answer.forEach { runBlocking { listener?.onEvent(it, isLive = false, relay = relay, forFilters = null) } }
                }
                listener?.onEose(relay, forFilters = null)
            }
        }

        override fun unsubscribe(subId: String) {}
    }

    private val note = event(1, content = "The relay answered in three seconds flat.")
    private val goodPage = "<!doctype html><html><head><style id=\"house\"></style></head><body><q>The relay answered in three seconds flat.</q></body></html>"
    private val badPage = "<!doctype html><html><body><q>The relay never answered.</q></body></html>"
    private val plan = "{\"lead\": {\"ids\": [\"${note.id}\"], \"angle\": \"The relay held\"}, \"full\": [\"${note.id}\", \"${"f".repeat(64)}\"]}"

    private fun relayWithNotes() =
        ScriptedRelay(
            ranked = { if (it.kinds == listOf(1)) listOf(note) else emptyList() },
            anonymous = { if (it.kinds == listOf(1)) listOf(note, event(2)) else emptyList() },
        )

    /** A writer that answers each plan call from [plans] (or fails with [planFailure]) and each page call from [pages]. */
    private fun press(
        client: INostrClient,
        pages: MutableList<String>,
        calls: MutableList<WriteCall> = mutableListOf(),
        plans: MutableList<String> = mutableListOf(plan),
        planFailure: WriterFailure? = null,
    ) = FrontPagePress(
        client = client,
        relay = relay,
        boundary = JsoupPaperBoundary,
        write = { call, onEvent ->
            calls.add(call)
            if (call.purpose == WriteCall.Purpose.PLAN) {
                planFailure?.let { throw WriterException(it, 500) }
                Written(plans.removeAt(0), Usage(10, 5))
            } else {
                val text = pages.removeAt(0)
                onEvent(WriterEvent.Text(text, text.length))
                Written(text, Usage(100, 50))
            }
        },
        deskIdleMs = 2_000,
        probeIdleMs = 2_000,
        now = { 1_794_268_800L },
    )

    @Test
    fun printsACleanPageFromTheRankedDesks() =
        runBlocking {
            val client = relayWithNotes()
            val steps = mutableListOf<PrintStep>()
            val result = press(client, mutableListOf(goodPage)).print(OBSERVER, "Alice") { steps.add(it) }
            val printed = assertIs<PrintResult.Printed>(result)
            assertEquals(1, printed.rounds)
            assertEquals(1, printed.quotes)
            assertTrue(printed.planned)
            assertEquals(listOf(note), printed.corpus.desks["notes"])
            assertEquals(1, printed.corpus.overlap)
            assertEquals(165L, printed.usage?.total)
            assertTrue(steps.any { it is PrintStep.Pull && it.desksDone == DESKS.size + 1 })
            // Planning shows before writing.
            val planning = steps.indexOfFirst { it is PrintStep.Write && it.phase == WritePhase.PLANNING }
            val writing = steps.indexOfFirst { it is PrintStep.Write && it.phase != WritePhase.PLANNING }
            assertTrue(planning in 0 until writing)
            // Every ranked desk carries the floor; the control and names say include:spam.
            assertTrue(client.filters.filter { it.search!!.startsWith("observer:") && it.search!!.contains("gte") }.size == DESKS.size)
            assertTrue(client.filters.any { it.kinds == listOf(0) && it.search == INCLUDE_SPAM })
        }

    @Test
    fun theWriteCallReadsThePostsInFullAndThePlanAfterTheSamePrefix() =
        runBlocking {
            val calls = mutableListOf<WriteCall>()
            press(relayWithNotes(), mutableListOf(goodPage), calls).print(OBSERVER, "Alice")
            val (planCall, pageCall) = calls
            assertEquals(WriteCall.Purpose.PLAN, planCall.purpose)
            assertEquals(WriteCall.Purpose.PAGE, pageCall.purpose)
            // The system brief and the digest are the same, and first, in both calls.
            assertEquals(planCall.messages[0].text, pageCall.messages[0].text)
            val prefix = assertNotNull(planCall.messages[1].prefix)
            assertEquals(prefix, pageCall.messages[1].prefix)
            assertTrue(prefix.endsWith("</corpus>"))
            assertTrue(planCall.messages[1].content.contains("JSON"))
            val write = pageCall.messages[1].content
            // The one id in the corpus is there in full; the unknown one is not.
            assertTrue(write.contains("<full>"))
            assertTrue(write.contains("## [${note.id}]"))
            assertFalse(write.contains("f".repeat(64)))
            assertTrue(write.indexOf("</full>") < write.indexOf("The relay held"))
            assertTrue(write.endsWith(WRITE_INSTRUCTION))
        }

    @Test
    fun aPlanThatIsNotJsonFallsBackToOnePass() =
        runBlocking {
            val calls = mutableListOf<WriteCall>()
            val log = mutableListOf<PrintLogKind>()
            val result =
                press(relayWithNotes(), mutableListOf(goodPage), calls, plans = mutableListOf("Sure! Here's the page: <html>"))
                    .print(OBSERVER, "Alice", log = recording(log))
            assertFalse(assertIs<PrintResult.Printed>(result).planned)
            assertEquals(WRITE_INSTRUCTION, calls[1].messages[1].content.trim())
            assertTrue(PrintLogKind.PLAN_FAILED in log)
        }

    @Test
    fun aPlanCallThatFailsFallsBackButARefusedKeyDoesNot() =
        runBlocking {
            val calls = mutableListOf<WriteCall>()
            val result = press(relayWithNotes(), mutableListOf(goodPage), calls, planFailure = WriterFailure.HTTP).print(OBSERVER, "Alice")
            assertIs<PrintResult.Printed>(result)
            assertEquals(listOf(WriteCall.Purpose.PLAN, WriteCall.Purpose.PAGE), calls.map { it.purpose })
            val refused = assertFailsWith<WriterException> { press(relayWithNotes(), mutableListOf(goodPage), planFailure = WriterFailure.REJECTED).print(OBSERVER, "Alice") }
            assertEquals(WriterFailure.REJECTED, refused.failure)
        }

    @Test
    fun theHouseStylesheetIsInsertedBeforeTheCheckAndKept() =
        runBlocking {
            val printed = assertIs<PrintResult.Printed>(press(relayWithNotes(), mutableListOf(goodPage)).print(OBSERVER, "Alice"))
            assertTrue(printed.html.contains("Tokens and primitives"))
            assertTrue(printed.html.contains("color-scheme: light"))
        }

    @Test
    fun theLocalDateAndTimeAreGivenAboveTheCorpus() =
        runBlocking {
            val calls = mutableListOf<WriteCall>()
            val clock = EditionClock("America/New_York", CronZone.fixed(-5 * 3600))
            press(relayWithNotes(), mutableListOf(goodPage), calls).print(OBSERVER, "Alice", context = EditionContext(number = 7, clock = clock))
            val prefix = calls[0].messages[1].prefix!!
            assertTrue(prefix.contains("Date: Monday, November 9, 2026"))
            assertTrue(prefix.contains("the 24 hours to 19:00, on the reader's clock (America/New_York, UTC-05:00)"))
            assertTrue(prefix.contains("Edition: Vol. I \u00b7 No. 7"))
            assertTrue(prefix.contains("1 events read, 1 voices, 1 kept in the digest"))
            assertTrue(prefix.indexOf("Date:") < prefix.indexOf("<corpus>"))
        }

    @Test
    fun aRefusedPageGetsUpToThreeRepairRounds() =
        runBlocking {
            val calls = mutableListOf<WriteCall>()
            val result = press(relayWithNotes(), mutableListOf(badPage, badPage, badPage, goodPage), calls).print(OBSERVER, "Alice")
            assertEquals(4, assertIs<PrintResult.Printed>(result).rounds)
            val repair = calls.last().messages
            // Only the latest page and what to fix in it: the earlier rounds are not sent again.
            assertEquals(listOf("system", "user", "assistant", "user"), repair.map { it.role })
            assertTrue(repair.last().content.contains("QUOTE"))
            assertFalse(repair[2].content.contains("Tokens and primitives"))
        }

    @Test
    fun aFourthRefusalShowsNoPage() =
        runBlocking {
            val calls = mutableListOf<WriteCall>()
            val result = press(relayWithNotes(), mutableListOf(badPage, badPage, badPage, badPage), calls).print(OBSERVER, "Alice")
            assertEquals(listOf(Violation.Kind.QUOTE), assertIs<PrintResult.Refused>(result).violations.map { it.kind })
            assertEquals(1 + 1 + MAX_REPAIR_ROUNDS, calls.size)
        }

    @Test
    fun rankingsNotYetProjectedStopBeforeSpending() =
        runBlocking {
            val client = ScriptedRelay(ranked = { emptyList() }, anonymous = { listOf(event(9)) })
            val calls = mutableListOf<WriteCall>()
            val result = press(client, mutableListOf(goodPage), calls).print(OBSERVER, "Alice")
            assertEquals(ProbeVerdict.PROJECTION_PENDING, assertIs<PrintResult.NotReady>(result).verdict)
            assertEquals(0, calls.size)
        }

    private fun recording(into: MutableList<PrintLogKind>) =
        object : PrintLogger {
            override fun add(
                kind: PrintLogKind,
                detail: String?,
                numbers: List<Int>,
            ) {
                into.add(kind)
            }

            override fun reasoning(delta: String) {}
        }
}
