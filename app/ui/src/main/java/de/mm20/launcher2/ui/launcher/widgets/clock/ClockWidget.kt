package de.mm20.launcher2.ui.launcher.widgets.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockStyle
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetColors
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetLayout
import de.mm20.launcher2.ui.base.LocalTime
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.AnalogClock
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.BinaryClock
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.DigitalClock1
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.DigitalClock2
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.OrbitClock
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.PartProvider
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper

@Composable
fun ClockWidget(
    modifier: Modifier = Modifier
) {
    val viewModel: ClockWidgetVM = viewModel()
    val context = LocalContext.current
    val layout by viewModel.layout.collectAsState()
    val clockStyle by viewModel.clockStyle.collectAsState()
    val color by viewModel.color.collectAsState()
    val time = LocalTime.current

    LaunchedEffect(time) {
        viewModel.updateTime(time)
    }

    val partProviders by remember { viewModel.getActiveParts(context) }.collectAsStateWithLifecycle(
        emptyList()
    )

    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {

        val contentColor =
            if (color == ClockWidgetColors.Auto && LocalPreferDarkContentOverWallpaper.current || color == ClockWidgetColors.Dark) {
                Color(0, 0, 0, 180)
            } else {
                Color.White
            }

        CompositionLocalProvider(
            LocalContentColor provides contentColor
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

                    for (part in partProviders) {
                        DynamicZone(
                            modifier = Modifier.padding(bottom = 8.dp),
                            layout = ClockWidgetLayout.Vertical,
                            provider = part,
                        )
                    }
                }
            }
            if (layout == ClockWidgetLayout.Horizontal) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (partProviders.size > 1) {
                        HorizontalPager(
                            state =  rememberPagerState { 2 },
                            beyondBoundsPageCount = 1,
                            modifier = Modifier.weight(1f)
                        ) {
                            partProviders.getOrNull(it)?.let {
                                DynamicZone(
                                    modifier = Modifier.fillMaxWidth(),
                                    layout = ClockWidgetLayout.Horizontal,
                                    provider = it,
                                )
                            }
                        }
                    } else if (partProviders.isNotEmpty()) {
                        DynamicZone(
                            modifier = Modifier.weight(1f),
                            layout = ClockWidgetLayout.Horizontal,
                            provider = partProviders[0],
                        )
                    }
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

@Composable
fun Clock(
    style: ClockStyle?,
    layout: ClockWidgetLayout
) {
    val time = LocalTime.current
    when (style) {
        ClockStyle.DigitalClock1 -> DigitalClock1(time, layout)
        ClockStyle.DigitalClock2 -> DigitalClock2(time, layout)
        ClockStyle.BinaryClock -> BinaryClock(time, layout)
        ClockStyle.AnalogClock -> AnalogClock(time, layout)
        ClockStyle.OrbitClock -> OrbitClock(time, layout)
        ClockStyle.EmptyClock -> {}
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