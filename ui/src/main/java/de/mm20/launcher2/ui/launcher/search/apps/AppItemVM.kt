package de.mm20.launcher2.ui.launcher.search.apps

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.service.notification.StatusBarNotification
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import de.mm20.launcher2.appshortcuts.AppShortcutRepository
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.notifications.NotificationRepository
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.search.data.LauncherApp
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class AppItemVM(
    private val app: Application
) : SearchableItemVM(app) {
    private val notificationRepository: NotificationRepository by inject()
    private val appShortcutRepository: AppShortcutRepository by inject()


    val notifications =
        notificationRepository.notifications.map { it.filter { it.packageName == app.`package` } }

    fun clearNotification(notification: StatusBarNotification) {
        notificationRepository.cancelNotification(notification)
    }

    fun openAppInfo(context: Context) {
        val launcherApps = context.getSystemService<LauncherApps>()!!

        if (app is LauncherApp) {
            launcherApps.startAppDetailsActivity(
                ComponentName(app.`package`, app.activity),
                app.getUser(),
                null,
                null
            )
        } else {
            context.tryStartActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.`package`}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    suspend fun shareApkFile(context: Context) {
        val launcherApps = context.getSystemService<LauncherApps>()!!
        val fileCopy = java.io.File(
            context.cacheDir,
            "${app.`package`}-${app.version}.apk"
        )
        withContext(Dispatchers.IO) {
            try {
                val user = (app as? LauncherApp)?.getUser()
                val info = if (user != null) {
                    launcherApps.getApplicationInfo(app.`package`, 0, user)
                } else {
                    context.packageManager.getApplicationInfo(app.`package`, 0)
                }
                val file = java.io.File(info.publicSourceDir)

                try {
                    file.copyTo(fileCopy, false)
                } catch (e: FileAlreadyExistsException) {
                    // Do nothing. If the file is already there we don't have to copy it again.
                }
            } catch (e: PackageManager.NameNotFoundException) {
                CrashReporter.logException(e)
            }
        }
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uri = FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".fileprovider",
            fileCopy
        )
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.type = "application/vnd.android.package-archive"
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(shareIntent, null))
        }
    }

    fun shareStoreLink(context: Context, url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(Intent.EXTRA_TEXT, url)
        shareIntent.type = "text/plain"
        context.startActivity(Intent.createChooser(shareIntent, null))
    }

    val canUninstall = app.flags and ApplicationInfo.FLAG_SYSTEM == 0 && (app as LauncherApp).getUser() == Process.myUserHandle()

    fun uninstall(context: Context) {
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = Uri.parse("package:" + app.`package`)
        context.startActivity(intent)
    }


    fun openNotification(notification: StatusBarNotification) {
        try {
            notification.notification.contentIntent.send()
        } catch (e: PendingIntent.CanceledException) {}
    }

    fun getShortcutIcon(context: Context, shortcut: ShortcutInfo) : Drawable? {
        val launcherApps = context.getSystemService<LauncherApps>() ?: return null
        return launcherApps.getShortcutIconDrawable(shortcut, 0)
    }

    val shortcuts = flow {
        if (app is LauncherApp) {
            emit(appShortcutRepository.getShortcutsForActivity(app.launcherActivityInfo, 5))
        }
    }

    fun isShortcutPinned(shortcut: AppShortcut): Flow<Boolean> {
        return favoritesRepository.isPinned(shortcut)
    }

    fun pinShortcut(shortcut: AppShortcut) {
        favoritesRepository.pinItem(shortcut)
    }

    fun unpinShortcut(shortcut: AppShortcut) {
        favoritesRepository.unpinItem(shortcut)
    }

    fun launchShortcut(context: Context, shortcut: AppShortcut) {
        shortcut.launch(context, null)
    }
}