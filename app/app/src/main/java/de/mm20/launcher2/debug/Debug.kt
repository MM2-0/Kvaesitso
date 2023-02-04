package de.mm20.launcher2.debug

import android.os.StrictMode
import android.util.Log

// This class does nothing in release builds
fun initDebugMode() {
    Log.d("MM20", "MM20Launcher2 is running in debug mode.")
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectAll()
            .penaltyLog()
            .build()
    )
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectAll()
            .penaltyLog()
            .build()
    )
}
