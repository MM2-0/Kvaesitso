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
package com.vitorpamplona.geode.grasp

import com.vitorpamplona.geode.server.RelayHttpExtension
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip34Git.git.GitHttpAuth
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import com.vitorpamplona.quartz.nip98HttpAuth.Nip98AuthVerifier
import com.vitorpamplona.quartz.utils.Log
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * A development GRASP server's git half, on the relay's own port: `/<npub>/<repo>.git/…` is served
 * by `git http-backend` (run as a CGI subprocess, never linked) from [root], for repositories
 * announced to this relay. It enforces what a GRASP server must:
 *
 * - anyone may push `refs/nostr/<event-id>` (a pull request's commits, GRASP-01);
 * - a branch or tag push must move the ref to what the newest kind-30618 state of one of the
 *   repository's maintainers says (so only maintainers move branches, by signing first);
 * - a private repository (`["private","true"]`, GRASP-08) wants, on every request, a NIP-98
 *   `Authorization` of kind 27235 for the repository URL (method GET), signed by a maintainer.
 *
 * For tests and local development: it trusts its own relay's store and keeps no other state.
 * Repositories are created on first use, bare, with pushes and partial fetches enabled.
 */
class GraspGitHost(
    private val root: File,
    private val store: IEventStore,
    /** The server's base URL as clients write it, e.g. `http://127.0.0.1:7447`. */
    private val publicBase: () -> String,
    private val git: String = "git",
) : RelayHttpExtension {
    private val verifier = Nip98AuthVerifier()

    override fun install(route: Route) {
        route.get("/{npub}/{repo}/{rest...}") { serve(call) }
        route.post("/{npub}/{repo}/{rest...}") { serve(call) }
    }

    private suspend fun serve(call: ApplicationCall) {
        val npub = call.parameters["npub"].orEmpty()
        val repo = call.parameters["repo"].orEmpty()
        val rest =
            call.parameters
                .getAll("rest")
                .orEmpty()
                .joinToString("/")
        // npub paths only: a hex owner would be a second directory for the same repository.
        val owner = if (npub.startsWith("npub1")) decodePublicKeyAsHexOrNull(npub) else null
        if (owner == null || !repo.endsWith(".git") || repo.contains("..") || rest.contains("..")) {
            return call.respondText("not a repository", status = HttpStatusCode.NotFound)
        }
        val identifier = repo.removeSuffix(".git")
        val announcement =
            store
                .query<GitRepositoryEvent>(Filter(kinds = listOf(GitRepositoryEvent.KIND), authors = listOf(owner), tags = mapOf("d" to listOf(identifier))))
                .maxByOrNull { it.createdAt }
                ?: return call.respondText("no announcement for $npub/$identifier on this relay", status = HttpStatusCode.NotFound)
        val maintainers = (listOf(announcement.pubKey) + announcement.maintainers()).toSet()

        if (announcement.isPrivate()) {
            val url = GitHttpAuth.repositoryUrl("${publicBase().trimEnd('/')}/$npub/$repo")
            val result = verifier.preverify(call.request.headers["Authorization"], "GET", url)
            val signer = (result as? Nip98AuthVerifier.Result.Verified)?.pubkey
            if (signer == null || signer !in maintainers) {
                call.response.header("WWW-Authenticate", "Nostr")
                return call.respondText("private repository: sign in (GRASP-08)", status = HttpStatusCode.Unauthorized)
            }
        }

        val declared = call.request.headers["Content-Length"]?.toLongOrNull()
        if (declared != null && declared > MAX_BODY_BYTES) return call.respondText("too large", status = HttpStatusCode.PayloadTooLarge)
        val body = if (call.request.httpMethod.value == "POST") call.receive<ByteArray>() else ByteArray(0)
        if (body.size > MAX_BODY_BYTES) return call.respondText("too large", status = HttpStatusCode.PayloadTooLarge)
        val encoding =
            call.request.headers["Content-Encoding"]
                ?.trim()
                ?.lowercase()
        var state: GitRepositoryStateEvent? = null
        if (rest == "git-receive-pack") {
            // git http-backend inflates gzip and x-gzip alike: the check must read what it will.
            val commandBytes =
                when (encoding) {
                    null, "", "identity" -> body
                    "gzip", "x-gzip" -> gunzipHead(body) ?: return call.respondText("bad gzip", status = HttpStatusCode.BadRequest)
                    else -> return call.respondText("unsupported Content-Encoding $encoding", status = HttpStatusCode.UnsupportedMediaType)
                }
            val commands = commandsOf(commandBytes)
            // A push the check cannot read is refused, never let through unchecked.
            if (commands.isEmpty() && commandBytes.isNotEmpty()) return call.respondText("unreadable push", status = HttpStatusCode.BadRequest)
            state = signedState(identifier, maintainers)
            val refusal = refuse(commands, state)
            if (refusal != null) return call.respondText(refusal, status = HttpStatusCode.Forbidden)
        }

        val output =
            withContext(Dispatchers.IO) {
                ensureRepository(File(File(root, npub), repo))
                runBackend(
                    pathInfo = "/$npub/$repo/$rest",
                    method = call.request.httpMethod.value,
                    query =
                        call.request.local.uri
                            .substringAfter('?', ""),
                    contentType = call.request.headers["Content-Type"],
                    encoding = encoding,
                    protocol = call.request.headers["Git-Protocol"],
                    body = body,
                )
            }
        if (output == null) return call.respondText("git http-backend failed for $npub/$repo", status = HttpStatusCode.InternalServerError)
        // HEAD follows the maintainers' signed state, so a clone checks out their default branch.
        state?.head()?.let { head -> withContext(Dispatchers.IO) { runCatching { run(File(File(root, npub), repo), "symbolic-ref", "HEAD", "refs/heads/$head") } } }
        respondCgi(call, output)
    }

    /** The newest state one of [maintainers] signed for [identifier]. */
    private suspend fun signedState(
        identifier: String,
        maintainers: Set<HexKey>,
    ): GitRepositoryStateEvent? =
        store
            .query<GitRepositoryStateEvent>(Filter(kinds = listOf(GitRepositoryStateEvent.KIND), authors = maintainers.toList(), tags = mapOf("d" to listOf(identifier))))
            .maxByOrNull { it.createdAt }

    /** Why a push is refused, or null: each ref update checked against GRASP's rules and the [signed] state. */
    private fun refuse(
        commands: List<Triple<String, String, String>>,
        signed: GitRepositoryStateEvent?,
    ): String? {
        for ((old, new, ref) in commands) {
            if (ref.startsWith("refs/nostr/")) {
                if (!ref.removePrefix("refs/nostr/").matches(HEX64)) return "refs/nostr/ takes an event id: $ref"
                // Anyone may create one; nobody may move or delete another's.
                if (old != ZERO || new == ZERO) return "$ref can only be created"
                continue
            }
            if (!ref.startsWith("refs/heads/") && !ref.startsWith("refs/tags/")) return "only branches, tags and refs/nostr/<id> are pushed here: $ref"
            val expected = signed?.refs()?.firstOrNull { it.name == ref }?.commit
            val deleting = new == ZERO
            if (deleting && expected != null) return "$ref is in the maintainers' signed state: delete it there first"
            if (!deleting && expected != new) return "$ref must match the maintainers' signed state (${expected ?: "none"}), not $new"
        }
        return null
    }

    /** The ref updates at the head of a receive-pack request: `old new ref` pkt-lines up to the flush. */
    private fun commandsOf(body: ByteArray): List<Triple<String, String, String>> {
        val commands = ArrayList<Triple<String, String, String>>()
        var at = 0
        while (at + 4 <= body.size) {
            val length = body.decodeToString(at, at + 4).toIntOrNull(16) ?: break
            if (length == 0) break
            if (length < 4 || at + length > body.size) break
            val line = body.decodeToString(at + 4, at + length).substringBefore('\u0000').trimEnd('\n')
            val parts = line.split(' ')
            if (parts.size >= 3) commands.add(Triple(parts[0], parts[1], parts[2]))
            at += length
        }
        return commands
    }

    /** The start of a gzipped body, enough for the command section: never the whole of a gzip bomb. */
    private fun gunzipHead(body: ByteArray): ByteArray? =
        runCatching {
            GZIPInputStream(ByteArrayInputStream(body)).use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (out.size() < MAX_COMMAND_BYTES) {
                    val read = input.read(buffer, 0, minOf(buffer.size, MAX_COMMAND_BYTES - out.size()))
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        }.getOrNull()

    private fun ensureRepository(directory: File): File {
        if (File(directory, "HEAD").exists()) return directory
        check(directory.mkdirs() || directory.isDirectory) { "cannot create $directory" }
        run(directory, "init", "--bare", "-q")
        run(directory, "config", "http.receivepack", "true")
        run(directory, "config", "uploadpack.allowFilter", "true")
        run(directory, "config", "uploadpack.allowAnySHA1InWant", "true")
        return directory
    }

    private fun run(
        directory: File,
        vararg args: String,
    ) {
        val process = ProcessBuilder(listOf(git) + args).directory(directory).redirectErrorStream(true).start()
        val out = process.inputStream.readBytes().decodeToString()
        check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { "git ${args.joinToString(" ")}: $out" }
    }

    private fun runBackend(
        pathInfo: String,
        method: String,
        query: String,
        contentType: String?,
        encoding: String?,
        protocol: String?,
        body: ByteArray,
    ): ByteArray? {
        val builder = ProcessBuilder(git, "http-backend")
        val env = builder.environment()
        env["GIT_PROJECT_ROOT"] = root.absolutePath
        env["GIT_HTTP_EXPORT_ALL"] = "1"
        env["PATH_INFO"] = pathInfo
        env["REQUEST_METHOD"] = method
        env["QUERY_STRING"] = query
        env["REMOTE_USER"] = "grasp"
        env["REMOTE_ADDR"] = "127.0.0.1"
        env["CONTENT_LENGTH"] = body.size.toString()
        contentType?.let { env["CONTENT_TYPE"] = it }
        encoding?.let { env["HTTP_CONTENT_ENCODING"] = it }
        protocol?.let { env["GIT_PROTOCOL"] = it }
        val process = builder.start()
        val stderr = StringBuilder()
        val errors = Thread { stderr.append(process.errorStream.readBytes().decodeToString()) }.apply { start() }
        process.outputStream.use { it.write(body) }
        val out = process.inputStream.readBytes()
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            return null
        }
        errors.join(1000)
        if (stderr.isNotBlank()) Log.d(TAG) { "http-backend $pathInfo: $stderr" }
        return out.takeIf { it.isNotEmpty() }
    }

    /** Sends a CGI response: its `Status:` and `Content-Type:` headers, the rest as the body. */
    private suspend fun respondCgi(
        call: ApplicationCall,
        output: ByteArray,
    ) {
        var end = -1
        var skip = 0
        for (i in 0 until output.size - 1) {
            if (output[i] == '\n'.code.toByte() && output[i + 1] == '\n'.code.toByte()) {
                end = i
                skip = 2
                break
            }
            if (i + 3 < output.size && output[i] == '\r'.code.toByte() && output[i + 1] == '\n'.code.toByte() && output[i + 2] == '\r'.code.toByte() && output[i + 3] == '\n'.code.toByte()) {
                end = i
                skip = 4
                break
            }
        }
        if (end < 0) return call.respondBytes(output, ContentType.Application.OctetStream)
        var status = HttpStatusCode.OK
        var type = ContentType.Application.OctetStream
        output.decodeToString(0, end).lines().forEach { line ->
            val name = line.substringBefore(':').trim()
            val value = line.substringAfter(':', "").trim()
            when (name.lowercase()) {
                "status" -> value.substringBefore(' ').toIntOrNull()?.let { status = HttpStatusCode.fromValue(it) }
                "content-type" -> type = ContentType.parse(value)
                "" -> {}
                else -> call.response.header(name, value)
            }
        }
        call.respondBytes(output.copyOfRange(end + skip, output.size), type, status)
    }

    companion object {
        private const val TAG = "GraspGitHost"
        private const val ZERO = "0000000000000000000000000000000000000000"
        private val HEX64 = Regex("^[0-9a-f]{64}$")

        /** A dev server's ceiling for one request body: a push of a large repository fits, a runaway one does not. */
        private const val MAX_BODY_BYTES = 256L * 1024 * 1024

        /** The most of a gzipped push read to find its ref updates. */
        private const val MAX_COMMAND_BYTES = 1024 * 1024
    }
}
