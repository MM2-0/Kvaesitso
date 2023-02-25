package de.mm20.launcher2.ui.launcher.gestures

import android.app.WallpaperManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.Settings.GestureSettings.GestureAction
import de.mm20.launcher2.ui.gestures.Gesture
import de.mm20.launcher2.ui.gestures.GestureHandler
import de.mm20.launcher2.ui.gestures.LocalGestureDetector
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.GestureState
import de.mm20.launcher2.ui.launcher.LauncherScaffoldVM
import de.mm20.launcher2.ui.launcher.sheets.FailedGestureSheet
import kotlin.math.absoluteValue

@Composable
fun LauncherGestureHandler() {
    val context = LocalContext.current
    val wallpaperManager = remember { WallpaperManager.getInstance(context) }
    val gestureDetector = LocalGestureDetector.current

    val viewModel: LauncherScaffoldVM = viewModel()

    val gestureState by viewModel.gestureState.collectAsState(GestureState())

    val shouldDetectDoubleTapGesture = gestureState.doubleTapAction != GestureAction.None

    LaunchedEffect(shouldDetectDoubleTapGesture) {
        gestureDetector.shouldDetectDoubleTaps = shouldDetectDoubleTapGesture
    }

    val windowToken = LocalView.current.windowToken


    val swipeThreshold = 150.dp.toPixels()
    GestureHandler(
        detector = gestureDetector,
        onDoubleTap = {
            viewModel.handleGesture(context, Gesture.DoubleTap)
        },
        onLongPress = {
            viewModel.handleGesture(context, Gesture.LongPress)
        },
        onDrag = {
            return@GestureHandler when {
                it.x > swipeThreshold && it.x.absoluteValue > it.y.absoluteValue * 2f -> {
                    viewModel.handleGesture(context, Gesture.SwipeRight)
                }

                it.x < -swipeThreshold && it.x.absoluteValue > it.y.absoluteValue * 2f -> {
                    viewModel.handleGesture(context, Gesture.SwipeLeft)
                }

                it.y > swipeThreshold && it.y.absoluteValue > it.x.absoluteValue * 2f -> {
                    viewModel.handleGesture(context, Gesture.SwipeDown)
                }

                else -> false
            }
        },
        onTap = {
            wallpaperManager.sendWallpaperCommand(
                windowToken,
                WallpaperManager.COMMAND_TAP,
                it.x.toInt(),
                it.y.toInt(),
                0,
                null
            )
        }
    )
    if (viewModel.failedGestureState != null) {
        FailedGestureSheet(
            failedGesture = viewModel.failedGestureState!!,
            onDismiss = {
                viewModel.dismissGestureFailedSheet()
            }
        )
    }
}