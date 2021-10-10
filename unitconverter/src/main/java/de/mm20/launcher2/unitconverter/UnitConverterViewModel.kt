package de.mm20.launcher2.unitconverter

import androidx.lifecycle.ViewModel

class UnitConverterViewModel(
    unitConverterRepository: UnitConverterRepository
): ViewModel() {
    val unitConverter = unitConverterRepository.unitConverter
}