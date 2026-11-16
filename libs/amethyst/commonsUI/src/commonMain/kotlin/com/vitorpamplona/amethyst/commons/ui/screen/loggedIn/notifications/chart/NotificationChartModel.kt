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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications.chart

import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.common.data.MutableExtraStore
import com.vitorpamplona.amethyst.commons.notifications.NotificationChart
import com.vitorpamplona.amethyst.commons.notifications.NotificationSummaryState

val ShowDecimals = ExtraStore.Key<Boolean>()
val BottomAxisLabelKey = ExtraStore.Key<List<String>>()

/** The summary chart's model: replies, boosts and reactions on one layer, zap amounts on the other. */
fun NotificationChart.toChartModel(state: NotificationSummaryState): CartesianChartModel {
    val chart1 =
        LineCartesianLayerModel.build {
            series(days, replies)
            series(days, boosts)
            series(days, reactions)
        }

    val chart2 =
        LineCartesianLayerModel.build {
            series(days, zaps)
        }

    val mutableStore = MutableExtraStore()
    mutableStore[ShowDecimals] = state.shouldShowDecimals(chart2.minY, chart2.maxY)

    return CartesianChartModel(chart1, chart2).copy(mutableStore)
}
