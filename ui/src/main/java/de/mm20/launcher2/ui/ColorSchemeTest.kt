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
            SwatchRow(swatch = colorScheme.neutral)
            SwatchRow(swatch = colorScheme.neutralVariant)
            SwatchRow(swatch = colorScheme.primary)
            SwatchRow(swatch = colorScheme.secondary)
            SwatchRow(swatch = colorScheme.tertiary)
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
            .background(swatch.shade100))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade99))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade95))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade90))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade80))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade70))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade60))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade50))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade40))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade30))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade20))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade10))
        Box(modifier = Modifier
            .height(24.dp).weight(1f)
            .background(swatch.shade0))
    }
}