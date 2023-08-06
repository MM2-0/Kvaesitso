package de.mm20.launcher2.ui.launcher

import android.content.Intent
import android.util.Log
import com.android.launcher3.GestureNavContract


class LauncherActivity: SharedLauncherActivity(LauncherActivityMode.Launcher) {
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val navContract = intent?.let { GestureNavContract.fromIntent(it) }
        if (navContract != null) {
            enterHomeTransitionManager.resolve(navContract, window)
        } else if (System.currentTimeMillis() - pausedAt < 50) {
            // If the onPause was called less than 50ms ago, we assume that the app was already
            // in the foreground when the user pressed the home button. In this case, we dispatch
            // the home button press event to the gesture detector.
            gestureDetector.dispatchHomeButtonPress()
        }
    }

    private var pausedAt: Long = 0

    override fun onPause() {
        super.onPause()
        enterHomeTransitionManager.clear()
        pausedAt = System.currentTimeMillis()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (onBackPressedDispatcher.hasEnabledCallbacks()) {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}