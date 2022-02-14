package de.mm20.launcher2.ui.component

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.Settings.IconSettings.IconShape
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShapedLauncherIcon(
    modifier: Modifier = Modifier,
    size: Dp,
    icon: LauncherIcon? = null,
    badge: Badge? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = LocalIconShape.current
) {
    Box(
        modifier = modifier
            .size(size)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    this.shape = shape
                }
                .combinedClickable(
                    enabled = onClick != null || onLongClick != null,
                    onClick = {
                        onClick?.invoke()
                    },
                    onLongClick = onLongClick,
                ),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {

                val fgScale = icon.foregroundScale
                val bgScale = icon.backgroundScale


                Canvas(modifier = Modifier.fillMaxSize()) {
                    val fg = icon.foreground
                    val bg = icon.background
                    drawIntoCanvas {
                        val paddingFg = (size * (1 - fgScale) * 0.5f).toPx()
                        val paddingBg = (size * (1 - bgScale) * 0.5f).toPx()
                        bg?.setBounds(
                            paddingBg.toInt(),
                            paddingBg.toInt(),
                            (this.size.width - paddingBg).toInt(),
                            (this.size.height - paddingBg).toInt()
                        )
                        bg?.draw(it.nativeCanvas)
                        fg.setBounds(
                            paddingFg.toInt(),
                            paddingFg.toInt(),
                            (this.size.width - paddingFg).toInt(),
                            (this.size.height - paddingFg).toInt()
                        )
                        fg.draw(it.nativeCanvas)
                    }
                }
            }
        }
        if (badge != null) {
            Surface(
                shadowElevation = 1.dp,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .size(size * 0.33f)
                    .align(Alignment.BottomEnd),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    val badgeIconRes = badge.iconRes
                    val badgeIcon = badge.icon

                    val number = badge.number
                    if (badgeIconRes != null) {
                        Image(
                            modifier = Modifier.fillMaxSize(),
                            painter = painterResource(badgeIconRes),
                            contentDescription = null
                        )
                    } else if (badgeIcon != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            badgeIcon.setBounds(
                                0,
                                0,
                                this.size.width.roundToInt(),
                                this.size.height.roundToInt()
                            )
                            drawIntoCanvas {
                                badgeIcon.draw(it.nativeCanvas)
                            }
                        }
                    } else if (number != null && number > 0 && number < 100) {
                        Text(
                            number.toString(),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = with(LocalDensity.current) {
                                    size.toSp() * 0.2f
                                }
                            ),
                        )
                    }
                }
            }
        }
    }
}

val LocalIconShape = compositionLocalOf<Shape> { CircleShape }

@Composable
fun ProvideIconShape(iconShape: IconShape, content: @Composable () -> Unit) {
    val shape = when (iconShape) {
        IconShape.PlatformDefault -> PlatformShape
        IconShape.Circle -> CircleShape
        IconShape.Square -> RectangleShape
        IconShape.RoundedSquare -> RoundedCornerShape(25)
        IconShape.Triangle -> TriangleShape
        IconShape.Squircle -> SquircleShape
        IconShape.Hexagon -> HexagonShape
        IconShape.Pentagon -> PentagonShape
        IconShape.EasterEgg -> EasterEggShape
        IconShape.UNRECOGNIZED -> CircleShape
    }
    CompositionLocalProvider(
        LocalIconShape provides shape,
        content = content
    )
}

private val TriangleShape: Shape
    get() = GenericShape { size, _ ->
        var cx = 0f
        var cy = size.height * 0.86f
        val r = size.width
        arcTo(Rect(cx - r, cy - r, cx + r, cy + r), 300f, 60f, false)
        cx = size.width
        cy = size.height * 0.86f
        arcTo(Rect(cx - r, cy - r, cx + r, cy + r), 180f, 60f, false)
        cx = size.width * 0.5f
        cy = 0f
        arcTo(Rect(cx - r, cy - r, cx + r, cy + r), 60f, 60f, false)
        close()
    }

private val SquircleShape: Shape
    get() = GenericShape { size, _ ->
        val radius = size.width / 2f
        val radiusToPow = radius.pow(3f).toDouble()
        moveTo(-radius, 0f)
        for (x in -radius.roundToInt()..radius.roundToInt())
            lineTo(
                x.toFloat(),
                Math.cbrt(radiusToPow - Math.abs(x * x * x)).toFloat()
            )
        for (x in radius.roundToInt() downTo -radius.roundToInt())
            lineTo(
                x.toFloat(),
                (-Math.cbrt(radiusToPow - Math.abs(x * x * x))).toFloat()
            )
        translate(Offset(size.width / 2f, size.height / 2f))
    }

private val HexagonShape: Shape
    get() = GenericShape { size, _ ->
        moveTo(
            size.width * 0.25f,
            size.height * 0.933f
        )
        lineTo(
            size.width * 0.75f,
            size.height * 0.933f
        )
        lineTo(
            size.width * 1.0f,
            size.height * 0.5f
        )
        lineTo(
            size.width * 0.75f,
            size.height * 0.067f
        )
        lineTo(
            size.width * 0.25f,
            size.height * 0.067f
        )
        lineTo(0f, size.height * 0.5f)
        close()
    }

private val PentagonShape: Shape
    get() = GenericShape { size, _ ->
        moveTo(
            0.49997027f * size.width,
            0.0060308f * size.height
        )
        lineTo(
            0.99994053f * size.width,
            0.36928048f * size.height
        )
        lineTo(
            0.80896887f * size.width,
            0.95703078f * size.height
        )
        lineTo(
            0.19097162f * size.width,
            0.95703076f * size.height
        )
        lineTo(
            0f,
            0.36928045f * size.height
        )
        close()
    }

private val EasterEggShape: Shape
    get() = GenericShape { size, _ ->
        moveTo(
            0.49999999f * size.width,
            1f * size.height
        )
        lineTo(
            0.42749999f * size.width,
            0.9339999999999999f * size.height
        )
        cubicTo(
            0.16999998f * size.width,
            0.7005004f * size.height,
            0f,
            0.5460004f * size.height,
            0f,
            0.3575003f * size.height
        )
        cubicTo(
            0f,
            0.2030004f * size.height,
            0.12100002f * size.width,
            0.0825004f * size.height,
            0.275f * size.width,
            0.0825004f * size.height
        )
        cubicTo(
            0.362f * size.width,
            0.0825004f * size.height,
            0.4455f * size.width,
            0.123f * size.height,
            0.5f * size.width,
            0.1865003f * size.height
        )
        cubicTo(
            0.55449999f * size.width,
            0.123f * size.height,
            0.638f * size.width,
            0.0825f * size.height,
            0.725f * size.width,
            0.0825f * size.height
        )
        cubicTo(
            0.87900006f * size.width,
            0.0825004f * size.height,
            1f * size.width,
            0.2030004f * size.height,
            1f * size.width,
            0.3575003f * size.height
        )
        cubicTo(
            1f * size.width,
            0.5460004f * size.height,
            0.82999999f * size.width,
            0.7005004f * size.height,
            0.57250001f * size.width,
            0.9340004f * size.height
        )
        close()
    }

private val PlatformShape: Shape
    get() {
        val drawable = AdaptiveIconDrawable(null, null)

        val pathBounds = RectF()
        drawable.iconMask.computeBounds(pathBounds, true)

        return GenericShape { size, _ ->
            val path = Path(drawable.iconMask)
            val transformMatrix = Matrix()
            transformMatrix.setScale(
                size.width / pathBounds.width(),
                size.height / pathBounds.height()
            )
            path.transform(transformMatrix)
            addPath(path.asComposePath())
        }
    }