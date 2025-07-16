package de.mm20.launcher2.ui.utils

import android.content.Context
import android.text.format.DateFormat
import de.mm20.launcher2.preferences.TimeFormat
import de.mm20.launcher2.preferences.TimeFormat.TwentyFourHour

fun TimeFormat.isTwentyFourHours(context: Context): Boolean {
    return this == TimeFormat.TwentyFourHour || this == TimeFormat.System && DateFormat.is24HourFormat(context)
}