package de.mm20.launcher2.ui.theme.colors

import androidx.compose.ui.graphics.Color


class DefaultColorScheme: ColorScheme() {

    override val neutral1 = colorSwatch(Color.Black)

    override val neutral2 = neutral1

    override val accent1 = colorSwatch(Color(0xFF39A0ED))

    override val accent2 = colorSwatch(Color(0xFF4C6085))

    override val accent3 = colorSwatch(Color(0xFFF59CA9))

}