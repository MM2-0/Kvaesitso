package de.mm20.launcher2.ui.component.view

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.children
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

/**
 * A reimplementation of View using composables.
 * Only views that are supported by RemoteViews are supported here.
 */
@Composable
fun ComposeAndroidView(
    view: () -> View,
    modifier: Modifier = Modifier
) {
    var view_ by remember { mutableStateOf<View?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(Unit) {
        view_ = view()
        onDispose {
            view_ = null
        }
    }
    if (view_ != null) {
        ComposeAndroidView(view_!!, modifier)
    }
}

@Composable
fun ComposeAndroidView(
    view: View,
    modifier: Modifier = Modifier
) {
    if (view.visibility == View.GONE) {
        return
    }

    if (view.visibility == View.INVISIBLE) {
        Spacer(modifier = modifier)
    }

    val mod = modifier.view(view)

    when (view) {
        is FrameLayout -> ComposeFrameLayout(view, mod)
        is LinearLayout -> ComposeLinearLayout(view, mod)
        is RelativeLayout -> ComposeRelativeLayout(view, mod)
        is ImageButton -> ComposeImageButton(view, mod)
        is ListView -> ComposeListView(view, mod)
        is TextView -> ComposeTextView(view, mod)
        is ImageView -> ComposeImageView(view, mod)
        is ViewGroup -> {
            Column(modifier = mod) {
                for (child in view.children) {
                    ComposeAndroidView(
                        child,
                        modifier = Modifier.layoutParams(child.layoutParams)
                    )
                }
                Text(view.javaClass.toString(), modifier = mod)
            }
        }

        else -> Text(view.javaClass.toString(), modifier = mod)
    }
}

internal fun Modifier.view(view: View): Modifier = this then Modifier.composed {
    val density = LocalDensity.current
    val backgroundDrawable = view.background
    val background = when (backgroundDrawable) {
        is ColorDrawable -> Modifier.background(Color(backgroundDrawable.color))
        is Drawable -> Modifier.drawBehind {
            backgroundDrawable.setBounds(
                0, 0, size.width.roundToInt(), size.height.roundToInt()
            )
            backgroundDrawable.draw(this.drawContext.canvas.nativeCanvas)
        }

        else -> Modifier
    }

    val foregroundDrawable = view.foreground
    val foreground = if (foregroundDrawable != null) {
        Modifier.drawWithContent {
            drawContent()
            foregroundDrawable.setBounds(
                0, 0, size.width.roundToInt(), size.height.roundToInt()
            )
            foregroundDrawable.draw(this.drawContext.canvas.nativeCanvas)
        }
    } else Modifier

    val padding = with(density) {
        Modifier.padding(
            start = view.paddingStart.toDp(),
            top = view.paddingTop.toDp(),
            end = view.paddingEnd.toDp(),
            bottom = view.paddingBottom.toDp()
        )
    }

    val clickable = when {
        view.isClickable || view.isLongClickable -> Modifier.combinedClickable(
            enabled = view.isEnabled,
            onClick = {
                if (view.isClickable) view.performClick()
            },
            onLongClick = { view.performLongClick() }
        )

        view.isClickable -> Modifier.clickable(
            enabled = view.isEnabled,
            onClick = { view.performClick() }
        )

        else -> Modifier
    }

    val graphicsLayer = Modifier.graphicsLayer {
        translationX = view.translationX
        translationY = view.translationY
        rotationX = view.rotationX
        rotationY = view.rotationY
        rotationZ = view.rotation
        scaleX = view.scaleX
        scaleY = view.scaleY
        shadowElevation = view.elevation
        cameraDistance = view.cameraDistance
    }

    val minWidth = if (view.minimumWidth > 0)
        Modifier.widthIn(
            min = with(density) { view.minimumWidth.toDp() }
        )
    else Modifier

    val minHeight = if (view.minimumHeight > 0)
        Modifier.heightIn(
            min = with(density) { view.minimumHeight.toDp() }
        )
    else Modifier


    minWidth then minHeight then
            background then foreground then
            clickable then padding then graphicsLayer
}


internal fun Modifier.layoutParams(params: ViewGroup.LayoutParams?) = this then Modifier.composed {
    params ?: return@composed Modifier
    val density = LocalDensity.current

    val margins = if (params is ViewGroup.MarginLayoutParams) {
        with(density) {
            Modifier.padding(
                start = params.marginStart.coerceAtLeast(0).toDp(),
                top = params.topMargin.coerceAtLeast(0).toDp(),
                end = params.marginEnd.coerceAtLeast(0).toDp(),
                bottom = params.bottomMargin.coerceAtLeast(0).toDp()
            )
        }
    } else {
        Modifier
    }

    val width = when (params.width) {
        ViewGroup.LayoutParams.MATCH_PARENT -> Modifier.fillMaxWidth()
        ViewGroup.LayoutParams.WRAP_CONTENT -> Modifier.wrapContentWidth()
        else -> Modifier.width(with(density) { params.width.toDp() })
    }

    val height = when (params.height) {
        ViewGroup.LayoutParams.MATCH_PARENT -> Modifier.fillMaxHeight()
        ViewGroup.LayoutParams.WRAP_CONTENT -> Modifier.wrapContentHeight()
        else -> Modifier.height(with(density) { params.height.toDp() })
    }

    margins then width then height
}