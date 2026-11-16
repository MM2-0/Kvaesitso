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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Real answers from a live vespa-relay (`search.brainstorm.world`), recorded once by
 * `tools/search-parity/fetch_fixtures.py` and read from disk here.
 *
 * Recorded rather than fetched so `./gradlew test` stays offline and deterministic — a suite that
 * reaches the network fails on a train, and this one runs in the pre-push hook.
 */
data class ParityCase(
    val name: String,
    val terms: String,
    val search: String,
    val filter: Filter,
    val events: List<Event>,
)

object SearchParityFixture {
    private val json = Json { ignoreUnknownKeys = true }

    val cases: List<ParityCase> by lazy { load() }

    /**
     * Read from the repository rather than a module's resources: the same corpus backs the
     * engine-level tests here and the `LocalCache` tests in `:amethyst`, and copying 60-odd real
     * events into two modules would guarantee they drift apart.
     */
    fun load(): List<ParityCase> {
        val file = resolveFixture()
        val root = json.parseToJsonElement(file.readText()).jsonObject
        return root["cases"]!!.jsonArray.map { it.jsonObject.toCase() }
    }

    /** Gradle runs a module's tests from its own directory, so try there and then the repo root. */
    private fun resolveFixture(): File =
        listOf(File("../tools/search-parity/fixture.json"), File("tools/search-parity/fixture.json"))
            .firstOrNull { it.isFile }
            ?: error("tools/search-parity/fixture.json is missing; regenerate with tools/search-parity/fetch_fixtures.py")

    private fun JsonObject.toCase(): ParityCase {
        val flags = this["flags"]!!.jsonArray.map { it.jsonPrimitive.content }
        return ParityCase(
            name = this["name"]!!.jsonPrimitive.content,
            terms = this["terms"]!!.jsonPrimitive.content,
            search = this["search"]!!.jsonPrimitive.content,
            filter = flags.toFilter(),
            // Through the real parser, so the corpus exercises the same code the app runs.
            events = this["events"]!!.jsonArray.map { Event.fromJson(it.toString()) },
        )
    }

    /**
     * The amy flags a case was fetched with, back as the NIP-01 filter they stood for. The fixture
     * stores the fields rather than a pre-built filter so the reconstruction is visible here and a
     * silently-wrong rebuild cannot make the parity assertions vacuous.
     */
    private fun List<String>.toFilter(): Filter {
        var kinds: List<Int>? = null
        var tags: MutableMap<String, List<String>>? = null
        var since: Long? = null
        var until: Long? = null
        var i = 0
        while (i < size) {
            val value = getOrNull(i + 1)
            when (this[i]) {
                "--kind" -> kinds = value?.split(",")?.map(String::toInt)
                "--since" -> since = value?.toLong()
                "--until" -> until = value?.toLong()
                "--tag" -> {
                    val (name, v) = value!!.split("=", limit = 2)
                    tags = (tags ?: mutableMapOf()).apply { put(name, listOf(v)) }
                }
            }
            i += 2
        }
        return Filter(kinds = kinds, tags = tags, since = since, until = until)
    }
}
