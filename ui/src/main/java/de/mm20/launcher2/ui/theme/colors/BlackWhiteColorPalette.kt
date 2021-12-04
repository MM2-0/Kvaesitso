package de.mm20.launcher2.ui.theme.colors

import androidx.compose.ui.graphics.Color

class BlackWhiteColorPalette: ColorPalette() {
    override val neutral: ColorSwatch
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
    override val neutralVariant: ColorSwatch
        get() = neutral
    override val primary: ColorSwatch
        get() = neutral
    override val secondary: ColorSwatch
        get() = neutral
    override val tertiary: ColorSwatch
        get() = neutral
}