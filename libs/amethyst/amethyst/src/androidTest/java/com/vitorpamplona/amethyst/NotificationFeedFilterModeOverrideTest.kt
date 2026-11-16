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
package com.vitorpamplona.amethyst

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.InMemoryGeohashIdentityStore
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.marmot.MarmotGroupNotifier
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuMintDirectoryFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.model.accountsCache.defaultMarmotStreamTransport
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip03Timestamp.EmptyOtsResolverBuilder
import com.vitorpamplona.quartz.nip60Cashu.mintApi.OkHttpMintTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [NotificationFeedFilter]'s `modeOverride` constructor parameter — the wiring
 * that drives the split-notifications Following / Everyone tabs (Issue #197).
 *
 * Asserts the three contracts the feature relies on:
 *   1. `feedKey` is mode-discriminated so each pinned tab caches independently.
 *   2. `followList()` honors `modeOverride` when set; falls back to the spinner setting otherwise.
 *   3. `buildFilterParams()` returns a GlobalTopNavFilter-backed FilterByListParams for both
 *      `TopFilter.Global` and `TopFilter.Selected` (so `isGlobal()` is true, allowing
 *      non-follower notifications through), and a non-Global filter for `TopFilter.AllFollows`
 *      (forcing the follow-membership gate).
 */
@RunWith(AndroidJUnit4::class)
class NotificationFeedFilterModeOverrideTest {
    companion object {
        private val keyPair = KeyPair()
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        private val client =
            NostrClient(
                BasicOkHttpWebSocket.Builder(
                    httpClient = {
                        OkHttpClient
                            .Builder()
                            .followRedirects(true)
                            .followSslRedirects(true)
                            .build()
                    },
                    canDial = { true },
                ),
                scope,
            )

        private val account =
            Account(
                settings = AccountSettings(keyPair = keyPair),
                signer = NostrSignerInternal(keyPair),
                geolocationFlow = { MutableStateFlow<LocationResult>(LocationResult.Loading) },
                nwcFilterAssembler = { NWCPaymentFilterAssembler(client) },
                cashuMintDirectoryFilterAssembler = { CashuMintDirectoryFilterAssembler(client) },
                cashuMintTransport = OkHttpMintTransport { OkHttpClient() },
                otsResolverBuilder = { EmptyOtsResolverBuilder.build() },
                cache = LocalCache,
                client = client,
                scope = scope,
                appVersion = "test",
                encryptionKeyCache = EncryptionKeyCache(),
                saveSettings = {},
                marmotNotifier = { MarmotGroupNotifier.None },
                nip46Consent = Nip46ConsentPrompter.Unanswered,
                geohashIdentityStore = InMemoryGeohashIdentityStore(),
                marmotStreamTransportFactory = ::defaultMarmotStreamTransport,
            )
    }

    @Test
    fun feedKeyDiffersByModeOverride() {
        val spinner = NotificationFeedFilter(account)
        val following = NotificationFeedFilter(account, TopFilter.AllFollows)
        val everyone = NotificationFeedFilter(account, TopFilter.Global)

        assertNotEquals(
            "Following tab's feedKey must differ from Everyone tab's so each caches independently",
            following.feedKey(),
            everyone.feedKey(),
        )
        assertTrue(
            "Everyone feedKey should encode the Global filter code",
            everyone.feedKey().endsWith(TopFilter.Global.code),
        )
        assertTrue(
            "Following feedKey should encode the AllFollows filter code",
            following.feedKey().endsWith(TopFilter.AllFollows.code),
        )

        // When override is null, feedKey reflects the spinner-selected default.
        account.settings.defaultNotificationFollowList.value = TopFilter.Global
        assertEquals(everyone.feedKey(), spinner.feedKey())
    }

    @Test
    fun followListHonorsModeOverride() {
        val following = NotificationFeedFilter(account, TopFilter.AllFollows)
        val everyone = NotificationFeedFilter(account, TopFilter.Global)

        assertEquals(TopFilter.AllFollows, following.followList())
        assertEquals(TopFilter.Global, everyone.followList())
    }

    @Test
    fun followListFallsBackToSpinnerWhenOverrideNull() {
        val spinner = NotificationFeedFilter(account)

        account.settings.defaultNotificationFollowList.value = TopFilter.Selected
        assertEquals(TopFilter.Selected, spinner.followList())

        account.settings.defaultNotificationFollowList.value = TopFilter.AllFollows
        assertEquals(TopFilter.AllFollows, spinner.followList())
    }

    @Test
    fun buildFilterParamsForGlobalOverrideReportsGlobal() {
        val everyone = NotificationFeedFilter(account, TopFilter.Global)

        val params = everyone.buildFilterParams(account)

        // isGlobal() short-circuits the follow-membership gate in acceptableEvent,
        // which is how the Everyone tab admits notifications from non-followed authors.
        assertTrue(
            "Everyone tab's FilterByListParams must report isGlobal so non-followers pass the gate",
            params.isGlobal(),
        )
    }

    @Test
    fun buildFilterParamsForSelectedOverrideReportsGlobal() {
        val selected = NotificationFeedFilter(account, TopFilter.Selected)

        val params = selected.buildFilterParams(account)

        // Selected rides the same GlobalFeedFlow relay set as Global, so it must
        // also report isGlobal and let non-followers through; the difference is
        // that acceptableEvent applies the per-kind relevance heuristics, which
        // Global skips.
        assertTrue(
            "Selected mode's FilterByListParams must report isGlobal so non-followers pass the gate",
            params.isGlobal(),
        )
    }

    @Test
    fun buildFilterParamsForAllFollowsOverrideIsNotGlobal() {
        val following = NotificationFeedFilter(account, TopFilter.AllFollows)

        val params = following.buildFilterParams(account)

        // The Following tab must NOT be Global so acceptableEvent falls through to
        // isAuthorInFollows() — that's the gate that drops non-follower notifications.
        assertFalse(
            "Following tab's FilterByListParams must not be Global; it must apply the follows gate",
            params.isGlobal(),
        )
    }
}
