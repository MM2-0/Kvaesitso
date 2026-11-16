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
package com.vitorpamplona.amethyst.commons.connectedApps.signers

import com.vitorpamplona.quartz.experimental.library.BlossomPieceIndexEvent
import com.vitorpamplona.quartz.experimental.library.LearningResourceEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationContentEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip25Reactions.ExternalReactionEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileMetadataEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The per-app Nostr signer permission ledger. Decides whether a signing or encryption
 * operation should auto-allow, auto-deny, or ask the user, by consulting:
 *
 * 1. Per-operation overrides ([NostrSignerPermissionStore.loadOpDecision]) — these always win.
 * 2. The app's [AppSignerPolicy] trust level ([NostrSignerPermissionStore.loadPolicy]).
 * 3. The built-in "reasonable" set ([REASONABLE_SIGN_KINDS] + encrypt are auto-allowed) when policy is [AppSignerPolicy.REASONABLE].
 *
 * When no policy has been set (`null`), [decide] returns [NostrOpDecision.ASK], which triggers the
 * first-connect dialog in the broker.
 */
class NostrSignerPermissionLedger(
    val store: NostrSignerPermissionStore,
) {
    /**
     * `true` when a trust level has been set for [coordinate] — i.e. the "Connect to Nostr"
     * dialog has already been shown and the user made a choice.
     */
    suspend fun hasPolicy(coordinate: String): Boolean = store.loadPolicy(coordinate) != null

    /**
     * The authorization verdict for ([coordinate], [op]) based on stored policy + per-op overrides.
     * Checks expiry: if a timed override has passed [now], it is cleared and the policy-level decision
     * is returned instead.
     */
    suspend fun decide(
        coordinate: String,
        op: NostrSignerOp,
        now: Long = TimeUtils.now(),
    ): NostrOpDecision {
        store.loadOpDecision(coordinate, op)?.let { decision ->
            val expiresAt = store.loadOpExpiry(coordinate, op)
            if (expiresAt != null && now > expiresAt) {
                store.clearOpDecision(coordinate, op)
                store.clearOpExpiry(coordinate, op)
            } else {
                return decision
            }
        }
        return when (store.loadPolicy(coordinate)) {
            AppSignerPolicy.FULL_TRUST -> NostrOpDecision.ALLOW
            AppSignerPolicy.PARANOID -> NostrOpDecision.ASK
            AppSignerPolicy.REASONABLE -> reasonableDecision(op)
            null -> NostrOpDecision.ASK
        }
    }

    /** Stores the user's chosen trust level for [coordinate]. */
    suspend fun setPolicy(
        coordinate: String,
        policy: AppSignerPolicy,
    ) = store.storePolicy(coordinate, policy)

    /** Stores a per-operation override for ([coordinate], [op]). */
    suspend fun setOpDecision(
        coordinate: String,
        op: NostrSignerOp,
        decision: NostrOpDecision,
    ) = store.storeOpDecision(coordinate, op, decision)

    /** Stores a time-bound per-operation override that expires at [expiresAt] (Unix epoch seconds). */
    suspend fun setTimedOpDecision(
        coordinate: String,
        op: NostrSignerOp,
        decision: NostrOpDecision,
        expiresAt: Long,
    ) {
        store.storeOpDecision(coordinate, op, decision)
        store.storeOpExpiry(coordinate, op, expiresAt)
    }

    /** Records the current time as the last-used timestamp for [coordinate]. */
    suspend fun updateLastUsed(
        coordinate: String,
        now: Long = TimeUtils.now(),
    ) = store.storeLastUsed(coordinate, now)

    /** The last-used timestamp for [coordinate], or `null` if never used. */
    suspend fun lastUsed(coordinate: String): Long? = store.loadLastUsed(coordinate)

    /** Removes a per-operation override (and any expiry), reverting to the policy-level decision. */
    suspend fun revokeOpDecision(
        coordinate: String,
        op: NostrSignerOp,
    ) {
        store.clearOpDecision(coordinate, op)
        store.clearOpExpiry(coordinate, op)
    }

    /** Removes all signer permissions for [coordinate] — trust level and all per-op overrides. */
    suspend fun revokeAll(coordinate: String) = store.clearAll(coordinate)

    /**
     * Applies the persisted part of a user's per-operation consent [grant] to [coordinate], so a
     * "remember" choice sticks. The transient variants ([SignerOpGrant.AllowOnce],
     * [SignerOpGrant.DenyOnce], [SignerOpGrant.AllowForSession]) persist nothing — the caller keeps
     * session grants in memory. Mirrors the broker's per-op recording so every consent surface
     * (napplet, browser, NIP-46) writes the ledger the same way.
     */
    suspend fun record(
        coordinate: String,
        grant: SignerOpGrant,
    ) {
        when (grant) {
            is SignerOpGrant.AllowForOp -> setOpDecision(coordinate, grant.op, NostrOpDecision.ALLOW)
            is SignerOpGrant.AllowUntil -> setTimedOpDecision(coordinate, grant.op, NostrOpDecision.ALLOW, grant.expiresAt)
            is SignerOpGrant.AllowAll -> setPolicy(coordinate, AppSignerPolicy.FULL_TRUST)
            is SignerOpGrant.DenyForOp -> setOpDecision(coordinate, grant.op, NostrOpDecision.DENY)
            SignerOpGrant.AllowOnce, SignerOpGrant.DenyOnce, is SignerOpGrant.AllowForSession -> Unit
        }
    }

    private fun reasonableDecision(op: NostrSignerOp): NostrOpDecision =
        when (op) {
            is NostrSignerOp.SignKind ->
                if (op.kind in REASONABLE_SIGN_KINDS) NostrOpDecision.ALLOW else NostrOpDecision.ASK
            NostrSignerOp.Encrypt -> NostrOpDecision.ALLOW
            // Decryption always asks under REASONABLE — both the broad grant and the per-counterparty
            // one, which is only ever created by an explicit "always allow for X" in the dialog.
            NostrSignerOp.Decrypt, is NostrSignerOp.DecryptFrom -> NostrOpDecision.ASK
        }

    companion object {
        /**
         * Event kinds auto-approved under [AppSignerPolicy.REASONABLE].
         *
         * Most of these are *public, non-destructive content* — an event the user creates and could
         * delete afterwards, in the same risk class as the original kind 1/6/7 set (notes, reposts,
         * reactions, pictures, videos, voice, public/live/relay chat, threads, polls, comments,
         * highlights, code snippets, file metadata, torrents, long-form articles, wiki, status).
         * Some are *addressable* (long-form 30023, wiki 30818, video 34235/34236): re-signing
         * with the same `d` tag replaces the app's own prior version at that address — an accepted
         * trade-off, since an app that can already post arbitrary notes could do equal reputational harm.
         * The set also includes two harmless non-content signatures:
         *  - **zap request** (9734) — moves nothing; it only fetches a Lightning invoice. The payment
         *    itself is the separately-gated `value.payInvoice` capability that prompts on *every* use
         *    regardless of policy.
         *
         * **Relay auth (22242) is deliberately NOT here.** It looks harmless — the event is ephemeral
         * and bound to one relay+challenge, so it cannot be replayed elsewhere. But replay is not the
         * threat: the requesting app supplies the `relay` and `challenge` tags verbatim, so it can ask
         * for a *fresh* signature naming any relay it likes, then AUTH to that relay as the user. That
         * yields read access to whatever the relay gates behind AUTH — notably the user's kind-1059
         * giftwrap inbox and its full DM metadata — and burns the quota on paid relays, which bill
         * whoever authenticates. Amethyst auto-signing AUTH for relays *the user configured* is not
         * the same as letting a third party name the relay.
         *
         * None of the members can silently: spend money, overwrite account configuration (profile 0,
         * contacts 3, relay/mute/bookmark lists are replaceable — a bad write can wipe settings),
         * delete content (kind 5), or leak private data. Notable exclusions that stay ASK:
         *  - **nutzap** (9321) — publishing one *is* the payment (it carries spendable ecash proofs).
         *  - **reports** (1984) — a public accusation against *another person*, and web-of-trust apps
         *    (Brainstorm) fold reports into that person's trust score. One tap in an app's menu
         *    published a spam report with no prompt; it must be asked, like the other actions that
         *    touch someone else.
         *  - **NIP-98 HTTP auth** (27235) — authorizes an arbitrary HTTP request as the user, including
         *    destructive/admin calls (NIP-96 blob deletes, NIP-86 relay management); blast radius is too
         *    broad to auto-approve.
         *  - **decryption** ([NostrSignerOp.Decrypt]) and DMs — reveal private content.
         *  - **replaceable configuration** (profile 0, contacts 3, and the 10000-range lists above) —
         *    unlike addressable *content*, these hold account settings a bad write can silently wipe.
         *
         * Deliberately conservative: when a kind's blast radius is unclear, it is left out so the user
         * is asked rather than surprised.
         */
        @Suppress("DEPRECATION") // TorrentCommentEvent is deprecated (NIP-22) but still a reasonable sign kind
        val REASONABLE_SIGN_KINDS: Set<Int> =
            setOf(
                TextNoteEvent.KIND, // 1 — short text notes & replies
                RepostEvent.KIND, // 6 — reposts of text notes
                ReactionEvent.KIND, // 7 — likes / emoji reactions
                ChatEvent.KIND, // 9 — NIP-C7 relay chat messages (public)
                ThreadEvent.KIND, // 11 — NIP-7D thread posts (same risk as kind 1)
                GenericRepostEvent.KIND, // 16 — reposts of non-text content (same risk as kind 6)
                PictureEvent.KIND, // 20 — picture posts (same risk as kind 1)
                VideoNormalEvent.KIND, // 21 — video posts (same risk as a picture)
                VideoShortEvent.KIND, // 22 — short-form video posts (same risk as a picture)
                PublicMessageEvent.KIND, // 24 — NIP-A4 public messages (plaintext, public)
                ChannelMessageEvent.KIND, // 42 — public chat messages
                PollResponseEvent.KIND, // 1018 — voting in a poll (additive, like a reaction)
                FileMetadataEvent.KIND, // 1063 — NIP-94 file metadata (shares a file reference)
                PollEvent.KIND, // 1068 — creating a poll (additive public content)
                CommentEvent.KIND, // 1111 — NIP-22 threaded comments (same risk as kind 1)
                VoiceEvent.KIND, // 1222 — voice messages (audio post, like a picture/video)
                VoiceReplyEvent.KIND, // 1244 — voice replies
                LiveActivitiesChatMessageEvent.KIND, // 1311 — live-stream chat (sibling of kind 42)
                CodeSnippetEvent.KIND, // 1337 — NIP-C0 code snippets (additive public content)
                TorrentEvent.KIND, // 2003 — NIP-35 torrent announcements (additive public content)
                TorrentCommentEvent.KIND, // 2004 — NIP-35 torrent comments
                HighlightEvent.KIND, // 9802 — highlighted snippets shared publicly
                ZapRequestEvent.KIND, // 9734 — Lightning zap request; the payment itself still prompts
                LongFormContentEvent.KIND, // 30023 — NIP-23 long-form articles (addressable content)
                UserStatusEvent.KIND, // 30315 — ephemeral user status / presence
                WikiArticleEvent.KIND, // 30818 — NIP-54 wiki articles (addressable content)
                AddressableNormalVideoEvent.KIND, // 34235 — addressable horizontal video (NIP-71)
                AddressableShortVideoEvent.KIND, // 34236 — addressable vertical video (NIP-71)
                PublicationIndexEvent.KIND, // 30040 — NKBIP-01 publication index (addressable content)
                LearningResourceEvent.KIND, // 30142 — learning resources (addressable content)
                BlossomPieceIndexEvent.KIND, // 32176 — Blossom piece indexes (addressable content)
                // Deliberately NOT auto-signed, by the rule above: a kind-30045 directory is a
                // replaceable *list* one bad write wipes; and ratings (34259), relay reviews
                // (31987) and wiki redirects (30819) attach a replaceable opinion or a redirect
                // to the user's identity, which an app should have to ask before writing.
                ExternalReactionEvent.KIND, // 17 — NIP-25 reactions to non-nostr targets
                PublicationContentEvent.KIND, // 30041 — NKBIP-01 publication section (addressable content)
            )
    }
}
