package de.mm20.launcher2.ui.theme.colors

import androidx.compose.ui.graphics.Color


class DefaultColorPalette: ColorPalette() {

    override val neutral = colorSwatch(Color.Black)

    override val neutralVariant = neutral

    override val primary = colorSwatch(Color(0xFF39A0ED))

    override val secondary = colorSwatch(Color(0xFF4C6085))

    override val tertiary = colorSwatch(Color(0xFFF59CA9))

}