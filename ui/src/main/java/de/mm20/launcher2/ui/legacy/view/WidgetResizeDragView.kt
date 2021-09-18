package de.mm20.launcher2.ui.legacy.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.MotionEvent.INVALID_POINTER_ID
import android.view.View
import android.widget.ImageView

class WidgetResizeDragView : ImageView {

    var resizeView: View? = null

    private var lastY = 0f

    var onResize: ((Int) -> Unit)? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val y = event.y
                lastY = y
            }
            MotionEvent.ACTION_MOVE -> {
                val y = event.y
                val dY = y - lastY
                val view = resizeView ?: return false
                val params = view.layoutParams
                val newHeight = (view.height + dY).toInt()
                params.height = newHeight
                onResize?.invoke(newHeight)
                view.layoutParams = params
            }
        }
        return true
    }
}