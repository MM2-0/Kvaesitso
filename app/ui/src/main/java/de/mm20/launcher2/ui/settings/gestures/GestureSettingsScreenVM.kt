package de.mm20.launcher2.ui.settings.gestures

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.BaseLayout
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.ui.GestureSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GestureSettingsScreenVM : ViewModel(), KoinComponent {
    private val gestureSettings: GestureSettings by inject()
    private val uiSettings: UiSettings by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val iconService: IconService by inject()

    val hasPermission = permissionsManager.hasPermission(PermissionGroup.Accessibility)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val allowWidgetGesture = uiSettings.homeScreenWidgets.map { it == false }
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

    fun setSwipeDown(action: GestureAction) {
        gestureSettings.setSwipeDown(action)
    }

    fun setSwipeLeft(action: GestureAction) {
        gestureSettings.setSwipeLeft(action)
    }

    fun setSwipeRight(action: GestureAction) {
        gestureSettings.setSwipeRight(action)
    }

    fun setSwipeUp(action: GestureAction) {
        gestureSettings.setSwipeUp(action)
    }

    fun setDoubleTap(action: GestureAction) {
        gestureSettings.setDoubleTap(action)
    }

    fun setLongPress(action: GestureAction) {
        gestureSettings.setLongPress(action)
    }

    fun setHomeButton(action: GestureAction) {
        gestureSettings.setHomeButton(action)
    }

    val swipeLeftApp: Flow<SavableSearchable?> = swipeLeft
        .flatMapLatest {
            if (it !is GestureAction.Launch || it.key == null) flowOf(null)
            else searchableRepository.getByKeys(listOf(it.key!!)).map {
                it.firstOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10000), null)

    fun setSwipeLeftApp(searchable: SavableSearchable?) {
        searchable?.let { searchableRepository.insert(it) } ?: return
        setSwipeLeft(GestureAction.Launch(searchable.key))
    }

    val swipeRightApp: Flow<SavableSearchable?> = swipeRight
        .flatMapLatest {
            if (it !is GestureAction.Launch || it.key == null) flowOf(null)
            else searchableRepository.getByKeys(listOf(it.key!!)).map {
                it.firstOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10000), null)

    fun setSwipeRightApp(searchable: SavableSearchable?) {
        searchable?.let { searchableRepository.insert(it) } ?: return
        setSwipeRight(GestureAction.Launch(searchable.key))
    }

    val swipeDownApp: Flow<SavableSearchable?> = swipeDown
        .flatMapLatest {
            if (it !is GestureAction.Launch || it.key == null) flowOf(null)
            else searchableRepository.getByKeys(listOf(it.key!!)).map {
                it.firstOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10000), null)

    fun setSwipeDownApp(searchable: SavableSearchable?) {
        searchable?.let { searchableRepository.insert(it) } ?: return
        setSwipeDown(GestureAction.Launch(searchable.key))
    }

    val swipeUpApp: Flow<SavableSearchable?> = swipeUp
        .flatMapLatest {
            if (it !is GestureAction.Launch || it.key == null) flowOf(null)
            else searchableRepository.getByKeys(listOf(it.key!!)).map {
                it.firstOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10000), null)

    fun setSwipeUpApp(searchable: SavableSearchable?) {
        searchable?.let { searchableRepository.insert(it) } ?: return
        setSwipeUp(GestureAction.Launch(searchable.key))
    }

    val longPressApp: Flow<SavableSearchable?> = longPress
        .flatMapLatest {
            if (it !is GestureAction.Launch || it.key == null) flowOf(null)
            else searchableRepository.getByKeys(listOf(it.key!!)).map {
                it.firstOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10000), null)

    fun setLongPressApp(searchable: SavableSearchable?) {
        searchable?.let { searchableRepository.insert(it) } ?: return
        setLongPress(GestureAction.Launch(searchable.key))
    }

    val doubleTapApp: Flow<SavableSearchable?> = doubleTap
        .flatMapLatest {
            if (it !is GestureAction.Launch || it.key == null) flowOf(null)
            else searchableRepository.getByKeys(listOf(it.key!!)).map {
                it.firstOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10000), null)

    fun setDoubleTapApp(searchable: SavableSearchable?) {
        searchable?.let { searchableRepository.insert(it) } ?: return
        setDoubleTap(GestureAction.Launch(searchable.key))
    }

    val homeButtonApp: Flow<SavableSearchable?> = homeButton
        .flatMapLatest {
            if (it !is GestureAction.Launch || it.key == null) flowOf(null)
            else searchableRepository.getByKeys(listOf(it.key!!)).map {
                it.firstOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10000), null)

    fun setHomeButtonApp(searchable: SavableSearchable?) {
        searchable?.let { searchableRepository.insert(it) } ?: return
        setHomeButton(GestureAction.Launch(searchable.key))
    }


    fun requestPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Accessibility)
    }

    fun getIcon(searchable: SavableSearchable?, size: Int): Flow<LauncherIcon?> {
        if (searchable == null) return emptyFlow()
        return iconService.getIcon(searchable, size)
    }
}