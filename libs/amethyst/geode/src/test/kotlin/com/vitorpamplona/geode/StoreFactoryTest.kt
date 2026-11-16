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
package com.vitorpamplona.geode

import com.vitorpamplona.geode.config.StaticConfig
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.fs.FsEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.IndexingStrategy
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A stand-in "any other IEventStore" — the escape hatch's target. It only
 * needs to implement [IEventStore] and expose the
 * `(NormalizedRelayUrl?, IndexingStrategy)` constructor [StoreFactory] looks
 * for; delegation to an in-memory [EventStore] keeps it a couple of lines.
 */
class FakeCustomStore(
    relay: NormalizedRelayUrl?,
    strategy: IndexingStrategy,
) : IEventStore by EventStore(dbName = null, relay = relay, indexStrategy = strategy)

class StoreFactoryTest {
    private fun config(toml: String) = StaticConfig.fromToml(toml)

    @Test
    fun defaultBackendIsSqlite() {
        val store = StoreFactory.open(config(""), relay = null, fullTextSearch = true)
        store.use { assertTrue(it is EventStore, "default backend should be the SQLite EventStore") }
    }

    @Test
    fun sqliteKeywordSelectsEventStore() {
        val store = StoreFactory.open(config("[database]\nbackend = \"SQLite\""), relay = null, fullTextSearch = true)
        store.use { assertTrue(it is EventStore) }
    }

    @Test
    fun fsBackendSelectsFsEventStore() {
        val dir = Files.createTempDirectory("geode-fs-store-").toFile()
        try {
            val toml = "[database]\nbackend = \"fs\"\nfile = \"${dir.absolutePath}\""
            val store = StoreFactory.open(config(toml), relay = null, fullTextSearch = true)
            store.use { assertTrue(it is FsEventStore, "fs backend should be the filesystem FsEventStore") }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun fsBackendViaDbOverride() {
        val dir = Files.createTempDirectory("geode-fs-store-").toFile()
        try {
            // No `file` in the config — the --db override supplies the root.
            val store =
                StoreFactory.open(
                    config("[database]\nbackend = \"fs\""),
                    relay = null,
                    fullTextSearch = true,
                    dbOverride = dir.absolutePath,
                )
            store.use { assertTrue(it is FsEventStore) }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun fsBackendWithoutPathFailsLoud() {
        assertFailsWith<IllegalArgumentException> {
            StoreFactory.open(config("[database]\nbackend = \"fs\""), relay = null, fullTextSearch = true)
        }
    }

    @Test
    fun customClassNameIsLoadedReflectively() {
        val toml = "[database]\nbackend = \"com.vitorpamplona.geode.FakeCustomStore\""
        val store = StoreFactory.open(config(toml), relay = null, fullTextSearch = true)
        store.use { assertTrue(it is FakeCustomStore, "a FQCN backend should be instantiated reflectively") }
    }

    @Test
    fun unknownClassNameFailsLoud() {
        assertFailsWith<IllegalArgumentException> {
            StoreFactory.open(
                config("[database]\nbackend = \"com.example.NoSuchStore\""),
                relay = null,
                fullTextSearch = true,
            )
        }
    }

    @Test
    fun classThatIsNotAnEventStoreFailsLoud() {
        // java.lang.String resolves but does not implement IEventStore.
        assertFailsWith<IllegalArgumentException> {
            StoreFactory.open(config("[database]\nbackend = \"java.lang.String\""), relay = null, fullTextSearch = true)
        }
    }
}
