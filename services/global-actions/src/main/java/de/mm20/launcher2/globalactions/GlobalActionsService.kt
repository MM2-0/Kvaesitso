package de.mm20.launcher2.globalactions

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import java.lang.reflect.InvocationTargetException

class GlobalActionsService(private val context: Context) {
    fun openNotificationDrawer() {
        expandNotificationPanel()
    }

    fun lockScreen() {
        LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    }

    fun openQuickSettings() {
        try {
            expandQuickSettings()
        } catch (e: Exception) {
            LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        }
    }

    fun openPowerDialog() {
        LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
    }

    fun openRecents() {
        LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    @SuppressLint("WrongConstant")
    private fun expandNotificationPanel() {
        try {
            val statusBarService = context.getSystemService("statusbar") ?: return
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val method = statusBarManager.getMethod("expandNotificationsPanel")
            method.invoke(statusBarService)
        } catch (e: IllegalAccessException) {
            Log.e("MM20", Log.getStackTraceString(e))
        } catch (e: InvocationTargetException) {
            Log.e("MM20", Log.getStackTraceString(e))
        } catch (e: NoSuchMethodException) {
            Log.e("MM20", Log.getStackTraceString(e))
        } catch (e: ClassNotFoundException) {
            Log.e("MM20", Log.getStackTraceString(e))
        }
    }

    private fun expandQuickSettings() {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val method = statusBarManager.getMethod("expandSettingsPanel")
        method.invoke(statusBarService)
    }

}
