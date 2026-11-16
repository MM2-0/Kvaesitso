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
package com.vitorpamplona.amethyst.commons.service.spellcheck

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SpellCheckServiceTest {
    private class FakeChecker(
        val known: Set<String>,
    ) : SpellChecker {
        val asked = mutableListOf<String>()

        override val name = "fake"

        override fun handles(word: String) = word.all { it.code < 0x250 }

        override fun isCorrect(word: String): Boolean {
            asked += word
            return word.lowercase() in known
        }

        override fun suggest(word: String) = listOf(word, "the", "tea", "ten", "tee", "ted", "tech")
    }

    private class MemoryDictionary : UserDictionary {
        var saved: Set<String> = emptySet()

        override fun load() = saved

        override fun save(words: Set<String>) {
            saved = words
        }
    }

    @Test
    fun findsNothingWithoutAnEngine() =
        runTest {
            val service = SpellCheckService(StandardTestDispatcher(testScheduler))
            assertEquals(emptyList(), service.findMisspellings("teh cat"))
        }

    @Test
    fun reportsMisspelledWordsWithOffsets() =
        runTest {
            val service = SpellCheckService(StandardTestDispatcher(testScheduler))
            service.setEngine(FakeChecker(setOf("the", "cat", "sat")))

            assertEquals(listOf(Misspelling(0, 3, "teh"), Misspelling(8, 11, "sta")), service.findMisspellings("teh cat sta"))
        }

    @Test
    fun acceptsNostrWordsAndWordsTheEngineCannotJudge() =
        runTest {
            val service = SpellCheckService(StandardTestDispatcher(testScheduler))
            service.setEngine(FakeChecker(setOf("i")))

            assertEquals(emptyList(), service.findMisspellings("Nostr zaps привет"))
        }

    @Test
    fun normalizesTypographicApostrophes() =
        runTest {
            val service = SpellCheckService(StandardTestDispatcher(testScheduler))
            val checker = FakeChecker(setOf("don't"))
            service.setEngine(checker)

            assertEquals(emptyList(), service.findMisspellings("don’t"))
            assertEquals(listOf("don't"), checker.asked)
        }

    @Test
    fun asksTheEngineOncePerWord() =
        runTest {
            val service = SpellCheckService(StandardTestDispatcher(testScheduler))
            val checker = FakeChecker(setOf("cat"))
            service.setEngine(checker)

            service.findMisspellings("cat cat")
            service.findMisspellings("cat cat cat")

            assertEquals(listOf("cat"), checker.asked)
        }

    @Test
    fun learnedWordsStopBeingFlaggedAndArePersisted() =
        runTest {
            val dictionary = MemoryDictionary()
            val service = SpellCheckService(StandardTestDispatcher(testScheduler), dictionary)
            service.setEngine(FakeChecker(emptySet()))
            assertEquals(1, service.findMisspellings("Pamplona").size)

            val before = service.revision.value
            service.addToDictionary("Pamplona")

            assertEquals(emptyList(), service.findMisspellings("pamplona Pamplona"))
            assertEquals(setOf("pamplona"), dictionary.saved)
            assertTrue(service.revision.value > before)
        }

    @Test
    fun learningAWordWithACurlyApostropheSticks() =
        runTest {
            val dictionary = MemoryDictionary()
            val service = SpellCheckService(StandardTestDispatcher(testScheduler), dictionary)
            service.setEngine(FakeChecker(emptySet()))

            service.addToDictionary("y\u2019all")

            assertEquals(emptyList(), service.findMisspellings("y\u2019all y'all"))
            assertEquals(setOf("y'all"), dictionary.saved)
        }

    @Test
    fun learningAWordKeepsOtherVerdictsCached() =
        runTest {
            val service = SpellCheckService(StandardTestDispatcher(testScheduler))
            val checker = FakeChecker(setOf("cat"))
            service.setEngine(checker)
            service.findMisspellings("cat dgo")

            service.addToDictionary("dgo")
            assertEquals(emptyList(), service.findMisspellings("cat dgo"))

            assertEquals(listOf("cat", "dgo"), checker.asked)
        }

    @Test
    fun suggestionsDropTheWordItselfAndAreCapped() =
        runTest {
            val service = SpellCheckService(StandardTestDispatcher(testScheduler))
            service.setEngine(FakeChecker(emptySet()))

            assertEquals(listOf("the", "tea", "ten", "tee", "ted"), service.suggestions("teh"))
        }

    @Test
    fun switchingEnginesForgetsOldVerdicts() =
        runTest {
            val service = SpellCheckService(StandardTestDispatcher(testScheduler))
            service.setEngine(FakeChecker(emptySet()))
            assertEquals(1, service.findMisspellings("colour").size)

            service.setEngine(FakeChecker(setOf("colour")))
            assertEquals(emptyList(), service.findMisspellings("colour"))
            assertEquals("fake", service.activeEngine.value)
        }
}
