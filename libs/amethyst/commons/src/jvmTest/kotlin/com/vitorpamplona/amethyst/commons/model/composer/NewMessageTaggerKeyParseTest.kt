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

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Dao
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache.getOrCreateAddressableNoteInternal
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class NewMessageTaggerKeyParseTest {
    val dao: Dao =
        object : Dao {
            override fun getOrCreateUser(pubkey: String): User = User(pubkey) { addr -> getOrCreateAddressableNoteInternal(addr) }

            override fun getOrCreateNote(hex: String) = Note(hex)

            override fun getOrCreateAddressableNote(address: Address) = AddressableNote(address)
        }

    @Test
    fun keyParseTestNote() {
        val result =
            NewMessageTagger(message = "", dao = dao)
                .parseDirtyWordForKey("note1z5e2m0smx6d7e2d0zaq8d3rnd7httm6j0uf8tf90yqqjrs842czshwtkmn")
        assertTrue(result?.key?.entity is NNote)
        assertEquals(
            "1532adbe1b369beca9af174076c4736faeb5ef527f1275a4af200121c0f55605",
            (result?.key?.entity as? NNote)?.hex,
        )
        assertEquals(null, result?.restOfWord)
    }

    @Test
    fun keyParseTestPub() {
        val result =
            NewMessageTagger(message = "", dao = dao)
                .parseDirtyWordForKey("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z")
        assertTrue(result?.key?.entity is NPub)
        assertEquals(
            "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
            (result?.key?.entity as? NPub)?.hex,
        )
        assertEquals(null, result?.restOfWord)
    }

    @Test
    fun keyParseTestNoteWithExtraChars() {
        val result =
            NewMessageTagger(message = "", dao = dao)
                .parseDirtyWordForKey("note1z5e2m0smx6d7e2d0zaq8d3rnd7httm6j0uf8tf90yqqjrs842czshwtkmn,")
        assertTrue(result?.key?.entity is NNote)
        assertEquals(
            "1532adbe1b369beca9af174076c4736faeb5ef527f1275a4af200121c0f55605",
            (result?.key?.entity as? NNote)?.hex,
        )
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun keyParseTestPubWithExtraChars() {
        val result =
            NewMessageTagger(message = "", dao = dao)
                .parseDirtyWordForKey("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z,")
        assertTrue(result?.key?.entity is NPub)
        assertEquals(
            "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
            (result?.key?.entity as? NPub)?.hex,
        )
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun keyParseTestNoteWithExtraCharsAndAt() {
        val result =
            NewMessageTagger(message = "", dao = dao)
                .parseDirtyWordForKey("@note1z5e2m0smx6d7e2d0zaq8d3rnd7httm6j0uf8tf90yqqjrs842czshwtkmn,")
        assertTrue(result?.key?.entity is NNote)
        assertEquals(
            "1532adbe1b369beca9af174076c4736faeb5ef527f1275a4af200121c0f55605",
            (result?.key?.entity as? NNote)?.hex,
        )
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun keyParseTestPubWithExtraCharsAndAt() {
        val result =
            NewMessageTagger(message = "", dao = dao)
                .parseDirtyWordForKey("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z,")
        assertTrue(result?.key?.entity is NPub)
        assertEquals(
            "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
            (result?.key?.entity as? NPub)?.hex,
        )
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun keyParseTestNoteWithExtraCharsAndNostrPrefix() {
        val result =
            NewMessageTagger(message = "", dao = dao)
                .parseDirtyWordForKey(
                    "nostr:note1z5e2m0smx6d7e2d0zaq8d3rnd7httm6j0uf8tf90yqqjrs842czshwtkmn,",
                )
        assertTrue(result?.key?.entity is NNote)
        assertEquals(
            "1532adbe1b369beca9af174076c4736faeb5ef527f1275a4af200121c0f55605",
            (result?.key?.entity as? NNote)?.hex,
        )
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun keyParseTestPubWithExtraCharsAndNostrPrefix() {
        val result =
            NewMessageTagger(message = "", dao = dao)
                .parseDirtyWordForKey(
                    "nostr:npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z,",
                )
        assertTrue(result?.key?.entity is NPub)
        assertEquals(
            "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
            (result?.key?.entity as? NPub)?.hex,
        )
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun keyParseTestUppercaseNoteWithExtraCharsAndNostrPrefix() {
        val result =
            NewMessageTagger(message = "", dao = dao)
                .parseDirtyWordForKey(
                    "Nostr:note1z5e2m0smx6d7e2d0zaq8d3rnd7httm6j0uf8tf90yqqjrs842czshwtkmn,",
                )
        assertTrue(result?.key?.entity is NNote)
        assertEquals(
            "1532adbe1b369beca9af174076c4736faeb5ef527f1275a4af200121c0f55605",
            (result?.key?.entity as? NNote)?.hex,
        )
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun keyParseTestUppercasePubWithExtraCharsAndNostrPrefix() {
        val result =
            NewMessageTagger(message = "", dao = dao)
                .parseDirtyWordForKey(
                    "nOstr:npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z,",
                )
        assertTrue(result?.key?.entity is NPub)
        assertEquals(
            "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
            (result?.key?.entity as? NPub)?.hex,
        )
        assertEquals(",", result?.restOfWord)
    }

    @Test
    fun userMentionsAsNamesWritesPlainAtNameAndStillTags() =
        runBlocking {
            val npub = "npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"
            val hex = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
            val user = dao.getOrCreateUser(hex)

            val tagger = NewMessageTagger(message = "hi @$npub, there", dao = dao, userMentionsAsNames = true)
            tagger.run()

            assertEquals("hi @${user.toBestDisplayName()}, there", tagger.message)
            assertEquals(listOf(hex), tagger.pTags?.map { it.pubkeyHex })
        }

    @Test
    fun defaultStillWritesNostrUri() =
        runBlocking {
            val npub = "npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"
            val tagger = NewMessageTagger(message = "hi @$npub", dao = dao)
            tagger.run()

            assertTrue(tagger.message.startsWith("hi nostr:nprofile1"))
        }

    @Test
    fun possessiveAtMentionBecomesNostrUri() =
        runBlocking {
            val npub = "npub19ma2w9dmk3kat0nt0k5dwuqzvmg3va9ezwup0zkakhpwv0vcwvcsg8axkl"
            val tagger = NewMessageTagger(message = "@$npub's ERP screens look good", dao = dao)
            tagger.run()

            val nprofile = tagger.pTags!!.single().toNProfile()
            assertEquals("nostr:$nprofile's ERP screens look good", tagger.message)
        }
}
