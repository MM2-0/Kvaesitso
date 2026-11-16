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
package com.vitorpamplona.amethyst.commons.service.pow

import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-disk checkpoint of pending PoW mining jobs so posts survive process
 * death: the queue upserts on enqueue and removes on finish/cancel, and
 * [PowJobRestorer] re-enqueues whatever is left when an account logs in.
 *
 * [save]/[remove] are the queue-facing fire-and-forget hooks; they serialize
 * onto a single-lane dispatcher so writes land in call order.
 */
class PoWJobStore(
    private val storageFile: File,
    scope: CoroutineScope,
) : PoWJobPersistence {
    /**
     * `encodeDefaults = true` so this writes the same bytes Jackson did — kotlinx
     * omits a value equal to its default, which would silently drop `"version":1`
     * from every file this build rewrites. `ignoreUnknownKeys` matches the
     * FAIL_ON_UNKNOWN_PROPERTIES=false it replaces, so a file written by a newer
     * build still loads here.
     */
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    // one lane: launch order == execution order, so a save followed by its
    // remove can never be applied backwards.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val writeLane = Dispatchers.IO.limitedParallelism(1)
    private val writeScope = CoroutineScope(scope.coroutineContext + writeLane)

    private val mutex = Mutex()
    private var loaded = false
    private var jobs: MutableList<PersistedPoWJob> = mutableListOf()

    override fun save(job: PersistedPoWJob) {
        writeScope.launch {
            mutex.withLock {
                ensureLoaded()
                jobs.removeAll { it.id == job.id }
                jobs.add(job)
                persist()
            }
        }
    }

    override fun remove(jobId: String) {
        writeScope.launch {
            mutex.withLock {
                ensureLoaded()
                if (jobs.removeAll { it.id == jobId }) persist()
            }
        }
    }

    suspend fun listFor(accountPubkey: String): List<PersistedPoWJob> =
        withContext(writeLane) {
            mutex.withLock {
                ensureLoaded()
                jobs.filter { it.accountPubkey == accountPubkey }
            }
        }

    /** Drops every record owned by [accountPubkey] (account deletion). */
    fun removeForAccount(accountPubkey: String) {
        writeScope.launch {
            mutex.withLock {
                ensureLoaded()
                if (jobs.removeAll { it.accountPubkey == accountPubkey }) persist()
            }
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        jobs =
            try {
                if (storageFile.exists() && storageFile.length() > 0) {
                    json.decodeFromString<PoWJobsFile>(storageFile.readText()).jobs.toMutableList()
                } else {
                    mutableListOf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load pending PoW jobs from $storageFile", e)
                mutableListOf()
            }
        loaded = true
        val cutoff = System.currentTimeMillis() / 1000 - MAX_AGE_SEC
        if (jobs.removeAll { it.createdAtSec in 1 until cutoff }) persist()
    }

    private fun persist() {
        storageFile.parentFile?.mkdirs()
        val tmp = File(storageFile.parentFile, storageFile.name + ".tmp")
        try {
            tmp.writeText(json.encodeToString(PoWJobsFile(version = 1, jobs = jobs.toList())))
            if (!tmp.renameTo(storageFile)) {
                if (!storageFile.delete() || !tmp.renameTo(storageFile)) {
                    Log.e(TAG) { "Failed to rename $tmp to $storageFile" }
                    if (!tmp.delete()) {
                        Log.w(TAG) { "Failed to clean up temp file $tmp" }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist pending PoW jobs to $storageFile", e)
            if (!tmp.delete()) {
                Log.w(TAG) { "Failed to clean up temp file $tmp after persist exception" }
            }
        }
    }

    companion object {
        private const val TAG = "PoWJobStore"
        const val FILE_NAME = "pending_pow_jobs.json"

        // a job this stale is a post the user has long forgotten; publishing
        // it a week later would be more surprising than dropping it.
        private const val MAX_AGE_SEC = 3L * 24 * 3600
    }
}

@Serializable
data class PoWJobsFile(
    val version: Int = 1,
    val jobs: List<PersistedPoWJob> = emptyList(),
)
