package de.mm20.launcher2.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.insets.LocalWindowInsets
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.ClockStyle
import de.mm20.launcher2.ui.component.AnalogClock
import de.mm20.launcher2.ui.component.BinaryClock
import de.mm20.launcher2.ui.component.DigitalClock
import de.mm20.launcher2.ui.locals.LocalWindowSize
import de.mm20.launcher2.ui.widget.parts.DatePart
import kotlinx.coroutines.flow.map

@Composable
fun ClockWidget(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {

        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                Clock()

                DynamicZone()
            }
        }

    }
}

@Composable
fun Clock() {
    var time by remember { mutableStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    val clockStyle = ClockStyle.Digital

    DisposableEffect(null) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                time = System.currentTimeMillis()
            }
        }
        val filter = IntentFilter(Intent.ACTION_TIME_TICK).also {
            it.addAction(Intent.ACTION_TIME_CHANGED)
            it.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Box(
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) {
            context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    ) {
        when (clockStyle) {
            ClockStyle.Analog -> {
                AnalogClock(time = time)
            }
            ClockStyle.Binary -> {
                BinaryClock(time = time)
            }
            else -> {
                DigitalClock(time = time)
            }
        }
    }
}

@Composable
fun DynamicZone() {
    Column(
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        DatePart()
    }
}