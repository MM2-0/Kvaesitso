package de.mm20.launcher2.debug

import android.os.StrictMode
import android.util.Log
import de.mm20.launcher2.BuildConfig

class Debug {
    init {
        Log.d("MM20", "MM20Launcher2 is running in debug mode.")
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build())
    }
    companion object {
        const val DEBUG_MODE = true
    }
}