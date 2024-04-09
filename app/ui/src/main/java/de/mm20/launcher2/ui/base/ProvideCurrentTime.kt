package de.mm20.launcher2.ui.base

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max


/**
 * Provide the current time (in millis) to the LocalTime composition local.
 * The time is updated every second.
 */
@Composable
fun ProvideCurrentTime(content: @Composable () -> Unit) {

    val lifecycleOwner = LocalLifecycleOwner.current

    var time by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(null) {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val dateTime = Instant.now().atZone(ZoneId.systemDefault())

                time = dateTime.toEpochSecond() * 1000

                val millis = dateTime.nano / 1000000L
                var next = 1000L - millis
                if (next <= 200L) next += 1000L

                handler.postDelayed(this, 1000 - millis)
            }

        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            handler.post(runnable)
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                handler.removeCallbacks(runnable)
            }
        }
    }

    CompositionLocalProvider(
        LocalTime provides time,
        content = content
    )
}

val LocalTime = compositionLocalOf { System.currentTimeMillis() }