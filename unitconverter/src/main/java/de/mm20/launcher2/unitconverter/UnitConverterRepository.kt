package de.mm20.launcher2.unitconverter

import android.content.Context
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.unitconverter.converters.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface UnitConverterRepository {
    fun search(query:String): Flow<UnitConverter?>
}

internal class UnitConverterRepositoryImpl(val context: Context) : UnitConverterRepository, KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    val unitConverter = MutableLiveData<UnitConverter?>()

    override fun search(query: String): Flow<UnitConverter?> = channelFlow {
        if (query.isBlank()) {
            send(null)
            return@channelFlow
        }
        dataStore.data.map { it.unitConverterSearch.enabled }.collectLatest {
            if (it) {
                send(queryUnitConverter(query))
            } else {
                send(null)
            }
        }
    }

    private suspend fun queryUnitConverter(query: String): UnitConverter? {
        if (!query.matches(Regex("[0-9,.:]+ [A-Za-z/²³°.]+")) && !query.matches(Regex("[0-9,.:]+ [A-Za-z/²³°.]+ >> [A-Za-z/²³°]+"))) return null
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

        val converters = listOf(
            lazy { MassConverter(context) },
            lazy { LengthConverter(context) },
            lazy { CurrencyConverter() },
            lazy { DataConverter(context) },
            lazy { TimeConverter(context) },
            lazy { VelocityConverter(context) },
            lazy { AreaConverter(context) },
            lazy { TemperatureConverter(context) }
        )
        for (conv in converters) {
            val converter = conv.value
            if (!converter.isValidUnit(unitStr)) continue
            if (targetUnitStr != null && !converter.isValidUnit(targetUnitStr)) continue
            return converter.convert(context, unitStr, value, targetUnitStr)
        }
        return null
    }
}