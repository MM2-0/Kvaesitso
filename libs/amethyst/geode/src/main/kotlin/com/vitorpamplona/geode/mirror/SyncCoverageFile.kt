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
package com.vitorpamplona.geode.mirror

import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.SyncCoverage
import com.vitorpamplona.quartz.utils.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * File persistence for [SyncCoverage], so the mirror's catch-up resumes
 * across restarts instead of re-syncing each upstream's whole backfill
 * window — which, for an upstream without NIP-77, is a full re-download
 * every boot.
 *
 * Same shape as the admin state file: JSON next to the event database,
 * written via a temp file and an atomic move so a reader never sees a half
 * map. A daemon timer flushes changed state so progress survives a hard
 * kill; [close] flushes the rest. A corrupt file starts fresh — the cost of
 * losing it is one re-sync, the cost of refusing to start is the relay.
 */
class SyncCoverageFile(
    private val file: File,
    flushSeconds: Long = DEFAULT_FLUSH_SECONDS,
) : AutoCloseable {
    @Volatile private var dirty = false

    val coverage = SyncCoverage(onChange = { dirty = true })

    private val flusher: Thread

    init {
        load()
        // restore() bypasses onChange, but stay defensive: reopening a file
        // must never count as a change, or every boot rewrites it.
        dirty = false
        flusher =
            Thread {
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        Thread.sleep(flushSeconds * 1000)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                    flush()
                }
            }.apply {
                isDaemon = true
                name = "mirror-sync-coverage-flush"
                start()
            }
    }

    /** Write the map if anything changed since the last write. */
    @Synchronized
    fun flush() {
        if (!dirty) return
        dirty = false
        save()
    }

    override fun close() {
        flusher.interrupt()
        flush()
    }

    private fun load() {
        if (!file.isFile) return
        runCatching {
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            // The file's key is the joined form, decoded by the class that
            // mints it — this layer never has to know the separator. A key it
            // cannot read names no pair and is dropped, which costs one
            // upstream's re-walk rather than the whole file.
            coverage.restore(
                root.entries
                    .mapNotNull { (k, v) ->
                        val key = SyncCoverage.BandKey.decode(k) ?: return@mapNotNull null
                        val o = v.jsonObject
                        key to
                            SyncCoverage.Band(
                                spansOf(o),
                                o["fullAt"]?.jsonPrimitive?.long ?: 0L,
                            )
                    }.toMap(),
            )
        }.onFailure {
            Log.w("SyncCoverageFile") { "could not read ${file.path} (${it.message}); starting fresh" }
        }
    }

    /**
     * The per-kind spans, or the single pre-split span read as covering every
     * kind under [SyncCoverage.ALL_KINDS].
     *
     * A file written before coverage was tracked per kind carries only
     * `min`/`max`, and that is exactly the over-wide claim per-kind spans
     * exist to stop — so it is loaded as what it always meant rather than
     * discarded, and the first paged walk that reports per kind replaces it.
     * Dropping it instead would re-download every upstream's corpus once on
     * upgrade, which is the cost bands exist to avoid.
     *
     * Completeness is read the same way, one level down: a span written before
     * it was per kind has no `complete` of its own, so it inherits the band's —
     * which is precisely what that flag used to mean for every kind at once.
     */
    private fun spansOf(o: JsonObject): Map<Int, SyncCoverage.Span> {
        val bandComplete = o["complete"]?.jsonPrimitive?.boolean ?: false
        o["spans"]?.jsonObject?.let { spans ->
            return spans.entries.associate { (kind, v) ->
                val span = v.jsonObject
                kind.toInt() to
                    SyncCoverage.Span(
                        span.getValue("min").jsonPrimitive.long,
                        span.getValue("max").jsonPrimitive.long,
                        span["complete"]?.jsonPrimitive?.boolean ?: bandComplete,
                    )
            }
        }
        return mapOf(
            SyncCoverage.ALL_KINDS to
                SyncCoverage.Span(
                    o.getValue("min").jsonPrimitive.long,
                    o.getValue("max").jsonPrimitive.long,
                    bandComplete,
                ),
        )
    }

    @Synchronized
    private fun save() {
        runCatching {
            val doc =
                buildJsonObject {
                    coverage.export().forEach { (key, band) ->
                        put(
                            key.encode(),
                            buildJsonObject {
                                // min/max are the outer edges across every
                                // kind, and are written for two readers: a
                                // human debugging why an upstream re-synced,
                                // and a ROLLBACK — a binary from before spans
                                // were per kind reads these and behaves as it
                                // always did, rather than failing to parse.
                                put("min", band.minCreatedAt)
                                put("max", band.maxCreatedAt)
                                put("complete", band.complete)
                                put("fullAt", band.fullAt)
                                put(
                                    "spans",
                                    buildJsonObject {
                                        band.spans.forEach { (kind, span) ->
                                            put(
                                                kind.toString(),
                                                buildJsonObject {
                                                    put("min", span.min)
                                                    put("max", span.max)
                                                    put("complete", span.complete)
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile ?: File("."), "${file.name}.tmp")
            tmp.writeText(json.encodeToString(JsonObject.serializer(), doc))
            // ATOMIC_MOVE requested explicitly: without it the JVM may
            // legally fall back to copy+delete, and a reader could see a
            // half map. Same-directory rename, so support is the norm; a
            // filesystem that truly can't gets the plain move (and the
            // corrupt-file recovery absorbs the residual risk).
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure {
            Log.w("SyncCoverageFile") { "could not write ${file.path}: ${it.message}" }
        }
    }

    companion object {
        // Pretty-printed: this file is read by a human debugging why an
        // upstream re-synced.
        private val json = Json { prettyPrint = true }

        // Often enough that a kill costs little, rare enough to be free.
        private const val DEFAULT_FLUSH_SECONDS = 30L
    }
}
