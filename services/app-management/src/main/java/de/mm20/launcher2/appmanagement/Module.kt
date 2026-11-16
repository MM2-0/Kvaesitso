package de.mm20.launcher2.appmanagement

import org.koin.dsl.module

val appManagementModule = module {
    single { de.mm20.launcher2.appmanagement.battery.BatteryPolicy(get(), get()) }
    single { ShizukuManager(get()) }
    single { FossUpdateDataStore(get()) }
    single { FossUpdateRepository(get()) }
    single { FossUpdateBadgeProvider(get()) }
}
