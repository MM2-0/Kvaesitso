package de.mm20.launcher2.calculator

import androidx.lifecycle.ViewModel

class CalculatorViewModel(
    calculatorRepository: CalculatorRepository
): ViewModel() {
    val calculator = calculatorRepository.calculator
}