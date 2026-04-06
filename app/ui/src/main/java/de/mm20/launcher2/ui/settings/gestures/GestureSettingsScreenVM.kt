package de.mm20.launcher2.ui.settings.gestures

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.WidgetScreenTarget
import de.mm20.launcher2.preferences.ui.GestureSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class GestureSettingsScreenVM : ViewModel(), KoinComponent {
    private val gestureSettings: GestureSettings by inject()
    private val uiSettings: UiSettings by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val iconService: IconService by inject()
    private val widgetRepository: WidgetRepository by inject()

    val hasPermission = permissionsManager.hasPermission(PermissionGroup.Accessibility)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val swipeDown = gestureSettings.swipeDown
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val swipeLeft = gestureSettings.swipeLeft
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val swipeRight = gestureSettings.swipeRight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val swipeUp = gestureSettings.swipeUp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val doubleTap = gestureSettings.doubleTap
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val longPress = gestureSettings.longPress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val homeButton = gestureSettings.homeButton
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setSwipeDown(action: GestureAction, searchable: SavableSearchable?) {
        saveShortcut(searchable)
        gestureSettings.setSwipeDown(action)
    }

    fun setSwipeLeft(action: GestureAction, searchable: SavableSearchable?) {
        saveShortcut(searchable)
        gestureSettings.setSwipeLeft(action)
    }

    fun setSwipeRight(action: GestureAction, searchable: SavableSearchable?) {
        saveShortcut(searchable)
        gestureSettings.setSwipeRight(action)
    }

    fun setSwipeUp(action: GestureAction, searchable: SavableSearchable?) {
        saveShortcut(searchable)
        gestureSettings.setSwipeUp(action)
    }

    fun setDoubleTap(action: GestureAction, searchable: SavableSearchable?) {
        saveShortcut(searchable)
        gestureSettings.setDoubleTap(action)
    }

    fun setLongPress(action: GestureAction, searchable: SavableSearchable?) {
        saveShortcut(searchable)
        gestureSettings.setLongPress(action)
    }

    fun setHomeButton(action: GestureAction, searchable: SavableSearchable?) {
        saveShortcut(searchable)
        gestureSettings.setHomeButton(action)
    }

    private fun saveShortcut(searchable: SavableSearchable?) {
        searchable?.let { searchableRepository.insert(it) }
    }

    val shortcutOptions: Flow<List<SavableSearchable>> = gestureSettings.flatMapLatest {
        val keys = listOfNotNull(
            (it.swipeUp as? GestureAction.Launch)?.key,
            (it.swipeLeft as? GestureAction.Launch)?.key,
            (it.swipeRight as? GestureAction.Launch)?.key,
            (it.swipeDown as? GestureAction.Launch)?.key,
            (it.doubleTap as? GestureAction.Launch)?.key,
            (it.homeButton as? GestureAction.Launch)?.key,
            (it.longPress as? GestureAction.Launch)?.key,
        )
        searchableRepository.getByKeys(keys)
    }

    val widgetOptions: Flow<List<WidgetPageOption>> =
        combine(uiSettings.homeScreenWidgets, gestureSettings) { home, gestures ->
            val options = mutableListOf<WidgetPageOption>()
            val usedTargets = setOfNotNull(
                (gestures.swipeUp as? GestureAction.Widgets)?.target,
                (gestures.swipeRight as? GestureAction.Widgets)?.target,
                (gestures.swipeLeft as? GestureAction.Widgets)?.target,
                (gestures.swipeDown as? GestureAction.Widgets)?.target,
                (gestures.doubleTap as? GestureAction.Widgets)?.target,
                (gestures.longPress as? GestureAction.Widgets)?.target,
                (gestures.homeButton as? GestureAction.Widgets)?.target,
            )
            // We want to provide at most one "new screen" option
            var newScreenOptionAdded = false
            for (target in WidgetScreenTarget.entries) {
                // Never offer the default screen as an option when widgets on home is enabled
                if (home && target == WidgetScreenTarget.Default) continue
                val widgets = widgetRepository.get(
                    parent = target.id,
                ).first()

                if (widgets.isNotEmpty() || usedTargets.contains(target)) {
                    options += WidgetPageOption(
                        widgets = widgets,
                        id = target,
                        isNewPage = false,
                    )
                } else if (!newScreenOptionAdded) {
                    options += WidgetPageOption(
                        id = target,
                        widgets = emptyList(),
                        isNewPage = true,
                    )
                    newScreenOptionAdded = true
                }
            }

            options.sortedWith { a, b ->
                if (a.isNewPage) return@sortedWith 1
                if (b.isNewPage) return@sortedWith -1
                a.id.compareTo(b.id)
            }
        }


    fun requestPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Accessibility)
    }

    fun getIcon(searchable: SavableSearchable?, size: Int): Flow<LauncherIcon?> {
        if (searchable == null) return emptyFlow()
        return iconService.getIcon(searchable, size)
    }
}

internal data class WidgetPageOption(
    val id: WidgetScreenTarget,
    val widgets: List<Widget>,
    val isNewPage: Boolean,
)