package de.mm20.launcher2.ui.legacy.search

import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.transition.Scene
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.badges.BadgeProvider
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import de.mm20.launcher2.ui.legacy.view.LauncherIconView
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class BasicGridRepresentation : Representation {
    override fun getScene(rootView: SearchableView, searchable: Searchable, previousRepresentation: Int?): Scene {
        val context = rootView.context as AppCompatActivity
        val scene = Scene.getSceneForLayout(rootView, R.layout.view_basic_grid, rootView.context)
        scene.setEnterAction {
            with(rootView) {
                val text = findViewById<TextView>(R.id.label)
                text.text = searchable.label
                /*text.alpha = 0f
                text.animate()
                        .setStartDelay(300)
                        .setDuration(200)
                        .alpha(1f)
                        .start()*/
                findViewById<LauncherIconView>(R.id.icon).apply {
                    badge = BadgeProvider.getInstance(context).getLiveBadge(searchable.badgeKey)
                    shape = LauncherIconView.getDefaultShape(context)
                    setOnClickListener {
                        if (!ActivityStarter.start(context, rootView.findViewById(R.id.card), item = searchable)) {
                            rootView.representation = SearchableView.REPRESENTATION_FULL
                        }
                    }
                    icon = IconRepository.getInstance(context).getIconIfCached(searchable)
                    lifecycleScope.launch {
                        IconRepository.getInstance(context).getIcon(searchable, (84 * rootView.dp).toInt()).collect {
                            icon = it
                        }
                    }
                    setOnLongClickListener {
                        rootView.representation = SearchableView.REPRESENTATION_FULL
                        true
                    }
                }
            }
        }

        return scene

    }

}
