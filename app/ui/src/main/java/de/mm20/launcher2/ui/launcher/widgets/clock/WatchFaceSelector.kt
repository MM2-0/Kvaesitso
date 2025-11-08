package de.mm20.launcher2.ui.launcher.widgets.clock

import android.app.Activity
import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.zIndex
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.ClockWidgetColors
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.ui.BuildConfig
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalAppWidgetHost
import de.mm20.launcher2.ui.component.DragResizeHandle
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.launcher.sheets.WidgetPickerSheet
import de.mm20.launcher2.ui.launcher.widgets.external.AppWidgetHost
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import de.mm20.launcher2.widgets.AppWidget
import kotlinx.coroutines.launch

@Composable
fun WatchFaceSelector(
    styles: List<ClockWidgetStyle>,
    compact: Boolean,
    colors: ClockWidgetColors,
    themeColors: Boolean,
    selected: ClockWidgetStyle?,
    onSelect: (ClockWidgetStyle) -> Unit,
) {
    val context = LocalContext.current

    var showWidgetPicker by rememberSaveable { mutableStateOf(false) }
    var resizeCustomWidget by remember { mutableStateOf(false) }

    val lightBackground =
        colors == ClockWidgetColors.Dark || colors == ClockWidgetColors.Auto && LocalPreferDarkContentOverWallpaper.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 0.dp),
        color = if (lightBackground) {
            if (LocalDarkTheme.current) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainer
        } else {
            if (LocalDarkTheme.current) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.inverseSurface
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        AnimatedContent(resizeCustomWidget) { resize ->
            if (resize && selected is ClockWidgetStyle.Custom) {
                ResizeCustomWidget(
                    style = selected,
                    compact = compact,
                    themeColors = themeColors,
                    lightBackground = lightBackground,
                    onChange = { onSelect(it) },
                    onExit = { resizeCustomWidget = false }
                )
                return@AnimatedContent
            }
            Column(
                modifier = Modifier,
            ) {
                val pagerState = rememberPagerState(
                    initialPage = styles.indexOfFirst { it.javaClass == selected?.javaClass }
                        .coerceAtLeast(0),
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
                        selected is ClockWidgetStyle.Digital1 ||
                                selected is ClockWidgetStyle.Analog ||
                                (selected is ClockWidgetStyle.Custom && selected.widgetId != null),
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
                            Icon(painterResource(R.drawable.tune_24px), null)
                            DropdownMenu(
                                expanded = showStyleSettings,
                                onDismissRequest = { showStyleSettings = false }) {
                                if (selected is ClockWidgetStyle.Digital1) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.clock_variant_outlined)) },
                                        leadingIcon = {
                                            Icon(
                                                painterResource(
                                                    if (selected.outlined) R.drawable.check_circle_24px_filled
                                                    else R.drawable.circle_24px,
                                                ),
                                                null
                                            )
                                        },
                                        onClick = {
                                            onSelect(selected.copy(outlined = !selected.outlined))
                                        }
                                    )
                                }
                                if (selected is ClockWidgetStyle.Analog) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.clock_variant_analog_ticks)) },
                                        leadingIcon = {
                                            Icon(
                                                painterResource(
                                                    if (selected.showTicks) R.drawable.check_circle_24px_filled
                                                    else R.drawable.circle_24px,
                                                ),
                                                null
                                            )
                                        },
                                        onClick = {
                                            onSelect(selected.copy(showTicks = !selected.showTicks))
                                        }
                                    )
                                }
                                if (selected is ClockWidgetStyle.Custom) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.widget_pick_widget)) },
                                        leadingIcon = {
                                            Icon(painterResource(R.drawable.swap_horiz_24px), null)
                                        },
                                        onClick = {
                                            showWidgetPicker = true
                                            showStyleSettings = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(painterResource(R.drawable.resize_24px), null)
                                        },
                                        text = { Text(stringResource(R.string.widget_config_appwidget_resize)) },
                                        onClick = { resizeCustomWidget = true }
                                    )
                                    val widget = remember(selected.widgetId) {
                                        val id = selected.widgetId ?: return@remember null
                                        AppWidgetManager.getInstance(context)
                                            .getAppWidgetInfo(id)
                                    }
                                    val appWidgetHost = LocalAppWidgetHost.current
                                    if (widget?.configure != null) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.widget_config_appwidget_configure)) },
                                            leadingIcon = {
                                                Icon(painterResource(R.drawable.settings_24px), null)
                                            },
                                            onClick = {
                                                appWidgetHost.startAppWidgetConfigureActivityForResult(
                                                    getActivityFromContext(context) ?: return@DropdownMenuItem,
                                                    selected.widgetId ?: return@DropdownMenuItem,
                                                    0,
                                                    0,
                                                    if (Build.VERSION.SDK_INT < 34) {
                                                        null
                                                    } else {
                                                        ActivityOptions.makeBasic()
                                                            .setPendingIntentBackgroundActivityStartMode(
                                                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                                            )
                                                            .toBundle()
                                                    }
                                                )
                                            }
                                        )
                                    }
                                    if (BuildConfig.DEBUG) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(painterResource(R.drawable.restart_alt_24px), null)
                                            },
                                            text = { Text("Reset") },
                                            onClick = {
                                                val widgetId = selected.widgetId
                                                if (widgetId != null) {
                                                    appWidgetHost.deleteAppWidgetId(widgetId)
                                                }
                                                onSelect(
                                                    ClockWidgetStyle.Custom()
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val darkColors =
                        colors == ClockWidgetColors.Auto && LocalPreferDarkContentOverWallpaper.current || colors == ClockWidgetColors.Dark

                    CompositionLocalProvider(
                        LocalContentColor provides if (darkColors) {
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
                            val currentPageStyle = styles[pageIndex]
                            if (currentPageStyle is ClockWidgetStyle.Custom && currentPageStyle.widgetId == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 24.dp, bottom = 8.dp),
                                    contentAlignment = Alignment.TopCenter,
                                ) {
                                    TextButton(
                                        onClick = {
                                            showWidgetPicker = true
                                        },
                                        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                                        modifier = Modifier
                                            .padding(16.dp),
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = if (darkColors == LocalDarkTheme.current) MaterialTheme.colorScheme.inversePrimary
                                            else MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(
                                            modifier = Modifier
                                                .padding(end = ButtonDefaults.IconSpacing)
                                                .size(ButtonDefaults.IconSize),
                                            painter = painterResource(R.drawable.widgets_20px),
                                            contentDescription = null,
                                        )
                                        Text(stringResource(R.string.widget_pick_widget))
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 24.dp, bottom = 8.dp)
                                        .pointerInput(Unit) {
                                            awaitEachGesture {
                                                val event =
                                                    awaitFirstDown(pass = PointerEventPass.Initial)
                                                event.consume()
                                            }
                                        },
                                    contentAlignment = Alignment.TopCenter,
                                ) {

                                    if (currentPageStyle.javaClass == selected?.javaClass) {
                                        Clock(selected, compact, darkColors)
                                    } else {
                                        Clock(currentPageStyle, compact, darkColors)
                                    }
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
                        Icon(painterResource(R.drawable.chevron_backward_24px), null)
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
                            painterResource(R.drawable.arrow_drop_down_20px),
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
                        Icon(painterResource(R.drawable.chevron_forward_24px), null)
                    }
                }
            }
        }
    }

    if (showWidgetPicker && selected is ClockWidgetStyle.Custom) {
        val previousWidgetId = selected.widgetId
        val appWidgetHost = LocalAppWidgetHost.current
        WidgetPickerSheet(
            includeBuiltinWidgets = false,
            onWidgetSelected = {
                if (previousWidgetId != null) {
                    appWidgetHost.deleteAppWidgetId(previousWidgetId)
                }
                it as AppWidget
                onSelect(
                    selected.copy(
                        widgetId = it.config.widgetId,
                        width = it.config.width,
                        height = it.config.height,
                    )
                )
            },
            onDismiss = {
                showWidgetPicker = false
            }
        )
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
        is ClockWidgetStyle.Custom -> context.getString(R.string.clock_style_custom)
        else -> ""
    }
}

@Composable
private fun ResizeCustomWidget(
    style: ClockWidgetStyle.Custom,
    compact: Boolean,
    themeColors: Boolean,
    lightBackground: Boolean,
    onChange: (ClockWidgetStyle.Custom) -> Unit,
    onExit: () -> Unit = {},
) {
    val context = LocalContext.current

    val widgetId = style.widgetId

    val widgetInfo = remember(widgetId) {
        widgetId?.let {
            AppWidgetManager.getInstance(context)
                .getAppWidgetInfo(it)
        }
    }
    if (widgetId != null && widgetInfo != null) {
        val minWidth = when {
            compact -> 64.dp
            widgetInfo.minResizeWidth in 1..widgetInfo.minWidth -> {
                widgetInfo.minResizeWidth.toDp()
            }

            else -> {
                widgetInfo.minWidth.toDp()
            }
        }

        val minHeight = when {
            compact -> 16.dp
            widgetInfo.minResizeHeight in 1..widgetInfo.minHeight -> {
                widgetInfo.minResizeHeight.toDp()
            }

            else -> {
                widgetInfo.minHeight.toDp()
            }
        }

        val maxWidth = when {
            compact -> 200.dp
            isAtLeastApiLevel(31) && widgetInfo.maxResizeWidth > 0 -> {
                widgetInfo.maxResizeWidth.toDp()
            }

            else -> Dp.Unspecified
        }

        val maxHeight = when {
            compact -> 64.dp
            isAtLeastApiLevel(31) && widgetInfo.maxResizeHeight > 0 -> {
                widgetInfo.maxResizeHeight.toDp()
            }

            else -> Dp.Unspecified
        }


        var resizeWidth by remember(style.widgetId) {
            mutableStateOf(
                style.width?.dp ?: Dp.Unspecified
            )
        }
        var resizeHeight by remember(style.widgetId) { mutableStateOf(style.height.dp) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 64.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            AppWidgetHost(
                widgetInfo = widgetInfo,
                widgetId = widgetId,
                modifier = Modifier
                    .then(
                        when {
                            compact && resizeWidth.isUnspecified -> Modifier.widthIn(max = 200.dp)
                            compact && !resizeWidth.isUnspecified -> Modifier.width(
                                resizeWidth.coerceAtMost(
                                    200.dp
                                )
                            )

                            !compact && !resizeWidth.isUnspecified -> Modifier.width(resizeWidth)
                            else -> Modifier.fillMaxWidth()
                        }
                    )
                    .then(
                        when {
                            compact -> Modifier.height(resizeHeight.coerceAtMost(64.dp))
                            else -> Modifier.height(resizeHeight)
                        }
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val event = awaitFirstDown(pass = PointerEventPass.Initial)
                            event.consume()
                        }
                    },
                borderless = compact,
                useThemeColors = themeColors,
                onLightBackground = lightBackground,
            )

            DragResizeHandle(
                alignment = Alignment.TopCenter,
                width = resizeWidth.coerceAtMost(maxWidth),
                height = resizeHeight.coerceAtMost(maxHeight),
                minWidth = minWidth,
                minHeight = minHeight,
                maxWidth = maxWidth,
                maxHeight = if (compact) 64.dp else Dp.Unspecified,
                onResize = { w, h ->
                    resizeWidth = w
                    resizeHeight = h
                },
                onResizeStopped = {
                    onChange(
                        style.copy(
                            width = resizeWidth.value.toInt(),
                            height = resizeHeight.value.toInt()
                        )
                    )
                }
            )

            FilledIconButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .offset(y = 64.dp),
                onClick = onExit
            ) {
                Icon(painterResource(R.drawable.check_24px), null)
            }
        }
    }
}

private fun getActivityFromContext(context: Context): Activity? {
    var activity = context

    while (activity is ContextWrapper) {
        if (activity is Activity) {
            return activity
        }

        activity = activity.baseContext
    }

    return null
}