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
package com.vitorpamplona.amethyst.commons.geocaches.create

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheGeohash
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.HintObfuscation
import com.vitorpamplona.quartz.nipCCGeocaching.listing.rot13
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheSize
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.CacheType
import com.vitorpamplona.quartz.nipCCGeocaching.listing.tags.TypeModifier
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The cache composer's state.
 *
 * Two parts of this deserve care rather than the usual form plumbing:
 *
 * **The verification keypair.** Generated locally, its *public* half goes in the listing and its
 * *private* half is shown to the owner once, as a QR to print and drop in the box. Amethyst does
 * not store it — there is nowhere safe to put a secret whose whole job is to live in a physical
 * container, and pretending otherwise would invite an owner to rely on a vault that does not
 * exist. [generatedPrivateKey] is in memory for the life of this composer and no longer.
 *
 * **The hint.** NIP-CC contradicts itself about whether hints go on the wire scrambled, and the
 * network is split. Amethyst writes ROT13, which is the reference client's choice and the one
 * that fails safe. [hintPreview] shows the author exactly what a finder sees, because the author
 * is the only person who can tell whether the scrambled form reads as noise.
 */
class NewGeocacheViewModel : ViewModel() {
    private lateinit var account: Account

    val name = mutableStateOf("")
    val description = mutableStateOf("")
    val geohash = mutableStateOf("")
    val difficulty = mutableStateOf(1)
    val terrain = mutableStateOf(1)
    val size = mutableStateOf(CacheSize.REGULAR)
    val type = mutableStateOf<CacheType?>(CacheType.TRADITIONAL)
    val modifiers = mutableStateListOf<TypeModifier>()
    val hint = mutableStateOf("")
    val scrambleHint = mutableStateOf(true)
    val mission = mutableStateOf("")
    val images = mutableStateListOf<String>()
    val requireProof = mutableStateOf(false)

    val isUploading = mutableStateOf(false)
    val isPublishing = mutableStateOf(false)

    /** Shown once, never stored. Null until the owner turns proof on. */
    val generatedPrivateKey = mutableStateOf<HexKey?>(null)
    private var generatedPublicKey: HexKey? = null

    private var editAddress: Address? = null

    val isEditing: Boolean
        get() = editAddress != null

    fun init(account: Account) {
        if (!::account.isInitialized) this.account = account
    }

    fun prefillGeohash(value: String?) {
        if (value != null && geohash.value.isEmpty()) geohash.value = value
    }

    /**
     * Loads an existing listing for editing.
     *
     * The hint is un-scrambled back into plaintext for the field, using the same per-hint
     * heuristic a reader gets — an author editing a cache published by another client should see
     * their words, not ciphertext, whichever convention that client used.
     */
    fun loadForEdit(
        kind: Int,
        pubKeyHex: String,
        dTag: String,
    ) {
        if (editAddress != null) return
        val address = Address(kind, pubKeyHex, dTag)
        val existing = LocalCache.addressables.get(address)?.event as? GeocacheListingEvent ?: return

        editAddress = address
        name.value = existing.cacheName().orEmpty()
        description.value = existing.content
        geohash.value = existing.location().orEmpty()
        difficulty.value = existing.difficulty() ?: 1
        terrain.value = existing.terrain() ?: 1
        existing.cacheSize()?.let { size.value = it }
        type.value = existing.cacheType()
        modifiers.clear()
        // typeModifiers() is keyed by category — one winner per category is the spec's rule.
        // The composer edits the flat set, so take the values.
        modifiers.addAll(existing.typeModifiers().values)
        hint.value = existing.hintOnWire()?.let { HintObfuscation.revealed(it) }.orEmpty()
        mission.value = existing.mission().orEmpty()
        images.clear()
        images.addAll(existing.images())
        // An existing verification key is kept as-is: the printed QR in the box still matches it,
        // and rotating it on an edit would silently invalidate every code already in the field.
        generatedPublicKey = existing.verificationKey()
        requireProof.value = generatedPublicKey != null
    }

    fun removeImage(url: String) {
        images.remove(url)
    }

    fun toggleModifier(modifier: TypeModifier) {
        if (modifiers.contains(modifier)) modifiers.remove(modifier) else modifiers.add(modifier)
    }

    /** Turning proof on mints a keypair; turning it off forgets it rather than keeping it around. */
    fun setRequireProof(enabled: Boolean) {
        requireProof.value = enabled
        if (!enabled) {
            generatedPrivateKey.value = null
            generatedPublicKey = null
            return
        }
        if (generatedPublicKey != null) return

        val pair = KeyPair()
        generatedPrivateKey.value = pair.privKey?.toHexKey()
        generatedPublicKey = pair.pubKey.toHexKey()
    }

    /** What a finder will actually see for this hint, given the current scramble setting. */
    fun hintPreview(): String {
        val text = hint.value.trim()
        if (text.isEmpty()) return ""
        return if (scrambleHint.value) rot13(text) else text
    }

    fun hasName() = name.value.isNotBlank()

    fun hasLocation() = geohash.value.length >= GeocacheGeohash.MIN_TAGGED

    fun isPreciseEnough() = GeocacheGeohash.isPreciseEnough(geohash.value, size.value)

    fun isValid() = hasName() && hasLocation()

    suspend fun uploadImage(
        uri: MediaUri,
        mimeType: String?,
        uploader: MediaUploader,
    ): Boolean {
        if (!::account.isInitialized) return false
        isUploading.value = true
        try {
            val result =
                UploadOrchestrator().upload(
                    uri = uri,
                    mimeType = mimeType,
                    alt = name.value.ifBlank { null },
                    contentWarningReason = null,
                    compressionQuality = CompressorQuality.MEDIUM,
                    server = account.settings.defaultFileServer,
                    account = account,
                    uploader = uploader,
                )
            val url =
                ((result as? UploadingState.Finished)?.result as? UploadOrchestrator.OrchestratorResult.ServerResult)?.url
            url?.let { images.add(it) }
            return url != null
        } finally {
            isUploading.value = false
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun publish(): Boolean {
        if (!isValid()) return false

        isPublishing.value = true
        try {
            val template =
                GeocacheListingEvent.build(
                    name = name.value.trim(),
                    description = description.value.trim(),
                    geohash = geohash.value.trim(),
                    difficulty = difficulty.value,
                    terrain = terrain.value,
                    size = size.value,
                    type = type.value,
                    modifiers = modifiers.toList(),
                    // The builder scrambles on the way out. Passing the plaintext when the author
                    // asked for no scrambling would double-apply it, so the already-previewed
                    // form is un-rotated once here to cancel that out.
                    hint =
                        hint.value
                            .trim()
                            .ifBlank { null }
                            ?.let { if (scrambleHint.value) it else rot13(it) },
                    mission = mission.value.trim().ifBlank { null },
                    verificationPubKey = generatedPublicKey,
                    images = images.toList().ifEmpty { null },
                    // Where finders should log. The owner's own outbox is the honest default: it is
                    // where they will be reading the thread from.
                    relays =
                        account.outboxRelays.flow.value
                            .toList()
                            .ifEmpty { null },
                    dTag = editAddress?.dTag ?: Uuid.random().toString(),
                )

            return runCatchingCancellable { account.signAndComputeBroadcast(template) }.isSuccess
        } finally {
            isPublishing.value = false
        }
    }
}
