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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverArchive
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverEdition
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverEditions
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverEditionsCodec
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSite
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * What publishing to an Observer site needs from the outside world: the author's newest site,
 * their Blossom servers, the write relays' size limits, and signing and sending. One
 * implementation per platform; tests use fakes.
 */
interface ObserverSiteIO {
    /**
     * The author's newest kind 35128 `d=observer`: across every write relay, read to EOSE, and
     * the client's cached copy. [SiteRead.Unreadable] when no relay answered and nothing is
     * cached, so a publish never starts from nothing because the network was down.
     */
    suspend fun latestSite(author: HexKey): SiteRead

    /** The Blossom servers the author chose (kind 10063), in their order; never a default. */
    suspend fun servers(): List<String>

    /** The blob [sha256] from the first of [servers] that serves it with that hash; null when none does. */
    suspend fun download(
        servers: List<String>,
        sha256: HexKey,
    ): ByteArray?

    /**
     * Uploads [bytes] (hash [sha256]) to every one of [servers] at once, under one upload auth
     * naming that hash; true when at least one accepted it.
     */
    suspend fun upload(
        servers: List<String>,
        bytes: ByteArray,
        sha256: HexKey,
        contentType: String,
    ): Boolean

    /** Each write relay's NIP-11 `max_message_length`, null where unknown. */
    suspend fun maxMessageLengths(): List<Int?>

    /** Each write relay's NIP-11 `max_event_tags`, null where unknown. */
    suspend fun maxEventTags(): List<Int?>

    /** Signs [template] as the author and sends it to their write relays. */
    suspend fun publish(template: EventTemplate<NamedSiteEvent>): NamedSiteEvent

    /**
     * The newest Observer site [runner] publishes for [reader] (NIP-FB, Runner: kind 35128 by
     * [runner], `d=observer`, tagging [reader]; [ObserverSite.runnerSiteFilter]), checked with
     * [ObserverSite.isRunnerSiteFor]: a site by any other key that tags the reader is never read.
     * [SiteRead.None] where the platform reads no runner sites.
     */
    suspend fun latestRunnerSite(
        runner: HexKey,
        reader: HexKey,
    ): SiteRead = SiteRead.None
}

/** One page to put on the site: its index entry and its HTML. */
class ObserverPage(
    val edition: ObserverEdition,
    val html: String,
)

/** One read of the author's site. */
sealed interface SiteRead {
    data class Found(
        val site: NamedSiteEvent,
    ) : SiteRead

    /** The relays answered and none has a site: the first publish starts one. */
    data object None : SiteRead

    /** No relay answered and nothing is cached: whatever is there is unknown. */
    data object Unreadable : SiteRead
}

sealed interface ObserverPublishResult {
    data class Published(
        val site: NamedSiteEvent,
        val kept: List<ObserverEdition>,
    ) : ObserverPublishResult

    data class Failed(
        val reason: ObserverFailure,
    ) : ObserverPublishResult
}

enum class ObserverFailure {
    /** No write relay answered the read: publishing could drop editions it doesn't know of. */
    READ,

    /** The reader has no Blossom server list (kind 10063). Nothing goes to a server they didn't choose. */
    NO_SERVERS,
    UPLOAD,
    PUBLISH,
}

/**
 * Publishes a reader's front pages to their Observer site and reads it back
 * (`quartz/plans/2026-11-11-observer-site.md`). Every publish is a read-modify-write of the
 * newest manifest, so editions printed on another device or app survive it. Nothing is
 * published unless every new page uploaded: a failure leaves the site as it was, and the local
 * editions stay to be retried with the next print.
 */
class ObserverPublisher(
    private val io: ObserverSiteIO,
    private val author: HexKey,
    private val displayName: () -> String?,
    private val now: () -> Long = { TimeUtils.now() },
) {
    /**
     * Puts [pages] on the site (replacing their days), rolls off the oldest, republishes. Each
     * page's HTML is published as given: the caller adds the paper's CSP ([withPaperCsp]) and
     * names it by those bytes' hash.
     */
    suspend fun publish(pages: List<ObserverPage>): ObserverPublishResult {
        val read = io.latestSite(author)
        if (read is SiteRead.Unreadable) return ObserverPublishResult.Failed(ObserverFailure.READ)
        val previous = (read as? SiteRead.Found)?.site
        val servers = io.servers()
        if (servers.isEmpty()) return ObserverPublishResult.Failed(ObserverFailure.NO_SERVERS)

        // Named by the bytes that go out, whatever the entry said.
        val named = pages.map { page -> page to page.edition.copy(sha256 = sha256Hex(page.html.encodeToByteArray())) }
        val uploaded =
            coroutineScope {
                named.map { (page, edition) -> async { io.upload(servers, page.html.encodeToByteArray(), edition.sha256, HTML) } }.awaitAll()
            }
        if (!uploaded.all { it }) return ObserverPublishResult.Failed(ObserverFailure.UPLOAD)

        var editions = previous?.let { editionsOf(it, servers) } ?: emptyList()
        named.forEach { (_, edition) -> editions = ObserverSite.upsert(editions, edition) }

        val name = displayName()
        val createdAt = ObserverSite.nextCreatedAt(previous, now())
        val kept =
            ObserverSite.keepWithinBudget(
                editions = editions,
                author = author,
                displayName = name,
                servers = servers,
                budgetBytes = ObserverSite.budget(io.maxMessageLengths()),
                maxTags = ObserverSite.tagBudget(io.maxEventTags()),
                protect = ObserverSite.utcDay(now()),
                createdAt = createdAt,
            )
        val title = ObserverSite.title(name)

        val archive = ObserverArchive.html(title, kept).encodeToByteArray()
        val archiveSha = sha256Hex(archive)
        val index = ObserverEditionsCodec.encode(ObserverEditions(title = title, editions = kept)).encodeToByteArray()
        val indexSha = sha256Hex(index)
        val filesUp =
            coroutineScope {
                listOf(
                    async { io.upload(servers, archive, archiveSha, HTML) },
                    async { io.upload(servers, index, indexSha, JSON) },
                ).awaitAll()
            }
        if (!filesUp.all { it }) return ObserverPublishResult.Failed(ObserverFailure.UPLOAD)

        val template = ObserverSite.build(kept, archiveSha, indexSha, name, servers, createdAt)
        return try {
            ObserverPublishResult.Published(io.publish(template), kept)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not publish the Observer site", e)
            ObserverPublishResult.Failed(ObserverFailure.PUBLISH)
        }
    }

    /** The newest site and its editions, newest first; null when the author has none, or it can't be read. */
    suspend fun read(): ObserverSiteView? {
        val site = (io.latestSite(author) as? SiteRead.Found)?.site ?: return null
        val servers = (site.servers() + io.servers()).distinct()
        return ObserverSiteView(site, editionsOf(site, servers), servers)
    }

    /**
     * The site the runner the reader's schedule names publishes for them ([ObserverSiteIO.latestRunnerSite])
     * and its editions, newest first; null when it has none, or it can't be read. Only [runner],
     * and only its site that tags this reader, ever counts.
     */
    suspend fun readRunner(runner: HexKey): ObserverSiteView? {
        val site = (io.latestRunnerSite(runner, author) as? SiteRead.Found)?.site ?: return null
        if (!ObserverSite.isRunnerSiteFor(site, runner, author)) return null
        // Its files are on the reader's own Blossom servers (the plan's rule for the runner), and on any it names.
        val servers = (site.servers() + io.servers()).distinct()
        return ObserverSiteView(site, editionsOf(site, servers), servers)
    }

    /** [edition]'s page, by hash, from [servers]; null when no server has the right bytes. */
    suspend fun page(
        edition: ObserverEdition,
        servers: List<String>,
    ): String? = io.download(servers, edition.sha256.lowercase())?.decodeToString()

    /**
     * The site's editions: its dated `path` tags, described by the `/editions.json` entry that
     * names the same bytes ([ObserverSite.mergeEditions]). The manifest is the truth about which
     * files exist; an index entry whose hash no longer matches is someone else's stale write.
     */
    private suspend fun editionsOf(
        site: NamedSiteEvent,
        servers: List<String>,
    ): List<ObserverEdition> {
        val paths = site.paths()
        val indexHash = paths.firstOrNull { it.path == ObserverSite.EDITIONS_PATH }?.hash?.lowercase()
        val fromIndex =
            indexHash?.let { hash ->
                io.download((site.servers() + servers).distinct(), hash)?.let { ObserverEditionsCodec.decode(it.decodeToString()) }
            }
        return ObserverSite.mergeEditions(fromIndex?.editions.orEmpty(), ObserverSite.editionsFromPaths(paths))
    }

    companion object {
        private const val TAG = "ObserverPublisher"
        const val HTML = "text/html"
        const val JSON = "application/json"

        fun sha256Hex(bytes: ByteArray): HexKey = sha256(bytes).toHexKey()

        /** The site entry for a kept edition: its UTC day, as every producer names it. */
        fun entryFor(
            edition: SavedEdition,
            sha256: HexKey,
        ): ObserverEdition {
            val day = ObserverSite.utcDay(edition.printedAt)
            return ObserverEdition(
                day = day,
                path = ObserverSite.dayPath(day),
                sha256 = sha256,
                headline = edition.headline,
                code = edition.code.ifBlank { null },
                printedAt = edition.printedAt,
                posts = edition.posts,
                quotesVerified = edition.quotes,
                model = edition.model.ifBlank { null },
            )
        }
    }
}

/** A read of the reader's own site. */
class ObserverSiteView(
    val site: NamedSiteEvent,
    val editions: List<ObserverEdition>,
    val servers: List<String>,
)

/**
 * The history the front page shows: the editions kept on this device, plus the site's editions
 * this device doesn't have, plus the editions on the site of the runner the reader's schedule
 * names ([runnerSite], NIP-FB, Runner) that neither has, marked [SavedEdition.byRunner]. An edition
 * is the same one when its hash matches; a day this device also printed (by its own local day)
 * stays the local one, and a day on the reader's own site stays the reader's.
 */
fun mergeHistory(
    local: List<SavedEdition>,
    site: List<ObserverEdition>,
    runnerSite: List<ObserverEdition> = emptyList(),
): List<SavedEdition> {
    val localShas = local.mapNotNull { it.sha256 }.toSet()
    val localDays = local.map { it.day }.toSet()
    val fromSite = site.filter { it.sha256 !in localShas && it.day !in localDays }.map { savedFromSite(it, byRunner = false) }
    val siteShas = site.map { it.sha256 }.toSet()
    val siteDays = site.map { it.day }.toSet()
    val fromRunner =
        runnerSite
            .filter { it.sha256 !in localShas && it.sha256 !in siteShas && it.day !in localDays && it.day !in siteDays }
            .map { savedFromSite(it, byRunner = true) }
    return (local + fromSite + fromRunner).sortedByDescending { it.day }
}

/** A site's [edition] as a kept one: published, by the reader or by their runner. */
fun savedFromSite(
    edition: ObserverEdition,
    byRunner: Boolean,
) = SavedEdition(
    day = edition.day,
    code = edition.code.orEmpty(),
    printedAt = edition.printedAt ?: 0,
    model = edition.model.orEmpty(),
    posts = edition.posts ?: 0,
    quotes = edition.quotesVerified ?: 0,
    headline = edition.headline,
    sha256 = edition.sha256,
    published = true,
    byRunner = byRunner,
)
