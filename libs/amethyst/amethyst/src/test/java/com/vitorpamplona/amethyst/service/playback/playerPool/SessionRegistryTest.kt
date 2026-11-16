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
package com.vitorpamplona.amethyst.service.playback.playerPool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SessionRegistryTest {
    private val dropped = mutableListOf<String>()

    private fun registry(maxIdle: Int) = SessionRegistry<String>(maxIdle) { dropped.add(it) }

    @Test
    fun evictionDropsTheOldestExactlyOnce() {
        val registry = registry(maxIdle = 1)
        registry.register("a", "A")
        registry.register("b", "B")
        assertEquals(listOf("A"), dropped)
    }

    @Test
    fun playingSessionSurvivesEviction() {
        val registry = registry(maxIdle = 1)
        registry.register("a", "A")
        registry.setPlaying("a", true)
        registry.register("b", "B")
        assertEquals(emptyList<String>(), dropped)
    }

    // The replacement guard. setPlaying(false) re-puts the SAME entry, which fires
    // entryRemoved(newValue === oldValue). Treating that as a drop would retire the
    // session we are trying to keep.
    @Test
    fun pauseDoesNotDropTheSessionItIsKeeping() {
        val registry = registry(maxIdle = 2)
        registry.register("a", "A")
        registry.setPlaying("a", true)
        registry.setPlaying("a", false)
        assertEquals(emptyList<String>(), dropped)
    }

    @Test
    fun pausedSessionBecomesEvictableAgain() {
        val registry = registry(maxIdle = 1)
        registry.register("a", "A")
        registry.setPlaying("a", true)
        registry.register("b", "B") // A survives, but leaves idle
        registry.setPlaying("a", false) // A re-enters idle and displaces B
        assertEquals(listOf("B"), dropped)
    }

    // Leak path 2: an explicit release must drop even while playing.
    @Test
    fun explicitDropOfPlayingSessionDropsExactlyOnce() {
        val registry = registry(maxIdle = 2)
        registry.register("a", "A")
        registry.setPlaying("a", true)
        registry.drop("a")
        assertEquals(listOf("A"), dropped)
    }

    // Leak path 2, the exact case the old cache.remove(id) missed: already evicted
    // from idle while playing, so there is no cache entry left to trigger the callback.
    @Test
    fun explicitDropAfterEvictionWhilePlayingStillDrops() {
        val registry = registry(maxIdle = 1)
        registry.register("a", "A")
        registry.setPlaying("a", true)
        registry.register("b", "B")
        registry.drop("a")
        assertEquals(listOf("A"), dropped)
    }

    @Test
    fun dropAllDropsEveryEntryExactlyOnce() {
        val registry = registry(maxIdle = 3)
        registry.register("a", "A")
        registry.register("b", "B")
        registry.register("c", "C")
        registry.setPlaying("b", true)
        registry.dropAll()
        assertEquals(3, dropped.size)
        assertEquals(setOf("A", "B", "C"), dropped.toSet())
    }

    @Test
    fun dropAllIncludesSessionEvictedWhilePlaying() {
        val registry = registry(maxIdle = 1)
        registry.register("a", "A")
        registry.setPlaying("a", true)
        registry.register("b", "B")
        registry.dropAll()
        assertEquals(2, dropped.size)
        assertEquals(setOf("A", "B"), dropped.toSet())
    }

    @Test
    fun getFindsPlayingAndIdleEntries() {
        val registry = registry(maxIdle = 2)
        registry.register("a", "A")
        registry.register("b", "B")
        registry.setPlaying("a", true)
        assertSame("A", registry.get("a"))
        assertSame("B", registry.get("b"))
        assertNull(registry.get("missing"))
    }

    @Test
    fun droppingUnknownIdIsANoOp() {
        val registry = registry(maxIdle = 2)
        assertNull(registry.drop("missing"))
        assertEquals(emptyList<String>(), dropped)
    }

    @Test
    fun enumerationSeesIdleAndPlayingSeparately() {
        val registry = registry(maxIdle = 2)
        registry.register("a", "A")
        registry.register("b", "B")
        registry.setPlaying("a", true)
        assertEquals(setOf("A", "B"), registry.idleSnapshot().toSet())
        assertEquals(listOf("A"), registry.playingEntries())
    }

    // The replacement guard uses !== (identity, not equality). This test pins that distinction:
    // replacing an entry with an equal-but-not-identical instance must drop the old one.
    // Changing !== to != would break this test, even though all existing tests would pass.
    @Test
    fun replacementWithEqualButDifferentInstanceDropsOldEntry() {
        val registry = registry(maxIdle = 2)
        val original = "A"
        registry.register("a", original)

        // Create a new String instance that is equal to but not identical to the original.
        // String literals are interned, so we must construct it explicitly.
        val replacement = buildString { append("A") }
        assertEquals("Equal values", original, replacement)
        assertNotSame("Not the same instance", original, replacement)

        // Registering the replacement should drop the original.
        registry.register("a", replacement)
        assertEquals(listOf(original), dropped)
    }
}
