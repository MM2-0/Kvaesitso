package de.mm20.launcher2.sdk.contacts

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.contracts.ContactPluginContract.ContactColumns
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import de.mm20.launcher2.plugin.data.buildCursor
import de.mm20.launcher2.plugin.data.get
import de.mm20.launcher2.sdk.base.QueryPluginProvider

abstract class ContactProvider(
    config: QueryPluginConfig,
) : QueryPluginProvider<String, Contact>(config) {

    override fun getQuery(uri: Uri): String? {
        return uri.getQueryParameter(SearchPluginContract.Params.Query)
    }

    override fun List<Contact>.toCursor(): Cursor {
        return buildCursor(ContactColumns, this) {
            put(ContactColumns.Id, it.id)
            put(ContactColumns.Uri, it.uri.toString())
            put(ContactColumns.Name, it.name)
            put(ContactColumns.PhoneNumbers, it.phoneNumbers)
            put(ContactColumns.EmailAddresses, it.emailAddresses)
            put(ContactColumns.PostalAddresses, it.postalAddresses)
            put(ContactColumns.CustomActions, it.customActions)
            put(ContactColumns.PhotoUri, it.photoUri?.toString())
        }
    }

    override fun Bundle.toResult(): Contact? {
        return Contact(
            id = get(ContactColumns.Id) ?: return null,
            name = get(ContactColumns.Name) ?: return null,
            uri = Uri.parse(get(ContactColumns.Uri) ?: return null),
            phoneNumbers = get(ContactColumns.PhoneNumbers) ?: emptyList(),
            emailAddresses = get(ContactColumns.EmailAddresses) ?: emptyList(),
            postalAddresses = get(ContactColumns.PostalAddresses) ?: emptyList(),
            customActions = get(ContactColumns.CustomActions) ?: emptyList(),
            photoUri = get(ContactColumns.PhotoUri)?.let { Uri.parse(it) },
        )
    }

    final override fun getPluginType(): PluginType {
        return PluginType.ContactSearch
    }
}