package de.mm20.launcher2.unitconverter

import android.content.Context
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.currencies.CurrencyRepository
import de.mm20.launcher2.search.BaseSearchableRepository
import de.mm20.launcher2.search.data.UnitConverter

class UnitConverterRepository(val context: Context) : BaseSearchableRepository() {

    val unitConverter = MutableLiveData<UnitConverter?>()

    override suspend fun search(query: String) {
        unitConverter.value = UnitConverter.search(context, query)
    }
}