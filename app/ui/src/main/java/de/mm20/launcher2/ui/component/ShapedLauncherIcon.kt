package de.mm20.launcher2.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.icu.number.NumberFormatter
import android.icu.text.NumberFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeIcon
import de.mm20.launcher2.icons.ClockLayer
import de.mm20.launcher2.icons.ClockSublayer
import de.mm20.launcher2.icons.ClockSublayerRole
import de.mm20.launcher2.icons.DynamicLauncherIcon
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.LauncherIconRenderSettings
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TextLayer
import de.mm20.launcher2.icons.TintedClockLayer
import de.mm20.launcher2.icons.TransparentLayer
import de.mm20.launcher2.icons.VectorLayer
import de.mm20.launcher2.ktx.drawWithColorFilter
import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.ui.base.LocalTime
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.modifier.scale
import palettes.TonalPalette
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import android.graphics.Shader as PlatformShader

@Composable
fun ShapedLauncherIcon(
    modifier: Modifier = Modifier,
    size: Dp,
    icon: () -> LauncherIcon? = { null },
    badge: () -> Badge? = { null },
    shape: Shape = LocalIconShape.current
) {

    val _icon = icon()

    var currentIcon by remember(_icon) {
        mutableStateOf(
            when (_icon) {
                is DynamicLauncherIcon -> null
                is StaticLauncherIcon -> _icon
                else -> null
            }
        )
    }

    val defaultIconSize = LocalGridSettings.current.iconSize.dp

    val renderSettings = LauncherIconRenderSettings(
        size = defaultIconSize.toPixels().toInt(),
        fgThemeColor = MaterialTheme.colorScheme.onPrimaryContainer.toArgb(),
        bgThemeColor = MaterialTheme.colorScheme.primaryContainer.toArgb(),
        fgTone = if (LocalDarkTheme.current) 90 else 10,
        bgTone = if (LocalDarkTheme.current) 30 else 90,
    )

    var currentBitmap by remember {
        mutableStateOf(currentIcon?.getCachedBitmap(renderSettings))
    }

    LaunchedEffect(currentIcon, renderSettings) {
        currentBitmap = currentIcon?.render(renderSettings)
    }

    if (_icon is DynamicLauncherIcon) {
        val date = Instant.ofEpochMilli(LocalTime.current).atZone(ZoneId.systemDefault())
        LaunchedEffect(date.dayOfYear, _icon) {
            currentIcon = _icon.getIcon(date.toEpochSecond() * 1000L)
        }
    }

    Box(
        modifier = modifier
            .size(size)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val bmp = currentBitmap
            val ic = currentIcon
            if (bmp != null && ic != null) {
                Canvas(
                    modifier = Modifier
                        .requiredSize(defaultIconSize)
                        .scale(size / defaultIconSize, TransformOrigin.Center)
                ) {
                    val brush = BitmapShaderBrush(bmp)
                    if (ic.backgroundLayer is TransparentLayer) {
                        drawRect(brush)
                    } else {
                        val outline =
                            shape.createOutline(
                                this.size,
                                layoutDirection,
                                Density(density, fontScale)
                            )
                        drawOutline(outline, brush)
                    }
                }
                // Background layer is always static layer, color layer, or transparent layer
                val fg = ic.foregroundLayer
                when (fg) {
                    is ClockLayer -> {
                        ClockLayer(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(shape),
                            sublayers = fg.sublayers,
                            defaultMinute = fg.defaultMinute,
                            defaultHour = fg.defaultHour,
                            defaultSecond = fg.defaultSecond,
                            scale = fg.scale,
                            tintColor = null,
                        )
                    }

                    is TintedClockLayer -> {
                        ClockLayer(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(shape),
                            sublayers = fg.sublayers,
                            defaultMinute = fg.defaultMinute,
                            defaultHour = fg.defaultHour,
                            defaultSecond = fg.defaultSecond,
                            scale = fg.scale,
                            tintColor = if (fg.color == 0) {
                                Color(renderSettings.fgThemeColor)
                            } else {
                                Color(getTone(fg.color, renderSettings.fgTone))
                            },
                        )
                    }

                    is TextLayer -> {
                        Text(
                            text = fg.text,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 20.sp * (size / 48.dp)
                            ),
                            color = if (fg.color == 0) {
                                Color(renderSettings.fgThemeColor)
                            } else {
                                Color(getTone(fg.color, renderSettings.fgTone))
                            },
                        )
                    }

                    is VectorLayer -> {
                        Icon(
                            painter = painterResource(fg.icon), contentDescription = null,
                            tint = if (fg.color == 0) {
                                Color(renderSettings.fgThemeColor)
                            } else {
                                Color(getTone(fg.color, renderSettings.fgTone))
                            },
                            modifier = Modifier.size(size / 2f),
                        )
                    }
                    else -> {}
                }
            } else {
                val color = MaterialTheme.colorScheme.secondaryContainer
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    val outline =
                        shape.createOutline(this.size, layoutDirection, Density(density, fontScale))
                    drawOutline(outline, color)
                }
            }
        }
        val _badge = badge()
        if (_badge != null) {
            Surface(
                tonalElevation = 1.dp,
                modifier = Modifier
                    .size(size * 0.33f)
                    .align(Alignment.BottomEnd),
                color = MaterialTheme.colorScheme.tertiary,
                shape = CircleShape
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {

                    _badge.progress?.let {
                        val progress by animateFloatAsState(it)
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(0.8f),
                            progress = { progress },
                            strokeWidth = size / 48,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                    val badgeIcon = _badge.icon

                    val number = _badge.number
                    if (badgeIcon is BadgeIcon.Vector) {
                        Icon(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(size / 24),
                            painter = painterResource(badgeIcon.iconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiary,
                        )
                    } else if (badgeIcon is BadgeIcon.Drawable) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(size / 48)
                        ) {
                            badgeIcon.drawable.setBounds(
                                0,
                                0,
                                this.size.width.roundToInt(),
                                this.size.height.roundToInt()
                            )
                            drawIntoCanvas {
                                badgeIcon.drawable.draw(it.nativeCanvas)
                            }
                        }
                    } else if (number != null && number > 0 && number < 100) {
                        Text(
                            NumberFormat.getInstance(Locale.current.platformLocale).format(number),
                            color = MaterialTheme.colorScheme.secondaryContainer,
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

private fun getTone(argb: Int, tone: Int): Int {
    return TonalPalette
        .fromInt(argb)
        .tone(tone)
}

@Composable
private fun ClockLayer(
    sublayers: List<ClockSublayer>,
    defaultMinute: Int,
    defaultHour: Int,
    defaultSecond: Int,
    scale: Float,
    tintColor: Color?,
    modifier: Modifier = Modifier,
) {
    val time = Instant.ofEpochMilli(LocalTime.current).atZone(ZoneId.systemDefault())

    val second = time.second
    val minute = time.minute
    val hour = time.hour

    Canvas(modifier = modifier) {
        val colorFilter = tintColor?.let {
            PorterDuffColorFilter(tintColor.toArgb(), PorterDuff.Mode.SRC_IN)
        }
        withTransform({
            this.scale(scale)
        }) {
            for (sublayer in sublayers) {
                when (sublayer.role) {
                    ClockSublayerRole.Hour -> {
                        sublayer.drawable.level = (((hour - defaultHour + 12) % 12) * 60
                                + ((minute) % 60))
                    }

                    ClockSublayerRole.Minute -> sublayer.drawable.level =
                        ((minute - defaultMinute + 60) % 60)

                    ClockSublayerRole.Second -> sublayer.drawable.level =
                        (((second - defaultSecond + 60) % 60) * 10)

                    else -> {}
                }
                drawIntoCanvas {
                    sublayer.drawable.bounds = this.size.toRect().toAndroidRect()
                    sublayer.drawable.drawWithColorFilter(it.nativeCanvas, colorFilter)
                }
            }
        }
    }
}

class BitmapShaderBrush(
    val bitmap: Bitmap,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        return BitmapShader(bitmap, PlatformShader.TileMode.CLAMP, PlatformShader.TileMode.CLAMP)
    }

}

val LocalIconShape = compositionLocalOf<Shape> { CircleShape }

fun getShape(iconShape: IconShape): Shape {
    return when (iconShape) {
        IconShape.PlatformDefault -> PlatformShape
        IconShape.Circle -> CircleShape
        IconShape.Square -> RectangleShape
        IconShape.RoundedSquare -> RoundedCornerShape(25)
        IconShape.Triangle -> TriangleShape
        IconShape.Squircle -> SquircleShape
        IconShape.Hexagon -> HexagonShape
        IconShape.Pentagon -> PentagonShape
        IconShape.Teardrop -> TeardropShape
        IconShape.Pebble -> PebbleShape
        IconShape.EasterEgg -> EasterEggShape
    }
}

@Composable
fun ProvideIconShape(iconShape: IconShape, content: @Composable () -> Unit) {
    val shape = getShape(iconShape)
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
                Math.cbrt(radiusToPow - abs(x * x * x)).toFloat()
            )
        for (x in radius.roundToInt() downTo -radius.roundToInt())
            lineTo(
                x.toFloat(),
                (-Math.cbrt(radiusToPow - abs(x * x * x))).toFloat()
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

private val TeardropShape: Shape
    get() = GenericShape { size, _ ->
        moveTo(0.5f * size.width, 0f)
        cubicTo(
            0.776f * size.width, 0f,
            size.width, 0.224f * size.height,
            size.width, 0.5f * size.height,
        )
        lineTo(
            size.width, 0.88f * size.height,
        )
        cubicTo(
            size.width, 0.946f * size.height,
            0.946f * size.width, size.height,
            0.88f * size.width, size.height,
        )
        lineTo(0.5f * size.width, size.height)
        cubicTo(
            0.224f * size.width, size.height,
            0f, 0.776f * size.height,
            0f, 0.5f * size.height,
        )
        cubicTo(
            0f, 0.224f * size.height,
            0.224f * size.width, 0f,
            0.5f * size.width, 0f,
        )
        close()
    }

private val PebbleShape: Shape
    get() = GenericShape { size, _ ->
        moveTo(0.55f * size.width, 0f * size.height)
        cubicTo(
            0.25f * size.width,
            0f * size.height,
            0f * size.width,
            0.25f * size.height,
            0f * size.width,
            0.5f * size.height
        )
        cubicTo(
            0f * size.width,
            0.78f * size.height,
            0.28f * size.width,
            1f * size.height,
            0.55f * size.width,
            1f * size.height
        )
        cubicTo(
            0.85f * size.width,
            1f * size.height,
            1f * size.width,
            0.85f * size.height,
            1f * size.width,
            0.58f * size.height
        )
        cubicTo(
            1f * size.width,
            0.3f * size.height,
            0.86f * size.width,
            0f * size.height,
            0.55f * size.width,
            0f * size.height
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