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
package com.vitorpamplona.nestsclient.moq.lite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoqLitePathTest {
    @Test
    fun normalize_strips_leading_trailing_and_collapses_duplicates() {
        assertEquals("foo/bar", MoqLitePath.normalize("/foo/bar/"))
        assertEquals("foo/bar", MoqLitePath.normalize("foo//bar"))
        assertEquals("foo/bar", MoqLitePath.normalize("///foo///bar///"))
        assertEquals("foo", MoqLitePath.normalize("/foo/"))
        assertEquals("", MoqLitePath.normalize(""))
        assertEquals("", MoqLitePath.normalize("/"))
        assertEquals("", MoqLitePath.normalize("/////"))
    }

    @Test
    fun normalize_preserves_already_canonical_paths() {
        assertEquals("foo/bar", MoqLitePath.normalize("foo/bar"))
        assertEquals(
            "nests/30312:abc:room/${"f".repeat(64)}",
            MoqLitePath.normalize("nests/30312:abc:room/${"f".repeat(64)}"),
        )
    }

    @Test
    fun join_handles_empty_sides() {
        assertEquals("foo", MoqLitePath.join("", "foo"))
        assertEquals("foo", MoqLitePath.join("foo", ""))
        assertEquals("", MoqLitePath.join("", ""))
    }

    @Test
    fun join_inserts_exactly_one_slash() {
        assertEquals("a/b", MoqLitePath.join("a", "b"))
        // Sides are normalized first — the join can't produce `//`.
        assertEquals("a/b", MoqLitePath.join("a/", "/b"))
        assertEquals("nests/30312:abc:room/pubkey", MoqLitePath.join("nests/30312:abc:room", "pubkey"))
    }

    @Test
    fun stripPrefix_is_path_component_aware() {
        assertEquals("baz", MoqLitePath.stripPrefix("foo/bar", "foo/bar/baz"))
        assertEquals("", MoqLitePath.stripPrefix("foo/bar", "foo/bar"))
        // Prefix must end on a path-component boundary.
        assertNull(MoqLitePath.stripPrefix("foo", "foobar"))
        assertNull(MoqLitePath.stripPrefix("foo/bar", "foo/barbaz"))
        // Disjoint paths return null.
        assertNull(MoqLitePath.stripPrefix("foo/bar", "baz"))
    }

    @Test
    fun stripPrefix_normalizes_both_sides() {
        assertEquals("baz", MoqLitePath.stripPrefix("/foo/bar/", "/foo//bar/baz/"))
    }

    @Test
    fun stripPrefix_with_empty_prefix_returns_normalized_path() {
        assertEquals("foo/bar", MoqLitePath.stripPrefix("", "/foo//bar/"))
    }
}
