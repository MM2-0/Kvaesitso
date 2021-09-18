package de.mm20.launcher2.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import de.mm20.launcher2.search.data.Contact

class ContactViewModel(app: Application) : AndroidViewModel(app) {
    val contacts: LiveData<List<Contact>?> = ContactRepository.getInstance(app).contacts
}