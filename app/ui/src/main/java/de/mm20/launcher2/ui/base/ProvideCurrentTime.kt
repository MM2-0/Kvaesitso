package de.mm20.launcher2.ui.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation

@Composable
fun ProvideCurrentTime(content: @Composable () -> Unit) {

    val context = LocalContext.current

    var time by remember { mutableStateOf(System.currentTimeMillis()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(null) {
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

            context.registerReceiver(receiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
            })

            handler.postDelayed(callback, 1000 - (time % 1000))

            try {
                awaitCancellation()
            } finally {
                context.unregisterReceiver(receiver)
                handler.removeCallbacks(callback)
            }
        }
    }

    CompositionLocalProvider(
        LocalTime provides time,
        content = content
    )
}

val LocalTime = compositionLocalOf { System.currentTimeMillis() }