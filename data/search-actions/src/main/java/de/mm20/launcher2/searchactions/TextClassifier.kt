package de.mm20.launcher2.searchactions

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.text.format.DateFormat
import android.util.Patterns
import java.net.MalformedURLException
import java.net.URL
import java.text.ParseException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import androidx.core.net.toUri

internal interface TextClassifier {
    suspend fun classify(context: Context, query: String): TextClassificationResult
}

internal class TextClassifierImpl : TextClassifier {
    override suspend fun classify(context: Context, query: String): TextClassificationResult {
        val trimmedQuery = query.trim()

        var email: String? = null
        if (Patterns.EMAIL_ADDRESS.matcher(trimmedQuery).matches()) {
            email = trimmedQuery
        }

        var phoneNumber: String? = null
        if (Patterns.PHONE.matcher(trimmedQuery).matches()) {
            phoneNumber = trimmedQuery
        }

        var url: Uri? = null

        if (Patterns.WEB_URL.matcher(trimmedQuery).matches()) {
            url = trimmedQuery.toUri()
            if (url.scheme == null) {
                url = url.buildUpon().scheme("https").build()
            }
        }

        val parsedDate = parseDate(context, trimmedQuery)

        if (parsedDate != null) {
            return parsedDate.copy(
                email = email,
                url = url
            )
        }

        return TextClassificationResult(
            text = trimmedQuery,
            phoneNumber = phoneNumber,
            email = email,
            url = url
        )
    }

    private fun parseDate(
        context: Context,
        trimmedQuery: String
    ): TextClassificationResult? {
        val dateFormat = SimpleDateFormat(
            DateFormat.getBestDateTimePattern(
                Locale.getDefault(),
                "yyyyMMdd, h:m a"
            ),
            context.resources.configuration.locales[0]
        )
        try {
            dateFormat.parse(trimmedQuery)?.let {
                val dateTime = LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                return TextClassificationResult(
                    text = trimmedQuery,
                    time = dateTime.toLocalTime(),
                    date = dateTime.toLocalDate(),
                )
            }
        } catch (_: ParseException) {
            // Not a 12h datetime
        }

        dateFormat.applyPattern(
            DateFormat.getBestDateTimePattern(
                Locale.getDefault(),
                "yyyyMMdd, H:m"
            ),
        )
        try {
            dateFormat.parse(trimmedQuery)?.let {
                val dateTime = LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                return TextClassificationResult(
                    text = trimmedQuery,
                    time = dateTime.toLocalTime(),
                    date = dateTime.toLocalDate(),
                )
            }
        } catch (_: ParseException) {
            // Not a 24h datetime
        }
        dateFormat.applyPattern(
            DateFormat.getBestDateTimePattern(
                Locale.getDefault(),
                "yyyyMMdd"
            ),
        )
        try {
            dateFormat.parse(trimmedQuery)?.let {
                return TextClassificationResult(
                    text = trimmedQuery,
                    date = LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                        .toLocalDate()
                )
            }
        } catch (_: ParseException) {
            // Not a date either
        }

        dateFormat.applyPattern(
            DateFormat.getBestDateTimePattern(
                Locale.getDefault(),
                "h:m a"
            ),
        )
        try {
            dateFormat.parse(trimmedQuery)?.let {
                return TextClassificationResult(
                    text = trimmedQuery,
                    time = LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                        .toLocalTime(),
                )
            }
        } catch (_: ParseException) {
            // Not a 12h time
        }

        dateFormat.applyPattern(
            DateFormat.getBestDateTimePattern(
                Locale.getDefault(),
                "H:m"
            ),
        )
        try {
            dateFormat.parse(trimmedQuery)?.let {
                return TextClassificationResult(
                    text = trimmedQuery,
                    time = LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                        .toLocalTime(),
                )
            }
        } catch (_: ParseException) {
            // Not a 24h time
        }

        val seconds = context.getString(R.string.unit_second_symbol)
        val secondsMatch = Regex("^([0-9]+)\\s?${seconds}$").find(trimmedQuery)
        if (secondsMatch != null) {
            val value = secondsMatch.groups[1]!!.value.toLong()
            return TextClassificationResult(
                text = trimmedQuery,
                timespan = Duration.ofSeconds(value)
            )
        }

        val days = context.getString(R.string.unit_day_symbol)
        val daysMatch = Regex("^([0-9]+)\\s?${days}$").find(trimmedQuery)
        if (daysMatch != null) {
            val value = daysMatch.groups[1]!!.value.toLong()
            return TextClassificationResult(
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
                text = trimmedQuery,
                timespan = Duration.ofMinutes(value)
            )
        }

        val hours = context.getString(R.string.unit_hour_symbol)
        val hoursMatch = Regex("^([0-9]+)\\s?${hours}$").find(trimmedQuery)
        if (hoursMatch != null) {
            val value = hoursMatch.groups[1]!!.value.toLong()
            return TextClassificationResult(
                text = trimmedQuery,
                timespan = Duration.ofHours(value)
            )
        }

        return null
    }
}

data class TextClassificationResult(
    val text: String,
    val email: String? = null,
    val phoneNumber: String? = null,
    val time: LocalTime? = null,
    val date: LocalDate? = null,
    val timespan: Duration? = null,
    val url: Uri? = null,
)