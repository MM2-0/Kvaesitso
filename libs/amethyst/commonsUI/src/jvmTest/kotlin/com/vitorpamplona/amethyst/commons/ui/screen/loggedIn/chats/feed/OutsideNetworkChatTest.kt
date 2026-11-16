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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OutsideNetworkChatTest {
    private val friend = user('a')
    private val stranger = user('b')
    private val otherStranger = user('c')
    private var next = 0

    private fun user(c: Char) = User(c.toString().repeat(64)) { address -> AddressableNote(address) }

    private fun message(author: User) = Note((next++).toString(16).padStart(64, '0')).also { it.author = author }

    private val outside = setOf(stranger.pubkeyHex, otherStranger.pubkeyHex)

    @Test
    fun consecutiveStrangersCollapseIntoOneRun() {
        // Newest first, as the chat feed holds it.
        val newest = message(stranger)
        val second = message(otherStranger)
        val friendly = message(friend)
        val oldest = message(stranger)
        val notes = listOf(newest, second, friendly, oldest)

        val runs = outsideNetworkRuns(notes, emptySet()) { it in outside }

        val run = assertNotNull(runs.byId[newest.idHex])
        assertSame(run, runs.byId[second.idHex], "two different strangers in a row share one row")
        assertEquals(listOf(newest, second), run.members)
        assertSame(second, run.head, "the row sits at the run's oldest message, its top on screen")
        assertNull(runs.byId[friendly.idHex])
        assertEquals(1, runs.byId[oldest.idHex]?.members?.size)
    }

    @Test
    fun revealedMessagesStayOpen() {
        val a = message(stranger)
        val b = message(stranger)
        val runs = outsideNetworkRuns(listOf(a, b), setOf(a.idHex, b.idHex)) { it in outside }
        assertTrue(runs.byId.isEmpty())
    }

    @Test
    fun rendersInBothThemes() {
        listOf(false, true).forEach { dark ->
            val scene =
                ImageComposeScene(width = 820, height = 200, density = Density(2f)) {
                    AmethystPreviewTheme(dark = dark) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            OutsideNetworkChatRow(count = 3, onReveal = {})
                        }
                    }
                }
            try {
                val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
                System.getenv("WOT_RENDER_DIR")?.let { File(it, "chat-outside-network-${if (dark) "dark" else "light"}.png").writeBytes(png) }
            } finally {
                scene.close()
            }
        }
    }
}
