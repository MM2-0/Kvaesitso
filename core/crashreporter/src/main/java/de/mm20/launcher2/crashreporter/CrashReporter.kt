package de.mm20.launcher2.crashreporter

import android.content.Context
import android.content.Intent
import android.util.Log
import com.balsikandar.crashreporter.CrashReporter
import com.balsikandar.crashreporter.utils.AppUtils
import com.balsikandar.crashreporter.utils.CrashUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CrashReporter {
    fun logException(e: Exception) {
        if (e !is CancellationException) {
            CrashReporter.logException(e)
        }
        Log.e("MM20", Log.getStackTraceString(e))
    }

    suspend fun getCrashReports(): List<CrashReport> {
        val files = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val path = CrashReporter.getCrashReportPath()?.takeIf { it.isEmpty() } ?: CrashUtil.getDefaultPath()
            File(path).listFiles { f ->
                f.lastModified() > now - 7 * 24 * 60 * 60 * 1000L
            }?.sortedByDescending { it.lastModified() }
        }
        return files?.map { CrashReport.fromFile(it, false) } ?: emptyList()
    }

    suspend fun getCrashReport(filePath: String): CrashReport {
        return CrashReport.fromFile(File(filePath), true)
    }

    fun getDeviceInformation(context: Context): String {
        return AppUtils.getDeviceDetails(context)
    }
}