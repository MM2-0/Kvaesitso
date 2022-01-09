package de.mm20.launcher2.crashreporter

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CancellationException

object CrashReporter {
    fun logException(e: Exception) {
        if (e !is CancellationException) {
            com.balsikandar.crashreporter.CrashReporter.logException(e)
        }
        Log.e("MM20", Log.getStackTraceString(e))
    }

    fun getLaunchIntent(): Intent {
        return com.balsikandar.crashreporter.CrashReporter.getLaunchIntent()
    }
}