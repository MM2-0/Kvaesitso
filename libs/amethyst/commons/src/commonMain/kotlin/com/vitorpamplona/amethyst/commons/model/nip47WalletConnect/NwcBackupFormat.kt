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
package com.vitorpamplona.amethyst.commons.model.nip47WalletConnect

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import kotlinx.serialization.Serializable

@Serializable
data class NwcBackupPayload(
    val v: Int = NwcBackupFormat.VERSION,
    val wallets: List<NwcWalletEntry> = emptyList(),
    val defaultId: String? = null,
)

/** NWC relay backups as NIP-44 encrypted kind 30078 events, the convention Wisp, zap.cooking and Sidecar share. */
object NwcBackupFormat {
    const val VERSION = 1

    const val D_TAG = "amethyst-nwc-backup"

    const val WISP_D_TAG = "nwc-wallet-backup"

    const val SIDECAR_D_TAG = "sidecar:nwc-backup"

    const val ZAPCOOKING_D_TAG = "zapcooking-nwc-backup"

    /** In priority order on an equal timestamp. */
    val RESTORE_D_TAGS = listOf(D_TAG, WISP_D_TAG, SIDECAR_D_TAG, ZAPCOOKING_D_TAG)

    const val ENCRYPTION_TAG_NAME = "encryption"

    const val ENCRYPTION_NIP44 = "nip44"

    fun contentTag(): Array<String> = arrayOf(ENCRYPTION_TAG_NAME, ENCRYPTION_NIP44)

    /** Dark Wisp shares Wisp's tag. */
    fun appName(dTag: String): String =
        when (dTag) {
            D_TAG -> "Amethyst"
            WISP_D_TAG -> "Wisp"
            SIDECAR_D_TAG -> "Sidecar"
            ZAPCOOKING_D_TAG -> "zap.cooking"
            else -> dTag
        }

    fun dTagOf(tags: Array<Array<String>>): String? = tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1)

    /** Empty content or Wisp's `deleted` tag, which NIP-09-unaware readers still understand. */
    fun isTombstone(
        tags: Array<Array<String>>,
        content: String,
    ): Boolean = content.isBlank() || tags.any { it.size >= 2 && it[0] == "deleted" && it[1] == "true" }

    /** The newest event per d-tag decides, across relays; a tombstone wins a same-second tie. */
    fun liveBackups(events: Iterable<Event>): List<Event> {
        val newestPerTag = HashMap<String, Event>()
        for (event in events) {
            val tag = dTagOf(event.tags) ?: continue
            if (tag !in RESTORE_D_TAGS) continue
            val current = newestPerTag[tag]
            if (current == null ||
                event.createdAt > current.createdAt ||
                (event.createdAt == current.createdAt && isTombstone(event.tags, event.content))
            ) {
                newestPerTag[tag] = event
            }
        }
        return newestPerTag.values
            .filterNot { isTombstone(it.tags, it.content) }
            .sortedWith(
                compareByDescending<Event> { it.createdAt }
                    .thenBy { RESTORE_D_TAGS.indexOf(dTagOf(it.tags)) },
            )
    }

    fun encode(
        wallets: List<NwcWalletEntryNorm>,
        defaultId: String?,
    ): String {
        val entries = wallets.mapNotNull { it.denormalize() }
        return JsonMapper.toJson(NwcBackupPayload(wallets = entries, defaultId = defaultId))
    }

    /** Accepts Amethyst's JSON envelope or the bare URI other apps store; null for an empty wallet list. */
    fun decodePlaintext(plaintext: String): NwcBackupPayload? {
        val trimmed = plaintext.trim()
        if (trimmed.isEmpty()) return null

        runCatching { JsonMapper.fromJson<NwcBackupPayload>(trimmed) }
            .getOrNull()
            ?.takeIf { it.wallets.isNotEmpty() }
            ?.let { return it }

        return runCatching { Nip47WalletConnect.parse(trimmed) }
            .getOrNull()
            ?.let { uri ->
                uri.denormalize()?.let { denorm ->
                    NwcBackupPayload(wallets = listOf(NwcWalletEntry(name = "Lightning Wallet", uri = denorm)))
                }
            }
    }
}
