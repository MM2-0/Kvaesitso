package de.mm20.launcher2.ui.legacy.component

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import de.mm20.launcher2.contacts.ContactViewModel
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.data.Contact
import de.mm20.launcher2.search.data.MissingPermission
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.search.SearchListView

class ContactView : FrameLayout {
    private val contacts: LiveData<List<Contact>?>

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    init {
        View.inflate(context, R.layout.view_search_category_list, this)
        layoutTransition = LayoutTransition()
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        val card = findViewById<ViewGroup>(R.id.card)
        card.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        contacts = ViewModelProvider(context as AppCompatActivity).get(ContactViewModel::class.java).contacts
        val list = findViewById<SearchListView>(R.id.list)
        contacts.observe(context as AppCompatActivity, {
            if (it == null) {
                visibility = View.GONE
                return@observe
            }
            if (it.isEmpty() && LauncherPreferences.instance.searchContacts && !PermissionsManager.checkPermission(context, PermissionsManager.CONTACTS)) {
                visibility = View.VISIBLE
                list.submitItems(listOf(
                        MissingPermission(
                                context.getString(R.string.permission_contact_search),
                                PermissionsManager.CONTACTS
                        )
                ))
                return@observe
            }
            visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
            list.submitItems(it)
        })
    }
}