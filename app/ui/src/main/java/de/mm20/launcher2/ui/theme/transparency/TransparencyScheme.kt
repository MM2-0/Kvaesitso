package de.mm20.launcher2.ui.theme.transparency

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import de.mm20.launcher2.themes.transparencies.Transparencies


@Composable
fun transparencySchemeOf(transparencies: Transparencies): TransparencyScheme {
    return remember(transparencies) {
        TransparencyScheme(
            background = transparencies.background ?: 0.85f,
            surface = transparencies.surface ?: 1f,
            elevatedSurface = transparencies.elevatedSurface ?: 1f,
        )
    }
}

data class TransparencyScheme(
    val background: Float = 0.85f,
    val surface: Float = 1f,
    val elevatedSurface: Float = 1f,
)

val LocalTransparencyScheme = compositionLocalOf { TransparencyScheme(background = 0.85f, surface = 1f, elevatedSurface = 1f) }

val MaterialTheme.transparency
    @Composable
    get() = LocalTransparencyScheme.current