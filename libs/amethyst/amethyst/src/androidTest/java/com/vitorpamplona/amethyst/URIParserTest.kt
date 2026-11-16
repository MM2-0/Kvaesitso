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

import com.vitorpamplona.amethyst.ui.navigation.findParameterValue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI

class URIParserTest {
    @Test
    fun testNEventWithAccount() {
        val test = "nevent1qqsp9wg5r3vkuv5al0459h3h44yxh6r0y7chgvu3pkdxhcj99t3msrgpzdmhxue69uhhwmm59e6hg7r09ehkuef0qgsdhcrqt2w8x9et446j8ge8kgmd2h4ykc6wsrnc4yqnmdu3lr74ktqrqsqqqqqp578kku?account=npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"
        val testUri = URI(test)

        // assertEquals("nostr", testUri.scheme)
        assertEquals(null, testUri.authority)
        assertEquals(null, testUri.host)
        assertEquals(-1, testUri.port)
        assertEquals(null, testUri.userInfo)
        assertEquals("nevent1qqsp9wg5r3vkuv5al0459h3h44yxh6r0y7chgvu3pkdxhcj99t3msrgpzdmhxue69uhhwmm59e6hg7r09ehkuef0qgsdhcrqt2w8x9et446j8ge8kgmd2h4ykc6wsrnc4yqnmdu3lr74ktqrqsqqqqqp578kku", testUri.path)
        assertEquals(null, testUri.fragment)
        assertEquals("account=npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z", testUri.rawQuery)

        assertEquals("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z", testUri.findParameterValue("account"))
    }

    @Test
    fun testNotificationsWithAccount() {
        val test = "notifications?account=npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"
        val testUri = URI(test)

        // assertEquals("nostr", testUri.scheme)
        assertEquals(null, testUri.authority)
        assertEquals(null, testUri.host)
        assertEquals(-1, testUri.port)
        assertEquals(null, testUri.userInfo)
        assertEquals("notifications", testUri.path)
        assertEquals(null, testUri.fragment)
        assertEquals("account=npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z", testUri.rawQuery)

        assertEquals("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z", testUri.findParameterValue("account"))
    }
}
