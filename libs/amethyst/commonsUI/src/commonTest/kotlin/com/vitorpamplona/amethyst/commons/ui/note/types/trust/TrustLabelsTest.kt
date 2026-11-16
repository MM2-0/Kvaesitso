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
package com.vitorpamplona.amethyst.commons.ui.note.types.trust

import com.vitorpamplona.quartz.nip85TrustedAssertions.AssertionMetric
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustLabelsTest {
    private val provider = "600f45510f5f621caea766bc2484a63076b1a252909323737497448b72dc5839"
    private val assistant = "919ba08af7786892093b8264332d817379662a0ba0ba1f5c791ed7b62a7ee2ff"

    @Test
    fun metricWordsReadsTagNamesAsWords() {
        assertEquals("Zaps", metricWords("zap_cnt"))
        assertEquals("Personalized grape rank influence", metricWords("personalizedGrapeRank_influence"))
        assertEquals("Verified follower count", metricWords("verifiedFollowerCount"))
        assertEquals("Pinned tag membership", metricWords("pinned-tag-membership"))
    }

    @Test
    fun formatsWholeNumbersInFullUntilTenThousand() {
        assertEquals("188", formatMetricValue(AssertionMetric("followers", "188", 188.0)))
        assertEquals("9999", formatMetricValue(AssertionMetric("followers", "9999", 9999.0)))
        assertEquals("12K", formatMetricValue(AssertionMetric("followers", "12345", 12345.0)))
        assertEquals("0.53", formatMetricValue(AssertionMetric("influence", "0.53", 0.53)))
    }

    @Test
    fun groupsDesignationRowsByProviderAndRelay() {
        // A Brainstorm activation, as served by wss://search.brainstorm.world.
        val tags =
            arrayOf(
                arrayOf("30392", provider, "wss://scores.brainstorm.world"),
                arrayOf("30393", provider, "wss://scores.brainstorm.world"),
                arrayOf("39998", assistant, "wss://dcosl.brainstorm.world"),
                arrayOf("3038x", assistant, "wss://nip85.brainstorm.world"),
                arrayOf("30382:rank", provider, "wss://scores.brainstorm.world/"),
                arrayOf("30382:rank", provider, "wss://scores.brainstorm.world"),
                arrayOf("client", "Brainstorm"),
                arrayOf("p", provider),
                arrayOf("30382:followers", "not-a-key", "wss://scores.brainstorm.world"),
            )

        val providers = designatedProviders(tags)

        assertEquals(3, providers.size)

        val scores = providers[0]
        assertEquals(provider, scores.pubKey)
        assertEquals("wss://scores.brainstorm.world/", scores.relay?.url)
        assertEquals(listOf("30392", "30393", "30382:rank"), scores.services.map { it.raw })
        assertEquals(30382, scores.services[2].kind)
        assertEquals("rank", scores.services[2].qualifier)
        assertNull(scores.services[0].qualifier)

        assertEquals(listOf("39998"), providers[1].services.map { it.raw })
        // A row whose kind does not parse still shows as written.
        assertNull(providers[2].services.single().kind)
        assertEquals("3038x", providers[2].services.single().raw)
    }

    @Test
    fun keepsRowsWithoutARelayAndMarksPrivateOnes() {
        val public = arrayOf(arrayOf("30392", provider, ""))
        val private = arrayOf(arrayOf("30382:rank", provider, "wss://scores.brainstorm.world"))

        val providers = designatedProviders(public, private)

        assertEquals(2, providers.size)
        assertNull(providers[0].relay)
        assertTrue(!providers[0].isPrivate)
        assertTrue(providers[1].isPrivate)
    }

    @Test
    fun namesTheScoredIdentifierForTheTitle() {
        assertEquals("yabu.me", identifierTitleName("wss://yabu.me/"))
        assertTrue(isRelayIdentifier("wss://yabu.me"))
        assertEquals("example.com/post/1", identifierTitleName("https://example.com/post/1?utm=x#top"))
        assertEquals("example.com", identifierTitleName("https://example.com/"))
        assertEquals("isbn:9780141439518", identifierTitleName(" isbn:9780141439518 "))
        assertEquals("#bitcoin", identifierTitleName("#bitcoin"))
        assertTrue(!isRelayIdentifier("https://example.com"))
        assertEquals(80, identifierTitleName("https://example.com/" + "a".repeat(200)).length)
    }
}
