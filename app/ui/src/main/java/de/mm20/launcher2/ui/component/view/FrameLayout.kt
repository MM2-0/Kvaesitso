package de.mm20.launcher2.ui.component.view

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.children


@Composable
internal fun ComposeFrameLayout(
    view: FrameLayout,
    modifier: Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopStart,
    ) {
        for (child in view.children) {
            ComposeAndroidView(
                child,
                modifier = Modifier.frameLayoutChild(
                    this@Box,
                    child.layoutParams
                )
            )
        }
    }
}

private fun Modifier.frameLayoutChild(scope: BoxScope, params: ViewGroup.LayoutParams) = this then
        Modifier.layoutParams(params) then
        with(scope) {
            if (params !is FrameLayout.LayoutParams) return@with Modifier
            val alignment = when (params.gravity and (Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK or Gravity.VERTICAL_GRAVITY_MASK)) {
                Gravity.START or Gravity.TOP -> Alignment.TopStart
                Gravity.START or Gravity.BOTTOM -> Alignment.BottomStart
                Gravity.END or Gravity.TOP -> Alignment.TopEnd
                Gravity.END or Gravity.BOTTOM -> Alignment.BottomEnd
                Gravity.START or Gravity.CENTER_VERTICAL -> Alignment.CenterStart
                Gravity.END or Gravity.CENTER_VERTICAL -> Alignment.CenterEnd
                Gravity.CENTER_HORIZONTAL or Gravity.TOP -> Alignment.TopCenter
                Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM -> Alignment.BottomCenter
                Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL -> Alignment.Center
                else -> Alignment.TopStart
            }
            Modifier.align(alignment)
        }