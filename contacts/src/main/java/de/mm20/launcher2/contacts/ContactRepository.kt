package de.mm20.launcher2.contacts

import android.content.Context
import de.mm20.launcher2.hiddenitems.HiddenItemsRepository
import de.mm20.launcher2.search.data.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

interface ContactRepository {
    fun search(query: String): Flow<List<Contact>>
}

class ContactRepositoryImpl(
    private val context: Context,
    hiddenItemsRepository: HiddenItemsRepository
) : ContactRepository {

    private val hiddenItems = hiddenItemsRepository.hiddenItemsKeys

    override fun search(query: String): Flow<List<Contact>> = channelFlow {
        val contacts = withContext(Dispatchers.IO) {
            Contact.search(context, query)
        }
        hiddenItems.collectLatest { hiddenItems ->
            val contactResults = withContext(Dispatchers.IO) {
                contacts.filter { !hiddenItems.contains(it.key) }
            }
            send(contactResults)
        }
    }
}