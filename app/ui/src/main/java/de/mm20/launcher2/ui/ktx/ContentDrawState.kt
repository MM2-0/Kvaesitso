package de.mm20.launcher2.ui.ktx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.unit.Dp

fun ContentDrawScope.drawFadedEdge(leftEdge: Boolean, edgeWidth: Dp) {
    // https://stackoverflow.com/a/75112743
    val edgeWidthPx = edgeWidth.toPx()
    drawRect(
        topLeft = Offset(if (leftEdge) 0f else size.width - edgeWidthPx, 0f),
        size = Size(edgeWidthPx, size.height),
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startX = if (leftEdge) 0f else size.width,
            endX = if (leftEdge) edgeWidthPx else size.width - edgeWidthPx
        ),
        blendMode = BlendMode.DstIn
    )
}