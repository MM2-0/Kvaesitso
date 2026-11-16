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
package com.vitorpamplona.amethyst.commons.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.commons.blurhash.BlurHashDecoder
import com.vitorpamplona.amethyst.commons.blurhash.BlurHashDecoderOld
import com.vitorpamplona.amethyst.commons.blurhash.toBlurhash
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class BlurhashTest {
    val warmHex = "[45#Y7_2^-xt%OSb%4S0-qt0xbotaRInV|M{RlD~M{M_IVIUNHM{M{M{M{RjNGRkoyj]o[t8tPt8"
    val testHex = "|NHL-]~pabocs+jDM{j?of4T9ZR+WBWZbdR-WCog04ITn\$t6t6t6t6oJoLZ}?bIUWBs:M{WCogRjs:s+o#R+WBoft7axWBx]IV%LogM{t5xaWBay%KRjxus.WCNGWWt7j[j]s+R-S5ofjYV@j[ofD%t8RPoJt7t7R*WCof"

    @Test
    fun testAspectRatioWarm() {
        Assert.assertEquals(0.44444445f, BlurHashDecoder.aspectRatio(warmHex)!!, 0.001f)
    }

    @Test
    fun testDecoderWarm() {
        val aspectRatio = BlurHashDecoder.aspectRatio(warmHex) ?: 1.0f

        val bmp1 = BlurHashDecoderOld.decode(warmHex, 100, (100 * (1 / aspectRatio)).roundToInt())
        val bmp2 = BlurHashDecoder.decodeKeepAspectRatio(warmHex, 100)

        assertTrue(bmp1!!.sameAs(bmp2!!.bitmap))
    }

    @Test
    fun testDecoderWarm25Pixels() {
        val aspectRatio = BlurHashDecoder.aspectRatio(warmHex) ?: 1.0f

        val bmp1 = BlurHashDecoderOld.decode(warmHex, 25, (25 * (1 / aspectRatio)).roundToInt())
        val bmp2 = BlurHashDecoder.decodeKeepAspectRatio(warmHex, 25)

        assertTrue(bmp1!!.sameAs(bmp2!!.bitmap))
    }

    @Test
    fun testAspectRatioTest() {
        Assert.assertEquals(1.0f, BlurHashDecoder.aspectRatio(testHex)!!)
    }

    @Test
    fun testDecoderTest() {
        val aspectRatio = BlurHashDecoder.aspectRatio(testHex) ?: 1.0f

        val bmp1 = BlurHashDecoderOld.decode(testHex, 100, (100 * (1 / aspectRatio)).roundToInt())
        val bmp2 = BlurHashDecoder.decodeKeepAspectRatio(testHex, 100)

        assertTrue(bmp1!!.sameAs(bmp2!!.bitmap))
    }

    @Test
    fun testBlack() {
        assertEquals("U00000fQfQfQfQfQfQfQfQfQfQfQfQfQfQfQ", load("/black.png").toBlurhash())
    }

    @Test
    fun test1x1() {
        assertEquals("U~TSUA~q~q~q~q~q~q~q~q~q~q~q~q~q~q~q", load("/1x1.png").toBlurhash())
    }

    @Test
    fun testWhite() {
        assertEquals("U2TSUA~qfQ~q~qj[fQj[fQfQfQfQ~qj[fQj[", load("/white.png").toBlurhash())
    }

    @Test
    fun testLorikeet() {
        val blurhash = load("/lorikeet.jpg").toBlurhash()
        assertEquals("rFDcT@_LNs#:-pyBnhRRE2Z~MyX5VuV@WUo{xta\$9]RQw[OXS}rrWFXSw|OsxaxWNHSwn~M}NGaK%0RkM}w{xto|jGs+Sh-Tj]W?wJnjXSxGs.NI", blurhash)
    }

    private fun load(filename: String): Bitmap =
        javaClass.getResourceAsStream(filename).use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
}
