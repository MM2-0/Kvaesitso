package de.mm20.launcher2.ui.legacy.helper

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroupOverlay
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.ref.WeakReference

object ActivityStarter : KoinComponent {

    private val favoritesRepository: FavoritesRepository by inject()
    private lateinit var overlayView: WeakReference<ViewGroupOverlay>
    private lateinit var rootView: WeakReference<ViewGroup>

    fun create(rootView: ViewGroup) {
        ActivityStarter.rootView = WeakReference(rootView)
        overlayView = WeakReference(rootView.overlay)
    }

    fun start(
        context: Context,
        transitionView: View,
        item: Searchable? = null,
        intent: Intent? = null,
        pendingIntent: PendingIntent? = null
    ): Boolean {
        if (!startActivity(context, item, intent, pendingIntent, transitionView)) return false

        return true
    }

    private fun startActivity(
        context: Context,
        item: Searchable? = null,
        intent: Intent? = null,
        pendingIntent: PendingIntent? = null,
        sourceView: View
    ): Boolean {
        val pos = intArrayOf(0, 0)
        sourceView.getLocationOnScreen(pos)
        val sourceBounds =
            Rect(pos[0], pos[1], pos[0] + sourceView.width, pos[1] + sourceView.height)

        val bundle = getActivityOptions(sourceView, sourceBounds).toBundle()

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
                favoritesRepository.incrementLaunchCounter(item)
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

    private fun getActivityOptions(
        sourceView: View,
        sourceBounds: Rect?
    ): ActivityOptionsCompat {
        return ActivityOptionsCompat.makeClipRevealAnimation(
            sourceView,
            0,
            0,
            sourceView.width,
            sourceView.height
        )

    }


}

interface ActivityStarterCallback {
    fun onResume()
}