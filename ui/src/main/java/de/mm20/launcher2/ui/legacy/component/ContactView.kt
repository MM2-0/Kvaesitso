package de.mm20.launcher2.ui.legacy.component

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.data.Contact
import de.mm20.launcher2.search.data.MissingPermission
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.legacy.search.SearchListView
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class ContactView : FrameLayout, KoinComponent {
    private val contacts: LiveData<List<Contact>?>

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleRes
    )

    init {
        val permissionsManager: PermissionsManager = get()
        View.inflate(context, R.layout.view_search_category_list, this)
        layoutTransition = LayoutTransition()
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        val card = findViewById<ViewGroup>(R.id.card)
        card.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        val viewModel: SearchVM by (context as AppCompatActivity).viewModels()
        contacts = viewModel.contactResults
        val list = findViewById<SearchListView>(R.id.list)
        contacts.observe(context as AppCompatActivity, {
            if (it == null) {
                visibility = View.GONE
                return@observe
            }
            if (it.isEmpty() && LauncherPreferences.instance.searchContacts && !permissionsManager.checkPermissionOnce(
                    PermissionGroup.Contacts
                )
            ) {
                visibility = View.VISIBLE
                list.submitItems(
                    listOf(
                        MissingPermission(
                            context.getString(R.string.permission_contact_search),
                            PermissionGroup.Contacts
                        )
                    )
                )
                return@observe
            }
            visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
            list.submitItems(it)
        })
    }
}