package de.mm20.launcher2.notifications

import android.app.Service
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import de.mm20.launcher2.permissions.PermissionsManager
import org.koin.android.ext.android.inject
import java.lang.ref.WeakReference

class NotificationService : NotificationListenerService() {

    private val notificationRepository: NotificationRepository by inject()

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
        notificationRepository.setNotifications(notifications)
    }

    private fun getNotifications(): Array<StatusBarNotification> {
        return try {
            activeNotifications
        } catch (e: SecurityException) {
            emptyArray()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        notificationRepository.postNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        notificationRepository.removeNotification(sbn)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        permissionsManager.reportNotificationListenerState(false)
        notificationRepository.setNotifications(emptyList())
        Log.d("MM20", "Notification listener disconnected")
    }

    companion object {
        private var instance: WeakReference<NotificationService>? = null
        internal fun getInstance(): NotificationService? {
            return instance?.get()
        }
    }
}