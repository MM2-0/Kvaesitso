package de.mm20.launcher2.ui.launcher.search.common

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.geometry.Rect
import androidx.core.app.ActivityOptionsCompat
import de.mm20.launcher2.badges.BadgeRepository
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.LauncherApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class SearchableItemVM(
    private val searchable: SavableSearchable
) : KoinComponent {
    protected val favoritesRepository: FavoritesRepository by inject()
    protected val badgeRepository: BadgeRepository by inject()
    protected val iconRepository: IconRepository by inject()
    protected val customAttributesRepository: CustomAttributesRepository by inject()

    val isPinned = favoritesRepository.isPinned(searchable)
    fun pin() {
        favoritesRepository.pinItem(searchable)
    }

    fun unpin() {
        favoritesRepository.unpinItem(searchable)
    }

    val isHidden = favoritesRepository.isHidden(searchable)
    fun hide() {
        favoritesRepository.hideItem(searchable)
    }

    fun unhide() {
        favoritesRepository.unhideItem(searchable)
    }

    val badge = badgeRepository.getBadge(searchable)

    fun getIcon(size: Int): Flow<LauncherIcon> {
        return iconRepository.getIcon(searchable, size)
    }

    fun getTags(): Flow<List<String>> {
        return customAttributesRepository.getTags(searchable)
    }

    open fun launch(context: Context, bounds: Rect? = null, weightFactor: Double = 0.0): Boolean {
        val view = (context as? AppCompatActivity)?.window?.decorView
        val options = if (bounds != null && view != null) {
            ActivityOptionsCompat.makeClipRevealAnimation(
                view,
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.width.toInt(),
                bounds.height.toInt()
            )
        } else {
            ActivityOptionsCompat.makeBasic()
        }
        val bundle = options.toBundle()
        if (searchable.launch(context, bundle)) {
            favoritesRepository.incrementLaunchCounter(searchable, weightFactor)
            return true
        } else if (searchable is LauncherApp || searchable is AppShortcut) {
            favoritesRepository.remove(searchable)
        }
        return false
    }
}