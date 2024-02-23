package de.mm20.launcher2.unitconverter

import de.mm20.launcher2.currencies.CurrencyRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val unitConverterModule = module {
    single { CurrencyRepository(androidContext()) }
    single<UnitConverterRepository> { UnitConverterRepositoryImpl(androidContext(), get(), get()) }
}