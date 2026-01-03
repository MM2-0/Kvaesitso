package de.mm20.launcher2.appshortcuts

import android.content.Context
import android.content.IntentSender
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import androidx.core.content.getSystemService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.Collator

class AppShortcutConfigActivity(
    private val launcherActivityInfo: LauncherActivityInfo,
): Comparable<AppShortcutConfigActivity> {
    val label = launcherActivityInfo.label.toString()

    fun getIcon(context: Context): Flow<Drawable?> = flow {
        val icon = launcherActivityInfo.getIcon(context.resources.displayMetrics.densityDpi)
        emit(icon)
    }
    fun getIntent(context: Context): IntentSender? {
        val launcherApps = context.getSystemService<LauncherApps>()!!
        return launcherApps.getShortcutConfigActivityIntent(launcherActivityInfo)
    }

    override fun compareTo(other: AppShortcutConfigActivity): Int {

        val label1 = label
        val label2 = other.label
        return Collator.getInstance().apply { strength = Collator.SECONDARY }
            .compare(label1, label2)
    }
}