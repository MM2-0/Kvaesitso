package de.mm20.launcher2.ui.launcher.sheets

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.ui.GestureSettings
import de.mm20.launcher2.ui.launcher.scaffold.Gesture
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FailedGestureSheetVM : ViewModel(), KoinComponent {
    private val permissionsManager: PermissionsManager by inject()
    private val gestureSettings: GestureSettings by inject()

    fun requestPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Accessibility)
    }

    fun disableGesture(gesture: Gesture) {
        when(gesture) {
            Gesture.DoubleTap -> gestureSettings.setDoubleTap(GestureAction.NoAction)
            Gesture.LongPress -> gestureSettings.setLongPress(GestureAction.NoAction)
            Gesture.SwipeDown -> gestureSettings.setSwipeDown(GestureAction.NoAction)
            Gesture.SwipeLeft -> gestureSettings.setSwipeLeft(GestureAction.NoAction)
            Gesture.SwipeRight -> gestureSettings.setSwipeRight(GestureAction.NoAction)
            //Gesture.HomeButton -> gestureSettings.setHomeButton(GestureAction.NoAction)
            else -> {}
        }
    }
}