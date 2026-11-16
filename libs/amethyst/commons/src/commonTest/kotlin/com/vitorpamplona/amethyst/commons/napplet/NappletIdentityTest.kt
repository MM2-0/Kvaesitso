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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.snapshot.NappletSnapshotEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NappletIdentityTest {
    private val hash = "ab".repeat(32)

    @Test
    fun storageSeparatesManifestKindsAndSnapshotIdsEvenWithIdenticalArtifacts() {
        val identity = NappletIdentity("aa".repeat(32), "", hash)
        val root = identity.copy(manifestKind = RootNappletEvent.KIND)
        val named = identity.copy(manifestKind = NamedNappletEvent.KIND)
        val first = identity.copy(manifestKind = NappletSnapshotEvent.KIND, manifestEventId = "01".repeat(32))
        val second = identity.copy(manifestKind = NappletSnapshotEvent.KIND, manifestEventId = "02".repeat(32))
        assertEquals(4, setOf(root.storageCoordinate, named.storageCoordinate, first.storageCoordinate, second.storageCoordinate).size)
        assertTrue(root.copy(authorPubKey = "bb".repeat(32)).storageCoordinate != root.storageCoordinate)
        assertTrue(root.copy(aggregateHash = "cd".repeat(32)).storageCoordinate != root.storageCoordinate)
        assertTrue(root.copy(instanceId = "one").instanceStorageCoordinate != root.copy(instanceId = "two").instanceStorageCoordinate)
    }

    @Test
    fun storageKeysKeepTheirPersistedFormat() {
        // These strings name napplets' saved storage on disk: changing them orphans users' data.
        val author = "aa".repeat(32)
        assertEquals("15129:$author:$hash", NappletIdentity(author, "", hash, manifestKind = RootNappletEvent.KIND).storageCoordinate)
        assertEquals("35129:$author:feed:$hash", NappletIdentity(author, "feed", hash, manifestKind = NamedNappletEvent.KIND).storageCoordinate)
        val id = "01".repeat(32)
        assertEquals("5129:$id:$hash", NappletIdentity(author, "", hash, manifestKind = NappletSnapshotEvent.KIND, manifestEventId = id).storageCoordinate)
        assertEquals("$author:feed:$hash", NappletIdentity(author, "feed", hash).storageCoordinate)
        assertEquals("$author:feed:$hash:instance:one", NappletIdentity(author, "feed", hash, instanceId = "one").instanceStorageCoordinate)
    }
}
