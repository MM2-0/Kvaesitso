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
package com.vitorpamplona.amethyst.commons.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsLightTest {
    @Test
    fun amethystDarkSchemeIsDarkForEveryAccent() {
        assertFalse(DarkColorPalette.isLight)
        assertFalse(amethystDarkColorScheme(AccentPinkDark, AccentPinkDark, AccentPinkLight).isLight)
    }

    @Test
    fun amethystLightSchemeIsLightForEveryAccent() {
        assertTrue(LightColorPalette.isLight)
        assertTrue(amethystLightColorScheme(AccentPinkLight, AccentPinkLight, AccentPinkDark).isLight)
    }

    @Test
    fun desktopRampsFallBackToLuminance() {
        // desktop/platform/PlatformColorScheme: dark #121212 is NOT pure black, light is #F2F2F7.
        assertFalse(darkColorScheme(background = Color(0xFF121212)).isLight)
        assertTrue(lightColorScheme(background = Color(0xFFF2F2F7)).isLight)
    }

    @Test
    fun themedRoomBackgroundsAreJudgedByTheirOwnLuminance() {
        // A themed room copies the scheme with the room's background: a dark room reads dark
        // even when it came from the light scheme, and a pale room reads light.
        assertFalse(LightColorPalette.copy(background = Color(0xFF1A2B3C)).isLight)
        assertTrue(DarkColorPalette.copy(background = Color(0xFFF5E6D3)).isLight)
    }

    @Test
    fun memoDoesNotLeakTheAnswerForADifferentBackground() {
        val pale = darkColorScheme(background = Color(0xFFEEEEEE))
        val deep = lightColorScheme(background = Color(0xFF222222))
        repeat(3) {
            assertTrue(pale.isLight)
            assertFalse(deep.isLight)
        }
        assertEquals(true, pale.isLight)
    }
}
