package de.mm20.launcher2

import android.content.pm.PackageManager
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.search.TagDeserializer
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val baseModule = module {
    factory<PackageManager> { androidContext().packageManager }
    factory<SearchableDeserializer>(named(Tag.Domain)) { TagDeserializer() }
}