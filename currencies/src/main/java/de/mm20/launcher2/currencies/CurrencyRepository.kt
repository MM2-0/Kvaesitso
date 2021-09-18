package de.mm20.launcher2.currencies

import android.content.Context
import android.util.Log
import androidx.work.*
import de.mm20.launcher2.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CurrencyRepository(val context: Context) {

    init {
        val currencyWorker = PeriodicWorkRequest.Builder(ExchangeRateWorker::class.java, 60, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance().enqueueUniquePeriodicWork("ExchangeRates",
                ExistingPeriodicWorkPolicy.REPLACE, currencyWorker)
    }

    suspend fun convertCurrency(fromCurrency: String, value: Double, toCurrency: String? = null): List<Pair<String, Double>> {

        return withContext<List<Pair<String, Double>>>(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(context)
                    .currencyDao()

            val from = Currency(dao.getCurrency(fromCurrency) ?: return@withContext emptyList())

            return@withContext if (toCurrency == null) {
                dao.getAllCurrencies().mapNotNull {
                    val to = Currency(it)
                    if (from.lastUpdate != to.lastUpdate) {
                        Log.w("MM20", "Exchange rate update dates do not match: $fromCurrency, $it")
                        return@mapNotNull null
                    }
                    if (from.symbol == to.symbol) return@mapNotNull null
                    to.symbol to value * to.value / from.value
                }
            } else {
                val to = Currency(dao.getCurrency(toCurrency) ?: return@withContext emptyList())
                if (from.lastUpdate != to.lastUpdate) {
                    Log.w("MM20", "Exchange rate update dates do not match: $fromCurrency, $toCurrency")
                    return@withContext emptyList()
                }
                listOf(toCurrency to value * to.value / from.value)
            }
        }
    }

    fun getFlag(currencySymbol: String): String {
        return when (currencySymbol) {
            "EUR" -> "\uD83C\uDDEA\uD83C\uDDFA" // European Union
            "USD" -> "\uD83C\uDDFA\uD83C\uDDF8" // United States
            "JPY" -> "\uD83C\uDDEF\uD83C\uDDF5" // Japan
            "GBP" -> "\uD83C\uDDEC\uD83C\uDDE7" // United Kingdom
            "AUD" -> "\uD83C\uDDE6\uD83C\uDDFA" // Australia
            "CAD" -> "\uD83C\uDDE8\uD83C\uDDE6" // Canada
            "CHF" -> "\uD83C\uDDE8\uD83C\uDDED" // Switzerland
            "CNY" -> "\uD83C\uDDE8\uD83C\uDDF3" // China
            "SEK" -> "\uD83C\uDDF8\uD83C\uDDEA" // Sweden
            "NZD" -> "\uD83C\uDDF3\uD83C\uDDFF" // New Zealand

            "HKD" -> "\uD83C\uDDED\uD83C\uDDF0" // Hong Kong
            "IDR" -> "\uD83C\uDDEE\uD83C\uDDE9" // Indonesia
            "ILS" -> "\uD83C\uDDEE\uD83C\uDDF1" // Israel
            "DKK" -> "\uD83C\uDDE9\uD83C\uDDF0" // Denmark
            "INR" -> "\uD83C\uDDEE\uD83C\uDDF3" // India
            "MXN" -> "\uD83C\uDDF2\uD83C\uDDFD" // Mexico
            "CZK" -> "\uD83C\uDDE8\uD83C\uDDFF" // Czechia
            "SGD" -> "\uD83C\uDDF8\uD83C\uDDEC" // Singapore
            "THB" -> "\uD83C\uDDF9\uD83C\uDDED" // Thailand
            "HRK" -> "\uD83C\uDDED\uD83C\uDDF7" // Croatia
            "MYR" -> "\uD83C\uDDF2\uD83C\uDDFE" // Malaysia
            "NOK" -> "\uD83C\uDDF3\uD83C\uDDF4" // Norway
            "BGN" -> "\uD83C\uDDE7\uD83C\uDDEC" // Bulgaria
            "PHP" -> "\uD83C\uDDF5\uD83C\uDDED" // Philippines
            "PLN" -> "\uD83C\uDDF5\uD83C\uDDF1" // Poland
            "ZAR" -> "\uD83C\uDDFF\uD83C\uDDE6" // South Africa
            "ISK" -> "\uD83C\uDDEE\uD83C\uDDF8" // Iceland
            "BRL" -> "\uD83C\uDDE7\uD83C\uDDF7" // Brazil
            "RON" -> "\uD83C\uDDF7\uD83C\uDDF4" // Romania
            "TRY" -> "\uD83C\uDDF9\uD83C\uDDF7" // Turkey
            "RUB" -> "\uD83C\uDDF7\uD83C\uDDFA" // Russia
            "KRW" -> "\uD83C\uDDF0\uD83C\uDDF7" // South Korea
            "HUF" -> "\uD83C\uDDED\uD83C\uDDFA" // Hungary

            else -> ""
        }
    }

    suspend fun isValidCurrency(symbol: String): Boolean {
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).currencyDao().exists(symbol)
        }
    }

    suspend fun getLastUpdate(symbol: String): Long {
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).currencyDao().getLastUpdate(symbol)
        }
    }

    companion object {
        private lateinit var instance: CurrencyRepository
        fun getInstance(context: Context): CurrencyRepository {
            if (!::instance.isInitialized) instance = CurrencyRepository(context.applicationContext)
            return instance
        }
    }
}