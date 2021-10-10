package de.mm20.launcher2.calculator

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val calculatorModule = module {
    single { CalculatorRepository() }
    viewModel { CalculatorViewModel(get()) }
}