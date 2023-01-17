package de.mm20.launcher2.ui.settings.gestures

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.GestureSettings.GestureAction
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GestureSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()
    private val permissionsManager: PermissionsManager by inject()

    val hasPermission = permissionsManager.hasPermission(PermissionGroup.Accessibility).asLiveData()

    val layout = dataStore.data.map { it.layout.baseLayout }.asLiveData()

    val swipeDown = dataStore.data.map { it.gestures.swipeDown }.asLiveData()
    val swipeLeft = dataStore.data.map { it.gestures.swipeLeft }.asLiveData()
    val swipeRight = dataStore.data.map { it.gestures.swipeRight }.asLiveData()
    val doubleTap = dataStore.data.map { it.gestures.doubleTap }.asLiveData()
    val longPress = dataStore.data.map { it.gestures.longPress }.asLiveData()

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

    fun requestPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Accessibility)
    }
}