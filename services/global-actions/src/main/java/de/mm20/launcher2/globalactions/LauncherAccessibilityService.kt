package de.mm20.launcher2.globalactions

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import de.mm20.launcher2.permissions.PermissionsManager
import org.koin.android.ext.android.inject
import java.lang.ref.WeakReference

class LauncherAccessibilityService: AccessibilityService() {

    private val permissionManager: PermissionsManager by inject()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

    }

    override fun onInterrupt() {

    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        permissionManager.reportAccessibilityServiceState(true)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        permissionManager.reportAccessibilityServiceState(false)
        instance = null
        return super.onUnbind(intent)
    }

    companion object {
        private var instance: WeakReference<LauncherAccessibilityService>? = null
        internal fun getInstance(): LauncherAccessibilityService? {
            return instance?.get()
        }
    }
}