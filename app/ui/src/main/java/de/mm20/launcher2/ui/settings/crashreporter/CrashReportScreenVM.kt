package de.mm20.launcher2.ui.settings.crashreporter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.crashreporter.CrashReport
import de.mm20.launcher2.crashreporter.CrashReporter
import kotlinx.coroutines.flow.flow
import java.io.File
import java.net.URLEncoder

class CrashReportScreenVM : ViewModel() {
    fun getCrashReport(fileName: String) = flow<CrashReport?> {
        emit(CrashReporter.getCrashReport(fileName))
    }

    fun getDeviceInformation(context: Context): String {
        return CrashReporter.getDeviceInformation(context)
    }

    fun createIssue(context: Context, crashReport: CrashReport) {
        val stacktrace = crashReport.stacktrace?.lines()?.let {
            if (it.size > 15) it.subList(0, 15)
                .joinToString("\n") + "\n[${it.size - 15} lines truncated]"
            else it.joinToString("\n")
        } ?: ""
        val body =
            "## Description\n\n" +
                    "*Please provide as many information about the crash as possible (What did you do before the crash happened? Steps to reproduce?)*\n\n" +
                    "## Stack trace\n\n" +
                    "```\n" +
                    "${stacktrace}\n" +
                    "```\n\n" +
                    "## Device info\n" +
                    "${getDeviceInformation(context).replace("\n", "<br>")}\n"
        val url = "https://github.com/MM2-0/Kvaesitso/issues/new?labels=crash+report&body=${
            URLEncoder.encode(
                body,
                "utf8"
            )
        }"
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        })
    }

    fun shareCrashReport(context: Context, crashReport: CrashReport) {

        val uri = FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".fileprovider",
            File(crashReport.filePath)
        )
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_TEXT, CrashReporter.getDeviceInformation(context))
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }

}
