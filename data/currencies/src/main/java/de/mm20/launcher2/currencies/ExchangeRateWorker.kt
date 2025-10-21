package de.mm20.launcher2.currencies

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import org.w3c.dom.Element
import java.text.SimpleDateFormat
import javax.xml.parsers.DocumentBuilderFactory

class ExchangeRateWorker(val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.d("MM20", "Updating currency exchange rates")
        val httpClient = HttpClient()
        try {
            val response = httpClient.get {
                url("https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml")
            }
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(response.bodyAsChannel().toInputStream())
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