package de.mm20.launcher2.ui.component.view

import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.children

@Composable
internal fun ComposeLinearLayout(
    view: LinearLayout,
    modifier: Modifier,
) {

    if (view.orientation == LinearLayout.VERTICAL) {
        val horizontalAlignment = when (view.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) {
            Gravity.START -> Alignment.Start
            Gravity.CENTER_HORIZONTAL -> Alignment.CenterHorizontally
            Gravity.END -> Alignment.End
            else -> Alignment.CenterHorizontally
        }
        val verticalArrangement = when (view.gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.TOP -> Arrangement.Top
            Gravity.CENTER_VERTICAL -> Arrangement.Center
            Gravity.BOTTOM -> Arrangement.Bottom
            else -> Arrangement.Top
        }
        Column(
            modifier = modifier,
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
        ) {
            for (child in view.children) {
                ComposeAndroidView(
                    child,
                    modifier = Modifier.linearLayoutChild(
                        this@Column,
                        child.layoutParams
                    )
                )
            }
        }
    } else {
        val horizontalArrangement =
            when (view.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) {
                Gravity.START -> Arrangement.Start
                Gravity.CENTER_HORIZONTAL -> Arrangement.Center
                Gravity.END -> Arrangement.End
                else -> Arrangement.Start
            }
        val verticalAlignment = when (view.gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.TOP -> Alignment.Top
            Gravity.CENTER_VERTICAL -> Alignment.CenterVertically
            Gravity.BOTTOM -> Alignment.Bottom
            else -> Alignment.CenterVertically
        }
        Row(
            modifier = modifier,
            verticalAlignment = verticalAlignment,
            horizontalArrangement = horizontalArrangement,
        ) {
            for (child in view.children) {
                ComposeAndroidView(
                    child,
                    modifier = Modifier.linearLayoutChild(
                        this@Row,
                        child.layoutParams,
                    )
                )
            }
        }
    }
}

private fun Modifier.linearLayoutChild(scope: RowScope, params: ViewGroup.LayoutParams) = this then
        Modifier.layoutParams(params) then
        with(scope) {
            if (params !is LinearLayout.LayoutParams) return@with Modifier
            val alignment = when (params.gravity and Gravity.VERTICAL_GRAVITY_MASK) {
                Gravity.TOP -> Modifier.align(Alignment.Top)
                Gravity.CENTER_VERTICAL -> Modifier.align(Alignment.CenterVertically)
                Gravity.BOTTOM -> Modifier.align(Alignment.Bottom)
                else -> Modifier
            }

            val weight = if ((params.weight) > 0f) {
                Modifier.weight(params.weight)
            } else {
                Modifier
            }

            alignment then weight
        }

private fun Modifier.linearLayoutChild(scope: ColumnScope, params: ViewGroup.LayoutParams) =
    this then
            Modifier.layoutParams(params) then
            with(scope) {
                if (params !is LinearLayout.LayoutParams) return@with Modifier
                val alignment = when (params.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) {
                    Gravity.START -> Modifier.align(Alignment.Start)
                    Gravity.CENTER_HORIZONTAL -> Modifier.align(Alignment.CenterHorizontally)
                    Gravity.END -> Modifier.align(Alignment.End)
                    else -> Modifier
                }

                val weight = if ((params.weight) > 0f) {
                    Modifier.weight(params.weight)
                } else {
                    Modifier
                }

                alignment then weight
            }
