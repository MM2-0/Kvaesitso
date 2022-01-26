package de.mm20.launcher2.ui.legacy.component

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.asLiveData
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.MissingPermission
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.legacy.search.SearchListView
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class ContactView : FrameLayout, KoinComponent {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleRes
    )

    init {
        val permissionsManager: PermissionsManager = get()
        val dataStore: LauncherDataStore = get()
        View.inflate(context, R.layout.view_search_category_list, this)
        layoutTransition = LayoutTransition()
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        val card = findViewById<ViewGroup>(R.id.card)
        card.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        val viewModel: SearchVM by (context as AppCompatActivity).viewModels()

        val showMissingPermissionBanner = combine(
            dataStore.data.map { it.contactsSearch.enabled },
            permissionsManager.hasPermission(PermissionGroup.Contacts)
        ) { contactSearchEnabled, hasPermission ->
            !hasPermission && contactSearchEnabled
        }.asLiveData()

        val searchQuery = viewModel.searchQuery
        val contactResults = viewModel.contactResults

        val show = MediatorLiveData<Boolean>()
        show.addSource(showMissingPermissionBanner) {
            show.value = !searchQuery.value.isNullOrBlank() &&
                    (showMissingPermissionBanner.value == true || !contactResults.value.isNullOrEmpty())
        }
        show.addSource(contactResults) {
            show.value = !searchQuery.value.isNullOrBlank() &&
                    (showMissingPermissionBanner.value == true || !contactResults.value.isNullOrEmpty())
        }
        show.addSource(searchQuery) {
            show.value = !searchQuery.value.isNullOrBlank() &&
                    (showMissingPermissionBanner.value == true || !contactResults.value.isNullOrEmpty())
        }

        show.observe(context as AppCompatActivity) {
            visibility = if (it) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        val list = findViewById<SearchListView>(R.id.list)
        contactResults.observe(context as AppCompatActivity) {
            if (showMissingPermissionBanner.value == true) {
                list.submitItems(listOf(
                    MissingPermission(
                        context.getString(R.string.permission_contact_search),
                        PermissionGroup.Contacts,
                        secondaryActionLabel = context.getString(R.string.turn_off),
                        secondaryAction = {
                            lifecycleScope.launch {
                                dataStore.updateData {
                                    it.toBuilder()
                                        .setContactsSearch(it.contactsSearch.toBuilder().setEnabled(false))
                                        .build()
                                }
                            }
                        }
                    )
                ) + it)
            } else {
                list.submitItems(it)
            }
        }

        showMissingPermissionBanner.observe(context as AppCompatActivity) {
            if (it == true) {
                list.submitItems(listOf(
                    MissingPermission(
                        context.getString(R.string.permission_contact_search),
                        PermissionGroup.Contacts,
                        secondaryActionLabel = context.getString(R.string.turn_off)
                    )
                ) + contactResults.value!!)
            } else {
                list.submitItems(contactResults.value)
            }
        }
    }
}