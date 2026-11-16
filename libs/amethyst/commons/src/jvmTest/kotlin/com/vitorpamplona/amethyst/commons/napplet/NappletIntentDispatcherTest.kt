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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletIntentRequest
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.tags.NappletIntent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** NAP-INTENT's runtime: the catalog of installed handlers, resolution, and delivery once the target is ready. */
class NappletIntentDispatcherTest {
    private val publisher = NostrSignerInternal(KeyPair())
    private val source = NappletIdentity(authorPubKey = "aa".repeat(32), identifier = "feed-builder", manifestKind = NamedNappletEvent.KIND)

    private suspend fun napplet(
        d: String,
        archetypes: List<String> = listOf("feed"),
        intents: List<NappletIntent> = listOf(NappletIntent("napplet:feed/open", listOf("filters"))),
        domains: List<String> = listOf("intent"),
    ): NamedNappletEvent = publisher.sign(NamedNappletEvent.build(d, "ab".repeat(32), "a feed", optional = domains, archetypes = archetypes, intents = intents))

    private fun invoke(
        convention: String = "napplet:feed/open",
        handler: String? = null,
    ) = NappletIntentRequest.Invoke("i1", convention.removePrefix("napplet:").substringBefore('/'), convention.substringAfter('/'), convention, """{"filters":"x"}""", handler, null)

    private fun json(text: String?): JsonObject = Json.parseToJsonElement(text!!).jsonObject

    @Test
    fun onlyInstalledNappletsThatCanReceiveAreHandlers() =
        runTest {
            val good = napplet("reader")
            val noIntent = napplet("silent", domains = emptyList())
            val roleMismatch = napplet("other", archetypes = listOf("profile"))
            val catalog = NappletIntentCatalog(listOf(good, noIntent, roleMismatch).mapNotNull(NappletIntentCatalog::handlerOf))
            assertEquals(listOf("35129:${publisher.pubKey}:reader"), catalog.handlers.map { it.id })
            val availability = catalog.availability("feed")
            assertEquals(
                listOf("filters"),
                availability.candidates
                    .single()
                    .contracts
                    .single()
                    .params,
            )
            assertTrue(availability.candidates.single().isDefault)
        }

    @Test
    fun theFirstInstalledHandlerIsTheDefaultAndAnExplicitOneNeverFallsBack() =
        runTest {
            val first = napplet("first")
            val second = napplet("second")
            val launched = mutableListOf<String>()
            val dispatcher =
                NappletIntentDispatcher<String>(
                    catalog = { NappletIntentCatalog(listOf(first, second).mapNotNull(NappletIntentCatalog::handlerOf)) },
                    launch = { handler -> handler.id.also { launched += it } },
                )

            val result = json(dispatcher.handle("source", source, {}, invoke()))["result"]!!.jsonObject
            assertEquals("true", result["ok"]!!.jsonPrimitive.content)
            assertEquals("35129:${publisher.pubKey}:first", result["handler"]!!.jsonPrimitive.content)

            val named = json(dispatcher.handle("source", source, {}, invoke(handler = "35129:${publisher.pubKey}:second")))["result"]!!.jsonObject
            assertEquals("35129:${publisher.pubKey}:second", named["handler"]!!.jsonPrimitive.content)

            val stranger = json(dispatcher.handle("source", source, {}, invoke(handler = "35129:${"cc".repeat(32)}:evil")))["result"]!!.jsonObject
            assertEquals("no handler", stranger["error"]!!.jsonPrimitive.content)
            assertEquals(2, launched.size, "a refused invocation starts nothing")

            assertEquals("no handler", json(dispatcher.handle("source", source, {}, invoke("napplet:emoji-list/open")))["result"]!!.jsonObject["error"]!!.jsonPrimitive.content)
            val forged = invoke().copy(action = "edit")
            assertEquals("invalid convention", json(dispatcher.handle("source", source, {}, forged))["result"]!!.jsonObject["error"]!!.jsonPrimitive.content)
        }

    @Test
    fun aDeliveryWaitsForTheTargetToBeReady() =
        runTest {
            val handler = napplet("reader")
            val dispatcher =
                NappletIntentDispatcher<String>(
                    catalog = { NappletIntentCatalog(listOfNotNull(NappletIntentCatalog.handlerOf(handler))) },
                    launch = { "target" },
                )
            val toTarget = mutableListOf<String>()
            dispatcher.handle("source", source, {}, invoke())
            assertTrue(toTarget.isEmpty())

            assertNull(dispatcher.handle("target", source, { toTarget += it }, NappletIntentRequest.Ready))
            val delivery = json(toTarget.single())["delivery"]!!.jsonObject
            // The runtime names the source by its catalog id; the payload is passed on as sent.
            assertEquals("35129:${"aa".repeat(32)}:feed-builder", delivery["sender"]!!.jsonPrimitive.content)
            assertEquals("napplet:feed/open", delivery["convention"]!!.jsonPrimitive.content)
            assertEquals("x", delivery["payload"]!!.jsonObject["filters"]!!.jsonPrimitive.content)

            // Once ready, the next one goes straight through.
            dispatcher.handle("source", source, {}, invoke())
            assertEquals(2, toTarget.size)
        }

    @Test
    fun handlersListsEveryArchetype() =
        runTest {
            val feed = napplet("reader")
            val profile = napplet("profile", archetypes = listOf("profile"), intents = listOf(NappletIntent("napplet:profile/open", listOf("pubkey"))))
            val dispatcher = NappletIntentDispatcher<String>(catalog = { NappletIntentCatalog(listOf(feed, profile).mapNotNull(NappletIntentCatalog::handlerOf)) }, launch = { null })
            val handlers = json(dispatcher.handle("s", source, {}, NappletIntentRequest.Handlers("h")))["handlers"]!!.jsonArray
            assertEquals(setOf("feed", "profile"), handlers.map { it.jsonObject["archetype"]!!.jsonPrimitive.content }.toSet())
            // A handler that can't be started is refused before acceptance.
            assertEquals("invoke rejected", json(dispatcher.handle("s", source, {}, invoke()))["result"]!!.jsonObject["error"]!!.jsonPrimitive.content)
        }
}
