package de.mm20.launcher2.ui.legacy.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.bartoszlipinski.viewpropertyobjectanimator.ViewPropertyObjectAnimator
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.toRectF
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.IconSettings.IconShape
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.helper.BitmapHolder
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.lang.Math.pow
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

class LauncherIconView : View, KoinComponent {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleRes
    )

    var shape: IconShape
        set(value) {
            if (value == IconShape.PlatformDefault) {
                platformShape = getSystemShape()
                transformMatrix = Matrix()
                platformShapeBounds = RectF()
                field = value
            } else {
                platformShape = null
                transformMatrix = null
                platformShapeBounds = null
                field = value
            }
        }

    private var platformShape: Path? = null
    private var transformMatrix: Matrix? = null
    private var platformShapeBounds: RectF? = null


    @RequiresApi(Build.VERSION_CODES.O)
    private fun getSystemShape(): Path {
        return AdaptiveIconDrawable(null, null).iconMask
    }

    var icon: LauncherIcon? = null
        set(value) {
            field = value
            foregroundScale = value?.foregroundScale ?: 1f
            backgroundScale = value?.backgroundScale ?: 1f
            value?.registerCallback(iconObserver)
            invalidate()
        }

    var badge: Badge? = null
        set(value) {
            field = value
            invalidate()
        }

    private val iconObserver: (LauncherIcon) -> Unit = {
        foregroundScale = it.foregroundScale
        backgroundScale = it.backgroundScale
        // Implicit invalidate
    }

    var foregroundScale = 1f
        set(value) {
            field = value
            postInvalidate()
        }

    var backgroundScale = 1f
        set(value) {
            field = value
            postInvalidate()
        }

    init {
        shape = IconShape.Circle
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }


    override fun setElevation(elevation: Float) {
        super.setElevation(elevation)
        shadowPaint = updateShadowPaint()
        badgeShadowPaint = updateBadgeShadowPaing()
    }

    override fun setTranslationZ(translationZ: Float) {
        super.setTranslationZ(translationZ)
        shadowPaint = updateShadowPaint()
        badgeShadowPaint = updateBadgeShadowPaing()
    }

    private var shadowPaint: Paint = updateShadowPaint()

    private var badgeShadowPaint = updateBadgeShadowPaing()

    private fun updateShadowPaint(): Paint {
        return Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
            color = Color.TRANSPARENT
            isAntiAlias = true
            setShadowLayer(0.5f * z, 0f, 0.5f * z, 0x40000000)
        }
    }

    private fun updateBadgeShadowPaing(): Paint {
        return Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            color = Color.TRANSPARENT
            isAntiAlias = true
            setShadowLayer(0.5f * z, 0f, 0.5f * z, 0x40000000)
        }
    }

    private val drawRect = Rect()
    private val bmpDrawRect = Rect()

    private val maskPaint = Paint().apply {
        style = Paint.Style.FILL
        color = 0xFF000000.toInt()
        isAntiAlias = true
    }

    private val bitmapPaint = Paint().apply {
        color = 0xFF000000.toInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        isAntiAlias = true
        isFilterBitmap = true
    }

    private val badgePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.badge)
        isAntiAlias = true
    }

    private val badgeTextPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.badge_text)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val badgeProgressPaint = Paint().apply {
        color = 0x30000000
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private var path: Path = Path()

    private val badgeRect = RectF()
    private val textBounds = Rect()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val fg = icon?.foreground ?: return
        val bg = icon?.background
        canvas.getClipBounds(drawRect)
        drawRect.left += paddingLeft
        drawRect.top += paddingTop
        drawRect.right -= paddingRight
        drawRect.bottom -= paddingBottom
        val (bmp, c) = BitmapHolder.getBitmapAndCanvas((drawRect.width() * 1.8).toInt())
        c.getClipBounds(bmpDrawRect)
        fg.bounds = bmpDrawRect
        if (bg != null) {
            bg.bounds = bmpDrawRect
            when (shape) {
                IconShape.PlatformDefault -> {
                    path.rewind()
                    val matrix = transformMatrix!!
                    val bounds = platformShapeBounds!!
                    val shape = platformShape!!
                    shape.computeBounds(bounds, true)
                    matrix.setRectToRect(
                        bounds,
                        badgeRect.also { drawRect.toRectF(it) },
                        Matrix.ScaleToFit.CENTER
                    )
                    path.rewind()
                    shape.transform(matrix, path)
                    canvas.drawPath(path, maskPaint)
                }
                IconShape.Circle -> {
                    canvas.drawOval(
                        drawRect.left.toFloat(),
                        drawRect.top.toFloat(),
                        drawRect.right.toFloat(),
                        drawRect.bottom.toFloat(),
                        maskPaint
                    )
                }
                IconShape.Square -> {
                    canvas.drawRect(drawRect, maskPaint)
                }
                IconShape.RoundedSquare -> {
                    canvas.drawRoundRect(
                        drawRect.left.toFloat(),
                        drawRect.top.toFloat(),
                        drawRect.right.toFloat(),
                        drawRect.bottom.toFloat(),
                        width * 0.125f,
                        height * 0.125f,
                        maskPaint
                    )
                }
                IconShape.Triangle -> {
                    path.rewind()
                    var cx = drawRect.left.toFloat()
                    var cy = drawRect.top + drawRect.height().toFloat() * 0.86f
                    val r = drawRect.width()
                    path.moveTo(cx, cy)
                    path.arcTo(cx - r, cy - r, cx + r, cy + r, 300f, 60f, true)
                    canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 300f, 60f, true, maskPaint)
                    cx = drawRect.right.toFloat()
                    cy = drawRect.top + drawRect.height().toFloat() * 0.86f
                    path.lineTo(cx, cy)
                    path.arcTo(cx - r, cy - r, cx + r, cy + r, 180f, 60f, true)
                    canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 180f, 60f, true, maskPaint)
                    cx = drawRect.left + drawRect.width() * 0.5f
                    cy = drawRect.top.toFloat()
                    path.lineTo(cx, cy)
                    path.close()
                    path.arcTo(cx - r, cy - r, cx + r, cy + r, 60f, 60f, true)
                    canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 60f, 60f, true, maskPaint)

                }
                IconShape.Squircle -> {
                    path.rewind()
                    val radius = drawRect.width() / 2
                    val radiusToPow = pow(radius.toDouble(), 3.0)
                    path.moveTo(-radius.toFloat(), 0f)
                    for (x in -radius..radius)
                        path.lineTo(
                            x.toFloat(),
                            Math.cbrt(radiusToPow - Math.abs(x * x * x)).toFloat()
                        )
                    for (x in radius downTo -radius)
                        path.lineTo(
                            x.toFloat(),
                            (-Math.cbrt(radiusToPow - Math.abs(x * x * x))).toFloat()
                        )
                    path.close()
                    canvas.save()
                    canvas.translate(width / 2f, height / 2f)
                    canvas.drawPath(path, maskPaint)
                    canvas.restore()
                }
                IconShape.Hexagon -> {
                    path.rewind()
                    path.moveTo(
                        drawRect.left + drawRect.width() * 0.25f,
                        drawRect.top + drawRect.height() * 0.933f
                    )
                    path.lineTo(
                        drawRect.left + drawRect.width() * 0.75f,
                        drawRect.top + drawRect.height() * 0.933f
                    )
                    path.lineTo(
                        drawRect.left + drawRect.width() * 1.0f,
                        drawRect.top + drawRect.height() * 0.5f
                    )
                    path.lineTo(
                        drawRect.left + drawRect.width() * 0.75f,
                        drawRect.top + drawRect.height() * 0.067f
                    )
                    path.lineTo(
                        drawRect.left + drawRect.width() * 0.25f,
                        drawRect.top + drawRect.height() * 0.067f
                    )
                    path.lineTo(drawRect.left.toFloat(), drawRect.top + drawRect.height() * 0.5f)
                    path.close()
                    canvas.drawPath(path, maskPaint)
                }
                IconShape.EasterEgg -> {
                    path.rewind()
                    path.moveTo(
                        0.49999999f * drawRect.width() + drawRect.left,
                        1f * drawRect.height() + drawRect.top
                    )
                    path.lineTo(
                        0.42749999f * drawRect.width() + drawRect.left,
                        0.9339999999999999f * drawRect.height() + drawRect.top
                    )
                    path.cubicTo(
                        0.16999998f * drawRect.width() + drawRect.left,
                        0.7005004f * drawRect.height() + drawRect.top,
                        0f + drawRect.left,
                        0.5460004f * drawRect.height() + drawRect.top,
                        0f + drawRect.left,
                        0.3575003f * drawRect.height() + drawRect.top
                    )
                    path.cubicTo(
                        0f + drawRect.left,
                        0.2030004f * drawRect.height() + drawRect.top,
                        0.12100002f * drawRect.width() + drawRect.left,
                        0.0825004f * drawRect.height() + drawRect.top,
                        0.275f * drawRect.width() + drawRect.left,
                        0.0825004f * drawRect.height() + drawRect.top
                    )
                    path.cubicTo(
                        0.362f * drawRect.width() + drawRect.left,
                        0.0825004f * drawRect.height() + drawRect.top,
                        0.4455f * drawRect.width() + drawRect.left,
                        0.123f * drawRect.height() + drawRect.top,
                        0.5f * drawRect.width() + drawRect.left,
                        0.1865003f * drawRect.height() + drawRect.top
                    )
                    path.cubicTo(
                        0.55449999f * drawRect.width() + drawRect.left,
                        0.123f * drawRect.height() + drawRect.top,
                        0.638f * drawRect.width() + drawRect.left,
                        0.0825f * drawRect.height() + drawRect.top,
                        0.725f * drawRect.width() + drawRect.left,
                        0.0825f * drawRect.height() + drawRect.top
                    )
                    path.cubicTo(
                        0.87900006f * drawRect.width() + drawRect.left,
                        0.0825004f * drawRect.height() + drawRect.top,
                        1f * drawRect.width() + drawRect.left,
                        0.2030004f * drawRect.height() + drawRect.top,
                        1f * drawRect.width() + drawRect.left,
                        0.3575003f * drawRect.height() + drawRect.top
                    )
                    path.cubicTo(
                        1f * drawRect.width() + drawRect.left,
                        0.5460004f * drawRect.height() + drawRect.top,
                        0.82999999f * drawRect.width() + drawRect.left,
                        0.7005004f * drawRect.height() + drawRect.top,
                        0.57250001f * drawRect.width() + drawRect.left,
                        0.9340004f * drawRect.height() + drawRect.top
                    )
                    path.close()
                    canvas.drawPath(path, maskPaint)
                }
                IconShape.Pentagon -> {
                    path.rewind()
                    path.moveTo(
                        0.49997027f * drawRect.width() + drawRect.left,
                        0.0060308f * drawRect.height() + drawRect.top
                    )
                    path.lineTo(
                        0.99994053f * drawRect.width() + drawRect.left,
                        0.36928048f * drawRect.height() + drawRect.top
                    )
                    path.lineTo(
                        0.80896887f * drawRect.width() + drawRect.left,
                        0.95703078f * drawRect.height() + drawRect.top
                    )
                    path.lineTo(
                        0.19097162f * drawRect.width() + drawRect.left,
                        0.95703076f * drawRect.height() + drawRect.top
                    )
                    path.lineTo(
                        drawRect.left.toFloat(),
                        0.36928045f * drawRect.height() + drawRect.top
                    )
                    path.close()
                    canvas.drawPath(path, maskPaint)
                }
            }
            c.save()
            c.scale(
                backgroundScale,
                backgroundScale,
                bmpDrawRect.centerX().toFloat(),
                bmpDrawRect.centerY().toFloat()
            )
            bg.draw(c)
            c.restore()
        }
        c.save()
        c.scale(
            foregroundScale,
            foregroundScale,
            bmpDrawRect.centerX().toFloat(),
            bmpDrawRect.centerY().toFloat()
        )
        fg.draw(c)
        c.restore()
        if (bg != null) {
            canvas.drawBitmap(bmp, bmpDrawRect, drawRect, bitmapPaint)
        } else {
            canvas.drawBitmap(bmp, bmpDrawRect, drawRect, maskPaint)
        }
        if (bg != null) {
            when (shape) {
                IconShape.Circle -> {
                    canvas.drawOval(
                        drawRect.left.toFloat(),
                        drawRect.top.toFloat(),
                        drawRect.right.toFloat(),
                        drawRect.bottom.toFloat(),
                        shadowPaint
                    )
                }
                IconShape.Square -> {
                    canvas.drawRect(drawRect, shadowPaint)
                }
                IconShape.RoundedSquare -> {
                    canvas.drawRoundRect(
                        drawRect.left.toFloat(),
                        drawRect.top.toFloat(),
                        drawRect.right.toFloat(),
                        drawRect.bottom.toFloat(),
                        width * 0.125f,
                        height * 0.125f,
                        shadowPaint
                    )
                }
                IconShape.Triangle, IconShape.Hexagon, IconShape.EasterEgg, IconShape.Pentagon, IconShape.PlatformDefault -> {
                    canvas.drawPath(path, shadowPaint)
                }
                IconShape.Squircle -> {
                    canvas.save()
                    canvas.translate(width / 2f, height / 2f)
                    canvas.drawPath(path, shadowPaint)
                    canvas.restore()
                }
            }
        }

        val badgeSize = drawRect.width() * 0.30f
        badgeRect.left = drawRect.right - badgeSize
        badgeRect.top = drawRect.bottom - badgeSize
        badgeRect.right = drawRect.right.toFloat()
        badgeRect.bottom = drawRect.bottom.toFloat()

        val badge = badge ?: return
        val badgeNumber = badge.number
        val badgeProgress = badge.progress
        val badgeIcon = badge.icon ?: badge.iconRes?.let { ContextCompat.getDrawable(context, it) }

        badgePaint.color = icon?.badgeColor ?: 0
        canvas.drawOval(badgeRect, badgeShadowPaint)
        canvas.drawOval(badgeRect, badgePaint)

        badgeProgress?.let {
            canvas.drawArc(badgeRect, 270f, it * 360, true, badgeProgressPaint)
        }
        badgeIcon?.let {
            it.setBounds(
                (drawRect.right - badgeSize * 0.9f).toInt(),
                (drawRect.bottom - badgeSize * 0.9f).toInt(),
                (drawRect.right - badgeSize * 0.1f).toInt(),
                (drawRect.bottom - badgeSize * 0.1f).toInt()
            )
            it.setBounds(
                badgeRect.left.roundToInt(),
                badgeRect.top.roundToInt(),
                badgeRect.right.roundToInt(),
                badgeRect.bottom.roundToInt()
            )
            it.draw(canvas)
            return
        }
        badgeNumber?.takeIf { it in 1..99 }?.let {
            val text = it.toString()
            val textSize = (1f - 0.1f - text.length * 0.1f) * badgeSize
            badgeTextPaint.textSize = textSize
            badgeTextPaint.getTextBounds(text, 0, text.length, textBounds)
            canvas.drawText(
                it.toString(),
                badgeRect.centerX(),
                badgeRect.centerY() - textBounds.exactCenterY(),
                badgeTextPaint
            )
        }
    }

    private var longClicked = false
    private val longClickRunnable = Runnable {
        longClicked = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        performLongClick()
    }

    private var downX = 0f
    private var downY = 0f


    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!hasOnClickListeners()) return false
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                animateTouchDown()
                downX = ev.rawX
                downY = ev.rawY
                longClicked = false
                handler?.postDelayed(
                    longClickRunnable,
                    ViewConfiguration.getLongPressTimeout().toLong()
                )
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(hypot(downX - ev.rawX, downY - ev.rawY)) > width * 0.25f) {
                    handler?.removeCallbacks(longClickRunnable)
                    animateTouchUp()
                    return false
                }
            }
            MotionEvent.ACTION_UP -> {
                animateTouchUp()
                if (ev.x > 0 && ev.x < width && ev.y > 0 && ev.y < height && !longClicked) {
                    performClick()
                }
                handler?.removeCallbacks(longClickRunnable)
                return false
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                animateTouchUp()
                handler?.removeCallbacks(longClickRunnable)
                return false
            }
        }
        return true
    }

    private fun animateTouchUp() {
        AnimatorSet().also {
            it.playTogether(
                ViewPropertyObjectAnimator.animate(this).translationZ(0f).get(),
                ObjectAnimator.ofFloat(this, "foregroundScale", icon?.foregroundScale ?: 1f),
                ObjectAnimator.ofFloat(this, "backgroundScale", icon?.backgroundScale ?: 1f)
            )
            it.duration = 300
            it.start()
        }
    }

    private fun animateTouchDown() {
        AnimatorSet().also {
            it.playTogether(
                ViewPropertyObjectAnimator.animate(this).translationZ(2 * dp).get(),
                ObjectAnimator.ofFloat(
                    this, "foregroundScale", (icon?.foregroundScale
                        ?: 1f) * 0.8f
                ),
                ObjectAnimator.ofFloat(
                    this, "backgroundScale", (icon?.backgroundScale
                        ?: 1f) * 1.2f
                )
            )
            it.duration = 250
            it.start()
        }
    }


    companion object: KoinComponent {

        var currentShape: IconShape = IconShape.PlatformDefault

        fun getDefaultShape(): Flow<IconShape> = channelFlow {
            send(currentShape)
            val dataStore: LauncherDataStore = get()
            dataStore.data.map { it.icons.shape }.distinctUntilChanged().collectLatest { shape ->
                dataStore.data.map { it.easterEgg }.distinctUntilChanged().collectLatest { ee ->
                    if (ee) {
                        currentShape = IconShape.EasterEgg
                        send(IconShape.EasterEgg)
                    } else {
                        currentShape = shape
                        send(shape)
                    }
                }
            }
        }
    }
}

