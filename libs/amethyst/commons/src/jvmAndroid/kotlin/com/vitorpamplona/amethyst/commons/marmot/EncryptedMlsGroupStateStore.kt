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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.amethyst.commons.model.preferences.SecretEncryption
import com.vitorpamplona.marmot.groups.MlsGroupStateStore
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * File-backed [MlsGroupStateStore], encrypted at rest.
 *
 * All MLS group state (containing private keys and epoch secrets) is encrypted
 * at rest using [SecretEncryption] (AES-256-GCM, keyed by the platform's keystore).
 *
 * Storage layout:
 * ```
 * <rootDir>/mls_groups/<nostrGroupId>/state     — encrypted MlsGroupState
 * <rootDir>/mls_groups/<nostrGroupId>/retained  — encrypted retained epoch secrets
 * <rootDir>/mls_groups/<nostrGroupId>/ratchet   — encrypted OwnSenderRatchet
 * ```
 */
class EncryptedMlsGroupStateStore(
    private val rootDir: File,
    private val encryption: SecretEncryption = SecretEncryption(),
) : MlsGroupStateStore {
    init {
        Log.d(TAG) {
            "Initialized EncryptedMlsGroupStateStore: rootDir=${rootDir.absolutePath}, " +
                "mls_groups exists=${File(rootDir, "mls_groups").exists()}"
        }
    }

    private fun groupDir(nostrGroupId: String): File {
        // Validate nostrGroupId is a hex string to prevent path traversal
        require(nostrGroupId.matches(HEX_PATTERN)) {
            "Invalid nostrGroupId: must be a hex string"
        }
        return File(rootDir, "mls_groups/$nostrGroupId")
    }

    companion object {
        private const val TAG = "EncryptedMlsGroupStateStore"
        private val HEX_PATTERN = Regex("^[0-9a-fA-F]+$")
    }

    private fun stateFile(nostrGroupId: String): File = File(groupDir(nostrGroupId), "state")

    private fun retainedFile(nostrGroupId: String): File = File(groupDir(nostrGroupId), "retained")

    private fun ratchetFile(nostrGroupId: String): File = File(groupDir(nostrGroupId), "ratchet")

    override suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    ) = withContext(Dispatchers.IO) {
        val file = stateFile(nostrGroupId)
        Log.d(TAG) { "save($nostrGroupId): ${state.size} bytes → ${file.absolutePath}" }
        try {
            file.parentFile?.mkdirs()
            val encrypted = encryption.encrypt(state)
            atomicWrite(file, encrypted)
            Log.d(TAG) {
                "save($nostrGroupId): wrote ${encrypted.size} encrypted bytes, " +
                    "file exists=${file.exists()}, file size=${if (file.exists()) file.length() else -1}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "save($nostrGroupId) FAILED: ${e.message}", e)
            throw e
        }
    }

    override suspend fun load(nostrGroupId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = stateFile(nostrGroupId)
            if (!file.exists()) {
                Log.d(TAG) { "load($nostrGroupId): file does not exist at ${file.absolutePath}" }
                return@withContext null
            }
            try {
                val bytes = file.readBytes()
                Log.d(TAG) { "load($nostrGroupId): read ${bytes.size} encrypted bytes from ${file.absolutePath}" }
                val decrypted = encryption.decrypt(bytes)
                Log.d(TAG) { "load($nostrGroupId): decrypted to ${decrypted?.size ?: -1} bytes" }
                decrypted
            } catch (e: Exception) {
                Log.e(TAG, "load($nostrGroupId) FAILED: ${e.message}", e)
                throw e
            }
        }

    override suspend fun delete(nostrGroupId: String) {
        withContext(Dispatchers.IO) {
            val dir = groupDir(nostrGroupId)
            Log.w(TAG) { "delete($nostrGroupId): deleting ${dir.absolutePath}" }
            dir.deleteRecursively()
        }
    }

    override suspend fun listGroups(): List<String> =
        withContext(Dispatchers.IO) {
            val baseDir = File(rootDir, "mls_groups")
            if (!baseDir.exists()) {
                Log.d(TAG) { "listGroups(): base dir does not exist at ${baseDir.absolutePath}" }
                return@withContext emptyList()
            }
            val allEntries = baseDir.listFiles()?.toList() ?: emptyList()
            val result =
                allEntries
                    .filter { it.isDirectory && File(it, "state").exists() }
                    .map { it.name }
            Log.d(TAG) {
                "listGroups(): ${baseDir.absolutePath} has ${allEntries.size} entries, " +
                    "${result.size} valid groups: $result"
            }
            result
        }

    override suspend fun saveRetainedEpochs(
        nostrGroupId: String,
        retainedSecrets: List<ByteArray>,
    ) = withContext(Dispatchers.IO) {
        val file = retainedFile(nostrGroupId)
        file.parentFile?.mkdirs()

        // Format: 4-byte count, then for each: 4-byte length + data
        val totalSize = 4 + retainedSecrets.sumOf { 4 + it.size }
        val buffer = ByteArray(totalSize)
        var offset = 0

        // Write count
        val count = retainedSecrets.size
        buffer[offset++] = (count shr 24).toByte()
        buffer[offset++] = (count shr 16).toByte()
        buffer[offset++] = (count shr 8).toByte()
        buffer[offset++] = count.toByte()

        // Write each entry
        for (secret in retainedSecrets) {
            val len = secret.size
            buffer[offset++] = (len shr 24).toByte()
            buffer[offset++] = (len shr 16).toByte()
            buffer[offset++] = (len shr 8).toByte()
            buffer[offset++] = len.toByte()
            secret.copyInto(buffer, offset)
            offset += len
        }

        atomicWrite(file, encryption.encrypt(buffer))
    }

    /**
     * The per-send ratchet position, written on its own so a message does not
     * re-encrypt the whole group state to record ~72 bytes.
     *
     * Written atomically, through a temp file and a rename. An in-place write
     * looked adequate — the record is small and written whole — but the failure
     * it allows is the one this record exists to prevent. A torn record is
     * discarded on load, which falls back to the position in the full state:
     * that is the position as of the last COMMIT, behind by every send since.
     * The next send would then re-emit every generation in between, reusing
     * AEAD key+nonce pairs. With a rename, a crash leaves the previous complete
     * record instead, which is at worst one send behind — and that send's
     * ciphertext was never published, because publishing waits for this write.
     */
    override suspend fun saveSenderRatchet(
        nostrGroupId: String,
        state: ByteArray,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val file = ratchetFile(nostrGroupId)
            try {
                file.parentFile?.mkdirs()
                val encrypted = encryption.encrypt(state)
                val tempFile = File(file.parentFile, "${file.name}.tmp")
                FileOutputStream(tempFile).use { out ->
                    out.write(encrypted)
                    out.fd.sync()
                }
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    if (!tempFile.delete()) {
                        Log.w(TAG) { "Failed to delete the temp ratchet file: ${tempFile.absolutePath}" }
                    }
                }
                true
            } catch (e: Exception) {
                // Returning false sends the caller to a full state write, which
                // is slower but carries the same guarantee. Swallowing this and
                // returning true would drop the position on the floor.
                Log.w(TAG, "saveSenderRatchet($nostrGroupId) FAILED, falling back to a full state write: ${e.message}", e)
                false
            }
        }

    override suspend fun loadSenderRatchet(nostrGroupId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = ratchetFile(nostrGroupId)
            if (!file.exists()) return@withContext null
            try {
                encryption.decrypt(file.readBytes())
            } catch (e: Exception) {
                // Serious: the fallback is the full state's position, which is
                // BEHIND, and a behind position re-emits used generations. The
                // atomic write above is what should make this unreachable, so
                // if it ever fires the group's key material is suspect.
                Log.e(TAG, "loadSenderRatchet($nostrGroupId) unreadable — the ratchet may rewind: ${e.message}", e)
                null
            }
        }

    override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> =
        withContext(Dispatchers.IO) {
            val file = retainedFile(nostrGroupId)
            if (!file.exists()) return@withContext emptyList()

            val buffer = encryption.decrypt(file.readBytes()) ?: return@withContext emptyList()
            if (buffer.size < 4) return@withContext emptyList()

            var offset = 0
            val count =
                ((buffer[offset++].toInt() and 0xFF) shl 24) or
                    ((buffer[offset++].toInt() and 0xFF) shl 16) or
                    ((buffer[offset++].toInt() and 0xFF) shl 8) or
                    (buffer[offset++].toInt() and 0xFF)

            val result = mutableListOf<ByteArray>()
            for (i in 0 until count) {
                if (offset + 4 > buffer.size) break
                val len =
                    ((buffer[offset++].toInt() and 0xFF) shl 24) or
                        ((buffer[offset++].toInt() and 0xFF) shl 16) or
                        ((buffer[offset++].toInt() and 0xFF) shl 8) or
                        (buffer[offset++].toInt() and 0xFF)
                if (offset + len > buffer.size) break
                result.add(buffer.copyOfRange(offset, offset + len))
                offset += len
            }
            result
        }

    /**
     * Write data atomically: write to a temp file first, then rename.
     * This avoids corrupted state if the app crashes mid-write.
     */
    private fun atomicWrite(
        target: File,
        data: ByteArray,
    ) {
        val tempFile = File(target.parentFile, "${target.name}.tmp")
        tempFile.writeBytes(data)
        if (!tempFile.renameTo(target)) {
            // Fallback: if rename fails (e.g., cross-filesystem), copy and delete
            tempFile.copyTo(target, overwrite = true)
            if (!tempFile.delete()) {
                Log.w("EncryptedMlsGroupStateStore") { "Failed to delete temp file after copy fallback: ${tempFile.absolutePath}" }
            }
        }
    }
}
