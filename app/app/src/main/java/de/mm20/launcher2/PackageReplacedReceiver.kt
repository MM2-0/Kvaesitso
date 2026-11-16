package de.mm20.launcher2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.mm20.launcher2.ui.launcher.SharedLauncherActivity
import kotlinx.coroutines.launch

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Log.i("PackageReplacedReceiver", "Telos Launcher updated! Checking default launcher status...")

        try {
            // Relaunch Telos Launcher directly on the main Home Screen post-update
            val launchIntent = Intent(context, SharedLauncherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(launchIntent)

            // Trigger background self-optimization (ART speed compile) if Shizuku/root is available
            de.mm20.launcher2.art.TelosSelfOptimizer.checkAndSelfCompileAsync(
                context = context,
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()),
            )
            val pending = goAsync()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    kotlinx.coroutines.delay(2_000)
                    de.mm20.launcher2.wallpapers.WallpaperRestorer.restoreAfterUpgrade(context)
                } finally {
                    pending.finish()
                }
            }
        } catch (e: Exception) {
            Log.e("PackageReplacedReceiver", "Failed to relaunch Telos Launcher post-update", e)
        }
    }
}
