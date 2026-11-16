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
package com.vitorpamplona.amethyst

import android.os.Looper
import com.vitorpamplona.amethyst.commons.model.composer.SplitBuilder
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.SpyK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SplitterTest {
    @SpyK var mySplitBuilder = SplitBuilder<String>()

    @Before
    fun setUp() {
        mockkStatic(Looper::class)
        every { Looper.myLooper() } returns mockk<Looper>()
        every { Looper.getMainLooper() } returns mockk<Looper>()
        MockKAnnotations.init(this)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testSplit() =
        runTest {
            val vitor = mySplitBuilder.addItem("Vitor")

            assertEquals(1f, mySplitBuilder.items[vitor].percentage, 0.01f)
            assertTrue(mySplitBuilder.isEqualSplit())

            val pablo = mySplitBuilder.addItem("Pablo")

            assertEquals(0.5f, mySplitBuilder.items[pablo].percentage, 0.01f)
            assertEquals(0.5f, mySplitBuilder.items[vitor].percentage, 0.01f)
            assertTrue(mySplitBuilder.isEqualSplit())

            val gigi = mySplitBuilder.addItem("Gigi")

            assertEquals(0.33f, mySplitBuilder.items[vitor].percentage, 0.01f)
            assertEquals(0.33f, mySplitBuilder.items[pablo].percentage, 0.01f)
            assertEquals(0.33f, mySplitBuilder.items[gigi].percentage, 0.01f)
            assertTrue(mySplitBuilder.isEqualSplit())

            mySplitBuilder.updatePercentage(vitor, 0.5f)

            assertEquals(0.5f, mySplitBuilder.items[vitor].percentage, 0.01f)
            assertEquals(0.33f, mySplitBuilder.items[pablo].percentage, 0.01f)
            assertEquals(0.16f, mySplitBuilder.items[gigi].percentage, 0.01f)
            assertFalse(mySplitBuilder.isEqualSplit())

            mySplitBuilder.updatePercentage(vitor, 0.95f)

            assertEquals(0.95f, mySplitBuilder.items[vitor].percentage, 0.01f)
            assertEquals(0.05f, mySplitBuilder.items[pablo].percentage, 0.01f)
            assertEquals(0.0f, mySplitBuilder.items[gigi].percentage, 0.01f)
            assertFalse(mySplitBuilder.isEqualSplit())

            mySplitBuilder.updatePercentage(vitor, 0.15f)

            assertEquals(0.15f, mySplitBuilder.items[vitor].percentage, 0.01f)
            assertEquals(0.05f, mySplitBuilder.items[pablo].percentage, 0.01f)
            assertEquals(0.80f, mySplitBuilder.items[gigi].percentage, 0.01f)
            assertFalse(mySplitBuilder.isEqualSplit())

            mySplitBuilder.updatePercentage(pablo, 0.95f)

            assertEquals(0.05f, mySplitBuilder.items[vitor].percentage, 0.01f)
            assertEquals(0.95f, mySplitBuilder.items[pablo].percentage, 0.01f)
            assertEquals(0.00f, mySplitBuilder.items[gigi].percentage, 0.01f)

            mySplitBuilder.updatePercentage(gigi, 1f)

            assertEquals(0.00f, mySplitBuilder.items[vitor].percentage, 0.01f)
            assertEquals(0.00f, mySplitBuilder.items[pablo].percentage, 0.01f)
            assertEquals(1.00f, mySplitBuilder.items[gigi].percentage, 0.01f)

            mySplitBuilder.updatePercentage(vitor, 0.5f)

            assertEquals(0.50f, mySplitBuilder.items[vitor].percentage, 0.01f)
            assertEquals(0.00f, mySplitBuilder.items[pablo].percentage, 0.01f)
            assertEquals(0.50f, mySplitBuilder.items[gigi].percentage, 0.01f)

            mySplitBuilder.updatePercentage(pablo, 0.3f)

            assertEquals(0.50f, mySplitBuilder.items[vitor].percentage, 0.01f)
            assertEquals(0.30f, mySplitBuilder.items[pablo].percentage, 0.01f)
            assertEquals(0.20f, mySplitBuilder.items[gigi].percentage, 0.01f)

            mySplitBuilder.updatePercentage(gigi, 1f)

            assertEquals(0.00f, mySplitBuilder.items[vitor].percentage, 0.01f)
            assertEquals(0.00f, mySplitBuilder.items[pablo].percentage, 0.01f)
            assertEquals(1.00f, mySplitBuilder.items[gigi].percentage, 0.01f)

            mySplitBuilder.updatePercentage(gigi, 0.5f)

            assertEquals(0.00f, mySplitBuilder.items[vitor].percentage, 0.01f)
            assertEquals(0.50f, mySplitBuilder.items[pablo].percentage, 0.01f)
            assertEquals(0.50f, mySplitBuilder.items[gigi].percentage, 0.01f)
        }
}
