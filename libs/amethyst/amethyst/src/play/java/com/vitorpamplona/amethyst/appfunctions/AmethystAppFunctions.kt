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
package com.vitorpamplona.amethyst.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionNotSupportedException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.actions.DmActions
import com.vitorpamplona.amethyst.commons.actions.FollowActions
import com.vitorpamplona.amethyst.commons.actions.SearchActions
import com.vitorpamplona.amethyst.commons.actions.ZapActions
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65RelaySet
import com.vitorpamplona.amethyst.commons.home.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.commons.model.payments.PaymentSource
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.service.ClinkDebitPayer
import com.vitorpamplona.amethyst.commons.service.lnurl.LightningAddressResolver
import com.vitorpamplona.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirmDetailed
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKey
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bridge that exposes Amethyst's "verbs" (commons/.../actions/) to the Android
 * App Functions runtime, which Gemini and other system agents can drive.
 *
 * **Status — pre-stable.** Built against androidx.appfunctions 1.0.0-alpha09.
 * The API is still moving; treat every release as ABI-breaking until 1.0.0
 * ships. Scoped to the `play` build flavor only — the F-Droid channel
 * ships without any Google AI dependencies.
 *
 * Plain class, no inheritance — the KSP compiler discovers `@AppFunction`
 * methods and generates the dispatcher glue (see
 * `amethyst/build/generated/ksp/playDebug/.../$AmethystAppFunctions_AppFunctionInvoker.kt`).
 * The generated invoker constructs this class via its default no-arg
 * constructor, so no `AppFunctionConfiguration.Provider` is required on
 * the Application. If we ever add an @AppFunction host class with
 * constructor parameters, we'll need to register a factory via Provider —
 * the docs nudge that direction, but the runtime does not require it for
 * default-constructed classes.
 *
 * Read verbs work with any account state. Write verbs (post / follow /
 * unfollow / sendDm) require a signer that can sign in-process — i.e.
 * a local [NostrSignerInternal] or a remote NIP-46 bunker. NIP-55
 * external signers (Amber) are refused with
 * [AppFunctionNotSupportedException] for now because the agent
 * dispatch happens outside the foreground task stack — the user can't
 * see the Amber approval activity from inside Gemini. See
 * `amethyst/plans/2026-05-25-appfunctions-signer-prompts.md` for the
 * design and the planned PendingIntent escape hatch.
 *
 * Account scoping uses the currently active account from
 * [com.vitorpamplona.amethyst.Amethyst.instance.sessionManager] — the same
 * Account the foreground UI is bound to. When no account is signed in,
 * every function returns an empty result rather than failing the call.
 */
class AmethystAppFunctions {
    /**
     * Find a person on Nostr by name, handle, or NIP-05. Use when the user
     * wants to look someone up on Nostr ("find vitor on nostr", "search for
     * jack dorsey", "who is alice@damus on nostr"), translate a display
     * name to an npub, or discover a user before following / DMing /
     * zapping them.
     *
     * Backed by NIP-50 full-text search across the active account's
     * configured search relays (kind:10007), with a fallback to
     * Amethyst's curated default search-relay set.
     *
     * Results carrying a NIP-05 claim are verified in parallel and dropped
     * when the claim explicitly fails — the listed domain doesn't sign for
     * that pubkey, or returns a different one. Profiles with no NIP-05 at
     * all are kept (no claim, nothing to refute). This is the
     * anti-impersonation guard for the downstream write verbs: if Gemini
     * asks the user "do you mean alice@damus.io?" the user should be able
     * to trust that the npub actually controls that handle.
     *
     * @param query free-form search text (display name, NIP-05 handle, etc.)
     * @param limit max number of profiles to return — capped to 50.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchProfiles(
        appFunctionContext: AppFunctionContext,
        query: String,
        limit: Int = 10,
    ): SearchProfilesResult {
        val cappedLimit = limit.coerceIn(1, 50)
        val filter = SearchActions.searchProfilesFilter(query, cappedLimit) ?: return SearchProfilesResult.empty()

        // Snapshot the active account + relay set + client at function entry
        // and never touch sessionManager again from this dispatch. If the
        // user switches account mid-fetch, this snapshot keeps the request
        // routed to the relays we originally queried — caller still gets a
        // coherent result rather than events mixed across accounts.
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchProfilesResult.empty()
        val client = Amethyst.instance.client.taggedAs(SubPurpose.SEARCH, ASSISTANT)

        // SearchRelayListState's flow already resolves to a concrete relay
        // set: NIP-44-decrypted private entries + public entries, or the
        // curated default set when the user has no kind:10007. Same source
        // of truth the foreground UI uses.
        val relays = account.searchRelayList.flow.value
        if (relays.isEmpty()) return SearchProfilesResult.empty()

        // Quartz's INostrClient.fetchAll handles subscribe → drain on
        // EOSE/closed/cannot-connect → unsubscribe → dedup by id → sort
        // newest-first. Wraps everything in a withTimeoutOrNull(idleTimeoutMs)
        // so a slow relay can't stall the dispatch.
        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                idleTimeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val candidates =
            events
                .mapNotNull { it as? MetadataEvent }
                .distinctBy { it.pubKey }
                .sortedByDescending { it.createdAt }
                .take(cappedLimit)

        // Verify NIP-05 claims in parallel with a bounded timeout, then
        // drop only the ones that explicitly fail (the domain returns a
        // different pubkey, or no entry for the local part). Network
        // errors / DNS failures / timeouts are inconclusive — keep those
        // so a flaky .well-known doesn't censor legitimate users.
        val verified =
            withTimeoutOrNull(NIP05_FILTER_TIMEOUT_MS) {
                coroutineScope {
                    candidates
                        .map { meta ->
                            async { meta to nip05IsExplicitlyFailed(meta) }
                        }.awaitAll()
                }
            } ?: candidates.map { it to false }

        val hits =
            verified
                .filterNot { (_, failed) -> failed }
                .map { (meta, _) -> meta.toProfileHit() }

        return SearchProfilesResult(matches = hits)
    }

    /**
     * Returns true only when the recipient's kind:0 carries a NIP-05 claim
     * that the listed domain actively refuses to sign for. Returns false
     * for "no claim", "unparseable claim", "network error", "namecoin
     * unreachable" — those are inconclusive, not refutations.
     */
    private suspend fun nip05IsExplicitlyFailed(meta: MetadataEvent): Boolean {
        val claim =
            meta
                .contactMetaData()
                ?.nip05
                ?.trim()
                .orEmpty()
        if (claim.isEmpty()) return false
        val parsed = Nip05Id.parse(claim) ?: return false
        return runCatchingCancellable {
            !Amethyst.instance.nip05Client.verify(parsed, meta.pubKey)
        }.getOrElse {
            // verify() throws IllegalStateException on network / parse
            // errors — treat as inconclusive rather than a failure.
            false
        }
    }

    /**
     * Read the user's Nostr timeline / home feed. Use when the user asks
     * "what's new on Nostr", "what's happening on Nostr today", "catch me
     * up on my Nostr feed", or wants a summary of recent posts from
     * people they follow.
     *
     * Drains recent kind:1 short text notes from the people the active
     * account follows; the same query the Amethyst home-feed UI runs,
     * truncated to one batch. Queries the account's home relays (NIP-65
     * outbox + any private storage + local relays).
     *
     * @param limit max notes to return, capped to 200. Default 30.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getRecentFromFollows(
        appFunctionContext: AppFunctionContext,
        limit: Int = 30,
    ): SearchNotesResult {
        val cappedLimit = limit.coerceIn(1, 200)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchNotesResult.empty()

        val events = fetchFollowFeed(account = account, sinceSecs = null, limit = cappedLimit)
        return SearchNotesResult(matches = events.map { it.toNoteHit() })
    }

    /**
     * Build a structured digest of the user's Nostr home feed for an
     * AI summary. Use when the user asks "summarize my Nostr feed",
     * "give me a digest of what my follows posted today", "recap
     * Nostr for me", "what have people been talking about on Nostr",
     * "summarize what's on my Nostr home screen", or any "summary /
     * digest / recap of my Nostr timeline" intent.
     *
     * **Mirrors the home page exactly.** Runs the same
     * `HomeNewThreadFeedFilter` the Amethyst home screen uses against
     * the local event cache — so the LLM sees what the user would see
     * if they opened the app: short text notes, reposts (deduped),
     * long-form articles, polls, comments, audio, classifieds,
     * highlights, and the rest. Respects the user's currently selected
     * NIP-51 follow list (not just plain kind:3), filters muted users,
     * and excludes replies (top-level threads only).
     *
     * Because the feed is read from the local cache rather than drained
     * fresh from relays, the digest reflects what the foreground app
     * has previously gathered — sparse if the app hasn't been opened
     * recently. Open Amethyst before asking the agent to summarise if
     * you want the freshest possible result.
     *
     * Returns the raw notes plus pre-extracted signals the LLM needs
     * to write a useful summary without re-deriving them: total note
     * count, unique author count, top hashtags in the window, and the
     * most-mentioned users (display names resolved from local kind:0
     * cache). The LLM composes the natural-language summary from
     * these.
     *
     * @param hoursBack window size in hours. Capped to 168 (7 days),
     *   default 12. Events older than this are excluded from both the
     *   stats and the body.
     * @param maxNotes max notes returned in the body. Capped to 200.
     *   Default 60 — big enough for a meaningful summary, small enough
     *   to fit comfortably in the LLM's prompt. Stats are computed
     *   over the full in-window set, not just the trimmed body.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getFeedDigest(
        appFunctionContext: AppFunctionContext,
        hoursBack: Int = 12,
        maxNotes: Int = 60,
    ): FeedDigestResult {
        val cappedHours = hoursBack.coerceIn(1, 24 * 7)
        val cappedMaxNotes = maxNotes.coerceIn(1, 200)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return FeedDigestResult.empty()

        val sinceSecs = TimeUtils.now() - cappedHours.toLong() * 3600L

        // Use the same filter the home screen uses, so the digest
        // mirrors what the user actually sees in the UI. The filter
        // reads from LocalCache (already maintained by the foreground
        // subscriptions) and applies the user's selected follow list,
        // mutes, repost dedup, and new-thread-only rule.
        val feed =
            HomeNewThreadFeedFilter(account)
                .feed()
                .asSequence()
                .mapNotNull { it.event }
                .filter { it.createdAt >= sinceSecs }
                .toList()

        // Hashtag frequencies — case-folded so `#Bitcoin` and
        // `#bitcoin` collapse to one bucket.
        val hashtagCounts = HashMap<String, Int>()
        // Mention frequencies, keyed by mentioned pubkey hex.
        val mentionCounts = HashMap<HexKey, Int>()
        val uniqueAuthors = HashSet<HexKey>()

        for (ev in feed) {
            uniqueAuthors.add(ev.pubKey)
            for (tag in ev.tags) {
                if (tag.size < 2) continue
                when (tag[0]) {
                    "t" -> {
                        val cleaned = tag[1].trim().removePrefix("#").lowercase()
                        if (cleaned.isNotEmpty()) {
                            hashtagCounts.merge(cleaned, 1, Int::plus)
                        }
                    }
                    "p" -> {
                        // Skip self-mentions (the author tags themself
                        // in some clients) — not useful for the digest.
                        if (tag[1].length == 64 && tag[1] != ev.pubKey) {
                            mentionCounts.merge(tag[1], 1, Int::plus)
                        }
                    }
                }
            }
        }

        val topHashtags =
            hashtagCounts.entries
                .sortedByDescending { it.value }
                .take(TOP_HASHTAGS_LIMIT)
                .map { HashtagFrequency(tag = it.key, noteCount = it.value) }

        val topMentions =
            mentionCounts.entries
                .sortedByDescending { it.value }
                .take(TOP_MENTIONS_LIMIT)
                .map { (pub, count) ->
                    MentionFrequency(
                        npub = NPub.create(pub),
                        pubkeyHex = pub,
                        displayName = displayNameOf(pub),
                        mentionCount = count,
                    )
                }

        return FeedDigestResult(
            windowHours = cappedHours,
            totalNoteCount = feed.size,
            uniqueAuthorCount = uniqueAuthors.size,
            topHashtags = topHashtags,
            topMentions = topMentions,
            // Truncate to the caller-requested limit for the body —
            // the LLM has the stats either way and doesn't need every
            // note quoted. Sorted newest-first.
            notes =
                feed
                    .sortedByDescending { it.createdAt }
                    .take(cappedMaxNotes)
                    .map { it.toFeedNoteHit() },
        )
    }

    /**
     * Shared core of [getRecentFromFollows] and [getFeedDigest]: drain
     * recent kind:1 notes from the active account's follow set,
     * optionally filtered by `since`. Returns empty when there's no
     * account, no follows, or no relays configured.
     */
    private suspend fun fetchFollowFeed(
        account: com.vitorpamplona.amethyst.commons.model.Account,
        sinceSecs: Long?,
        limit: Int,
    ): List<TextNoteEvent> {
        val authors = account.kind3FollowList.flow.value.authors
        if (authors.isEmpty()) return emptyList()

        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return emptyList()

        val filter =
            Filter(
                kinds = listOf(TextNoteEvent.KIND),
                authors = authors.toList(),
                since = sinceSecs,
                limit = limit,
            )
        return Amethyst.instance.client
            .taggedAs(SubPurpose.HOME_FEED, ASSISTANT)
            .fetchAll(
                filters = relays.associateWith { listOf(filter) },
                idleTimeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            ).mapNotNull { it as? TextNoteEvent }
            .take(limit)
    }

    /**
     * Read recent Nostr posts from a specific user. Use when the user
     * asks "what did Snowden post recently on Nostr", "catch me up on
     * what Jack has been posting", "show me Alice's latest notes", or
     * wants to see one specific Nostr user's activity.
     *
     * Pass the target user's npub or hex pubkey — use [searchProfiles]
     * first if you only have a display name. Queries the target's
     * NIP-65 write relays when cached, falling back to the active
     * account's home relays.
     *
     * @param user npub (`npub1…`) or 64-character hex pubkey.
     * @param limit max notes to return, capped to 100. Default 20.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getNotesByUser(
        appFunctionContext: AppFunctionContext,
        user: String,
        limit: Int = 20,
    ): SearchNotesResult {
        val cappedLimit = limit.coerceIn(1, 100)
        val pubkey = decodeUserOrThrow(user)

        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchNotesResult.empty()
        val client = Amethyst.instance.client.taggedAs(SubPurpose.USER_PROFILE, ASSISTANT)

        val targetWriteRelays =
            account.cache
                .checkGetOrCreateUser(pubkey)
                ?.outboxRelays()
                ?.toSet()
                .orEmpty()
        val relays =
            targetWriteRelays
                .ifEmpty { account.homeRelays.flow.value }
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return SearchNotesResult.empty()

        val filter =
            Filter(
                kinds = listOf(TextNoteEvent.KIND),
                authors = listOf(pubkey),
                limit = cappedLimit,
            )
        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                idleTimeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val hits =
            events
                .mapNotNull { it as? TextNoteEvent }
                .filter { it.pubKey == pubkey }
                .take(cappedLimit)
                .map { it.toNoteHit() }

        return SearchNotesResult(matches = hits)
    }

    /**
     * Look up one Nostr profile by npub or hex pubkey. Use when the user
     * asks "who is npub1…", "tell me about [npub]", "what's [user]'s
     * Nostr profile", or wants the bio / NIP-05 / Lightning address of a
     * specific Nostr user.
     *
     * Returns the latest kind:0 metadata — cache-first, with a short
     * network fallback when the user's profile hasn't been observed
     * locally yet.
     *
     * @param user npub (`npub1…`) or 64-character hex pubkey.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getProfile(
        appFunctionContext: AppFunctionContext,
        user: String,
    ): GetProfileResult {
        val pubkey = decodeUserOrThrow(user)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return GetProfileResult.notFound(pubkey)

        // Cache-first: the foreground UI keeps observed metadata around.
        val cached =
            account.cache
                .checkGetOrCreateUser(pubkey)
                ?.metadataOrNull()
        if (cached != null) {
            val info = cached.flow.value?.info
            return GetProfileResult(
                found = true,
                profile =
                    ProfileHit(
                        npub = NPub.create(pubkey),
                        pubkeyHex = pubkey,
                        displayName = cached.bestName(),
                        about = info?.about,
                        nip05 = cached.nip05(),
                        picture = cached.profilePicture(),
                        lnAddress = cached.lnAddress(),
                    ),
            )
        }

        // Cache miss: drain bootstrap relays for the latest kind:0.
        val client = Amethyst.instance.client.taggedAs(SubPurpose.USER_PROFILE, ASSISTANT)
        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return GetProfileResult.notFound(pubkey)

        val filter =
            Filter(
                kinds = listOf(MetadataEvent.KIND),
                authors = listOf(pubkey),
                limit = 1,
            )
        val event =
            client
                .fetchAll(
                    filters = relays.associateWith { listOf(filter) },
                    idleTimeoutMs = GEMINI_FETCH_TIMEOUT_MS,
                ).mapNotNull { it as? MetadataEvent }
                .filter { it.pubKey == pubkey }
                .maxByOrNull { it.createdAt }
                ?: return GetProfileResult.notFound(pubkey)

        return GetProfileResult(found = true, profile = event.toProfileHit())
    }

    /**
     * Find Nostr posts about a topic via hashtag. Use when the user asks
     * "show me Nostr posts about Bitcoin", "find Nostr discussion of
     * #Tor", "what's the Nostr take on [topic]", or wants to browse
     * conversation about a specific subject.
     *
     * Pass the tag value without the leading `#` — "bitcoin", not
     * "#bitcoin". The hashtag is lowercased before matching (the
     * convention most Nostr clients follow).
     *
     * @param hashtag the tag value without the leading `#`.
     * @param limit max notes to return, capped to 100. Default 30.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchByHashtag(
        appFunctionContext: AppFunctionContext,
        hashtag: String,
        limit: Int = 30,
    ): SearchNotesResult {
        val tag = hashtag.trim().removePrefix("#").lowercase()
        if (tag.isEmpty()) throw AppFunctionInvalidArgumentException("hashtag must not be blank")
        val cappedLimit = limit.coerceIn(1, 100)

        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchNotesResult.empty()
        val client = Amethyst.instance.client.taggedAs(SubPurpose.TAG_FEED, ASSISTANT)
        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return SearchNotesResult.empty()

        val filter =
            Filter(
                kinds = listOf(TextNoteEvent.KIND),
                tags = mapOf("t" to listOf(tag)),
                limit = cappedLimit,
            )
        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                idleTimeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val hits =
            events
                .mapNotNull { it as? TextNoteEvent }
                .take(cappedLimit)
                .map { it.toNoteHit() }

        return SearchNotesResult(matches = hits)
    }

    /**
     * Report who the user is signed in as on Nostr. Use when the user
     * asks "who am I logged in as on Nostr", "what's my npub", "what's
     * my Nostr identity", "how many people do I follow on Nostr", or
     * any other "tell me about my Nostr account" query.
     *
     * Returns the active account's npub, display name, NIP-05 handle,
     * follow count, and how many relays are configured for outbox /
     * DM inbox. Use this for Nostr-side diagnostics rather than as a
     * general "who am I" answer.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getActiveAccountInfo(appFunctionContext: AppFunctionContext): AccountInfoResult {
        val account =
            Amethyst.instance.sessionManager.loggedInAccount() ?: return AccountInfoResult.signedOut()

        val myPub = account.signer.pubKey
        val myUser = account.cache.checkGetOrCreateUser(myPub)
        val meta = myUser?.metadataOrNull()

        return AccountInfoResult(
            signedIn = true,
            npub = NPub.create(myPub),
            pubkeyHex = myPub,
            displayName = meta?.bestName(),
            nip05 = meta?.nip05(),
            followCount = account.kind3FollowList.userList.value.size,
            outboxRelayCount = account.homeRelays.flow.value.size,
            dmRelayCount = account.dmRelays.flow.value.size,
        )
    }

    /**
     * Most recent kind:1 notes the active account itself published. For
     * "what did I post recently?" — drains the user's own outbox relays
     * filtered to their own pubkey.
     *
     * @param limit max notes to return, capped to 100. Default 20.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getMyRecentNotes(
        appFunctionContext: AppFunctionContext,
        limit: Int = 20,
    ): SearchNotesResult {
        val cappedLimit = limit.coerceIn(1, 100)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchNotesResult.empty()
        val client = Amethyst.instance.client.taggedAs(SubPurpose.ACCOUNT_DATA, ASSISTANT)
        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return SearchNotesResult.empty()

        val myPub = account.signer.pubKey
        val filter =
            Filter(
                kinds = listOf(TextNoteEvent.KIND),
                authors = listOf(myPub),
                limit = cappedLimit,
            )
        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                idleTimeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val hits =
            events
                .mapNotNull { it as? TextNoteEvent }
                .filter { it.pubKey == myPub }
                .take(cappedLimit)
                .map { it.toNoteHit() }

        return SearchNotesResult(matches = hits)
    }

    /**
     * Notes where someone tagged the active account with a `p` tag —
     * the Nostr equivalent of being @-mentioned. Use this for "did
     * anyone mention me recently?".
     *
     * @param limit max notes to return, capped to 100. Default 20.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getMyMentions(
        appFunctionContext: AppFunctionContext,
        limit: Int = 20,
    ): SearchNotesResult {
        val cappedLimit = limit.coerceIn(1, 100)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchNotesResult.empty()
        val client = Amethyst.instance.client.taggedAs(SubPurpose.NOTIFICATIONS, ASSISTANT)
        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return SearchNotesResult.empty()

        val myPub = account.signer.pubKey
        val filter =
            Filter(
                kinds = listOf(TextNoteEvent.KIND),
                tags = mapOf("p" to listOf(myPub)),
                limit = cappedLimit,
            )
        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                idleTimeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val hits =
            events
                .mapNotNull { it as? TextNoteEvent }
                .take(cappedLimit)
                .map { it.toNoteHit() }

        return SearchNotesResult(matches = hits)
    }

    /**
     * Replies to a specific note (kind:1 events with an `e` tag
     * pointing at [eventId]). Used for "did anyone respond to my last
     * post?" — pass `getMyRecentNotes(1).matches.first().eventId`
     * from a previous call, or any other note you want to track.
     *
     * @param eventId 64-character hex id of the note being replied to.
     * @param limit max replies to return, capped to 100. Default 20.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getRepliesToNote(
        appFunctionContext: AppFunctionContext,
        eventId: String,
        limit: Int = 20,
    ): SearchNotesResult {
        if (eventId.length != 64) {
            throw AppFunctionInvalidArgumentException("eventId must be 64-character hex (nevent bech32 not yet supported)")
        }
        val cappedLimit = limit.coerceIn(1, 100)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchNotesResult.empty()
        val client = Amethyst.instance.client.taggedAs(SubPurpose.THREAD, ASSISTANT)
        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return SearchNotesResult.empty()

        val filter =
            Filter(
                kinds = listOf(TextNoteEvent.KIND),
                tags = mapOf("e" to listOf(eventId)),
                limit = cappedLimit,
            )
        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                idleTimeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val hits =
            events
                .mapNotNull { it as? TextNoteEvent }
                .filter { it.id != eventId } // self-reference safety
                .take(cappedLimit)
                .map { it.toNoteHit() }

        return SearchNotesResult(matches = hits)
    }

    /**
     * Report how many sats the user earned on Nostr in a time window.
     * Use when the user asks "did I get any zaps today", "how many sats
     * did I earn on Nostr this week", "did anyone zap my last post",
     * or wants a summary of incoming NIP-57 Lightning zaps.
     *
     * Drains kind:9735 zap receipts addressed to the user in the window
     * and parses the bolt11 invoice from each to compute total sats.
     * Returns total + per-window zap count + unique zapper count.
     *
     * @param hoursBack window size in hours. Capped to 168 (7 days),
     *   default 24.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getZapsReceived(
        appFunctionContext: AppFunctionContext,
        hoursBack: Int = 24,
    ): ZapsReceivedResult {
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return ZapsReceivedResult.empty()
        val client = Amethyst.instance.client.taggedAs(SubPurpose.NOTIFICATIONS, ASSISTANT)
        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return ZapsReceivedResult.empty()

        val cappedHours = hoursBack.coerceIn(1, 24 * 7)
        val sinceSecs = TimeUtils.now() - cappedHours.toLong() * 3600L
        val myPub = account.signer.pubKey

        val filter =
            Filter(
                kinds = listOf(ZapReceiptEvent.KIND, Bolt12ZapEvent.KIND),
                tags = mapOf("p" to listOf(myPub)),
                since = sinceSecs,
                limit = 500,
            )
        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                idleTimeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val receipts = events.mapNotNull { it as? ZapReceiptEvent }
        val zapperIds = mutableSetOf<HexKey>()
        var totalSats = 0L
        var unparseable = 0
        for (z in receipts) {
            val bolt11 =
                z.tags
                    .firstOrNull { it.size > 1 && it[0] == "bolt11" }
                    ?.get(1)
            val sats =
                bolt11
                    ?.let { runCatching { LnInvoiceUtil.getAmountInSats(it).toLong() }.getOrNull() }
                    ?: run {
                        unparseable++
                        0L
                    }
            totalSats += sats
            // Zap sender is recorded in the description's signed kind:9734;
            // we only have it as a pubkey-id mention via `P` tag on some
            // receipts. Best-effort:
            z.tags
                .firstOrNull { it.size > 1 && (it[0] == "P" || it[0] == "p" && it[1] != myPub) }
                ?.get(1)
                ?.let { zapperIds.add(it) }
        }

        return ZapsReceivedResult(
            windowHours = cappedHours,
            totalSats = totalSats,
            zapCount = receipts.size,
            uniqueZapperCount = zapperIds.size,
            unparseableInvoiceCount = unparseable,
        )
    }

    /**
     * Read recent Nostr direct messages. Use when the user asks "did I
     * get any Nostr DMs", "what did Alice DM me", "show me my recent
     * Nostr messages", "summarize my unread Nostr DMs", or wants
     * decrypted message content (not just notifications) from Nostr.
     *
     * Drains NIP-17 gift wraps from the active account's DM-inbox
     * relays, decrypts each in-process (Amethyst is the only place
     * the user's NIP-44 keys live), and returns the inner kind:14
     * messages with sender display names attached. File-attachment
     * DMs (kind:15) are filtered out for now to keep the response
     * small.
     *
     * @param peer optional npub/hex; when set, only returns messages
     *   to/from that specific peer. When null, returns conversations
     *   with anyone.
     * @param hoursBack window size in hours. Capped to 168 (7 days),
     *   default 24.
     * @param limit max messages to return, capped to 100. Default 20.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getRecentDms(
        appFunctionContext: AppFunctionContext,
        peer: String?,
        hoursBack: Int = 24,
        limit: Int = 20,
    ): DmsResult {
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return DmsResult.empty()
        val client = Amethyst.instance.client.taggedAs(SubPurpose.DIRECT_MESSAGES, ASSISTANT)
        val cappedHours = hoursBack.coerceIn(1, 24 * 7)
        val cappedLimit = limit.coerceIn(1, 100)
        val peerPub = peer?.takeIf { it.isNotBlank() }?.let { decodeUserOrThrow(it) }

        // DM-inbox relays per kind:10050; fall back to home relays if the
        // user never published a kind:10050 (interop with stale clients).
        val relays =
            account.dmRelays.flow.value
                .ifEmpty { account.homeRelays.flow.value }
        if (relays.isEmpty()) return DmsResult.empty()

        val myPub = account.signer.pubKey
        val sinceSecs = TimeUtils.now() - cappedHours.toLong() * 3600L

        // NIP-59 gift wraps randomise their `created_at` up to two days
        // in the past, so we widen the filter by 2 days. Same trick the
        // foreground client and amy use.
        val filter =
            Filter(
                kinds = listOf(GiftWrapEvent.KIND),
                tags = mapOf("p" to listOf(myPub)),
                since = sinceSecs - TimeUtils.twoDays(),
                limit = 200,
            )
        val wraps =
            client
                .fetchAll(
                    filters = relays.associateWith { listOf(filter) },
                    idleTimeoutMs = GEMINI_FETCH_TIMEOUT_MS,
                ).mapNotNull { it as? GiftWrapEvent }

        val seen = HashSet<HexKey>()
        val messages = mutableListOf<DmMessage>()
        for (wrap in wraps) {
            val inner = wrap.unwrapAndUnsealOrNull(account.signer) ?: continue
            if (inner !is BaseDMGroupEvent) continue
            if (inner !is ChatMessageEvent) continue // skip file headers for v1; keep payload small
            if (!seen.add(inner.id)) continue
            // After widening for randomised `created_at`, drop anything
            // outside the requested window so the result honours the
            // caller's hoursBack.
            if (inner.createdAt < sinceSecs) continue
            if (peerPub != null && peerPub !in inner.groupMembers()) continue

            messages.add(
                DmMessage(
                    fromNpub = NPub.create(inner.pubKey),
                    fromPubkeyHex = inner.pubKey,
                    fromDisplayName = displayNameOf(inner.pubKey),
                    sentByMe = inner.pubKey == myPub,
                    content = inner.content,
                    createdAt = inner.createdAt,
                ),
            )
        }

        return DmsResult(
            windowHours = cappedHours,
            messages =
                messages
                    .sortedByDescending { it.createdAt }
                    .take(cappedLimit),
        )
    }

    /**
     * NIP-50 search restricted to NIP-23 long-form articles
     * (kind:30023). Use for "find Nostr articles about [topic]" when
     * the user wants written-up posts rather than short notes.
     *
     * @param query free-form search text.
     * @param limit max articles to return, capped to 50. Default 10.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchArticles(
        appFunctionContext: AppFunctionContext,
        query: String,
        limit: Int = 10,
    ): SearchNotesResult {
        val cappedLimit = limit.coerceIn(1, 50)
        val filter =
            SearchActions.searchNotesFilter(
                query = query,
                kinds = listOf(LongFormContentEvent.KIND),
                limit = cappedLimit,
            ) ?: return SearchNotesResult.empty()

        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchNotesResult.empty()
        val client = Amethyst.instance.client.taggedAs(SubPurpose.SEARCH, ASSISTANT)
        val relays = account.searchRelayList.flow.value
        if (relays.isEmpty()) return SearchNotesResult.empty()

        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                idleTimeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val hits =
            events
                .mapNotNull { it as? LongFormContentEvent }
                .take(cappedLimit)
                .map { (it as com.vitorpamplona.quartz.nip01Core.core.Event).toFeedNoteHit() }

        return SearchNotesResult(matches = hits)
    }

    /**
     * Live audio/video streams currently broadcasting on Nostr (NIP-53
     * kind:30311 events with `status=live`). Use for "what's live on
     * Nostr right now?".
     *
     * @param limit max streams to return, capped to 50. Default 20.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getLiveStreams(
        appFunctionContext: AppFunctionContext,
        limit: Int = 20,
    ): LiveStreamsResult {
        val cappedLimit = limit.coerceIn(1, 50)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return LiveStreamsResult.empty()
        val client = Amethyst.instance.client.taggedAs(SubPurpose.DISCOVER_FEED, ASSISTANT)
        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return LiveStreamsResult.empty()

        // NIP-53 has no `since` semantics — a live activity can have an
        // arbitrarily old createdAt. We over-fetch and post-filter for
        // `isLive()`, which also applies the 8-hour staleness guard
        // (status=live + recent createdAt) baked into quartz.
        val filter =
            Filter(
                kinds = listOf(LiveActivitiesEvent.KIND),
                limit = cappedLimit * 4,
            )
        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                idleTimeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val streams =
            events
                .mapNotNull { it as? LiveActivitiesEvent }
                .filter { it.isLive() }
                .take(cappedLimit)
                .map { ev ->
                    val hostPub = ev.host()?.pubKey
                    LiveStreamHit(
                        eventId = ev.id,
                        title = ev.title(),
                        summary = ev.summary(),
                        streamingUrl = ev.streaming(),
                        hostNpub = hostPub?.let { NPub.create(it) },
                        hostPubkeyHex = hostPub,
                        hostDisplayName = hostPub?.let { displayNameOf(it) },
                        startsAt = ev.starts(),
                        createdAt = ev.createdAt,
                    )
                }

        return LiveStreamsResult(streams = streams)
    }

    // ------------------------------------------------------------------
    // Write verbs — gated on a signer that can sign without launching a
    // foreground activity. See requireInProcessSigner below.
    // ------------------------------------------------------------------

    /**
     * Publish a short text note on Nostr. Use when the user asks "post
     * this to Nostr", "tweet this on Nostr", "share [X] on Nostr",
     * "publish a Nostr note saying [X]", or any other "send to Nostr"
     * intent for plain-text content.
     *
     * Publishes a NIP-10 kind:1 short text note as the signed-in user,
     * broadcast to the account's configured outbox relays. Returns per-
     * relay ack so the caller can confirm the post landed.
     *
     * @param text the note body. Cannot be blank; capped at 8000
     *   characters so an accidentally-pasted document doesn't try to
     *   become a Nostr post.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun postNote(
        appFunctionContext: AppFunctionContext,
        text: String,
    ): WriteResult {
        val body = text.trim()
        if (body.isEmpty()) throw AppFunctionInvalidArgumentException("text cannot be blank")
        if (body.length > MAX_NOTE_LENGTH) {
            throw AppFunctionInvalidArgumentException("text is $${body.length} chars; cap is $MAX_NOTE_LENGTH")
        }

        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: throw notSignedIn()
        requireInProcessSigner(account.signer)
        val relays = account.outboxRelays.flow.value
        if (relays.isEmpty()) throw AppFunctionInvalidArgumentException("account has no outbox relays configured")

        val template = TextNoteEvent.build(body)
        val signed = account.signer.sign(template)
        // Mirror the foreground UI: cache the freshly-signed event so
        // subsequent reads see it without waiting for a relay echo.
        account.cache.justConsumeMyOwnEvent(signed)
        val ack = Amethyst.instance.client.publishAndConfirmDetailed(signed, relays, PUBLISH_TIMEOUT_SECS)

        return WriteResult.from(signed.id, ack)
    }

    /**
     * Follow a user on Nostr. Use when the user asks "follow [X] on
     * Nostr", "add [npub] to my Nostr follows", or "subscribe to
     * [user]" with a Nostr context. Idempotent — re-following someone
     * already followed is a safe no-op.
     *
     * Adds [user] to the signed-in account's NIP-02 kind:3 follow list
     * and publishes the updated list. [WriteResult.changed] reports
     * `false` when the user is already followed.
     *
     * **BEFORE INVOKING:** the agent MUST resolve [user] via
     * [searchProfiles] / [getProfile] first and present the recipient
     * to the human with all three identity signals — display name,
     * npub, and NIP-05 handle — and wait for explicit confirmation
     * ("yes, follow Vitor (vitor@vitorpamplona.com, npub1…)"). Nostr
     * has no global namespace, so multiple users can share a display
     * name; the NIP-05 + npub disambiguates.
     *
     * @param user npub (`npub1…`) or 64-character hex pubkey.
     * @param expectedDisplayName when set, the verb cross-checks that
     *   the resolved profile's display name / NIP-05 contains (or is
     *   contained by) this string. Pass the same name you showed the
     *   user during the confirmation prompt — a mismatch aborts the
     *   write with a typed error so the agent can re-prompt.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun followUser(
        appFunctionContext: AppFunctionContext,
        user: String,
        expectedDisplayName: String? = null,
    ): WriteResult {
        val target = decodeUserOrThrow(user)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: throw notSignedIn()
        if (target == account.signer.pubKey) {
            throw AppFunctionInvalidArgumentException("cannot follow yourself")
        }
        requireInProcessSigner(account.signer)
        verifyExpectedRecipient(account, target, expectedDisplayName, requireFollow = false)
        val relays = account.outboxRelays.flow.value
        if (relays.isEmpty()) throw AppFunctionInvalidArgumentException("account has no outbox relays configured")

        val currentList = account.kind3FollowList.getFollowListEvent()
        if (currentList != null && currentList.isFollowing(target)) {
            return WriteResult.unchanged()
        }

        // Relay hint from cached kind:10002 so the follow tag points
        // readers at where the target publishes.
        val relayHint =
            account.cache
                .checkGetOrCreateUser(target)
                ?.outboxRelays()
                ?.firstOrNull()

        val newList =
            FollowActions.buildFollow(
                signer = account.signer,
                pubkeyToFollow = target,
                currentContactList = currentList,
                relayHint = relayHint,
            )
        account.cache.justConsumeMyOwnEvent(newList)
        val ack = Amethyst.instance.client.publishAndConfirmDetailed(newList, relays, PUBLISH_TIMEOUT_SECS)
        return WriteResult.from(newList.id, ack)
    }

    /**
     * Unfollow a user on Nostr. Use when the user asks "unfollow [X]
     * on Nostr", "remove [npub] from my Nostr follows", or "stop
     * following [user]" with a Nostr context. Idempotent — unfollowing
     * someone the user wasn't following is a safe no-op.
     *
     * Removes [user] from the signed-in account's NIP-02 kind:3
     * follow list and publishes the updated list. [WriteResult.changed]
     * reports `false` when the user wasn't followed.
     *
     * @param user npub (`npub1…`) or 64-character hex pubkey.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun unfollowUser(
        appFunctionContext: AppFunctionContext,
        user: String,
    ): WriteResult {
        val target = decodeUserOrThrow(user)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: throw notSignedIn()
        requireInProcessSigner(account.signer)
        val relays = account.outboxRelays.flow.value
        if (relays.isEmpty()) throw AppFunctionInvalidArgumentException("account has no outbox relays configured")

        val currentList = account.kind3FollowList.getFollowListEvent()
        if (currentList == null || !currentList.isFollowing(target)) {
            return WriteResult.unchanged()
        }

        val newList =
            FollowActions.buildUnfollow(
                signer = account.signer,
                pubkeyToUnfollow = target,
                currentContactList = currentList,
            ) ?: return WriteResult.unchanged()
        account.cache.justConsumeMyOwnEvent(newList)
        val ack = Amethyst.instance.client.publishAndConfirmDetailed(newList, relays, PUBLISH_TIMEOUT_SECS)
        return WriteResult.from(newList.id, ack)
    }

    /**
     * Send a direct message to a user on Nostr. Use when the user asks
     * "DM [X] on Nostr", "send a Nostr message to [user] saying [Y]",
     * "message [npub] on Nostr", or any other "send a private message"
     * intent in a Nostr context.
     *
     * The message is gift-wrapped (kind:1059) per NIP-59 — only the
     * recipient (and the signed-in user, who keeps their own copy)
     * can decrypt it. Recipients without a published kind:10050
     * DM-inbox list fall back through NIP-65 read relays then
     * bootstrap relays.
     *
     * **BEFORE INVOKING:** the agent MUST resolve [recipient] via
     * [searchProfiles] / [getProfile] first and confirm with the user
     * using all three identity signals — display name, npub, and
     * NIP-05 handle ("DM Alice (alice@damus.io, npub1…)?"). Nostr has
     * no global namespace and impersonation is trivial; the npub +
     * NIP-05 are the only cryptographic disambiguators.
     *
     * By default, the verb refuses to message anyone not in the user's
     * follow list — a near-zero-cost guard against same-name
     * impersonators. Pass `requireFollow = false` only after the user
     * explicitly approves messaging a stranger.
     *
     * @param recipient npub (`npub1…`) or 64-character hex pubkey.
     * @param text the message body. Cannot be blank; capped at 8000
     *   characters.
     * @param expectedDisplayName when set, the verb cross-checks that
     *   the resolved profile's display name / NIP-05 contains (or is
     *   contained by) this string. Pass the same name you showed the
     *   user during the confirmation prompt — a mismatch aborts the
     *   send with a typed error.
     * @param requireFollow when true (the default), the verb refuses
     *   to send to a pubkey the user doesn't already follow on Nostr.
     *   Override to false only when the user explicitly confirmed they
     *   want to DM a stranger.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun sendDm(
        appFunctionContext: AppFunctionContext,
        recipient: String,
        text: String,
        expectedDisplayName: String? = null,
        requireFollow: Boolean = true,
    ): SendDmResult {
        val body = text.trim()
        if (body.isEmpty()) throw AppFunctionInvalidArgumentException("text cannot be blank")
        if (body.length > MAX_NOTE_LENGTH) {
            throw AppFunctionInvalidArgumentException("text is $${body.length} chars; cap is $MAX_NOTE_LENGTH")
        }
        val recipientPub = decodeUserOrThrow(recipient)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: throw notSignedIn()
        requireInProcessSigner(account.signer)
        verifyExpectedRecipient(account, recipientPub, expectedDisplayName, requireFollow)

        val client = Amethyst.instance.client.taggedAs(SubPurpose.RELAY_LISTS, ASSISTANT)
        val result = DmActions.buildTextDm(account.signer, recipientPub, body)

        // One wrap per recipient — for a 1:1 DM that's two (the recipient's
        // copy + the sender's own copy on the sender's inbox).
        val deliveries = mutableListOf<DmDelivery>()
        for (wrap in result.wraps) {
            val target = wrap.recipientPubKey() ?: continue
            // Fetch the recipient's kind:10050 / 10051 / 10002 fresh — local
            // cache may be stale for users we rarely interact with, and the
            // cost is one short drain on already-warmed sockets.
            val lists =
                RecipientRelayFetcher.fetchRelayLists(client, target, account.outboxRelays.flow.value)
            val resolution =
                DmActions.resolveDmRelays(
                    recipientLists = lists,
                    bootstrap = account.outboxRelays.flow.value,
                    allowFallback = true,
                )
            if (resolution.relays.isEmpty()) {
                deliveries.add(
                    DmDelivery(
                        recipientNpub = NPub.create(target),
                        recipientPubkeyHex = target,
                        wrapId = wrap.id,
                        publishedTo = emptyList(),
                        rejectedBy = emptyList(),
                        relaySource = resolution.source.name.lowercase(),
                    ),
                )
                continue
            }
            val ack = client.publishAndConfirmDetailed(wrap, resolution.relays, PUBLISH_TIMEOUT_SECS)
            deliveries.add(
                DmDelivery(
                    recipientNpub = NPub.create(target),
                    recipientPubkeyHex = target,
                    wrapId = wrap.id,
                    publishedTo = ack.filterValues { it }.keys.map { it.url },
                    rejectedBy = ack.filterValues { !it }.keys.map { it.url },
                    relaySource = resolution.source.name.lowercase(),
                ),
            )
        }
        // Cache the inner kind:14 so the foreground UI sees the message
        // immediately in the relevant DM thread.
        account.cache.justConsumeMyOwnEvent(result.msg)

        return SendDmResult(
            messageEventId = result.msg.id,
            deliveries = deliveries,
        )
    }

    /**
     * Tip a Nostr user with Bitcoin sats. Use when the user asks "zap
     * [X] on Nostr", "tip [user] [N] sats", "send a Lightning tip to
     * [npub]", "send [N] sats onchain to [user]", "send Alice
     * [N] sats via Bitcoin", or "thank [user] with sats".
     *
     * Two rails are supported:
     *
     *   * **Lightning** (default) — NIP-57 zap. Builds the kind:9734
     *     request, fetches a BOLT11 invoice from the recipient's
     *     Lightning service, and — when the user has a Nostr Wallet
     *     Connect wallet configured — pays automatically over NIP-47.
     *     Falls back to returning the invoice for manual payment.
     *
     *   * **Onchain** (NIP-BC kind:8333) — when [chain] is "onchain",
     *     builds and broadcasts a Bitcoin transaction paying the
     *     recipient's derived Taproot address, then publishes a
     *     kind:8333 zap receipt. Requires the user to have a Bitcoin
     *     chain backend configured in Amethyst (an Esplora-compatible
     *     API like mempool.space — see Settings → Bitcoin).
     *
     * Defaults to 21 sats Lightning. Cap is 1,000,000 sats so an
     * accidental tip can't drain a wallet.
     *
     * **BEFORE INVOKING:** zaps move real money, so the agent MUST
     * resolve [user] via [searchProfiles] / [getProfile] first and
     * confirm with the human using all three identity signals —
     * display name, npub, and NIP-05 handle ("Zap Alice
     * (alice@damus.io, npub1…) 21 sats?"). Nostr has no global
     * namespace; a same-name impersonator is the most likely way for
     * a zap to go to the wrong person.
     *
     * By default, the verb refuses to zap anyone not in the user's
     * follow list. This is the strongest guard against same-name
     * impersonators — even if Gemini picked the wrong Alice, the user
     * almost certainly isn't following her. Pass `requireFollow =
     * false` only after the user explicitly approves zapping a
     * stranger (e.g. a public-figure npub they read out themselves).
     *
     * @param user npub (`npub1…`) or 64-character hex pubkey of the
     *   zap recipient.
     * @param sats amount to zap, in whole sats. Capped at 1,000,000
     *   sats. Default 21.
     * @param comment optional message to attach to the zap. Capped at
     *   280 characters.
     * @param chain transport rail: "lightning" (default) or "onchain".
     *   Null / blank is treated as Lightning.
     * @param feeRateSatPerVByte miner fee rate for onchain zaps in
     *   sats per virtual byte. Ignored for Lightning. Default 5.0,
     *   which targets fast confirmation under typical mempool
     *   conditions without being aggressive.
     * @param expectedDisplayName when set, the verb cross-checks that
     *   the resolved profile's display name / NIP-05 contains (or is
     *   contained by) this string. Pass the same name you showed the
     *   user during the confirmation prompt — a mismatch aborts the
     *   zap before any money moves.
     * @param requireFollow when true (the default), the verb refuses
     *   to zap a pubkey the user doesn't already follow on Nostr.
     *   Override to false only when the user explicitly confirmed
     *   they want to zap a stranger.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun zapUser(
        appFunctionContext: AppFunctionContext,
        user: String,
        sats: Long = 21,
        comment: String? = null,
        chain: String? = null,
        feeRateSatPerVByte: Double = 5.0,
        expectedDisplayName: String? = null,
        requireFollow: Boolean = true,
    ): ZapResult {
        val cappedSats = sats.coerceIn(1L, MAX_ZAP_SATS)
        val trimmedComment = comment.orEmpty().trim().take(MAX_ZAP_COMMENT_LENGTH)
        val recipientPub = decodeUserOrThrow(user)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: throw notSignedIn()
        requireInProcessSigner(account.signer)
        verifyExpectedRecipient(account, recipientPub, expectedDisplayName, requireFollow)

        val rail = chain?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "lightning"
        return when (rail) {
            "lightning", "ln" -> zapUserViaLightning(account, recipientPub, cappedSats, trimmedComment)
            "onchain", "btc", "bitcoin" ->
                zapUserViaOnchain(account, recipientPub, cappedSats, trimmedComment, feeRateSatPerVByte)
            else ->
                throw AppFunctionInvalidArgumentException(
                    "chain must be \"lightning\" or \"onchain\" (got \"$chain\").",
                )
        }
    }

    private suspend fun zapUserViaLightning(
        account: com.vitorpamplona.amethyst.commons.model.Account,
        recipientPub: HexKey,
        sats: Long,
        comment: String,
    ): ZapResult {
        val client = Amethyst.instance.client.taggedAs(SubPurpose.PROFILE_METADATA, ASSISTANT)

        // Pull the recipient's kind:0 — needs lnAddress to receive the zap.
        val metadata =
            account.cache
                .checkGetOrCreateUser(recipientPub)
                ?.metadataOrNull()
                ?.flow
                ?.value
                ?.info
                ?.let { extractLnAddressFromMetadata(it) }
                ?: fetchProfileForZap(client, account, recipientPub)
                ?: throw AppFunctionInvalidArgumentException(
                    "No kind:0 metadata for ${NPub.create(recipientPub)} — recipient must have a Nostr profile first.",
                )
        val lnAddress =
            metadata.takeIf { it.isNotBlank() }
                ?: throw AppFunctionInvalidArgumentException(
                    "Recipient has no lud16 or lud06 in their profile — they can't receive Lightning zaps. " +
                        "Try chain=\"onchain\" instead if they have NIP-BC enabled on their account.",
                )

        val zapRequest =
            ZapActions.buildUserZapRequest(
                signer = account.signer,
                recipientPubkey = recipientPub,
                amountMillisats = ZapActions.satsToMillisats(sats),
                inboxRelays = account.nip65RelayList.inboxFlow.value,
                comment = comment,
                zapType = ZapReceiptEvent.ZapType.PUBLIC,
            )

        val invoice = fetchInvoiceOrThrow(lnAddress, sats, comment, zapRequest)

        // If the user has a Nostr Wallet Connect wallet configured, pay
        // the invoice automatically over NIP-47 so Gemini can answer
        // "I zapped Alice 21 sats" instead of "here's a BOLT11 invoice
        // for you to paste somewhere." Falls back to manual when NWC
        // isn't set up or the wallet declines.
        val nwc = payViaDefaultSourceOrNull(account, invoice, null)

        return ZapResult(
            chain = "lightning",
            recipientNpub = NPub.create(recipientPub),
            recipientPubkeyHex = recipientPub,
            recipientDisplayName = displayNameOf(recipientPub),
            lnAddress = lnAddress,
            amountSats = sats,
            comment = comment,
            invoice = invoice,
            zapRequestId = zapRequest.id,
            nwcAttempted = nwc != null,
            nwcPaid = nwc?.success == true,
            nwcPreimage = nwc?.preimage,
            nwcError = nwc?.errorMessage,
            onchainTxid = null,
            onchainFeeSats = null,
            onchainChangeSats = null,
            onchainReceiptEventId = null,
            onchainError = null,
            onchainStage = null,
        )
    }

    private suspend fun zapUserViaOnchain(
        account: com.vitorpamplona.amethyst.commons.model.Account,
        recipientPub: HexKey,
        sats: Long,
        comment: String,
        feeRateSatPerVByte: Double,
    ): ZapResult {
        // The Account.sendOnchainZap call returns Failure when the
        // backend isn't configured; we surface that as a typed
        // NotSupported error so the LLM can tell the user "you need to
        // set up a Bitcoin backend in Amethyst Settings → Bitcoin."
        if (account.cache.onchainBackend == null) {
            throw AppFunctionNotSupportedException(
                "Amethyst has no Bitcoin chain backend configured. " +
                    "Open Settings → Bitcoin and add an Esplora-compatible endpoint (e.g. mempool.space) " +
                    "before sending onchain zaps.",
            )
        }
        if (feeRateSatPerVByte < 0.1) {
            throw AppFunctionInvalidArgumentException(
                "feeRateSatPerVByte must be at least 0.1 (got $feeRateSatPerVByte).",
            )
        }
        if (feeRateSatPerVByte > 1000) {
            throw AppFunctionInvalidArgumentException(
                "feeRateSatPerVByte must be ≤ 1000 (got $feeRateSatPerVByte) — anything higher is almost certainly a mistake.",
            )
        }

        val result =
            account.zaps.sendOnchainZap(
                recipientPubKey = recipientPub,
                amountSats = sats,
                feeRateSatPerVByte = feeRateSatPerVByte,
                comment = comment,
                // No zappedEvent: this is a profile zap. Event zaps via
                // onchain would need a separate verb that takes an
                // event id (and resolves splits) — deferred.
                zappedEvent = null,
            )

        return when (result) {
            is com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendResult.Success ->
                ZapResult(
                    chain = "onchain",
                    recipientNpub = NPub.create(recipientPub),
                    recipientPubkeyHex = recipientPub,
                    recipientDisplayName = displayNameOf(recipientPub),
                    // Onchain doesn't go through an LN service.
                    lnAddress = "",
                    amountSats = sats,
                    comment = comment,
                    // Onchain has no BOLT11; pre-fill empty to satisfy
                    // the non-null result-class contract.
                    invoice = "",
                    zapRequestId = "",
                    nwcAttempted = false,
                    nwcPaid = false,
                    nwcPreimage = null,
                    nwcError = null,
                    onchainTxid = result.txid,
                    onchainFeeSats = result.feeSats,
                    onchainChangeSats = result.changeSats,
                    onchainReceiptEventId = result.receiptEventId,
                    onchainError = null,
                    onchainStage = null,
                )

            is com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendResult.Failure ->
                ZapResult(
                    chain = "onchain",
                    recipientNpub = NPub.create(recipientPub),
                    recipientPubkeyHex = recipientPub,
                    recipientDisplayName = displayNameOf(recipientPub),
                    lnAddress = "",
                    amountSats = sats,
                    comment = comment,
                    invoice = "",
                    zapRequestId = "",
                    nwcAttempted = false,
                    nwcPaid = false,
                    nwcPreimage = null,
                    nwcError = null,
                    // When stage == PUBLISHING, the tx WAS broadcast
                    // but the receipt couldn't be published — surface
                    // the txid even on failure so the user can verify
                    // on a block explorer.
                    onchainTxid = result.broadcastTxid,
                    onchainFeeSats = null,
                    onchainChangeSats = null,
                    onchainReceiptEventId = null,
                    onchainError = result.message,
                    onchainStage = result.stage.name.lowercase(),
                )
        }
    }

    /**
     * Zap a specific Nostr note (NIP-57 event zap). Use when the user
     * asks "zap this Nostr post", "tip the author of [event id]",
     * "send sats for that Nostr note about [X]", or "boost this Nostr
     * post with sats".
     *
     * Honors NIP-57 zap-split tags — a post with multiple `zap` tags
     * produces one invoice per recipient, proportional to weight, so
     * a multi-party collab post pays everyone correctly. When the user
     * has a Nostr Wallet Connect wallet configured, every split is
     * paid automatically over NIP-47 and the result reports per-
     * recipient success / failure. Without NWC, the verb returns the
     * BOLT11 invoices for manual payment.
     *
     * @param eventId 64-character hex id of the note to zap. Must be
     *   in the local cache — get it via [getNotesByUser] /
     *   [getRecentFromFollows] / [searchByHashtag] / [searchNotes]
     *   first.
     * @param sats total amount to zap, in whole sats. Capped at
     *   1,000,000.
     * @param comment optional message attached to every zap request.
     *   Capped at 280 characters.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun zapEvent(
        appFunctionContext: AppFunctionContext,
        eventId: String,
        sats: Long = 21,
        comment: String? = null,
    ): ZapEventResult {
        if (eventId.length != 64) {
            throw AppFunctionInvalidArgumentException("eventId must be 64-character hex")
        }
        val cappedSats = sats.coerceIn(1L, MAX_ZAP_SATS)
        val trimmedComment = comment.orEmpty().trim().take(MAX_ZAP_COMMENT_LENGTH)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: throw notSignedIn()
        requireInProcessSigner(account.signer)

        val note =
            account.cache.getNoteIfExists(eventId)
                ?: throw AppFunctionInvalidArgumentException(
                    "Event $eventId not in local cache. Fetch it via getNotesByUser or " +
                        "getRecentFromFollows first, or open the note in Amethyst.",
                )
        val event =
            note.event
                ?: throw AppFunctionInvalidArgumentException(
                    "Event $eventId is referenced locally but its content hasn't been observed yet.",
                )

        val client = Amethyst.instance.client.taggedAs(SubPurpose.PROFILE_METADATA, ASSISTANT)
        val totalMsats = ZapActions.satsToMillisats(cappedSats)

        // Lookups for the split resolver — first try the local cache,
        // then fall back to a one-shot network drain.
        val lookupLnAddress: suspend (HexKey) -> String? = { pk ->
            account.cache
                .checkGetOrCreateUser(pk)
                ?.metadataOrNull()
                ?.lnAddress()
                ?: fetchProfileForZap(client, account, pk)
        }
        val lookupInboxRelays: suspend (HexKey) -> Set<NormalizedRelayUrl> = { pk ->
            account.cache
                .checkGetOrCreateUser(pk)
                ?.inboxRelays()
                ?.toSet()
                .orEmpty()
        }

        val requests =
            ZapActions.buildEventZapRequestsForSplits(
                signer = account.signer,
                zappedEvent = event,
                totalAmountMillisats = totalMsats,
                senderInboxRelays = account.nip65RelayList.inboxFlow.value,
                lookupLnAddress = lookupLnAddress,
                lookupInboxRelays = lookupInboxRelays,
                comment = trimmedComment,
                zapType = ZapReceiptEvent.ZapType.PUBLIC,
            )
        if (requests.isEmpty()) {
            throw AppFunctionInvalidArgumentException(
                "No payable recipients — neither the author nor any zap-split recipient has a usable Lightning address.",
            )
        }

        val invoices =
            requests.map { req ->
                val shareSats = req.amountMillisats / 1000
                val invoiceResult =
                    runCatchingCancellable {
                        fetchInvoiceOrThrow(
                            lnAddress = req.recipient.lnAddress,
                            sats = shareSats,
                            comment = trimmedComment,
                            zapRequest = req.request,
                        )
                    }
                val invoice = invoiceResult.getOrNull()
                // Try NWC for every invoice that came back. Failed splits
                // stay as a manual invoice with nwcError set — the others
                // still go through.
                val nwc = invoice?.let { payViaDefaultSourceOrNull(account, it, note) }
                ZapInvoice(
                    recipientNpub = req.recipient.pubkey?.let { NPub.create(it) },
                    recipientPubkeyHex = req.recipient.pubkey,
                    recipientDisplayName = req.recipient.pubkey?.let { displayNameOf(it) },
                    lnAddress = req.recipient.lnAddress,
                    weight = req.recipient.weight,
                    amountSats = shareSats,
                    invoice = invoice,
                    invoiceError = invoiceResult.exceptionOrNull()?.message,
                    zapRequestId = req.request.id,
                    nwcAttempted = nwc != null,
                    nwcPaid = nwc?.success == true,
                    nwcPreimage = nwc?.preimage,
                    nwcError = nwc?.errorMessage,
                )
            }

        return ZapEventResult(
            zappedEventId = eventId,
            requestedSats = cappedSats,
            billedSats = invoices.sumOf { it.amountSats },
            comment = trimmedComment,
            invoices = invoices,
        )
    }

    /** Read lnAddress out of an already-resolved UserMetadata. */
    private fun extractLnAddressFromMetadata(info: com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata): String? = info.lnAddress()

    /**
     * Cache miss path for zap recipient profile lookup. Drain the
     * recipient's NIP-65 outbox / our home relays for their kind:0;
     * returns the lnAddress directly so callers don't have to re-parse
     * the metadata blob.
     */
    private suspend fun fetchProfileForZap(
        client: com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient,
        account: com.vitorpamplona.amethyst.commons.model.Account,
        pubkey: HexKey,
    ): String? {
        val relays =
            account.cache
                .checkGetOrCreateUser(pubkey)
                ?.outboxRelays()
                ?.toSet()
                ?.ifEmpty { account.homeRelays.flow.value }
                ?: account.homeRelays.flow.value
        if (relays.isEmpty()) return null

        val filter = Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(pubkey), limit = 1)
        return client
            .fetchAll(
                filters = relays.associateWith { listOf(filter) },
                idleTimeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            ).mapNotNull { it as? MetadataEvent }
            .maxByOrNull { it.createdAt }
            ?.contactMetaData()
            ?.lnAddress()
    }

    /** Internal result of [payViaDefaultSourceOrNull]. */
    private data class PayOutcome(
        val success: Boolean,
        val preimage: String?,
        val errorMessage: String?,
    )

    /**
     * Try to pay [bolt11] through the account's selected default payment source — an NWC
     * wallet or a CLINK debit. Returns null when no in-app source is configured (caller
     * should fall back to surfacing the invoice for manual payment), otherwise an outcome
     * with [PayOutcome.success] = true on a wallet-confirmed payment, or false (with
     * [PayOutcome.errorMessage]) on rejection or timeout.
     */
    private suspend fun payViaDefaultSourceOrNull(
        account: com.vitorpamplona.amethyst.commons.model.Account,
        bolt11: String,
        zappedNote: com.vitorpamplona.amethyst.commons.model.Note?,
    ): PayOutcome? =
        when (val source = account.settings.defaultPaymentSource()) {
            is PaymentSource.Nwc -> payViaNwc(account, bolt11, zappedNote)
            is PaymentSource.ClinkDebit -> payViaClinkDebit(account, source.wallet.pointer, bolt11)
            null -> null
        }

    /** Pays [bolt11] via a CLINK debit pointer, mapping the kind-21002 reply to a [PayOutcome]. */
    private suspend fun payViaClinkDebit(
        account: com.vitorpamplona.amethyst.commons.model.Account,
        pointer: NDebit,
        bolt11: String,
    ): PayOutcome {
        val response = ClinkDebitPayer.payInvoice(account, Amethyst.instance.torEvaluatorFlow, pointer, bolt11)
        return when {
            response == null ->
                PayOutcome(false, null, "CLINK debit wallet didn't respond within ${ClinkDebitPayer.DEFAULT_TIMEOUT_MS / 1000}s")
            response.isOk() -> PayOutcome(true, response.preimage, null)
            else ->
                PayOutcome(false, null, response.error?.takeIf { it.isNotBlank() } ?: "debit declined (code ${response.code})")
        }
    }

    /**
     * Pays [bolt11] via the default NWC wallet. The wallet's response can take a few
     * seconds; bounded by [NWC_PAYMENT_TIMEOUT_MS] so a hung wallet can't stall dispatch.
     */
    private suspend fun payViaNwc(
        account: com.vitorpamplona.amethyst.commons.model.Account,
        bolt11: String,
        zappedNote: com.vitorpamplona.amethyst.commons.model.Note?,
    ): PayOutcome {
        val deferred = CompletableDeferred<Response?>()
        // sendZapPaymentRequestFor fires onResponse exactly once when the wallet replies
        // (success, error, or NwcError). On timeout we discard the late response.
        account.zaps.sendZapPaymentRequestFor(bolt11, zappedNote) { response ->
            if (!deferred.isCompleted) deferred.complete(response)
        }
        val response =
            withTimeoutOrNull(NWC_PAYMENT_TIMEOUT_MS) { deferred.await() }
                ?: return PayOutcome(
                    success = false,
                    preimage = null,
                    errorMessage =
                        "NWC wallet didn't respond within ${NWC_PAYMENT_TIMEOUT_MS / 1000}s, " +
                            "or returned a malformed reply we couldn't decrypt",
                )

        return when (response) {
            is PayInvoiceSuccessResponse ->
                PayOutcome(true, response.result?.preimage, null)
            is PayInvoiceErrorResponse ->
                PayOutcome(false, null, response.error?.message ?: response.error?.code?.name ?: "wallet returned an unspecified pay_invoice error")
            is NwcErrorResponse ->
                PayOutcome(false, null, response.error?.message ?: response.error?.code?.name ?: "wallet returned an NWC error")
            else ->
                PayOutcome(false, null, "Unexpected NWC response type: ${response::class.simpleName}")
        }
    }

    /**
     * LNURL-pay round-trip: resolves the LN address to a callback URL,
     * posts the zap request, returns the BOLT11 invoice. Uses
     * Amethyst's roleBasedHttpClientBuilder so the request honors the
     * user's Tor / money-routing preferences.
     */
    private suspend fun fetchInvoiceOrThrow(
        lnAddress: String,
        sats: Long,
        comment: String,
        zapRequest: ZapRequestEvent,
    ): String {
        // Compute the LNURL-pay endpoint so we can ask the privacy-aware
        // HttpClient builder for the right OkHttpClient for that host.
        val endpointUrl =
            LightningAddressResolver(httpClient = okhttp3.OkHttpClient()).assembleUrl(lnAddress)
                ?: throw AppFunctionInvalidArgumentException("Couldn't resolve LN address '$lnAddress' to an LNURL-pay URL.")
        val client = Amethyst.instance.roleBasedHttpClientBuilder.okHttpClientForMoney(endpointUrl)
        val resolver = LightningAddressResolver(httpClient = client)
        val result =
            resolver.fetchInvoice(
                lnAddress = lnAddress,
                milliSats = ZapActions.satsToMillisats(sats),
                message = comment,
                zapRequest = zapRequest,
            )
        return when (result) {
            is LightningAddressResolver.Result.Success -> result.invoice
            is LightningAddressResolver.Result.Error ->
                throw AppFunctionInvalidArgumentException("Lightning service rejected the zap: ${result.message}")
        }
    }

    /**
     * Anti-impersonation guard for write verbs. Two checks, both
     * defensive against an agent that misroutes "DM Alice" to the wrong
     * Alice:
     *
     *   1. If [expectedDisplayName] is provided, verify it loosely
     *      matches the cached display name, name, or NIP-05 handle of
     *      [target]. The match is case-insensitive and bidirectional
     *      ("vitor" matches "Vitor Pamplona" and vice-versa) so the
     *      agent can pass whatever the user said without having to
     *      reconstruct the exact metadata string.
     *
     *   2. If [requireFollow] is true, verify the target is in the
     *      active account's kind:3 follow list. This is the strongest
     *      lever against wrong-recipient writes: even if Gemini picked
     *      a same-name impersonator, the user is overwhelmingly
     *      unlikely to be following them. Caller can pass
     *      `requireFollow = false` to override (e.g. zapping a stranger
     *      the user explicitly named by npub).
     *
     * Throws [AppFunctionInvalidArgumentException] with the npub +
     * cached NIP-05 + display name on mismatch so the agent can render
     * a clarifying prompt to the user.
     */
    private fun verifyExpectedRecipient(
        account: com.vitorpamplona.amethyst.commons.model.Account,
        target: HexKey,
        expectedDisplayName: String?,
        requireFollow: Boolean,
    ) {
        val user = account.cache.checkGetOrCreateUser(target)
        val meta =
            user
                ?.metadataOrNull()
                ?.flow
                ?.value
                ?.info
        val cachedName = meta?.bestName()
        val cachedNip05 = meta?.nip05?.trim()?.takeIf { it.isNotEmpty() }
        val npub = NPub.create(target)
        val expected = expectedDisplayName?.trim()?.takeIf { it.isNotEmpty() }

        if (expected != null) {
            val normalized = expected.lowercase()
            val candidates =
                listOfNotNull(
                    cachedName?.lowercase(),
                    meta
                        ?.displayName
                        ?.trim()
                        ?.lowercase()
                        ?.takeIf { it.isNotEmpty() },
                    meta
                        ?.name
                        ?.trim()
                        ?.lowercase()
                        ?.takeIf { it.isNotEmpty() },
                    cachedNip05?.lowercase(),
                    cachedNip05?.substringBefore('@')?.lowercase()?.takeIf { it.isNotEmpty() },
                )
            val matches =
                candidates.any { it == normalized || it.contains(normalized) || normalized.contains(it) }
            if (!matches) {
                throw AppFunctionInvalidArgumentException(
                    "Recipient mismatch: expectedDisplayName=\"$expected\" doesn't match the resolved " +
                        "Nostr profile (npub=$npub, displayName=${cachedName ?: "<unknown>"}, " +
                        "nip05=${cachedNip05 ?: "<none>"}). Ask the user to confirm with the agent " +
                        "before retrying — there may be multiple users with the same name on Nostr.",
                )
            }
        }

        if (requireFollow && target !in account.kind3FollowList.flow.value.authors) {
            throw AppFunctionInvalidArgumentException(
                "Recipient is not in the user's Nostr follow list (npub=$npub, " +
                    "displayName=${cachedName ?: "<unknown>"}, nip05=${cachedNip05 ?: "<none>"}). " +
                    "Ask the user to confirm they want to act on this stranger, then retry with " +
                    "requireFollow=false. This is a guard against acting on a same-name impersonator.",
            )
        }
    }

    /**
     * Reject the call when the active signer can't sign in-process —
     * NIP-55 external signers (Amber) need a foreground activity to
     * show the user an approval prompt, which we can't launch from a
     * background AppFunctionService dispatch.
     *
     * Throws [AppFunctionNotSupportedException] when the user's signer
     * is read-only, and a typed [AppFunctionNotSupportedException]
     * with a clarifying message when it's an external signer.
     */
    private fun requireInProcessSigner(signer: NostrSigner) {
        if (!signer.isWriteable()) {
            throw AppFunctionNotSupportedException(
                "Active Amethyst account is read-only (npub login). Sign in with a private key or NIP-46 bunker to publish.",
            )
        }
        // NostrSignerExternal lives in quartz/androidMain, which is on the
        // classpath here because this is android-app code. This used to compare
        // `signer::class.qualifiedName` against the fully-qualified name to
        // avoid the import; R8 renames the class in release builds, so that
        // comparison silently stopped matching and the external-signer branch
        // never ran. A type check has no such failure mode.
        if (signer is NostrSignerExternal) {
            throw AppFunctionNotSupportedException(
                "Amethyst is configured to use an external NIP-55 signer (Amber). " +
                    "Write actions from Gemini aren't supported with this signer yet — " +
                    "they require Amber's approval activity which can't launch from a " +
                    "background dispatch. Open Amethyst directly to complete the action.",
            )
        }
    }

    private fun notSignedIn(): AppFunctionNotSupportedException = AppFunctionNotSupportedException("No Amethyst account is signed in.")

    /**
     * Accepts either an npub bech32 (`npub1…`) or 64-character hex
     * pubkey and returns the 64-char lowercase hex. Throws
     * [AppFunctionInvalidArgumentException] on anything else so the
     * caller sees a typed error rather than a generic crash.
     */
    private fun decodeUserOrThrow(input: String): HexKey =
        runCatching { decodePublicKey(input.trim()).toHexKey() }
            .getOrElse {
                throw AppFunctionInvalidArgumentException(
                    "Could not decode user '$input' — expected npub1… or 64-char hex pubkey.",
                )
            }

    /**
     * Look up the cached display name for a pubkey. Returns null when no
     * kind:0 has been observed for this user yet — caller renders the
     * npub instead.
     *
     * Cheap in-memory read against the same LocalCache the foreground UI
     * uses; no relay round-trip, no allocation beyond the lookup.
     */
    private fun displayNameOf(pubkey: HexKey): String? =
        Amethyst.instance.cache
            .checkGetOrCreateUser(pubkey)
            ?.metadataOrNull()
            ?.bestName()

    private fun TextNoteEvent.toNoteHit(): NoteHit = (this as com.vitorpamplona.quartz.nip01Core.core.Event).toFeedNoteHit()

    /**
     * Project any home-feed-eligible event into a [NoteHit]. Covers
     * the broader event set the home filter accepts (kind:1, kind:6
     * reposts, kind:30023 long-form, polls, comments, etc.), so a
     * digest can carry whatever the user actually sees.
     *
     * Long content is snippet-truncated so a book-length article
     * doesn't blow up the AppFunctions response — Gemini can ask the
     * user whether to fetch the full article through a follow-up
     * verb call.
     */
    private fun com.vitorpamplona.quartz.nip01Core.core.Event.toFeedNoteHit(): NoteHit {
        val snippet =
            if (content.length > LONG_FORM_SNIPPET_LIMIT) {
                content.take(LONG_FORM_SNIPPET_LIMIT) + "…"
            } else {
                content
            }
        return NoteHit(
            eventId = id,
            kind = kind,
            npub = NPub.create(pubKey),
            pubkeyHex = pubKey,
            authorDisplayName = displayNameOf(pubKey),
            createdAt = createdAt,
            content = snippet,
        )
    }

    private fun MetadataEvent.toProfileHit(): ProfileHit {
        val meta = contactMetaData()
        return ProfileHit(
            npub = NPub.create(pubKey),
            pubkeyHex = pubKey,
            displayName = meta?.bestName(),
            about = meta?.about,
            nip05 = meta?.nip05,
            picture = meta?.picture,
            lnAddress = meta?.lnAddress(),
        )
    }

    /**
     * Searches Nostr notes for [query] via NIP-50 full-text search across the
     * active account's configured search relays (kind:10007), falling back to
     * Amethyst's curated default search-relay set when none is configured.
     *
     * Defaults to short text notes (kind:1) only. Currently no way to widen
     * to long-form or other kinds — add a parameter when the need is real;
     * App Functions doesn't support `List<Int>` parameters in alpha09.
     *
     * @param query free-form search text.
     * @param limit max number of notes to return — capped to 50.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchNotes(
        appFunctionContext: AppFunctionContext,
        query: String,
        limit: Int = 20,
    ): SearchNotesResult {
        val cappedLimit = limit.coerceIn(1, 50)
        val filter = SearchActions.searchNotesFilter(query, limit = cappedLimit) ?: return SearchNotesResult.empty()

        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchNotesResult.empty()
        val client = Amethyst.instance.client.taggedAs(SubPurpose.SEARCH, ASSISTANT)

        val relays = account.searchRelayList.flow.value
        if (relays.isEmpty()) return SearchNotesResult.empty()

        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                idleTimeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val hits =
            events
                .mapNotNull { it as? TextNoteEvent }
                // Sorted newest-first by fetchAll already, but the cast may
                // have dropped non-kind:1 events from a relay that ignored
                // our kinds filter.
                .take(cappedLimit)
                .map { it.toNoteHit() }

        return SearchNotesResult(matches = hits)
    }

    /**
     * Lists the active account's current follow set — the people the signed-in
     * user follows per their latest NIP-02 kind:3 contact list.
     *
     * Returned entries include best-effort display names sourced from each
     * user's cached kind:0; users with no cached metadata appear with
     * [FollowedUser.displayName] null. The order matches the on-disk follow
     * list (which is the order the user followed them in).
     *
     * @param limit cap on entries returned — capped to 500. Set to 0 for the
     *   full list when there's no specific bound.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getFollowing(
        appFunctionContext: AppFunctionContext,
        limit: Int = 100,
    ): FollowingResult {
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return FollowingResult.empty()

        // userList resolves authors through LocalCache so display names /
        // pictures / nip05 are filled in for anyone whose kind:0 we've seen.
        val users = account.kind3FollowList.userList.value
        val effectiveLimit = if (limit <= 0) users.size else limit.coerceIn(1, 500)

        val out =
            users
                .take(effectiveLimit)
                .map { user ->
                    val meta = user.metadataOrNull()
                    FollowedUser(
                        npub = NPub.create(user.pubkeyHex),
                        pubkeyHex = user.pubkeyHex,
                        displayName = meta?.bestName(),
                        nip05 = meta?.nip05(),
                        picture = meta?.profilePicture(),
                    )
                }

        return FollowingResult(totalFollowing = users.size, returned = out)
    }

    companion object {
        /** Detail on every relay request made for a system assistant, so its traffic is told apart from the app's own. */
        private const val ASSISTANT = "Assistant"

        /**
         * 6-second fetch window. App Functions invocations are user-initiated
         * foreground requests in the Gemini UI — anything beyond a few seconds
         * is a poor user experience.
         */
        private const val GEMINI_FETCH_TIMEOUT_MS = 6_000L

        /**
         * Cap on the content payload returned from [searchArticles] — NIP-23
         * articles can be book-length; truncate so the AppFunctions response
         * stays bounded. Gemini can show the snippet and ask the user
         * whether to fetch the full article.
         */
        private const val LONG_FORM_SNIPPET_LIMIT = 2_000

        /**
         * Cap on the body of a Gemini-driven write (postNote / sendDm).
         * Anything larger is almost certainly an accidentally-pasted
         * document; bail out with a typed error instead of silently
         * publishing a wall of text to relays.
         */
        private const val MAX_NOTE_LENGTH = 8_000

        /**
         * Per-publish ack window. We wait this long for OK responses
         * from each relay; relays that don't answer in time are
         * reported as `rejectedBy` (no ack, no event). 15 s lines up
         * with what `cli/Context.publish` uses.
         */
        private const val PUBLISH_TIMEOUT_SECS = 15L

        /** Upper bound on a single zap. Anything above this is almost
         *  certainly a typo; bail out instead of letting Gemini bill
         *  the user a million sats by accident. */
        private const val MAX_ZAP_SATS = 1_000_000L

        /** LN providers typically reject longer comments — capping at
         *  280 keeps us under the most aggressive ceilings while still
         *  fitting a tweet-length thank-you note. */
        private const val MAX_ZAP_COMMENT_LENGTH = 280

        /** Max time to wait for an NWC wallet to respond to a
         *  pay_invoice request. Mobile wallets typically settle in
         *  a few seconds; 30s is generous without letting a stuck
         *  wallet stall the dispatch indefinitely. */
        private const val NWC_PAYMENT_TIMEOUT_MS = 30_000L

        /** Cap on the number of distinct hashtags surfaced in a feed
         *  digest. Picked to fit a one-paragraph summary without
         *  noise — the long tail won't help the LLM. */
        private const val TOP_HASHTAGS_LIMIT = 10

        /** Cap on the number of mentioned users surfaced in a feed
         *  digest. Same rationale as TOP_HASHTAGS_LIMIT. */
        private const val TOP_MENTIONS_LIMIT = 10

        /** Overall budget for the NIP-05 verification pass inside
         *  [searchProfiles]. We fan out one HTTP request per result so
         *  the wall-clock is dominated by the slowest .well-known.
         *  4s is short enough to keep search snappy and long enough to
         *  catch most genuine refutations. On timeout we keep all
         *  candidates rather than censoring them. */
        private const val NIP05_FILTER_TIMEOUT_MS = 4_000L
    }
}

/**
 * Single match in [SearchProfilesResult]. Nullable fields let callers
 * render whatever subset of metadata the profile happens to publish.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class ProfileHit(
    /** Bech32 npub identifier (`npub1…`) for the matched profile. */
    val npub: String,
    /** Hex-encoded pubkey (same identity as [npub], non-bech32 form). */
    val pubkeyHex: String,
    /** Best-effort display name (display_name then name). */
    val displayName: String?,
    /** Profile bio / about. */
    val about: String?,
    /** NIP-05 verified handle, e.g. `alice@example.com`. */
    val nip05: String?,
    /** Avatar image URL. */
    val picture: String?,
    /** Lightning address (lud16 preferred, otherwise lud06 LNURL). */
    val lnAddress: String?,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
class SearchProfilesResult(
    /** Matched profiles, deduplicated by pubkey and sorted newest-first. */
    val matches: List<ProfileHit>,
) {
    companion object {
        fun empty() = SearchProfilesResult(matches = emptyList())
    }
}

/** One hashtag and how many notes in the digest window carried it.
 *  Lowercased and stripped of the leading `#`. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class HashtagFrequency(
    /** The hashtag value without the leading `#`, lowercased. */
    val tag: String,
    /** Number of notes in the digest window that carried this tag. */
    val noteCount: Int,
)

/** One pubkey that was mentioned via `p` tags in the digest window,
 *  with display name resolved from the local kind:0 cache when known. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class MentionFrequency(
    /** Bech32 npub of the mentioned user. */
    val npub: String,
    /** Hex pubkey of the mentioned user. */
    val pubkeyHex: String,
    /** Best-effort display name from the local kind:0 cache. Null when
     *  the user's profile hasn't been seen yet — caller falls back to
     *  the npub. */
    val displayName: String?,
    /** Number of notes in the digest window that mention this user. */
    val mentionCount: Int,
)

/**
 * Structured snapshot of the active account's Nostr feed for
 * [AmethystAppFunctions.getFeedDigest]. The LLM uses the aggregate
 * signals (counts + top hashtags + top mentions) to write a one- or
 * two-paragraph summary; the raw [notes] list is included for
 * follow-up questions ("which post was about X?").
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class FeedDigestResult(
    /** Window size in hours actually queried (after capping). */
    val windowHours: Int,
    /** Total notes scanned for stats. May exceed [notes].size when the
     *  body was truncated to fit the LLM prompt. */
    val totalNoteCount: Int,
    /** Distinct authors who posted in the window. */
    val uniqueAuthorCount: Int,
    /** Top hashtags by note count — at most 10. */
    val topHashtags: List<HashtagFrequency>,
    /** Most-mentioned users by note count — at most 10. */
    val topMentions: List<MentionFrequency>,
    /** Notes themselves (truncated to the caller's maxNotes). Newest-
     *  first; same fields as [NoteHit] returned by the search verbs. */
    val notes: List<NoteHit>,
) {
    companion object {
        fun empty() =
            FeedDigestResult(
                windowHours = 0,
                totalNoteCount = 0,
                uniqueAuthorCount = 0,
                topHashtags = emptyList(),
                topMentions = emptyList(),
                notes = emptyList(),
            )
    }
}

/** Single match in [SearchNotesResult] or entry in a feed result. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class NoteHit(
    /** Hex event id of the note. */
    val eventId: String,
    /** Nostr event kind. 1 = short text note, 6 = repost, 30023 =
     *  long-form article, 1111 = comment, 9802 = highlight, 1068 =
     *  poll, etc. Lets the LLM distinguish "Alice posted a note"
     *  from "Alice published an article" or "Alice ran a poll". */
    val kind: Int,
    /** Bech32 npub of the note's author. */
    val npub: String,
    /** Hex pubkey of the note's author. */
    val pubkeyHex: String,
    /** Best-effort display name of the author from the local kind:0 cache.
     *  Null when the author's profile hasn't been seen yet — caller renders
     *  the npub instead. */
    val authorDisplayName: String?,
    /** Unix-seconds timestamp the note was created at. */
    val createdAt: Long,
    /** Raw content of the note (plain text, may contain Nostr URIs /
     *  hashtags). Truncated at ~2000 chars for very long content; the
     *  full event is reachable by its [eventId] via a follow-up verb. */
    val content: String,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
class SearchNotesResult(
    /** Matched notes, sorted newest-first by created_at. */
    val matches: List<NoteHit>,
) {
    companion object {
        fun empty() = SearchNotesResult(matches = emptyList())
    }
}

/** Single entry in [FollowingResult]. Metadata fields may be null when the
 *  user's kind:0 hasn't been cached locally. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class FollowedUser(
    /** Bech32 npub of the followed user. */
    val npub: String,
    /** Hex pubkey of the followed user. */
    val pubkeyHex: String,
    /** Best-effort display name (display_name then name). */
    val displayName: String?,
    /** NIP-05 verified handle, e.g. `alice@example.com`. */
    val nip05: String?,
    /** Avatar image URL. */
    val picture: String?,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
class FollowingResult(
    /** Total number of follows in the active account's kind:3 — may exceed
     *  [returned] when the caller passed a limit. */
    val totalFollowing: Int,
    /** Subset of follows returned to the caller, in original on-disk order. */
    val returned: List<FollowedUser>,
) {
    companion object {
        fun empty() = FollowingResult(totalFollowing = 0, returned = emptyList())
    }
}

/**
 * Result of [AmethystAppFunctions.getProfile]. Distinguishes "user has no
 * cached + observable kind:0 metadata" (`found = false`) from "user has a
 * stub profile with empty fields" — the latter shouldn't normally happen
 * but the explicit flag keeps callers from rendering a hollow card.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class GetProfileResult(
    /** True when a kind:0 was found (cache or relay). False means the
     *  user exists as a pubkey but no profile event was reachable. */
    val found: Boolean,
    /** Resolved profile when [found] is true, otherwise a stub with
     *  pubkey-only fields populated. */
    val profile: ProfileHit?,
) {
    companion object {
        fun notFound(pubkeyHex: String) =
            GetProfileResult(
                found = false,
                profile =
                    ProfileHit(
                        npub = NPub.create(pubkeyHex),
                        pubkeyHex = pubkeyHex,
                        displayName = null,
                        about = null,
                        nip05 = null,
                        picture = null,
                        lnAddress = null,
                    ),
            )
    }
}

/**
 * Aggregate of NIP-57 zaps received in a recent time window. Returned
 * by [AmethystAppFunctions.getZapsReceived].
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class ZapsReceivedResult(
    /** Window size in hours that was actually queried (after capping). */
    val windowHours: Int,
    /** Sum of sats from every parseable bolt11 invoice in the window. */
    val totalSats: Long,
    /** Total kind:9735 receipts observed — includes ones with unparseable invoices. */
    val zapCount: Int,
    /** Distinct zapping pubkeys, best-effort from the `P` / second-`p` tag. */
    val uniqueZapperCount: Int,
    /** Receipts whose bolt11 couldn't be parsed and didn't contribute to [totalSats]. */
    val unparseableInvoiceCount: Int,
) {
    companion object {
        fun empty() =
            ZapsReceivedResult(
                windowHours = 0,
                totalSats = 0L,
                zapCount = 0,
                uniqueZapperCount = 0,
                unparseableInvoiceCount = 0,
            )
    }
}

/** One decrypted NIP-17 direct message returned by [AmethystAppFunctions.getRecentDms]. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class DmMessage(
    /** Bech32 npub of the sender. */
    val fromNpub: String,
    /** Hex pubkey of the sender. */
    val fromPubkeyHex: String,
    /** Best-effort display name of the sender from the local kind:0 cache. */
    val fromDisplayName: String?,
    /** True when the active account sent this message — useful for the
     *  caller to distinguish "Alice said X" from "I said Y" when both
     *  appear in the same thread snapshot. */
    val sentByMe: Boolean,
    /** Plaintext message body. */
    val content: String,
    /** Unix-seconds timestamp of the inner kind:14 event. */
    val createdAt: Long,
)

/** Decrypted recent NIP-17 DMs in a time window. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class DmsResult(
    /** Window size in hours that was actually queried. */
    val windowHours: Int,
    /** Messages, newest first. Capped to the caller's limit. */
    val messages: List<DmMessage>,
) {
    companion object {
        fun empty() = DmsResult(windowHours = 0, messages = emptyList())
    }
}

/** Single hit from [AmethystAppFunctions.getLiveStreams]. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class LiveStreamHit(
    /** Hex event id of the kind:30311 announcement. */
    val eventId: String,
    /** Stream title from the `title` tag, or null when absent. */
    val title: String?,
    /** Short description from the `summary` tag, or null when absent. */
    val summary: String?,
    /** The URL where the stream is playable (HLS / WebRTC / etc.) from
     *  the `streaming` tag. Null when the announcement carries no
     *  streaming endpoint — caller has nothing to play. */
    val streamingUrl: String?,
    /** Bech32 npub of the host, when a host tag is present. */
    val hostNpub: String?,
    /** Hex pubkey of the host, when a host tag is present. */
    val hostPubkeyHex: String?,
    /** Best-effort display name of the host from the local kind:0 cache. */
    val hostDisplayName: String?,
    /** Unix-seconds timestamp the stream's `starts` tag points to. */
    val startsAt: Long?,
    /** Unix-seconds timestamp of the kind:30311 event itself. */
    val createdAt: Long,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
class LiveStreamsResult(
    /** Currently-live streams, in the order they were observed. */
    val streams: List<LiveStreamHit>,
) {
    companion object {
        fun empty() = LiveStreamsResult(streams = emptyList())
    }
}

/**
 * Summary of the active Nostr account on this device. Returned by
 * [AmethystAppFunctions.getActiveAccountInfo].
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class AccountInfoResult(
    /** False when no account is currently logged into Amethyst. */
    val signedIn: Boolean,
    /** Bech32 npub of the active account, or null when signed out. */
    val npub: String?,
    /** Hex pubkey of the active account, or null when signed out. */
    val pubkeyHex: String?,
    /** Best-effort display name from cached kind:0. */
    val displayName: String?,
    /** NIP-05 verified handle. */
    val nip05: String?,
    /** Number of pubkeys in the user's current kind:3 follow list. */
    val followCount: Int,
    /** Number of NIP-65 outbox / home relays configured. */
    val outboxRelayCount: Int,
    /** Number of NIP-17 DM-inbox relays (kind:10050) configured. */
    val dmRelayCount: Int,
) {
    companion object {
        fun signedOut() =
            AccountInfoResult(
                signedIn = false,
                npub = null,
                pubkeyHex = null,
                displayName = null,
                nip05 = null,
                followCount = 0,
                outboxRelayCount = 0,
                dmRelayCount = 0,
            )
    }
}

/**
 * Result of a single-event write verb (postNote / followUser /
 * unfollowUser). When the verb is a no-op — already following, not
 * following, content unchanged — [changed] is false and [eventId] is
 * null; the relay lists are empty for the same reason.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class WriteResult(
    /** True when a new event was actually signed and published. */
    val changed: Boolean,
    /** Hex event id of the signed event, or null when the verb was a no-op. */
    val eventId: String?,
    /** Relays that ACK'd the publish. */
    val publishedTo: List<String>,
    /** Relays that rejected the event or didn't answer in time. */
    val rejectedBy: List<String>,
) {
    companion object {
        fun unchanged() =
            WriteResult(
                changed = false,
                eventId = null,
                publishedTo = emptyList(),
                rejectedBy = emptyList(),
            )

        fun from(
            eventId: String,
            ack: Map<com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl, Boolean>,
        ) = WriteResult(
            changed = true,
            eventId = eventId,
            publishedTo = ack.filterValues { it }.keys.map { it.url },
            rejectedBy = ack.filterValues { !it }.keys.map { it.url },
        )
    }
}

/**
 * Per-recipient delivery status for a NIP-17 DM send. A 1:1 DM
 * produces two entries — the recipient's wrap and the sender's own
 * copy on their own DM-inbox relays. [relaySource] reports which
 * bucket the relays were drawn from: `kind_10050`, `nip65_read`,
 * `bootstrap`, or `none`.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class DmDelivery(
    /** Bech32 npub of the recipient this wrap was addressed to. */
    val recipientNpub: String,
    /** Hex pubkey of the recipient. */
    val recipientPubkeyHex: String,
    /** Hex event id of the kind:1059 gift wrap published to this recipient. */
    val wrapId: String,
    /** Relays that ACK'd this wrap. */
    val publishedTo: List<String>,
    /** Relays that rejected this wrap or didn't answer in time. */
    val rejectedBy: List<String>,
    /** Bucket the relays were resolved from: kind_10050 / nip65_read / bootstrap / none. */
    val relaySource: String,
)

/** Result of [AmethystAppFunctions.sendDm]. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class SendDmResult(
    /** Hex event id of the inner kind:14 (the plaintext message — only the
     *  signer and the recipient know it; relays only see the kind:1059 wraps). */
    val messageEventId: String,
    /** One entry per gift-wrap delivery. */
    val deliveries: List<DmDelivery>,
)

/**
 * Result of [AmethystAppFunctions.zapUser]. Carries either the
 * Lightning BOLT11 invoice (+ NWC payment outcome when configured) or
 * the onchain Bitcoin broadcast result, depending on the rail used.
 * The [chain] field tells callers which set of fields to read.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class ZapResult(
    /** Transport rail actually used: "lightning" or "onchain". */
    val chain: String,
    /** Bech32 npub of the zap recipient. */
    val recipientNpub: String,
    /** Hex pubkey of the recipient. */
    val recipientPubkeyHex: String,
    /** Best-effort display name from the local kind:0 cache. */
    val recipientDisplayName: String?,
    /** LN address the invoice was fetched from. Empty for onchain zaps. */
    val lnAddress: String,
    /** Amount actually billed (after capping). For onchain, the
     *  miner fee is reported separately in [onchainFeeSats]. */
    val amountSats: Long,
    /** Comment attached to the zap (truncated to 280 chars). */
    val comment: String,
    /** BOLT11 invoice — populated for Lightning, empty for onchain.
     *  Pay manually if [nwcPaid] is false. */
    val invoice: String,
    /** Hex event id of the signed kind:9734 zap request. Empty for onchain. */
    val zapRequestId: String,
    /** True when the user has NWC configured and we tried to auto-pay.
     *  Always false for onchain. */
    val nwcAttempted: Boolean,
    /** True only when an NWC wallet confirmed the payment. */
    val nwcPaid: Boolean,
    /** Payment preimage from the wallet on success, otherwise null. */
    val nwcPreimage: String?,
    /** Failure reason when [nwcAttempted] is true but [nwcPaid] is false. */
    val nwcError: String?,
    /** Bitcoin transaction id on a successful onchain zap, or — when
     *  the tx was broadcast but the receipt failed to publish — the
     *  txid so the user can verify the payment manually. Null for
     *  Lightning zaps. */
    val onchainTxid: String?,
    /** Miner fee paid in sats on a successful onchain zap. */
    val onchainFeeSats: Long?,
    /** Change returned to the sender's address on an onchain zap. */
    val onchainChangeSats: Long?,
    /** Hex event id of the kind:8333 onchain zap receipt. */
    val onchainReceiptEventId: String?,
    /** Failure reason when an onchain zap failed. */
    val onchainError: String?,
    /** Stage at which an onchain zap failed: "loading_utxos",
     *  "building", "signing", "broadcasting", or "publishing"
     *  (last means the tx is on-chain but the receipt didn't land
     *  on relays — the payment is still real). */
    val onchainStage: String?,
)

/**
 * Per-recipient BOLT11 invoice for an event zap. Multiple invoices
 * appear when the zapped note carries NIP-57 zap-split tags. When NWC
 * is configured we try to pay each invoice automatically; per-split
 * NWC results are reported in the `nwc*` fields.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class ZapInvoice(
    /** Bech32 npub of the recipient, or null when the split tag carried
     *  only an LN address with no pubkey. */
    val recipientNpub: String?,
    /** Hex pubkey of the recipient, or null when only an LN address was given. */
    val recipientPubkeyHex: String?,
    /** Best-effort display name from the cache, when the recipient is known. */
    val recipientDisplayName: String?,
    /** LN address the invoice was fetched from. */
    val lnAddress: String,
    /** Relative weight in the zap split — 1.0 for unweighted recipients. */
    val weight: Double,
    /** This recipient's share of the total in whole sats. */
    val amountSats: Long,
    /** BOLT11 invoice, or null when the Lightning provider failed
     *  (see [invoiceError] for the reason). */
    val invoice: String?,
    /** Failure reason from the Lightning provider when [invoice] is null. */
    val invoiceError: String?,
    /** Hex event id of this recipient's kind:9734 zap request. */
    val zapRequestId: String,
    /** True when NWC was configured and we tried to auto-pay this
     *  invoice. False when no NWC was set up or the invoice itself
     *  couldn't be fetched. */
    val nwcAttempted: Boolean,
    /** True only when an NWC wallet confirmed the payment for this split. */
    val nwcPaid: Boolean,
    /** Payment preimage from the wallet on success, otherwise null. */
    val nwcPreimage: String?,
    /** Failure reason when [nwcAttempted] is true but [nwcPaid] is false. */
    val nwcError: String?,
)

/**
 * Result of [AmethystAppFunctions.zapEvent]. Total billed sats may
 * differ from requested by a few sats due to whole-sat rounding in the
 * splits — same drift the foreground UI has.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class ZapEventResult(
    /** Hex event id of the note being zapped. */
    val zappedEventId: String,
    /** Total sats the caller asked for (capped, post-validation). */
    val requestedSats: Long,
    /** Sum of per-recipient sats actually billed across all invoices. */
    val billedSats: Long,
    /** Comment attached to every zap request. */
    val comment: String,
    /** One invoice per recipient — multiple entries when the note has
     *  NIP-57 zap-split tags. Pay each one in a Lightning wallet to
     *  complete the zap; invoices with non-null [ZapInvoice.invoiceError]
     *  couldn't be fetched and won't go through. */
    val invoices: List<ZapInvoice>,
)
