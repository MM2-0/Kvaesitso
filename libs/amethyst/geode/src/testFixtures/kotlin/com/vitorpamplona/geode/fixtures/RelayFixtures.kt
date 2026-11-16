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
package com.vitorpamplona.geode.fixtures

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import java.util.zip.GZIPInputStream

/**
 * Loaders for test event corpora bundled in `quartz/src/commonTest/resources/`.
 * Lookup order:
 *   1. The `TEST_RESOURCES_ROOT` environment variable (set by quartz's Gradle
 *      config to the absolute path of `commonTest/resources`).
 *   2. The classpath, for callers that copy fixtures into their own
 *      `src/test/resources`.
 */
object RelayFixtures {
    /** Reads a fixture file as a UTF-8 string. */
    fun loadString(name: String): String {
        val envRoot = System.getenv("TEST_RESOURCES_ROOT")
        if (envRoot != null) {
            val file = java.io.File(envRoot, name)
            if (file.exists()) return file.readText()
        }
        val cp = RelayFixtures::class.java.classLoader?.getResourceAsStream(name)
        if (cp != null) return cp.bufferedReader().use { it.readText() }
        throw IllegalArgumentException(
            "Fixture not found: $name. Set TEST_RESOURCES_ROOT or place on classpath.",
        )
    }

    /** Reads a gzipped fixture file as a UTF-8 string. */
    fun loadGzipString(name: String): String {
        val envRoot = System.getenv("TEST_RESOURCES_ROOT")
        if (envRoot != null) {
            val file = java.io.File(envRoot, name)
            if (file.exists()) {
                return GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }
            }
        }
        val cp = RelayFixtures::class.java.classLoader?.getResourceAsStream(name)
        if (cp != null) return GZIPInputStream(cp).bufferedReader().use { it.readText() }
        throw IllegalArgumentException(
            "Fixture not found: $name. Set TEST_RESOURCES_ROOT or place on classpath.",
        )
    }

    /** Loads `nostr_vitor_short.json` — the small handcrafted Vitor corpus. */
    fun vitorShort(): List<Event> = OptimizedJsonMapper.fromJsonToEventList(loadString("nostr_vitor_short.json"))

    /** Loads `nostr_vitor_startup_data.json.gz` — the larger Vitor startup corpus. */
    fun vitorStartup(): List<Event> = OptimizedJsonMapper.fromJsonToEventList(loadGzipString("nostr_vitor_startup_data.json.gz"))
}
