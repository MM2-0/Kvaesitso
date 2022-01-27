package de.mm20.launcher2.ui.legacy.search

import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.Scene
import de.mm20.launcher2.badges.BadgeRepository
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.lifecycleOwner
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.search.data.File
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import de.mm20.launcher2.ui.legacy.view.FavoriteSwipeAction
import de.mm20.launcher2.ui.legacy.view.HideSwipeAction
import de.mm20.launcher2.ui.legacy.view.LauncherIconView
import de.mm20.launcher2.ui.legacy.view.SwipeCardView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FileListRepresentation : Representation, KoinComponent {

    private val iconRepository: IconRepository by inject()
    private val badgeRepository: BadgeRepository by inject()

    private var job: Job? = null

    override fun getScene(
        rootView: SearchableView,
        searchable: Searchable,
        previousRepresentation: Int?
    ): Scene {
        val file = searchable as File
        val context = rootView.context as AppCompatActivity
        val scene = Scene.getSceneForLayout(rootView, R.layout.view_file_list, rootView.context)
        scene.setEnterAction {
            with(rootView) {
                findViewById<TextView>(R.id.fileLabel).text = file.label
                findViewById<TextView>(R.id.fileInfo).text = file.getFileType(context)
                findViewById<LauncherIconView>(R.id.icon).apply {
                    icon = iconRepository.getIconIfCached(file)
                    shape = LauncherIconView.currentShape
                    job = rootView.scope.launch {
                        rootView.lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            launch {
                                iconRepository.getIcon(searchable, (84 * rootView.dp).toInt())
                                    .collectLatest {
                                        icon = it
                                    }
                            }
                            launch {
                                badgeRepository.getBadge(searchable.badgeKey).collectLatest {
                                    badge = it
                                }
                            }
                            launch {
                                LauncherIconView.getDefaultShape().collectLatest {
                                    shape = it
                                }
                            }
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
        scene.setExitAction {
            job?.cancel()
        }
        return scene
    }
}