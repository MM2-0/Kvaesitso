package de.mm20.launcher2.globalactions

import android.accessibilityservice.AccessibilityService

class GlobalActionsService {
    fun openNotificationDrawer() {
        LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun lockScreen() {
        LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    }

    fun openQuickSettings() {
        LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }

    fun openPowerDialog() {
        LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
    }

    fun openRecents() {
        LauncherAccessibilityService.getInstance()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }
}