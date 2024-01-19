package de.mm20.launcher2.contacts

import de.mm20.launcher2.search.Contact
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val contactsModule = module {
    factory { ContactRepository(androidContext(), get(), get()) }
    factory<SearchableRepository<Contact>>(named<Contact>()) { get<ContactRepository>() }
    factory<SearchableDeserializer>(named(AndroidContact.Domain)) { ContactDeserializer(get(), get()) }
}