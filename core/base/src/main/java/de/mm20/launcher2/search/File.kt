package de.mm20.launcher2.search

import android.content.Context
import androidx.core.content.ContextCompat
import de.mm20.launcher2.base.R
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

interface File : SavableSearchable {
    val path: String?
    val mimeType: String
    val size: Long
    val isDirectory: Boolean
    val metaData: ImmutableMap<FileMetaType, String>

    override val preferDetailsOverLaunch: Boolean
        get() = false

    val providerIconRes: Int?
        get() = null

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        val (resId, bgColor) = when {
            isDirectory -> R.drawable.folder_24px to R.color.lightblue
            mimeType.startsWith("image/") -> R.drawable.photo_24px to R.color.teal
            mimeType.startsWith("audio/") -> R.drawable.music_note_24px to R.color.orange
            mimeType.startsWith("video/") -> R.drawable.movie_24px to R.color.purple
            else -> when (mimeType) {
                "application/zip", "application/x-gtar", "application/x-tar",
                "application/java-archive", "application/x-7z-compressed",
                "application/x-compressed-tar", "application/x-gzip", "application/x-bzip2" -> R.drawable.folder_zip_24px to R.color.brown
                "application/pdf" -> R.drawable.docs_24px to R.color.red
                "application/vnd.oasis.opendocument.text",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword", "text/plain", "application/vnd.google-apps.document" -> R.drawable.article_24px to R.color.blue
                "application/vnd.oasis.opendocument.spreadsheet",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel", "application/vnd.google-apps.spreadsheet" -> R.drawable.table_24px to R.color.green
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint", "application/vnd.google-apps.presentation" -> R.drawable.slideshow_24px to R.color.amber
                "text/x-asm", "text/x-c", "text/x-java-source", "text/x-script.phyton", "text/x-pascal",
                "text/x-script.perl", "text/javascript", "application/json" -> R.drawable.data_object_24px to R.color.pink
                "text/xml", "text/html" -> R.drawable.code_24px to R.color.deeporange
                "application/vnd.android.package-archive" -> R.drawable.android_24px to R.color.lightgreen
                "application/vnd.google-apps.form" -> R.drawable.ballot_24px to R.color.deeppurple
                "application/vnd.google-apps.drawing" -> R.drawable.shape_line_24px to R.color.teal
                "application/vnd.de.mm20.launcher2.backup" -> R.drawable.settings_24px to R.color.brown
                "application/vnd.de.mm20.launcher2.theme" -> R.drawable.palette_24px to R.color.amber
                else -> R.drawable.draft_24px to R.color.bluegrey
            }
        }
        return StaticLauncherIcon(
            foregroundLayer = TintedIconLayer(
                icon = ContextCompat.getDrawable(context, resId)!!,
                scale = 0.5f,
                color = ContextCompat.getColor(context, bgColor)
            ),
            backgroundLayer = ColorLayer(ContextCompat.getColor(context, bgColor))
        )
    }

    fun getFileType(context: Context): String {
        if (isDirectory) return context.getString(R.string.file_type_directory)
        if (mimeType == "application/vnd.de.mm20.launcher2.backup") {
            return context.getString(
                R.string.file_type_launcherbackup,
                context.getString(R.string.app_name)
            )
        }
        if (mimeType == "application/vnd.de.mm20.launcher2.theme") {
            return context.getString(
                R.string.file_type_launchertheme,
                context.getString(R.string.app_name)
            )
        }
        val resource = when (mimeType) {
            "application/zip",
            "application/x-zip-compressed",
            "application/x-gtar",
            "application/x-tar",
            "application/java-archive",
            "application/x-7z-compressed" -> R.string.file_type_archive
            "application/x-gzip",
            "application/x-bzip2" -> R.string.file_type_compressed
            "application/vnd.android.package-archive" -> R.string.file_type_android
            "text/x-asm",
            "text/x-c",
            "text/x-java-source",
            "text/x-script.phyton",
            "text/x-pascal",
            "text/x-script.perl",
            "text/javascript",
            "application/json" -> R.string.file_type_source_code
            "application/vnd.oasis.opendocument.text",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/x-iwork-pages-sffpages",
            "application/vnd.apple.pages",
            "application/vnd.google-apps.document" -> R.string.file_type_document
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/x-iwork-numbers-sffnumbers",
            "application/vnd.apple.numbers",
            "application/vnd.google-apps.spreadsheet" -> R.string.file_type_spreadsheet
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "application/x-iwork-keynote-sffkey",
            "application/vnd.apple.keynote",
            "application/vnd.google-apps.presentation" -> R.string.file_type_presentation
            "text/plain" -> R.string.file_type_text
            "application/vnd.google-apps.drawing" -> R.string.file_type_drawing
            "application/vnd.google-apps.form" -> R.string.file_type_form
            "application/epub+zip" -> R.string.file_type_ebook
            else -> when {
                mimeType.startsWith("image/") -> R.string.file_type_image
                mimeType.startsWith("video/") -> R.string.file_type_video
                mimeType.startsWith("audio/") -> R.string.file_type_music
                else -> R.string.file_type_none
            }
        }
        if (resource == R.string.file_type_none && label.matches(Regex(".+\\..+"))) {
            val extension = label.substringAfterLast(".").uppercase(Locale.getDefault())
            return context.getString(R.string.file_type_generic, extension)
        }
        return context.getString(resource)
    }

    val isDeletable: Boolean
        get() = false

    val canShare: Boolean
        get() = false

    fun share(context: Context) {}
    suspend fun delete(context: Context) {}

}

@Serializable
enum class FileMetaType {
    @SerialName("title") Title,
    @SerialName("artist") Artist,
    @SerialName("album") Album,
    @SerialName("duration") Duration,
    @SerialName("year") Year,
    @SerialName("dimensions") Dimensions,
    @SerialName("location") Location,
    @SerialName("app_name") AppName,
    @SerialName("app_version") AppVersion,
    @SerialName("app_minsdk") AppMinSdk,
    @SerialName("app_packagename") AppPackageName,
    @SerialName("owner") Owner,
}