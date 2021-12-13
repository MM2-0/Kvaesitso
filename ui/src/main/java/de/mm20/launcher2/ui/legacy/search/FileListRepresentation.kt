package de.mm20.launcher2.ui.legacy.search

import android.content.Context
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.transition.Scene
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.badges.BadgeProvider
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.search.data.File
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

class FileListRepresentation : Representation, KoinComponent {

    val iconRepository: IconRepository by inject()
    val badgeProvider: BadgeProvider by inject()

    override fun getScene(rootView: SearchableView, searchable: Searchable, previousRepresentation: Int?): Scene {
        val file = searchable as File
        val context = rootView.context as AppCompatActivity
        val scene = Scene.getSceneForLayout(rootView, R.layout.view_file_list, rootView.context)
        scene.setEnterAction {
            with(rootView) {
                findViewById<TextView>(R.id.fileLabel).text = file.label
                findViewById<TextView>(R.id.fileInfo).text = file.getFileType(context)
                findViewById<LauncherIconView>(R.id.icon).apply {
                    badge = badgeProvider.getLiveBadge(file.badgeKey)
                    shape = LauncherIconView.getDefaultShape(context)
                    icon = iconRepository.getIconIfCached(file)
                    lifecycleScope.launch {
                        iconRepository.getIcon(file, (84 * rootView.dp).toInt()).collectLatest {
                            icon = it
                        }
                    }
                }
                findViewById<SwipeCardView>(R.id.fileCard).apply {
                    setOnClickListener {
                        ActivityStarter.start(context, rootView, item = file)
                    }
                    setOnLongClickListener {
                        rootView.representation = SearchableView.REPRESENTATION_FULL
                        true
                    }
                    leftAction = FavoriteSwipeAction(context, file)
                    rightAction = HideSwipeAction(context, file)
                }
            }
        }
        return scene
    }
}