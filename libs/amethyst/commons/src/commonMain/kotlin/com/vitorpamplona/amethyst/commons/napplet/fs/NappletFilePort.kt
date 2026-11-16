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
package com.vitorpamplona.amethyst.commons.napplet.fs

/**
 * The NAP-FS error codes (`FsError`). Every failed `fs.*` result carries exactly one of these.
 */
object NappletFsError {
    const val NOT_FOUND = "not-found"
    const val ALREADY_EXISTS = "already-exists"
    const val NOT_A_FILE = "not-a-file"
    const val NOT_A_DIRECTORY = "not-a-directory"
    const val INVALID_PATH = "invalid-path"
    const val INVALID_DATA = "invalid-data"
    const val PERMISSION_DENIED = "permission-denied"
    const val POLICY_DENIED = "policy-denied"
    const val QUOTA_EXCEEDED = "quota-exceeded"
    const val TOO_LARGE = "too-large"
    const val UNSUPPORTED = "unsupported"
    const val CONFLICT = "conflict"
    const val CANCELLED = "cancelled"
    const val IO_ERROR = "io-error"
}

/**
 * A failure a [NappletFilePort] reports with its NAP-FS [code]. Anything else a port throws is
 * reported to the napplet as [NappletFsError.IO_ERROR], and its message never leaves the host.
 */
class NappletFsException(
    val code: String,
    message: String? = null,
) : Exception(message ?: code)

/** NAP-FS `FsPermission`. */
enum class NappletFsPermission(
    val wire: String,
) {
    READ("read"),
    WRITE("write"),
    CREATE("create"),
    DELETE("delete"),
    LIST("list"),
    WATCH("watch"),
    ;

    companion object {
        fun fromWire(value: String): NappletFsPermission? = entries.firstOrNull { it.wire == value }
    }
}

/** One advisory `FsAcceptRule`: a MIME type or an extension (with its dot) the picker may filter on. */
data class NappletFsAccept(
    val mime: String? = null,
    val extension: String? = null,
) {
    /** The rule as an HTML `accept` token, the form the platforms' picker filters already read. */
    fun token(): String? = mime?.takeIf { it.isNotBlank() } ?: extension?.takeIf { it.isNotBlank() }?.let { if (it.startsWith(".")) it else ".$it" }
}

/**
 * `FsPickOptions`: hints from the napplet, never authority. [permissions] is the raw requested
 * intent (unknown values are ignored); the runtime decides what it grants.
 */
data class NappletFsPickOptions(
    val permissions: List<String>? = null,
    val accept: List<NappletFsAccept> = emptyList(),
    val suggestedName: String? = null,
    val description: String? = null,
)

/**
 * A file the user chose in the platform's picker. [handle] is how the platform reaches it again (a
 * content URI, an absolute path) and never leaves the host: the napplet only ever sees the virtual
 * path the session assigns. [name] is the platform's display name, sanitized before use.
 */
class NappletPickedFile(
    val handle: String,
    val name: String,
    val size: Long? = null,
    val writable: Boolean = false,
)

/** What one [NappletFilePort.read] returns: the [bytes] read, and the file's [size] when known. */
class NappletFsChunk(
    val bytes: ByteArray,
    val size: Long?,
)

/** How a write lands in the file. */
sealed interface NappletFsWriteMode {
    /** The file becomes exactly the written bytes. */
    data object Replace : NappletFsWriteMode

    /** The bytes go after the file's current end. */
    data object Append : NappletFsWriteMode

    /** The bytes overwrite the file from [offset] on, growing it when they run past its end. */
    data class Patch(
        val offset: Long,
    ) : NappletFsWriteMode
}

/**
 * The platform half of NAP-FS: the system file pickers and the bytes behind what the user picked.
 * The shared [NappletFsSession] does everything else (virtual paths, permissions, limits, wire errors),
 * so a port only shows a picker and moves bytes. Each call may throw [NappletFsException]; anything
 * else becomes `io-error`.
 */
interface NappletFilePort {
    /**
     * Lets the user choose one file, or several when [multiple], to read. Returns null when the user
     * cancelled. [options] are advisory: the picker may show its filters and description.
     */
    suspend fun pickOpen(
        options: NappletFsPickOptions,
        multiple: Boolean,
    ): List<NappletPickedFile>?

    /** Lets the user choose or name one file to write. Returns null when the user cancelled. */
    suspend fun pickSave(options: NappletFsPickOptions): NappletPickedFile?

    /** The current size of [handle] in bytes, or null when the platform cannot tell. */
    suspend fun size(handle: String): Long?

    /**
     * Up to [length] bytes of [handle] from [offset] (fewer only at the end of the file, none past it),
     * with the file's size when the platform can tell, read from the same opening of the file.
     */
    suspend fun read(
        handle: String,
        offset: Long,
        length: Int,
    ): NappletFsChunk

    /**
     * Writes [data] to [handle] as [mode] says and returns the file's resulting size, or null when the
     * platform cannot tell. All of [data] lands, or the call throws and leaves the file as it was as
     * far as the platform allows.
     */
    suspend fun write(
        handle: String,
        data: ByteArray,
        mode: NappletFsWriteMode,
    ): Long?
}
