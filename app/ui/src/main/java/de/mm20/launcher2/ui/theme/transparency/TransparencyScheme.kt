package de.mm20.launcher2.ui.theme.transparency

import androidx.compose.runtime.compositionLocalOf

data class TransparencyScheme(
    val background: Float,
    val surface: Float,
)

val LocalTransparencyScheme = compositionLocalOf { TransparencyScheme(0.85f, 1f) }