package de.mm20.launcher2.ui.launcher.scaffold.animation

import androidx.compose.animation.core.AnimationVector3D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.ui.geometry.Offset

/**
 * 3D displacement used by scaffold animations.
 * x/y are screen-space offsets, z is normalized depth progress (0..1).
 */
internal data class Offset3D(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
) {
    fun toOffset2D(): Offset = Offset(x, y)

    companion object {
        val Zero = Offset3D()

        val VectorConverter: TwoWayConverter<Offset3D, AnimationVector3D> = TwoWayConverter(
            convertToVector = { AnimationVector3D(it.x, it.y, it.z) },
            convertFromVector = { Offset3D(it.v1, it.v2, it.v3) },
        )

        fun from2D(offset: Offset): Offset3D = Offset3D(x = offset.x, y = offset.y)
    }
}
