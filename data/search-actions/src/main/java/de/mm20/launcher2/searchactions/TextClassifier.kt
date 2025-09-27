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
        return when {
            query.matches(Regex("^\\S+@\\S+$")) -> TextClassificationResult(
                type = TextType.Email,
                text = query,
                email = query
            )

            query.matches(Regex("^\\+?[0-9- /.]{4,18}$")) -> TextClassificationResult(
                type = TextType.PhoneNumber,
                text = query,
                phoneNumber = query
            )

            query.matches(Regex("^(http(s)?://.)?(www\\.)?[-a-zA-Z0-9@:%._+~#=]{2,256}\\.[a-z]{2,6}\\b([-a-zA-Z0-9@:%_+.~#?&/=]*)$")) -> TextClassificationResult(
                type = TextType.Url,
                text = query,
                url = query
            )

            else -> {
                parseDate(context, query)?.let { return it }
                TextClassificationResult(type = TextType.Text, text = query)
            }
        }
    }

    private fun parseDate(context: Context, query: String): TextClassificationResult? {
        val dateTimeFormat = SimpleDateFormat(
            DateFormat.getBestDateTimePattern(
                Locale.getDefault(),
                "yMd, H:m"
            ),
            context.resources.configuration.locales[0]
        )
        try {
            dateTimeFormat.parse(query)?.let {
                val dateTime = LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                return TextClassificationResult(
                    type = TextType.DateTime,
                    text = query,
                    time = dateTime.toLocalTime(),
                    date = dateTime.toLocalDate(),
                )
            }
        } catch (_: ParseException) {
            // Not a datetime
        }
        val dateFormat = DateFormat.getDateFormat(context)
        try {
            dateFormat.parse(query)?.let {
                return TextClassificationResult(
                    type = TextType.Date,
                    text = query,
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
            timeFormat.parse(query)?.let {
                return TextClassificationResult(
                    type = TextType.Time,
                    text = query,
                    time = LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                        .toLocalTime(),
                )
            }
        } catch (_: ParseException) {
            // Nope, not a time
        }

        val seconds = context.getString(R.string.unit_second_symbol)
        if (query.matches(Regex("^[0-9]+ ${seconds}$"))) {
            val value = query.substringBefore(" ").toLong()
            return TextClassificationResult(
                type = TextType.Timespan,
                text = query,
                timespan = Duration.ofSeconds(value)
            )
        }

        val days = context.getString(R.string.unit_day_symbol)
        if (query.matches(Regex("^[0-9]+ ${days}$"))) {
            val value = query.substringBefore(" ").toLong()
            return TextClassificationResult(
                type = TextType.Timespan,
                text = query,
                timespan = Duration.ofDays(value)
            )
        }
        val minutes = context.getString(R.string.unit_minute_symbol)
        if (query.matches(Regex("^[0-9]+ ${minutes}$"))) {
            val value = query.substringBefore(" ").toLong()
            val then = LocalDateTime.now().plusMinutes(value)
            return TextClassificationResult(
                type = TextType.Timespan,
                text = query,
                timespan = Duration.ofMinutes(value)
            )
        }
        val hours = context.getString(R.string.unit_hour_symbol)
        if (query.matches(Regex("^[0-9]+ ${hours}$"))) {
            val value = query.substringBefore(" ").toLong()
            return TextClassificationResult(
                type = TextType.Timespan,
                text = query,
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