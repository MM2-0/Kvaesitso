package de.mm20.launcher2.ui.settings.transparencies

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ktx.ceilToInt

fun Modifier.checkerboard(
    color1: Color = Color.LightGray,
    color2: Color = Color.DarkGray,
    tileSize: Dp = 16.dp
): Modifier {
    return drawBehind {
        val tileSizePx = with(density) { tileSize.toPx() }
        val hTiles = (size.width / tileSizePx).ceilToInt()
        val vTiles = (size.height / tileSizePx).ceilToInt()

        for (i in 0 until hTiles) {
            for (j in 0 until vTiles) {
                val color = if ((i + j) % 2 == 0) color1 else color2
                drawRect(
                    color = color,
                    topLeft = Offset(
                        x = i * tileSizePx,
                        y = j * tileSizePx
                    ),
                    size = Size(tileSizePx + 1, tileSizePx + 1)
                )
            }
        }
    }
}