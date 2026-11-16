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
package com.vitorpamplona.amethyst.commons.podcasts

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.podcasts.PodcastValue
import com.vitorpamplona.quartz.podcasts.PodcastValueRecipient

/**
 * Editable state for a Podcasting-2.0 value-for-value (V4V) split, shared by the show and episode
 * composers. Holds one [RecipientDraft] per payee; [toPodcastValue] turns the drafts back into a
 * [PodcastValue] on save (or null when there are no payable recipients). The suggested amount /
 * currency / enabled flag on a loaded block are carried through untouched — the editor only manages
 * the recipient list.
 *
 * Lives in `commons` (a CLI-safe snapshot-state holder, no Compose UI) so any front end can drive a
 * V4V split editor; the actual editor composable is platform-side.
 */
class V4VSplitEditorState {
    val recipients = mutableStateListOf<RecipientDraft>()

    private var amount: Long? = null
    private var currency: String? = null
    private var enabled: Boolean? = null

    fun load(value: PodcastValue?) {
        recipients.clear()
        amount = value?.amount
        currency = value?.currency
        enabled = value?.enabled
        value?.recipients?.forEach { recipients.add(RecipientDraft.from(it)) }
    }

    /** Add a blank row for a raw destination (a node keysend, or a non-Nostr lightning address). */
    fun addManual() {
        recipients.add(RecipientDraft())
    }

    /**
     * Add a Nostr user as a recipient. Returns false (and adds nothing) if the user has no lightning
     * address to pay — there's nothing to put in the value block. Duplicate users are ignored.
     */
    fun addUser(user: User): Boolean {
        if (user.lnAddress().isNullOrBlank()) return false
        if (recipients.any { it.user.value?.pubkeyHex == user.pubkeyHex }) return true
        recipients.add(RecipientDraft.forUser(user))
        return true
    }

    fun remove(draft: RecipientDraft) {
        recipients.remove(draft)
    }

    /** Sum of the weights of the payable recipients — used to show each as a percentage. */
    fun totalSplit(): Int = recipients.mapNotNull { it.toRecipient() }.sumOf { it.split }

    fun toPodcastValue(): PodcastValue? {
        val valid = recipients.mapNotNull { it.toRecipient() }
        if (valid.isEmpty()) return null
        return PodcastValue(
            enabled = enabled,
            amount = amount,
            currency = currency,
            recipients = valid,
        )
    }
}

/**
 * One editable recipient row. Either backed by a Nostr [user] (rendered with avatar + name; its
 * lightning address resolves at save time) or a raw destination typed by hand ([name]/[isNode]/
 * [address]). All fields are Compose state so the editor recomposes as they change.
 */
class RecipientDraft {
    /** When set, this row is a Nostr user — paid at their lud16, shown with avatar + name. */
    val user = mutableStateOf<User?>(null)

    val name = mutableStateOf("")

    /** false = lnaddress (LNURL-pay), true = node (keysend to a raw node pubkey). */
    val isNode = mutableStateOf(false)
    val address = mutableStateOf("")
    val split = mutableStateOf("1")
    val fee = mutableStateOf(false)

    /** A recipient is payable once it resolves to an address and has a positive weight. */
    fun toRecipient(): PodcastValueRecipient? {
        val weight = split.value.trim().toIntOrNull() ?: return null
        if (weight <= 0) return null

        user.value?.let { u ->
            val lud = u.lnAddress()?.takeIf { it.isNotBlank() } ?: return null
            return PodcastValueRecipient(
                name = u.toBestDisplayName(),
                type = PodcastValue.TYPE_LNADDRESS,
                address = lud,
                split = weight,
                fee = if (fee.value) true else null,
            )
        }

        val addr = address.value.trim()
        if (addr.isBlank()) return null
        return PodcastValueRecipient(
            name = name.value.trim().ifBlank { null },
            type = if (isNode.value) PodcastValue.TYPE_NODE else PodcastValue.TYPE_LNADDRESS,
            address = addr,
            split = weight,
            fee = if (fee.value) true else null,
        )
    }

    companion object {
        fun forUser(user: User): RecipientDraft =
            RecipientDraft().apply {
                this.user.value = user
            }

        fun from(recipient: PodcastValueRecipient): RecipientDraft =
            RecipientDraft().apply {
                name.value = recipient.name.orEmpty()
                isNode.value = recipient.type == PodcastValue.TYPE_NODE
                address.value = recipient.address.orEmpty()
                split.value = recipient.split.toString()
                fee.value = recipient.fee == true
            }
    }
}
