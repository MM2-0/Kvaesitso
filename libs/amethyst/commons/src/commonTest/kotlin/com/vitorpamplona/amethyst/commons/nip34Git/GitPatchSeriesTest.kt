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
package com.vitorpamplona.amethyst.commons.nip34Git

import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPatchSeries
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class GitPatchSeriesTest {
    private val sig = "0".repeat(128)
    private val author = "1".repeat(64)
    private val root = patch('a', 100, arrayOf("t", "root"))

    private fun patch(
        id: Char,
        createdAt: Long,
        vararg tags: Array<String>,
        pubKey: String = author,
    ) = GitPatchEvent(id.toString().repeat(64), pubKey, createdAt, arrayOf(*tags), "", sig)

    private fun rootTag() = arrayOf("e", root.id, "", "root")

    private fun replyTag(to: GitPatchEvent) = arrayOf("e", to.id, "", "reply")

    @Test
    fun followsTheReplyChainNotTheClock() {
        val second = patch('b', 300, rootTag(), replyTag(root))
        val third = patch('c', 200, rootTag(), replyTag(second))
        assertEquals(listOf(root, second, third), GitPatchSeries.chain(root, listOf(third, second)))
    }

    @Test
    fun aSeriesRootedOnlyAtItsFirstPatchIsInTheOrderItWasSent() {
        val second = patch('b', 200, rootTag())
        val third = patch('c', 300, rootTag())
        assertEquals(listOf(root, second, third), GitPatchSeries.chain(root, listOf(third, second)))
    }

    @Test
    fun aGapEndsTheSeries() {
        val third = patch('c', 300, rootTag(), replyTag(patch('b', 200)))
        val fourth = patch('d', 400, rootTag(), replyTag(third))
        assertEquals(listOf(root), GitPatchSeries.chain(root, listOf(third, fourth)))
    }
}
