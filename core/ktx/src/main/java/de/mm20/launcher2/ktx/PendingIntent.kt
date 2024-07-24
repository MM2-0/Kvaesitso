package de.mm20.launcher2.ktx

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context

fun PendingIntent.sendWithBackgroundPermission(context: Context) {
    if (isAtLeastApiLevel(34)) {
        val options = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            .toBundle()
        send(context, 0, null, null, null, null, options)
    } else {
        send(context, 0, null)
    }
}