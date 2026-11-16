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
package com.vitorpamplona.amethyst.commons.service.upload

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import java.io.File
import java.nio.file.Files

/**
 * Uploads a local directory (or single file) to a Blossom server and produces the NIP-5A
 * `path → sha256` tags a static-site / napplet manifest needs. Each file is content-addressed by its
 * own sha256, signed-uploaded via [BlossomClient] + [BlossomAuth], and mapped to an absolute web path
 * (`/index.html`, `/assets/app.js`, …) so the same tree resolves the same way the host serves it.
 *
 * This is the reusable "ship a directory" half of publishing; the caller turns [Result.pathTags] into
 * the actual NIP-5A/5D event (kind 15128/35128 or 15129/35129), signs and broadcasts it. Lives next
 * to [BlossomClient] in `jvmMain` so the CLI and the desktop app share one upload path.
 */
class StaticSitePublisher(
    private val client: BlossomClient = BlossomClient(),
) {
    /** One uploaded file: its absolute web [path], content [sha256], byte [size], and Blossom [url]. */
    data class UploadedFile(
        val path: String,
        val sha256: String,
        val size: Long,
        val url: String?,
    )

    data class Result(
        val uploaded: List<UploadedFile>,
    ) {
        val pathTags: List<PathTag> get() = uploaded.map { PathTag(it.path, it.sha256) }
    }

    /**
     * Uploads every file under [source] (recursively; or [source] itself if it is a single file) to
     * [server], signing each BUD-02 upload with [signer]. Returns the per-file results. Throws if
     * [source] has no files or any upload fails (so a half-published manifest is never built).
     */
    suspend fun uploadTree(
        source: File,
        server: String,
        signer: NostrSigner,
    ): Result {
        val root = source.canonicalFile
        val files =
            when {
                root.isFile -> listOf(root)
                root.isDirectory ->
                    root
                        .walkTopDown()
                        // Never follow a link out of the published tree: a symlink
                        // to ~ would publish the home directory to a Blossom server.
                        .onEnter { !Files.isSymbolicLink(it.toPath()) }
                        .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) && it.canonicalFile.startsWith(root) }
                        .sortedBy { it.invariantPath() }
                        .toList()
                else -> throw IllegalArgumentException("No such file or directory: ${root.path}")
            }
        require(files.isNotEmpty()) { "No files to upload under ${root.path}" }

        val uploaded =
            files.map { file ->
                val webPath = webPath(root, file)
                val bytes = file.readBytes()
                val hash = sha256(bytes).toHexKey()
                val mime = runCatching { Files.probeContentType(file.toPath()) }.getOrNull() ?: "application/octet-stream"
                val auth = BlossomAuth.createUploadAuth(hash, file.length(), "Upload ${file.name}", signer)
                // A republish sends unchanged files again; a server that already holds one may refuse it.
                val result = client.uploadOrHeld(hash, file.length(), mime, server) { client.upload(file, mime, server, auth) }
                require(result.sha256 == null || result.sha256.equals(hash, ignoreCase = true)) { "Upload server returned a different artifact hash" }
                UploadedFile(webPath, hash, file.length(), result.url)
            }
        return Result(uploaded)
    }

    companion object {
        /** The absolute web path a [file] is served under, relative to the published [root]. */
        fun webPath(
            root: File,
            file: File,
        ): String {
            if (root.isFile) return "/" + root.name
            val rel =
                root
                    .toPath()
                    .relativize(file.toPath())
                    .toString()
                    .replace(File.separatorChar, '/')
            return "/" + rel.removePrefix("/")
        }

        private fun File.invariantPath() = path.replace(File.separatorChar, '/')
    }
}
