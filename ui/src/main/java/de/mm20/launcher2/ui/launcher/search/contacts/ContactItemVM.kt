package de.mm20.launcher2.ui.launcher.search.contacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.data.Contact
import de.mm20.launcher2.search.data.ContactInfo
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM

class ContactItemVM(
    val contact: Contact
) : SearchableItemVM(contact) {
    fun contact(context: Context, contactInfo: ContactInfo) {
        context.tryStartActivity(
            Intent(Intent.ACTION_VIEW).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(Uri.parse(contactInfo.data))
        )
    }
}