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
import kotlin.test.assertNotNull

class DeckLayoutTest {
    @Test
    fun startsWithNotificationsBesideTheMainScreen() {
        assertEquals(listOf<Route>(Route.Notification()), DeckLayout().current.columns.map { it.root })
    }

    @Test
    fun addsAfterTheFocusedColumnAndMovesWithinBounds() {
        var layout = DeckLayout().addColumn(Route.Home)
        val first = layout.current.columns[0].id
        layout = layout.addColumn(Route.Bookmarks, afterId = first)
        assertEquals(listOf(Route.Notification(), Route.Bookmarks, Route.Home), layout.current.columns.map { it.root })

        layout = layout.moveColumn(first, 5)
        assertEquals(
            Route.Notification(),
            layout.current.columns
                .last()
                .root,
        )
        layout = layout.moveColumn(first, -9)
        assertEquals(
            Route.Notification(),
            layout.current.columns
                .first()
                .root,
        )
    }

    @Test
    fun widthsStayInRange() {
        val layout = DeckLayout()
        val id = layout.current.columns[0].id
        assertEquals(
            DeckLayout.MIN_COLUMN_WIDTH,
            layout
                .resizeColumn(id, 10f)
                .current.columns[0]
                .width,
        )
        assertEquals(
            DeckLayout.MAX_COLUMN_WIDTH,
            layout
                .resizeColumn(id, 5000f)
                .current.columns[0]
                .width,
        )
    }

    @Test
    fun workspacesSaveSwitchAndKeepAtLeastOne() {
        var layout = DeckLayout().saveAsWorkspace(" Reading ")
        assertEquals(1, layout.active)
        assertEquals("Reading", layout.current.name)
        // A copy, not the same columns.
        assertEquals(layout.workspaces[0].columns.map { it.root }, layout.current.columns.map { it.root })

        layout = layout.deleteWorkspace(1)
        assertEquals(0, layout.active)
        assertEquals(layout, layout.deleteWorkspace(0))
    }

    @Test
    fun survivesAJsonRoundTrip() {
        val layout = DeckLayout().addColumn(Route.Hashtag("nostr")).addColumn(Route.Profile("a".repeat(64)))
        assertEquals(layout, assertNotNull(DeckLayout.fromJson(layout.toJson())))
    }

    @Test
    fun aColumnThisBuildCannotReadIsDroppedAlone() {
        val layout = DeckLayout().addColumn(Route.Hashtag("nostr")).saveAsWorkspace("Second")
        val json = layout.toJson()
        // A route a newer version added, under a serial name this build does not know.
        val broken = json.replaceFirst("\"type\":\"", "\"type\":\"com.example.FutureRoute\",\"was\":\"")
        val read = assertNotNull(DeckLayout.fromJson(broken))

        assertEquals(2, read.workspaces.size)
        assertEquals(1, read.active)
        assertEquals(layout.workspaces.sumOf { it.columns.size } - 1, read.workspaces.sumOf { it.columns.size })
    }
}
