/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.resource_usage_legend_cellular
import com.vitorpamplona.amethyst.commons.resources.resource_usage_legend_wifi
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageSummary
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications.chart.LastWeekLabelFormatter
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications.chart.makeLine
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.RoyalBlue
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt

/**
 * Cellular-on-dark-surfaces variant of BitcoinOrange. The light/dark pairs
 * (#F7931A + #4169E1 on light; #C77414 + #4169E1 on dark) were validated for
 * CVD separation, lightness band, and chroma with the dataviz palette checks;
 * the light orange's low surface contrast is relieved by the text legend.
 */
private val CellularOnDark = Color(0xFFC77414)

/**
 * 7-day data-per-day trend: one line per network class, MB per day, today at
 * the right edge. Reuses the notification chart's Vico idioms ([makeLine],
 * [LastWeekLabelFormatter], x = -6..0 with today at 0).
 */
@Composable
fun UsageTrendChart(
    days: Map<Long, Map<String, Long>>,
    today: Long,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cellularColor = if (isDark) CellularOnDark else BitcoinOrange
    val wifiColor = RoyalBlue

    val model =
        remember(days, today) {
            val xs = (-6..0).toList()
            val cellular =
                xs.map { offset ->
                    UsageSummary.from(days[today + offset].orEmpty()).mobileBytes.toMb()
                }
            val wifi =
                xs.map { offset ->
                    val s = UsageSummary.from(days[today + offset].orEmpty())
                    (s.wifiBytesBg + s.wifiBytesFg).toMb()
                }
            CartesianChartModel(
                LineCartesianLayerModel.build {
                    series(xs, cellular)
                    series(xs, wifi)
                },
            )
        }

    val chart =
        rememberCartesianChart(
            layers =
                arrayOf(
                    LineCartesianLayer(
                        LineCartesianLayer.LineProvider.series(
                            makeLine(cellularColor),
                            makeLine(wifiColor),
                        ),
                    ),
                ),
            startAxis =
                VerticalAxis.rememberStart(
                    valueFormatter = MegabyteAxisFormatter,
                    itemPlacer = VerticalAxis.ItemPlacer.count({ 5 }),
                ),
            bottomAxis =
                HorizontalAxis.rememberBottom(
                    valueFormatter = LastWeekLabelFormatter(),
                ),
        )

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CartesianChartHost(
            chart = chart,
            model = model,
            modifier = Modifier.fillMaxWidth().height(170.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendEntry(cellularColor, Res.string.resource_usage_legend_cellular)
            LegendEntry(wifiColor, Res.string.resource_usage_legend_wifi)
        }
    }
}

@Composable
private fun LegendEntry(
    color: Color,
    label: StringResource,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(
            text = stringRes(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Long.toMb(): Float = this / 1_048_576f

private val MegabyteAxisFormatter =
    object : CartesianValueFormatter {
        override fun format(
            context: CartesianMeasuringContext,
            value: Double,
            verticalAxisPosition: Axis.Position.Vertical?,
        ): CharSequence =
            if (value >= 1024) {
                "%.1fG".format(value / 1024.0)
            } else {
                "${value.roundToInt()}M"
            }
    }
