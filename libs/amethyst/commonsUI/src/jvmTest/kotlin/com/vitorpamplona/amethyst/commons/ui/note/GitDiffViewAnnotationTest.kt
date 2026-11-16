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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip34Git.patch.UnifiedDiffParser
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review comments under a diff line are laid out at the card's width, not the code's: the code
 * scrolls sideways, a comment wraps. Rendered offscreen at density 1, so dp == px.
 */
class GitDiffViewAnnotationTest {
    private var scene: ImageComposeScene? = null

    @AfterTest
    fun close() {
        scene?.close()
    }

    private val longLine = "val x = " + "veryLongIdentifier".repeat(40)
    private val patch =
        """
        diff --git a/a.kt b/a.kt
        --- a/a.kt
        +++ b/a.kt
        @@ -1,3 +1,4 @@
         fun main() {
        -    old()
        +    $longLine
        +    added()
         }
        """.trimIndent() + "\n"

    @Test
    fun aCommentWrapsToTheCardWhileTheCodeScrolls() {
        val parsed = UnifiedDiffParser.parse(patch)
        var commentWidth = -1
        var commentsDrawn = 0
        val width = 400
        scene =
            ImageComposeScene(width = width, height = 900, density = Density(1f)) {
                GitDiffView(
                    parsed,
                    Modifier.width(width.dp),
                    onLineClick = { _, _ -> },
                    lineAnnotations = { _, line ->
                        commentsDrawn++
                        Text(
                            "A review comment long enough to need several lines at this width: " + "words ".repeat(40),
                            Modifier.onGloballyPositioned { commentWidth = it.size.width },
                        )
                    },
                    // The long added line carries the comment.
                    hasAnnotations = { _, line -> line.content.startsWith("    val x") },
                )
            }
        val image = scene!!.render()
        System.getenv("DIFF_SHOT")?.let { File(it).writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes) }

        assertEquals(1, commentsDrawn, "only the commented line draws its comments")
        assertTrue(commentWidth in 1..width, "the comment is as wide as the card ($commentWidth px), not the scrolled code")
    }

    @Test
    fun aShortChangedLineIsColouredAcrossTheCard() {
        val parsed = UnifiedDiffParser.parse(patch)
        val width = 400
        scene = ImageComposeScene(width = width, height = 600, density = Density(1f)) { GitDiffView(parsed, Modifier.width(width.dp)) }
        val image = scene!!.render()
        val bitmap = Bitmap.makeFromImage(image)
        // The short removed line `old()` is the third row of the card; its red background must
        // reach the card's right edge, not stop after the text.
        val y = (0 until bitmap.height).first { y -> isRedTint(bitmap.getColor(width / 2, y)) || isRedTint(bitmap.getColor(width - 6, y)) }
        assertTrue(isRedTint(bitmap.getColor(width - 6, y)), "the removed row's colour reaches the card's edge")
    }

    private fun isRedTint(argb: Int): Boolean {
        val r = (argb shr 16) and 0xff
        val g = (argb shr 8) and 0xff
        val b = argb and 0xff
        return r > g + 12 && r > b + 12
    }
}
