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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.amethyst.commons.audio.AnonymizedResult
import com.vitorpamplona.amethyst.commons.model.location.DeviceLocation
import com.vitorpamplona.amethyst.commons.relays.health.RelayHealthStore
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.commons.service.ai.WritingAssistant
import com.vitorpamplona.amethyst.commons.service.lnurl.LnurlHttpTransport
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobFailure
import com.vitorpamplona.amethyst.commons.service.uploads.AudioFileMetadata
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.tor.MoneyOpRelayRouting
import com.vitorpamplona.amethyst.commons.tor.TorRelayEvaluation
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderHttp
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthSnapshot
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okio.Path

/**
 * What the account's ViewModel needs from the process it runs in: app-wide services that outlive
 * any one account, and the few actions only the platform can perform. Android implements it over
 * its app modules; previews pass a no-op.
 */
interface AccountViewModelHost {
    /** Fires when memory is tight enough that feeds should drop the Notes they hold. */
    val memoryPressure: Flow<Unit>

    /** Whether the local Blossom cache answers its probe. */
    val localBlossomCacheAvailable: Flow<Boolean>

    /** Background proof-of-work posts that could not be signed or sent. */
    val powPublishFailures: Flow<PoWJobFailure>

    /** Per-relay connection statistics, which order the relays a crawl visits. */
    val relayStats: RelayStats

    /** How fast each relay answers and which ones are slow, for the relay screens; null where not measured. */
    val relayHealth: RelayHealthStore? get() = null

    /** Opens sockets for the throwaway clients crawls use (Event Sync, Cashu discovery). */
    val websocketBuilder: WebsocketBuilder

    /** HTTP for LNURL-pay (zaps, invoices, melts), over the clients the app routes payments through. */
    val lnurlTransport: LnurlHttpTransport

    /** HTTP for Web of Trust provider sign-ups (Brainstorm's API). */
    val trustProviderHttp: TrustProviderHttp get() = TrustProviderHttp.Unsupported

    /** Routes a payment's relays under the money-operations Tor preference while it runs. */
    val moneyOpRelays: MoneyOpRelayRouting

    /** Which relays go through Tor under the user's settings, kept current. */
    val torRelayEvaluation: StateFlow<TorRelayEvaluation>

    /** NIP-42 authentication state per relay, across every logged-in account. */
    val relayAuthState: StateFlow<PersistentMap<NormalizedRelayUrl, RelayAuthSnapshot>>

    /** Compresses, strips, encrypts and uploads picked media for the composers. */
    val mediaUploader: MediaUploader

    /** The device's position as geohashes. */
    val deviceLocation: DeviceLocation get() = DeviceLocation.None

    /** Posts signed now and published later by a background worker; null where scheduling is unsupported. */
    val scheduledPostStore: ScheduledPostStore? get() = null

    /**
     * Where the pictures the user draws on are kept between sessions, one subfolder per account
     * (see `DrawingBackgroundStore`). Null where the platform keeps none.
     */
    val drawingBackgroundsDir: Path? get() = null

    /**
     * Re-voices the recording at [input] with the voice preset named [presetName], into a new file
     * beside it.
     */
    suspend fun anonymizeVoice(
        input: Path,
        presetName: String,
    ): Result<AnonymizedResult> = Result.failure(UnsupportedOperationException("Voice anonymization is not available on this platform"))

    /** Asks the platform's scheduler to publish any scheduled post that is now due, soon. */
    fun requestScheduledPostCatchUp() {}

    /**
     * Reads the tags of a picked audio file (duration, title, artist, album) off the main thread,
     * or null when the platform cannot or the file is not a readable audio container.
     */
    suspend fun probeAudioFile(uri: MediaUri): AudioFileMetadata? = null

    /** Whether this build can offer an on-device writing assistant at all (it shows the setting). */
    val supportsWritingAssistant: Boolean get() = false

    /** A new on-device writing assistant, or null where the platform has none. */
    fun createWritingAssistant(): WritingAssistant? = null

    /**
     * Whether the account [npub] has saved its secret key, as a live flag. Absent reads as true,
     * so a platform that does not track it never shows the backup nudge.
     */
    suspend fun hasBackedUpKeys(npub: String): StateFlow<Boolean> = MutableStateFlow(true)

    /** Records whether the account [npub] has saved its secret key. */
    suspend fun setHasBackedUpKeys(
        npub: String,
        value: Boolean,
    ) = Unit

    /** Public keys of every account saved on this device. */
    val savedAccounts: Flow<Set<HexKey>>

    /** Removes the system notification posted for [eventId], if there is one. */
    fun dismissNotificationFor(eventId: HexKey)

    /** Hands a BOLT11 [invoice] to an installed wallet app. False when none could take it. */
    fun openLightningWallet(invoice: String): Boolean
}
