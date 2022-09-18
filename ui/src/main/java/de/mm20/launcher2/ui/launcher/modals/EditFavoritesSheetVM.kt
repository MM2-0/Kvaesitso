package de.mm20.launcher2.ui.launcher.modals

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeRepository
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EditFavoritesSheetVM : ViewModel(), KoinComponent {

    private val repository: FavoritesRepository by inject()
    private val iconRepository: IconRepository by inject()
    private val badgeRepository: BadgeRepository by inject()

    val gridItems = MutableLiveData<List<FavoritesSheetGridItem>>(emptyList())

    val loading = MutableLiveData(false)

    private var manuallySorted: MutableList<Searchable> = mutableListOf()
    private var automaticallySorted: MutableList<Searchable> = mutableListOf()
    private var frequentlyUsed: MutableList<Searchable> = mutableListOf()

    suspend fun reload() {
        loading.value = true
        manuallySorted = mutableListOf()
        manuallySorted = repository.getFavorites(
            manuallySorted = true
        ).first().toMutableList()
        automaticallySorted = repository.getFavorites(
            automaticallySorted = true
        ).first().toMutableList()
        frequentlyUsed = repository.getFavorites(
            frequentlyUsed = true
        ).first().toMutableList()
        buildItemList()
        loading.value = false
    }

    private fun buildItemList() {
        val items = mutableListOf<FavoritesSheetGridItem>()

        items.add(FavoritesSheetGridItem.Tags())

        items.add(FavoritesSheetGridItem.Divider(R.string.edit_favorites_dialog_pinned_sorted))
        if (manuallySorted.isEmpty()) {
            items.add(FavoritesSheetGridItem.EmptySection())
        } else {
            items.addAll(manuallySorted.map { FavoritesSheetGridItem.Favorite(it) })
            items.add(FavoritesSheetGridItem.Spacer())
        }

        items.add(FavoritesSheetGridItem.Divider(R.string.edit_favorites_dialog_pinned_unsorted))
        if (automaticallySorted.isEmpty()) {
            items.add(FavoritesSheetGridItem.EmptySection())
        } else {
            items.addAll(automaticallySorted.map { FavoritesSheetGridItem.Favorite(it) })
            items.add(FavoritesSheetGridItem.Spacer())
        }

        items.add(FavoritesSheetGridItem.Divider(R.string.edit_favorites_dialog_unpinned))
        if (frequentlyUsed.isEmpty()) {
            items.add(FavoritesSheetGridItem.EmptySection())
        } else {
            items.addAll(frequentlyUsed.map { FavoritesSheetGridItem.Favorite(it) })
            items.add(FavoritesSheetGridItem.Spacer())
        }

        gridItems.value = items
    }

    fun moveItem(from: LazyGridItemInfo, to: LazyGridItemInfo) {
        gridItems.value?.getOrNull(from.index)?.takeIf { it is FavoritesSheetGridItem.Favorite }
            ?: return
        gridItems.value?.getOrNull(to.index)
            ?.takeIf {
                it is FavoritesSheetGridItem.Favorite ||
                        it is FavoritesSheetGridItem.EmptySection ||
                        it is FavoritesSheetGridItem.Spacer
            }
            ?: return
        val manuallySortedSize = manuallySorted.size + 1
        val automaticallySortedSize = automaticallySorted.size + 1
        val item = when {
            from.index < manuallySortedSize + 2 -> {
                manuallySorted.removeAt(from.index - 2)
            }
            from.index < manuallySortedSize + automaticallySortedSize + 3 -> {
                automaticallySorted.removeAt(from.index - 3 - manuallySortedSize)
            }
            else -> {
                frequentlyUsed.removeAt(from.index - 4 - manuallySortedSize - automaticallySortedSize)
            }
        }

        when {
            to.index < manuallySortedSize + 2 -> {
                manuallySorted.add((to.index - 2).coerceAtMost(manuallySorted.size), item)
            }
            to.index < manuallySortedSize + automaticallySortedSize + 3 -> {
                automaticallySorted.add(
                    (to.index - 3 - manuallySortedSize).coerceAtMost(
                        automaticallySorted.size
                    ), item
                )
            }
            else -> {
                frequentlyUsed.add(
                    (to.index - 4 - manuallySortedSize - automaticallySortedSize).coerceAtMost(
                        frequentlyUsed.size
                    ),
                    item
                )
            }
        }
        repository.updateFavorites(
            buildList {
                addAll(manuallySorted)
            },
            buildList {
                addAll(automaticallySorted)
            },
        )
        buildItemList()
    }

    fun getIcon(searchable: Searchable, size: Int): Flow<LauncherIcon?> {
        return iconRepository.getIcon(searchable, size)
    }

    fun getBadge(searchable: Searchable): Flow<Badge?> {
        return badgeRepository.getBadge(searchable)
    }

}