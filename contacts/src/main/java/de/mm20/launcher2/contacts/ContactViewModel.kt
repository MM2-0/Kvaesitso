package de.mm20.launcher2.contacts

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.search.data.Contact

class ContactViewModel(
    contactRepository: ContactRepository
) : ViewModel() {
    val contacts: LiveData<List<Contact>?> = contactRepository.contacts
}