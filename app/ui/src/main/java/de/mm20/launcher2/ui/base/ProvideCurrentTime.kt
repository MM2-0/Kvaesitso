package de.mm20.launcher2.ui.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import java.time.Instant
import java.time.ZoneId

/**
 * Provide the current time (in millis) to the LocalTime composition local.
 * The time is updated every second.
 */
@Composable
fun ProvideCurrentTime(content: @Composable () -> Unit) {

    val context = LocalContext.current

    var time by remember { mutableStateOf(System.currentTimeMillis()) }

    val secondsAnimation = remember { Animatable(0f) }

    LaunchedEffect(time) {
        val currentSeconds = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).second
        secondsAnimation.animateTo(currentSeconds.toFloat(), snap())
        secondsAnimation.animateTo(
            60f,
            tween((60 - currentSeconds) * 1000, easing = LinearEasing)
        )
    }

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
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIME_TICK)
            })

            try {
                awaitCancellation()
            } finally {
                context.unregisterReceiver(receiver)
            }
        }
    }

    CompositionLocalProvider(
        LocalTime provides time + secondsAnimation.value.toInt() * 1000,
        content = content
    )
}

val LocalTime = compositionLocalOf { System.currentTimeMillis() }