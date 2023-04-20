package de.mm20.launcher2.ui.settings.gestures

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.searchable.SearchableRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.GestureSettings.GestureAction
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GestureSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val searchableRepository: SearchableRepository by inject()
    private val iconService: IconService by inject()

    val hasPermission = permissionsManager.hasPermission(PermissionGroup.Accessibility)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val baseLayout = dataStore.data.map { it.layout.baseLayout }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setBaseLayout(baseLayout: Settings.LayoutSettings.Layout) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setLayout(it.layout.toBuilder().setBaseLayout(baseLayout))
                    .build()
            }
        }
    }

    val swipeDown = dataStore.data.map { it.gestures.swipeDown }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val swipeLeft = dataStore.data.map { it.gestures.swipeLeft }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val swipeRight = dataStore.data.map { it.gestures.swipeRight }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val doubleTap = dataStore.data.map { it.gestures.doubleTap }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val longPress = dataStore.data.map { it.gestures.longPress }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setSwipeDown(action: GestureAction) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setGestures(it.gestures.toBuilder().setSwipeDown(action).build())
                    .build()
            }
        }
    }

    fun setSwipeLeft(action: GestureAction) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setGestures(it.gestures.toBuilder().setSwipeLeft(action).build())
                    .build()
            }
        }
    }

    fun setSwipeRight(action: GestureAction) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setGestures(it.gestures.toBuilder().setSwipeRight(action).build())
                    .build()
            }
        }
    }

    fun setDoubleTap(action: GestureAction) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setGestures(it.gestures.toBuilder().setDoubleTap(action).build())
                    .build()
            }
        }
    }

    fun setLongPress(action: GestureAction) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setGestures(it.gestures.toBuilder().setLongPress(action).build())
                    .build()
            }
        }
    }

    val swipeLeftApp: Flow<SavableSearchable?> = dataStore.data.map { it.gestures.swipeLeftApp }
        .map {
            if (it.isEmpty()) null else searchableRepository.getByKeys(listOf(it)).firstOrNull()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10000), null)

    fun setSwipeLeftApp(searchable: SavableSearchable?) {
        viewModelScope.launch {
            searchable?.let { searchableRepository.insert(it) }
            dataStore.updateData {
                it.toBuilder()
                    .setGestures(it.gestures.toBuilder()
                        .setSwipeLeftApp(searchable?.key ?: "")
                        .build()
                    )
                    .build()
            }
        }
    }

    val swipeRightApp: Flow<SavableSearchable?> = dataStore.data.map { it.gestures.swipeRightApp }
        .map {
            if (it.isEmpty()) null else searchableRepository.getByKeys(listOf(it)).firstOrNull()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10000), null)

    fun setSwipeRightApp(searchable: SavableSearchable?) {
        viewModelScope.launch {
            searchable?.let { searchableRepository.insert(it) }
            dataStore.updateData {
                it.toBuilder()
                    .setGestures(it.gestures.toBuilder()
                        .setSwipeRightApp(searchable?.key ?: "")
                        .build()
                    )
                    .build()
            }
        }
    }

    val swipeDownApp: Flow<SavableSearchable?> = dataStore.data.map { it.gestures.swipeDownApp }
        .map {
            if (it.isEmpty()) null else searchableRepository.getByKeys(listOf(it)).firstOrNull()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10000), null)

    fun setSwipeDownApp(searchable: SavableSearchable?) {
        viewModelScope.launch {
            searchable?.let { searchableRepository.insert(it) }
            dataStore.updateData {
                it.toBuilder()
                    .setGestures(it.gestures.toBuilder()
                        .setSwipeDownApp(searchable?.key ?: "")
                        .build()
                    )
                    .build()
            }
        }
    }

    val longPressApp: Flow<SavableSearchable?> = dataStore.data.map { it.gestures.longPressApp }
        .map {
            if (it.isEmpty()) null else searchableRepository.getByKeys(listOf(it)).firstOrNull()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10000), null)

    fun setLongPressApp(searchable: SavableSearchable?) {
        viewModelScope.launch {
            searchable?.let { searchableRepository.insert(it) }
            dataStore.updateData {
                it.toBuilder()
                    .setGestures(it.gestures.toBuilder()
                        .setLongPressApp(searchable?.key ?: "")
                        .build()
                    )
                    .build()
            }
        }
    }

    val doubleTapApp: Flow<SavableSearchable?> = dataStore.data.map { it.gestures.doubleTapApp }
        .map {
            if (it.isEmpty()) null else searchableRepository.getByKeys(listOf(it)).firstOrNull()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10000), null)

    fun setDoubleTapApp(searchable: SavableSearchable?) {
        viewModelScope.launch {
            searchable?.let { searchableRepository.insert(it) }
            dataStore.updateData {
                it.toBuilder()
                    .setGestures(it.gestures.toBuilder()
                        .setDoubleTapApp(searchable?.key ?: "")
                        .build()
                    )
                    .build()
            }
        }
    }



    fun requestPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Accessibility)
    }

    fun getIcon(searchable: SavableSearchable?, size: Int): Flow<LauncherIcon> {
        if (searchable == null) return emptyFlow()
        return iconService.getIcon(searchable, size)
    }
}