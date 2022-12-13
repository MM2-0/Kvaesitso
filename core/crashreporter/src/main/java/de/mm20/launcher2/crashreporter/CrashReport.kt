package de.mm20.launcher2.crashreporter

import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class CrashReport(
    val type: CrashReportType,
    val time: Date,
    val summary: String,
    val stacktrace: String?,
    val filePath: String
) {
    companion object {
        suspend fun fromFile(file: File, loadStackTrace: Boolean): CrashReport {
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val time = df.parse(file.name.replace("[a-zA-Z_.]", ""))
            val content = if (loadStackTrace) {
                withContext(Dispatchers.IO) {
                    file.inputStream().bufferedReader().use {
                        it.readText()
                    }
                }
            } else null
            val summary = content?.substringBefore("\n")
                ?: withContext(Dispatchers.IO) {
                    file.inputStream().bufferedReader().use {
                        it.readLine()
                    }
                }
            return CrashReport(
                type = if (file.name.endsWith("_crash.txt")) CrashReportType.Crash else CrashReportType.Exception,
                time = time,
                summary = summary,
                stacktrace = content,
                filePath = file.absolutePath
            )
        }
    }
}

enum class CrashReportType {
    Exception,
    Crash
}