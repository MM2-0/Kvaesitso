package de.mm20.launcher2.calculator

import org.koin.dsl.module

val calculatorModule = module {
    single<CalculatorRepository> { CalculatorRepositoryImpl(get()) }
}