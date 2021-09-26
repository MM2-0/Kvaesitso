package de.mm20.launcher2.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.ui.locals.LocalColorScheme

val lightPalette = lightColors(
    primary = Color(0, 114, 255)
)

val darkPalette = darkColors(
    primary = Color(0, 114, 255)
)

val Inter = FontFamily(
    Font(R.font.inter_thin, FontWeight.Thin),
    Font(R.font.inter_extralight, FontWeight.ExtraLight),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_black, FontWeight.Black),
)


val typography = Typography(
    h1 = TextStyle(
        fontSize = 96.sp,
        fontWeight = FontWeight.Light,
        fontFamily = Inter
    ),
    h2 = TextStyle(
        fontSize = 60.sp,
        fontWeight = FontWeight.Light,
        fontFamily = Inter
    ),
    h3 = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = Inter
    ),
    h4 = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = Inter
    ),
    h5 = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = Inter
    ),
    h6 = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = Inter
    ),
    caption = TextStyle(
        fontFamily = Inter,
        fontSize = 13.sp
    ),
    subtitle1 = TextStyle(
        fontFamily = Inter,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
    ),
    subtitle2 = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    body1 = TextStyle(
        fontSize = 13.sp
    ),
    body2 = TextStyle(
        fontSize = 12.sp
    )
)

val shapes = Shapes(
    medium = RoundedCornerShape(8.dp)
)

val Colors.red: Color
    get() = if (isLight) Color(0xFFE53935) else Color(0xFFE57373)

val Colors.pink: Color
    get() = if (isLight) Color(0xFFD81B60) else Color(0xFFF06292)

val Colors.purple: Color
    get() = if (isLight) Color(0xFF8E24AA) else Color(0xFFBA68C8)

val Colors.deepPurple: Color
    get() = if (isLight) Color(0xFF5E35B1) else Color(0xFF9575CD)

val Colors.indigo: Color
    get() = if (isLight) Color(0xFF3949AB) else Color(0xFF7986CB)

val Colors.blue: Color
    get() = if (isLight) Color(0xFF039BE5) else Color(0xFF4FC3F7)

val Colors.lightBlue: Color
    get() = if (isLight) Color(0xFF1E88E5) else Color(0xFF64B5F6)

val Colors.cyan: Color
    get() = if (isLight) Color(0xFF00ACC1) else Color(0xFF4DD0E1)

val Colors.teal: Color
    get() = if (isLight) Color(0xFF00897B) else Color(0xFF4DB6AC)

val Colors.green: Color
    get() = if (isLight) Color(0xFF388E3C) else Color(0xFF81C784)

val Colors.lightGreen: Color
    get() = if (isLight) Color(0xFF7CB342) else Color(0xFFAED581)


val Colors.lime: Color
    get() = if (isLight) Color(0xFFC0CA33) else Color(0xFFDCE775)

val Colors.yellow: Color
    get() = if (isLight) Color(0xFFFDD835) else Color(0xFFFFF176)

val Colors.amber: Color
    get() = if (isLight) Color(0xFFFFB300) else Color(0xFFFFD54F)

val Colors.orange: Color
    get() = if (isLight) Color(0xFFFB8C00) else Color(0xFFFFB74D)

val Colors.deepOrange: Color
    get() = if (isLight) Color(0xFFF4511E) else Color(0xFFFF8A65)

val Colors.brown: Color
    get() = if (isLight) Color(0xFF6D4C41) else Color(0xFFA1887F)

val Colors.gray: Color
    get() = if (isLight) Color(0xFF757575) else Color(0xFFE0E0E0)

val Colors.blueGray: Color
    get() = if (isLight) Color(0xFF546E7A) else Color(0xFF90A4AE)


val Colors.androidGreen: Color
    get() = if (isLight) Color(0xFF00A55B) else Color(0xFF00DE7A)

val Colors.weatherSkyClear: Color
    get() = Color(0xff4482ac)

val Colors.weatherSkyClearNight: Color
    get() = deepPurple

val Colors.weatherSkyCloudy: Color
    get() = gray

val Colors.weatherSkyCloudyNight: Color
    get() = gray

val Colors.weatherSkyThunder: Color
    get() = gray

val Colors.weatherSkyThunderNight: Color
    get() = gray

val Colors.weatherCloudLight1: Color
    get() = Color(0xFFECEFF1)

val Colors.weatherCloudLight2: Color
    get() = if (isLight) Color(0xFF90A4AE) else Color(0xFFCFD8DC)

val Colors.weatherCloudMedium1: Color
    get() = if (isLight) Color(0xFF546E7A) else Color(0xFF78909C)

val Colors.weatherCloudMedium2: Color
    get() = if (isLight) Color(0xFF455a64) else Color(0xFF607D8B)

val Colors.weatherCloudDark1: Color
    get() = if (isLight) Color(0xFF37474F) else Color(0xFF546E7A)

val Colors.weatherCloudDark2: Color
    get() = if (isLight) Color(0xFF263238) else Color(0xFF455A64)

val Colors.weatherSun: Color
    get() = amber

val Colors.weatherMoon: Color
    get() = if (isLight) Color(0xFF9E9E9E) else Color(0xFFE0E0E0)

val Colors.weatherBolt: Color
    get() = amber

val Colors.weatherHot: Color
    get() = red

val Colors.weatherCold: Color
    get() = lightBlue

val Colors.weatherWind: Color
    get() = if (isLight) Color(0xFF90A4AE) else Color(0xFFCFD8DC)

val Colors.weatherWindDark: Color
    get() = if (isLight) Color(0xFF546E7A) else Color(0xFF78909C)

val Colors.weatherRain: Color
    get() = blue

val Colors.weatherHail: Color
    get() = if (isLight) Color(0xFFBBDEFB) else Color(0xFFE3F2FD)

val Colors.weatherSnow: Color
    get() = if (isLight) Color(0xFFE0E0E0) else Color(0xFFF5F5F5)

val Colors.weatherFog: Color
    get() = weatherCloudLight2

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {

    val colorScheme = LocalColorScheme.current

    val colors = if (isSystemInDarkTheme()) {
        darkColors(
            onSurface = colorScheme.neutral2.shade10,
            surface = colorScheme.neutral2.shade800,
            onBackground = colorScheme.neutral2.shade10,
            background = colorScheme.neutral2.shade900,
            primary = colorScheme.accent1.shade300,
            primaryVariant = colorScheme.accent1.shade400,
            secondary = colorScheme.accent2.shade300,
            secondaryVariant = colorScheme.accent3.shade300,
        )
    } else {
        lightColors(
            surface = colorScheme.neutral1.shade0,
            onSurface = colorScheme.neutral2.shade1000,
            onBackground = colorScheme.neutral2.shade1000,
            background = colorScheme.neutral1.shade50,
            primary = colorScheme.accent1.shade600,
            primaryVariant = colorScheme.accent1.shade700,
            secondary = colorScheme.accent2.shade600,
            secondaryVariant = colorScheme.accent3.shade600,
        )
    }

    MaterialTheme(
        colors = colors,
        typography = typography,
        shapes = shapes,
        content = content
    )
}