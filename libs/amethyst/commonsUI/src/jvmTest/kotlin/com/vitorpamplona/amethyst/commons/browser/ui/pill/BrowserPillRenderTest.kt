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
package com.vitorpamplona.amethyst.commons.browser.ui.pill

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders every browser-pill prototype offscreen (no device, no display) through `ImageComposeScene`, in the
 * dark and light Amethyst themes side by side, and writes each to `commonsUI/build/browser-pill/<name>.png`.
 *
 * It exists so the redesign can be *looked at* while it's built — see
 * `amethyst/plans/2026-09-26-browser-ui-review.md` — and it fails if a screen throws or draws nothing.
 */
class BrowserPillRenderTest {
    private val outDir = File("build/browser-pill").apply { mkdirs() }

    private fun render(
        name: String,
        widthDp: Int,
        heightDp: Int,
        minColours: Int = 20,
        content: @Composable () -> Unit,
    ) {
        val density = 2f
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        val scene = ImageComposeScene(width = width, height = height, density = Density(density), content = content)
        try {
            // Strings and fonts load asynchronously from compose resources: let a few frames settle.
            var image = scene.render(0)
            repeat(SETTLE_FRAMES) { frame ->
                Thread.sleep(FRAME_MILLIS)
                image = scene.render((frame + 1) * FRAME_MILLIS * 1_000_000L)
            }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("could not encode $name")
            File(outDir, "$name.png").writeBytes(png.bytes)

            val pixels = image.peekPixels() ?: error("no pixels for $name")
            val distinct = HashSet<Int>()
            for (y in 0 until height step 7) for (x in 0 until width step 7) distinct += pixels.getColor(x, y)
            assertTrue(distinct.size > minColours, "$name rendered almost nothing (${distinct.size} colours)")
        } finally {
            scene.close()
        }
    }

    @Test fun expanded() = render("01-expanded", 820, 1000) { BrowserPillExpandedPreview() }

    @Test fun torOutOfScope() = render("02-tor-out-of-scope", 820, 1100) { BrowserPillTorOutOfScopePreview() }

    @Test fun addressEditor() = render("03-address-editor", 820, 700) { BrowserPillAddressEditorPreview() }

    @Test fun napplet() = render("04-napplet", 820, 800) { BrowserPillNappletPreview() }

    // Three small bars on a mostly empty canvas, and deliberately the quietest thing
    // the redesign draws — so it clears the "did anything render" bar by less than the
    // full screens do. It scored 18 when the accent came off; a blank render is 1-3, so
    // 12 still catches the failure this guard is for without demanding a colour the
    // component is not supposed to have.
    @Test fun handles() = render("05-handles", 820, 160, minColours = 12) { PillHandlesPreview() }

    @Test fun find() = render("06-find", 820, 220) { FindInPagePreview() }

    @Test fun console() = render("07-console", 820, 380) { ConsoleSheetPreview() }

    @Test fun permission() = render("08-permission", 820, 700) { PermissionPromptPreview() }

    @Test fun dialog() = render("09-dialog", 820, 560) { PageDialogPreview() }

    @Test fun leave() = render("10-leave", 820, 420) { LeaveSiteDialogPreview() }

    @Test fun pageInfo() = render("11-page-info", 820, 1250) { PageInfoPreview() }

    @Test fun accessInfo() = render("12-access-info", 820, 610) { AccessInfoPreview() }

    private companion object {
        const val SETTLE_FRAMES = 12
        const val FRAME_MILLIS = 60L
    }
}
