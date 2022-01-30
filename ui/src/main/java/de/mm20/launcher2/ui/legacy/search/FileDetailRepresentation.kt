package de.mm20.launcher2.ui.legacy.search

import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.Scene
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.mm20.launcher2.badges.BadgeRepository
import de.mm20.launcher2.files.FileRepository
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.lifecycleOwner
import de.mm20.launcher2.search.data.File
import de.mm20.launcher2.search.data.GDriveFile
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import de.mm20.launcher2.ui.legacy.view.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.text.DecimalFormat

class FileDetailRepresentation : Representation, KoinComponent {

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
        val scene = Scene.getSceneForLayout(rootView, R.layout.view_file_detail, rootView.context)
        scene.setEnterAction {
            with(rootView) {
                findViewById<TextView>(R.id.fileLabel).text = file.label
                findViewById<TextView>(R.id.fileInfo).text = getInfo(context, file)
                findViewById<LauncherIconView>(R.id.icon).apply {
                    icon = iconRepository.getIconIfCached(file)
                    shape = LauncherIconView.currentShape
                    job = rootView.scope.launch {
                        rootView.lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
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
                findViewById<SwipeCardView>(R.id.fileCard).also {
                    it.leftAction = FavoriteSwipeAction(context, file)
                    it.rightAction = HideSwipeAction(context, file)
                }
                setupMenu(rootView, findViewById(R.id.fileToolbar), file)
            }
        }
        scene.setExitAction {
            job?.cancel()
        }
        return scene
    }

    private fun setupMenu(rootView: SearchableView, toolbar: ToolbarView, file: File) {
        val context = toolbar.context
        toolbar.clear()

        val backAction =
            ToolbarAction(R.drawable.ic_arrow_back, context.getString(R.string.menu_back))
        backAction.clickAction = {
            rootView.back()
        }
        toolbar.addAction(backAction, ToolbarView.PLACEMENT_START)

        val favAction = FavoriteToolbarAction(context, file)
        toolbar.addAction(favAction, ToolbarView.PLACEMENT_END)

        if (file.isDeletable) {
            val deleteAction =
                ToolbarAction(R.drawable.ic_delete, context.getString(R.string.menu_delete))
            deleteAction.clickAction = {
                delete(context, file)
            }
            toolbar.addAction(deleteAction, ToolbarView.PLACEMENT_END)
        }

        val hideAction = VisibilityToolbarAction(context, file)
        toolbar.addAction(hideAction, ToolbarView.PLACEMENT_END)

        if (file !is GDriveFile) {
            val shareAction =
                ToolbarAction(R.drawable.ic_share, context.getString(R.string.menu_share))
            shareAction.clickAction = {
                share(context, file)
            }
            toolbar.addAction(shareAction, ToolbarView.PLACEMENT_END)
        }
    }

    private fun delete(context: Context, file: File) {
        MaterialAlertDialogBuilder(context)
            .setMessage(
                context.getString(
                    if (file.isDirectory) R.string.alert_delete_directory
                    else R.string.alert_delete_file,
                    file.path
                )
            )
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                deleteFile(file)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteFile(file: File) {
        val fileRepository: FileRepository = get()
        fileRepository.deleteFile(file)
    }

    private fun share(context: Context, fileDetail: File) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uri = FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".fileprovider",
            java.io.File(fileDetail.path)
        )
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.type = fileDetail.mimeType
        context.startActivity(Intent.createChooser(shareIntent, null))
    }

    private fun getInfo(context: Context, file: File): String {
        val sb = StringBuilder()

        sb.append(
            context.getString(
                R.string.file_meta_data_entry,
                context.getString(R.string.file_meta_type),
                file.mimeType
            )
        )

        for ((k, v) in file.metaData) {
            sb.append("\n")
                .append(context.getString(R.string.file_meta_data_entry, context.getString(k), v))
        }
        if (!file.isDirectory) {
            sb.append("\n").append(
                context.getString(
                    R.string.file_meta_data_entry,
                    context.getString(R.string.file_meta_size),
                    formatFileSize(file.size)
                )
            )
        }
        if (file.path.isNotEmpty()) {
            sb.append("\n").append(
                context.getString(
                    R.string.file_meta_data_entry,
                    context.getString(R.string.file_meta_path),
                    file.path
                )
            )
        }
        return sb.toString()
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1000L -> "$size Bytes"
            size < 1000000L -> "${DecimalFormat("#,##0.#").format(size / 1000.0)} kB"
            size < 1000000000L -> "${DecimalFormat("#,##0.#").format(size / 1000000.0)} MB"
            size < 1000000000000L -> "${DecimalFormat("#,##0.#").format(size / 1000000000.0)} GB"
            else -> "${DecimalFormat("#,##0.#").format(size / 1000000000000.0)} TB"
        }
    }
}