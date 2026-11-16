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
package com.vitorpamplona.amethyst.commons.nip34Git

import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip34Git.git.GitHttpAuth
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * GRASP-08 auth for the clone URLs of private repositories the cache knows (an announcement
 * tagged `["private", "true"]`): their git requests carry the user's signed `Authorization`;
 * every other repository's go without one, so nothing is signed for a public server.
 *
 * Only a URL under the announcer's own GRASP path (`https://<server>/<their npub>/…`) counts: a
 * stranger's announcement can call any URL private, and signing for it would tell that host who
 * the user is. Each URL's answer is kept for [rememberSeconds], so a fetch's many requests (blame,
 * search) do not each walk every repository in the cache; a "not private" for only
 * [rememberNegativeSeconds], since the announcement that makes it private may still be arriving.
 */
class GitPrivateRepositoryAuth(
    signer: NostrSigner,
    private val cache: EventCache = LocalCache,
    private val rememberSeconds: Long = 30,
    private val rememberNegativeSeconds: Long = 3,
) : GitHttpAuth(signer) {
    private val answers = ConcurrentHashMap<String, Pair<Long, Boolean>>()

    override suspend fun header(cloneUrl: String): String? = if (isPrivate(cloneUrl)) super.header(cloneUrl) else null

    private fun isPrivate(cloneUrl: String): Boolean {
        val wanted = repositoryUrl(cloneUrl).removeSuffix(".git")
        val now = TimeUtils.now()
        answers[wanted]?.let { (at, answer) -> if (now - at < if (answer) rememberSeconds else rememberNegativeSeconds) return answer }
        val answer = findPrivate(wanted)
        answers[wanted] = now to answer
        return answer
    }

    private fun findPrivate(wanted: String): Boolean {
        var found = false
        cache.gitRepositories.forEach { _, repository ->
            if (found) return@forEach
            found =
                repository.announcements().values.any { announcement ->
                    announcement.isPrivate() &&
                        announcement.clones().any { clone ->
                            val url = repositoryUrl(clone).removeSuffix(".git")
                            url == wanted && isOwnGraspPath(url, announcement.pubKey)
                        }
                }
        }
        return found
    }

    /** True when [url]'s path starts with [author]'s npub: the GRASP layout, where only they decide what is private. */
    private fun isOwnGraspPath(
        url: String,
        author: String,
    ): Boolean {
        val path = url.substringAfter("://", url).substringAfter('/', "")
        return path.substringBefore('/') == Hex.decode(author).toNpub()
    }
}
