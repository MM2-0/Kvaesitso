package de.mm20.launcher2.legacy.helper

import android.animation.AnimatorSet
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroupOverlay
import android.view.animation.AccelerateInterpolator
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import com.bartoszlipinski.viewpropertyobjectanimator.ViewPropertyObjectAnimator
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.preferences.AppStartAnimation
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.ref.WeakReference

object ActivityStarter: KoinComponent {

    val favoritesRepository: FavoritesRepository by inject()

    private var initialized = false
    private lateinit var overlayView: WeakReference<ViewGroupOverlay>
    private lateinit var rootView: WeakReference<ViewGroup>

    const val ANIM_SPLASH1 = 0
    const val ANIM_SPLASH2 = 1
    const val ANIM_M = 2
    const val ANIM_FADE = 3
    const val ANIM_SLIDE_BOTTOM = 4

    private lateinit var animationStyle: AppStartAnimation

    fun create(rootView: ViewGroup) {
        this.rootView = WeakReference(rootView)
        this.overlayView = WeakReference(rootView.overlay)
        onResumeCallback = null
        animationStyle = LauncherPreferences.instance.appStartAnim
        initialized = true
    }

    fun start(context: Context, transitionView: View, item: Searchable? = null, intent: Intent? = null, pendingIntent: PendingIntent? = null): Boolean {
        if (!initialized) throw IllegalStateException("Item starter has not been initialized properly.")

        if (!startActivity(context, item, intent, pendingIntent, transitionView)) return false

        if (animationStyle == AppStartAnimation.SLIDE_BOTTOM || animationStyle == AppStartAnimation.FADE ||
                animationStyle == AppStartAnimation.M) {
            return true
        }

        val rootView = rootView.get() ?: return true
        val background = rootView.findViewById<View>(R.id.activityStartOverlay)
        background.pivotX = background.width * 0.5f
        background.pivotY = background.height * 0.5f
        val searchView = rootView.findViewById<View>(R.id.container)
        val parent = transitionView.parent as ViewGroup
        val index = parent.indexOfChild(transitionView)
        overlayView.get()?.add(transitionView)
        val bounds = Rect()
        transitionView.getGlobalVisibleRect(bounds)
        val scale = (rootView.width).toFloat() / transitionView.width
        //val x = bounds.left.toFloat()
        //val y = bounds.top.toFloat()
        val x = (rootView.width - transitionView.width) * 0.5f - transitionView.x
        val y = (rootView.height - transitionView.height) * 0.5f - transitionView.y

        background.visibility = View.VISIBLE
        background.scaleX = transitionView.width.toFloat() / background.width
        background.scaleY = transitionView.height.toFloat() / background.height
        background.translationX = bounds.exactCenterX() - background.width * 0.5f
        background.translationY = bounds.exactCenterY() - background.height * 0.5f

        AnimatorSet().apply {
            playTogether(
                    ViewPropertyObjectAnimator.animate(background)
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationX(0f)
                            .translationY(0f)
                            .setDuration(200)
                            .setInterpolator(AccelerateInterpolator(0.8f))
                            .get(),
                    ViewPropertyObjectAnimator.animate(transitionView)
                            .scaleX(scale)
                            .scaleY(scale)
                            .alpha(0f)
                            .translationX(x)
                            .translationY(y)
                            .setDuration(200)
                            .setInterpolator(AccelerateInterpolator(0.8f))
                            .get(),
                    ViewPropertyObjectAnimator.animate(searchView)
                            .scaleX(0.8f)
                            .scaleY(0.8f)
                            .alpha(0f)
                            .get()
            )
        }.start()
        onResumeCallback = {
            transitionView.translationX = 0f
            transitionView.translationY = 0f
            transitionView.scaleX = 1f
            transitionView.scaleY = 1f
            transitionView.alpha = 1f
            searchView.scaleX = 1f
            searchView.scaleY = 1f
            searchView.alpha = 1f
            background.scaleX = 1f
            background.scaleY = 1f
            background.translationX = 0f
            background.translationY = 0f
            overlayView.get()?.remove(transitionView)
            background.visibility = View.INVISIBLE
            parent.addView(transitionView, index)
        }

        return true
    }

    private var onResumeCallback: (() -> Unit)? = null

    private fun startActivity(context: Context, item: Searchable? = null, intent: Intent? = null, pendingIntent: PendingIntent? = null, sourceView: View): Boolean {
        val pos = intArrayOf(0, 0)
        sourceView.getLocationOnScreen(pos)
        val sourceBounds = Rect(pos[0], pos[1], pos[0] + sourceView.width, pos[1] + sourceView.height)

        val bundle = getActivityOptions(context, sourceView, sourceBounds)?.toBundle()

        if (pendingIntent != null) {
            return try {
                pendingIntent.send()
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
        }

        if (item != null) {
            if (item.launch(context, bundle)) {
                favoritesRepository.incrementLaunchCount(item)
                return true
            }
            return false
        }

        val i = intent ?: return false

        return if (i.resolveActivity(context.packageManager) != null) {
            context.startActivity(i, bundle)
            true
        } else {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun getActivityOptions(context: Context, sourceView: View, sourceBounds: Rect?): ActivityOptionsCompat? {
        return when (animationStyle) {
            AppStartAnimation.FADE -> ActivityOptionsCompat.makeCustomAnimation(context, R.anim.activity_start_fade_enter, R.anim.activity_start_fade_exit)
            AppStartAnimation.SLIDE_BOTTOM -> ActivityOptionsCompat.makeCustomAnimation(context, R.anim.activity_start_slide_bottom_enter, R.anim.activity_start_slide_bottom_exit)
            AppStartAnimation.M -> sourceBounds?.let { ActivityOptionsCompat.makeClipRevealAnimation(sourceView, 0, 0, sourceView.width, sourceView.height) }
            else -> ActivityOptionsCompat.makeCustomAnimation(context, R.anim.activity_start_splash2_enter, R.anim.activity_start_splash2_exit)
        }
    }

    fun pause() {
    }

    fun resume() {
        onResumeCallback?.invoke()
        onResumeCallback = null
        val it = callbacks.iterator()
        while (it.hasNext()) {
            val callbackRef = it.next()
            val callback = callbackRef.get()
            if (callback == null) {
                it.remove()
                continue
            }
            callback.onResume()
        }
    }

    fun destroy() {
        onResumeCallback = null
    }

    /**
     * Returns true when an animation is running
     */
    fun isStarting(): Boolean {
        return onResumeCallback != null
    }

    private val callbacks = mutableSetOf<WeakReference<ActivityStarterCallback>>()

    fun registerCallback(callback: ActivityStarterCallback) {
        callbacks.add(WeakReference(callback))
    }

}

interface ActivityStarterCallback {
    fun onResume()
}