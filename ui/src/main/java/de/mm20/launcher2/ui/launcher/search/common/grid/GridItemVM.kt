package de.mm20.launcher2.ui.launcher.search.common.grid

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAndroidRect
import androidx.core.app.ActivityOptionsCompat
import de.mm20.launcher2.badges.BadgeRepository
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GridItemVM(
    private val searchable: Searchable
): SearchableItemVM(searchable)