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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The setup checklist knows both steps at once, where the gate knows only the first missing. */
class FrontPageSetupTest {
    private val key = AgentKeyTag("sk-or-test", "https://openrouter.ai/api/v1")
    private val rank = ServiceProviderTag(ProviderTypes.rank, "c".repeat(64), RelayUrlNormalizer.normalize("wss://scores.example.com"))
    private val boundary = PaperBoundary { html, _ -> CheckedPage(html, emptyList(), emptyList(), 0) }

    @Test
    fun neitherStepDone() {
        val setup = frontPageSetupOf(null, null)
        assertFalse(setup.hasKey)
        assertFalse(setup.hasLens)
        assertFalse(setup.complete)
        assertEquals(FrontPageGate.NO_KEY, gateOf(null, false, boundary))
    }

    @Test
    fun keyWithoutLensStillShowsTheLensStep() {
        val setup = frontPageSetupOf(key, null)
        assertTrue(setup.hasKey)
        assertEquals("OpenRouter", setup.provider)
        assertFalse(setup.hasLens)
        assertFalse(setup.complete)
    }

    @Test
    fun lensWithoutKeyStillShowsTheLensDone() {
        // The gate stops at NO_KEY; the checklist still knows the lens is set.
        val setup = frontPageSetupOf(null, rank, "Brainstorm")
        assertFalse(setup.hasKey)
        assertTrue(setup.hasLens)
        assertEquals("c".repeat(64), setup.lens)
        assertEquals("Brainstorm", setup.lensName)
        assertFalse(setup.complete)
        assertEquals(FrontPageGate.NO_KEY, gateOf(null, true, boundary))
    }

    @Test
    fun bothDoneIsComplete() {
        val setup = frontPageSetupOf(key, rank)
        assertTrue(setup.complete)
        assertEquals(FrontPageGate.READY, gateOf(key, true, boundary))
    }

    @Test
    fun theProviderIsNamedByItsPresetOrHost() {
        assertEquals("Ollama", frontPageSetupOf(AgentKeyTag("", "http://localhost:11434/v1"), null).provider)
        assertEquals("llm.example.com", frontPageSetupOf(AgentKeyTag("k", "https://llm.example.com/v1"), null).provider)
    }

    @Test
    fun aBlankOrOrphanNameIsDropped() {
        assertNull(frontPageSetupOf(key, rank, " ").lensName)
        assertNull(frontPageSetupOf(key, null, "Brainstorm").lensName)
    }
}
