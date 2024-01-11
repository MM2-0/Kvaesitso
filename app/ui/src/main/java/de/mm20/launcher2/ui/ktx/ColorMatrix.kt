package de.mm20.launcher2.ui.ktx

import androidx.compose.ui.graphics.ColorMatrix
import de.mm20.launcher2.ktx.PI
import kotlin.math.cos
import kotlin.math.sin

// inspiration from darkreader
// https://github.com/darkreader/darkreader/blob/main/src/generators/utils/matrix.ts

fun ColorMatrix.invert(fraction: Float, withAlpha: Boolean = false): ColorMatrix {
    assert(fraction in 0f..1f)
    val scale = -2f * fraction + 1f
    this *= ColorMatrix().apply {
        setToScale(scale, scale, scale, if (withAlpha) scale else 1f)
        this[0,4] = 255f
        this[1,4] = 255f
        this[2,4] = 255f
    }
    return this
}

// https://chromium.googlesource.com/chromium/blink/+/master/Source/platform/graphics/filters/FEColorMatrix.cpp#93
fun ColorMatrix.hueRotate(deg: Float): ColorMatrix {
    val cosHue = cos(deg * Float.PI / 180f)
    val sinHue = sin(deg * Float.PI / 180f)
    val mat = FloatArray(20)
    mat[0] = 0.213f + cosHue * 0.787f - sinHue * 0.213f
    mat[1] = 0.715f - cosHue * 0.715f - sinHue * 0.715f
    mat[2] = 0.072f - cosHue * 0.072f + sinHue * 0.928f
    mat[5] = 0.213f - cosHue * 0.213f + sinHue * 0.143f
    mat[6] = 0.715f + cosHue * 0.285f + sinHue * 0.140f
    mat[7] = 0.072f - cosHue * 0.072f - sinHue * 0.283f
    mat[10] = 0.213f - cosHue * 0.213f - sinHue * 0.787f
    mat[11] = 0.715f - cosHue * 0.715f + sinHue * 0.715f
    mat[12] = 0.072f + cosHue * 0.928f + sinHue * 0.072f
    mat[18] = 1f
    this *= ColorMatrix(mat)
    return this
}

fun ColorMatrix.contrast(contrast: Float): ColorMatrix {
    this *= ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, 0f,
            0f, contrast, 0f, 0f, 0f,
            0f, 0f, contrast, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    return this
}

val NHueInvertColorMatrix = ColorMatrix(
    floatArrayOf(
        0.333f, -0.667f, -0.667f, 0f, 1f,
        -0.667f, 0.333f, -0.667f, 0f, 1f,
        -0.667f, -0.667f, 0.333f, 0f, 1f,
        0f, 0f, 0f, 1f, 0f,
    )
)
