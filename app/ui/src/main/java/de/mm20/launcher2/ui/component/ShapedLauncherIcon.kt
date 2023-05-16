package de.mm20.launcher2.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.icons.ClockLayer
import de.mm20.launcher2.icons.ClockSublayer
import de.mm20.launcher2.icons.ClockSublayerRole
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.DynamicLauncherIcon
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.LauncherIconLayer
import de.mm20.launcher2.icons.LauncherIconRenderSettings
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TextLayer
import de.mm20.launcher2.icons.TintedClockLayer
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.icons.TransparentLayer
import de.mm20.launcher2.ktx.drawWithColorFilter
import de.mm20.launcher2.preferences.Settings.IconSettings.IconShape
import de.mm20.launcher2.ui.base.LocalTime
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.modifier.scale
import kotlinx.coroutines.launch
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
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
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
        val time = LocalTime.current
        LaunchedEffect(time) {
            currentIcon = _icon.getIcon(time)
        }
    }

    Box(
        modifier = modifier
            .size(size)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (onClick != null || onLongClick != null) {
                        Modifier.pointerInput(onClick, onLongClick) {
                            detectTapGestures(
                                onLongPress = { onLongClick?.invoke() },
                                onTap = { onClick?.invoke() },
                            )
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = currentBitmap
            val ic = currentIcon
            if (bmp != null && ic != null) {
                Canvas(
                    modifier = Modifier
                        .size(defaultIconSize)
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

                    else -> {}
                }
            } else {
                val color = MaterialTheme.colorScheme.secondaryContainer
                Canvas(
                    modifier = Modifier
                        .size(defaultIconSize)
                        .scale(size / defaultIconSize, TransformOrigin.Center)
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
                shadowElevation = 1.dp,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .size(size * 0.33f)
                    .align(Alignment.BottomEnd)
                    .then(
                        if (onClick != null || onLongClick != null) {
                            Modifier.pointerInput(onClick, onLongClick) {
                                detectTapGestures(
                                    onLongPress = { onLongClick?.invoke() },
                                    onTap = { onClick?.invoke() },
                                )
                            }
                        } else Modifier
                    ),
                color = MaterialTheme.colorScheme.secondary,
                shape = CircleShape
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {

                    _badge.progress?.let {
                        val progress by animateFloatAsState(it)
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            progress = progress,
                            strokeWidth = size / 48,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        )
                    }
                    val badgeIconRes = _badge.iconRes
                    val badgeIcon = _badge.icon

                    val number = _badge.number
                    if (badgeIconRes != null) {
                        Image(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(size / 48),
                            painter = painterResource(badgeIconRes),
                            contentDescription = null
                        )
                    } else if (badgeIcon != null) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(size / 48)
                        ) {
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

@Composable
private fun IconLayer(
    layer: LauncherIconLayer,
    size: Dp,
    colorTone: Int,
    defaultTintColor: Color
) {
    when (layer) {
        is ClockLayer -> {
            ClockLayer(
                layer.sublayers,
                scale = layer.scale,
                defaultSecond = layer.defaultSecond,
                defaultMinute = layer.defaultMinute,
                defaultHour = layer.defaultHour,
                tintColor = null
            )
        }

        is TintedClockLayer -> {
            ClockLayer(
                layer.sublayers,
                scale = layer.scale,
                defaultSecond = layer.defaultSecond,
                defaultMinute = layer.defaultMinute,
                defaultHour = layer.defaultHour,
                tintColor = if (layer.color == 0) defaultTintColor
                else Color(getTone(layer.color, colorTone))
            )

        }

        is ColorLayer -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (layer.color == 0) {
                            defaultTintColor
                        } else {
                            Color(getTone(layer.color, colorTone))
                        }
                    )
            )
        }

        is StaticIconLayer -> {
            Canvas(modifier = Modifier.fillMaxSize()) {
                withTransform({
                    this.scale(layer.scale)
                }) {
                    drawIntoCanvas {
                        layer.icon.bounds = this.size.toRect().toAndroidRect()
                        layer.icon.draw(it.nativeCanvas)
                    }
                }
            }
        }

        is TextLayer -> {
            Text(
                text = layer.text,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 20.sp * (size / 48.dp)
                ),
                color = if (layer.color == 0) {
                    defaultTintColor
                } else {
                    Color(getTone(layer.color, colorTone))
                },
            )
        }

        is TintedIconLayer -> {
            val color =
                if (layer.color == 0) defaultTintColor.toArgb()
                else getTone(layer.color, colorTone)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas {
                    layer.icon.bounds = this.size.toRect().toAndroidRect()
                    layer.icon.drawWithColorFilter(
                        it.nativeCanvas,
                        PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                    )
                }
            }
        }

        is TransparentLayer -> {}
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
) {
    val time = remember(LocalTime.current) {
        Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault())
    }

    val second = remember {
        Animatable((time.second - defaultSecond).toFloat())
    }

    val minute = remember {
        Animatable((time.minute - defaultMinute).toFloat() + (time.second - defaultSecond).toFloat() / 60f)
    }

    val hour = remember {
        Animatable((time.hour - defaultHour).toFloat() + (time.minute + defaultMinute).toFloat() / 60f)
    }

    LaunchedEffect(time) {
        val h = (time.hour - defaultHour).toFloat() + (time.minute - defaultSecond).toFloat() / 60f
        val m =
            (time.minute - defaultMinute).toFloat() + (time.second - defaultSecond).toFloat() / 60f
        val s = (time.second - defaultSecond).toFloat() + (time.nano / 1000000f) / 1000f
        second.snapTo(s)
        hour.snapTo(h)
        minute.snapTo(m)
        launch {
            hour.animateTo(h + 1.5f / 60f, tween(90000, easing = LinearEasing))
        }
        launch {
            minute.animateTo(m + 1.5f, tween(90000, easing = LinearEasing))
        }
        launch {
            second.animateTo(s + 90f, tween(90000, easing = LinearEasing))
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val colorFilter = tintColor?.let {
            PorterDuffColorFilter(tintColor.toArgb(), PorterDuff.Mode.SRC_IN)
        }
        withTransform({
            this.scale(scale)
        }) {
            for (sublayer in sublayers) {
                withTransform({
                    when (sublayer.role) {
                        ClockSublayerRole.Hour -> {
                            rotate(hour.value / 12f * 360f)
                        }

                        ClockSublayerRole.Minute -> {
                            rotate(minute.value / 60f * 360f)
                        }

                        ClockSublayerRole.Second -> {
                            rotate(second.value / 60f * 360f)
                        }

                        ClockSublayerRole.Static -> {}
                    }
                }) {
                    drawIntoCanvas {
                        sublayer.drawable.bounds = this.size.toRect().toAndroidRect()
                        sublayer.drawable.drawWithColorFilter(it.nativeCanvas, colorFilter)
                    }
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
        IconShape.UNRECOGNIZED -> CircleShape
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