package de.mm20.launcher2.ui.legacy.search

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.Scene
import de.mm20.launcher2.badges.BadgeRepository
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.lifecycleOwner
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.ktx.setStartCompoundDrawable
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.search.data.Contact
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import de.mm20.launcher2.ui.legacy.view.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.URLEncoder

class ContactDetailRepresentation : Representation, KoinComponent {

    private val iconRepository: IconRepository by inject()
    private val badgeRepository: BadgeRepository by inject()

    private var job: Job? = null

    override fun getScene(
        rootView: SearchableView,
        searchable: Searchable,
        previousRepresentation: Int?
    ): Scene {
        val contact = searchable as Contact
        val context = rootView.context as AppCompatActivity
        val scene =
            Scene.getSceneForLayout(rootView, R.layout.view_contact_detail, rootView.context)
        scene.setEnterAction {
            with(rootView) {
                findViewById<LauncherIconView>(R.id.icon).apply {
                    shape = LauncherIconView.getDefaultShape(context)
                    icon = iconRepository.getIconIfCached(contact)
                    job = rootView.scope.launch {
                        rootView.lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            launch {
                                iconRepository.getIcon(contact, (84 * rootView.dp).toInt())
                                    .collectLatest {
                                        icon = it
                                    }
                            }
                            launch {
                                badgeRepository.getBadge(contact.badgeKey).collectLatest {
                                    badge = it
                                }
                            }
                        }
                    }
                }
                findViewById<TextView>(R.id.contactName).text = contact.displayName
                findViewById<SwipeCardView>(R.id.contactCard).also {
                    it.leftAction = FavoriteSwipeAction(context, contact)
                    it.rightAction = HideSwipeAction(context, contact)
                }
                val toolbar = findViewById<ToolbarView>(R.id.contactToolbar)
                setupMenu(this, toolbar, contact)
                addShortcuts(rootView, contact)
            }
        }
        scene.setExitAction {
            job?.cancel()
        }
        return scene
    }

    private fun setupMenu(rootView: SearchableView, toolbar: ToolbarView, contact: Contact) {
        val context = rootView.context

        val backAction =
            ToolbarAction(R.drawable.ic_arrow_back, context.getString(R.string.menu_back))
        backAction.clickAction = {
            rootView.back()
        }
        toolbar.addAction(backAction, ToolbarView.PLACEMENT_START)

        val favAction = FavoriteToolbarAction(context, contact)
        toolbar.addAction(favAction, ToolbarView.PLACEMENT_END)

        val hideAction = VisibilityToolbarAction(context, contact)
        toolbar.addAction(hideAction, ToolbarView.PLACEMENT_END)

        val openAction = ToolbarAction(
            R.drawable.ic_open_external,
            context.getString(R.string.contacts_menu_open_externally)
        )
        openAction.clickAction = {
            try {
                val uri =
                    ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id)
                val intent = Intent(Intent.ACTION_VIEW).setData(uri)
                ActivityStarter.start(context, rootView, intent = intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
            }
        }
        toolbar.addAction(openAction, ToolbarView.PLACEMENT_END)
    }

    private fun addShortcuts(rootView: SearchableView, contact: Contact) {

        val context = rootView.context
        val shortcutContainer = rootView.findViewById<LinearLayout>(R.id.contactShortcuts)


        if (contact.phones.isNotEmpty()) {
            val callView = (View.inflate(context, R.layout.view_list_item, null) as TextView).also {
                it.setStartCompoundDrawable(R.drawable.ic_call)
                if (contact.phones.size == 1) {
                    it.text = contact.phones.first()
                    it.setOnClickListener {
                        call(rootView, contact.phones.first())
                    }
                } else {
                    it.text =
                        context.getString(R.string.contact_multiple_numbers, contact.phones.size)
                    it.setOnClickListener {
                        val menu = PopupMenu(context, it, Gravity.START)
                        val phones = contact.phones.toList()
                        for ((i, phone) in phones.withIndex()) {
                            menu.menu.add(Menu.NONE, i, Menu.NONE, phone)
                        }
                        menu.setOnMenuItemClickListener {
                            call(rootView, phones[it.itemId])
                            true
                        }
                        menu.show()
                    }
                }
            }
            shortcutContainer.addView(callView)
        }

        if (contact.phones.isNotEmpty()) {
            val messageView =
                (View.inflate(context, R.layout.view_list_item, null) as TextView).also {
                    it.setStartCompoundDrawable(R.drawable.ic_message)
                    if (contact.phones.size == 1) {
                        it.text = contact.phones.first()
                        it.setOnClickListener {
                            message(rootView, contact.phones.first())
                        }
                    } else {
                        it.text = context.getString(
                            R.string.contact_multiple_numbers,
                            contact.phones.size
                        )
                        it.setOnClickListener {
                            val menu = PopupMenu(context, it, Gravity.START)
                            val phones = contact.phones.toList()
                            for ((i, phone) in phones.withIndex()) {
                                menu.menu.add(Menu.NONE, i, Menu.NONE, phone)
                            }
                            menu.setOnMenuItemClickListener {
                                message(rootView, phones[it.itemId])
                                true
                            }
                            menu.show()
                        }
                    }
                }
            shortcutContainer.addView(messageView)
        }

        if (contact.emails.isNotEmpty()) {
            val emailView =
                (View.inflate(context, R.layout.view_list_item, null) as TextView).also {
                    it.setStartCompoundDrawable(R.drawable.ic_mail)
                    if (contact.emails.size == 1) {
                        it.text = contact.emails.first()
                        it.setOnClickListener {
                            email(rootView, contact.emails.first())
                        }
                    } else {
                        it.text =
                            context.getString(R.string.contact_multiple_emails, contact.emails.size)
                        it.setOnClickListener {
                            val menu = PopupMenu(context, it, Gravity.START)
                            val emails = contact.emails.toList()
                            for ((i, email) in emails.withIndex()) {
                                menu.menu.add(Menu.NONE, i, Menu.NONE, email)
                            }
                            menu.setOnMenuItemClickListener {
                                email(rootView, emails[it.itemId])
                                true
                            }
                            menu.show()
                        }
                    }
                }
            shortcutContainer.addView(emailView)
        }

        if (contact.telegram.isNotEmpty()) {
            val telegramView =
                (View.inflate(context, R.layout.view_list_item, null) as TextView).also {
                    it.setStartCompoundDrawable(R.drawable.ic_telegram)
                    if (contact.telegram.size == 1) {
                        it.text = contact.telegram.first().substringAfter('$')
                        it.setOnClickListener {
                            telegram(rootView, contact.telegram.first().substringBefore('$'))
                        }
                    } else {
                        it.text = context.getString(
                            R.string.contact_multiple_numbers,
                            contact.telegram.size
                        )
                        it.setOnClickListener {
                            val menu = PopupMenu(context, it, Gravity.START)
                            val phones = contact.telegram.toList()
                            for ((i, phone) in phones.withIndex()) {
                                menu.menu.add(Menu.NONE, i, Menu.NONE, phone.substringAfter('$'))
                            }
                            menu.setOnMenuItemClickListener {
                                telegram(rootView, phones[it.itemId].substringBefore('$'))
                                true
                            }
                            menu.show()
                        }
                    }
                }
            shortcutContainer.addView(telegramView)
        }
        if (contact.whatsapp.isNotEmpty()) {
            val whatsappView =
                (View.inflate(context, R.layout.view_list_item, null) as TextView).also {
                    it.setStartCompoundDrawable(R.drawable.ic_whatsapp)
                    if (contact.whatsapp.size == 1) {
                        it.text = contact.whatsapp.first().substringAfter("$")
                        it.setOnClickListener {
                            whatsapp(rootView, contact.whatsapp.first().substringBefore("$"))
                        }
                    } else {
                        it.text = context.getString(
                            R.string.contact_multiple_numbers,
                            contact.whatsapp.size
                        )
                        it.setOnClickListener {
                            val menu = PopupMenu(context, it, Gravity.START)
                            val phones = contact.whatsapp.toList()
                            for ((i, phone) in phones.withIndex()) {
                                menu.menu.add(Menu.NONE, i, Menu.NONE, phone.substringAfter('$'))
                            }
                            menu.setOnMenuItemClickListener {
                                whatsapp(rootView, phones[it.itemId].substringBefore('$'))
                                true
                            }
                            menu.show()
                        }
                    }
                }
            shortcutContainer.addView(whatsappView)
        }
        if (contact.postals.isNotEmpty()) {
            val locationView =
                (View.inflate(context, R.layout.view_list_item, null) as TextView).also {
                    it.setStartCompoundDrawable(R.drawable.ic_location)
                    if (contact.postals.size == 1) {
                        it.text = contact.postals.first()
                        it.setOnClickListener {
                            navigate(rootView, contact.postals.first())
                        }
                    } else {
                        it.text = context.getString(
                            R.string.contact_multiple_postals,
                            contact.postals.size
                        )
                        it.setOnClickListener {
                            val menu = PopupMenu(context, it, Gravity.START)
                            val postals = contact.postals.toList()
                            for ((i, postal) in postals.withIndex()) {
                                menu.menu.add(Menu.NONE, i, Menu.NONE, postal)
                            }
                            menu.setOnMenuItemClickListener {
                                navigate(rootView, postals[it.itemId])
                                true
                            }
                            menu.show()
                        }
                    }
                }
            shortcutContainer.addView(locationView)
        }
    }

    private fun call(rootView: SearchableView, number: String) {
        val context = rootView.context
        try {
            val callIntent = Intent(Intent.ACTION_DIAL)
            callIntent.data = Uri.parse("tel:$number")
            ActivityStarter.start(context, rootView, intent = callIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun message(rootView: SearchableView, number: String) {
        val context = rootView.context
        try {
            val messageIntent = Intent(Intent.ACTION_VIEW)
            messageIntent.data = Uri.parse("sms:$number")
            ActivityStarter.start(context, rootView, intent = messageIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun email(rootView: SearchableView, address: String) {
        val context = rootView.context
        try {
            val mailIntent = Intent(Intent.ACTION_VIEW)
            mailIntent.data = Uri.parse("mailto:$address")
            ActivityStarter.start(context, rootView, intent = mailIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun whatsapp(rootView: SearchableView, number: String) {
        val context = rootView.context
        try {
            val whatsappIntent = Intent(Intent.ACTION_VIEW)
            whatsappIntent.data = Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, number)
            ActivityStarter.start(context, rootView, intent = whatsappIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun telegram(rootView: SearchableView, userId: String) {
        val context = rootView.context
        try {
            val telegramIntent = Intent(Intent.ACTION_VIEW)
            telegramIntent.data = Uri.parse("tg:openmessage?user_id=$userId")
            ActivityStarter.start(context, rootView, intent = telegramIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigate(rootView: SearchableView, location: String) {
        val context = rootView.context
        try {
            val mapsIntent = Intent(Intent.ACTION_VIEW)
            mapsIntent.data = Uri.parse("geo:0,0?q=${URLEncoder.encode(location, "utf8")}")
            ActivityStarter.start(context, rootView, intent = mapsIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        }
    }
}