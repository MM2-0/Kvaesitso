package de.mm20.launcher2

import android.content.pm.PackageManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val baseModule = module {
    factory<PackageManager> { androidContext().packageManager }
}