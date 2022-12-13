package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.Settings
import java.util.*

@Composable
fun BinaryClock(
    time: Long,
    layout: Settings.ClockWidgetSettings.ClockWidgetLayout
) {
    val verticalLayout = layout == Settings.ClockWidgetSettings.ClockWidgetLayout.Vertical
    val date = Calendar.getInstance()
    date.timeInMillis = time
    val minute = date[Calendar.MINUTE]
    var hour = date[Calendar.HOUR]
    if (hour == 0) hour = 12
    if (verticalLayout) {
        Row(
            modifier = Modifier.padding(vertical = 24.dp)
        ) {
            for (i in 0 until 10) {
                val active = if (i < 4) {
                    hour and (1 shl (3 - i)) != 0
                } else {
                    minute and (1 shl (9 - i)) != 0
                }
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(12.dp)
                        .background(
                            LocalContentColor.current.copy(
                                if (active) 1f else 0.45f
                            )
                        )
                )
                if (i == 3) {
                    Box(Modifier.size(8.dp))
                }
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Row {
                for (i in 0 until 4) {
                    val active = hour and (1 shl (3 - i)) != 0
                    Box(
                        modifier = Modifier
                            .padding( 4.dp)
                            .size(12.dp)
                            .background(
                                LocalContentColor.current.copy(
                                    if (active) 1f else 0.45f
                                )
                            )
                    )
                }
            }
            Row {
                for (i in 4 until 10) {
                    val active = minute and (1 shl (9 - i)) != 0
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(12.dp)
                            .background(
                                LocalContentColor.current.copy(
                                    if (active) 1f else 0.45f
                                )
                            )
                    )
                }
            }
        }
    }
}