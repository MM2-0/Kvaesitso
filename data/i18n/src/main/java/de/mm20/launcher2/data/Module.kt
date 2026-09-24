package de.mm20.launcher2.data

import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.search.StringNormalizer
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val i18nDataModule = module {
    single<StringNormalizer> {
        if (isAtLeastApiLevel(29)) IcuStringNormalizer(androidContext(), get())
        else CompatStringNormalizer()
    }
}