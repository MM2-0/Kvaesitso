package de.mm20.launcher2.currencies

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import de.mm20.launcher2.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

class CurrencyRepository(
    private val context: Context,
) {

    private fun getOwnCurrency(): String {
        return try {
            java.util.Currency.getInstance(Locale.getDefault()).currencyCode ?: "USD"
        } catch (e: IllegalArgumentException) {
            "USD"
        }
    }

    private val currencySymbolAliases = buildMap {
        val ownCurrency = getOwnCurrency()

        put("€", "EUR")

        val dollarSymbolCurrencies = listOf(
            // dollar
            "AUD",
            "BBD",
            "BMD",
            "BND",
            "BSD",
            "BZD",
            "CAD",
            "FJD",
            "GYD",
            "HKD",
            "JMD",
            "KID",
            "KYD",
            "LRD",
            "NAD",
            "NZD",
            "SBD",
            "SGD",
            "SRD",
            "TTD",
            "TVD",
            "TWD",
            "USD",
            "XCD",
            // peso
            "ARS",
            "CLP",
            "COP",
            "DOP",
            "MXN",
            "UYU",
        )

        if (ownCurrency in dollarSymbolCurrencies) {
            put("$", ownCurrency)
        } else {
            put("$", "USD")
        }

        val poundSymbolCurrencies = listOf("EGP", "FKP", "GBP", "GIP", "SHP", "SDG", "SYP")
        if (ownCurrency in poundSymbolCurrencies) {
            put("£", ownCurrency)
        } else {
            put("£", "GBP")
        }

        put("¥", if (ownCurrency == "CNY") "CNY" else "JPY")
        put("₩", if (ownCurrency == "KPW") "KPW" else "KRW")

        put("kr", if (ownCurrency == "NOK") "NOK" else "SEK")
        put("kr.", "DKK")
        put("Kr", "ISK")

        put("zł", "PLN")
        put("Kč", "CZK")
        put("₴", "UAH")
        put("₽", "RUB")
        put("Ft", "HUF")

        put("₪", "ILS")
        put("TL", "TRY")

        put("R$", "BRL")

        put("₱", if (ownCurrency == "CUP") "CUP" else "PHP")
        put("฿", "THB")
        put("₹", "INR")

        return@buildMap
    }

    fun enableCurrencyUpdateWorker() {
        val currencyWorker =
            PeriodicWorkRequest.Builder(ExchangeRateWorker::class.java, 60, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "ExchangeRates",
            ExistingPeriodicWorkPolicy.KEEP, currencyWorker
        )
    }

    fun disableCurrencyUpdateWorker() {
        WorkManager.getInstance(context).cancelUniqueWork("ExchangeRates")
    }

    /**
     * Resolves currency symbol aliases to their full currency symbol.
     * (e.g. € -> EUR)
     * @param symbol The currency symbol to resolve
     * @return The resolved currency symbol or the original symbol if no alias was found
     */
    fun resolveAlias(symbol: String): String {
        return currencySymbolAliases[symbol] ?: symbol.uppercase()
    }

    suspend fun convertCurrency(
        fromCurrency: String,
        value: Double,
        toCurrency: String? = null
    ): List<Pair<String, Double>> {

        return withContext(Dispatchers.IO) {
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
                    Log.w(
                        "MM20",
                        "Exchange rate update dates do not match: $fromCurrency, $toCurrency"
                    )
                    return@withContext emptyList()
                }
                listOf(toCurrency to value * to.value / from.value)
            }
        }
    }

    suspend fun getKnownUnits(): List<String> {
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).currencyDao().getAllCurrencies().map { it.symbol }
        }
    }

    suspend fun isValidCurrency(symbol: String): Boolean {
        val isoSymbol = currencySymbolAliases[symbol] ?: symbol.uppercase()
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).currencyDao().exists(isoSymbol)
        }
    }

    suspend fun getLastUpdate(symbol: String): Long {
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).currencyDao().getLastUpdate(symbol)
        }
    }
}