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
package com.vitorpamplona.amethyst.commons.drss

import com.vitorpamplona.quartz.drss.show.DrssShowEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DrssShowSettingsTest {
    @Test
    fun readsTheFieldsARealShowCarries() {
        // drss.io's "Daily Briefing", 2026-10-28, with its prompts cut short.
        val settings =
            DrssShowSettings.parse(
                """
                {"name":"Daily Briefing","slug":"egregore","voice":"gg","language":"en-us","first_run_window":"24h",
                 "episode_retention":"120h","cover_style":"mead-berkey","tags":["podcast","nostr","river","briefing"],
                 "products":["script","summary","article","cover","audio"],
                 "schedule":[{"part":"morning","time":"04:00","zone":"America/Chicago"},{"part":"evening","time":"16:00","zone":"America/Chicago"}],
                 "prompts":{"compose":"# The Composition Contract"}}
                """.trimIndent(),
            )

        assertEquals("Daily Briefing", settings.name)
        assertEquals("en-us", settings.language)
        assertEquals("gg", settings.voice)
        assertEquals(listOf("podcast", "nostr", "river", "briefing"), settings.tags)
        assertEquals(listOf("script", "summary", "article", "cover", "audio"), settings.products)
        assertEquals(listOf(DrssShowSlot("04:00", "America/Chicago", "morning"), DrssShowSlot("16:00", "America/Chicago", "evening")), settings.schedule)
        assertEquals(120, settings.retentionHours)
        assertNull(settings.picture)
    }

    @Test
    fun keepsOnlyAWebPicture() {
        assertEquals("https://example.com/a.gif", DrssShowSettings.parse("""{"picture":"https://example.com/a.gif"}""").picture)
        assertNull(DrssShowSettings.parse("""{"picture":"javascript:alert(1)"}""").picture)
    }

    @Test
    fun anythingElseIsEmptySettings() {
        listOf("", "not json", "[1,2]", "\"text\"", """{"name":5,"tags":"podcast","schedule":{"time":"04:00"}}""").forEach {
            val settings = DrssShowSettings.parse(it)
            assertNull(settings.name, it)
            assertTrue(settings.tags.isEmpty(), it)
            assertTrue(settings.schedule.isEmpty(), it)
        }
    }

    @Test
    fun readsEachShowEventOnce() {
        fun show(id: Char) = DrssShowEvent(id.toString().repeat(64), "b".repeat(64), 1L, emptyArray(), """{"name":"drift"}""", "c".repeat(128))

        val first = DrssShowSettings.of(show('1'))
        assertEquals("drift", first.name)
        assertSame(first, DrssShowSettings.of(show('1')), "the same event is not parsed again")
        assertNotSame(first, DrssShowSettings.of(show('2')), "a new version is")
    }

    @Test
    fun editingKeepsTheKeysAmethystDoesNotKnow() {
        val content =
            """{"name":"drift","slug":"drift","prompts":{"compose":"# Contract"},"schedule":[{"part":"morning","time":"05:00","zone":"America/Chicago","days":["mon"]}],"episode_retention":"336h"}"""
        val edited =
            DrssShowSettings.edit(
                content = content,
                name = "Drift",
                language = "en-us",
                voice = " ",
                tags = listOf("music", " "),
                products = listOf("audio"),
                schedule = listOf(DrssShowSlot("06:00", "America/Chicago", "morning"), DrssShowSlot("18:00", null, null)),
                retentionHours = 120,
            )
        val settings = DrssShowSettings.parse(edited)
        assertEquals("Drift", settings.name)
        assertEquals("en-us", settings.language)
        assertNull(settings.voice)
        assertEquals(listOf("music"), settings.tags)
        assertEquals(listOf(DrssShowSlot("06:00", "America/Chicago", "morning"), DrssShowSlot("18:00", null, null)), settings.schedule)
        assertEquals(120, settings.retentionHours)
        assertTrue(edited.contains("\"prompts\":{\"compose\":\"# Contract\"}"), edited)
        assertTrue(edited.contains("\"days\":[\"mon\"]"), "a slot keeps its other keys")
        assertTrue(edited.contains("\"slug\":\"drift\""))
    }
}
