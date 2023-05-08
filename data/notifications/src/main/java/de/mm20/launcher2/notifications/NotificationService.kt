package de.mm20.launcher2.notifications

import android.app.Service
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import de.mm20.launcher2.permissions.PermissionsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.lang.ref.WeakReference

class NotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

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

        scope.launch {
            val statusBarNotifications = getNotifications().sortedBy { it.postTime }
            val ranking = Ranking()
            val rankingMap = currentRanking

            val notifications = statusBarNotifications.map {
                rankingMap.getRanking(it.key, ranking)
                Notification(it, ranking)
            }

            notificationRepository.setNotifications(notifications)
        }
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        super.onNotificationRankingUpdate(rankingMap)
        scope.launch {
            val notifications = notificationRepository.getNotifications()

            val ranking = Ranking()
            val updatedNotifications = notifications.map {
                rankingMap?.getRanking(it.key, ranking)
                Notification(it, ranking)
            }

            notificationRepository.setNotifications(updatedNotifications)
        }
    }

    private fun getNotifications(): Array<StatusBarNotification> {
        return try {
            activeNotifications
        } catch (e: SecurityException) {
            emptyArray()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        notificationRepository.onNotificationRemoved(sbn.key)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        super.onNotificationPosted(sbn, rankingMap)

        val ranking = Ranking()
        rankingMap.getRanking(sbn.key, ranking)
        val notification = Notification(sbn, ranking)

        notificationRepository.onNotificationPosted(notification)
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