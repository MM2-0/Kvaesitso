package de.mm20.launcher2.ui.widget.parts

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateUtils
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.ui.component.TextClock

@Composable
fun DatePart() {
    val context = LocalContext.current
    TextButton(onClick = {
        val startMillis = System.currentTimeMillis()
        val builder = CalendarContract.CONTENT_URI.buildUpon()
        builder.appendPath("time")
        ContentUris.appendId(builder, startMillis)
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(builder.build())
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }) {
        TextClock(
            formatFlags = DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}