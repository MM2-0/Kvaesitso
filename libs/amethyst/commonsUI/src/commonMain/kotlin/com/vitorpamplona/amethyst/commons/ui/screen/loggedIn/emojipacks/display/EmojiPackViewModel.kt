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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.display

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.actions.EmojiPackActions
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiArchiveLimits
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiImport
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.extractEmojiArchive
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.lastPathSegmentOrNull
import com.vitorpamplona.amethyst.commons.service.uploads.mediaUriOfFile
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadToDefaultServer
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.Path
import kotlin.reflect.KClass

/** Uploads running at once during an import. */
private const val PARALLEL_UPLOADS = 3

@Stable
class EmojiPackViewModel(
    val account: Account,
    val packIdentifier: String,
) : ViewModel() {
    val selectedPackFlow =
        account.ownedEmojiPacks
            .getOwnedEmojiPackFlow(packIdentifier)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2500), null)

    var isUploadingEmojiImage by mutableStateOf(false)

    suspend fun addEmoji(
        emoji: EmojiUrlTag,
        isPrivate: Boolean,
    ) {
        account.addEmojiToOwnedPack(packIdentifier, emoji, isPrivate)
    }

    suspend fun removeEmoji(
        shortcode: String,
        isPrivate: Boolean,
    ) {
        account.removeEmojiFromOwnedPack(packIdentifier, shortcode, isPrivate)
    }

    suspend fun deletePack() {
        account.deleteOwnedEmojiPack(packIdentifier)
    }

    fun uploadEmojiImage(
        uri: SelectedMedia,
        uploader: MediaUploader,
        onUploaded: (String) -> Unit,
        onError: (String, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            upload(
                uri,
                uploader,
                onUploading = { isUploadingEmojiImage = it },
                onUploaded = onUploaded,
                onError = onError,
            )
        }
    }

    private suspend fun upload(
        galleryUri: SelectedMedia,
        uploader: MediaUploader,
        onUploading: (Boolean) -> Unit,
        onUploaded: (String) -> Unit,
        onError: (String, String) -> Unit,
    ) {
        onUploading(true)
        val url = uploadToDefaultServer(galleryUri, account, uploader, onError)
        onUploading(false)
        if (url != null) onUploaded(url)
    }

    /** True while [importFiles] runs; with no [importItems] yet, it is still reading the files. */
    var importRunning by mutableStateOf(false)
        private set

    /** The images the running or last import is uploading, in order, each with its status. */
    var importItems by mutableStateOf<ImmutableList<EmojiImportItem>>(persistentListOf())
        private set

    /** What the last [importFiles] did, until [clearImport]. */
    var importResult by mutableStateOf<EmojiImportResult?>(null)
        private set

    // Images taken out of a .zip. They stay until the dialog closes because its grid previews them.
    private val importTempFiles = mutableListOf<Path>()
    private var cleared = false

    fun clearImport() {
        importResult = null
        importItems = persistentListOf()
        deleteImportTempFiles()
    }

    override fun onCleared() {
        cleared = true
        deleteImportTempFiles()
        super.onCleared()
    }

    private fun deleteImportTempFiles() {
        importTempFiles.forEach { runCatching { platformFileSystem.delete(it, mustExist = false) } }
        importTempFiles.clear()
    }

    /**
     * Uploads [files] (images, and the images inside any .zip among them) to the default media
     * server and adds them to this pack in one new version of it, each named after its file.
     * Shortcodes the pack already has in that bucket are skipped before uploading.
     */
    suspend fun importFiles(
        files: List<SelectedMedia>,
        uploader: MediaUploader,
        isPrivate: Boolean,
    ) = withContext(Dispatchers.IO) {
        clearImport()
        importRunning = true
        try {
            // Fail before uploading anything rather than after: a pack that is gone, or a private
            // bucket the signer cannot decrypt, would otherwise strand every upload.
            val packEvent = account.ownedEmojiPacks.getOwnedEmojiPackEvent(packIdentifier) ?: return@withContext
            val existing =
                if (isPrivate) {
                    val privateTags = packEvent.privateTags(account.signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
                    privateTags.mapNotNullTo(HashSet(), EmojiUrlTag::parseCode)
                } else {
                    packEvent.publicEmojiCodes().toSet()
                }

            val candidates = mutableListOf<Pair<String, SelectedMedia>>()
            val unsupported = mutableListOf<String>()
            val unreadable = mutableListOf<String>()
            val tooLarge = mutableListOf<String>()
            val truncated = mutableListOf<String>()

            for (media in files) {
                val name = uploader.displayName(media.uri) ?: media.uri.lastPathSegmentOrNull() ?: media.uri.toString()
                val mimeType = media.mimeType ?: uploader.mimeType(media.uri)
                val size = uploader.fileSize(media.uri)
                if (EmojiImport.isZipFileName(name) || EmojiImport.isZipMimeType(mimeType)) {
                    // The archive is read whole into memory, so a huge one is refused unread.
                    if (size != null && size > EmojiArchiveLimits.MAX_ARCHIVE_BYTES) {
                        tooLarge.add(name)
                        continue
                    }
                    val archive =
                        try {
                            uploader.readBytes(media.uri)?.let { extractEmojiArchive(it) }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w("EmojiPackViewModel", "Could not read the archive $name", e)
                            null
                        }
                    if (archive == null) {
                        unreadable.add(name)
                        continue
                    }
                    archive.files.forEach { extracted ->
                        importTempFiles.add(extracted.path)
                        candidates.add(extracted.fileName to SelectedMedia(mediaUriOfFile(extracted.path), EmojiImport.imageMimeTypeOf(extracted.fileName)))
                    }
                    tooLarge.addAll(archive.tooLarge)
                    if (archive.truncated) truncated.add(name)
                } else if (mimeType?.startsWith("image/") == true || EmojiImport.isImageFileName(name)) {
                    if (size != null && size > EmojiArchiveLimits.MAX_ENTRY_BYTES) {
                        tooLarge.add(name)
                    } else {
                        candidates.add(name to SelectedMedia(media.uri, mimeType ?: EmojiImport.imageMimeTypeOf(name)))
                    }
                } else {
                    unsupported.add(name)
                }
            }

            val plan = EmojiImport.plan(candidates.map { it.first }, existing)
            val items =
                plan.toUpload.map { (index, code) ->
                    val (name, media) = candidates[index]
                    EmojiImportItem(code, name, media.uri.toString())
                }
            importItems = items.toImmutableList()

            // A few at a time: faster than one by one, without flooding the media server.
            val uploads = arrayOfNulls<EmojiUrlTag>(items.size)
            var failureReason: String? = null
            val slots = Semaphore(PARALLEL_UPLOADS)
            coroutineScope {
                plan.toUpload.forEachIndexed { i, (index, code) ->
                    launch {
                        slots.withPermit {
                            val item = items[i]
                            item.status = EmojiImportItem.Status.UPLOADING
                            val url = uploadToDefaultServer(candidates[index].second, account, uploader, onError = { _, message -> failureReason = message })
                            if (url != null) {
                                uploads[i] = EmojiUrlTag(code, url)
                                item.status = EmojiImportItem.Status.DONE
                            } else {
                                item.status = EmojiImportItem.Status.FAILED
                            }
                        }
                    }
                }
            }
            val uploaded = uploads.filterNotNull()

            val result =
                if (uploaded.isNotEmpty()) {
                    account.addEmojisToOwnedPack(packIdentifier, uploaded, isPrivate, EmojiPackActions.OnConflict.SKIP)
                } else {
                    null
                }

            importResult =
                EmojiImportResult(
                    added = result?.added?.size ?: 0,
                    skipped = (plan.skipped + result?.skipped.orEmpty()).toImmutableList(),
                    failed = items.size - uploaded.size,
                    failureReason = failureReason,
                    unnamed = plan.unnamed.toImmutableList(),
                    tooLarge = tooLarge.toImmutableList(),
                    unsupported = unsupported.toImmutableList(),
                    unreadable = unreadable.toImmutableList(),
                    truncated = truncated.toImmutableList(),
                )
        } finally {
            importRunning = false
            // Failed before a result (a signer error): back to the start, which the dialog shows.
            if (importResult == null) importItems = persistentListOf()
            if (cleared) deleteImportTempFiles()
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Initializer(
        val account: Account,
        val packIdentifier: String,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = EmojiPackViewModel(account, packIdentifier) as T
    }
}

/**
 * One image of an import: the shortcode it gets, its file, a preview Coil can load, and how its
 * upload is going. [status] is its own snapshot state, so a change redraws only this image's tile
 * and the parallel uploads can each write theirs.
 */
@Stable
class EmojiImportItem(
    val shortcode: String,
    val fileName: String,
    val preview: String,
    status: Status = Status.WAITING,
) {
    var status by mutableStateOf(status)

    enum class Status { WAITING, UPLOADING, DONE, FAILED }
}

/** What an import did: shortcodes in [skipped], file names in the other lists. */
@Immutable
data class EmojiImportResult(
    val added: Int,
    val skipped: ImmutableList<String>,
    /** How many uploads failed; [importItems][EmojiPackViewModel.importItems] marks which. */
    val failed: Int,
    /** The error of the last failed upload, as the uploader worded it. */
    val failureReason: String?,
    val unnamed: ImmutableList<String>,
    val tooLarge: ImmutableList<String>,
    val unsupported: ImmutableList<String>,
    val unreadable: ImmutableList<String>,
    /** Archives only partly read, for being over [EmojiArchiveLimits.MAX_IMAGES] or [EmojiArchiveLimits.MAX_TOTAL_BYTES]. */
    val truncated: ImmutableList<String> = persistentListOf(),
)
