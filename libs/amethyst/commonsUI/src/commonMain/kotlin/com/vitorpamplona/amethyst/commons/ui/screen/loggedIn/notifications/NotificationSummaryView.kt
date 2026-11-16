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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.vitorpamplona.amethyst.commons.notifications.NotificationSummaryState
import com.vitorpamplona.amethyst.commons.ui.note.UserReactionsRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications.chart.ShowChart
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications.chart.toChartModel
import com.vitorpamplona.amethyst.commons.ui.theme.chartStyle

@Composable
fun SummaryBar(state: NotificationSummaryState) {
    var showChart by remember { mutableStateOf(false) }

    // Opaque background: DisappearingScaffold layers feed content under the topBar
    // on scroll, and SummaryBar otherwise has no container of its own.
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        UserReactionsRow(state) { showChart = !showChart }

        AnimatedVisibility(
            visible = showChart,
            enter = slideInVertically() + expandVertically(),
            exit = slideOutVertically() + shrinkVertically(),
        ) {
            Row(
                modifier =
                    Modifier
                        .padding(vertical = 0.dp, horizontal = 20.dp)
                        .clickable(onClick = { showChart = !showChart }),
            ) {
                ProvideVicoTheme(MaterialTheme.colorScheme.chartStyle) {
                    ObserveAndShowChart(state)
                }
            }
        }
    }
}

@Composable
private fun ObserveAndShowChart(state: NotificationSummaryState) {
    val chartData by state.chartData.collectAsStateWithLifecycle()

    chartData?.let {
        val chartModel = remember(it) { it.toChartModel(state) }
        ShowChart(chartModel)
    }
}
