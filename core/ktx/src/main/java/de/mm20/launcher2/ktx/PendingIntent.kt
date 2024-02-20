package de.mm20.launcher2.ktx

import android.app.ActivityOptions
import android.app.PendingIntent

fun PendingIntent.sendWithBackgroundPermission() {
    if (isAtLeastApiLevel(34)) {
        val options = ActivityOptions.makeBasic()
            .setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            .toBundle()
        send(options)
    } else {
        send()
    }
}