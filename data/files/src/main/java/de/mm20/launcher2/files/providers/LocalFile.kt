package de.mm20.launcher2.files.providers

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.files.LocalFileSerializer
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TransparentLayer
import de.mm20.launcher2.ktx.formatToString
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.media.ThumbnailUtilsCompat
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.file.FileMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.Year
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import java.io.File as JavaIOFile

internal data class LocalFile(
    val id: Long,
    override val path: String,
    override val mimeType: String,
    override val size: Long,
    override val isDirectory: Boolean,
    override val metaData: FileMetadata,
    override val labelOverride: String? = null
) : File {

    override val label = path.substringAfterLast('/')

    override val owner: String? = null

    override fun overrideLabel(label: String): LocalFile {
        return this.copy(labelOverride = label)
    }

    override val domain: String = Domain

    override val key = "$domain://$path"

    override suspend fun loadIcon(
        context: Context,
        size: Int,
        themed: Boolean,
    ): LauncherIcon? {
        if (!JavaIOFile(path).exists()) return null
        when {
            mimeType.startsWith("image/") -> {
                val thumbnail = withContext(Dispatchers.IO) {
                    // Subsample large images while decoding, decoding them at full resolution
                    // just to create a small thumbnail can easily cause an OutOfMemoryError
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    var sampleSize = 1
                    while (size > 0 && bounds.outWidth / (sampleSize * 2) >= size && bounds.outHeight / (sampleSize * 2) >= size) {
                        sampleSize *= 2
                    }
                    ThumbnailUtils.extractThumbnail(
                        BitmapFactory.decodeFile(
                            path,
                            BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        ),
                        size, size
                    )
                } ?: return null

                return StaticLauncherIcon(
                    foregroundLayer = StaticIconLayer(
                        icon = BitmapDrawable(context.resources, thumbnail),
                        scale = 1f,
                    ),
                    backgroundLayer = ColorLayer()
                )
            }

            mimeType.startsWith("video/") -> {
                val thumbnail = withContext(Dispatchers.IO) {
                    ThumbnailUtilsCompat.createVideoThumbnail(
                        JavaIOFile(path),
                        Size(size, size)
                    )
                } ?: return null

                return StaticLauncherIcon(
                    foregroundLayer = StaticIconLayer(
                        icon = BitmapDrawable(context.resources, thumbnail),
                        scale = 1f,
                    ),
                    backgroundLayer = ColorLayer()
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

                return StaticLauncherIcon(
                    foregroundLayer = StaticIconLayer(
                        icon = BitmapDrawable(context.resources, thumbnail),
                        scale = 1f,
                    ),
                    backgroundLayer = ColorLayer()
                )

            }

            mimeType == "application/vnd.android.package-archive" -> {
                val pkgInfo = context.packageManager.getPackageArchiveInfo(path, 0)
                val icon = withContext(Dispatchers.IO) {
                    pkgInfo?.applicationInfo?.loadIcon(context.packageManager)
                } ?: return null
                when (icon) {
                    is AdaptiveIconDrawable -> {
                        return StaticLauncherIcon(
                            foregroundLayer = icon.foreground?.let {
                                StaticIconLayer(
                                    icon = it,
                                    scale = 1.5f,
                                )
                            } ?: TransparentLayer,
                            backgroundLayer = icon.background?.let {
                                StaticIconLayer(
                                    icon = it,
                                    scale = 1.5f,
                                )
                            } ?: TransparentLayer,
                        )
                    }

                    else -> {
                        return StaticLauncherIcon(
                            foregroundLayer = StaticIconLayer(
                                icon = icon,
                                scale = 0.7f,
                            ),
                            backgroundLayer = ColorLayer()
                        )
                    }
                }
            }
        }
        return null
    }


    private fun getLaunchIntent(context: Context): Intent {
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

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(getLaunchIntent(context), options)
    }

    override val isDeletable: Boolean
        get() {
            val file = java.io.File(path)
            return file.canWrite() && file.parentFile?.canWrite() == true
        }

    override suspend fun delete(context: Context) {
        super.delete(context)

        val file = java.io.File(path)

        withContext(NonCancellable + Dispatchers.IO) {
            file.deleteRecursively()

            context.contentResolver.delete(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.Files.FileColumns._ID} = ?",
                arrayOf(id.toString())
            )
        }
    }


    companion object {

        const val Domain = "file"

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
                "kvaesitso" -> "application/vnd.de.mm20.launcher2.backup"
                "kvtheme" -> "application/vnd.de.mm20.launcher2.theme"
                else -> "application/octet-stream"
            }
        }


        internal fun getMetaData(
            context: Context,
            mimeType: String,
            path: String
        ): FileMetadata {
            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var duration: Duration? = null
            var year: Year? = null
            var date: ZonedDateTime? = null
            var dimensions: FileMetadata.Dimensions? = null
            var location: String? = null
            var appName: String? = null
            var appPackageName: String? = null
            var appVersion: String? = null

            when {
                mimeType.startsWith("audio/") -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(path)

                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            ?.also { title = it }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            ?.also { artist = it }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                            ?.also { album = it }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                            ?.toIntOrNull()
                            ?.let { year = Year.of(it) }
                        retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                            ?.toDateOrNull()
                            ?.also { date = it }
                        retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()
                            ?.also { duration = it.milliseconds }
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
                                ?.toLongOrNull()
                        val height =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                                ?.toLongOrNull()
                        if (width != null && height != null) {
                            dimensions = FileMetadata.Dimensions(width.toInt(), height.toInt())
                        }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()
                            ?.let { duration = it.milliseconds }
                        retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                            ?.toDateOrNull()
                            ?.also { date = it }

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
                            val list = try {
                                Geocoder(context).getFromLocation(lon, lat, 1)
                            } catch (e: IOException) {
                                null
                            } catch (e: IllegalArgumentException) {
                                null
                            }
                            if (!list.isNullOrEmpty()) {
                                location = list[0].formatToString()
                            }
                        }
                    } catch (e: RuntimeException) {
                        CrashReporter.logException(e)
                    } catch (e: IOException) {
                        CrashReporter.logException(e)
                    } finally {
                        retriever.release()
                    }
                }

                mimeType.startsWith("image/") -> {
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(path, options)
                    val width = options.outWidth
                    val height = options.outHeight
                    if (height >= 0 && width >= 0) {
                        dimensions = FileMetadata.Dimensions(width, height)
                    }
                    try {
                        val exif = ExifInterface(path)
                        val loc = exif.latLong
                        if (loc != null && Geocoder.isPresent()) {
                            val list = try {
                                Geocoder(context).getFromLocation(loc[0], loc[1], 1)
                            } catch (e: IllegalArgumentException) {
                                null
                            } catch (e: IOException) {
                                null
                            }
                            if (!list.isNullOrEmpty()) {
                                location = list[0].formatToString()
                            }
                        }
                        date = exif.getAttribute(ExifInterface.TAG_DATETIME)?.toDateOrNull()
                    } catch (_: IOException) {

                    }
                }

                mimeType == "application/vnd.android.package-archive" -> {
                    val pkgInfo = context.packageManager.getPackageArchiveInfo(path, 0)
                        ?: return FileMetadata()

                    pkgInfo.applicationInfo?.loadLabel(context.packageManager)?.toString()?.also {
                        appName = it
                    }
                    appPackageName = pkgInfo.packageName
                    appVersion = pkgInfo.versionName
                }
            }
            return FileMetadata(
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                year = year,
                date = date,
                dimensions = dimensions,
                location = location,
                appName = appName,
                appPackageName = appPackageName,
                appVersion = appVersion,
            )
        }

        private fun String.toDateOrNull(): ZonedDateTime? {
            val formats = listOf(
                "yyyyMMdd'T'HHmmss",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy:MM:dd HH:mm:ss",
                "yyyy-MM-dd",
                "yyyy MM dd"
            )
            for(format in formats) {
                try {
                    return LocalDateTime.parse(
                        this,
                        DateTimeFormatter.ofPattern(format, Locale.ROOT)
                    ).atZone(ZonedDateTime.now().zone)
                } catch (_: DateTimeParseException) {
                }
            }
            return null

        }
    }

    override val canShare: Boolean
        get() = !isDirectory

    override fun share(context: Context) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uri = FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".fileprovider",
            java.io.File(path)
        )
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.type = mimeType
        context.startActivity(Intent.createChooser(shareIntent, null))
    }

    override fun getSerializer(): SearchableSerializer {
        return LocalFileSerializer()
    }
}