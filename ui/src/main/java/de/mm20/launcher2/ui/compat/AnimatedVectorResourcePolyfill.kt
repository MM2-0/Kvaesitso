package de.mm20.launcher2.ui.compat

import androidx.annotation.DrawableRes
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import de.mm20.launcher2.ui.R

@Composable
fun animatedVectorResource(@DrawableRes id: Int): AnimatedVectorResourceStub {
    return AnimatedVectorResourceStub(id)
}

class AnimatedVectorResourceStub(
    val res: Int
) {
    @Composable
    fun painterFor(atEnd: Boolean): Painter {
        return ColorPainter(MaterialTheme.colors.onSurface)
    }
}