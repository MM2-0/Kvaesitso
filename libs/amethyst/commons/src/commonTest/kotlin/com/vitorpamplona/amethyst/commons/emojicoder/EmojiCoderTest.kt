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
package com.vitorpamplona.amethyst.commons.emojicoder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmojiCoderTest {
    companion object {
        val EMOJI_LIST = arrayOf("😀", "😂", "🥰", "😎", "🤔", "👍", "👎", "👏", "😅", "🤝", "🎉", "🎂", "🍕", "🌈", "🌞", "🌙", "🔥", "💯", "🚀", "👀", "💀", "🥹")

        val testStrings =
            arrayOf(
                "Hello, World!",
                "Testing 123",
                "Special chars: !@#$%^&*()",
                "Unicode: 你好，世界",
                " ",
            )

        val HELLO_WORLD = "\uD83D\uDE00\uDB40\uDD38\uDB40\uDD55\uDB40\uDD5C\uDB40\uDD5C\uDB40\uDD5F\uDB40\uDD1C\uDB40\uDD10\uDB40\uDD47\uDB40\uDD5F\uDB40\uDD62\uDB40\uDD5C\uDB40\uDD54\uDB40\uDD11"
        val HELLO_WORLD_WITH_EXTRAS = HELLO_WORLD + "askfasdf"
    }

    @Test
    fun testIsCoded() {
        assertEquals(true, EmojiCoder.isCoded(HELLO_WORLD))
    }

    @Test
    fun testEncode() {
        assertEquals(HELLO_WORLD, EmojiCoder.encode("\uD83D\uDE00", "Hello, World!"))
    }

    @Test
    fun testDecode() {
        assertEquals("Hello, World!", EmojiCoder.decode(HELLO_WORLD))
    }

    @Test
    fun testCrop() {
        assertEquals(HELLO_WORLD, EmojiCoder.cropToFirstMessage(HELLO_WORLD_WITH_EXTRAS))
    }

    @Test
    fun testEncodeDecode() {
        for (emoji in EMOJI_LIST) {
            for (sentence in testStrings) {
                val encoded = EmojiCoder.encode(emoji, sentence)
                val decoded = EmojiCoder.decode(encoded)
                assertEquals(sentence, decoded)
                assertTrue(EmojiCoder.isCoded(encoded), "Failed sentence for emoji $emoji with sentence `$sentence`: `$encoded`")
            }
        }
    }

    @Test
    fun testLinkFromAmethyst() {
        assertEquals("https://cdn.satellite.earth/947e4ab2d3115be565a49cf5db02559f310ca0a6ddfddbd4bd8cbec44995c2e7.webp", EmojiCoder.decode("🚀󠅘󠅤󠅤󠅠󠅣󠄪󠄟󠄟󠅓󠅔󠅞󠄞󠅣󠅑󠅤󠅕󠅜󠅜󠅙󠅤󠅕󠄞󠅕󠅑󠅢󠅤󠅘󠄟󠄩󠄤󠄧󠅕󠄤󠅑󠅒󠄢󠅔󠄣󠄡󠄡󠄥󠅒󠅕󠄥󠄦󠄥󠅑󠄤󠄩󠅓󠅖󠄥󠅔󠅒󠄠󠄢󠄥󠄥󠄩󠅖󠄣󠄡󠄠󠅓󠅑󠄠󠅑󠄦󠅔󠅔󠅖󠅔󠅔󠅒󠅔󠄤󠅒󠅔󠄨󠅓󠅒󠅕󠅓󠄤󠄤󠄩󠄩󠄥󠅓󠄢󠅕󠄧󠄞󠅧󠅕󠅒󠅠"))
    }
}
