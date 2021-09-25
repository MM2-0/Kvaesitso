package de.mm20.launcher2.ui.widget.parts

import android.text.format.DateUtils
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import de.mm20.launcher2.ui.component.TextClock

@Composable
fun DatePart() {
    TextClock(
        formatFlags = DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR,
        style = MaterialTheme.typography.subtitle1
    )
}