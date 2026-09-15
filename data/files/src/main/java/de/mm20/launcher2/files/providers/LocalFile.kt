package de.mm20.launcher2.files.providers

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.AdaptiveIconDrawable
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.format.DateUtils
import android.util.Size
import androidx.core.content.FileProvider
import androidx.core.net.toUri
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
import de.mm20.launcher2.search.FileMetaType
import de.mm20.launcher2.search.SearchableSerializer
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.File as JavaIOFile
import androidx.core.graphics.drawable.toDrawable

internal data class LocalFile(
    val id: Long,
    override val path: String,
    override val mimeType: String,
    override val size: Long,
    override val isDirectory: Boolean,
    override val metaData: ImmutableMap<FileMetaType, String>,
    override val labelOverride: String? = null
) : File {

    override val label = path.substringAfterLast('/')

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
                    ThumbnailUtils.extractThumbnail(
                        BitmapFactory.decodeFile(path),
                        size, size
                    )
                } ?: return null

                return StaticLauncherIcon(
                    foregroundLayer = StaticIconLayer(
                        icon = thumbnail.toDrawable(context.resources),
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
                        icon = thumbnail.toDrawable(context.resources),
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
                        icon = thumbnail.toDrawable(context.resources),
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
            path.toUri()
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
        if (context.tryStartActivity(getLaunchIntent(context), options)) {
            return true
        }

        if (isDirectory && path == Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path) {
            val viewDownloadsIntent = Intent("android.intent.action.VIEW_DOWNLOADS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK + Intent.FLAG_GRANT_READ_URI_PERMISSION)

            return context.tryStartActivity(viewDownloadsIntent, options)
        }

        return false
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
        ): ImmutableMap<FileMetaType, String> {
            val metaData = mutableMapOf<FileMetaType, String>()
            when {
                mimeType.startsWith("audio/") -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(path)

                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            ?.let { metaData[FileMetaType.Title] = it }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            ?.let { metaData[FileMetaType.Artist] = it }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                            ?.let { metaData[FileMetaType.Album] = it }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                            ?.let { metaData[FileMetaType.Year] = it }
                        retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()
                            ?.let {
                                metaData[FileMetaType.Duration] =
                                    DateUtils.formatElapsedTime((it) / 1000)
                            }
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
                            metaData[FileMetaType.Dimensions] = "${width}x$height"
                        }
                        val duration =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                ?.toLongOrNull()
                                ?.let {
                                    metaData[FileMetaType.Duration] =
                                        DateUtils.formatElapsedTime((it) / 1000)
                                }
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
                            if (list != null && list.size > 0) {
                                metaData[FileMetaType.Location] = list[0].formatToString()
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
                        metaData[FileMetaType.Dimensions] = "${width}x$height"
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
                            if (list != null && list.size > 0) {
                                metaData[FileMetaType.Location] = list[0].formatToString()
                            }
                        }
                    } catch (_: IOException) {

                    }
                }

                mimeType == "application/vnd.android.package-archive" -> {
                    val pkgInfo = context.packageManager.getPackageArchiveInfo(path, 0)
                        ?: return metaData.toImmutableMap()

                    pkgInfo.applicationInfo?.loadLabel(context.packageManager)?.toString()?.let {
                        metaData[FileMetaType.AppName] = it
                    }
                    pkgInfo.versionName?.let { metaData[FileMetaType.AppVersion] = it }
                    pkgInfo.packageName.let { metaData[FileMetaType.AppPackageName] = it }
                    pkgInfo.applicationInfo?.minSdkVersion?.toString()?.let {
                        metaData[FileMetaType.AppMinSdk] = it
                    }
                }
            }
            return metaData.toImmutableMap()
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