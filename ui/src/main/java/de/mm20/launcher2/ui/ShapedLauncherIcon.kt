package de.mm20.launcher2.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.icons.PlaceholderIcon
import de.mm20.launcher2.ui.icons.getPlaceholderIcon
import de.mm20.launcher2.ui.ktx.conditional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun ShapedLauncherIcon(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    item: Searchable,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val iconSize = size.toPixels().toInt()

    var icon by remember {
        mutableStateOf<LauncherIcon?>(null)
    }

    LaunchedEffect(item) {
        icon = withContext(Dispatchers.IO) {
            item.loadIconAsync(context, iconSize)
        }
    }

    val placeholderIcon = item.getPlaceholderIcon()

    ShapedLauncherIcon(
        modifier = modifier,
        size = size,
        icon = icon,
        placeholder = placeholderIcon,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShapedLauncherIcon(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    icon: LauncherIcon?,
    placeholder: PlaceholderIcon,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val iconShape = LocalLauncherIconShape.current

    val interactionSource = remember { MutableInteractionSource() }

    val isPressed by interactionSource.collectIsPressedAsState()

    val fgScale by animateFloatAsState(if (isPressed) 0.75f else 1f)
    val bgScale by animateFloatAsState(if (isPressed) 1.25f else 1f)

    Surface(
        shape = iconShape,
        elevation = animateDpAsState(if (isPressed) 4.dp else 1.dp).value,
        modifier = modifier
            .requiredSize(size)
    ) {
        Box(
            modifier = Modifier
                .requiredSize(size)
                .background(
                    color = if (icon == null) {
                        placeholder.color.copy(alpha = 0.4f).compositeOver(MaterialTheme.colors.surface)
                    } else {
                        Color.Gray
                    }
                )
                .conditional(
                    onClick != null || onLongClick != null, Modifier.combinedClickable(
                        onClick = {
                            onClick?.invoke()
                        },
                        onLongClick = {
                            onLongClick?.invoke()
                        },
                        interactionSource = interactionSource,
                        indication = LocalIndication.current
                    )
                )
        ) {
            if (icon == null) {
                Icon(
                    imageVector = placeholder.icon, contentDescription = null,
                    tint = placeholder.color,
                    modifier = Modifier
                        .scale(fgScale)
                        .align(Alignment.Center)
                )
            } else {

                val fg = icon.foreground
                val bg = icon.background

                Canvas(
                    modifier = Modifier
                        .size(size)
                        .align(Alignment.Center)
                ) {
                    drawIntoCanvas {
                        val actualSize = size.toPx() * icon.backgroundScale * bgScale
                        val offset = (size.toPx() - actualSize) / 2
                        bg?.setBounds(
                            offset.toInt(),
                            offset.toInt(),
                            (offset + actualSize).toInt(),
                            (offset + actualSize).toInt()
                        )
                        bg?.draw(it.nativeCanvas)
                    }
                }
                Canvas(
                    modifier = Modifier
                        .size(size)
                        .align(Alignment.Center)
                ) {
                    drawIntoCanvas {
                        val actualSize = size.toPx() * icon.foregroundScale * fgScale
                        val offset = (size.toPx() - actualSize) / 2
                        fg.setBounds(
                            offset.toInt(),
                            offset.toInt(),
                            (offset + actualSize).toInt(),
                            (offset + actualSize).toInt()
                        )
                        fg.draw(it.nativeCanvas)
                    }
                }
            }
        }
    }
}

/*private fun getSystemShape(): AndroidPath? {
    return if (isAtLeastApiLevel(Build.VERSION_CODES.O)) {
        AdaptiveIconDrawable(null, null).iconMask
    } else {
        null
    }
}

private fun getIconShape(shape: LauncherIconShape): Shape {
    return when (shape) {
        LauncherIconShape.Circle -> CircleShape
        LauncherIconShape.Square -> RectangleShape
        LauncherIconShape.RoundedSquare -> RoundedCornerShape(13)
        LauncherIconShape.Hexagon -> GenericShape {
            moveTo(it.width * 0.25f, it.height * 0.933f)
            lineTo(it.width * 0.75f, it.height * 0.933f)
            lineTo(it.width * 1.0f, it.height * 0.5f)
            lineTo(it.width * 0.75f, it.height * 0.067f)
            lineTo(it.width * 0.25f, it.height * 0.067f)
            lineTo(0f, it.height * 0.5f)
            close()
        }
        LauncherIconShape.PlatformDefault -> {
            val platformShape = getSystemShape() ?: return CircleShape
            GenericShape {
                val matrix = AndroidMatrix()
                val bounds = RectF()
                platformShape.computeBounds(bounds, true)
                matrix.setRectToRect(bounds, RectF(0f, 0f, it.width, it.height), AndroidMatrix.ScaleToFit.CENTER)
                platformShape.transform(matrix)
                addPath(platformShape.asComposePath())
            }
        }
        else -> CircleShape
    }
}*/