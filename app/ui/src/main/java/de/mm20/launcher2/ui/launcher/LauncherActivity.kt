package de.mm20.launcher2.ui.launcher

import android.content.Intent
import com.android.launcher3.GestureNavContract
import de.mm20.launcher2.ui.launcher.scaffold.ScaffoldPage


class LauncherActivity: SharedLauncherActivity(LauncherActivityMode.Launcher) {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val navContract = intent.let { GestureNavContract.fromIntent(it) }
        if (navContract != null) {
            val page = if (System.currentTimeMillis() - pauseTime > 5000L || pauseOnHome) {
                ScaffoldPage.Home
            } else {
                ScaffoldPage.Secondary
            }
            enterHomeTransitionManager.resolve(navContract, window, page)
        }
    }

    override fun onPause() {
        super.onPause()
        enterHomeTransitionManager.clear()
    }
}