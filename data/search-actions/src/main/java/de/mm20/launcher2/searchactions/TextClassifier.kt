package de.mm20.launcher2.searchactions

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.text.format.DateFormat
import java.text.ParseException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

internal interface TextClassifier {
    suspend fun classify(context: Context, query: String): TextClassificationResult
}

internal class TextClassifierImpl : TextClassifier {
    override suspend fun classify(context: Context, query: String): TextClassificationResult {
        val trimmedQuery = query.trim()
        return when {
            trimmedQuery.matches(Regex("^\\S+@\\S+$")) -> TextClassificationResult(
                type = TextType.Email,
                text = trimmedQuery,
                email = trimmedQuery
            )

            trimmedQuery.matches(Regex("^\\+?[0-9- /.]{4,18}$")) -> TextClassificationResult(
                type = TextType.PhoneNumber,
                text = trimmedQuery,
                phoneNumber = trimmedQuery
            )

            trimmedQuery.matches(Regex("^(http(s)?://.)?(www\\.)?[-a-zA-Z0-9@:%._+~#=]{2,256}\\.[a-z]{2,6}\\b([-a-zA-Z0-9@:%_+.~#?&/=]*)$")) -> TextClassificationResult(
                type = TextType.Url,
                text = trimmedQuery,
                url = trimmedQuery
            )

            else -> {
                parseDate(context, trimmedQuery)?.let { return it }
                TextClassificationResult(type = TextType.Text, text = trimmedQuery)
            }
        }
    }

    private fun parseDate(context: Context, trimmedQuery: String): TextClassificationResult? {
        val dateTimeFormat = SimpleDateFormat(
            DateFormat.getBestDateTimePattern(
                Locale.getDefault(),
                "yMd, H:m"
            ),
            context.resources.configuration.locales[0]
        )
        try {
            dateTimeFormat.parse(trimmedQuery)?.let {
                val dateTime = LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                return TextClassificationResult(
                    type = TextType.DateTime,
                    text = trimmedQuery,
                    time = dateTime.toLocalTime(),
                    date = dateTime.toLocalDate(),
                )
            }
        } catch (_: ParseException) {
            // Not a datetime
        }
        val dateFormat = DateFormat.getDateFormat(context)
        try {
            dateFormat.parse(trimmedQuery)?.let {
                return TextClassificationResult(
                    type = TextType.Date,
                    text = trimmedQuery,
                    date = LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                        .toLocalDate()
                )
            }
        } catch (_: ParseException) {
            // Not a date either
        }
        val timeFormat = SimpleDateFormat(
            DateFormat.getBestDateTimePattern(
                Locale.getDefault(),
                if (DateFormat.is24HourFormat(context)) "H:m" else "h:m a"
            ),
            context.resources.configuration.locales[0]
        )
        try {
            timeFormat.parse(trimmedQuery)?.let {
                return TextClassificationResult(
                    type = TextType.Time,
                    text = trimmedQuery,
                    time = LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                        .toLocalTime(),
                )
            }
        } catch (_: ParseException) {
            // Nope, not a time
        }

        val seconds = context.getString(R.string.unit_second_symbol)
        val secondsMatch = Regex("^([0-9]+)\\s?${seconds}$").find(trimmedQuery)
        if (secondsMatch != null) {
            val value = secondsMatch.groups[1]!!.value.toLong()
            return TextClassificationResult(
                type = TextType.Timespan,
                text = trimmedQuery,
                timespan = Duration.ofSeconds(value)
            )
        }

        val days = context.getString(R.string.unit_day_symbol)
        val daysMatch = Regex("^([0-9]+)\\s?${days}$").find(trimmedQuery)
        if (daysMatch != null) {
            val value = daysMatch.groups[1]!!.value.toLong()
            return TextClassificationResult(
                type = TextType.Timespan,
                text = trimmedQuery,
                timespan = Duration.ofDays(value)
            )
        }

        val minutes = context.getString(R.string.unit_minute_symbol)
        val minutesMatch = Regex("^([0-9]+)\\s?${minutes}$").find(trimmedQuery)
        if (minutesMatch != null) {
            val value = minutesMatch.groups[1]!!.value.toLong()
            val then = LocalDateTime.now().plusMinutes(value)
            return TextClassificationResult(
                type = TextType.Timespan,
                text = trimmedQuery,
                timespan = Duration.ofMinutes(value)
            )
        }

        val hours = context.getString(R.string.unit_hour_symbol)
        val hoursMatch = Regex("^([0-9]+)\\s?${hours}$").find(trimmedQuery)
        if (hoursMatch != null) {
            val value = hoursMatch.groups[1]!!.value.toLong()
            return TextClassificationResult(
                type = TextType.Timespan,
                text = trimmedQuery,
                timespan = Duration.ofHours(value)
            )
        }

        return null
    }
}

data class TextClassificationResult(
    val type: TextType,
    val text: String,
    val email: String? = null,
    val phoneNumber: String? = null,
    val time: LocalTime? = null,
    val date: LocalDate? = null,
    val timespan: Duration? = null,
    val url: String? = null,
)

enum class TextType {
    Text,
    Email,
    Url,
    PhoneNumber,
    DateTime,
    Date,
    Time,
    Timespan,
}