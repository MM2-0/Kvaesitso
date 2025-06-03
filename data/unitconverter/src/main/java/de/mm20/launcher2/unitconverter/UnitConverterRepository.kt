package de.mm20.launcher2.unitconverter

import android.content.Context
import de.mm20.launcher2.currencies.CurrencyRepository
import de.mm20.launcher2.preferences.search.UnitConverterSettings
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.unitconverter.converters.AreaConverter
import de.mm20.launcher2.unitconverter.converters.Converter
import de.mm20.launcher2.unitconverter.converters.CurrencyConverter
import de.mm20.launcher2.unitconverter.converters.DataConverter
import de.mm20.launcher2.unitconverter.converters.LengthConverter
import de.mm20.launcher2.unitconverter.converters.MassConverter
import de.mm20.launcher2.unitconverter.converters.TemperatureConverter
import de.mm20.launcher2.unitconverter.converters.TimeConverter
import de.mm20.launcher2.unitconverter.converters.VelocityConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

interface UnitConverterRepository {
    fun search(query: String): Flow<UnitConverter?>
    suspend fun getAvailableConverters(includeCurrencies: Boolean): List<Converter>
}

internal class UnitConverterRepositoryImpl(
        private val context: Context,
        private val currencyRepository: CurrencyRepository,
        private val settings: UnitConverterSettings,
) : UnitConverterRepository, KoinComponent {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    init {
        scope.launch {
            settings.map { it.enabled && it.currencies }.distinctUntilChanged().collectLatest {
                if (it) currencyRepository.enableCurrencyUpdateWorker()
                else currencyRepository.disableCurrencyUpdateWorker()
            }
        }
    }

    override fun search(query: String): Flow<UnitConverter?> {
        if (query.isBlank()) return flowOf(null)
        return settings.distinctUntilChanged().map {
            if (!it.enabled) null else queryUnitConverter(query, it.currencies)
        }
    }

    override suspend fun getAvailableConverters(includeCurrencies: Boolean): List<Converter> {
        val converters =
                mutableListOf(
                        MassConverter(context),
                        LengthConverter(context),
                        DataConverter(context),
                        TimeConverter(context),
                        VelocityConverter(context),
                        AreaConverter(context),
                        TemperatureConverter(context)
                )
        if (includeCurrencies) converters.add(CurrencyConverter(currencyRepository))

        return converters
    }

    private suspend fun queryUnitConverter(
            query: String,
            includeCurrencies: Boolean
    ): UnitConverter? {
        val customSearchSeparator = settings.customVerb.first().ifBlank { "\\s+" }
        if (!query.matches(Regex("""[0-9,.:]+[^\\s]+ (-|>>|>|to|a|en|$customSearchSeparator) [^\\s]+""")))
                return null


        // 12cm -> 12 cm so computer sees correct number of tokens
        val parseableQuery = query.replace(Regex("""([0-9,.:])([a-zA-Z])"""), "$1 $2")
        val valueStr: String
        val unitStr: String
        val targetUnitStr: String?

        parseableQuery.split(" ").also {
            valueStr = it.get(0)
            unitStr = it.get(1)
            targetUnitStr = it.getOrNull(3)
        }
        val value =
                valueStr.toDoubleOrNull()
                        ?: valueStr.replace(',', '.').toDoubleOrNull() ?: return null

        val converters = getAvailableConverters(includeCurrencies)

        for (converter in converters) {
            if (!converter.isValidUnit(unitStr)) continue
            if (targetUnitStr != null && !converter.isValidUnit(targetUnitStr)) continue
            return converter.convert(context, unitStr, value, targetUnitStr)
        }
        return null
    }
}

