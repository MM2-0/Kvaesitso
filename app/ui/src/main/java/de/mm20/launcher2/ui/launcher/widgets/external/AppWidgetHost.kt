package de.mm20.launcher2.ui.launcher.widgets.external

import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.SparseIntArray
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.ScrollView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.iterator
import androidx.core.view.setPadding
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ui.base.LocalAppWidgetHost
import de.mm20.launcher2.ui.ktx.toPixels
import palettes.TonalPalette
import kotlin.math.roundToInt

@Composable
fun AppWidgetHost(
    widgetInfo: AppWidgetProviderInfo,
    widgetId: Int,
    modifier: Modifier = Modifier,
    borderless: Boolean = false,
    useThemeColors: Boolean = false,
    onLightBackground: Boolean = false,
) {
    val padding = if (borderless) 0 else 8.dp.toPixels().roundToInt()

    val colorScheme = MaterialTheme.colorScheme
    val appWidgetHost = LocalAppWidgetHost.current

    BoxWithConstraints(
        modifier = modifier,
    ) {
        val maxWidth = maxWidth
        val maxHeight = maxHeight
        key(widgetId) {
            AndroidView(
                modifier = modifier
                    .fillMaxSize(),
                factory = {
                    val view = appWidgetHost.createView(it.applicationContext, widgetId, widgetInfo)
                    enableNestedScroll(view)
                    return@AndroidView view
                },
                update = {
                    if (isAtLeastApiLevel(29)) {
                        it.setOnLightBackground(onLightBackground)
                    }
                    if (isAtLeastApiLevel(31)) {
                        if (useThemeColors) {
                            val colorMapping = getColorMapping(colorScheme)
                            it.setColorResources(colorMapping)
                        } else {
                            it.resetColorResources()
                        }
                        it.updateAppWidgetSize(
                            Bundle(),
                            arrayListOf(SizeF(maxWidth.value, maxHeight.value))
                        )
                    } else {
                        it.updateAppWidgetSize(
                            null,
                            maxWidth.value.roundToInt(),
                            maxHeight.value.roundToInt(),
                            maxWidth.value.roundToInt(),
                            maxHeight.value.roundToInt(),
                        )
                        // Workaround to force update of the widget view
                        it.updateAppWidgetOptions(Bundle())
                    }
                    it.setPadding(padding)
                }
            )
        }
    }
}

private fun enableNestedScroll(view: View) {
    if (view is ViewGroup) {
        for (child in view.iterator()) {
            enableNestedScroll(child)
        }
    }
    if (view is ListView || view is ScrollView) view.isNestedScrollingEnabled = true
}

@RequiresApi(Build.VERSION_CODES.S)
private fun getColorMapping(colorScheme: ColorScheme): SparseIntArray {
    val p = TonalPalette.fromInt(colorScheme.primary.toArgb())
    val s = TonalPalette.fromInt(colorScheme.secondary.toArgb())
    val t = TonalPalette.fromInt(colorScheme.tertiary.toArgb())
    val n = TonalPalette.fromInt(colorScheme.outline.toArgb())
    val nv = TonalPalette.fromInt(colorScheme.outlineVariant.toArgb())

    val colorResources = SparseIntArray()
    colorResources.append(android.R.color.system_accent1_0, p.tone(100))
    colorResources.append(android.R.color.system_accent1_10, p.tone(99))
    colorResources.append(android.R.color.system_accent1_50, p.tone(95))
    colorResources.append(android.R.color.system_accent1_100, p.tone(90))
    colorResources.append(android.R.color.system_accent1_200, p.tone(80))
    colorResources.append(android.R.color.system_accent1_300, p.tone(70))
    colorResources.append(android.R.color.system_accent1_400, p.tone(60))
    colorResources.append(android.R.color.system_accent1_500, p.tone(50))
    colorResources.append(android.R.color.system_accent1_600, p.tone(40))
    colorResources.append(android.R.color.system_accent1_700, p.tone(30))
    colorResources.append(android.R.color.system_accent1_800, p.tone(20))
    colorResources.append(android.R.color.system_accent1_900, p.tone(10))
    colorResources.append(android.R.color.system_accent1_1000, s.tone(0))
    colorResources.append(android.R.color.system_accent2_0, s.tone(100))
    colorResources.append(android.R.color.system_accent2_10, s.tone(99))
    colorResources.append(android.R.color.system_accent2_50, s.tone(95))
    colorResources.append(android.R.color.system_accent2_100, s.tone(90))
    colorResources.append(android.R.color.system_accent2_200, s.tone(80))
    colorResources.append(android.R.color.system_accent2_300, s.tone(70))
    colorResources.append(android.R.color.system_accent2_400, s.tone(60))
    colorResources.append(android.R.color.system_accent2_500, s.tone(50))
    colorResources.append(android.R.color.system_accent2_600, s.tone(40))
    colorResources.append(android.R.color.system_accent2_700, s.tone(30))
    colorResources.append(android.R.color.system_accent2_800, s.tone(20))
    colorResources.append(android.R.color.system_accent2_900, s.tone(10))
    colorResources.append(android.R.color.system_accent2_1000, t.tone(0))
    colorResources.append(android.R.color.system_accent3_0, t.tone(100))
    colorResources.append(android.R.color.system_accent3_10, t.tone(99))
    colorResources.append(android.R.color.system_accent3_50, t.tone(95))
    colorResources.append(android.R.color.system_accent3_100, t.tone(90))
    colorResources.append(android.R.color.system_accent3_200, t.tone(80))
    colorResources.append(android.R.color.system_accent3_300, t.tone(70))
    colorResources.append(android.R.color.system_accent3_400, t.tone(60))
    colorResources.append(android.R.color.system_accent3_500, t.tone(50))
    colorResources.append(android.R.color.system_accent3_600, t.tone(40))
    colorResources.append(android.R.color.system_accent3_700, t.tone(30))
    colorResources.append(android.R.color.system_accent3_800, t.tone(20))
    colorResources.append(android.R.color.system_accent3_900, t.tone(10))
    colorResources.append(android.R.color.system_accent3_1000, t.tone(0))
    colorResources.append(android.R.color.system_neutral1_0, n.tone(100))
    colorResources.append(android.R.color.system_neutral1_10, n.tone(99))
    colorResources.append(android.R.color.system_neutral1_50, n.tone(95))
    colorResources.append(android.R.color.system_neutral1_100, n.tone(90))
    colorResources.append(android.R.color.system_neutral1_200, n.tone(80))
    colorResources.append(android.R.color.system_neutral1_300, n.tone(70))
    colorResources.append(android.R.color.system_neutral1_400, n.tone(60))
    colorResources.append(android.R.color.system_neutral1_500, n.tone(50))
    colorResources.append(android.R.color.system_neutral1_600, n.tone(40))
    colorResources.append(android.R.color.system_neutral1_700, n.tone(30))
    colorResources.append(android.R.color.system_neutral1_800, n.tone(20))
    colorResources.append(android.R.color.system_neutral1_900, n.tone(10))
    colorResources.append(android.R.color.system_neutral1_1000, nv.tone(0))
    colorResources.append(android.R.color.system_neutral2_0, nv.tone(100))
    colorResources.append(android.R.color.system_neutral2_10, nv.tone(99))
    colorResources.append(android.R.color.system_neutral2_50, nv.tone(95))
    colorResources.append(android.R.color.system_neutral2_100, nv.tone(90))
    colorResources.append(android.R.color.system_neutral2_200, nv.tone(80))
    colorResources.append(android.R.color.system_neutral2_300, nv.tone(70))
    colorResources.append(android.R.color.system_neutral2_400, nv.tone(60))
    colorResources.append(android.R.color.system_neutral2_500, nv.tone(50))
    colorResources.append(android.R.color.system_neutral2_600, nv.tone(40))
    colorResources.append(android.R.color.system_neutral2_700, nv.tone(30))
    colorResources.append(android.R.color.system_neutral2_800, nv.tone(20))
    colorResources.append(android.R.color.system_neutral2_900, nv.tone(10))
    colorResources.append(android.R.color.system_neutral2_1000, nv.tone(0))

    return colorResources
}