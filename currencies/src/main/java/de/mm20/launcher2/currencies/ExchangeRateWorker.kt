package de.mm20.launcher2.currencies

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import java.text.SimpleDateFormat
import javax.xml.parsers.DocumentBuilderFactory

class ExchangeRateWorker(val context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Log.d("MM20", "Updating currency exchange rates")
        val httpClient = OkHttpClient()
        val request = Request.Builder()
                .url("https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml")
                .get()
                .build()
        try {
            val response = httpClient.newCall(request).execute()
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(response.body?.byteStream()
                    ?: return Result.retry())
            val cubes = document.getElementsByTagName("Cube")
            val values = mutableListOf<Pair<String, Double>>()
            var timestamp = System.currentTimeMillis()
            values += "EUR" to 1.0
            for (i in 0 until cubes.length) {
                val cube = cubes.item(i) as? Element ?: continue
                if (cube.hasAttribute("currency")) {
                    val symbol = cube.getAttribute("currency")
                    val value = cube.getAttribute("rate").toDoubleOrNull() ?: continue
                    values += symbol to value
                } else if (cube.hasAttribute("time")) {
                    val date = cube.getAttribute("time")
                    timestamp = SimpleDateFormat("yyyy-MM-dd").parse(date).time
                }
            }
            val currencies = values.map {
                Currency(
                        symbol = it.first,
                        value = it.second,
                        lastUpdate = timestamp
                ).toDatabaseEntity()
            }
            AppDatabase.getInstance(context).currencyDao().insertAll(currencies)
            return Result.success()
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return Result.retry()
        }
    }
}