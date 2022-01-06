package de.mm20.launcher2.notifications

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.graphics.drawable.Drawable
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeProvider
import de.mm20.launcher2.music.MusicRepository
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherPreferences
import org.koin.android.ext.android.inject
import java.lang.ref.WeakReference

class NotificationService : NotificationListenerService() {

    private val musicRepository: MusicRepository by inject()

    private val badgeProvider: BadgeProvider by inject()
    private val permissionsManager: PermissionsManager by inject()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("MM20", "Notification listener connected")
        permissionsManager.reportNotificationListenerState(true)
        instance = WeakReference(this)
        val notifications = getNotifications().sortedBy { it.postTime }
        for (n in notifications) {
            /*val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MUSIC) }
            if (packageManager.queryIntentActivities(intent, 0).none { it.activityInfo.packageName == n.packageName }) continue*/
            val token = n.notification.extras[NotificationCompat.EXTRA_MEDIA_SESSION] as? MediaSession.Token
                    ?: continue
            musicRepository.setMediaSession(MediaSessionCompat.Token.fromToken(token), n.packageName)
        }
        if (LauncherPreferences.instance.notificationBadges) {
            generateBadges()
        }
    }

    fun getNotifications(): Array<StatusBarNotification> {
        return try {
            activeNotifications
        } catch (e: SecurityException) {
            emptyArray()
        }
    }

    fun generateBadges() {
        badgeProvider.removeNotificationBadges()
        getNotifications().forEach {
            val pkg = it.packageName
            val badge = badgeProvider.getBadge("app://$pkg") ?: Badge()
            badge.number = activeNotifications.filter {
                it.packageName == pkg
            }.sumBy {
                it.notification.number
            }
            badgeProvider.setBadge("app://$pkg", badge)
        }
    }

    fun getNotifications(packageName: String): List<StatusBarNotification> {
        return getNotifications().filter { it.packageName == packageName }
    }

    private fun getLargeIcon(notification: Notification): Drawable? {
        return notification.getLargeIcon()?.loadDrawable(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.notification.category == Notification.CATEGORY_TRANSPORT || sbn.notification.category == Notification.CATEGORY_SERVICE) {
            /*val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MUSIC) }
            if (packageManager.queryIntentActivities(intent, 0).none { it.activityInfo.packageName == sbn.packageName }) return*/
            val token = sbn.notification.extras[NotificationCompat.EXTRA_MEDIA_SESSION] as? MediaSession.Token
                    ?: return
            musicRepository.setMediaSession(MediaSessionCompat.Token.fromToken(token), sbn.packageName)
        }
        if (LauncherPreferences.instance.notificationBadges) {
            val pkg = sbn.packageName
            val badge = badgeProvider.getBadge("app://$pkg") ?: Badge()
            badge.number = activeNotifications.filter { it.packageName == pkg }.sumBy {
                it.notification.number
            }
            badgeProvider.setBadge("app://$pkg", badge)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        if (LauncherPreferences.instance.notificationBadges) {
            val pkg = sbn.packageName
            if (getNotifications().any { it.packageName == pkg && it.id != sbn.id }) {
                val badge = badgeProvider.getBadge("app://$pkg") ?: Badge()
                badge.number = activeNotifications.filter { it.packageName == pkg }.sumBy {
                    it.notification.number
                }
                badgeProvider.setBadge("app://$pkg", badge)
            } else {
                badgeProvider.removeBadge("app://$pkg")
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        badgeProvider.removeNotificationBadges()
        permissionsManager.reportNotificationListenerState(false)
        Log.d("MM20", "Notification listener disconnected")
    }

    companion object {
        private var instance: WeakReference<NotificationService>? = null
        fun getInstance(): NotificationService? {
            return instance?.get()
        }
    }
}