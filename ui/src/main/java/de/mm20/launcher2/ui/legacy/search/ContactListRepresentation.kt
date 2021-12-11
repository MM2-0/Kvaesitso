package de.mm20.launcher2.ui.legacy.search

import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.transition.Scene
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.badges.BadgeProvider
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.search.data.Contact
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import de.mm20.launcher2.ui.legacy.view.FavoriteSwipeAction
import de.mm20.launcher2.ui.legacy.view.HideSwipeAction
import de.mm20.launcher2.ui.legacy.view.LauncherIconView
import de.mm20.launcher2.ui.legacy.view.SwipeCardView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ContactListRepresentation : Representation, KoinComponent {

    val iconRepository: IconRepository by inject()
    val badgeProvider: BadgeProvider by inject()

    override fun getScene(rootView: SearchableView, searchable: Searchable, previousRepresentation: Int?): Scene {
        val contact = searchable as Contact
        val context = rootView.context as AppCompatActivity
        val scene = Scene.getSceneForLayout(rootView, R.layout.view_contact_list, rootView.context)
        scene.setEnterAction {
            with(rootView) {
                findViewById<LauncherIconView>(R.id.icon).apply {
                    badge = badgeProvider.getLiveBadge(contact.badgeKey)
                    shape = LauncherIconView.getDefaultShape(context)
                    icon = iconRepository.getIconIfCached(contact)
                    lifecycleScope.launch {
                        iconRepository.getIcon(contact, (84 * rootView.dp).toInt()).collectLatest {
                            icon = it
                        }
                    }
                }
                findViewById<TextView>(R.id.contactName).text = contact.displayName
                val contactSummary = findViewById<TextView>(R.id.contactSummary)
                contactSummary.text = contact.summary
                findViewById<SwipeCardView>(R.id.contactCard).also {
                    it.leftAction = FavoriteSwipeAction(context, contact)
                    it.rightAction = HideSwipeAction(context, contact)
                    it.setOnClickListener {
                        rootView.representation = SearchableView.REPRESENTATION_FULL
                    }
                }
                contactSummary.alpha = 0f
                contactSummary.animate()
                        .setStartDelay(100)
                        .setDuration(200)
                        .alpha(1f)
                        .start()

            }
        }
        return scene
    }


}