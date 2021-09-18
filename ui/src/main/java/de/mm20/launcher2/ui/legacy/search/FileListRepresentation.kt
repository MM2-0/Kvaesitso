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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FileListRepresentation : Representation {
    override fun getScene(rootView: SearchableView, searchable: Searchable, previousRepresentation: Int?): Scene {
        val file = searchable as File
        val context = rootView.context as AppCompatActivity
        val scene = Scene.getSceneForLayout(rootView, R.layout.view_file_list, rootView.context)
        scene.setEnterAction {
            with(rootView) {
                findViewById<TextView>(R.id.fileLabel).text = file.label
                findViewById<TextView>(R.id.fileInfo).text = getFileType(context, file)
                findViewById<LauncherIconView>(R.id.icon).apply {
                    badge = BadgeProvider.getInstance(context).getLiveBadge(file.badgeKey)
                    shape = LauncherIconView.getDefaultShape(context)
                    icon = IconRepository.getInstance(context).getIconIfCached(file)
                    lifecycleScope.launch {
                        IconRepository.getInstance(context).getIcon(file, (84 * rootView.dp).toInt()).collect {
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

    fun getFileType(context: Context, file: File): String {
        if (file.isDirectory) return context.getString(R.string.file_type_directory)
        val mimeType = file.mimeType
        val resource = when (mimeType) {
            "application/zip", "application/x-gtar", "application/x-tar",
            "application/java-archive", "application/x-7z-compressed" -> R.string.file_type_archive
            "application/x-gzip", "application/x-bzip2" -> R.string.file_type_compressed
            "application/vnd.android.package-archive" -> R.string.file_type_android
            "text/x-asm", "text/x-c", "text/x-java-source", "text/x-script.phyton", "text/x-pascal",
            "text/x-script.perl", "text/javascript", "application/json" ->
                R.string.file_type_source_code
            "application/vnd.oasis.opendocument.text",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword", "application/vnd.google-apps.document" -> R.string.file_type_document
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel", "application/vnd.google-apps.spreadsheet" -> R.string.file_type_spreadsheet
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint", "application/vnd.google-apps.presentation" -> R.string.file_type_presentation
            "text/plain" -> R.string.file_type_text
            "application/vnd.google-apps.drawing" -> R.string.file_type_drawing
            "application/vnd.google-apps.form" -> R.string.file_type_form
            else -> when {
                mimeType.startsWith("image/") -> R.string.file_type_image
                mimeType.startsWith("video/") -> R.string.file_type_video
                mimeType.startsWith("audio/") -> R.string.file_type_music
                else -> R.string.file_type_none
            }
        }
        if (resource == R.string.file_type_none && file.label.matches(Regex(".+\\..+"))) {
            val extension = file.label.substringAfterLast(".").uppercase()
            return context.getString(R.string.file_type_generic, extension)
        }
        return context.getString(resource)
    }
}