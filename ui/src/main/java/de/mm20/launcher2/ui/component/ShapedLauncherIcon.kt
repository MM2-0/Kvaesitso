package de.mm20.launcher2.ui.component

import android.graphics.*
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.ktx.drawWithColorFilter
import de.mm20.launcher2.preferences.Settings.IconSettings.IconShape
import de.mm20.launcher2.ui.base.LocalTime
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import kotlinx.coroutines.launch
import palettes.TonalPalette
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

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
                .graphicsLayer {
                    clip = currentIcon?.backgroundLayer !is TransparentLayer
                    this.shape = shape
                }
                .pointerInput(null) {
                    detectTapGestures(
                        onLongPress = { onLongClick?.invoke() },
                        onTap = { onClick?.invoke() },
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            currentIcon?.let {
                IconLayer(
                    it.backgroundLayer,
                    size,
                    colorTone = if (LocalDarkTheme.current) 30 else 90,
                    MaterialTheme.colorScheme.primaryContainer
                )
                IconLayer(
                    it.foregroundLayer,
                    size,
                    colorTone = if (LocalDarkTheme.current) 90 else 10,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
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
                    .pointerInput(null) {
                        detectTapGestures(
                            onLongPress = { onLongClick?.invoke() },
                            onTap = { onClick?.invoke() },
                        )
                    },
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
private fun Badge(
    badge: () -> Badge?
) {

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
            ClockLayer(layer.sublayers, scale = layer.scale, tintColor = null)
        }
        is TintedClockLayer -> {
            ClockLayer(
                layer.sublayers,
                scale = layer.scale,
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
                withTransform({
                    this.scale(layer.scale)
                }) {
                    drawIntoCanvas {
                        layer.icon.bounds = this.size.toRect().toAndroidRect()
                        layer.icon.drawWithColorFilter(
                            it.nativeCanvas,
                            PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                        )
                    }
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
    scale: Float,
    tintColor: Color?,
) {
    val time = remember(LocalTime.current) {
        Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault())
    }

    val second = remember {
        Animatable(time.second.toFloat())
    }

    val minute = remember {
        Animatable(time.minute.toFloat() + time.second.toFloat() / 60f)
    }

    val hour = remember {
        Animatable(time.hour.toFloat() + time.minute.toFloat() / 60f)
    }

    LaunchedEffect(time) {
        val h = time.hour.toFloat() + time.minute.toFloat() / 60f
        val m = time.minute.toFloat() + time.second.toFloat() / 60f
        val s = time.second.toFloat() + (time.nano / 1000000f) / 1000f
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