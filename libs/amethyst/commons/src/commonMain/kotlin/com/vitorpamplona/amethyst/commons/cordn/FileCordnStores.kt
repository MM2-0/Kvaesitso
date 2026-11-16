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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.amethyst.commons.storage.EncryptedAppendLog
import com.vitorpamplona.amethyst.commons.util.deleteQuietly
import com.vitorpamplona.amethyst.commons.util.deleteRecursivelyQuietly
import com.vitorpamplona.amethyst.commons.util.moveOrCopy
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.sibling
import com.vitorpamplona.cordn.client.CoordinatorConfig
import com.vitorpamplona.cordn.client.CoordinatorListCodec
import com.vitorpamplona.cordn.client.CordnBlobCipher
import com.vitorpamplona.cordn.client.CordnCoordinatorStore
import com.vitorpamplona.cordn.client.CordnGroupStore
import com.vitorpamplona.cordn.client.CordnHandoffStore
import com.vitorpamplona.cordn.client.CordnKeyPackageStore
import com.vitorpamplona.cordn.client.CordnMessageSummary
import com.vitorpamplona.cordn.client.CordnMessageSummaryCodec
import com.vitorpamplona.cordn.client.CordnRoomState
import com.vitorpamplona.cordn.client.CordnRoomStateCodec
import com.vitorpamplona.cordn.client.EchoStateCodec
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessageCodec
import com.vitorpamplona.cordn.sync.EchoState
import com.vitorpamplona.cordn.sync.GroupCursor
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.Path
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Where one account's state for one coordinator lives on disk.
 *
 * Both halves of the key matter and neither is optional. Per account, because
 * two accounts on one device must not read each other's groups. Per
 * coordinator, because a `gid` is unique only within one (`spec/00.md` §4) —
 * two coordinators can both serve `gid = "abc"` as unrelated groups, and a
 * layout that ignored the coordinator would have one silently overwrite the
 * other's ratchet tree.
 */
@OptIn(ExperimentalEncodingApi::class)
object CordnStorageLayout {
    /**
     * `<root>/cordn/<account>/<coordinator>`.
     *
     * Both keys are validated as hex before they reach a path. They always are
     * — they are Nostr pubkeys — so this costs nothing and closes the one place
     * a caller could smuggle `..` into a directory name.
     */
    fun directoryFor(
        root: Path,
        accountPubKey: HexKey,
        coordinatorPubKey: HexKey,
    ): Path {
        require(accountPubKey.matches(HEX)) { "account pubkey must be hex" }
        require(coordinatorPubKey.matches(HEX)) { "coordinator pubkey must be hex" }
        return root / "cordn" / accountPubKey / coordinatorPubKey
    }

    /**
     * `<root>/cordn/<account>` — the account's own directory, above any
     * coordinator's.
     *
     * The coordinator list lives here rather than inside a coordinator's
     * directory, because it is the list OF them: storing it under one would
     * make that coordinator's removal delete the record of the others.
     */
    fun accountDirectoryFor(
        root: Path,
        accountPubKey: HexKey,
    ): Path {
        require(accountPubKey.matches(HEX)) { "account pubkey must be hex" }
        return root / "cordn" / accountPubKey
    }

    /**
     * A filename for an arbitrary caller-chosen key.
     *
     * Base64url rather than the key itself, and deliberately NOT the hex
     * validation Marmot's store uses. A Marmot group id is a hash and is
     * always hex; a cordn `gid` is whatever the group's creator picked (§4 —
     * the coordinator never interprets it, and the reference client happens to
     * use a UUID). So a `gid` can contain `/`, `..`, a NUL, or a name that is
     * special on some filesystem, and rejecting those would refuse groups the
     * protocol allows. Encoding accepts every one of them and can still be
     * reversed, which is what lets [FileCordnGroupStore.listGroups] give the
     * real `gid` back rather than an opaque hash.
     */
    fun encodeKey(key: String): String {
        val encoded = Base64.UrlSafe.encode(key.encodeToByteArray()).trimEnd('=')
        require(encoded.length <= MAX_NAME) { "key is too long to store: ${key.length} chars" }
        return encoded
    }

    /** The inverse of [encodeKey]; null when the name was not written by us. */
    fun decodeKey(name: String): String? =
        try {
            Base64.UrlSafe.decode(name.padEnd((name.length + 3) / 4 * 4, '=')).decodeToString()
        } catch (e: IllegalArgumentException) {
            // A stray file in our directory. Ignoring it is better than failing
            // the whole listing and hiding every real group behind it.
            null
        }

    private val HEX = Regex("^[0-9a-fA-F]{64}$")

    /** Comfortably under the 255-byte limit every filesystem we target has. */
    private const val MAX_NAME = 200
}

private const val TAG = "CordnStores"

/**
 * Writes a blob to [file] so that a crash leaves either the old bytes or the
 * new ones, never half of each.
 *
 * MLS makes this sharper than ordinary durability: a truncated `MlsGroupState`
 * is not a stale group, it is an unreadable one, and the group cannot be
 * re-derived from anywhere else on this device.
 */
private suspend fun FileSystem.atomicWrite(
    file: Path,
    data: ByteArray,
) = lockFor(file).withLock {
    file.parent?.let { createDirectories(it) }
    val temp = file.sibling("${file.name}.tmp")
    write(temp) { write(data) }
    moveOrCopy(temp, file)
}

private suspend fun FileSystem.readBytes(file: Path): ByteArray = lockFor(file).withLock { read(file) { readByteArray() } }

/**
 * Serialises every read and replace of one file, across every store instance.
 *
 * Windows refuses to open a file that is being renamed over ("being used by
 * another process"), and refuses the rename while someone is reading it, which
 * sends [moveOrCopy] to its non-atomic copy. POSIX allows both, so only the
 * Windows CI job ever saw it. The sync loop persisting a group while a backup
 * export or a handoff snapshot reads it off disk is exactly that pair.
 *
 * Process-wide, not per store: those readers build their own stores over the
 * same directory, so a lock held by an instance would never meet the writer's.
 * Striped so it holds no state per file; two files sharing a stripe only wait
 * on each other, and nothing takes a second stripe while holding one.
 */
private val fileLocks = Array(64) { Mutex() }

private fun lockFor(file: Path) = fileLocks[(file.normalized().toString().hashCode() and Int.MAX_VALUE) % fileLocks.size]

private fun FileSystem.isDirectory(path: Path): Boolean = metadataOrNull(path)?.isDirectory == true

private fun FileSystem.isRegularFile(path: Path): Boolean = metadataOrNull(path)?.isRegularFile == true

/**
 * A [CordnGroupStore] on the filesystem, encrypted through [cipher].
 *
 * ```
 * <dir>/groups/<base64url(gid)>/state       — encrypted MlsGroupState
 * <dir>/groups/<base64url(gid)>/cursor      — encrypted GroupCursor
 * <dir>/groups/<base64url(gid)>/via-request — present iff admitted by request
 * <dir>/groups/<base64url(gid)>/room        — encrypted draft + read position
 * ```
 *
 * Scope [dir] with [CordnStorageLayout.directoryFor]; this class trusts that it
 * already belongs to exactly one (account, coordinator) pair.
 *
 * Not a refactor of Marmot's `AndroidMlsGroupStateStore` and not shareable with
 * it: that one keys by a hex Nostr group id and persists retained epoch secrets
 * for Marmot's own rotation, neither of which exists here. The resemblance is
 * that both encrypt blobs, which is not an abstraction worth having.
 */
class FileCordnGroupStore(
    private val dir: Path,
    private val cipher: CordnBlobCipher,
    private val fileSystem: FileSystem = platformFileSystem,
) : CordnGroupStore {
    private fun groupDir(gid: String) = dir / "groups" / CordnStorageLayout.encodeKey(gid)

    private fun stateFile(gid: String) = groupDir(gid) / "state"

    private fun cursorFile(gid: String) = groupDir(gid) / "cursor"

    private fun joinOriginFile(gid: String) = groupDir(gid) / "via-request"

    private fun roomStateFile(gid: String) = groupDir(gid) / "room"

    private fun echoStateFile(gid: String) = groupDir(gid) / "echoes"

    /**
     * Inside [groupDir] so [deleteGroup]'s recursive delete already covers it:
     * a group that left its history behind would keep the plaintext of an
     * end-to-end encrypted conversation after the key that read it was gone.
     */
    private fun messagesFile(gid: String) = groupDir(gid) / "messages"

    private fun summaryFile(gid: String) = groupDir(gid) / "newest"

    /**
     * Appending a segment rather than rewriting the conversation, so the cost
     * of receiving a message does not grow with how much has been said.
     */
    private val messageLog =
        EncryptedAppendLog(
            encrypt = cipher::encrypt,
            // The log treats null as "this segment is unreadable" and carries on
            // with the rest, which is what one corrupt segment should cost.
            decrypt = { runCatching { cipher.decrypt(it) }.getOrNull() },
            fileSystem = fileSystem,
        )

    private val messageLock = Mutex()

    /**
     * Envelope ids already in a group's log.
     *
     * Dedup cannot be the log's own whole-entry comparison: the same message
     * re-delivered after a crash carries the same envelope but not necessarily
     * the same cursor, so the entries differ as strings while naming one
     * message. Built once per group from the log the first time it is touched.
     */
    private val seenIds = mutableMapOf<String, MutableSet<HexKey>>()

    private fun idsFor(gid: String): MutableSet<HexKey> =
        seenIds.getOrPut(gid) {
            messageLog
                .readAll(messagesFile(gid))
                .mapNotNullTo(mutableSetOf()) { CordnDeliveredMessageCodec.decodeOrNull(it)?.envelope?.id }
        }

    override suspend fun saveGroup(
        gid: String,
        state: ByteArray,
    ) = withContext(Dispatchers.IO) {
        fileSystem.atomicWrite(stateFile(gid), cipher.encrypt(state))
    }

    override suspend fun loadGroup(gid: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = stateFile(gid)
            if (!fileSystem.exists(file)) null else cipher.decrypt(fileSystem.readBytes(file))
        }

    override suspend fun deleteGroup(gid: String) {
        withContext(Dispatchers.IO) {
            messageLock.withLock {
                // The log caches a file's entries by path, so dropping the
                // directory alone would leave a re-join of the same gid reading
                // the previous membership's messages out of memory.
                messageLog.forget(messagesFile(gid))
                seenIds.remove(gid)
            }
            // The cursor goes with it. Leaving one behind would mean a later
            // re-join of the same gid resumes from a cursor belonging to a
            // group it is no longer in, skipping everything before it.
            fileSystem.deleteRecursivelyQuietly(groupDir(gid))
        }
    }

    override suspend fun listGroups(): List<String> =
        withContext(Dispatchers.IO) {
            fileSystem
                .listOrNull(dir / "groups")
                .orEmpty()
                .filter { fileSystem.isDirectory(it) && fileSystem.exists(it / "state") }
                .mapNotNull { CordnStorageLayout.decodeKey(it.name) }
        }

    // Two big-endian int64s, which is what ByteBuffer wrote: its default order
    // is BIG_ENDIAN, and so is okio's Buffer.
    override suspend fun saveCursor(
        gid: String,
        cursor: GroupCursor,
    ) = withContext(Dispatchers.IO) {
        val bytes = Buffer().writeLong(cursor.fetchCursor).writeLong(cursor.lastCursor).readByteArray()
        fileSystem.atomicWrite(cursorFile(gid), cipher.encrypt(bytes))
    }

    override suspend fun loadCursor(gid: String): GroupCursor? =
        withContext(Dispatchers.IO) {
            val file = cursorFile(gid)
            if (!fileSystem.exists(file)) return@withContext null
            val bytes = cipher.decrypt(fileSystem.readBytes(file))
            if (bytes.size < 16) return@withContext null
            // Only the first 16 bytes are read, as ByteBuffer.wrap did; a longer
            // blob's tail is ignored rather than refused.
            val buffer = Buffer().write(bytes, 0, 16)
            GroupCursor(fetchCursor = buffer.readLong(), lastCursor = buffer.readLong())
        }

    // Existence IS the flag, so there is nothing to encrypt and nothing to
    // read back wrong. It only ever goes from absent to present -- a join
    // request cannot be unsent -- and it is removed with the group because a
    // later re-join of the same gid is a different admission.
    override suspend fun saveJoinedViaRequest(gid: String) {
        withContext(Dispatchers.IO) {
            fileSystem.atomicWrite(joinOriginFile(gid), ByteArray(0))
        }
    }

    override suspend fun loadJoinedViaRequest(gid: String): Boolean = withContext(Dispatchers.IO) { fileSystem.exists(joinOriginFile(gid)) }

    override suspend fun saveRoomState(
        gid: String,
        state: CordnRoomState,
    ) = withContext(Dispatchers.IO) {
        // Deleted rather than blanked when there is nothing to remember, so an
        // emptied draft leaves no plaintext behind in an old file.
        if (state.isBlank) {
            fileSystem.deleteQuietly(roomStateFile(gid))
            return@withContext
        }
        fileSystem.atomicWrite(roomStateFile(gid), cipher.encrypt(CordnRoomStateCodec.encode(state)))
    }

    override suspend fun loadRoomState(gid: String): CordnRoomState =
        withContext(Dispatchers.IO) {
            val file = roomStateFile(gid)
            if (!fileSystem.exists(file)) return@withContext CordnRoomState()
            try {
                CordnRoomStateCodec.decode(cipher.decrypt(fileSystem.readBytes(file)))
            } catch (e: Exception) {
                CordnRoomState()
            }
        }

    override suspend fun appendMessage(
        gid: String,
        message: CordnDeliveredMessage,
    ) = withContext(Dispatchers.IO) {
        messageLock.withLock {
            val ids = idsFor(gid)
            // Idempotent on the envelope id. The crash window between this and
            // saveCursor is deliberate — see the store interface — and it is
            // this check that makes re-delivery free rather than duplicating.
            if (!ids.add(message.envelope.id)) return@withContext

            fileSystem.createDirectories(groupDir(gid))
            messageLog.append(messagesFile(gid), CordnDeliveredMessageCodec.encode(message))
            // Written on the same beat, so the inbox preview cannot disagree
            // with the room. Whole-blob rather than appended: it is one entry
            // that is always overwritten.
            fileSystem.atomicWrite(summaryFile(gid), cipher.encrypt(CordnMessageSummaryCodec.encode(message, ids.size)))
        }
    }

    override suspend fun loadMessages(gid: String): List<CordnDeliveredMessage> =
        withContext(Dispatchers.IO) {
            messageLock.withLock {
                // One unreadable entry costs that message, not the conversation
                // behind it in the file.
                messageLog.readAll(messagesFile(gid)).mapNotNull { CordnDeliveredMessageCodec.decodeOrNull(it) }
            }
        }

    override suspend fun loadMessageSummary(gid: String): CordnMessageSummary? =
        withContext(Dispatchers.IO) {
            val file = summaryFile(gid)
            if (!fileSystem.exists(file)) return@withContext null
            try {
                CordnMessageSummaryCodec.decode(cipher.decrypt(fileSystem.readBytes(file)))
            } catch (e: Exception) {
                // A summary is a derived convenience; losing one costs a preview
                // line until the next message, not the history it summarises.
                Log.w("FileCordnGroupStore", "unreadable message summary for $gid: ${e.message}", e)
                null
            }
        }

    override suspend fun saveEchoState(
        gid: String,
        state: EchoState,
    ) = withContext(Dispatchers.IO) {
        // Deleted when there is nothing pending, so the common steady state is
        // no file rather than an empty one.
        if (state.isEmpty) {
            fileSystem.deleteQuietly(echoStateFile(gid))
            return@withContext
        }
        fileSystem.atomicWrite(echoStateFile(gid), cipher.encrypt(EchoStateCodec.encode(state)))
    }

    override suspend fun loadEchoState(gid: String): EchoState =
        withContext(Dispatchers.IO) {
            val file = echoStateFile(gid)
            if (!fileSystem.exists(file)) return@withContext EchoState()
            try {
                EchoStateCodec.decode(cipher.decrypt(fileSystem.readBytes(file)))
            } catch (e: Exception) {
                EchoState()
            }
        }
}

/**
 * A [CordnKeyPackageStore] on the filesystem, encrypted through [cipher].
 *
 * ```
 * <dir>/keypackages/<base64url(kp_ref)>  — encrypted KeyPackageBundle
 * ```
 *
 * Scope [dir] per coordinator like the group store: a `kp_ref` is the
 * coordinator's primary key for a KeyPackage (§4.2) and the same account
 * publishes different ones to different coordinators.
 */
class FileCordnKeyPackageStore(
    private val dir: Path,
    private val cipher: CordnBlobCipher,
    private val fileSystem: FileSystem = platformFileSystem,
) : CordnKeyPackageStore {
    private fun bundleFile(keyPackageRef: String) = dir / "keypackages" / CordnStorageLayout.encodeKey(keyPackageRef)

    override suspend fun save(
        keyPackageRef: String,
        bundle: ByteArray,
    ) = withContext(Dispatchers.IO) {
        fileSystem.atomicWrite(bundleFile(keyPackageRef), cipher.encrypt(bundle))
    }

    override suspend fun load(keyPackageRef: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = bundleFile(keyPackageRef)
            if (!fileSystem.exists(file)) null else cipher.decrypt(fileSystem.readBytes(file))
        }

    override suspend fun delete(keyPackageRef: String) {
        withContext(Dispatchers.IO) {
            fileSystem.deleteQuietly(bundleFile(keyPackageRef))
        }
    }

    override suspend fun list(): List<String> =
        withContext(Dispatchers.IO) {
            fileSystem
                .listOrNull(dir / "keypackages")
                .orEmpty()
                .filter { fileSystem.isRegularFile(it) }
                // No explicit ".tmp" exclusion, because the encoding already
                // is one: '.' is not in the base64url alphabet, so a temp file
                // left behind by a crashed write cannot decode to a ref and
                // [CordnStorageLayout.decodeKey] drops it. A second filter
                // saying the same thing would be a branch no test can reach.
                .mapNotNull { CordnStorageLayout.decodeKey(it.name) }
        }
}

/**
 * A [CordnCoordinatorStore] on the filesystem, encrypted through [cipher].
 *
 * ```
 * <dir>/coordinators  — encrypted CoordinatorListCodec blob
 * ```
 *
 * Scope [dir] with [CordnStorageLayout.accountDirectoryFor]: one file per
 * account, sitting above the per-coordinator directories it names.
 */
class FileCordnCoordinatorStore(
    private val dir: Path,
    private val cipher: CordnBlobCipher,
    private val fileSystem: FileSystem = platformFileSystem,
) : CordnCoordinatorStore {
    private val file get() = dir / "coordinators"

    override suspend fun save(configs: List<CoordinatorConfig>) =
        withContext(Dispatchers.IO) {
            fileSystem.atomicWrite(file, cipher.encrypt(CoordinatorListCodec.encode(configs)))
        }

    override suspend fun load(): List<CoordinatorConfig> =
        withContext(Dispatchers.IO) {
            val stored = file
            if (!fileSystem.exists(stored)) return@withContext emptyList()
            try {
                CoordinatorListCodec.decode(cipher.decrypt(fileSystem.readBytes(stored)))
            } catch (e: Exception) {
                // A list written by a future build, or one the keystore can no
                // longer decrypt. Returning nothing loses the coordinators but
                // keeps the account usable; throwing here would fail login.
                //
                // Say so, though. Losing this list loses every cordn group with
                // it — the MLS state stays on disk but a gid whose coordinator
                // is unknown is not a group anyone can open — and silently it
                // looks exactly like an account that never had a coordinator:
                // the rooms are gone from the inbox and the invitations screen
                // says there is nowhere to look. One line is the difference
                // between a diagnosable fault and a mystery.
                val size = fileSystem.metadataOrNull(stored)?.size ?: 0L
                Log.w(TAG, "could not read $size bytes of coordinators, losing them: ${e.message}", e)
                emptyList()
            }
        }
}

/**
 * The handoff flag, as the presence of a file.
 *
 * `<accountDir>/handed-off`. A zero-byte marker rather than an encrypted blob:
 * it carries no secret, and a flag that failed to decrypt would fail open —
 * which here means a device that quietly resumes committing after handing its
 * groups to another one, the exact fork the flag exists to prevent.
 */
class FileCordnHandoffStore(
    private val accountDir: Path,
    private val fileSystem: FileSystem = platformFileSystem,
) : CordnHandoffStore {
    override suspend fun load(): Boolean = fileSystem.exists(marker())

    override suspend fun save(handedOff: Boolean) {
        val file = marker()
        if (handedOff) {
            file.parent?.let { fileSystem.createDirectories(it) }
            fileSystem.write(file) { }
        } else {
            fileSystem.deleteQuietly(file)
        }
    }

    private fun marker() = accountDir / "handed-off"
}
