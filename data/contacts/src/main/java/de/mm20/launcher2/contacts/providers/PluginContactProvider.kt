package de.mm20.launcher2.contacts.providers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import de.mm20.launcher2.plugin.PluginApi
import de.mm20.launcher2.plugin.QueryPluginApi
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.contracts.ContactPluginContract
import de.mm20.launcher2.plugin.contracts.ContactPluginContract.ContactColumns
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import de.mm20.launcher2.plugin.data.set
import de.mm20.launcher2.plugin.data.withColumns
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.asUpdateResult

internal class PluginContactProvider(
    private val context: Context,
    private val authority: String,
): QueryPluginApi<String, PluginContact>(context, authority), ContactProvider {

    private fun getPluginConfig(): QueryPluginConfig? {
        return PluginApi(authority, context.contentResolver).getSearchPluginConfig()
    }

    override fun Uri.Builder.appendQueryParameters(query: String): Uri.Builder {
        return appendQueryParameter(SearchPluginContract.Params.Query, query)
    }

    override fun Cursor.getData(): List<PluginContact>? {
        val config = getPluginConfig()
        val cursor = this

        if (config == null) {
            Log.e("MM20", "Plugin $authority returned null config")
            cursor.close()
            return null
        }

        val results = mutableListOf<PluginContact>()
        val timestamp = System.currentTimeMillis()
        cursor.withColumns(ContactColumns) {
            while (cursor.moveToNext()) {
                results.add(
                    PluginContact(
                        id = cursor[ContactColumns.Id] ?: continue,
                        name = cursor[ContactColumns.Name] ?: continue,
                        uri = Uri.parse(cursor[ContactColumns.Uri] ?: continue),
                        phoneNumbers = cursor[ContactColumns.PhoneNumbers] ?: emptyList(),
                        emailAddresses = cursor[ContactColumns.EmailAddresses] ?: emptyList(),
                        postalAddresses = cursor[ContactColumns.PostalAddresses] ?: emptyList(),
                        customActions = cursor[ContactColumns.CustomActions] ?: emptyList(),
                        photoUri = cursor[ContactColumns.PhotoUri]?.let { Uri.parse(it) },
                        authority = authority,
                        storageStrategy = config.storageStrategy,
                        timestamp = timestamp,
                        updatedSelf = {
                            if (it !is PluginContact) UpdateResult.TemporarilyUnavailable()
                            else refresh(it, timestamp).asUpdateResult()
                        }
                    )
                )
            }
        }
        return results
    }

    override fun PluginContact.toBundle(): Bundle {
        return Bundle().apply {
            set(ContactColumns.Id, id)
            set(ContactColumns.Uri, uri.toString())
            set(ContactColumns.Name, name)
            set(ContactColumns.PhoneNumbers, phoneNumbers)
            set(ContactColumns.EmailAddresses, emailAddresses)
            set(ContactColumns.PostalAddresses, postalAddresses)
            set(ContactColumns.CustomActions, customActions)
            set(ContactColumns.PhotoUri, photoUri?.toString())
        }
    }
}