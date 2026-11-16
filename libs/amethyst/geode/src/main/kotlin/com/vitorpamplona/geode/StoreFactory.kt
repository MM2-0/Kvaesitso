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
import java.lang.reflect.InvocationTargetException
import kotlin.io.path.Path

/**
 * Builds the relay's [IEventStore] from the parsed [StaticConfig].
 *
 * `[database].backend` selects the implementation (see
 * [StaticConfig.DatabaseSection.backend]); everything else the store needs
 * — the advertised relay URL for NIP-62 scoping, the relay-tuned
 * [relayIndexingStrategy], the SQLite deployment pragmas — is threaded
 * through here so both the `serve` path and the `import`/`export` verbs open
 * the *same* store from the *same* config.
 *
 * Two backends ship in quartz and are named by keyword; anything else is
 * treated as a fully-qualified class name and loaded reflectively, which is
 * what makes "pick any `IEventStore` implementation" true rather than a
 * fixed menu of two.
 */
object StoreFactory {
    /** `[database].backend` values that select the SQLite [EventStore] (the default). */
    val SQLITE_BACKEND_KEYWORDS = setOf("sqlite", "sqlite3", "")

    /** `[database].backend` values that select the filesystem [FsEventStore]. */
    val FS_BACKEND_KEYWORDS = setOf("fs", "file", "files", "filesystem")

    /**
     * Open the configured store.
     *
     * @param config parsed operator config (owns `[database]`, `[negentropy]`).
     * @param relay advertised relay URL, or `null` for an unscoped store
     *   (the `import`/`export` verbs pass `null`).
     * @param fullTextSearch whether to build/maintain the NIP-50 index —
     *   already resolved from `[options].full_text_search` and `--no-search`.
     * @param dbOverride the `--db` CLI flag when present; wins over
     *   `[database].file`. For SQLite it names the db file (`null` →
     *   in-memory); for `fs` it names the root directory.
     * @param extraPragmas SQLite-only deployment pragmas (mmap/temp_store);
     *   ignored by non-SQLite backends.
     */
    fun open(
        config: StaticConfig,
        relay: NormalizedRelayUrl?,
        fullTextSearch: Boolean,
        dbOverride: String? = null,
        extraPragmas: List<String> = emptyList(),
    ): IEventStore {
        val strategy = relayIndexingStrategy(fullTextSearch, config.negentropy.live_index)
        val key =
            config.database.backend
                .trim()
                .lowercase()
        return when {
            key in SQLITE_BACKEND_KEYWORDS ->
                EventStore(
                    // `--db` wins; else the configured file unless the operator
                    // asked for an in-memory db (events vanish on restart).
                    dbName = dbOverride ?: config.database.file?.takeUnless { config.database.in_memory },
                    relay = relay,
                    indexStrategy = strategy,
                    numReaders = config.database.readers ?: 4,
                    extraPragmas = extraPragmas,
                )

            key in FS_BACKEND_KEYWORDS -> {
                // `in_memory` is a SQLite concept — the FS store always needs
                // a directory. `--db` overrides the configured path.
                val root =
                    dbOverride ?: config.database.file
                        ?: throw IllegalArgumentException(
                            "[database].backend = \"fs\" needs a directory: set [database].file " +
                                "(or pass --db <dir>). The filesystem store cannot run in-memory.",
                        )
                FsEventStore(
                    root = Path(root),
                    indexingStrategy = strategy,
                    relay = relay,
                )
            }

            else -> loadCustom(config.database.backend.trim(), relay, strategy)
        }
    }

    /**
     * Reflectively instantiate a custom [IEventStore] named by its
     * fully-qualified class name. The class must implement `IEventStore`
     * and expose one of these public constructors, tried most-specific
     * first:
     *
     *   1. `(NormalizedRelayUrl?, IndexingStrategy)`
     *   2. `(NormalizedRelayUrl?)`
     *   3. `()`
     *
     * Enough to hand a custom store the same relay scoping and index
     * strategy the built-in backends receive, while still accepting a
     * dependency-free no-arg store.
     */
    private fun loadCustom(
        className: String,
        relay: NormalizedRelayUrl?,
        strategy: IndexingStrategy,
    ): IEventStore {
        val clazz =
            try {
                Class.forName(className)
            } catch (e: ClassNotFoundException) {
                throw IllegalArgumentException(
                    "[database].backend = \"$className\" is neither a known backend " +
                        "(\"sqlite\", \"fs\") nor a class on the classpath. Give the fully-qualified " +
                        "name of an ${IEventStore::class.simpleName} implementation.",
                    e,
                )
            }
        require(IEventStore::class.java.isAssignableFrom(clazz)) {
            "[database].backend class $className does not implement ${IEventStore::class.qualifiedName}."
        }

        newInstance(clazz, arrayOf(NormalizedRelayUrl::class.java, IndexingStrategy::class.java), arrayOf(relay, strategy))
            ?.let { return it }
        newInstance(clazz, arrayOf(NormalizedRelayUrl::class.java), arrayOf(relay))
            ?.let { return it }
        newInstance(clazz, emptyArray(), emptyArray())
            ?.let { return it }

        throw IllegalArgumentException(
            "$className has no supported public constructor. Provide one of: " +
                "(NormalizedRelayUrl?, IndexingStrategy), (NormalizedRelayUrl?), or ().",
        )
    }

    /**
     * Invoke the `paramTypes` constructor with `args`, or return `null` if
     * the class has no such (public) constructor. A throw from inside the
     * constructor is unwrapped and rethrown — the store failed to build,
     * which is not a signal to fall back to a different constructor shape.
     */
    private fun newInstance(
        clazz: Class<*>,
        paramTypes: Array<Class<*>>,
        args: Array<Any?>,
    ): IEventStore? =
        try {
            clazz.getConstructor(*paramTypes).newInstance(*args) as IEventStore
        } catch (e: NoSuchMethodException) {
            null
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        }
}
