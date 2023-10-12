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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockStyle
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetColors
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetLayout
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import kotlinx.coroutines.launch

@Composable
fun WatchFaceSelector(
    layout: ClockWidgetLayout,
    colors: ClockWidgetColors,
    selected: ClockStyle?,
    onSelect: (ClockStyle) -> Unit,
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
                sortedMapOf(
                    ClockStyle.DigitalClock1 to 0,
                    ClockStyle.DigitalClock2 to 1,
                    ClockStyle.AnalogClock to 2,
                    ClockStyle.OrbitClock to 3,
                    ClockStyle.BinaryClock to 4,
                    ClockStyle.EmptyClock to 5,
                )
            }
            val pagerState = rememberPagerState(
                initialPage = styles.getOrDefault(selected, 0)
            ) {
                styles.values.max() + 1
            }

            LaunchedEffect(pagerState.currentPage) {
                if (styles.getOrDefault(selected, 0) == pagerState.currentPage) {
                    return@LaunchedEffect
                }
                onSelect(styles.entries.first { it.value == pagerState.currentPage }.key)
            }

            val scope = rememberCoroutineScope()

            Box {
                androidx.compose.animation.AnimatedVisibility(
                    styles.entries.count { it.value == pagerState.currentPage } > 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .zIndex(1f),
                    enter = scaleIn(),
                    exit = scaleOut(),
                ) {
                    var showVariantDropdown by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showVariantDropdown = true },
                        modifier = Modifier
                            .padding(4.dp)
                    ) {
                        Icon(Icons.Rounded.Style, null)
                    }
                    DropdownMenu(
                        expanded = showVariantDropdown,
                        onDismissRequest = { showVariantDropdown = false }) {
                        for (variant in styles.filter { it.value == pagerState.currentPage }) {
                            DropdownMenuItem(
                                onClick = {
                                    onSelect(variant.key)
                                    showVariantDropdown = false
                                },
                                text = {
                                    Text(
                                        text = getVariantName(context, variant.key),
                                    )
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
                            val currentPageStyles = remember {
                                styles.filter { it.value == pageIndex }
                            }
                            if (currentPageStyles.containsKey(selected)) {
                                Clock(selected, layout)
                            } else {
                                Clock(currentPageStyles.keys.first(), layout)
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
                        text = getClockstyleName(
                            context,
                            styles.entries.first { (k, v) -> v == pagerState.currentPage }.key
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
                        for (style in styles.entries.distinctBy { it.value }) {
                            DropdownMenuItem(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            style.value,
                                        )
                                    }
                                    showStyleDropdown = false
                                },
                                text = {
                                    Text(
                                        text = getClockstyleName(context, style.key),
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

fun getClockstyleName(context: Context, style: ClockStyle): String {
    return when (style) {
        ClockStyle.DigitalClock1 -> "Bold"
        ClockStyle.DigitalClock2 -> "Simple"
        ClockStyle.OrbitClock -> "Orbit"
        ClockStyle.BinaryClock -> "Binary"
        ClockStyle.AnalogClock -> "Hands"
        ClockStyle.EmptyClock -> "Empty"
        ClockStyle.UNRECOGNIZED -> ""
    }
}

fun getVariantName(context: Context, style: ClockStyle): String {
    return when (style) {
        ClockStyle.DigitalClock1,
        ClockStyle.DigitalClock2,
        ClockStyle.OrbitClock,
        ClockStyle.BinaryClock,
        ClockStyle.AnalogClock,
        ClockStyle.EmptyClock -> "Standard"

        ClockStyle.UNRECOGNIZED -> ""
    }
}