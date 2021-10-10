package de.mm20.launcher2.ui.legacy.search

import android.os.Build
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.transition.Scene
import de.mm20.launcher2.badges.BadgeProvider
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import de.mm20.launcher2.ui.legacy.view.FavoriteToolbarAction
import de.mm20.launcher2.ui.legacy.view.LauncherIconView
import de.mm20.launcher2.ui.legacy.view.ToolbarAction
import de.mm20.launcher2.ui.legacy.view.ToolbarView
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@RequiresApi(Build.VERSION_CODES.N_MR1)
class AppShortcutDetailRepresentation: Representation, KoinComponent {

    val iconRepository: IconRepository by inject()
    val badgeProvider: BadgeProvider by inject()

    override fun getScene(rootView: SearchableView, searchable: Searchable, previousRepresentation: Int?): Scene {
        val appShortcut = searchable as AppShortcut
        val context = rootView.context as AppCompatActivity
        val scene = Scene.getSceneForLayout(rootView, R.layout.view_application_detail, context)
        scene.setEnterAction {
            with(rootView) {
                setOnClickListener(null)
                setOnLongClickListener(null)
                findViewById<TextView>(R.id.appName).text = appShortcut.label
                findViewById<LauncherIconView>(R.id.icon).apply {
                    badge = badgeProvider.getLiveBadge(appShortcut.badgeKey)
                    shape = LauncherIconView.getDefaultShape(context)
                    icon = iconRepository.getIconIfCached(appShortcut)
                    lifecycleScope.launch {
                        iconRepository.getIcon(appShortcut, (84 * rootView.dp).toInt()).collect {
                            icon = it
                        }
                    }
                }
                val appName = appShortcut.appName
                findViewById<TextView>(R.id.appInfo).text = context.getString(R.string.shortcut_summary, appName)

                val toolbar = findViewById<ToolbarView>(R.id.appToolbar)
                setupToolbar(this, toolbar, appShortcut)

            }
        }
        return scene
    }

    private fun setupToolbar(searchableView: SearchableView, toolbar: ToolbarView, shortcut: AppShortcut) {
        val context = searchableView.context
        val favAction = FavoriteToolbarAction(context, shortcut)
        toolbar.addAction(favAction, ToolbarView.PLACEMENT_END)

        val backAction = ToolbarAction(R.drawable.ic_arrow_back, context.getString(R.string.menu_back))
        backAction.clickAction = {
            searchableView.back()
        }
        toolbar.addAction(backAction, ToolbarView.PLACEMENT_START)

    }
}