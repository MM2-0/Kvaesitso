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
package com.vitorpamplona.amethyst.commons.scheduledposts

import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.restrictFileToOwner
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class ScheduledPostStore(
    private val storageFile: Path,
    private val nowSec: () -> Long = { TimeUtils.now() },
    private val fileSystem: FileSystem = platformFileSystem,
) {
    /** Takes the platform's own path string, so callers need no okio of their own. */
    constructor(
        storagePath: String,
        nowSec: () -> Long = { TimeUtils.now() },
    ) : this(storagePath.toPath(), nowSec)

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

    private val mutex = Mutex()
    private var loaded = false
    private var posts: MutableList<ScheduledPost> = mutableListOf()

    private val _flow = MutableStateFlow<List<ScheduledPost>>(emptyList())

    /** Live snapshot of all stored posts. Updated on every mutation. */
    val flow: StateFlow<List<ScheduledPost>> = _flow.asStateFlow()

    suspend fun add(post: ScheduledPost) =
        mutex.withLock {
            ensureLoaded()
            posts.add(post)
            persist()
        }

    /**
     * Cancel a scheduled post. Only PENDING or FAILED rows may be cancelled — a
     * PUBLISHING row is mid-send and must not be pulled out from under the drainer,
     * so cancelling one is a no-op that returns false. Reloads first so the decision
     * is made against the other process's committed writes.
     */
    suspend fun cancel(id: String): Boolean =
        mutex.withLock {
            ensureLoaded()
            reloadFromDiskLocked()
            val now = nowSec()
            val updated =
                mutate(id) {
                    if (it.status == ScheduledPostStatus.PENDING || it.status == ScheduledPostStatus.FAILED) {
                        it.copy(status = ScheduledPostStatus.CANCELLED, terminatedAtSec = now)
                    } else {
                        it
                    }
                }
            if (updated) persist()
            updated
        }

    suspend fun list(): List<ScheduledPost> =
        mutex.withLock {
            ensureLoaded()
            posts.toList()
        }

    suspend fun listFor(accountPubkey: String): List<ScheduledPost> =
        mutex.withLock {
            ensureLoaded()
            posts.filter { it.accountPubkey == accountPubkey }
        }

    /**
     * Atomically claim posts due at or before [nowSec]: each PENDING post with
     * publishAtSec <= now is flipped to PUBLISHING and returned. A concurrent
     * claim from another worker will see those posts as PUBLISHING and skip them.
     *
     * This all-accounts overload is used by Desktop's headless `--publish-scheduled`
     * run, which publishes on behalf of every account in a single pass.
     */
    suspend fun claimDuePosts(nowSec: Long): List<ScheduledPost> =
        mutex.withLock {
            ensureLoaded()
            reloadFromDiskLocked()
            recoverStuckClaims(nowSec)
            claimLocked(nowSec) { true }
        }

    /**
     * Same as [claimDuePosts] but scoped to a single account: only rows whose
     * accountPubkey equals [accountPubkey] are claimed. Used by callers that drive
     * publishing per-account (e.g. Desktop) so one account's drain never touches
     * another account's due posts.
     */
    suspend fun claimDuePosts(
        nowSec: Long,
        accountPubkey: String,
    ): List<ScheduledPost> =
        mutex.withLock {
            ensureLoaded()
            reloadFromDiskLocked()
            recoverStuckClaims(nowSec)
            claimLocked(nowSec) { it.accountPubkey == accountPubkey }
        }

    private fun claimLocked(
        nowSec: Long,
        predicate: (ScheduledPost) -> Boolean,
    ): List<ScheduledPost> {
        ensureLoaded()
        val dueIds =
            posts
                .filter { it.status == ScheduledPostStatus.PENDING && it.publishAtSec <= nowSec && predicate(it) }
                .map { it.id }
                .toSet()
        if (dueIds.isEmpty()) return emptyList()
        dueIds.forEach { id ->
            mutate(id) {
                it.copy(
                    status = ScheduledPostStatus.PUBLISHING,
                    lastAttemptAtSec = nowSec,
                    attemptCount = it.attemptCount + 1,
                )
            }
        }
        persist()
        return posts.filter { it.id in dueIds }
    }

    /**
     * Mark a claimed row SENT. Only a PUBLISHING row may transition to SENT — this
     * guards against a stale caller stamping SENT over a row another process already
     * re-claimed. Reloads first so the guard sees the other process's committed state.
     */
    suspend fun markSent(id: String): Boolean =
        mutex.withLock {
            ensureLoaded()
            reloadFromDiskLocked()
            val now = nowSec()
            val updated =
                mutate(id) {
                    if (it.status == ScheduledPostStatus.PUBLISHING) {
                        it.copy(status = ScheduledPostStatus.SENT, lastError = null, terminatedAtSec = now)
                    } else {
                        it
                    }
                }
            if (updated) persist()
            updated
        }

    /**
     * Mark a claimed row FAILED. Only a PUBLISHING row may transition to FAILED, for the
     * same reason as [markSent]. Reloads first to adopt the other process's writes.
     */
    suspend fun markFailed(
        id: String,
        error: String?,
    ): Boolean =
        mutex.withLock {
            ensureLoaded()
            reloadFromDiskLocked()
            val updated =
                mutate(id) {
                    if (it.status == ScheduledPostStatus.PUBLISHING) {
                        it.copy(status = ScheduledPostStatus.FAILED, lastError = error)
                    } else {
                        it
                    }
                }
            if (updated) persist()
            updated
        }

    /**
     * Force a post to publish immediately by setting its publishAtSec to [nowSec]
     * and resetting status to PENDING. Handles cases with one method:
     *  - PENDING (future-scheduled): user wants to push it out now
     *  - FAILED: user wants to retry
     *  - CANCELLED: user wants to re-activate a cancelled row
     * A PUBLISHING row is mid-send and is left untouched (returns false) so a UI
     * action can't stomp a live claim. Reloads first so the guard reflects the other
     * process's committed writes. Caller is expected to enqueue
     * ScheduledPostWorker.scheduleCatchUp() afterwards so the worker picks it up promptly.
     */
    suspend fun publishNow(
        id: String,
        nowSec: Long = TimeUtils.now(),
    ): Boolean =
        mutex.withLock {
            ensureLoaded()
            reloadFromDiskLocked()
            val updated =
                mutate(id) {
                    if (it.status != ScheduledPostStatus.PUBLISHING) {
                        it.copy(
                            publishAtSec = nowSec,
                            status = ScheduledPostStatus.PENDING,
                            lastError = null,
                            terminatedAtSec = null,
                        )
                    } else {
                        it
                    }
                }
            if (updated) persist()
            updated
        }

    /**
     * Remove every row owned by [accountPubkey]. Used when the user deletes
     * an account — the account's signed events should not linger. Returns the
     * number of rows removed; persists once if any rows matched.
     */
    suspend fun removeForAccount(accountPubkey: String): Int =
        mutex.withLock {
            ensureLoaded()
            val before = posts.size
            val removed = posts.removeAll { it.accountPubkey == accountPubkey }
            val count = before - posts.size
            if (removed) persist()
            count
        }

    /**
     * Revert a PUBLISHING claim back to PENDING (e.g. when the account is not
     * loaded at fire time, so we should retry on the next cycle rather than
     * marking the post failed permanently).
     */
    suspend fun releaseClaim(id: String) =
        mutex.withLock {
            ensureLoaded()
            reloadFromDiskLocked()
            val changed =
                mutate(id) {
                    if (it.status == ScheduledPostStatus.PUBLISHING) {
                        it.copy(status = ScheduledPostStatus.PENDING)
                    } else {
                        it
                    }
                }
            if (changed) persist()
        }

    private fun mutate(
        id: String,
        transform: (ScheduledPost) -> ScheduledPost,
    ): Boolean {
        val idx = posts.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val before = posts[idx]
        val after = transform(before)
        if (after == before) return false
        posts[idx] = after
        return true
    }

    /**
     * Revert PUBLISHING rows that have been stuck longer than [CLAIM_TTL_SEC] back
     * to PENDING. A post is flipped to PUBLISHING the moment it is claimed; if the
     * process crashes (or the worker is torn down) mid-publish, that row would stay
     * PUBLISHING forever and never be re-claimed — silently stranded. Reverting an
     * expired claim lets the next cycle retry it. Returns true if any row changed.
     *
     * Runs both at first load (via [ensureLoaded], to unstick rows written by a
     * previous process) and at the start of every claim (to unstick rows stranded
     * within the current process). [now] must be on the same time base as the
     * rows' lastAttemptAtSec — the claim path passes the caller-supplied nowSec so
     * the TTL is compared against the same clock that stamped the claim. Must be
     * called under [mutex]; persist is the caller's responsibility to keep the
     * single-write-per-op contract.
     */
    private fun recoverStuckClaimsLocked(now: Long): Boolean {
        var changed = false
        posts.indices.forEach { idx ->
            val post = posts[idx]
            val lastAttempt = post.lastAttemptAtSec
            if (post.status == ScheduledPostStatus.PUBLISHING && lastAttempt != null && now - lastAttempt > CLAIM_TTL_SEC) {
                posts[idx] = post.copy(status = ScheduledPostStatus.PENDING)
                changed = true
            }
        }
        return changed
    }

    private fun recoverStuckClaims(now: Long): Boolean {
        ensureLoaded()
        val changed = recoverStuckClaimsLocked(now)
        if (changed) persist()
        return changed
    }

    /**
     * Re-read the on-disk snapshot into [posts] and refresh [_flow].
     *
     * Cross-process reload: the running app and the headless `--publish-scheduled`
     * process share this file but each keeps its own in-memory copy. Without a reload
     * the app never sees status writes made by the headless process, so it could
     * re-claim and double-publish an already-SENT row. Because every mutation persists
     * immediately (in-memory == disk after every op), adopting the disk snapshot at the
     * start of a cross-process-sensitive op only pulls in the *other* process's
     * committed changes — it can never lose one of our own writes.
     *
     * Tolerant of a missing/corrupt file: on any parse failure the current in-memory
     * [posts] is kept untouched. Must be called under [mutex], after [ensureLoaded].
     */
    private fun reloadFromDiskLocked() {
        val fromDisk =
            try {
                if (hasContent()) {
                    json.decodeFromString<ScheduledPostFile>(readText()).posts.toMutableList()
                } else {
                    // File vanished — treat as no external state; keep current in-memory.
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reload scheduled posts from $storageFile", e)
                return
            }
        posts = fromDisk
        _flow.value = posts.toList()
    }

    private fun ensureLoaded() {
        if (loaded) return
        posts =
            try {
                if (hasContent()) {
                    json.decodeFromString<ScheduledPostFile>(readText()).posts.toMutableList()
                } else {
                    mutableListOf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load scheduled posts from $storageFile", e)
                mutableListOf()
            }
        loaded = true
        // Secure a pre-existing file up front (an older build may have left it at the
        // 0644 umask default) so it's owner-only even if nothing mutates the store
        // this session.
        if (storeFileExists()) restrictToOwner(storageFile)
        var dirty = purgeStale(nowSec())
        // Recover claims stranded by a crash-mid-publish so they aren't lost forever.
        if (recoverStuckClaimsLocked(nowSec())) dirty = true
        _flow.value = posts.toList()
        if (dirty) persist()
    }

    /**
     * Drop SENT rows older than [SENT_RETENTION_SEC] and CANCELLED rows older
     * than [CANCELLED_RETENTION_SEC]. Returns true if any row was removed.
     * FAILED rows are kept indefinitely so the user can still see and retry them;
     * PENDING / PUBLISHING rows are never purged.
     */
    private fun purgeStale(now: Long): Boolean {
        val before = posts.size
        posts.removeAll { post ->
            // terminatedAtSec is the post-PR field. Legacy rows lack it; fall back to
            // lastAttemptAtSec (set at SENT) and finally createdAtSec. The fallback
            // can purge old CANCELLED rows up to 30d earlier than intended on first
            // run after upgrade — self-healing once new rows are written.
            val age = now - (post.terminatedAtSec ?: post.lastAttemptAtSec ?: post.createdAtSec)
            when (post.status) {
                ScheduledPostStatus.SENT -> age > SENT_RETENTION_SEC
                ScheduledPostStatus.CANCELLED -> age > CANCELLED_RETENTION_SEC
                else -> false
            }
        }
        return posts.size < before
    }

    /**
     * Writes the snapshot to disk *while holding the data mutex*. This is a
     * deliberate tradeoff: moving the write outside the lock would require a
     * separate write-mutex (or a sequence number) to preserve write ordering
     * across concurrent mutations — otherwise an older snapshot can clobber a
     * newer one if the OS schedules the second write to finish first. For a
     * file that's a few KB and a single-process owner with infrequent writes,
     * holding the mutex across the rename is the simpler and correct choice.
     * Revisit if the store ever grows past a hundred rows or starts seeing
     * concurrent multi-writer pressure.
     */
    private fun persist() {
        val snapshot = posts.toList()
        _flow.value = snapshot
        val tmp = "$storageFile.tmp".toPath()
        try {
            storageFile.parent?.let { fileSystem.createDirectories(it) }
            fileSystem.write(tmp) { writeUtf8(json.encodeToString(ScheduledPostFile(version = 1, posts = snapshot))) }
            // Restrict to owner-only BEFORE the rename, so the store file itself is never
            // world-readable. It holds pre-signed events + the account's pubkey, which
            // must not leak to other local users on a shared machine. (The tmp file is
            // created at the umask default and narrowed right after its write; the
            // directory around it is owner-only on every host that uses this store.)
            restrictToOwner(tmp)
            // Replaces an existing target, including on Windows, where a plain rename
            // onto an existing file fails.
            fileSystem.atomicMove(tmp, storageFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist scheduled posts to $storageFile", e)
            try {
                fileSystem.delete(tmp)
            } catch (cleanupError: Exception) {
                Log.w(TAG, "Failed to clean up temp file $tmp after persist exception", cleanupError)
            }
        }
    }

    /**
     * Permissions are a property of the real disk, so a caller that injects another
     * [FileSystem] (an in-memory fake) gets no chmod aimed at a same-named real path.
     */
    private fun restrictToOwner(path: Path) {
        if (fileSystem === platformFileSystem) restrictFileToOwner(path, TAG)
    }

    /**
     * The store file's metadata, or null when there is none. A directory is not a store
     * file. A symlink counts: okio's metadata does not follow links, so its size is the
     * link's, and reading it (which does follow) decides whether there is anything in it.
     * A stat that fails (EACCES on iOS, where File.exists() used to answer false) is "no
     * file" rather than an exception out of the store.
     */
    private fun storeFileMetadata() =
        try {
            fileSystem.metadataOrNull(storageFile)?.takeIf { it.isRegularFile || it.symlinkTarget != null }
        } catch (e: Exception) {
            Log.w(TAG, "Could not stat $storageFile", e)
            null
        }

    private fun storeFileExists(): Boolean = storeFileMetadata() != null

    private fun hasContent(): Boolean = (storeFileMetadata()?.size ?: 0L) > 0L

    private fun readText(): String = fileSystem.read(storageFile) { readUtf8() }

    companion object {
        private const val TAG = "ScheduledPostStore"
        const val FILE_NAME = "scheduled_posts.json"
        private const val SENT_RETENTION_SEC = 7L * 24 * 3600
        private const val CANCELLED_RETENTION_SEC = 30L * 24 * 3600

        // A row stuck in PUBLISHING longer than this is assumed to be a crash-mid-
        // publish leftover and is reverted to PENDING so it can be retried.
        private const val CLAIM_TTL_SEC = 10L * 60
    }
}
