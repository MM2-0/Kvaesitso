package de.mm20.launcher2.debug

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DebugInformationDumper {

    suspend fun dump(context: Context): String {
        val df = SimpleDateFormat("yyyy-MM-dd-HHmmss")
        val file = File(
            context.getExternalFilesDir(null),
            "kvaesitso-log-${df.format(Date(System.currentTimeMillis()))}"
        )
        withContext(Dispatchers.IO) {
            val timeout = System.currentTimeMillis() + 5000
            val fos = file.outputStream().writer()
            fos.write("Device: ${Build.DEVICE}\n")
            fos.write("SDK version: ${Build.VERSION.SDK_INT}\n")
            fos.write("====================================\n")
            val input =
                Runtime.getRuntime().exec("/system/bin/sh -c logcat").inputStream.bufferedReader()
            var line = input.readLine()
            while (line != null && System.currentTimeMillis() < timeout) {
                line = input.readLine()
                fos.write("$line\n")
            }
            fos.close()
        }
        return file.absolutePath
    }

    suspend fun exportDatabases(context: Context): String {
        val df = SimpleDateFormat("yyyy-MM-dd-HHmmss")
        val exportFile = File(
            context.getExternalFilesDir(null),
            "room-${df.format(Date(System.currentTimeMillis()))}.db"
        )
        withContext(Dispatchers.IO) {
            context.getDatabasePath("room").copyTo(exportFile)
        }
        return exportFile.absolutePath
    }

}