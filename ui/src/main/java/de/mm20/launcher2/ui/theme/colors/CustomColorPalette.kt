package de.mm20.launcher2.ui.theme.colors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.preferences.dataStore
import kotlinx.coroutines.flow.map

class CustomColorPalette(val colors: CustomColors) : ColorPalette() {
    override val neutral: ColorSwatch
        get() = colorSwatch(colors.neutral1)
    override val neutralVariant: ColorSwatch
        get() = colorSwatch(colors.neutral2)
    override val primary: ColorSwatch
        get() = colorSwatch(colors.accent1)
    override val secondary: ColorSwatch
        get() = colorSwatch(colors.accent2)
    override val tertiary: ColorSwatch
        get() = colorSwatch(colors.accent3)

}

data class CustomColors(
    val neutral1: Color,
    val neutral2: Color,
    val accent1: Color,
    val accent2: Color,
    val accent3: Color,
)

@Composable
fun customColorsAsState(): State<CustomColors> {
    val dataStore = LocalContext.current.dataStore
    return remember {
        dataStore.data.map {
            val colors = it.appearance.customColors
            CustomColors(
                neutral1 = Color(colors.neutral1).copy(alpha = 1f),
                neutral2 = Color(colors.neutral2).copy(alpha = 1f),
                accent1 = Color(colors.accent1).copy(alpha = 1f),
                accent2 = Color(colors.accent2).copy(alpha = 1f),
                accent3 = Color(colors.accent3).copy(alpha = 1f),
            )
        }
    }.collectAsState(
        initial = DefaultCustomColors
    )
}

val DefaultCustomColors = CustomColors(
    Color.Black,
    Color.Black,
    Color.Black,
    Color.Black,
    Color.Black,
)