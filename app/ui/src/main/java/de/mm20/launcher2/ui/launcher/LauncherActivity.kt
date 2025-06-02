package de.mm20.launcher2.ui.launcher

import android.content.Intent
import com.android.launcher3.GestureNavContract


class LauncherActivity: SharedLauncherActivity(LauncherActivityMode.Launcher) {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val navContract = intent.let { GestureNavContract.fromIntent(it) }
        if (navContract != null) {
            enterHomeTransitionManager.resolve(navContract, window)
        }
    }

    override fun onPause() {
        super.onPause()
        enterHomeTransitionManager.clear()
    }
}