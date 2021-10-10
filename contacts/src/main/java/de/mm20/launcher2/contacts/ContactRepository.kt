package de.mm20.launcher2.contacts

import android.content.Context
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.hiddenitems.HiddenItemsRepository
import de.mm20.launcher2.search.BaseSearchableRepository
import de.mm20.launcher2.search.data.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactRepository(
    val context: Context,
    hiddenItemsRepository: HiddenItemsRepository
) : BaseSearchableRepository() {

    val contacts = MediatorLiveData<List<Contact>?>()

    private val allContacts = MutableLiveData<List<Contact>?>(emptyList())
    private val hiddenItemKeys = hiddenItemsRepository.hiddenItemsKeys

    init {
        contacts.addSource(hiddenItemKeys) { keys ->
            contacts.value = allContacts.value?.filter { !keys.contains(it.key) }
        }
        contacts.addSource(allContacts) { c ->
            contacts.value = c?.filter { hiddenItemKeys.value?.contains(it.key) != true }
        }
    }

    override suspend fun search(query: String) {
        if (query.isBlank()) {
            allContacts.value = null
            return
        }
        val results = withContext(Dispatchers.IO) {
            Contact.search(context, query)
        }
        allContacts.value = results
    }
}