package de.mm20.launcher2.calculator

import androidx.lifecycle.ViewModel

class CalculatorViewModel: ViewModel() {
    val calculator = CalculatorRepository.getInstance().calculator
}