package de.mm20.launcher2.ui.launcher.sheets

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.gestures.Gesture
import de.mm20.launcher2.ui.launcher.FailedGesture

@Composable
fun FailedGestureSheet(
    failedGesture: FailedGesture,
    onDismiss: () -> Unit,
) {
    val viewModel: FailedGestureSheetVM = viewModel()

    val actionName = stringResource(when(failedGesture.action) {
        is GestureAction.Search -> R.string.gesture_action_open_search
        is GestureAction.Notifications -> R.string.gesture_action_notifications
        is GestureAction.ScreenLock -> R.string.gesture_action_lock_screen
        is GestureAction.QuickSettings -> R.string.gesture_action_quick_settings
        is GestureAction.Recents -> R.string.gesture_action_recents
        is GestureAction.PowerMenu -> R.string.gesture_action_power_menu
        else -> R.string.gesture_action_none
    })
    val gestureName = stringResource(when(failedGesture.gesture) {
        Gesture.DoubleTap -> R.string.preference_gesture_double_tap
        Gesture.LongPress -> R.string.preference_gesture_long_press
        Gesture.SwipeDown -> R.string.preference_gesture_swipe_down
        Gesture.SwipeLeft -> R.string.preference_gesture_swipe_left
        Gesture.SwipeRight -> R.string.preference_gesture_swipe_right
        Gesture.HomeButton -> R.string.preference_gesture_home_button
    })

    BottomSheetDialog(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(it)
        ) {
            Text(
                stringResource(R.string.gesture_failed_message, gestureName, actionName),
                style = MaterialTheme.typography.bodySmall
            )
            val context = LocalLifecycleOwner.current
            MissingPermissionBanner(
                modifier = Modifier.padding(vertical = 16.dp),
                text = stringResource(id = R.string.missing_permission_accessibility_gesture_failed),
                secondaryAction = {
                    OutlinedButton(onClick = {
                        viewModel.disableGesture(failedGesture.gesture)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.turn_off))
                    }
                },
                onClick = {
                    viewModel.requestPermission(context as AppCompatActivity)
                    onDismiss()
                })
        }
    }
}