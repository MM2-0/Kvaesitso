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
package com.vitorpamplona.amethyst.navigation

import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the persisted bottom-bar format. [BottomBarEntry] uses stable `@SerialName` discriminators
 * (`builtIn` / `favorite`) so the saved config survives class renames/moves. Configs written before the
 * stable names used the fully-qualified class name as the polymorphic discriminator, so this also pins
 * the migration that recovers them — otherwise upgrading silently resets the user's bottom bar.
 */
class BottomBarEntrySerializationTest {
    private val sample =
        listOf(
            BottomBarEntry.BuiltIn(NavBarItem.HOME),
            BottomBarEntry.Favorite("url:https://example.com"),
            BottomBarEntry.PublicChat("25e5c82273a271cb1a840d0060391a0bf4965cafeb029d5ab55350b418953fbb"),
            BottomBarEntry.RelayGroup("abcd1234", "wss://groups.example.com"),
            BottomBarEntry.RelayServer("wss://groups.example.com"),
            BottomBarEntry.Concord("f".repeat(64), listOf("wss://relay.ditto.pub", "wss://community.example.com")),
            BottomBarEntry.ConcordChannel("f".repeat(64), "a".repeat(32), listOf("wss://relay.ditto.pub")),
        )

    @Test
    fun roundTripsWithStableDiscriminators() {
        val json = JsonMapper.toJson(sample)
        // Stable short names, NOT the fragile fully-qualified class name.
        assertTrue("expected stable discriminators, got: $json", json.contains("\"builtIn\"") && json.contains("\"favorite\""))
        assertTrue(
            "expected group discriminators, got: $json",
            json.contains("\"publicChat\"") &&
                json.contains("\"relayGroup\"") &&
                json.contains("\"relayServer\"") &&
                json.contains("\"concord\"") &&
                json.contains("\"concordChannel\""),
        )
        assertEquals(sample, JsonMapper.fromJson<List<BottomBarEntry>>(json))
    }

    @Test
    fun legacyFullyQualifiedDiscriminatorMigratesInsteadOfResetting() {
        // What an earlier build of this branch persisted: the default polymorphic discriminator is the
        // fully-qualified class name. Under the stable @SerialName this no longer decodes directly...
        val legacy =
            """
            [
              {"type":"com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry.BuiltIn","item":"HOME"},
              {"type":"com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry.Favorite","favoriteId":"url:https://example.com"}
            ]
            """.trimIndent()

        runCatching { JsonMapper.fromJson<List<BottomBarEntry>>(legacy) }
            .onSuccess { error("legacy discriminator unexpectedly decoded directly: $it") }

        // ...but the migration (rewrite FQN -> short name) recovers the exact same config. The legacy
        // format predates the group entries, so the recovered config is just the built-in + favorite.
        val expected =
            listOf(
                BottomBarEntry.BuiltIn(NavBarItem.HOME),
                BottomBarEntry.Favorite("url:https://example.com"),
            )
        val migrated =
            legacy
                .replace("com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry.BuiltIn", "builtIn")
                .replace("com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry.Favorite", "favorite")
        assertEquals(expected, JsonMapper.fromJson<List<BottomBarEntry>>(migrated))
    }
}
