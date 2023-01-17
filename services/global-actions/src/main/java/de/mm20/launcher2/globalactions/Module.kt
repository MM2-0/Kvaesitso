package de.mm20.launcher2.globalactions

import org.koin.dsl.module

val globalActionsModule = module {
    single { GlobalActionsService() }
}