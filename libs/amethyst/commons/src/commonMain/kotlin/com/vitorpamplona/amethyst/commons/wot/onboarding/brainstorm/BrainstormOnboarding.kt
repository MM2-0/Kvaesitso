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
package com.vitorpamplona.amethyst.commons.wot.onboarding.brainstorm

import com.vitorpamplona.amethyst.commons.model.trustedAssertions.SCORE_SERVICES
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.TrustProviderRow
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderException
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderHttp
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderHttpResponse
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderOnboarding
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderOnboardingStep
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderRegistration
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Sign-up with Brainstorm (NosFabrica's GrapeRank service, brainstorm.world). Read from
 * `NosFabrica/brainstorm_server` and its web app:
 *
 *  1. `GET /authChallenge/{pubkey}` → `{data:{challenge}}`.
 *  2. Sign a kind 22242 with `["t","brainstorm_login"]` and `["challenge", c]`;
 *     `POST /authChallenge/{pubkey}/verify` `{signed_event}` → `{data:{token}}` (a JWT, valid for
 *     an hour). Signing in also creates the user's per-user service key.
 *  3. `POST /user/graperank` (Bearer token) asks for a score run. It is throttled (403 when the
 *     last run is recent, 429 when a tier quota is used up); both mean scores exist or are
 *     coming, so neither stops the sign-up.
 *  4. `GET /user/history` → `{data:{ta_pubkey, last_time_calculated_graperank}}`: the key the
 *     user's kind 30382 cards are signed with, published to `wss://scores.brainstorm.world`.
 *  5. `POST /user/assistantProfile` (Bearer token), while no scores exist yet: publishes the
 *     assistant key's profile now instead of before the first batch.
 *  6. `GET /setup/{pubkey}` → `[[name, key, relay], …]`: the kind 10040 rows Brainstorm serves
 *     this user with (`30382:rank`, `followers`, `hops`, `reporters`, `muters`, and `30392` for
 *     Trusted Lists), the same ones its site publishes. When that fails, the two its site has
 *     always published: `30382:rank` and `30382:followers` under `ta_pubkey`.
 */
class BrainstormOnboarding(
    private val http: TrustProviderHttp,
    private val api: String = API,
) : TrustProviderOnboarding {
    override val id = "brainstorm"
    override val name = "Brainstorm"
    override val homepage = "https://brainstorm.world"
    override val setupUrl = "https://brainstorm.world/setup/activate"

    // brainstorm_server serves its default observer as NIP-05 `_` (nip05_service.py).
    override val houseObserver = "_@brainstorm.world"

    // GrapeRank crawls these two for follows, mutes and reports.
    override val graphRelays =
        setOf(
            RelayUrlNormalizer.normalize("wss://wot.grapevine.network"),
            RelayUrlNormalizer.normalize("wss://search.brainstorm.world"),
        )
    override val relay: NormalizedRelayUrl = RelayUrlNormalizer.normalize(SCORES_RELAY)

    // The scores relay also serves the network's notes, ranked: a better Global than the user's own relays.
    override val networkRelay: NormalizedRelayUrl = relay

    // The service key is per user and only known after a sign-up, but Brainstorm's scores relay
    // carries nothing but Brainstorm's keys, so an entry on it is Brainstorm's.
    override fun serves(provider: ServiceProviderTag): Boolean = provider.relayUrl == relay

    override suspend fun register(
        signer: NostrSigner,
        onStep: (TrustProviderOnboardingStep) -> Unit,
    ): TrustProviderRegistration {
        val pubkey = signer.pubKey

        onStep(TrustProviderOnboardingStep.SIGNING_IN)
        val challenge =
            data(call { http.get("$api/authChallenge/$pubkey") })
                .string("challenge")
                ?: throw unexpected("no challenge")

        val login: Event =
            try {
                signer.sign(TimeUtils.now(), LOGIN_KIND, arrayOf(arrayOf("t", "brainstorm_login"), arrayOf("challenge", challenge)), "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: SignerExceptions) {
                throw TrustProviderException(TrustProviderException.Reason.SIGNER_DECLINED, "The signer did not sign the Brainstorm login", e)
            }

        val verify = call { http.post("$api/authChallenge/$pubkey/verify", """{"signed_event":${login.toJson()}}""") }
        if (verify.status == 400 || verify.status == 401) {
            throw TrustProviderException(TrustProviderException.Reason.SIGN_IN_REJECTED, "Brainstorm rejected the login (${verify.status})")
        }
        val token = data(verify).string("token") ?: throw unexpected("no token")
        val auth = mapOf("Authorization" to "Bearer $token")

        onStep(TrustProviderOnboardingStep.REQUESTING_SCORES)
        val trigger = call { http.post("$api/user/graperank", null, auth) }
        // 403: a run is recent; 429: the tier's quota is used. Scores exist or are coming either way.
        // (5xx already threw in call.)
        if (!trigger.isSuccessful && trigger.status != 403 && trigger.status != 429) {
            throw unexpected("score run refused (HTTP ${trigger.status})")
        }

        onStep(TrustProviderOnboardingStep.FETCHING_SERVICE_KEY)
        val history = data(call { http.get("$api/user/history", auth) })
        val serviceKey = history.string("ta_pubkey")?.lowercase()
        if (serviceKey == null || serviceKey.length != 64 || !Hex.isHex64(serviceKey)) throw unexpected("no service key")
        val calculated = history["last_time_calculated_graperank"]
        val scoresReady = calculated != null && calculated !is JsonNull

        // Brainstorm publishes the assistant's profile (kind 0) only before its first batch of
        // scores, minutes away for a new user; until then the provider shows as a bare key. Its
        // site asks for it at sign-up, so do the same. Best effort: the sign-up does not need it.
        if (!scoresReady) {
            try {
                http.post("$api/user/assistantProfile", null, auth)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The first batch publishes it anyway.
            }
        }

        // /setup is not authenticated: use it only when its rank row names the key the signed-in
        // /user/history just returned.
        val rows =
            setupRows(pubkey)?.takeIf { rows -> rows.any { it.name == ProviderTypes.rank.toValue() && it.key == serviceKey } }
                ?: SCORE_SERVICES.map { TrustProviderRow(it.toValue(), serviceKey, relay) }
        return TrustProviderRegistration(
            rows = rows,
            scoresReady = scoresReady,
        )
    }

    /**
     * The kind 10040 rows Brainstorm serves [pubkey] with, or null when it cannot say (then the
     * caller falls back to the user-score rows). Rows that are not well formed are left out; a
     * list without a `30382:rank` row is not used.
     */
    private suspend fun setupRows(pubkey: HexKey): List<TrustProviderRow>? {
        val response =
            try {
                http.get("$api/setup/$pubkey")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return null
            }
        if (!response.isSuccessful) return null
        val rows =
            try {
                (Json.parseToJsonElement(response.body) as? JsonArray)?.mapNotNull { row ->
                    (row as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }?.let { TrustProviderRow.parse(it) }
                }
            } catch (e: Exception) {
                null
            } ?: return null
        return rows.distinctBy { it.name }.takeIf { list -> list.any { it.name == ProviderTypes.rank.toValue() } }
    }

    private suspend fun call(request: suspend () -> TrustProviderHttpResponse): TrustProviderHttpResponse {
        val response =
            try {
                request()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw TrustProviderException(TrustProviderException.Reason.UNREACHABLE, "Could not reach Brainstorm: ${e.message}", e)
            }
        if (response.status >= 500) {
            throw TrustProviderException(TrustProviderException.Reason.UNREACHABLE, "Brainstorm is unavailable (${response.status})")
        }
        return response
    }

    /** The `data` object of Brainstorm's `{code, message, data}` envelope. */
    private fun data(response: TrustProviderHttpResponse): JsonObject {
        if (!response.isSuccessful) throw unexpected("HTTP ${response.status}")
        return try {
            Json
                .parseToJsonElement(response.body)
                .asObject()
                ?.get("data")
                ?.asObject()
        } catch (e: Exception) {
            null
        } ?: throw unexpected("unreadable response")
    }

    private fun JsonElement.asObject() = this as? JsonObject

    private fun JsonObject.string(key: String): String? = (get(key)?.takeUnless { it is JsonNull })?.jsonPrimitive?.content

    private fun unexpected(what: String) = TrustProviderException(TrustProviderException.Reason.UNEXPECTED_RESPONSE, "Unexpected Brainstorm response: $what")

    companion object {
        const val API = "https://api.brainstorm.world"
        const val SCORES_RELAY = "wss://scores.brainstorm.world"
        const val LOGIN_KIND = 22242

        /**
         * Brainstorm's Settings › Assistants › AI model: where the reader gives their Assistant an
         * AI key of its own (its NIP-FA list), which it prints the reader's Observer with.
         */
        const val AI_MODEL_SETTINGS = "https://brainstorm.world/settings?tab=assistants&view=ai"
    }
}
