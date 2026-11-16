package de.mm20.launcher2.wallpapers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ktx.PrivilegedShell
import de.mm20.launcher2.preferences.ui.WallpaperSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

/**
 * Android drops a live wallpaper when its host APK is replaced. Files in
 * `filesDir/wallpapers/` survive; the system component binding does not.
 * Static images set via WallpaperManager also survive unless a live wallpaper
 * was covering them and then vanished.
 */
object WallpaperRestorer {
    private const val CHANNEL = "telos-wallpaper"
    private const val NOTIFY_ID = 7142

    fun markWanted(context: Context) {
        runCatching {
            GlobalContext.get().get<WallpaperSettings>().setLiveWallpaperWanted(true)
        }
    }

    suspend fun restoreAfterUpgrade(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val service = runCatching { GlobalContext.get().get<WallpapersService>() }.getOrNull() ?: return@withContext
        val wanted = runCatching {
            GlobalContext.get().get<WallpaperSettings>().liveWallpaperWanted.first()
        }.getOrDefault(service.hasVideoWallpaper())
        if (service.hasVideoWallpaper() && !service.isVideoWallpaperActive() && wanted) {
            if (!tryBindLiveWallpaper(app)) {
                notifyRestore(app, service)
            }
            return@withContext
        }
        if (!service.isVideoWallpaperActive()) {
            runCatching { GlassWallpaperStore(app).restoreStaticIfPresent() }
        }
    }

    private suspend fun tryBindLiveWallpaper(context: Context): Boolean {
        val component = ComponentName(context, VideoWallpaperService::class.java)
        val hidden = runCatching {
            val method = WallpaperManager::class.java.getDeclaredMethod("setWallpaperComponent", ComponentName::class.java)
            method.isAccessible = true
            method.invoke(WallpaperManager.getInstance(context), component)
            true
        }.getOrDefault(false)
        if (hidden && WallpapersService(context).isVideoWallpaperActive()) return true
        val flattened = component.flattenToShortString()
        val shell = PrivilegedShell.run("cmd wallpaper set --live $flattened")
            ?: PrivilegedShell.run("service call wallpaper 2 s16 $flattened")
        return shell?.exitCode == 0 && WallpapersService(context).isVideoWallpaperActive()
    }

    private fun notifyRestore(context: Context, service: WallpapersService) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(I18nR.string.wallpaper_restore_channel), NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val pending = PendingIntent.getActivity(
            context,
            NOTIFY_ID,
            service.getActivationIntent().addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(context.getString(I18nR.string.wallpaper_restore_title))
            .setContentText(context.getString(I18nR.string.wallpaper_restore_text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFY_ID, notification) }
    }
}
