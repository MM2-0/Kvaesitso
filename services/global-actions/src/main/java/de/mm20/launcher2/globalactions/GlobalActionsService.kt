package de.mm20.launcher2.globalactions

import android.accessibilityservice.AccessibilityService
import android.content.Context

class GlobalActionsService(private val context: Context) {
    fun openNotificationDrawer() {
        try {
            expandNotificationPanel()
        } catch (e: Exception) {
            LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        }
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

    private fun expandNotificationPanel() {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val method = statusBarManager.getMethod("expandNotificationsPanel")
        method.invoke(statusBarService)
    }

    private fun expandQuickSettings() {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val method = statusBarManager.getMethod("expandSettingsPanel")
        method.invoke(statusBarService)
    }

}