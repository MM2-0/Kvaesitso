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
package com.vitorpamplona.amethyst.commons.geocaches.log

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
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationValidator
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the scanned code turned out to be. Each state is a different sentence to the user. */
enum class ScanOutcome {
    NONE,
    VERIFIED,
    NOT_A_KEY,
    WRONG_CACHE,
}

/**
 * The found-log composer's state, and the one place in Amethyst that handles a geocache
 * verification secret.
 *
 * The threat model is worth stating because it is unusual: the key arrives by pointing a camera
 * at an object in the physical world, which means it is attacker-controlled input that the user
 * has been socially engineered into trusting. So:
 *
 * - it is parsed defensively (hex or nsec, exactly 32 bytes, non-zero) before it touches crypto;
 * - it is used for exactly one signature, on [Dispatchers.Default], through a throwaway
 *   [NostrSignerInternal] that is not retained anywhere;
 * - it is never written to state, never logged, never persisted, and the local reference is
 *   dropped as soon as the 7517 exists;
 * - the resulting 7517 is validated against the listing before it is shown as proof, so a code
 *   from a *different* cache reads as a mistake rather than silently producing a log that every
 *   other client will reject.
 */
class LogGeocacheFindViewModel : ViewModel() {
    private lateinit var account: Account

    val message = mutableStateOf("")
    val missionAnswer = mutableStateOf("")
    val images = mutableStateListOf<String>()
    val isUploading = mutableStateOf(false)
    val isPublishing = mutableStateOf(false)
    val scanOutcome = mutableStateOf(ScanOutcome.NONE)

    /**
     * The signed proof, not the key. A [GeocacheVerificationEvent] is already public — it is
     * meant to be embedded in the log and read by everyone — so holding it costs nothing, while
     * holding the key that made it would be the whole problem.
     */
    private var verification: GeocacheVerificationEvent? = null

    private var cacheAddress: Address? = null

    fun init(
        account: Account,
        address: Address,
    ) {
        if (!::account.isInitialized) this.account = account
        cacheAddress = address
    }

    fun listing(): GeocacheListingEvent? = cacheAddress?.let { LocalCache.addressables.get(it)?.event as? GeocacheListingEvent }

    fun hasProof() = verification != null

    fun isValid() = message.value.isNotBlank() || images.isNotEmpty() || hasProof()

    /**
     * Turns a scanned string into a verification event for this cache, or explains why it could
     * not.
     *
     * Returns without retaining anything on every failure path — an unparseable code leaves no
     * trace, and a code for the wrong cache leaves the *proof* discarded rather than attached to
     * a log that would be rejected downstream.
     */
    suspend fun consumeScannedCode(scanned: String?) {
        val listing = listing()
        val address = cacheAddress
        if (listing == null || address == null) {
            scanOutcome.value = ScanOutcome.NOT_A_KEY
            return
        }

        val privKeyHex = parsePrivateKey(scanned)
        if (privKeyHex == null) {
            scanOutcome.value = ScanOutcome.NOT_A_KEY
            return
        }

        val me = account.userProfile().pubkeyHex

        val signed =
            withContext(Dispatchers.Default) {
                runCatchingCancellable {
                    // Scoped to this block so the signer — and the key inside it — becomes
                    // garbage the moment the signature exists.
                    val ephemeral = NostrSignerInternal(KeyPair(Hex.decode(privKeyHex)))
                    ephemeral.sign(
                        GeocacheVerificationEvent.build(
                            finderPubKey = me,
                            cache = address,
                            relayHint = listing.logRelays().firstOrNull(),
                        ),
                    )
                }.getOrNull()
            }

        if (signed == null) {
            scanOutcome.value = ScanOutcome.NOT_A_KEY
            return
        }

        // The signature is only proof if it holds up against *this* listing. A code from
        // another cache signs perfectly well and is still worthless here.
        if (!GeocacheVerificationValidator.isValid(signed, listing, me)) {
            verification = null
            scanOutcome.value = ScanOutcome.WRONG_CACHE
            return
        }

        verification = signed
        scanOutcome.value = ScanOutcome.VERIFIED
    }

    /**
     * Accepts the two forms a cache owner might print: raw 64-character hex, or an `nsec`.
     *
     * The length check is explicit because [Hex.isHex64] only validates the alphabet, and the
     * all-zero scalar is rejected because it is not a valid secp256k1 private key — the curve
     * library's behaviour on it is not something to find out at signing time.
     */
    private fun parsePrivateKey(scanned: String?): HexKey? {
        val trimmed = scanned?.trim()?.ifBlank { null } ?: return null

        val hex =
            when {
                trimmed.startsWith("nsec") -> runCatching { decodePrivateKeyAsHexOrNull(trimmed) }.getOrNull()
                trimmed.length == 64 && Hex.isHex64(trimmed) -> trimmed
                else -> null
            } ?: return null

        if (hex.length != 64 || !Hex.isHex64(hex)) return null
        if (hex.all { it == '0' }) return null

        return hex
    }

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
                    alt = null,
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

    /**
     * Publishes the 7516.
     *
     * The Key Quest answer is appended to the log body rather than given a tag of its own:
     * NIP-CC defines `mission` on the listing but nothing for the response, so the only
     * interoperable place for it is the text every client already renders.
     */
    suspend fun publish(): Boolean {
        val listing = listing() ?: return false
        val address = cacheAddress ?: return false
        if (!isValid()) return false

        isPublishing.value = true
        try {
            val body =
                buildString {
                    append(message.value.trim())
                    val answer = missionAnswer.value.trim()
                    if (answer.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append(answer)
                    }
                }

            val template =
                GeocacheFoundLogEvent.build(
                    message = body,
                    cache = address,
                    relayHint = listing.logRelays().firstOrNull(),
                    verification = verification,
                    images = images.toList().ifEmpty { null },
                )

            // The cache names where finders should log. Honouring that is what keeps the
            // thread together for someone reading the cache on a different relay set.
            return runCatchingCancellable {
                account.signAndSendPrivatelyOrBroadcast(template) { listing.logRelays().ifEmpty { null } }
            }.isSuccess
        } finally {
            isPublishing.value = false
        }
    }
}
