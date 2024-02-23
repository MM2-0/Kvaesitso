package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.base.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.*

class DatePartProvider : PartProvider {
    override fun getRanking(context: Context): Flow<Int> = flow {
        emit(1)
    }

    @Composable
    override fun Component(compactLayout: Boolean) {
        val time = LocalTime.current
        val verticalLayout = !compactLayout
        val context = LocalContext.current
        TextButton(
            colors = ButtonDefaults.textButtonColors(
                contentColor = LocalContentColor.current
            ),
            onClick = {
                val startMillis = System.currentTimeMillis()
                val builder = CalendarContract.CONTENT_URI.buildUpon()
                builder.appendPath("time")
                ContentUris.appendId(builder, startMillis)
                val intent = Intent(Intent.ACTION_VIEW)
                    .setData(builder.build())
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                context.tryStartActivity(intent)
            }) {
            if (verticalLayout) {
                Text(
                    text = DateUtils.formatDateTime(
                        context,
                        time,
                        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                val line1Format = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE")
                val line2Format =
                    DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMM dd")
                val format = SimpleDateFormat("$line1Format\n$line2Format")
                Text(
                    text = format.format(time),
                    lineHeight = 1.2.em,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }

}