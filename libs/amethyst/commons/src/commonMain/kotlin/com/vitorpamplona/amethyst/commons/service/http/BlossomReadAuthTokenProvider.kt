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
package com.vitorpamplona.amethyst.commons.service.http

import com.vitorpamplona.amethyst.commons.service.upload.BlossomAuth
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Signs and caches BUD-01 read-auth headers for auth-gated Blossom hosts.
 *
 * Signing never blocks a caller's thread. It runs on [scope]; callers either
 * `suspend` on [header] or fire [warm] and pick the token up later. That
 * matters because the consumer used to be [BlossomReadAuthInterceptor], which
 * runs on an OkHttp dispatcher thread — bridging the suspend signer with
 * `runBlocking` there held one of the 16 per-host dispatcher slots for as long
 * as the signer took (up to the timeout), so a feed's first burst against a
 * gated host could occupy every slot and stall every other image from it.
 *
 * One signature per host, however many callers. The token cache alone couldn't
 * provide that: it is only populated *after* a signature returns, so a cold
 * burst of N images all missed it and all signed concurrently — with a NIP-55
 * external signer that meant N IPC round trips (and potentially N prompts).
 * [inFlight] is what collapses them; the leader signs and every follower awaits
 * the same [CompletableDeferred].
 *
 * Tokens are cached per host, not per blob, and are therefore minted with a
 * BUD-11 `server` tag and **no** `x` tag. BUD-11 lists `x` as optional for
 * `GET /<sha256>` but is strict about what including one means: "When `x` tags
 * are present, the token is only valid for operations on the specified blob
 * hashes." A token carrying the hash of whichever blob happened to trigger
 * signing would therefore be invalid for every other blob it was reused for.
 * Server-scoped and hash-free, one signed event legitimately covers a whole
 * feed's worth of images from the host for the life of the token.
 *
 * The tradeoff that buys: the token authorizes reading any blob on that host
 * until it expires, rather than one. It is only ever sent to that host, over
 * TLS, and BUD-11 sanctions the shape — but it is a wider grant than a
 * per-blob token, which is the price of caching at all.
 */
class BlossomReadAuthTokenProvider(
    private val signerProvider: () -> NostrSigner?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { TimeUtils.nowMillis() },
) {
    private class CachedToken(
        val header: String,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentMap<String, CachedToken>()
    private val inFlight = ConcurrentMap<String, CompletableDeferred<String?>>()

    /**
     * The token already held for [host], or null. Pure map read — safe to call
     * from an OkHttp interceptor, and never signs.
     */
    fun cachedHeader(host: String): String? = cache[host]?.takeIf { it.expiresAtMs > clock() }?.header

    /**
     * The token for [host], signing one if none is cached. Suspends rather than
     * blocking, so the caller must already be in a coroutine — on the image path
     * that is Coil's `Fetcher.fetch()`.
     */
    suspend fun header(host: String): String? {
        cachedHeader(host)?.let { return it }
        return signOnce(host)?.await()
    }

    /**
     * Starts a signature for [host] without waiting for it. For callers that
     * cannot suspend (the interceptor) and only need the token to exist by the
     * time some later request needs it.
     */
    fun warm(host: String) {
        if (cachedHeader(host) != null) return
        signOnce(host)
    }

    /**
     * Returns the in-flight signature for [host], starting one if this caller
     * wins the race — or an already-completed one carrying the token a leader
     * cached while this caller was on its way in. Null when there is no signer
     * to sign with.
     *
     * Leader/follower over [ConcurrentHashMap.putIfAbsent] rather than
     * `computeIfAbsent`: the completion handler removes the map entry, and a job
     * that finishes immediately would run that removal *inside* the mapping
     * function, which `ConcurrentHashMap` forbids.
     */
    private fun signOnce(host: String): CompletableDeferred<String?>? {
        inFlight[host]?.let { return it }

        // Second look at the cache, because the caller's own miss happened before this
        // read and a leader caches its token *before* retiring its [inFlight] entry — so
        // an absent entry here means any token that leader minted is already visible.
        // Without this look, a signature fast enough to finish inside that gap (a local
        // key signs in microseconds) loses the single-flight guarantee: every straggler
        // still between its cache miss and this read finds both empty and signs again,
        // which is the N-signatures burst [inFlight] exists to collapse.
        cachedHeader(host)?.let { return CompletableDeferred<String?>(it) }

        val signer = signerProvider() ?: return null

        val fresh = CompletableDeferred<String?>()
        inFlight.putIfAbsent(host, fresh)?.let { return it }

        // Third look, now that this caller owns the slot. The look above still leaves a
        // gap: a leader can insert, sign, cache and retire its entry entirely between
        // that read and the putIfAbsent, so the map is empty again and this caller wins
        // it. Any leader that retired before this insert cached first, so a token
        // present now is theirs — take it and give the slot back instead of re-signing.
        cachedHeader(host)?.let {
            inFlight.remove(host, fresh)
            fresh.complete(it)
            return fresh
        }

        scope
            .launch {
                val header =
                    try {
                        withTimeoutOrNull(SIGN_TIMEOUT_MS) {
                            BlossomAuth.createGetAuth(
                                // No `x` tag: this token is reused for every blob
                                // on the host. See the class kdoc.
                                hash = null,
                                alt = "Downloading media from $host",
                                signer = signer,
                                servers = listOf(host),
                            )
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    }

                if (header != null) {
                    cache[host] = CachedToken(header, clock() + CACHE_TTL_MS)
                }

                // Retire the entry *before* completing it. `invokeOnCompletion` fires when the
                // job ends, which is after `complete()` resumes the awaiting caller — so a
                // caller that returned from `header()` could come straight back, find this
                // finished deferred still in the map, and be handed its already-signed token
                // instead of signing a new one. A caller whose token has just expired does
                // exactly that, and got the expired token back for as long as the window
                // lasted — [refreshesAfterExpiry] closes it immediately and so hit it every run.
                inFlight.remove(host, fresh)
                fresh.complete(header)
            }.invokeOnCompletion {
                inFlight.remove(host, fresh)
                // No-op when the job completed normally; releases followers when
                // it was cancelled (scope torn down) instead of hanging them.
                fresh.complete(null)
            }

        return fresh
    }

    companion object {
        // The signed event expires one hour out (BlossomAuthorizationEvent), so
        // refresh a little early to avoid handing over a token that dies mid-flight.
        private const val CACHE_TTL_MS = 55L * 60L * 1000L

        // Bounds how long an image may wait on a slow signer. No thread is held
        // for this window any more — only the waiting coroutine.
        private const val SIGN_TIMEOUT_MS = 8_000L
    }
}
