package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import java.util.Calendar

@Composable
fun BinaryClock(
    time: Long,
    compact: Boolean,
    showSeconds: Boolean,
    useThemeColor: Boolean,
) {
    val verticalLayout = !compact
    val date = Calendar.getInstance()
    date.timeInMillis = time
    val second = date[Calendar.SECOND]
    val minute = date[Calendar.MINUTE]
    var hour = date[Calendar.HOUR]
    if (hour == 0) hour = 12

    val color = if (useThemeColor) {
        if (LocalContentColor.current == Color.White) {
            if (LocalDarkTheme.current) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.primaryContainer
        } else {
            if (LocalDarkTheme.current) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.primary
        }
    }
    else {
        LocalContentColor.current
    }

    // FIXME: Accent color by setting
    val disabledColor = LocalContentColor.current.copy(alpha = 0.45f)

    if (verticalLayout) {
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Row(
                modifier = Modifier.padding(start = 0.dp, top = 24.dp, end = 0.dp, bottom = 6.dp)
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
                                if (active) color else disabledColor
                            )
                    )
                    if (i == 3) {
                        Box(Modifier.size(8.dp))
                    }
                }
            }
            if (showSeconds) {
                Row(
                    horizontalArrangement = Arrangement.End
                ) {
                    for (i in 0 until 6) {
                        val active = second and (1 shl (5 - i)) != 0
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(12.dp)
                                .background(
                                    if (active) color else disabledColor
                                )
                        )
                    }
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
                                if (active) color else disabledColor
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
                                if (active) color else disabledColor
                            )
                    )
                }
            }
        }
    }
}