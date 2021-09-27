package de.mm20.launcher2.ui.theme.colors

import androidx.compose.ui.graphics.Color

class BlackWhiteColorScheme: ColorScheme() {
    override val neutral1: ColorSwatch
        get() = ColorSwatch(
            Color.White,
            Color.White,
            Color.White,
            Color.White,
            Color.White,
            Color.White,
            Color.White,
            Color.Black,
            Color.Black,
            Color.Black,
            Color.Black,
            Color.Black,
            Color.Black,
        )
    override val neutral2: ColorSwatch
        get() = neutral1
    override val accent1: ColorSwatch
        get() = neutral1
    override val accent2: ColorSwatch
        get() = neutral1
    override val accent3: ColorSwatch
        get() = neutral1
}