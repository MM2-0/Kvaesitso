package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.text.format.DateUtils
import android.util.Size
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import de.mm20.launcher2.files.R
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.formatToString
import de.mm20.launcher2.media.ThumbnailUtilsCompat
import de.mm20.launcher2.preferences.Settings.IconSettings.LegacyIconBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import java.io.IOException
import java.io.File as JavaIOFile

open class LocalFile(
    id: Long,
    path: String,
    mimeType: String,
    size: Long,
    isDirectory: Boolean,
    metaData: List<Pair<Int, String>>
) : File(id, path, mimeType, size, isDirectory, metaData) {

    override val label = path.substringAfterLast('/')

    override val key = "file://$path"

    override val isStoredInCloud = false

    override suspend fun loadIcon(
        context: Context,
        size: Int,
        legacyIconBackground: LegacyIconBackground
    ): LauncherIcon? {
        if (!JavaIOFile(path).exists()) return null
        when {
            mimeType.startsWith("image/") -> {
                val thumbnail = withContext(Dispatchers.IO) {
                    ThumbnailUtils.extractThumbnail(
                        BitmapFactory.decodeFile(path),
                        size, size
                    )
                } ?: return null

                return LauncherIcon(
                    foreground = BitmapDrawable(context.resources, thumbnail),
                    autoGenerateBackgroundMode = legacyIconBackground.number
                )
            }
            mimeType.startsWith("video/") -> {
                val thumbnail = withContext(Dispatchers.IO) {
                    ThumbnailUtilsCompat.createVideoThumbnail(
                        JavaIOFile(path),
                        Size(size, size)
                    )
                } ?: return null
                return LauncherIcon(
                    foreground = BitmapDrawable(context.resources, thumbnail),
                    autoGenerateBackgroundMode = legacyIconBackground.number
                )
            }
            mimeType.startsWith("audio/") -> {
                val thumbnail = withContext(Dispatchers.IO) {
                    val mediaMetadataRetriever = MediaMetadataRetriever()
                    try {
                        mediaMetadataRetriever.setDataSource(path)
                        val thumbData = mediaMetadataRetriever.embeddedPicture
                        if (thumbData != null) {
                            val thumbnail = ThumbnailUtils.extractThumbnail(
                                BitmapFactory.decodeByteArray(thumbData, 0, thumbData.size),
                                size,
                                size
                            )
                            mediaMetadataRetriever.release()
                            return@withContext thumbnail
                        }
                    } catch (e: RuntimeException) {
                    }
                    mediaMetadataRetriever.release()
                    return@withContext null

                }
                thumbnail ?: return null
                return LauncherIcon(
                    foreground = BitmapDrawable(context.resources, thumbnail),
                    autoGenerateBackgroundMode = legacyIconBackground.number
                )

            }
            mimeType == "application/vnd.android.package-archive" -> {
                val pkgInfo = context.packageManager.getPackageArchiveInfo(path, 0)
                val icon = withContext(Dispatchers.IO) {
                    pkgInfo?.applicationInfo?.loadIcon(context.packageManager)
                } ?: return null
                when {
                    icon is AdaptiveIconDrawable -> {
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
                            autoGenerateBackgroundMode = legacyIconBackground.number
                        )
                    }
                }
            }
        }
        return null
    }


    override fun getLaunchIntent(context: Context): Intent? {
        val uri = if (isDirectory) {
            Uri.parse(path)
        } else {
            FileProvider.getUriForFile(
                context,
                context.applicationContext.packageName + ".fileprovider", JavaIOFile(path)
            )
        }
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override val isDeletable: Boolean
        get() {
            val file = java.io.File(path)
            return file.canWrite() && file.parentFile?.canWrite() == true
        }

    override suspend fun delete(context: Context) {
        super.delete(context)

        val file = java.io.File(path)

        withContext(Dispatchers.IO) {
            file.deleteRecursively()

            context.contentResolver.delete(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.Files.FileColumns._ID} = ?",
                arrayOf(id.toString())
            )
        }
    }


    companion object : KoinComponent {
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


        internal fun getMetaData(
            context: Context,
            mimeType: String,
            path: String
        ): List<Pair<Int, String>> {
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
                            retriever.extractMetadata(it.second)
                                ?.let { m -> metaData.add(it.first to m) }
                        }
                        val duration =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                ?.toLong() ?: 0
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
                        val width =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                                ?.toLong() ?: 0
                        val height =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                                ?.toLong() ?: 0
                        metaData.add(R.string.file_meta_dimensions to "${width}x$height")
                        val duration =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                ?.toLong() ?: 0
                        val d = DateUtils.formatElapsedTime(duration / 1000)
                        metaData.add(R.string.file_meta_duration to d)
                        val loc =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                        if (Geocoder.isPresent() && loc != null) {
                            val lon =
                                loc.substring(0, loc.lastIndexOfAny(charArrayOf('+', '-')))
                                    .toDouble()
                            val lat = loc.substring(
                                loc.lastIndexOfAny(charArrayOf('+', '-')),
                                loc.indexOf('/')
                            ).toDouble()
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
                    metaData.add(
                        R.string.file_meta_app_name to pkgInfo.applicationInfo.loadLabel(
                            context.packageManager
                        ).toString()
                    )
                    metaData.add(R.string.file_meta_app_pkgname to pkgInfo.packageName)
                    metaData.add(R.string.file_meta_app_version to pkgInfo.versionName)
                    metaData.add(R.string.file_meta_app_min_sdk to pkgInfo.applicationInfo.minSdkVersion.toString())
                }
            }
            return metaData
        }
    }
}