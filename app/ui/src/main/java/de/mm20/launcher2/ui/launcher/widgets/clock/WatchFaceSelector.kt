package de.mm20.launcher2.ui.launcher.widgets.clock

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.mm20.launcher2.preferences.ClockWidgetColors
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import kotlinx.coroutines.launch

@Composable
fun WatchFaceSelector(
    compact: Boolean,
    colors: ClockWidgetColors,
    selected: ClockWidgetStyle?,
    onSelect: (ClockWidgetStyle) -> Unit,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 32.dp),
        color = if (colors == ClockWidgetColors.Dark || colors == ClockWidgetColors.Auto && LocalPreferDarkContentOverWallpaper.current) {
            if (LocalDarkTheme.current) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainer
        } else {
            if (LocalDarkTheme.current) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.inverseSurface
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier,
        ) {
            val styles = remember {
                listOf(
                    ClockWidgetStyle.Digital1(),
                    ClockWidgetStyle.Digital2,
                    ClockWidgetStyle.Analog,
                    ClockWidgetStyle.Orbit,
                    ClockWidgetStyle.Segment,
                    ClockWidgetStyle.Binary,
                    ClockWidgetStyle.Empty,
                )
            }
            val pagerState = rememberPagerState(
                initialPage = styles.indexOfFirst { it.javaClass == selected?.javaClass }.coerceAtLeast(0),
            ) {
                styles.size
            }

            LaunchedEffect(pagerState.currentPage) {
                val newStyle = styles[pagerState.currentPage]
                if (newStyle.javaClass == selected?.javaClass) return@LaunchedEffect
                onSelect(newStyle)
            }

            val scope = rememberCoroutineScope()

            Box {
                androidx.compose.animation.AnimatedVisibility(
                    selected is ClockWidgetStyle.Digital1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .zIndex(1f),
                    enter = scaleIn(),
                    exit = scaleOut(),
                ) {
                    var showStyleSettings by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showStyleSettings = true },
                        modifier = Modifier
                            .padding(4.dp)
                    ) {
                        Icon(Icons.Rounded.Settings, null)
                    }
                    DropdownMenu(
                        expanded = showStyleSettings,
                        onDismissRequest = { showStyleSettings = false }) {
                        if (selected is ClockWidgetStyle.Digital1) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clock_variant_outlined)) },
                                leadingIcon = {
                                    if (selected.outlined) {
                                        Icon(Icons.Rounded.Check, null)
                                    }
                                },
                                onClick = {
                                    onSelect(selected.copy(outlined = !selected.outlined))
                                }
                            )
                        }
                    }
                }

                CompositionLocalProvider(
                    LocalContentColor provides if (colors == ClockWidgetColors.Auto && LocalPreferDarkContentOverWallpaper.current || colors == ClockWidgetColors.Dark) {
                        Color(0, 0, 0, 180)
                    } else {
                        Color.White
                    },
                ) {

                    HorizontalPager(
                        modifier = Modifier.animateContentSize(),
                        state = pagerState,
                        verticalAlignment = Alignment.Top,
                    ) { pageIndex ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 8.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            val currentPageStyle = styles[pageIndex]
                            if (currentPageStyle.javaClass == selected?.javaClass) {
                                Clock(selected, compact)
                            } else {
                                Clock(currentPageStyle, compact)
                            }
                        }
                    }

                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    enabled = pagerState.currentPage > 0,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage - 1,
                            )
                        }
                    }) {
                    Icon(Icons.Rounded.ChevronLeft, null)
                }
                var showStyleDropdown by remember { mutableStateOf(false) }
                TextButton(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    onClick = { showStyleDropdown = true },
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 8.dp,
                        start = 16.dp,
                        end = 12.dp,
                    ),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = LocalContentColor.current,
                    ),
                ) {
                    Text(
                        text = getClockStyleName(
                            context,
                            styles[pagerState.currentPage]
                        ),
                        textAlign = TextAlign.Center,
                    )
                    Icon(
                        Icons.Rounded.ArrowDropDown,
                        null,
                        modifier = Modifier
                            .padding(ButtonDefaults.IconSpacing)
                            .size(ButtonDefaults.IconSize)
                    )
                    DropdownMenu(
                        expanded = showStyleDropdown,
                        onDismissRequest = { showStyleDropdown = false }
                    ) {
                        for (style in styles.withIndex()) {
                            DropdownMenuItem(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            style.index,
                                        )
                                    }
                                    showStyleDropdown = false
                                },
                                text = {
                                    Text(
                                        text = getClockStyleName(context, style.value),
                                    )
                                }
                            )
                        }
                    }
                }

                IconButton(
                    enabled = pagerState.currentPage < pagerState.pageCount - 1,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + 1,
                            )
                        }
                    }) {
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }
    }
}

fun getClockStyleName(context: Context, style: ClockWidgetStyle): String {
    return when (style) {
        is ClockWidgetStyle.Digital1 -> context.getString(R.string.clock_style_digital1)
        is ClockWidgetStyle.Digital2 -> context.getString(R.string.clock_style_digital2)
        is ClockWidgetStyle.Orbit -> context.getString(R.string.clock_style_orbit)
        is ClockWidgetStyle.Binary -> context.getString(R.string.clock_style_binary)
        is ClockWidgetStyle.Analog -> context.getString(R.string.clock_style_analog)
        is ClockWidgetStyle.Segment -> context.getString(R.string.clock_style_segment)
        is ClockWidgetStyle.Empty -> context.getString(R.string.clock_style_empty)
        else -> ""
    }
}

// Compat for old enum names, TODO refactor this screen
object ClockStyle {
    val DigitalClock1 = ClockWidgetStyle.Digital1()
    val DigitalClock1_Outlined = ClockWidgetStyle.Digital1(outlined = true)
    val DigitalClock2 = ClockWidgetStyle.Digital2
    val OrbitClock = ClockWidgetStyle.Orbit
    val AnalogClock = ClockWidgetStyle.Analog
    val BinaryClock = ClockWidgetStyle.Binary
    val SegmentClock = ClockWidgetStyle.Segment
    val EmptyClock = ClockWidgetStyle.Empty
}