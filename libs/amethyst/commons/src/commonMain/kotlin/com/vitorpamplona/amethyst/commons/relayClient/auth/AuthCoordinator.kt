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
package com.vitorpamplona.amethyst.commons.relayClient.auth

import androidx.collection.LruCache
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthContext
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthVerdict
import com.vitorpamplona.amethyst.commons.util.isDebug
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ScreenAuthAccount(
    val account: Account,
)

@Stable
class AuthCoordinator(
    val client: INostrClient,
    private val scope: CoroutineScope,
    val promptBus: RelayAuthPromptBus = RelayAuthPromptBus(),
) {
    private val authWithAccounts = ListWithUniqueSetCache<ScreenAuthAccount, Account> { it.account }

    val receiver =
        RelayAuthenticator(
            client,
            scope,
            signWithAllLoggedInUsers = { relayUrl, authTemplate, interactive ->
                // Concord plane traffic is gated behind NIP-42 as the derived *stream key*, not the
                // user: a relay serves a plane's kind-1059 wraps only to a connection authenticated
                // as that stream key. These AUTHs expose no user identity (ephemeral derived keys)
                // and are signed locally, so we always attach them — independent of the per-account
                // policy below — or Concord channels/messages never load. No-op for non-Concord relays.
                val streamAuths = signConcordStreamAuths(relayUrl, authTemplate)

                // Reconstruct *why* this relay wants auth from what the shared client is doing with
                // it. Built lazily so accounts that fail the first-party gate below don't pay for it.
                val context by
                    lazy(LazyThreadSafetyMode.NONE) {
                        RelayAuthContext(
                            relayUrl = relayUrl.url,
                            purposes =
                                RelayAuthPurposeDeriver.derive(
                                    pendingEvents = client.activeOutboxEvents(relayUrl),
                                    activeFilters = client.activeRequests(relayUrl),
                                    // The context describes the shared socket, not one account, so a
                                    // plane is looked up across every watched account — same as the
                                    // stream-key AUTHs above.
                                    venueForPlaneAuthor = ::concordCommunityForPlane,
                                ),
                        )
                    }
                // One socket is shared by every logged-in account, so an AUTH challenge is not tied
                // to any single one of them. We answer PER ACCOUNT: an account only reveals its
                // identity to a relay it has a first-party reason to be on (its own inbox/outbox
                // traffic, or a relay it configured) AND its own ledger verdict allows it. This is
                // what stops account B — or a throwaway key — being billed / de-anonymized on a
                // relay only account A uses (the inbox.nostr.wine over-AUTH bug): unlike the old
                // "any account ALLOWs → sign with everyone (else a random key)" path, a bystander
                // account never signs, and there is no random-key fallback.
                val signed = mutableListOf<RelayAuthEvent>()

                authWithAccounts.distinctValues().forEach forEachAccount@{ screen ->
                    val account = screen.account
                    if (!account.signer.isWriteable()) return@forEachAccount

                    // Not a gate any more, an input. Failing it only rules out the *automatic* grants
                    // (see RelayAuthResolver): a relay this account has no first-party reason to be on
                    // is never silently authenticated, but a challenge we can explain still becomes a
                    // question. Returning early here instead made "decide per relay" mean "deny, and
                    // don't mention it" for every purpose that names someone else — the exact case the
                    // prompt was built to explain.
                    //
                    // It does not reach the "…I'm reading someone I follow" toggle at all: a follow's
                    // outbox relay can never be first-party for us, so applying it there emptied the
                    // category instead of narrowing it. RelayAuthResolver.customAllows has the detail.
                    val firstParty = isFirstParty(account, relayUrl)

                    val approve =
                        when (account.relayAuthLedger.decide(context, firstParty)) {
                            RelayAuthVerdict.ALLOW -> true
                            RelayAuthVerdict.DENY -> false
                            RelayAuthVerdict.ASK ->
                                when {
                                    // A non-[interactive] pass is an automatic re-auth off an
                                    // `auth-required:` CLOSED (e.g. a Concord channel-plane REQ refused
                                    // because the connection AUTHed before the control plane folded in
                                    // its channel stream keys). It never raises a dialog: only identities
                                    // already approved (ledger ALLOWs, session grants, stream keys) re-send.
                                    !interactive -> false

                                    // On a relay that hosts our Concord planes, the derived stream-key AUTHs
                                    // ride this same answer and must not wait on a dialog. The account's
                                    // question used to be dropped outright here, which left the account
                                    // unauthenticated on every Concord relay: it could not read its own
                                    // gift wraps there (Direct Invites, DMs). Ask it in the background
                                    // instead; an approval re-authenticates with the stored challenge.
                                    streamAuths.isNotEmpty() -> {
                                        askInBackground(account, relayUrl, context)
                                        false
                                    }

                                    // Prompt PER ACCOUNT, not once per challenge. The dialog names whose
                                    // npub is about to be revealed, so answering it for @a must not also
                                    // reveal @b. The bus still collapses concurrent challenges for the same
                                    // (relay, account) pair. In practice this rarely means two dialogs: for
                                    // everything except reading a follow, isFirstParty already drops every
                                    // account without its own reason to be on this relay.
                                    else -> applyChoice(account, relayUrl, prompt(account, relayUrl, context))
                                }
                        }

                    if (approve) {
                        // Remember why we granted this relay so the settings screen can explain it.
                        account.relayAuthLedger.recordGrant(context)
                        try {
                            signed.add(account.signer.sign(buzzAugmented(authTemplate, account, relayUrl)))
                        } catch (e: Exception) {
                            Log.e("AuthCoordinator", "Failed trying to authenticate a writeable account", e)
                        }
                    }
                }

                signed + streamAuths
            },
        )

    private suspend fun prompt(
        account: Account,
        relayUrl: NormalizedRelayUrl,
        context: RelayAuthContext,
    ): UserAuthChoice =
        promptBus.requestDecision(
            relayUrl = relayUrl,
            purposes = context.purposes,
            askingAccount = account.pubKey,
            isMyOwnRelay = account.relayAuthLedger.isInMyRelayList(relayUrl.url),
        )

    /** Asks about [account] on [relayUrl] without holding up the challenge's other AUTHs; see the ASK branch. */
    private fun askInBackground(
        account: Account,
        relayUrl: NormalizedRelayUrl,
        context: RelayAuthContext,
    ) {
        scope.launch {
            if (applyChoice(account, relayUrl, prompt(account, relayUrl, context))) {
                account.relayAuthLedger.recordGrant(context)
                receiver.reauthenticate(relayUrl)
            }
        }
    }

    /** Stores what the user chose for [account] on [relayUrl]; true when it means "authenticate". */
    private suspend fun applyChoice(
        account: Account,
        relayUrl: NormalizedRelayUrl,
        choice: UserAuthChoice,
    ): Boolean =
        when (choice) {
            UserAuthChoice.ALLOW_ONCE -> {
                // Not literally once: relays re-challenge on every reconnect, so answering only the
                // in-flight challenge meant the same dialog came back minutes later. The grant is kept
                // in memory for the rest of this run and dies with the process.
                account.relayAuthLedger.grantForSession(relayUrl.url)
                true
            }
            UserAuthChoice.ALWAYS_ALLOW -> {
                account.relayAuthLedger.setDecision(relayUrl.url, RelayAuthDecision.ALLOW)
                true
            }
            UserAuthChoice.ALWAYS_ALLOW_EVERYWHERE -> {
                // No per-relay decision is stored: the policy already answers this relay, and an
                // exception on top of it would survive a later switch back to "decide per relay". The UI
                // has normally applied this already (see [applyPolicyEverywhere]); repeating it is free.
                applyPolicyEverywhere(account.pubKey, RelayAuthPolicy.ALWAYS)
                true
            }
            UserAuthChoice.BLOCK -> {
                account.relayAuthLedger.setDecision(relayUrl.url, RelayAuthDecision.DENY)
                false
            }
            UserAuthChoice.NEVER_ALLOW_EVERYWHERE -> {
                applyPolicyEverywhere(account.pubKey, RelayAuthPolicy.NEVER)
                false
            }
            UserAuthChoice.DISMISS -> false
        }

    /**
     * Sets [askingAccount]'s top-level NIP-42 policy — the "Always, all relays" / "Never, all relays"
     * answers. Public because the *prompt* calls it the moment the user confirms, instead of relying
     * on the answer reaching the suspended challenge above: that answer window is 60s from the dialog
     * appearing (see [RelayAuthPromptBus]), and a user who spends it reading the confirmation would
     * otherwise have their setting silently dropped along with the expired prompt. The relay's own
     * AUTH is the only thing worth losing to a timeout; a setting is not, and the next challenge —
     * seconds later, on reconnect — is answered by the policy this wrote.
     *
     * Goes through [Account.changeDefaultRelayAuthPolicy] rather than the settings object because
     * that is what pairs [RelayAuthPolicy.NEVER] with dropping this run's session grants, which
     * outrank the policy and would otherwise keep authenticating the relays just answered "log in".
     *
     * Takes a pubkey rather than an [Account] because the prompt names the account whose npub is at
     * stake, which on a multi-account device is not the one the screen is showing. Unknown pubkeys
     * (an account logged out while its prompt was up) are a no-op.
     */
    fun applyPolicyEverywhere(
        askingAccount: HexKey,
        policy: RelayAuthPolicy,
    ) {
        authWithAccounts.distinctValues().forEach { screen ->
            if (screen.account.pubKey == askingAccount) screen.account.changeDefaultRelayAuthPolicy(policy)
        }
    }

    /**
     * The joined Concord community whose plane [planeAddress] is, across every watched account, or
     * null when the pubkey isn't a plane of ours. Feeds [RelayAuthPurposeDeriver] so a pending plane
     * wrap reads as a post into that community rather than as a DM to the throwaway key it `p`-tags.
     */
    private fun concordCommunityForPlane(planeAddress: HexKey): HexKey? = authWithAccounts.distinct().firstNotNullOfOrNull { it.concordSessions.communityIdForPlane(planeAddress) }

    /**
     * Signs one kind-22242 AUTH per Concord plane stream key hosted on [relayUrl], across every
     * watched account. Signed locally from the derived stream secret (a raw [KeyPair] via
     * [NostrSignerSync]) — never the account signer, and never surfacing the user's identity.
     */
    private suspend fun signConcordStreamAuths(
        relayUrl: NormalizedRelayUrl,
        authTemplate: EventTemplate<RelayAuthEvent>,
    ): List<RelayAuthEvent> {
        val secrets = authWithAccounts.distinct().flatMap { it.concordSessions.streamAuthSecretsFor(relayUrl) }
        if (secrets.isEmpty()) return emptyList()
        return secrets.mapNotNull { secret ->
            try {
                // Cache the signer by secret so we don't re-derive the secp256k1 keypair for every
                // plane on every relay challenge/reconnect.
                val key = secret.toHexKey()
                val signer = streamSigners[key] ?: NostrSignerSync(KeyPair(privKey = secret)).also { streamSigners.put(key, it) }
                signer.sign(authTemplate)
            } catch (e: Exception) {
                Log.e("AuthCoordinator", "Failed to sign a Concord stream-key AUTH", e)
                null
            }
        }
    }

    /**
     * If [relayUrl] speaks the Buzz dialect and [account] holds a NIP-OA attestation authorizing
     * its key, returns [template] with the owner-signed `auth` tag appended — so the relay grants
     * virtual membership to an un-enrolled agent key while its owner stays a member. Otherwise
     * returns [template] unchanged.
     *
     * Applied ONLY to an account's own AUTH (the caller passes the account being signed for), never
     * to the Concord stream-key AUTHs that share the same [template] object, and it is a no-op on
     * non-Buzz relays and for accounts with no held attestation — so it can never add an `auth` tag
     * where one isn't wanted.
     */
    private fun buzzAugmented(
        template: EventTemplate<RelayAuthEvent>,
        account: Account,
        relayUrl: NormalizedRelayUrl,
    ): EventTemplate<RelayAuthEvent> {
        if (!BuzzRelayDialect.isBuzz(relayUrl)) return template
        val authTag = account.buzzAttestation.authTag(template.createdAt) ?: return template
        return EventTemplate(template.createdAt, template.kind, template.tags + arrayOf(authTag), template.content)
    }

    // stream secret (hex) -> its local signer. An LRU: every rekey and every new plane brings a new
    // secret and nothing removed the old ones, so retired private stream keys stayed resident.
    private val streamSigners = LruCache<HexKey, NostrSignerSync>(256)

    /**
     * True when [account] has a first-party reason to authenticate with [relayUrl] on the shared
     * client: it is publishing its own event there, a subscription there is reading its own
     * inbox/outbox (`#p` or `authors` names its pubkey), or the relay is in its own relay list.
     *
     * Merely *following* the counterparty of someone else's traffic is deliberately NOT first-party:
     * that is exactly how a bystander account got dragged into a paid inbox relay's AUTH (the shared
     * auth context carries the OTHER account's counterparties, evaluated against this account's
     * follow graph).
     *
     * Reading a followed author's outbox is the one case this cannot speak to. That relay is the
     * author's, so nothing here can ever return true for it, which is why
     * [com.vitorpamplona.amethyst.commons.relayauth.RelayAuthResolver] applies the
     * [com.vitorpamplona.amethyst.commons.relayauth.RelayAuthCustomToggles.readFollows] category
     * without consulting this — otherwise the toggle would be permanently off.
     */
    private fun isFirstParty(
        account: Account,
        relayUrl: NormalizedRelayUrl,
    ): Boolean =
        // A Buzz workspace THIS account explicitly joined is a first-party reason to authenticate:
        // its channel/DM discovery is read-only (`#p` = me), which is otherwise deliberately NOT
        // first-party, so without this the p-gated 44100/30622 reads would never be served and the
        // workspace would stay empty. Read off the account, never a device-global set: the invite was
        // redeemed by one key, and a shared set made every other logged-in account first-party here.
        account.buzzWorkspaces.isJoined(relayUrl) ||
            RelayAuthFirstParty.hasReason(
                me = account.pubKey,
                relayUrl = relayUrl,
                pendingEvents = client.activeOutboxEvents(relayUrl),
                myRelays = account.trustedRelays.flow.value,
                // A room the user explicitly joined — a NIP-29 relay group (kind-10009) or a Concord
                // community (kind-13302) — makes its host relay first-party. Neither room's traffic
                // names the user: NIP-29 content is `#h`-scoped and Concord planes ride derived stream
                // keys, so both fail the pubkey checks above. Without this, a joined private group's
                // messages are refused with `auth-required` and the room stays empty.
                myVenueRelays = account.venueHostRelays(),
            )

    fun destroy() {
        receiver.destroy()
    }

    // This is called by main. Keep it really fast.
    fun subscribe(account: ScreenAuthAccount?) {
        if (account == null) return

        if (isDebug) {
            Log.d("AuthCoordinator") { "Watch $account" }
        }

        authWithAccounts.add(account)
    }

    // This is called by main. Keep it really fast.
    fun unsubscribe(account: ScreenAuthAccount?) {
        if (account == null) return

        if (isDebug) {
            Log.d("AuthCoordinator") { "Unwatch $account" }
        }

        authWithAccounts.remove(account)
    }
}
