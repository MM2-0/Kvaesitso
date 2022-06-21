package de.mm20.launcher2.notifications

import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface NotificationRepository {
    val notifications: Flow<List<StatusBarNotification>>

    /**
     * Internal use only. Used by NotificationService.
     */
    fun setNotifications(notifications: List<StatusBarNotification>)

    /**
     * Internal use only. Used by NotificationService.
     */
    fun postNotification(notification: StatusBarNotification)

    /**
     * Internal use only. Used by NotificationService.
     */
    fun removeNotification(notification: StatusBarNotification)

    /**
     * Cancel a notification
     */
    fun cancelNotification(notification: StatusBarNotification)
}

internal class NotificationRepositoryImpl : NotificationRepository {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    override val notifications: MutableStateFlow<List<StatusBarNotification>> = MutableStateFlow(
        emptyList()
    )

    override fun setNotifications(notifications: List<StatusBarNotification>) {
        this.notifications.value = notifications
    }

    override fun postNotification(notification: StatusBarNotification) {
        notifications.value = notifications.value.filter { !isEqual(it, notification) } + notification
    }

    override fun removeNotification(notification: StatusBarNotification) {
        notifications.value = notifications.value.filter { !isEqual(it, notification) }
    }

    private fun isEqual(
        notification1: StatusBarNotification,
        notification2: StatusBarNotification
    ): Boolean {
        return notification1.key == notification2.key
    }

    override fun cancelNotification(notification: StatusBarNotification) {
        NotificationService.getInstance()?.cancelNotification(notification.key)
    }

}