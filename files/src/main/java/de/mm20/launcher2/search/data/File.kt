package de.mm20.launcher2.search.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQueryBuilder
import android.graphics.BitmapFactory
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateUtils
import android.util.Size
import androidx.core.content.ContentResolverCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.database.getStringOrNull
import androidx.exifinterface.media.ExifInterface
import de.mm20.launcher2.files.R
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.ktx.formatToString
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.media.ThumbnailUtilsCompat
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherPreferences
import org.json.JSONObject
import java.io.IOException
import java.util.*
import java.io.File as JavaIOFile

open class File(
        val id: Long,
        val path: String,
        val mimeType: String,
        val size: Long,
        val isDirectory: Boolean,
        val metaData: List<Pair<Int, String>>
) : Searchable() {

    override val label = path.substringAfterLast('/')

    override val key = "file://$path"

    open val isStoredInCloud = false

    override suspend fun loadIcon(context: Context, size: Int): LauncherIcon? {
        if (!JavaIOFile(path).exists()) return null
        when {
            mimeType.startsWith("image/") -> {
                val thumbnail = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(path),
                        size, size) ?: return null
                return LauncherIcon(
                        foreground = BitmapDrawable(context.resources, thumbnail),
                        autoGenerateBackgroundMode = LauncherIcon.BACKGROUND_COLOR
                )
            }
            mimeType.startsWith("video/") -> {
                val thumbnail = ThumbnailUtilsCompat.createVideoThumbnail(JavaIOFile(path),
                        Size(size, size)) ?: return null
                return LauncherIcon(
                        foreground = BitmapDrawable(context.resources, thumbnail),
                        autoGenerateBackgroundMode = LauncherIcon.BACKGROUND_COLOR
                )
            }
            mimeType.startsWith("audio/") -> {
                val mediaMetadataRetriever = MediaMetadataRetriever()
                try {
                    mediaMetadataRetriever.setDataSource(path)
                    val thumbData = mediaMetadataRetriever.embeddedPicture
                    if (thumbData != null) {
                        val thumbnail = ThumbnailUtils.extractThumbnail(
                                BitmapFactory.decodeByteArray(thumbData, 0, thumbData.size), size, size)
                        mediaMetadataRetriever.release()
                        thumbnail ?: return null
                        return LauncherIcon(
                                foreground = BitmapDrawable(context.resources, thumbnail),
                                autoGenerateBackgroundMode = LauncherIcon.BACKGROUND_COLOR
                        )
                    }
                } catch (e: RuntimeException) {
                    mediaMetadataRetriever.release()
                    return null
                }
            }
            mimeType == "application/vnd.android.package-archive" -> {
                val pkgInfo = context.packageManager.getPackageArchiveInfo(path, 0)
                val icon = pkgInfo?.applicationInfo?.loadIcon(context.packageManager) ?: return null
                when {
                    Build.VERSION.SDK_INT > Build.VERSION_CODES.O && icon is AdaptiveIconDrawable -> {
                        return LauncherIcon(
                                foreground = icon.foreground,
                                background = icon.background,
                                foregroundScale = 1.5f,
                                backgroundScale = 1.5f
                        )
                    }
                    else -> {
                        return LauncherIcon(
                                foreground = icon,
                                foregroundScale = 0.7f,
                                autoGenerateBackgroundMode = LauncherIcon.BACKGROUND_COLOR
                        )
                    }
                }
            }
        }
        return null
    }

    override fun getPlaceholderIcon(context: Context): LauncherIcon {
        val (resId, bgColor) = when {
            isDirectory -> R.drawable.ic_file_folder to R.color.lightblue
            mimeType.startsWith("image/") -> R.drawable.ic_file_picture to R.color.teal
            mimeType.startsWith("audio/") -> R.drawable.ic_file_music to R.color.orange
            mimeType.startsWith("video/") -> R.drawable.ic_file_video to R.color.purple
            else -> when (mimeType) {
                "application/zip", "application/x-gtar", "application/x-tar",
                "application/java-archive", "application/x-7z-compressed",
                "application/x-compressed-tar", "application/x-gzip", "application/x-bzip2" -> R.drawable.ic_file_archive to R.color.brown
                "application/pdf" -> R.drawable.ic_file_pdf to R.color.red
                "application/vnd.oasis.opendocument.text",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword", "text/plain", "application/vnd.google-apps.document" -> R.drawable.ic_file_document to R.color.blue
                "application/vnd.oasis.opendocument.spreadsheet",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel", "application/vnd.google-apps.spreadsheet" -> R.drawable.ic_file_spreadsheet to R.color.green
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint", "application/vnd.google-apps.presentation" -> R.drawable.ic_file_presentation to R.color.amber
                "text/x-asm", "text/x-c", "text/x-java-source", "text/x-script.phyton", "text/x-pascal",
                "text/x-script.perl", "text/javascript", "application/json" -> R.drawable.ic_file_code to R.color.pink
                "text/xml", "text/html" -> R.drawable.ic_file_markup to R.color.deeporange
                "application/vnd.android.package-archive" -> R.drawable.ic_file_android to R.color.lightgreen
                "application/vnd.google-apps.form" -> R.drawable.ic_file_form to R.color.deeppurple
                "application/vnd.google-apps.drawing" -> R.drawable.ic_file_picture to R.color.teal
                else -> R.drawable.ic_file_generic to R.color.bluegrey
            }
        }
        return LauncherIcon(
                foreground = context.getDrawable(resId)!!,
                background = ColorDrawable(ContextCompat.getColor(context, bgColor)),
                foregroundScale = 0.5f
        )
    }

    override fun getLaunchIntent(context: Context): Intent? {
        val uri = FileProvider.getUriForFile(context,
                context.applicationContext.packageName + ".fileprovider", JavaIOFile(path))
        return Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun getFileType(context: Context): String {
        if (isDirectory) return context.getString(R.string.file_type_directory)
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
            val extension = label.substringAfterLast(".").toUpperCase(Locale.getDefault())
            return context.getString(R.string.file_type_generic, extension)
        }
        return context.getString(resource)
    }

    companion object {
        fun search(context: Context, query: String): List<File> {
            val results = mutableListOf<File>()
            if (!LauncherPreferences.instance.searchFiles) return results
            if (query.isBlank()) return results
            if (!PermissionsManager.checkPermission(context, PermissionsManager.EXTERNAL_STORAGE)) return results
            val uri = MediaStore.Files.getContentUri("external").buildUpon().appendQueryParameter("limit", "10").build()
            val projection = arrayOf(
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Files.FileColumns.MIME_TYPE)
            val selection = if (query.length > 3) "${MediaStore.Files.FileColumns.TITLE} LIKE ?" else "${MediaStore.Files.FileColumns.TITLE} = ?"
            val selArgs = if (query.length > 3) arrayOf("%$query%") else arrayOf(query)
            val sort = "${MediaStore.Files.FileColumns.DISPLAY_NAME} COLLATE NOCASE ASC"


            val cursor = context.contentResolver.query(uri, projection, selection, selArgs, sort)
                    ?: return results
            while (cursor.moveToNext()) {
                if (results.size >= 10) {
                    break
                }
                val path = cursor.getString(3)
                if (!JavaIOFile(path).exists()) continue
                val directory = JavaIOFile(path).isDirectory
                val mimeType = (cursor.getStringOrNull(4)
                        ?: if (directory) "inode/directory" else getMimetypeByFileExtension(path.substringAfterLast('.')))
                val file = File(
                        path = path,
                        mimeType = mimeType,
                        size = cursor.getLong(2),
                        isDirectory = directory,
                        id = cursor.getLong(1),
                        metaData = getMetaData(context, mimeType, path))
                results.add(file)
            }
            cursor.close()
            return results.sortedBy { it }
        }

        internal fun getMimetypeByFileExtension(extension: String): String {
            return when (extension) {
                "apk" -> "application/vnd.android.package-archive"
                "zip" -> "application/zip"
                "jar" -> "application/java-archive"
                "txt" -> "text/plain"
                "js" -> "text/javascript"
                "html", "htm" -> "text/html"
                "css" -> "text/css"
                "gif" -> "image/gif"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "bmp" -> "image/bmp"
                "webp" -> "image/webp"
                "ico" -> "image/x-icon"
                "midi" -> "audio/midi"
                "mp3" -> "audio/mpeg3"
                "webm" -> "audio/webm"
                "ogg" -> "audio/ogg"
                "wav" -> "audio/wav"
                "mp4" -> "video/mp4"
                else -> "application/octet-stream"
            }
        }


        internal fun getMetaData(context: Context, mimeType: String, path: String): List<Pair<Int, String>> {
            val metaData = mutableListOf<Pair<Int, String>>()
            when {
                mimeType.startsWith("audio/") -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(path)
                        arrayOf(
                                R.string.file_meta_title to MediaMetadataRetriever.METADATA_KEY_TITLE,
                                R.string.file_meta_artist to MediaMetadataRetriever.METADATA_KEY_ARTIST,
                                R.string.file_meta_album to MediaMetadataRetriever.METADATA_KEY_ALBUM,
                                R.string.file_meta_year to MediaMetadataRetriever.METADATA_KEY_YEAR
                        ).forEach {
                            retriever.extractMetadata(it.second)?.let { m -> metaData.add(it.first to m) }
                        }
                        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
                        val d = DateUtils.formatElapsedTime((duration) / 1000)
                        metaData.add(3, R.string.file_meta_duration to d)
                        retriever.release()
                    } catch (e: RuntimeException) {
                        retriever.release()
                    }
                }
                mimeType.startsWith("video/") -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(path)
                        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toLong() ?: 0
                        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toLong() ?: 0
                        metaData.add(R.string.file_meta_dimensions to "${width}x$height")
                        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
                        val d = DateUtils.formatElapsedTime(duration / 1000)
                        metaData.add(R.string.file_meta_duration to d)
                        val loc = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                        if (Geocoder.isPresent() && loc != null) {
                            val lon = loc.substring(0, loc.lastIndexOfAny(charArrayOf('+', '-'))).toDouble()
                            val lat = loc.substring(loc.lastIndexOfAny(charArrayOf('+', '-')), loc.indexOf('/')).toDouble()
                            val list = Geocoder(context).getFromLocation(lon, lat, 1)
                            if (list.size > 0) {
                                metaData.add(R.string.file_meta_location to list[0].formatToString())
                            }
                        }
                        retriever.release()
                    } catch (e: RuntimeException) {
                        retriever.release()
                    }
                }
                mimeType.startsWith("image/") -> {
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(path, options)
                    val width = options.outWidth
                    val height = options.outHeight
                    metaData.add(R.string.file_meta_dimensions to "${width}x$height")
                    try {
                        val exif = ExifInterface(path)
                        val loc = exif.latLong
                        if (loc != null && Geocoder.isPresent()) {
                            val list = Geocoder(context).getFromLocation(loc[0], loc[1], 1)
                            if (list.size > 0) {
                                metaData.add(R.string.file_meta_location to list[0].formatToString())
                            }
                        }
                    } catch (_: IOException) {

                    }
                }
                mimeType == "application/vnd.android.package-archive" -> {
                    val pkgInfo = context.packageManager.getPackageArchiveInfo(path, 0)
                            ?: return metaData
                    metaData.add(R.string.file_meta_app_name to pkgInfo.applicationInfo.loadLabel(context.packageManager).toString())
                    metaData.add(R.string.file_meta_app_pkgname to pkgInfo.packageName)
                    metaData.add(R.string.file_meta_app_version to pkgInfo.versionName)
                    metaData.add(R.string.file_meta_app_min_sdk to pkgInfo.applicationInfo.minSdkVersion.toString())
                }
            }
            return metaData
        }
    }
}