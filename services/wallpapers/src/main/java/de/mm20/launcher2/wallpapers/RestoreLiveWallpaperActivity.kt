package de.mm20.launcher2.wallpapers

import android.app.Activity
import android.os.Bundle

/** Explicit trampoline so the restore notification is not an implicit PendingIntent. */
class RestoreLiveWallpaperActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { startActivity(WallpapersService(this).getActivationIntent()) }
        finish()
    }
}
