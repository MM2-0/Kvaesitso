package de.mm20.launcher2.plugin.contracts

import de.mm20.launcher2.search.contact.CustomContactAction
import de.mm20.launcher2.search.contact.EmailAddress
import de.mm20.launcher2.search.contact.PhoneNumber
import de.mm20.launcher2.search.contact.PostalAddress

abstract class ContactPluginContract {
    object ContactColumns: Columns() {
        /**
         * The unique ID of the contact.
         */
        val Id = column<String>("id")

        /**
         * The display name of the contact.
         * First name + last name, if applicable.
         */
        val Name = column<String>("name")

        val PhoneNumbers = column<List<PhoneNumber>>("phone_numbers")
        val EmailAddresses = column<List<EmailAddress>>("email_addresses")
        val PostalAddresses = column<List<PostalAddress>>("postal_addresses")
        val CustomActions = column<List<CustomContactAction>>("custom_actions")

        val PhotoUri = column<String>("photo_uri")
    }
}