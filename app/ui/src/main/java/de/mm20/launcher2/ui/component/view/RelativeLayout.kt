package de.mm20.launcher2.ui.component.view

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.constraintlayout.compose.ConstrainedLayoutReference
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintLayoutScope
import androidx.core.view.children

@Composable
internal fun ComposeRelativeLayout(
    view: RelativeLayout,
    modifier: Modifier,
) {
    ConstraintLayout(
        modifier = modifier,
    ) {
        val refs = view.children.filterNot { it.id == View.NO_ID }.associate { it.id to createRef() }
        for (child in view.children) {
            ComposeAndroidView(
                child,
                modifier =  Modifier.relativeLayoutChild(
                    this@ConstraintLayout,
                    child.layoutParams,
                    child.id,
                    refs,
                )
            )
        }
    }
}

private fun Modifier.relativeLayoutChild(
    scope: ConstraintLayoutScope,
    params: ViewGroup.LayoutParams,
    id: Int,
    refs: Map<Int, ConstrainedLayoutReference>
) = this then
        Modifier.layoutParams(params) then
        with(scope) {
            if (params !is RelativeLayout.LayoutParams) return@with Modifier
            val ref = refs[id] ?: return@with Modifier
            Modifier.constrainAs(ref) {
                val above = refs[params.getRule(RelativeLayout.ABOVE)]
                if (above != null) {
                    bottom.linkTo(above.top)
                }

                val alignBaseline = refs[params.getRule(RelativeLayout.ALIGN_BASELINE)]
                if (alignBaseline != null) {
                    baseline.linkTo(alignBaseline.baseline)
                }

                val alignBottom = refs[params.getRule(RelativeLayout.ALIGN_BOTTOM)]
                if (alignBottom != null) {
                    bottom.linkTo(alignBottom.bottom)
                }

                val alignEnd = refs[params.getRule(RelativeLayout.ALIGN_END)]
                if (alignEnd != null) {
                    end.linkTo(alignEnd.end)
                }

                val alignLeft = refs[params.getRule(RelativeLayout.ALIGN_LEFT)]
                if (alignLeft != null) {
                    absoluteLeft.linkTo(alignLeft.absoluteLeft)
                }

                val alignParentBottom = params.getRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                if (alignParentBottom == RelativeLayout.TRUE) {
                    bottom.linkTo(parent.bottom)
                }

                val alignParentEnd = params.getRule(RelativeLayout.ALIGN_PARENT_END)
                if (alignParentEnd == RelativeLayout.TRUE) {
                    end.linkTo(parent.end)
                }

                val alignParentLeft = params.getRule(RelativeLayout.ALIGN_PARENT_LEFT)
                if (alignParentLeft == RelativeLayout.TRUE) {
                    absoluteLeft.linkTo(parent.absoluteLeft)
                }

                val alignParentRight = params.getRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                if (alignParentRight == RelativeLayout.TRUE) {
                    absoluteRight.linkTo(parent.absoluteRight)
                }

                val alignParentStart = params.getRule(RelativeLayout.ALIGN_PARENT_START)
                if (alignParentStart == RelativeLayout.TRUE) {
                    start.linkTo(parent.start)
                }

                val alignParentTop = params.getRule(RelativeLayout.ALIGN_PARENT_TOP)
                if (alignParentTop == RelativeLayout.TRUE) {
                    top.linkTo(parent.top)
                }

                val alignRight = refs[params.getRule(RelativeLayout.ALIGN_RIGHT)]
                if (alignRight != null) {
                    absoluteRight.linkTo(alignRight.absoluteRight)
                }

                val alignStart = refs[params.getRule(RelativeLayout.ALIGN_START)]
                if (alignStart != null) {
                    start.linkTo(alignStart.start)
                }

                val alignTop = refs[params.getRule(RelativeLayout.ALIGN_TOP)]
                if (alignTop != null) {
                    top.linkTo(alignTop.top)
                }

                val below = refs[params.getRule(RelativeLayout.BELOW)]
                if (below != null) {
                    top.linkTo(below.bottom)
                }

                val centerHorizontal = params.getRule(RelativeLayout.CENTER_HORIZONTAL)
                if (centerHorizontal == RelativeLayout.TRUE) {
                    centerHorizontallyTo(parent)
                }

                val centerInParent = params.getRule(RelativeLayout.CENTER_IN_PARENT)
                if (centerInParent == RelativeLayout.TRUE) {
                    centerTo(parent)
                }

                val centerVertical = params.getRule(RelativeLayout.CENTER_VERTICAL)
                if (centerVertical == RelativeLayout.TRUE) {
                    centerVerticallyTo(parent)
                }

                val toEndOf = refs[params.getRule(RelativeLayout.END_OF)]
                if (toEndOf != null) {
                    start.linkTo(toEndOf.end)
                }

                val toLeftOf = refs[params.getRule(RelativeLayout.LEFT_OF)]
                if (toLeftOf != null) {
                    absoluteRight.linkTo(toLeftOf.absoluteLeft)
                }

                val toRightOf = refs[params.getRule(RelativeLayout.RIGHT_OF)]
                if (toRightOf != null) {
                    absoluteLeft.linkTo(toRightOf.absoluteRight)
                }

                val toStartOf = refs[params.getRule(RelativeLayout.START_OF)]
                if (toStartOf != null) {
                    end.linkTo(toStartOf.start)
                }
            }
        }