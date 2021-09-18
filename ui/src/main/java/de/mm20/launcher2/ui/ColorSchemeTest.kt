package de.mm20.launcher2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.locals.LocalColorScheme
import de.mm20.launcher2.ui.theme.colors.ColorSwatch

@Composable
fun ColorSchemeTest() {
    val colorScheme = LocalColorScheme.current

    Card {
        Column {
            SwatchRow(swatch = colorScheme.neutral1)
            SwatchRow(swatch = colorScheme.neutral2)
            SwatchRow(swatch = colorScheme.accent1)
            SwatchRow(swatch = colorScheme.accent2)
            SwatchRow(swatch = colorScheme.accent3)
        }
    }
}

@Composable
fun SwatchRow(swatch: ColorSwatch) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade0))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade10))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade50))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade100))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade200))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade300))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade400))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade500))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade600))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade700))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade800))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade900))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade1000))
    }
}