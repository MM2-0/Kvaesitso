package de.mm20.launcher2.ui.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.*
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
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    time = System.currentTimeMillis()
                }
            }
            context.registerReceiver(receiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
            })
            try {
                awaitCancellation()
            } finally {
                context.unregisterReceiver(receiver)
            }
        }
    }

    CompositionLocalProvider(
        LocalTime provides time,
        content = content
    )
}

val LocalTime = compositionLocalOf { System.currentTimeMillis() }