package de.mm20.launcher2.currencies

import android.content.Context
import android.util.Log
import androidx.work.*
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.CurrencyEntity
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class CurrencyRepository(
    private val context: Context,
) {

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
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).currencyDao().exists(symbol)
        }
    }

    suspend fun getLastUpdate(symbol: String): Long {
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).currencyDao().getLastUpdate(symbol)
        }
    }
}