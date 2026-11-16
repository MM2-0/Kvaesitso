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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag

/**
 * NIP-17 direct-message verbs — relay resolution policy + gift-wrap builders.
 *
 * Like [FollowActions] / [SearchActions] / [ZapActions], this is pure logic
 * usable from amy CLI, the Android App Functions adapter for Gemini, and any
 * other non-UI consumer. The send builders return signed gift wraps but do
 * NOT publish; the read side (decrypting incoming gift wraps) stays at the
 * caller because quartz's `GiftWrapEvent.unwrapAndUnsealOrNull` is already a
 * one-liner.
 *
 * **Caller responsibilities** that this object leaves to the consumer:
 *
 *  * **Publish.** Each wrap goes to its own recipient's DM-relay set —
 *    resolve via [resolveDmRelays] and hand each wrap to your relay client.
 *  * **Recipient resolution.** Translate npub / NIP-05 / hex to [HexKey]
 *    before calling — the Android UI uses `User.pubkeyHex`, amy uses
 *    `Context.requireUserHex`, the Gemini adapter would resolve through
 *    its own NIP-05 path.
 *  * **File upload (kind:15).** [buildFileDmReference] assumes the file is
 *    already at a URL. For "upload-then-DM", use
 *    `commons/.../service/upload/UploadOrchestrator` (jvmAndroid only, has
 *    an OkHttp dep) before calling here.
 *  * **Receipt of incoming DMs.** The kind:1059 gift-wrap drain, NIP-44
 *    unseal, and decrypt-to-inner-event step is a 3-line caller-side loop
 *    over [com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent.unwrapAndUnsealOrNull]
 *    — too small to bother extracting.
 */
object DmActions {
    /**
     * Source bucket from which [DmRelaySet.relays] was drawn. Useful for
     * surfacing "where did we deliver?" telemetry to the caller — amy
     * emits this on stdout, Gemini could mention it in the assistant
     * response.
     */
    enum class RelaySource {
        /** Recipient's NIP-17 inbox (kind:10050). The strict NIP-17 path. */
        KIND_10050,

        /** NIP-65 read marker (kind:10002 read relays). Fallback bucket. */
        NIP65_READ,

        /** Caller-provided bootstrap pool. Last-resort fallback. */
        BOOTSTRAP,

        /** No relays available — caller should refuse to send. */
        NONE,
    }

    /** Outcome of [resolveDmRelays]: the relays to publish to, plus which bucket they came from. */
    data class DmRelaySet(
        val relays: Set<NormalizedRelayUrl>,
        val source: RelaySource,
    )

    /**
     * Apply Amethyst's NIP-17 relay-resolution policy to a recipient.
     *
     * NIP-17 says clients "shouldn't try" to deliver a gift wrap unless the
     * recipient has published a kind:10050. In strict mode (the default), an
     * empty kind:10050 returns [RelaySource.NONE] so the caller refuses to
     * send. Permissive mode walks the fallback chain instead — NIP-65 read
     * relays, then the bootstrap pool — for cases like interop tests and
     * brand-new accounts where strict mode is too strict.
     *
     * @param recipientLists the recipient's relay-list snapshot from
     *   [RecipientRelayFetcher.fetchRelayLists] (or a local cache).
     *   Pass null when the recipient is unknown — same effect as empty lists.
     * @param bootstrap the caller's bootstrap relay pool, used as the
     *   last-resort fallback when [allowFallback] is true.
     * @param allowFallback opt into the NIP-65-read → bootstrap chain when
     *   kind:10050 is empty. Default false (strict mode).
     */
    fun resolveDmRelays(
        recipientLists: RecipientRelayFetcher.Lists?,
        bootstrap: Set<NormalizedRelayUrl>,
        allowFallback: Boolean = false,
    ): DmRelaySet {
        val dmInbox = recipientLists?.dmInbox?.toSet().orEmpty()
        if (dmInbox.isNotEmpty()) return DmRelaySet(dmInbox, RelaySource.KIND_10050)
        if (!allowFallback) return DmRelaySet(emptySet(), RelaySource.NONE)
        val nip65Read = recipientLists?.nip65Read()?.toSet().orEmpty()
        if (nip65Read.isNotEmpty()) return DmRelaySet(nip65Read, RelaySource.NIP65_READ)
        return DmRelaySet(bootstrap, RelaySource.BOOTSTRAP)
    }

    /**
     * Build a NIP-17 text DM (kind:14) wrapped in a NIP-59 gift wrap per
     * recipient. The returned [NIP17Factory.Result] carries the inner
     * event for local caching and one gift wrap per recipient (just one
     * here — the recipient + the sender's own copy). Caller publishes each
     * wrap to that recipient's DM-relay set.
     */
    suspend fun buildTextDm(
        signer: NostrSigner,
        recipient: HexKey,
        text: String,
    ): NIP17Factory.Result {
        val template = ChatMessageEvent.build(text, listOf(PTag(recipient)))
        return NIP17Factory().createMessageNIP17(template, signer)
    }

    /**
     * Build a NIP-17 encrypted-file DM (kind:15) for a file that has
     * already been uploaded to [url]. The [cipher]'s key + nonce travel
     * inside the gift-wrapped inner event so only the recipient — and the
     * sender, who keeps their own copy — can decrypt the bytes at [url].
     *
     * Pre-uploaded URL only: the upload step is jvmAndroid-only (needs
     * OkHttp). For "upload then DM" use `UploadOrchestrator` first and
     * pass its returned URL + the cipher you generated here.
     */
    suspend fun buildFileDmReference(
        signer: NostrSigner,
        recipient: HexKey,
        url: String,
        cipher: AESGCM,
        mimeType: String? = null,
        hash: String? = null,
        originalHash: String? = null,
        size: Int? = null,
        dimension: DimensionTag? = null,
        blurhash: String? = null,
    ): NIP17Factory.Result {
        val template =
            ChatMessageEncryptedFileHeaderEvent.build(
                to = listOf(PTag(recipient)),
                url = url,
                cipher = cipher,
                mimeType = mimeType,
                hash = hash,
                size = size,
                dimension = dimension,
                blurhash = blurhash,
                originalHash = originalHash,
            )
        return NIP17Factory().createEncryptedFileNIP17(template, signer)
    }
}
