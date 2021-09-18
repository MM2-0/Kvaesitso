package de.mm20.launcher2.helper

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DebugInformationDumper {

    fun dump(context: Context): String {
        val df = SimpleDateFormat("yyyy-MM-dd-HHmmss")
        val file = File(context.getExternalFilesDir(null), "kvaesitso-log-${df.format(Date(System.currentTimeMillis()))}")
        val fos = file.outputStream().writer()
        fos.write("Device: ${Build.DEVICE}\n")
        fos.write("SDK version: ${Build.VERSION.SDK_INT}\n")
        fos.write("====================================\n")
        Thread {
            val input = Runtime.getRuntime().exec("/system/bin/sh -c logcat").inputStream.bufferedReader()
            var line = input.readLine()
            while (line != null) {
                line = input.readLine()
                fos.write("$line\n")
            }
            fos.close()
        }.start()
        return file.absolutePath
    }

    fun exportDatabases(context: Context): String {
        val df = SimpleDateFormat("yyyy-MM-dd-HHmmss")
        val exportFile = File(context.getExternalFilesDir(null), "room-${df.format(Date(System.currentTimeMillis()))}.db")
        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                context.getDatabasePath("room").copyTo(exportFile)
            }
        }
        return exportFile.absolutePath
    }

}