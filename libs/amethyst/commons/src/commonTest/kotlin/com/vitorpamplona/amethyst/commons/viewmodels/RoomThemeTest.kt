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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomThemeTest {
    private fun event(extra: List<Array<String>>): MeetingSpaceEvent =
        MeetingSpaceEvent(
            id = "0".repeat(64),
            pubKey = "a".repeat(64),
            createdAt = 1L,
            tags =
                (
                    listOf(
                        arrayOf("d", "rt"),
                        arrayOf("room", "Room"),
                        arrayOf("status", "open"),
                        arrayOf("service", "https://moq"),
                    ) + extra
                ).toTypedArray(),
            content = "",
            sig = "0".repeat(128),
        )

    @Test
    fun emptyEventProducesEmptyTheme() {
        val theme = RoomTheme.from(event(emptyList()))
        assertNull(theme.backgroundArgb)
        assertNull(theme.textArgb)
        assertNull(theme.primaryArgb)
        assertNull(theme.backgroundImageUrl)
        assertEquals(RoomTheme.BackgroundMode.COVER, theme.backgroundMode)
    }

    @Test
    fun projectsAllThreeColorTargets() {
        val theme =
            RoomTheme.from(
                event(
                    listOf(
                        arrayOf("c", "#FF8800", "background"),
                        arrayOf("c", "#101010", "text"),
                        arrayOf("c", "#1E88E5", "primary"),
                    ),
                ),
            )
        assertEquals(0xFFFF8800L, theme.backgroundArgb)
        assertEquals(0xFF101010L, theme.textArgb)
        assertEquals(0xFF1E88E5L, theme.primaryArgb)
    }

    @Test
    fun firstColorPerTargetWins() {
        val theme =
            RoomTheme.from(
                event(
                    listOf(
                        arrayOf("c", "#FF0000", "background"),
                        // A second background color is ignored — palette
                        // fallbacks aren't supported in v1.
                        arrayOf("c", "#00FF00", "background"),
                    ),
                ),
            )
        assertNull(theme.backgroundArgb)
    }

    @Test
    fun typoedHexFallsBackToNullField() {
        val theme =
            RoomTheme.from(
                event(
                    listOf(
                        arrayOf("c", "red", "background"), // ColorTag rejects, RoomTheme leaves null
                        arrayOf("c", "#101010", "text"),
                    ),
                ),
            )
        assertNull(theme.backgroundArgb)
        assertNull(theme.textArgb)
    }

    @Test
    fun backgroundImageWithTileMode() {
        val theme =
            RoomTheme.from(
                event(listOf(arrayOf("bg", "https://i/p.png", "tile"))),
            )
        assertEquals("https://i/p.png", theme.backgroundImageUrl)
        assertEquals(RoomTheme.BackgroundMode.TILE, theme.backgroundMode)
    }

    @Test
    fun unknownBackgroundModeFallsBackToCover() {
        val theme =
            RoomTheme.from(
                event(listOf(arrayOf("bg", "https://i/p.png", "blur"))),
            )
        assertEquals(RoomTheme.BackgroundMode.COVER, theme.backgroundMode)
    }

    @Test
    fun packedArgbIsAlwaysOpaque() {
        // Even for low-luminance hex like #000000, the alpha channel
        // must be 0xFF — themes don't expose transparency in v1.
        assertEquals(0xFF000000L, RoomTheme.hexToOpaqueArgb("000000"))
        assertEquals(0xFFFFFFFFL, RoomTheme.hexToOpaqueArgb("FFFFFF"))
    }

    @Test
    fun fontFamilyOnly() {
        val theme = RoomTheme.from(event(listOf(arrayOf("f", "Inter"))))
        assertEquals("Inter", theme.fontFamily)
        assertNull(theme.fontUrl)
    }

    @Test
    fun fontFamilyAndUrl() {
        val theme =
            RoomTheme.from(
                event(listOf(arrayOf("f", "Inter", "https://fonts.example/inter.woff2"))),
            )
        assertEquals("Inter", theme.fontFamily)
        assertEquals("https://fonts.example/inter.woff2", theme.fontUrl)
    }

    @Test
    fun emptyFontDefaultsBothNull() {
        val theme = RoomTheme.from(event(emptyList()))
        assertNull(theme.fontFamily)
        assertNull(theme.fontUrl)
    }
}
