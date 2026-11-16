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
package com.vitorpamplona.geode.config

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

/**
 * Resolves the relay's own key pair (NIP-11 `self`) from
 * [StaticConfig.IdentitySection] — see its docs for the precedence.
 */
object RelayIdentity {
    /**
     * @param needed whether a feature that signs as the relay (NIP-43
     *   membership) is on; without it and without explicit config there
     *   is no identity (`null`).
     * @param stateFile `[admin].state_file`, next to which a generated key
     *   is kept when [needed] and nothing else is configured.
     * @param warn where to report falling back to an in-memory key.
     */
    fun resolve(
        identity: StaticConfig.IdentitySection,
        needed: Boolean,
        stateFile: String?,
        warn: (String) -> Unit = { System.err.println("warning: $it") },
    ): KeyPair? {
        identity.secret_key?.let { return parse(it, "[identity].secret_key") }
        identity.secret_key_file?.let { return loadOrCreate(File(it)) }
        if (!needed) return null
        stateFile?.let { return loadOrCreate(File("$it$KEY_FILE_SUFFIX")) }
        warn(
            "no relay key configured ([identity].secret_key / secret_key_file, or [admin].state_file); " +
                "using a throwaway identity — the NIP-11 self pubkey will change on every restart",
        )
        return KeyPair()
    }

    /** Reads the key in [file], or writes a newly generated one there (owner-only) when it's missing. */
    fun loadOrCreate(file: File): KeyPair {
        if (file.exists()) return parse(file.readText(), file.path)

        val key = KeyPair()
        file.absoluteFile.parentFile?.mkdirs()
        val tmp = File(file.absoluteFile.parentFile, "${file.name}.tmp")
        Files.deleteIfExists(tmp.toPath())
        try {
            Files.createFile(tmp.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        } catch (_: UnsupportedOperationException) {
            // Not a POSIX filesystem (Windows): fall back to the default ACLs.
            tmp.createNewFile()
        }
        tmp.writeText(key.privKey!!.toHexKey() + "\n")
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return key
    }

    private fun parse(
        value: String,
        source: String,
    ): KeyPair {
        val hex =
            decodePrivateKeyAsHexOrNull(value.trim())?.takeIf { it.length == 64 }
                ?: throw IllegalArgumentException("$source is not a valid secret key (expected nsec1… or 64-char hex)")
        return KeyPair(hex.hexToByteArray())
    }

    /** Suffix appended to `[admin].state_file` for the generated key file. */
    const val KEY_FILE_SUFFIX = ".relay-key"
}
