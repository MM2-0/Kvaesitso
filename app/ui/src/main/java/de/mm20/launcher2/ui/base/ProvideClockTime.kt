package de.mm20.launcher2.ui.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import kotlinx.coroutines.awaitCancellation
import org.koin.androidx.compose.inject

@Composable
fun ProvideClockTime(content: @Composable () -> Unit) {

    val context = LocalContext.current
    val clockSettings: ClockWidgetSettings by inject()
    val showSeconds by clockSettings.showSeconds.collectAsState(initial = false)
    val isCompact by clockSettings.compact.collectAsState(initial = false)

    var time by remember { mutableStateOf(System.currentTimeMillis()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(showSeconds, isCompact) {
        time = System.currentTimeMillis()

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            time = System.currentTimeMillis()
            val handler = Handler(Looper.myLooper()!!)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    time = System.currentTimeMillis()
                }
            }
            val callback = object : Runnable {
                override fun run() {
                    time = System.currentTimeMillis()
                    handler.postDelayed(this, 1000 - (time % 1000))
                }
            }

            if (!isCompact && showSeconds) {
                context.registerReceiver(receiver, IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_CHANGED)
                })

                handler.postDelayed(callback, 1000 - (time % 1000))
            }
            else {
                context.registerReceiver(receiver, IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIME_TICK)
                })
            }

            try {
                awaitCancellation()
            } finally {
                context.unregisterReceiver(receiver)
                handler.removeCallbacks(callback)
            }
        }
    }

    CompositionLocalProvider(
        LocalClockTime provides time,
        content = content
    )
}

val LocalClockTime = compositionLocalOf { System.currentTimeMillis() }