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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

val Purple200 = Color(0xFFBB86FC)
val Purple500 = Color(0xFF6200EE)
val Teal200 = Color(0xFF03DAC5)

// Accent palette options selected through Settings -> Accent Color.
// Each accent ships a brighter variant for the dark theme and a deeper variant for the light theme.
val AccentBlueDark = Color(0xFF82B1FF)
val AccentBlueLight = Color(0xFF1565C0)
val AccentGreenDark = Color(0xFF80CBC4)
val AccentGreenLight = Color(0xFF2E7D32)
val AccentOrangeDark = Color(0xFFFFB74D)
val AccentOrangeLight = Color(0xFFE65100)
val AccentRedDark = Color(0xFFEF9A9A)
val AccentRedLight = Color(0xFFC62828)
val AccentPinkDark = Color(0xFFF48FB1)
val AccentPinkLight = Color(0xFFAD1457)
val BitcoinOrange = Color(0xFFF7931A)
val RoyalBlue = Color(0xFF4169E1)

val LikedColor = Color(0xFFCA395f)
val RepostedColor = Color(0xFF59bc6d)

val BitcoinDark = Color(0xFFF7931A)
val BitcoinLight = Color(0xFFB66605)

val Following = Color(0xFF03DAC5)

val LightRedColor = Color(0xFFC62828)

val RelayIconFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.5f) })

val LightWarningColor = Color(0xFFffcc00)
val DarkWarningColor = Color(0xFFF8DE22)

val LightRedColorOnSecondSurface = Color(0xFFC62828)
val DarkRedColorOnSecondSurface = Color(0xFFF34747)

val LightWarningColorOnSecondSurface = Color(0xFFC09B14)
val DarkWarningColorOnSecondSurface = Color(0xFFE1C419)

val LightAllGoodColor = Color(0xFF339900)
val DarkAllGoodColor = Color(0xFF99cc33)

val LightFundraiserProgressColor = Color(0xFF3DB601)
val DarkFundraiserProgressColor = Color(0xFF61A229)

// Brand colors used by desktop chrome.
val AmethystBlue = Color(0xFF0096FF)
val AmethystBlueDark = Color(0xFF4DB8FF)
val AmethystPurple = Color(0xFF9A82DB)

// Tonal steps of the default purple (desktop's accent picker falls back to Primary80).
val Primary50 = Color(red = 127, green = 103, blue = 190)
val Primary60 = Color(red = 154, green = 130, blue = 219)
val Primary70 = Color(red = 182, green = 157, blue = 248)
val Primary80 = Color(red = 208, green = 188, blue = 255)

val Purple700 = Color(0xFF3700B3)

val FollowsFollow = Color.Yellow
val Nip05Verified = Color.Blue

// NIP-05 email colors
val Nip05EmailColor = Color(0xFFb198ec)
val Nip05EmailColorDark = Color(0xFF6e5490)
val Nip05EmailColorLight = Color(0xFFa770f3)

val DarkerGreen = Color.Green.copy(alpha = 0.32f)
val LighterRedColor = Color(0xFFFF0E0E)

// Semantic status colors for desktop
val StatusGreen = Color(0xFF4CAF50)
val StatusGreenDark = Color(0xFF81C784)
val StatusRed = Color(0xFFF44336)
val StatusRedDark = Color(0xFFEF9A9A)
val StatusAmber = Color(0xFFFFB300)
val StatusAmberDark = Color(0xFFFFD54F)
val StatusBlue = Color(0xFF2196F3)

// Relay status colors
object RelayStatusColors {
    val Connected = Color.Green
    val Connecting = Color.Yellow
    val Disconnected = Color.Red
    val Unknown = Color.Gray
}
