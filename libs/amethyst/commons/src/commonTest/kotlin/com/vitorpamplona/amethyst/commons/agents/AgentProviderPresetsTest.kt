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
package com.vitorpamplona.amethyst.commons.agents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentProviderPresetsTest {
    @Test
    fun everyPresetButCustomHasAValidUrl() {
        AgentProviderPresets.ALL.filter { it != AgentProviderPresets.CUSTOM }.forEach {
            assertTrue(AgentProviderPresets.isValidUrl(it.url), it.id)
        }
        assertEquals(
            AgentProviderPresets.ALL.size,
            AgentProviderPresets.ALL
                .map { it.id }
                .toSet()
                .size,
        )
    }

    @Test
    fun monogramsNeverCollideAndMainComesFirst() {
        val monograms = AgentProviderPresets.ALL.map { it.monogram }
        assertEquals(monograms.size, monograms.toSet().size)
        assertTrue(AgentProviderPresets.ALL.all { it.monogram.length in 1..2 })
        val groups = AgentProviderPresets.ALL.map { it.group }
        assertEquals(groups.sortedBy { it.ordinal }, groups)
        assertEquals(AgentProviderGroup.MAIN, AgentProviderPresets.OPENROUTER.group)
    }

    @Test
    fun savedKeysGetMonogramsAndLocality() {
        assertEquals("OR", AgentProviderPresets.monogramFor("https://openrouter.ai/api/v1/"))
        assertEquals("MY", AgentProviderPresets.monogramFor("http://my.box:8080/v1"))
        assertTrue(AgentProviderPresets.isLocalUrl("http://localhost:11434/v1"))
        assertTrue(AgentProviderPresets.isLocalUrl("http://127.0.0.1:8080/v1"))
        assertTrue(AgentProviderPresets.isLocalUrl("http://[::1]:8080/v1"))
        assertFalse(AgentProviderPresets.isLocalUrl("https://api.openai.com/v1"))
        assertFalse(AgentProviderPresets.isLocalUrl("http://my.box:8080/v1"))
    }

    @Test
    fun aSavedUrlFindsItsPreset() {
        assertEquals("OpenRouter", AgentProviderPresets.displayName("https://openrouter.ai/api/v1/"))
        assertEquals("Anthropic", AgentProviderPresets.displayName("HTTPS://api.anthropic.com/v1"))
        assertEquals("my.box:8080", AgentProviderPresets.displayName("http://my.box:8080/v1"))
        assertNull(AgentProviderPresets.forUrl("https://example.com/v1"))
    }

    @Test
    fun urlValidation() {
        assertTrue(AgentProviderPresets.isValidUrl("http://localhost:11434/v1"))
        assertFalse(AgentProviderPresets.isValidUrl("openrouter.ai/api/v1"))
        assertFalse(AgentProviderPresets.isValidUrl("ftp://x.com"))
        assertFalse(AgentProviderPresets.isValidUrl("https://"))
        assertFalse(AgentProviderPresets.isValidUrl("https://a b.com"))
        // NIP-FA: a key never goes over http to another machine.
        assertFalse(AgentProviderPresets.isValidUrl("http://api.example.com/v1", key = "sk-1"))
        assertTrue(AgentProviderPresets.isValidUrl("http://192.168.1.5:11434/v1", key = ""))
        assertTrue(AgentProviderPresets.isValidUrl("http://localhost:11434/v1", key = "sk-1"))
        assertFalse(AgentProviderPresets.isValidUrl("http://localhost@evil.example/v1", key = "sk-1"))
    }

    @Test
    fun maskingNeverShowsTheKey() {
        // A real OpenRouter key: its prefix and the last 4.
        val openRouter = "sk-or-v1-" + "0123456789abcdef".repeat(4) + "a1b2"
        assertEquals("sk-or-v1-…a1b2", AgentProviderPresets.maskKey(openRouter))
        assertEquals("sk-ant-…wxyz", AgentProviderPresets.maskKey("sk-ant-api03-0123456789abcdef0123456789abcdefwxyz"))
        // Shorter keys show less: never more than a third of the key.
        assertEquals("…a1b2", AgentProviderPresets.maskKey("sk-or-v1-0123456789abcdefa1b2"))
        assertEquals("…cdef", AgentProviderPresets.maskKey("sk-0123456789abcdef"))
        // No prefix to show: nothing of the secret's start.
        assertEquals("…wxyz", AgentProviderPresets.maskKey("AIzaSyD0123456789abcdefwxyz"))
        assertEquals("…ab", AgentProviderPresets.maskKey("short-ab"))
        assertEquals("…", AgentProviderPresets.maskKey("ab"))
        assertEquals("", AgentProviderPresets.maskKey(""))
    }

    @Test
    fun aMaskNeverShowsMoreThanAThird() {
        for (length in 1..80) {
            val key = "sk-or-v1-" + "x".repeat(length)
            val shown = AgentProviderPresets.maskKey(key).replace("…", "")
            assertTrue(shown.length <= key.length / 3, "$length: ${shown.length} of ${key.length}")
        }
    }
}
