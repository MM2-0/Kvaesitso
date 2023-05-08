package de.mm20.launcher2.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow


class NotificationRepository {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val _notifications: MutableStateFlow<List<Notification>> = MutableStateFlow(
        emptyList()
    )

    val notifications: Flow<List<Notification>> = _notifications

    internal fun setNotifications(notifications: List<Notification>) {
        _notifications.value = notifications
    }

    internal fun getNotifications(): List<Notification> = _notifications.value

    internal fun onNotificationPosted(notification: Notification) {
        _notifications.value = _notifications.value.filter { !isEqual(it, notification) } + notification
    }

    internal fun onNotificationRemoved(key: String) {
        _notifications.value = _notifications.value.filter { it.key != key }
    }

    private fun isEqual(
        notification1: Notification,
        notification2: Notification
    ): Boolean {
        return notification1.key == notification2.key
    }

    fun cancelNotification(notification: Notification) {
        NotificationService.getInstance()?.cancelNotification(notification.key)
    }

}