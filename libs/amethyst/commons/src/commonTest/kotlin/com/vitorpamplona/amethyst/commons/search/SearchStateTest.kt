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
package com.vitorpamplona.amethyst.commons.search

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The state both front ends now share, at the points where their two copies used to disagree.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchStateTest {
    @Test
    fun aSeededBoxIsAlreadyParsedBeforeAnythingTicks() =
        runTest {
            // A screen seeds the box on the way in, and the search opens showing chips. If the
            // parse waited for a debounce window the first frame would draw the raw tokens.
            val s = SearchState(backgroundScope, "kind:article bitcoin")
            runCurrent()
            assertEquals(listOf(30023), s.current.value.query.kinds)
            assertEquals("bitcoin", s.current.value.query.text)
            assertEquals(listOf(30023), s.debounced.value.query.kinds)
        }

    @Test
    fun theTextAndItsParseAlwaysAgree() =
        runTest {
            // The point of carrying them as one value: a collector cannot pair one keystroke's
            // characters with another's parse.
            val s = SearchState(backgroundScope)
            s.updateText("#nostr")
            runCurrent()
            assertEquals("#nostr", s.current.value.text)
            assertEquals(listOf("nostr"), s.current.value.query.hashtags)
        }

    @Test
    fun relaysAreAskedLaterThanTheCacheIs() =
        runTest {
            val s = SearchState(backgroundScope)
            s.updateText("bitcoin")
            advanceTimeBy(SearchState.LOCAL_DEBOUNCE_MS + 1)
            assertEquals("bitcoin", s.debounced.value.text, "the cache scan runs on the short window")
            assertEquals("", s.debouncedForRelays.value.text, "a REQ does not")
            advanceTimeBy(SearchState.RELAY_DEBOUNCE_MS)
            assertEquals("bitcoin", s.debouncedForRelays.value.text)
        }

    @Test
    fun aButtonPressBecomesTextTheReaderCouldHaveTyped() =
        runTest {
            // The rule the whole token language rests on: a control that adds a filter writes it
            // into the box, so a chip is always something editable rather than hidden state.
            val s = SearchState(backgroundScope)
            s.updateText("bitcoin")
            runCurrent()
            s.edit { it.copy(kinds = kotlinx.collections.immutable.persistentListOf(30023)) }
            runCurrent()
            assertEquals("kind:article bitcoin", s.text.value)
            assertEquals(listOf(30023), s.current.value.query.kinds)
        }

    @Test
    fun aKindPinsTheScopeToNotesAndReleasesItOnDeletion() =
        runTest {
            // Pinned rather than assigned: dropping the chip must give back the scope the reader
            // chose, not leave them stuck in Notes.
            val s = SearchState(backgroundScope)
            s.updateScope(SearchScope.PEOPLE)
            s.updateText("kind:article")
            runCurrent()
            assertTrue(s.scopePinnedToNotes.value)
            assertEquals(SearchScope.NOTES, s.scope.value)
            assertEquals(SearchScope.PEOPLE, s.pickedScope.value)

            s.updateText("")
            runCurrent()
            assertFalse(s.scopePinnedToNotes.value)
            assertEquals(SearchScope.PEOPLE, s.scope.value)
        }

    @Test
    fun aBareKindWindowIsAQueryThatAsksNothing() =
        runTest {
            // "every recent article" is an unbounded REQ, so the builder refuses it. Without
            // saying so, a screen that seeds its kind opens holding a chip and showing nothing.
            val s = SearchState(backgroundScope)
            s.updateText("kind:article")
            runCurrent()
            assertTrue(s.asksNothing.value)

            s.updateText("kind:article bitcoin")
            runCurrent()
            assertFalse(s.asksNothing.value)

            s.updateText("")
            runCurrent()
            assertFalse(s.asksNothing.value, "an empty box is not asking anything wrong")
        }

    @Test
    fun nothingIsSaidToBeMissingUntilTheSearchHasSettled() =
        runTest {
            val s = SearchState(backgroundScope)
            s.updateText("bitcoin")
            runCurrent()
            assertFalse(s.settled.value)
            advanceTimeBy(SearchState.DEFAULT_SETTLE_MS + 1)
            assertTrue(s.settled.value)

            // And a further keystroke takes the claim back.
            s.updateText("bitcoins")
            runCurrent()
            assertFalse(s.settled.value)
        }

    @Test
    fun clearingPutsEveryControlBackWhereItStarted() =
        runTest {
            val s = SearchState(backgroundScope, "kind:article")
            s.updateScope(SearchScope.PEOPLE)
            s.updateFollowsOnly(true)
            s.updateEventSortOrder(SearchSortOrder.OLDEST)
            runCurrent()

            s.clear()
            runCurrent()
            assertEquals("", s.text.value)
            assertEquals(SearchScope.ALL, s.scope.value)
            assertFalse(s.followsOnly.value)
            assertEquals(SearchSortOrder.EVENT_DEFAULT, s.eventSortOrder.value)
        }

    @Test
    fun aKeystrokeIsParsedOnceAndSharedByEveryWindow() =
        runTest {
            // The debounced views are debounces of the parse, not of the text. Parsing per window
            // was three parses for one answer, and this is the cheapest way to say which it is.
            val s = SearchState(backgroundScope)
            s.updateText("kind:article bitcoin")
            advanceTimeBy(SearchState.RELAY_DEBOUNCE_MS + 1)
            assertSame(s.current.value, s.debounced.value)
            assertSame(s.current.value, s.debouncedForRelays.value)
        }
}
