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
package com.vitorpamplona.amethyst.commons.model.composer

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Dao
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlinx.coroutines.CancellationException

/**
 * @param userMentionsAsNames write a user mention as plain `@Name` text instead of a `nostr:`
 * reference — the form Buzz clients send and render (the cited user still lands in [pTags], which is
 * what notifies them). Buzz shows a `nostr:nprofile…` body verbatim.
 */
class NewMessageTagger(
    var message: String,
    var pTags: List<User>? = null,
    var eTags: List<Note>? = null,
    var dao: Dao,
    val userMentionsAsNames: Boolean = false,
) {
    val directMentions = mutableSetOf<HexKey>()
    val directMentionsNotes = mutableSetOf<Note>()
    val directMentionsUsers = mutableSetOf<User>()

    fun addUserToMentions(user: User) {
        directMentionsUsers.add(user)
        directMentions.add(user.pubkeyHex)
        pTags = if (pTags?.contains(user) == true) pTags else pTags?.plus(user) ?: listOf(user)
    }

    fun addNoteToReplyTos(note: Note) {
        directMentionsNotes.add(note)
        directMentions.add(note.idHex)

        note.author?.let { addUserToMentions(it) }
        eTags = if (eTags?.contains(note) == true) eTags else eTags?.plus(note) ?: listOf(note)
    }

    suspend fun run() {
        // adds all references to mentions and reply tos
        message.split('\n').forEach { paragraph: String ->
            paragraph.split(' ').forEach { word: String ->
                val results = parseDirtyWordForKey(word)

                when (val entity = results?.key?.entity) {
                    is NPub -> {
                        addUserToMentions(dao.getOrCreateUser(entity.hex))
                    }

                    is NProfile -> {
                        addUserToMentions(dao.getOrCreateUser(entity.hex))
                    }

                    is com.vitorpamplona.quartz.nip19Bech32.entities.NNote -> {
                        addNoteToReplyTos(dao.getOrCreateNote(entity.hex))
                    }

                    is NEvent -> {
                        addNoteToReplyTos(dao.getOrCreateNote(entity.hex))
                    }

                    is NEmbed -> {
                        addNoteToReplyTos(dao.getOrCreateNote(entity.event.id))
                    }

                    is NAddress -> {
                        val note = dao.getOrCreateAddressableNote(entity.address())
                        if (note != null) {
                            addNoteToReplyTos(note)
                        }
                    }

                    is NSec -> {}

                    is NRelay -> {}
                }
            }
        }

        // Tags the text in the correct order.
        message =
            message
                .split('\n')
                .map { paragraph: String ->
                    paragraph
                        .split(' ')
                        .map { word: String ->
                            val results = parseDirtyWordForKey(word)
                            when (val entity = results?.key?.entity) {
                                is NPub -> {
                                    userMention(dao.getOrCreateUser(entity.hex), results.restOfWord)
                                }

                                is NProfile -> {
                                    userMention(dao.getOrCreateUser(entity.hex), results.restOfWord)
                                }

                                is com.vitorpamplona.quartz.nip19Bech32.entities.NNote -> {
                                    getNostrAddress(dao.getOrCreateNote(entity.hex).toNEvent(), results.restOfWord)
                                }

                                is NEvent -> {
                                    getNostrAddress(dao.getOrCreateNote(entity.hex).toNEvent(), results.restOfWord)
                                }

                                is NAddress -> {
                                    val note = dao.getOrCreateAddressableNote(entity.address())
                                    if (note != null) {
                                        getNostrAddress(note.toNAddr(), results.restOfWord)
                                    } else {
                                        word
                                    }
                                }

                                is NEmbed -> {
                                    word
                                }

                                is NSec -> {
                                    word
                                }

                                is NRelay -> {
                                    word
                                }

                                else -> {
                                    word
                                }
                            }
                        }.joinToString(" ")
                }.joinToString("\n")
    }

    private fun userMention(
        user: User,
        restOfTheWord: String?,
    ): String {
        if (!userMentionsAsNames) return getNostrAddress(user.toNProfile(), restOfTheWord)
        val name = "@" + user.toBestDisplayName()
        return when {
            restOfTheWord.isNullOrEmpty() -> name
            restOfTheWord[0].isLetterOrDigit() -> "$name $restOfTheWord"
            else -> name + restOfTheWord
        }
    }

    fun getNostrAddress(
        bechAddress: String,
        restOfTheWord: String?,
    ): String =
        if (restOfTheWord.isNullOrEmpty()) {
            "nostr:$bechAddress"
        } else {
            if (Bech32.ALPHABET.contains(restOfTheWord.get(0), true)) {
                "nostr:$bechAddress $restOfTheWord"
            } else {
                "nostr:${bechAddress}$restOfTheWord"
            }
        }

    @Immutable data class DirtyKeyInfo(
        val key: Nip19Parser.ParseReturn,
        val restOfWord: String?,
    )

    fun parseDirtyWordForKey(mightBeAKey: String): DirtyKeyInfo? {
        var key = mightBeAKey
        if (key.startsWith("nostr:", true)) {
            key = key.substring("nostr:".length)
        }

        key = key.removePrefix("@")

        try {
            when {
                key.startsWith("nsec1", true) -> {
                    if (key.length < 63) {
                        return null
                    }

                    val keyB32 = key.substring(0, 63)
                    val restOfWord = key.substring(63)
                    // Converts to npub
                    val pubkey =
                        Nip19Parser.uriToRoute(Nip01Crypto.pubKeyCreate(keyB32.bechToBytes()).toNpub()) ?: return null

                    return DirtyKeyInfo(pubkey, restOfWord.ifEmpty { null })
                }

                key.startsWith("npub1", true) -> {
                    if (key.length < 63) {
                        return null
                    }

                    val keyB32 = key.substring(0, 63)
                    val restOfWord = key.substring(63)

                    val pubkey = Nip19Parser.uriToRoute(keyB32) ?: return null

                    return DirtyKeyInfo(pubkey, restOfWord.ifEmpty { null })
                }

                key.startsWith("note1", true) -> {
                    if (key.length < 63) {
                        return null
                    }

                    val keyB32 = key.substring(0, 63)
                    val restOfWord = key.substring(63)

                    val noteId = Nip19Parser.uriToRoute(keyB32) ?: return null

                    return DirtyKeyInfo(noteId, restOfWord.ifEmpty { null })
                }

                key.startsWith("nprofile", true) -> {
                    val pubkeyRelay = Nip19Parser.uriToRoute(key) ?: return null

                    return DirtyKeyInfo(pubkeyRelay, pubkeyRelay.additionalChars)
                }

                key.startsWith("nevent1", true) -> {
                    val noteRelayId = Nip19Parser.uriToRoute(key) ?: return null

                    return DirtyKeyInfo(noteRelayId, noteRelayId.additionalChars)
                }

                key.startsWith("naddr1", true) -> {
                    val address = Nip19Parser.uriToRoute(key) ?: return null

                    return DirtyKeyInfo(
                        address,
                        address.additionalChars,
                    ) // no way to know when they address ends and dirt begins
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
        }

        return null
    }
}
