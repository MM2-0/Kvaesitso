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

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSite
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

actual fun observerSiteIO(
    account: Account,
    http: IRoleBasedHttpClientBuilder,
): ObserverSiteIO? = AccountObserverSiteIO(account, http)

/**
 * The Observer site through this account: its outbox relays for the manifest, its Blossom
 * servers (kind 10063 only: never a server the reader didn't choose) for the files, the same
 * BUD-02 upload with kind 24242 auth that every other upload uses, and the account's broadcaster.
 */
class AccountObserverSiteIO(
    private val account: Account,
    private val http: IRoleBasedHttpClientBuilder,
) : ObserverSiteIO {
    override suspend fun latestSite(author: HexKey): SiteRead {
        val filter = Filter(kinds = listOf(NamedSiteEvent.KIND), authors = listOf(author), tags = mapOf("d" to listOf(ObserverSite.D_TAG)))
        val relays = account.outboxRelays.flow.first()
        val result =
            try {
                account.client.fetchAllWithHooks(relays.associateWith { listOf(filter) }, idleTimeoutMs = 10_000) { relay, event ->
                    account.cache.justConsume(event, relay, wasVerified = false)
                    true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the Observer site", e)
                null
            }
        val fetched = result?.events?.map { it.second }.orEmpty()
        val cached = account.cache.getAddressableNoteIfExists(ObserverSite.address(author))?.event
        val newest =
            (fetched + listOfNotNull(cached))
                .filterIsInstance<NamedSiteEvent>()
                .filter { it.pubKey == author && it.identifier() == ObserverSite.D_TAG }
                .maxByOrNull { it.createdAt }
        return when {
            newest != null -> SiteRead.Found(newest)
            result?.anyRelayServed == true -> SiteRead.None
            else -> SiteRead.Unreadable
        }
    }

    /**
     * The runner's site for this reader, from the reader's outbox relays and the runner's own (its
     * NIP-65, when this device has it), through the same read as the reader's own site. Only a
     * site [ObserverSite.isRunnerSiteFor] accepts counts: by [runner], `d=observer`, tagging [reader].
     */
    override suspend fun latestRunnerSite(
        runner: HexKey,
        reader: HexKey,
    ): SiteRead {
        val filter = ObserverSite.runnerSiteFilter(runner, reader)
        val relays =
            account.outboxRelays.flow.first() +
                account.cache
                    .getUserIfExists(runner)
                    ?.outboxRelays()
                    .orEmpty()
        val result =
            try {
                account.client.fetchAllWithHooks(relays.associateWith { listOf(filter) }, idleTimeoutMs = 10_000) { relay, event ->
                    account.cache.justConsume(event, relay, wasVerified = false)
                    true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the runner's Observer site", e)
                null
            }
        val fetched = result?.events?.map { it.second }.orEmpty()
        val cached = account.cache.getAddressableNoteIfExists(ObserverSite.address(runner))?.event
        val newest = ObserverSite.runnerSite(fetched + listOfNotNull(cached), runner, reader)
        return when {
            newest != null -> SiteRead.Found(newest)
            result?.anyRelayServed == true -> SiteRead.None
            else -> SiteRead.Unreadable
        }
    }

    // Only the servers the reader chose: no kind 10063 means nothing is uploaded anywhere.
    override suspend fun servers(): List<String> = account.blossomServers.flow.value

    override suspend fun download(
        servers: List<String>,
        sha256: HexKey,
    ): ByteArray? {
        for (server in servers) {
            val url = BlossomServerUrl.blob(server, sha256)
            val bytes =
                try {
                    BlossomClient(http.okHttpClientForUploads(url)).download(url)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not download $sha256 from $server", e)
                    null
                } ?: continue
            // A Blossom server is untrusted: only the bytes that hash to the name are the file.
            if (ObserverPublisher.sha256Hex(bytes).equals(sha256, ignoreCase = true)) return bytes
            Log.w(TAG) { "$server served the wrong bytes for $sha256" }
        }
        return null
    }

    override suspend fun upload(
        servers: List<String>,
        bytes: ByteArray,
        sha256: HexKey,
        contentType: String,
    ): Boolean {
        val auth = account.createBlossomUploadAuth(sha256, bytes.size.toLong(), "Upload $sha256").toAuthorizationHeader()
        return coroutineScope {
            servers
                .map { server ->
                    async {
                        try {
                            val result = BlossomClient(http.okHttpClientForUploads(server)).upload(bytes = bytes, contentType = contentType, serverBaseUrl = server, authHeader = auth)
                            result.url != null && (result.sha256 == null || result.sha256.equals(sha256, ignoreCase = true))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Upload of $sha256 to $server failed", e)
                            false
                        }
                    }
                }.awaitAll()
                .any { it }
        }
    }

    override suspend fun maxMessageLengths(): List<Int?> =
        account.outboxRelays.flow
            .first()
            .map {
                account.cache.appHost.nip11Cache
                    .peek(it)
                    ?.limitation
                    ?.max_message_length
            }

    override suspend fun maxEventTags(): List<Int?> =
        account.outboxRelays.flow
            .first()
            .map {
                account.cache.appHost.nip11Cache
                    .peek(it)
                    ?.limitation
                    ?.max_event_tags
            }

    override suspend fun publish(template: EventTemplate<NamedSiteEvent>): NamedSiteEvent {
        val signed = account.signer.sign(template)
        account.sendAutomatic(signed)
        return signed
    }

    companion object {
        private const val TAG = "AccountObserverSiteIO"
    }
}
