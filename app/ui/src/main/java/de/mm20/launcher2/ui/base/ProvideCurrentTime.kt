package de.mm20.launcher2.ui.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun ProvideCurrentTime(content: @Composable () -> Unit) {

    val context = LocalContext.current

    var time by remember { mutableStateOf(System.currentTimeMillis()) }

    DisposableEffect(null) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                time = System.currentTimeMillis()
            }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
        })
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    CompositionLocalProvider(
        LocalTime provides time,
        content = content
    )
}

val LocalTime = compositionLocalOf { System.currentTimeMillis() }