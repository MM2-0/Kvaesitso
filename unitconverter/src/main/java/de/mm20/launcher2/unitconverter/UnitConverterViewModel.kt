package de.mm20.launcher2.unitconverter

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class UnitConverterViewModel(app: Application): AndroidViewModel(app) {
    val unitConverter = UnitConverterRepository.getInstance(app).unitConverter
}