package de.mm20.launcher2.ui.launcher.modals

import android.content.Context
import android.content.Intent
import android.content.Intent.ShortcutIconResource
import android.content.pm.LauncherApps
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.appshortcuts.AppShortcutRepository
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeRepository
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.LauncherShortcut
import de.mm20.launcher2.search.data.LegacyShortcut
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EditFavoritesSheetVM : ViewModel(), KoinComponent {

    private val repository: FavoritesRepository by inject()
    private val shortcutRepository: AppShortcutRepository by inject()
    private val iconRepository: IconRepository by inject()
    private val badgeRepository: BadgeRepository by inject()
    private val permissionsManager: PermissionsManager by inject()

    val gridItems = MutableLiveData<List<FavoritesSheetGridItem>>(emptyList())

    val loading = MutableLiveData(false)

    val createShortcutTarget = MutableLiveData<FavoritesSheetSection?>(null)

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

        items.add(FavoritesSheetGridItem.Divider(FavoritesSheetSection.ManuallySorted))
        if (manuallySorted.isEmpty()) {
            items.add(FavoritesSheetGridItem.EmptySection)
        } else {
            items.addAll(manuallySorted.map { FavoritesSheetGridItem.Favorite(it) })
            items.add(FavoritesSheetGridItem.Spacer())
        }

        items.add(FavoritesSheetGridItem.Divider(FavoritesSheetSection.AutomaticallySorted))
        if (automaticallySorted.isEmpty()) {
            items.add(FavoritesSheetGridItem.EmptySection)
        } else {
            items.addAll(automaticallySorted.map { FavoritesSheetGridItem.Favorite(it) })
            items.add(FavoritesSheetGridItem.Spacer())
        }

        items.add(FavoritesSheetGridItem.Divider(FavoritesSheetSection.FrequentlyUsed))
        if (frequentlyUsed.isEmpty()) {
            items.add(FavoritesSheetGridItem.EmptySection)
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
        save()
        buildItemList()
    }

    private fun save() {
        repository.updateFavorites(
            buildList {
                addAll(manuallySorted)
            },
            buildList {
                addAll(automaticallySorted)
            },
        )
    }

    fun getIcon(searchable: Searchable, size: Int): Flow<LauncherIcon?> {
        return iconRepository.getIcon(searchable, size)
    }

    fun getBadge(searchable: Searchable): Flow<Badge?> {
        return badgeRepository.getBadge(searchable)
    }

    fun pickShortcut(section: FavoritesSheetSection) {
        createShortcutTarget.value = section
    }

    fun cancelPickShortcut() {
        createShortcutTarget.value = null
    }

    fun getShortcutActivities() = flow {
        emit(shortcutRepository.getShortcutsConfigActivities())
    }

    val hasShortcutPermission = permissionsManager.hasPermission(PermissionGroup.AppShortcuts)

    fun requestShortcutPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.AppShortcuts)
    }

    fun createShortcut(context: Context, data: Intent?) {
        data ?: return cancelPickShortcut()

        val shortcut = AppShortcut.fromPinRequestIntent(context, data)

        if (shortcut == null) {
            cancelPickShortcut()
            return
        }

        if (!manuallySorted.any { it.key == shortcut.key }
            && !automaticallySorted.any { it.key == shortcut.key }
            && !frequentlyUsed.any { it.key == shortcut.key }
        ) {
            if (createShortcutTarget.value == FavoritesSheetSection.ManuallySorted) {
                manuallySorted.add(shortcut)
            } else {
                automaticallySorted.add(shortcut)
            }
        }
        save()
        buildItemList()
        createShortcutTarget.value = null
    }

    fun remove(key: String) {
        val gridItems = gridItems.value?.toMutableList() ?: return
        val item = gridItems.find { it is FavoritesSheetGridItem.Favorite && it.item.key == key } as FavoritesSheetGridItem.Favorite?
        if (item != null) {
            repository.removeFromFavorites(item.item)
            gridItems.remove(item)
            this.gridItems.value = gridItems
        }
    }


}