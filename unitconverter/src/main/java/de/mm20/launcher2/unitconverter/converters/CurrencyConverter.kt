package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import android.icu.text.PluralRules
import android.icu.util.Currency
import android.os.Build
import de.mm20.launcher2.currencies.CurrencyRepository
import de.mm20.launcher2.search.data.CurrencyUnitConverter
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.unitconverter.Dimension
import de.mm20.launcher2.unitconverter.UnitValue
import java.text.DecimalFormat
import java.util.Locale
import java.util.Currency as JCurrency
import kotlin.math.abs

class CurrencyConverter(context: Context) : Converter() {

    override val dimension: Dimension = Dimension.Currency

    val repository = CurrencyRepository.getInstance(context)

    private val topCurrencies = arrayOf("USD", "EUR", "JPY", "GBP", "AUD")

    override suspend fun isValidUnit(symbol: String): Boolean {
        return repository.isValidCurrency(symbol)
    }


    private fun formatName(symbol: String, value: Double): String {
        val text = StringBuilder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val currency = Currency.getInstance(symbol) ?: return formatNameFallback(symbol)
            val pluralCount = PluralRules.forLocale(Locale.getDefault()).select(value)
            text.append(currency.getName(Locale.getDefault(), Currency.PLURAL_LONG_NAME, pluralCount, booleanArrayOf(false)))
                    .append(" ")
                    .append(repository.getFlag(symbol))

        } else {
            val currency = JCurrency.getInstance(symbol) ?: return formatNameFallback(symbol)
            text.append(currency.displayName)
                    .append(" ")
                    .append(repository.getFlag(symbol))
        }
        return text.toString()
    }

    private fun formatNameFallback(symbol: String): String {
        return "$symbol ${repository.getFlag(symbol)}"
    }

    private fun formatValue(symbol: String, value: Double): String {
        if (abs(value) > 1e10) {
            return DecimalFormat("#.###E0").apply {
            }.format(value)
        }
        val currency = JCurrency.getInstance(symbol)
        return if (currency != null) {
            val format = StringBuilder("#,##0")
            val digits = currency.defaultFractionDigits
            if (digits > 0) format.append(".")
            for (i in 0 until digits) format.append("0")
            DecimalFormat(format.toString()).format(value)
        } else {
            DecimalFormat("#,##0.00").format(value)
        }
    }


    override suspend fun convert(context: Context, fromUnit: String, value: Double, toUnit: String?): UnitConverter {
        val values = repository.convertCurrency(fromUnit, value, toUnit).map {
            UnitValue(it.second, it.first, formatName(it.first, it.second), formatValue(it.first, it.second))
        }.toMutableList()

        val ownCurrencySymbol = JCurrency.getInstance(Locale.getDefault()).currencyCode ?: "USD"
        val index = values.indexOfFirst { it.symbol == ownCurrencySymbol }

        val ownCurrency = if (index != -1) {
            values.removeAt(index)
        } else {
            null
        }

        values.sortBy {
            val i = topCurrencies.indexOf(it.symbol)
            if (i != -1) i.toString()
            else it.formattedName
        }

        if (ownCurrency != null) {
            values.add(0, ownCurrency)
        }

        val inputValue = UnitValue(value, fromUnit, formatName(fromUnit, value), formatValue(fromUnit, value))
        val lastUpdate = repository.getLastUpdate(fromUnit)
        return CurrencyUnitConverter(dimension, inputValue, values, lastUpdate)
    }
}