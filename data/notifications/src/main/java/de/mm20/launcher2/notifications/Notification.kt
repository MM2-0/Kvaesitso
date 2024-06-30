package de.mm20.launcher2.notifications

import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.os.Bundle
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

data class Notification(
    val id: Int,
    val key: String,
    val packageName: String,
    val postTime: Long,
    val isClearable: Boolean,
    val canShowBadge: Boolean,
    val number: Int,
    val color: Int,
    val smallIcon: Icon?,
    val extras: Bundle,
    val flags: Int = 0,
    val contentIntent: PendingIntent?,
) {
    constructor(
        sbn: StatusBarNotification,
        ranking: Ranking
    ) : this(
        id = sbn.id,
        key = sbn.key,
        packageName = sbn.packageName,
        postTime = sbn.postTime,
        isClearable = sbn.isClearable,
        canShowBadge = ranking.canShowBadge(),
        number = sbn.notification.number,
        color = sbn.notification.color,
        smallIcon = sbn.notification.smallIcon,
        extras = sbn.notification.extras,
        flags = sbn.notification.flags,
        contentIntent = sbn.notification.contentIntent,
    )

    constructor(
        notification: Notification,
        ranking: Ranking,
    ) : this(
        id = notification.id,
        key = notification.key,
        packageName = notification.packageName,
        postTime = notification.postTime,
        canShowBadge = ranking.canShowBadge(),
        number = notification.number,
        color = notification.color,
        isClearable = notification.isClearable,
        smallIcon = notification.smallIcon,
        extras = notification.extras,
        contentIntent = notification.contentIntent
    )

    val mediaSessionToken: MediaSession.Token?
        get() = extras.getParcelable(NotificationCompat.EXTRA_MEDIA_SESSION) as? MediaSession.Token

    val progress: Int?
        get() = if (extras.containsKey(android.app.Notification.EXTRA_PROGRESS))
            extras.getInt(NotificationCompat.EXTRA_PROGRESS)
        else null


    val progressMax: Int?
        get() = extras.getInt(NotificationCompat.EXTRA_PROGRESS_MAX).takeIf { it > 0 }

    val title: String?
        get() = extras.getString(NotificationCompat.EXTRA_TITLE)?.takeIf { it.isNotBlank() }

    val text: String?
        get() = extras.getString(NotificationCompat.EXTRA_TEXT)?.takeIf { it.isNotBlank() }

    val isGroupSummary: Boolean
        get() = flags and NotificationCompat.FLAG_GROUP_SUMMARY != 0
}