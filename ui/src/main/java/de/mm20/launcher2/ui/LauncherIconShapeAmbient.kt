package de.mm20.launcher2.ui

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.util.Log
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.core.graphics.flatten
import de.mm20.launcher2.ktx.isAtLeastApiLevel

val LocalLauncherIconShape = compositionLocalOf { LauncherIconShape.circle }

object LauncherIconShape {
    val circle: Shape = CircleShape
    val square: Shape = RectangleShape
    val roundedSquare: Shape = RoundedCornerShape(13)
    val hexagon: Shape = GenericShape { size, _ ->
        moveTo(size.width * 0.25f, size.height * 0.933f)
        lineTo(size.width * 0.75f, size.height * 0.933f)
        lineTo(size.width * 1.0f, size.height * 0.5f)
        lineTo(size.width * 0.75f, size.height * 0.067f)
        lineTo(size.width * 0.25f, size.height * 0.067f)
        lineTo(0f, size.height * 0.5f)
        close()
    }
    val platformDefault: Shape = run {
        val platformShape = getSystemShape()
        if (platformShape == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@run CircleShape
        GenericShape { size, _ ->
            Log.d("MM20", "GenericShape {}")
            val matrix = Matrix()
            val bounds = RectF()
            platformShape.computeBounds(bounds, true)
            matrix.setRectToRect(bounds, RectF(0f, 0f, size.width, size.height), Matrix.ScaleToFit.CENTER)
            platformShape.transform(matrix)
            addPath(platformShape.asComposePath())
        }
    }

    private fun getSystemShape(): Path? {
        return if (isAtLeastApiLevel(Build.VERSION_CODES.O)) {
            AdaptiveIconDrawable(null, null).iconMask
        } else {
            null
        }
    }
}