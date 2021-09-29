package de.mm20.launcher2.ui.component

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.ui.locals.LocalColorScheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DigitalClock(time: Long) {
    val format = SimpleDateFormat(
        if (DateFormat.is24HourFormat(LocalContext.current))
            "HH\nmm" else "hh\nmm",
        Locale.getDefault()
    )
    Text(
        modifier = Modifier.padding(4.dp),
        text = format.format(time),
        style = MaterialTheme.typography.h1.copy(
            fontSize = 100.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 0.8.em,
            letterSpacing = -0.1.em
        )
    )
}

@Composable
fun BinaryClock(time: Long) {
    val date = Calendar.getInstance()
    date.timeInMillis = time
    val minute = date[Calendar.MINUTE]
    var hour = date[Calendar.HOUR]
    if (hour == 0) hour = 12
    Row(
        modifier = Modifier.padding(bottom = 24.dp)
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
}

@Composable
fun AnalogClock(time: Long) {
    val date = Calendar.getInstance()
    date.timeInMillis = time
    val minute = date[Calendar.MINUTE]
    val hour = date[Calendar.HOUR]
    val dark = !MaterialTheme.colors.isLight
    val cs = LocalColorScheme.current
    val bgColor = if (dark) cs.accent1.shade800 else cs.accent1.shade200
    val hourColor = if (dark) cs.accent1.shade300 else cs.accent1.shade600
    val minuteColor = if (dark) cs.accent1.shade200 else cs.accent1.shade700
    val textColor = if (dark) cs.accent1.shade500 else cs.accent1.shade400

    val hourAngle = 30f * hour + 0.5f * minute
    val minuteAngle = 6f * minute

    Surface(
        modifier = Modifier
            .padding(bottom = 24.dp)
            .size(156.dp),
        shape = CircleShape,
        color = bgColor,
        elevation = 8.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            Text(
                text = "12",
                style = MaterialTheme.typography.subtitle1.copy(
                    fontSize = 32.sp,
                    lineHeight = 32.sp,
                ),
                color = textColor,
                modifier = Modifier
                    .padding(10.dp, 2.dp)
                    .align(Alignment.TopCenter),
            )
            Text(
                text = "3",
                style = MaterialTheme.typography.subtitle1.copy(
                    fontSize = 32.sp
                ),
                color = textColor,
                modifier = Modifier
                    .padding(10.dp, 2.dp)
                    .align(Alignment.CenterEnd),
            )
            Text(
                text = "6",
                style = MaterialTheme.typography.subtitle1.copy(
                    fontSize = 32.sp
                ),
                color = textColor,
                modifier = Modifier
                    .padding(10.dp, 2.dp)
                    .align(Alignment.BottomCenter),
            )
            Text(
                text = "9",
                style = MaterialTheme.typography.subtitle1.copy(
                    fontSize = 32.sp
                ),
                color = textColor,
                modifier = Modifier
                    .padding(10.dp, 2.dp)
                    .align(Alignment.CenterStart),
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            rotate(
                degrees = hourAngle - 180f
            ) {

                drawLine(
                    strokeWidth = 12.dp.toPx(),
                    start = size.center.plus(Offset(0f, size.width / 5f)),
                    end = size.center,
                    color = hourColor,
                    cap = StrokeCap.Round
                )
            }
            rotate(
                degrees = minuteAngle - 180f
            ) {
                drawLine(
                    strokeWidth = 12.dp.toPx(),
                    start = size.center.plus(Offset(0f, size.width / 3f)),
                    end = size.center,
                    color = minuteColor,
                    cap = StrokeCap.Round
                )
            }
        }

    }

    PointMode.Polygon
}