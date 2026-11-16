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
package com.vitorpamplona.amethyst.commons.walletScrutiny

import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.BuildStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BuildVerdictTest {
    @Test
    fun onlyAReproducedBuildIsTrusted() {
        assertEquals(VerdictTone.TRUSTED, verdictToneOf(BuildStatus.REPRODUCIBLE))
        BuildStatus.entries.filter { it != BuildStatus.REPRODUCIBLE }.forEach {
            assertEquals(false, verdictToneOf(it) == VerdictTone.TRUSTED, "$it")
        }
    }

    @Test
    fun evidenceAgainstTheBinaryIsAFailure() {
        assertEquals(VerdictTone.FAILED, verdictToneOf(BuildStatus.NOT_REPRODUCIBLE))
        assertEquals(VerdictTone.FAILED, verdictToneOf(BuildStatus.NOSOURCE))
        assertEquals(VerdictTone.FAILED, verdictToneOf(BuildStatus.OBFUSCATED))
        assertEquals(VerdictTone.FAILED, verdictToneOf(BuildStatus.SPAM))
    }

    @Test
    fun aBuildThatProvedNothingIsACaution() {
        assertEquals(VerdictTone.CAUTION, verdictToneOf(BuildStatus.FTBFS))
        assertEquals(VerdictTone.CAUTION, verdictToneOf(BuildStatus.NOTAG))
        assertEquals(VerdictTone.CAUTION, verdictToneOf(BuildStatus.WARNING))
    }

    @Test
    fun anUnknownCodeIsUnknown() {
        assertEquals(VerdictTone.UNKNOWN, verdictToneOf(BuildStatus.fromCode("not-a-verdict")))
    }

    @Test
    fun releaseLineSkipsWhatIsMissing() {
        assertEquals("com.example.wallet · 1.2.3 · android", verifiedReleaseLine("com.example.wallet", "1.2.3", "android"))
        assertEquals("com.example.wallet · android", verifiedReleaseLine("com.example.wallet", null, "android"))
        assertNull(verifiedReleaseLine(null, " ", null))
    }
}
