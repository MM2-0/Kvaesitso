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
package com.vitorpamplona.amethyst.commons.model.nip38UserStatuses

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.UserDependencies
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip40Expiration.isExpired
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class UserStatusCache : UserDependencies {
    val statuses = MutableStateFlow<ImmutableList<AddressableNote>>(persistentListOf())

    val sortModel: Comparator<Note> =
        compareBy(
            { it.event?.expiration() ?: it.event?.createdAt },
            { it.idHex },
        )

    fun addStatus(note: AddressableNote) {
        // A newer version of a listed status can be blank (NIP-38 clears a status that way, and
        // a music status is cleared every time playback stops) or already expired: the note is
        // the same object, so it has to leave the list rather than be skipped as already there.
        if (note.isEmptyStatus() || note.event?.isExpired() == true) {
            removeStatus(note)
            return
        }

        // if it's already there, quick exit
        if (statuses.value.contains(note)) return

        statuses.update {
            (it + note).sortedWith(sortModel).toImmutableList()
        }
    }

    /**
     * Nothing to show: no event, or blank text with no plain status emoji. An emoji-only status
     * (Buzz writes `["emoji", "🌴"]` with blank content) is still a status.
     */
    private fun Note.isEmptyStatus(): Boolean {
        val event = event ?: return true
        return if (event is UserStatusEvent) event.isCleared() else event.content.isBlank()
    }

    fun removeStatus(deleteNote: AddressableNote) {
        // if it's not already there, quick exit
        if (!statuses.value.contains(deleteNote)) return

        statuses.update {
            (it - deleteNote).toImmutableList()
        }
    }

    fun removeExpired() {
        val hasExpired = statuses.value.any { it.event?.isExpired() == true }
        if (hasExpired) {
            statuses.update { list ->
                val filtered = list.filter { it.event?.isExpired() != true }
                if (filtered.size != list.size) {
                    filtered.toImmutableList()
                } else {
                    list
                }
            }
        }
    }
}
