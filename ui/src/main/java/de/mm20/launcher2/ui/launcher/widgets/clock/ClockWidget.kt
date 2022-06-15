package de.mm20.launcher2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockStyle
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetLayout
import de.mm20.launcher2.ui.base.LocalTime
import de.mm20.launcher2.ui.launcher.widgets.clock.ClockWidgetVM
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.*
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.PartProvider

@Composable
fun ClockWidget(
    modifier: Modifier = Modifier
) {
    val viewModel: ClockWidgetVM = viewModel()
    val context = LocalContext.current
    val layout by viewModel.layout.observeAsState()
    val clockStyle by viewModel.clockStyle.observeAsState()
    val time = LocalTime.current

    LaunchedEffect(time) {
        viewModel.updateTime(time)
    }

    val partProvider by viewModel.getActivePart(LocalContext.current).collectAsState(null)

    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {

        CompositionLocalProvider(
            LocalContentColor provides Color.White
        ) {
            if (layout == ClockWidgetLayout.Vertical) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.clickable(
                            enabled = clockStyle != ClockStyle.EmptyClock,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            viewModel.launchClockApp(context)
                        }
                    ) {
                        Clock(clockStyle, ClockWidgetLayout.Vertical)
                    }

                    DynamicZone(
                        modifier = Modifier.padding(bottom = 16.dp),
                        ClockWidgetLayout.Vertical,
                        provider = partProvider,
                    )
                }
            }
            if (layout == ClockWidgetLayout.Horizontal) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Rounded.ExpandLess, contentDescription = "")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DynamicZone(
                            modifier = Modifier.weight(1f),
                            ClockWidgetLayout.Horizontal,
                            provider = partProvider,
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .height(56.dp)
                                .width(2.dp)
                                .background(
                                    LocalContentColor.current
                                ),
                        )
                        Box(
                            modifier = Modifier.clickable(
                                enabled = clockStyle != ClockStyle.EmptyClock,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                viewModel.launchClockApp(context)
                            }
                        ) {
                            Clock(clockStyle, ClockWidgetLayout.Horizontal)
                        }
                    }
                }
            }
        }

    }
}

@Composable
fun Clock(
    style: ClockStyle?,
    layout: ClockWidgetLayout,
) {
    val time = LocalTime.current
    when (style) {
        ClockStyle.DigitalClock1 -> DigitalClock1(time, layout)
        ClockStyle.DigitalClock2 -> DigitalClock2(time, layout)
        ClockStyle.BinaryClock -> BinaryClock(time, layout)
        ClockStyle.AnalogClock -> AnalogClock(time, layout)
        ClockStyle.EmptyClock -> EmptyClock(time, layout)
        else -> {}
    }
}

@Composable
fun DynamicZone(
    modifier: Modifier = Modifier,
    layout: ClockWidgetLayout,
    provider: PartProvider?,
) {
    Column(
        modifier = modifier
    ) {
        provider?.Component(layout)
    }
}