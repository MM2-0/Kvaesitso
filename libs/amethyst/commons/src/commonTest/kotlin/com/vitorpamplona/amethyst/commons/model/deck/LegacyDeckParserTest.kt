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
package com.vitorpamplona.amethyst.commons.model.deck

import com.vitorpamplona.amethyst.commons.model.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LegacyDeckParserTest {
    private val me = "f".repeat(64)

    @Test
    fun workspacesBecomeWorkspacesAndUnknownColumnsAreReported() {
        val legacy =
            """
            {"activeIndex":1,"workspaces":[
              {"id":"a","name":"Social","iconName":"Groups","layoutMode":"DECK","columns":[
                {"typeKey":"home","param":null,"width":420.0},
                {"typeKey":"hashtag","param":"nostr","width":100.0}]},
              {"id":"b","name":"Reading","iconName":"MenuBook","layoutMode":"DECK","columns":[
                {"typeKey":"reads","width":500.0},
                {"typeKey":"custom_feed","param":"feed-1"},
                {"typeKey":"global"}]}
            ]}
            """.trimIndent()

        val layout = LegacyDeckParser.parse(legacy, "", me) { if (it == "feed-1") "🚀 Rockets" else null }!!

        assertEquals(listOf("Social", "Reading"), layout.workspaces.map { it.name })
        assertEquals(1, layout.active)
        assertEquals(listOf(Route.Home, Route.Hashtag("nostr")), layout.workspaces[0].columns.map { it.root })
        // Too narrow a width is raised to the minimum.
        assertEquals(300f, layout.workspaces[0].columns[1].width)
        assertEquals(listOf(Route.Articles), layout.workspaces[1].columns.map { it.root })
        assertEquals(listOf("🚀 Rockets", "Global"), layout.importNotice)
    }

    @Test
    fun olderBuildsKeptOneListOfColumns() {
        val columns = """[{"id":"x","type":"my_profile","width":400.0},{"id":"y","type":"thread","param":"abc","width":400.0}]"""

        val layout = LegacyDeckParser.parse("", columns, me)!!

        assertEquals(listOf(Route.Profile(me), Route.Note("abc")), layout.current.columns.map { it.root })
    }

    @Test
    fun nothingToImport() {
        assertNull(LegacyDeckParser.parse("", "", me))
        assertNull(LegacyDeckParser.parse("not json", "[]", me))
    }
}
