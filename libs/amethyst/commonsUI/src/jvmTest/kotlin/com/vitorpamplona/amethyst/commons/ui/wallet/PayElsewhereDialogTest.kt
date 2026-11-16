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
package com.vitorpamplona.amethyst.commons.ui.wallet

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dialog a desktop shows when no wallet app can take a payment. Renders it headlessly (every
 * string must resolve, the QR must draw); set PAY_ELSEWHERE_RENDER_DIR to also write the PNGs.
 */
class PayElsewhereDialogTest {
    private val invoice =
        "lnbc10u1p5test0pp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdqqcqzzsxqyz5vqsp5" +
            "zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygs9qyyssq"

    @Test
    fun copiesTheBarePayment() {
        assertEquals(invoice, paymentFromUri("lightning:$invoice"))
        assertEquals(invoice, paymentFromUri("LIGHTNING:$invoice"))
        assertEquals("lno1offer", paymentFromUri("bitcoin:?lno=lno1offer"))
        assertEquals(invoice, paymentFromUri(invoice))
    }

    @Test
    fun rendersInBothThemes() {
        listOf(false, true).forEach { dark ->
            val scene =
                ImageComposeScene(width = 900, height = 1500, density = Density(2f)) {
                    AmethystPreviewTheme(dark = dark) {
                        PayElsewhereDialog("lightning:$invoice", onDismiss = {})
                    }
                }
            val png =
                try {
                    // The dialog fades in: the first frame is still translucent, so draw a second later.
                    scene.render()
                    scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
                } finally {
                    scene.close()
                }
            System.getenv("PAY_ELSEWHERE_RENDER_DIR")?.let { dir ->
                File(dir, "pay-elsewhere-${if (dark) "dark" else "light"}.png").writeBytes(png)
            }
            val image = ImageIO.read(ByteArrayInputStream(png))
            val colours = mutableSetOf<Int>()
            for (x in 0 until image.width step 3) {
                for (y in 0 until image.height step 3) colours += image.getRGB(x, y)
            }
            assertTrue(colours.size > 20, "the dialog drew ${colours.size} colours")
        }
    }
}
