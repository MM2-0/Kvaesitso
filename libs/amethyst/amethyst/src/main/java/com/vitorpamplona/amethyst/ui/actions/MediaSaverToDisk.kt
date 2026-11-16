/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.actions

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.ui.actions.MediaSaverToDisk.AMETHYST_SUBDIRECTORY
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object MediaSaverToDisk {
    suspend fun saveDownloadingIfNeeded(
        videoUri: String?,
        okHttpClient: (String) -> OkHttpClient,
        mimeType: String?,
        localContext: Context,
        resolveBlossom: suspend (String) -> String? = { null },
        onSuccess: () -> Any?,
        onError: (Throwable) -> Any?,
    ) {
        // No dispatch here: save() and downloadAndSave() both move themselves to IO.
        when {
            videoUri.isNullOrBlank() -> {
                return
            }

            videoUri.startsWith("file") -> {
                save(
                    localFile = videoUri.toUri().toFile(),
                    mimeType = mimeType,
                    context = localContext,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            }

            else -> {
                downloadAndSave(
                    url = videoUri,
                    mimeType = mimeType,
                    okHttpClient = okHttpClient,
                    context = localContext,
                    resolveBlossom = resolveBlossom,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            }
        }
    }

    /**
     * Saves the image to the gallery. May require a storage permission.
     *
     * `blossom:` (BUD-10) URIs are resolved to a concrete `http(s)` server URL
     * via [resolveBlossom] before downloading, since OkHttp only speaks
     * http/https. When resolution fails the save reports an error instead of
     * crashing with "expected scheme http or https but was blossom".
     *
     * @see AMETHYST_SUBDIRECTORY
     */
    suspend fun downloadAndSave(
        url: String,
        mimeType: String?,
        okHttpClient: (String) -> OkHttpClient,
        context: Context,
        resolveBlossom: suspend (String) -> String? = { null },
        onSuccess: () -> Any?,
        onError: (Throwable) -> Any?,
    ) {
        try {
            val downloadUrl =
                if (url.startsWith(BLOSSOM_SCHEME, ignoreCase = true)) {
                    resolveBlossom(url)
                        ?: throw IOException("Could not find a Blossom server that hosts $url")
                } else {
                    url
                }

            val client = okHttpClient(downloadUrl)

            val request =
                Request
                    .Builder()
                    .get()
                    .url(downloadUrl)
                    .build()

            client.newCall(request).executeAsync().use { response ->
                withContext(Dispatchers.IO) {
                    check(response.isSuccessful) {
                        "Failed to download $downloadUrl: HTTP ${response.code} ${response.message}"
                    }

                    val trimmedUrl = trimInlineMetaData(downloadUrl)
                    val headerType =
                        response
                            .header("Content-Type")
                            ?.substringBefore(";")
                            ?.trim()

                    // Resolved for both paths: the API level decides how the file is
                    // written, never which directory it belongs in.
                    val realType =
                        headerType?.takeIf(::isSaveableMimeType)
                            ?: mimeType?.takeIf(::isSaveableMimeType)
                            ?: getMimeTypeFromExtension(trimmedUrl).takeIf(::isSaveableMimeType)
                            ?: ""

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Deliberately Q-only: MediaStore refuses an insert without a usable
                        // type, so there is nothing to do but report it. The legacy path has
                        // always written whatever it downloaded and still does - an unresolved
                        // type lands in Downloads, which accepts any file.
                        check(realType.isNotBlank()) { "Can't find out the content type" }

                        saveContentQ(
                            displayName = File(trimmedUrl).nameWithoutExtension,
                            contentType = realType,
                            contentSource = response.body.source(),
                            contentResolver = context.contentResolver,
                        )
                    } else {
                        saveContentDefault(
                            fileName = File(trimmedUrl).name,
                            contentType = realType,
                            contentSource = response.body.source(),
                            context = context,
                        )
                    }
                    onSuccess()
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MediaSaverToDisk", "Error parsing response", e)
            onError(e)
        }
    }

    private fun getMimeTypeFromExtension(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase().let {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(it).orEmpty()
        }

    private fun isSaveableMimeType(type: String): Boolean =
        type.isNotBlank() &&
            (
                MediaStoreTarget.of(type) != MediaStoreTarget.DOWNLOADS ||
                    type.equals(PDF_MIME_TYPE, ignoreCase = true)
            )

    /**
     * Copies a local file into the gallery. Suspending and dispatched to IO like
     * [downloadAndSave]: callers reach this from click handlers, and a
     * storage-permission callback among them launches on the main dispatcher,
     * where copying a whole video would block the UI thread.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun save(
        localFile: File,
        mimeType: String?,
        context: Context,
        onSuccess: () -> Any?,
        onError: (Throwable) -> Any?,
    ) {
        withContext(Dispatchers.IO) {
            try {
                // use{}: readAll leaves its source open, so without this the file
                // descriptor stays open until the finalizer runs.
                localFile.inputStream().source().buffer().use { buffer ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveContentQ(
                            displayName = Uuid.random().toString(),
                            contentType = mimeType ?: "",
                            contentSource = buffer,
                            contentResolver = context.contentResolver,
                        )
                    } else {
                        val extension =
                            mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""
                        saveContentDefault(
                            fileName = "${Uuid.random()}.$extension",
                            contentType = mimeType ?: "",
                            contentSource = buffer,
                            context = context,
                        )
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("MediaSaverToDisk", "Unable to save", e)
                onError(e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveContentQ(
        displayName: String,
        contentType: String,
        contentSource: BufferedSource,
        contentResolver: ContentResolver,
    ) {
        val cleanMimeType = normalizeMimeTypeForMediaStore(contentType.substringBefore(";").trim())
        val target = MediaStoreTarget.of(cleanMimeType)

        val contentValues =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, cleanMimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    target.relativeDirectory + File.separatorChar + AMETHYST_SUBDIRECTORY,
                )
            }

        val uri = contentResolver.insert(target.collectionUri(), contentValues)
        checkNotNull(uri) { "Can't insert the new content" }

        try {
            val outputStream = contentResolver.openOutputStream(uri)
            checkNotNull(outputStream) { "Can't open the content output stream" }

            outputStream.use { contentSource.readAll(it.sink()) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun saveContentDefault(
        fileName: String,
        contentType: String,
        contentSource: BufferedSource,
        context: Context,
    ) {
        val baseDir = MediaStoreTarget.of(contentType).relativeDirectory

        val subdirectory =
            File(
                Environment.getExternalStoragePublicDirectory(baseDir),
                AMETHYST_SUBDIRECTORY,
            ).apply {
                if (!exists()) mkdirs()
            }

        val outputFile = File(subdirectory, fileName)

        outputFile.outputStream().use { contentSource.readAll(it.sink()) }

        // Call the media scanner manually, so the image
        // appears in the gallery faster.
        MediaScannerConnection.scanFile(context, arrayOf(outputFile.toString()), null, null)
    }

    private fun trimInlineMetaData(url: String): String = url.substringBefore("#")

    // Android's MediaStore only accepts a fixed allow-list of MIME types.
    // MimeTypeMap returns variants like video/x-m4v that MediaProvider rejects,
    // so map them to the closest supported equivalent.
    internal fun normalizeMimeTypeForMediaStore(mimeType: String): String =
        when (mimeType.lowercase()) {
            "video/x-m4v" -> "video/mp4"
            else -> mimeType
        }

    /**
     * The MediaStore collection a download is filed under, together with the public
     * directory it is written to.
     *
     * MediaProvider validates the primary directory of [MediaStore.MediaColumns.RELATIVE_PATH]
     * against the collection being inserted into and rejects a mismatch with
     * `IllegalArgumentException: Primary directory Pictures not allowed for
     * content://media/external/video/media; allowed directories are [DCIM, Movies]`.
     * A collection usually accepts more than one directory; these are the ones Amethyst
     * files under.
     */
    internal enum class MediaStoreTarget(
        val relativeDirectory: String,
    ) {
        // The directory names are the values of Environment.DIRECTORY_PICTURES, _MUSIC,
        // _MOVIES and _DOWNLOADS. They are spelled out because those are plain static
        // fields that the unit-test android.jar leaves null, which would make this
        // mapping impossible to cover off-device. MediaStoreTargetInstrumentedTest pins
        // them back to the platform constants on-device.
        IMAGES("Pictures"),
        AUDIO("Music"),
        VIDEO("Movies"),
        DOWNLOADS("Download"),
        ;

        /**
         * Has to stay a method. The EXTERNAL_CONTENT_URI fields are null under the same
         * unit-test android.jar, and MediaStore.Downloads only exists from API 29, so
         * reading them from the constructor would break class init off-device and below Q.
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        fun collectionUri(): Uri =
            when (this) {
                IMAGES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                DOWNLOADS -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }

        companion object {
            /**
             * PDFs, and anything that isn't image, audio or video content, go to Downloads
             * — the one collection that accepts every kind of file.
             */
            fun of(mimeType: String): MediaStoreTarget =
                when {
                    mimeType.startsWith("image/", ignoreCase = true) -> IMAGES
                    mimeType.startsWith("audio/", ignoreCase = true) -> AUDIO
                    mimeType.startsWith("video/", ignoreCase = true) -> VIDEO
                    else -> DOWNLOADS
                }
        }
    }

    private const val AMETHYST_SUBDIRECTORY = "Amethyst"
    private const val PDF_MIME_TYPE = "application/pdf"
    private const val BLOSSOM_SCHEME = "blossom:"
}
