package de.mm20.launcher2.unitconverter

import android.content.Context
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.search.data.UnitConverter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

interface UnitConverterRepository {
    fun search(query:String): Flow<UnitConverter?>
}

class UnitConverterRepositoryImpl(val context: Context) : UnitConverterRepository {

    val unitConverter = MutableLiveData<UnitConverter?>()

    override fun search(query: String): Flow<UnitConverter?> = channelFlow {
        send(UnitConverter.search(context, query))
    }
}