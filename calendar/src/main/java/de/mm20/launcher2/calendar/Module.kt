package de.mm20.launcher2.calendar

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val calendarModule = module {
    single<CalendarRepository> { CalendarRepositoryImpl(androidContext()) }
}