package de.mm20.launcher2.ui.launcher.search.common

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.geometry.Rect
import androidx.core.app.ActivityOptionsCompat
import de.mm20.launcher2.badges.BadgeService
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.searchable.SearchableRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.LauncherApp
import de.mm20.launcher2.services.favorites.FavoritesService
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class SearchableItemVM(
    private val searchable: SavableSearchable
) : KoinComponent {
    protected val favoritesService: FavoritesService by inject()
    protected val searchableRepository: SearchableRepository by inject()
    protected val badgeService: BadgeService by inject()
    protected val iconService: IconService by inject()
    protected val customAttributesRepository: CustomAttributesRepository by inject()

    val isPinned = searchableRepository.isPinned(searchable)
    fun pin() {
        favoritesService.pinItem(searchable)
    }

    fun unpin() {
        favoritesService.unpinItem(searchable)
    }

    val isHidden = searchableRepository.isHidden(searchable)
    fun hide() {
        searchableRepository.upsert(searchable, hidden = true, pinned = false)
    }

    fun unhide() {
        searchableRepository.update(searchable, hidden = false)
    }

    val badge = badgeService.getBadge(searchable)

    fun getIcon(size: Int): Flow<LauncherIcon> {
        return iconService.getIcon(searchable, size)
    }

    fun getTags(): Flow<List<String>> {
        return customAttributesRepository.getTags(searchable)
    }

    open fun launch(context: Context, bounds: Rect? = null): Boolean {
        val view = (context as? AppCompatActivity)?.window?.decorView
        val options = if (bounds != null && view != null) {
            ActivityOptionsCompat.makeScaleUpAnimation(
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
            favoritesService.reportLaunch(searchable)
            return true
        } else if (searchable is LauncherApp || searchable is AppShortcut) {
            searchableRepository.delete(searchable)
        }
        return false
    }
}