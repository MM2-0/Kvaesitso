package de.mm20.launcher2.ui.legacy.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import de.mm20.launcher2.ui.R

class AspectRationImageView : AppCompatImageView {

    var aspectRatio = 1f
    var fixedHeight = false

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes) {
        attrs?.let {
            val ta = context.theme.obtainStyledAttributes(it, R.styleable.AspectRatioImageView, 0, defStyleRes)
            aspectRatio = ta.getFloat(R.styleable.AspectRatioImageView_aspectRatio, 1f)
            fixedHeight = ta.getBoolean(R.styleable.AspectRatioImageView_fixedHeight, false)
            ta.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (fixedHeight) {
            setMeasuredDimension((measuredHeight / aspectRatio).toInt(), measuredHeight)
        } else {
            setMeasuredDimension(measuredWidth, (measuredWidth * aspectRatio).toInt())
        }
    }
}