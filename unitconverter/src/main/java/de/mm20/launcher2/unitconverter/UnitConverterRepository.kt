package de.mm20.launcher2.unitconverter

import android.content.Context
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.currencies.CurrencyRepository
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.unitconverter.converters.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface UnitConverterRepository {
    fun search(query: String): Flow<UnitConverter?>
}

internal class UnitConverterRepositoryImpl(
    private val context: Context,
    private val currencyRepository: CurrencyRepository,
) : UnitConverterRepository, KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    val unitConverter = MutableLiveData<UnitConverter?>()

    init {
        scope.launch {
            dataStore.data.map { it.unitConverterSearch }.distinctUntilChanged().collectLatest {
                if (it.enabled && it.currencies) currencyRepository.enableCurrencyUpdateWorker()
                else currencyRepository.disableCurrencyUpdateWorker()
            }
        }
    }

    override fun search(query: String): Flow<UnitConverter?> = channelFlow {
        if (query.isBlank()) {
            send(null)
            return@channelFlow
        }
        dataStore.data.map { it.unitConverterSearch }.collectLatest {
            if (it.enabled) {
                send(queryUnitConverter(query, it.currencies))
            } else {
                send(null)
            }
        }
    }

    private suspend fun queryUnitConverter(
        query: String,
        includeCurrencies: Boolean
    ): UnitConverter? {
        if (!query.matches(Regex("[0-9,.:]+ [^\\s]+")) && !query.matches(Regex("[0-9,.:]+ [^\\s]+ >> [^\\s]+"))) return null
        val valueStr: String
        val unitStr: String
        val targetUnitStr: String?

        query.split(" ").also {
            valueStr = it.get(0)
            unitStr = it.get(1)
            targetUnitStr = it.getOrNull(3)
        }
        val value = valueStr.toDoubleOrNull() ?: valueStr.replace(',', '.').toDoubleOrNull()
        ?: return null

        val converters = mutableListOf(
            MassConverter(context),
            LengthConverter(context),
            DataConverter(context),
            TimeConverter(context),
            VelocityConverter(context),
            AreaConverter(context),
            TemperatureConverter(context)
        )

        if (includeCurrencies) converters.add(CurrencyConverter(currencyRepository))

        for (converter in converters) {
            if (!converter.isValidUnit(unitStr)) continue
            if (targetUnitStr != null && !converter.isValidUnit(targetUnitStr)) continue
            return converter.convert(context, unitStr, value, targetUnitStr)
        }
        return null
    }
}