package de.mm20.launcher2.ui.launcher.sheets

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.appshortcuts.AppShortcutRepository
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeService
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.appshortcuts.AppShortcut
import de.mm20.launcher2.preferences.search.FavoritesSettings
import de.mm20.launcher2.search.Searchable
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.searchable.PinnedLevel
import de.mm20.launcher2.services.favorites.FavoritesService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.Collator

class EditFavoritesSheetVM : ViewModel(), KoinComponent {

    private val favoritesService: FavoritesService by inject()
    private val shortcutRepository: AppShortcutRepository by inject()
    private val iconService: IconService by inject()
    private val badgeService: BadgeService by inject()
    private val customAttributesRepository: CustomAttributesRepository by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val favoritesSettings: FavoritesSettings by inject()

    val gridItems = mutableStateOf<List<FavoritesSheetGridItem>>(emptyList())

    val loading = mutableStateOf(false)

    val createShortcutTarget = mutableStateOf<FavoritesSheetSection?>(null)

    private var manuallySorted: MutableList<SavableSearchable> = mutableListOf()
    private var automaticallySorted: MutableList<SavableSearchable> = mutableListOf()
    private var frequentlyUsed: MutableList<SavableSearchable> = mutableListOf()

    val pinnedTags = mutableStateOf<List<Tag>>(emptyList())
    val availableTags = mutableStateOf<List<Tag>>(emptyList())

    suspend fun reload(showLoadingIndicator: Boolean = true) {
        loading.value = showLoadingIndicator
        manuallySorted = mutableListOf()
        manuallySorted = favoritesService.getFavorites(
            minPinnedLevel = PinnedLevel.ManuallySorted,
            excludeTypes = listOf("tag"),
        ).first().toMutableList()
        automaticallySorted = favoritesService.getFavorites(
            minPinnedLevel = PinnedLevel.AutomaticallySorted,
            maxPinnedLevel = PinnedLevel.AutomaticallySorted,
            excludeTypes = listOf("tag"),
        ).first().toMutableList()
        frequentlyUsed = favoritesService.getFavorites(
            minPinnedLevel = PinnedLevel.FrequentlyUsed,
            maxPinnedLevel = PinnedLevel.FrequentlyUsed,
            excludeTypes = listOf("tag"),
        ).first().toMutableList()
        val pinnedTags = favoritesService.getFavorites(
            includeTypes = listOf("tag"),
            minPinnedLevel = PinnedLevel.AutomaticallySorted,
        ).first().filterIsInstance<Tag>().toMutableList()

        val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }

        availableTags.value =
            customAttributesRepository
                .getAllTags()
                .first()
                .filter {t -> pinnedTags.none { it.tag == t } }
                .sortedWith { el1, el2 ->
                    collator.compare(el1, el2)
                }
                .map { Tag(it) }
        this.pinnedTags.value = pinnedTags

        createShortcutTarget.value = null

        buildItemList()
        loading.value = false
    }

    private fun buildItemList() {
        val items = mutableListOf<FavoritesSheetGridItem>()

        items.add(FavoritesSheetGridItem.Tags)

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
        favoritesService.updateFavorites(
            manuallySorted = buildList {
                pinnedTags.value?.let { addAll(it) }
                addAll(manuallySorted)
            },
            automaticallySorted = buildList {
                addAll(automaticallySorted)
            },
        )
    }

    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(searchable, size)
    }

    fun getBadge(searchable: Searchable): Flow<Badge?> {
        return badgeService.getBadge(searchable)
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

        val shortcut = AppShortcut(context, data)

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
        val item =
            gridItems.find { it is FavoritesSheetGridItem.Favorite && it.item.key == key } as FavoritesSheetGridItem.Favorite?
        if (item != null) {
            favoritesService.reset(item.item)
            automaticallySorted.removeAll { it.key == item.item.key }
                    || manuallySorted.removeAll { it.key == item.item.key }
                    || frequentlyUsed.removeAll { it.key == item.item.key }
            buildItemList()
        }
    }

    val enableFrequentlyUsed = favoritesSettings.frequentlyUsed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setFrequentlyUsed(frequentlyUsed: Boolean) {
        favoritesSettings.setFrequentlyUsed(frequentlyUsed)
    }

    val frequentlyUsedRows = favoritesSettings.frequentlyUsedRows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)
    fun setFrequentlyUsedRows(frequentlyUsedRows: Int) {
        favoritesSettings.setFrequentlyUsedRows(frequentlyUsedRows)
    }

    fun pinTag(tag: Tag) {
        val pinned = pinnedTags.value.toMutableList()
        pinned.add(tag)
        val available = availableTags.value
        availableTags.value = available.filter { it.tag != tag.tag }
        pinnedTags.value = pinned.distinctBy { it.tag }
        save()
    }

    fun unpinTag(tag: Tag) {
        val pinned = pinnedTags.value.toMutableList()
        val available = availableTags.value
        availableTags.value = (available + tag).sorted()
        pinnedTags.value = pinned.filter { it.tag != tag.tag }
        save()
    }

    fun moveTag(from: LazyListItemInfo, to: LazyListItemInfo) {
        val pinned = pinnedTags.value.toMutableList()
        val tag = pinned.removeAt(from.index)
        pinned.add(to.index, tag)
        pinnedTags.value = pinned
        save()
    }

    fun addTag(key: String?, tag: String?) {
        val gridItems = gridItems.value?.toMutableList() ?: return
        if (key == null || tag == null) return
        val item =
            gridItems.find { it is FavoritesSheetGridItem.Favorite && it.item.key == key } as FavoritesSheetGridItem.Favorite?
        if (item != null) {
            automaticallySorted.removeAll { it.key == item.item.key }
                    || manuallySorted.removeAll { it.key == item.item.key }
                    || frequentlyUsed.removeAll { it.key == item.item.key }
            buildItemList()
            customAttributesRepository.addTag(item.item, tag)
            favoritesService.unpinItem(item.item)
            viewModelScope.launch {
                frequentlyUsed = favoritesService.getFavorites(
                    minPinnedLevel = PinnedLevel.FrequentlyUsed,
                    maxPinnedLevel = PinnedLevel.FrequentlyUsed,
                    excludeTypes = listOf("tag"),
                ).first().toMutableList()
                buildItemList()
            }
        }
    }

    fun createNewTag(newTag: String) {
        pinTag(Tag(newTag))
    }

}