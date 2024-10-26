package de.mm20.launcher2.files

import de.mm20.launcher2.files.providers.GDriveFile
import de.mm20.launcher2.files.providers.LocalFile
import de.mm20.launcher2.files.providers.NextcloudFile
import de.mm20.launcher2.files.providers.OwncloudFile
import de.mm20.launcher2.files.providers.PluginFile
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val filesModule = module {
    factory<SearchableRepository<File>>(named<File>()) {
        FileRepository(
            androidContext(),
            get(),
            get()
        )
    }
    factory<SearchableDeserializer>(named(LocalFile.Domain)) { LocalFileDeserializer(androidContext()) }
    factory<SearchableDeserializer>(named(OwncloudFile.Domain)) { OwncloudFileDeserializer() }
    factory<SearchableDeserializer>(named(NextcloudFile.Domain)) { NextcloudFileDeserializer() }
    factory<SearchableDeserializer>(named(GDriveFile.Domain)) { GDriveFileDeserializer() }
    factory<SearchableDeserializer>(named(PluginFile.Domain)) {
        PluginFileDeserializer(
            androidContext(),
            get()
        )
    }
}