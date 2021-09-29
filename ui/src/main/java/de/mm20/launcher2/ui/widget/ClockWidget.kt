package de.mm20.launcher2.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.insets.LocalWindowInsets
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.ClockStyle
import de.mm20.launcher2.preferences.dataStore
import de.mm20.launcher2.ui.component.AnalogClock
import de.mm20.launcher2.ui.component.BinaryClock
import de.mm20.launcher2.ui.component.DigitalClock
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.locals.LocalWindowSize
import de.mm20.launcher2.ui.widget.parts.DatePart
import kotlinx.coroutines.flow.map

@Composable
fun ClockWidget(
    modifier: Modifier = Modifier,
    transparentBackground: Boolean
) {
    val windowHeight = LocalWindowSize.current.height
    val insets = LocalWindowInsets.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((windowHeight - insets.systemBars.bottom).toDp()),
        contentAlignment = Alignment.BottomCenter
    ) {
        val contentColor by animateColorAsState(
            if (transparentBackground) Color.White else MaterialTheme.colors.onSurface
        )

        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                Clock(transparentBackground)

                DynamicZone()
            }
        }

    }
}

@Composable
fun Clock(transparentBackground: Boolean) {
    var time by remember { mutableStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    val dataStore = context.dataStore
    val clockStyle by remember { dataStore.data.map { it.appearance.clockStyle } }.collectAsState(
        initial = ClockStyle.Digital
    )

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

@Composable
fun DynamicZone() {
    Column(
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        DatePart()
    }
}