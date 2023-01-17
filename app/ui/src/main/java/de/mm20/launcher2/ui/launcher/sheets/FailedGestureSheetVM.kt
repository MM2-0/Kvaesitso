package de.mm20.launcher2.ui.launcher.sheets

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.GestureSettings.GestureAction
import de.mm20.launcher2.ui.gestures.Gesture
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FailedGestureSheetVM : ViewModel(), KoinComponent {
    private val permissionsManager: PermissionsManager by inject()
    private val dataStore: LauncherDataStore by inject()

    fun requestPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Accessibility)
    }

    fun disableGesture(gesture: Gesture) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setGestures(
                    it.gestures.toBuilder().apply {
                        when (gesture) {
                            Gesture.SwipeDown -> swipeDown = GestureAction.None
                            Gesture.SwipeLeft -> swipeLeft = GestureAction.None
                            Gesture.SwipeRight -> swipeRight = GestureAction.None
                            Gesture.DoubleTap -> doubleTap = GestureAction.None
                            Gesture.LongPress -> longPress = GestureAction.None
                        }
                    }.build()
                ).build()
            }
        }
    }
}