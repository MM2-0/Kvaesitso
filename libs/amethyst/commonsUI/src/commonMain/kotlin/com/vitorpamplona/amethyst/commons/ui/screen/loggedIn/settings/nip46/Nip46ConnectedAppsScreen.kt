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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.nip46

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientInfo
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.icons.symbols.rememberMaterialSymbolPainter
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_full_trust
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_paranoid
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_reasonable
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_app_last_used
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_app_offline
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_app_online
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_app_reconnect
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_app_relay_count
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_apps_empty
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_manage_apps
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_reconnecting
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_remote_app
import com.vitorpamplona.amethyst.commons.service.AppServices
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** One connected NIP-46 remote-signer client, with everything the row needs to render. */
private data class Nip46AppEntry(
    val coordinate: String,
    val clientPubKey: HexKey,
    val policy: AppSignerPolicy?,
    val info: Nip46ClientInfo?,
    val lastUsedSeconds: Long?,
)

/**
 * The remote-signer clients connected to this account. Kept separate from the shared Connected Apps
 * screen because — unlike napplets/nsites/browser origins — each NIP-46 app can carry its own relays
 * (from a `nostrconnect://` offer), which the signer subscribes to in the background for as long as
 * the app stays connected. Surfacing them here, with their relay footprint and last-used time, lets
 * the user prune the background relay connections they no longer need (idle apps are also auto-forgotten
 * after a week; see `Nip46SignerState.IDLE_PRUNE_SECONDS`).
 *
 * Tapping a row opens the shared [Route.ConnectedAppDetail], which already renders NIP-46 clients
 * (history + Forget).
 */
@Composable
fun Nip46ConnectedAppsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val reconnectingStr = stringRes(Res.string.nip46_signer_reconnecting)
    val account = accountViewModel.account
    val signerPubKey = remember { account.signer.pubKey }
    val shortNotice = rememberShortNotice()
    val services = LocalAppServices.current

    // Live relay state so each app shows whether the signer currently reaches it. An app that brought
    // its own relays (nostrconnect) is judged on those; a bunker-flow app rides the inbox relays.
    val connectedRelays by account.client.connectedRelaysFlow().collectAsStateWithLifecycle()
    val inboxRelays by account.nip46Signer.inboxRelays.collectAsStateWithLifecycle()

    var items by remember { mutableStateOf<List<Nip46AppEntry>?>(null) }
    // Bumped on resume so a Forget performed on the detail screen is reflected when we return.
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        items = withContext(Dispatchers.IO) { loadNip46Apps(services, signerPubKey) }
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.nip46_signer_manage_apps), nav) },
    ) { padding ->
        val current = items
        when {
            current == null ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            current.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            MaterialSymbols.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp),
                        )
                        Text(
                            stringRes(Res.string.nip46_signer_apps_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(current, key = { it.coordinate }) { entry ->
                        val online =
                            remember(entry.info?.relays, inboxRelays, connectedRelays) {
                                nip46AppOnline(entry.info?.relays.orEmpty(), inboxRelays, connectedRelays)
                            }
                        Nip46AppCard(
                            entry = entry,
                            online = online,
                            onReconnect = {
                                account.client.reconnect(ignoreRetryDelays = true)
                                shortNotice.show(reconnectingStr)
                            },
                            onClick = { nav.nav(Route.ConnectedAppDetail(entry.coordinate)) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun Nip46AppCard(
    entry: Nip46AppEntry,
    online: Boolean?,
    onReconnect: () -> Unit,
    onClick: () -> Unit,
) {
    // Same identity line the detail screen (Nip46AppHeader) uses: the app's self-declared website
    // when it has one, npub otherwise — so a row and its detail never disagree.
    val subtitle = remember(entry.info?.url, entry.clientPubKey) { nip46ClientSubtitle(entry.info?.url, entry.clientPubKey) }
    val title = entry.info?.name?.ifBlank { null } ?: stringRes(Res.string.nip46_signer_remote_app)
    val relayCount = entry.info?.relays?.size ?: 0

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Nip46AppIcon(entry.info?.image, Modifier.size(48.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta =
                    buildList {
                        if (relayCount > 0) add(pluralStringRes(Res.plurals.nip46_signer_app_relay_count, relayCount, relayCount))
                        entry.lastUsedSeconds?.let { add(stringRes(Res.string.nip46_signer_app_last_used, timeAgo(it).trim())) }
                    }.joinToString("  ·  ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                online?.let { Nip46LiveStatus(it) }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                entry.policy?.let { policy ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(policy.shortLabel(), style = MaterialTheme.typography.labelSmall) },
                    )
                }
                if (online == false) {
                    Nip46ReconnectPill(onReconnect)
                } else {
                    Icon(
                        MaterialSymbols.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSignerPolicy.shortLabel(): String =
    when (this) {
        AppSignerPolicy.FULL_TRUST -> stringRes(Res.string.napplet_policy_full_trust)
        AppSignerPolicy.REASONABLE -> stringRes(Res.string.napplet_policy_reasonable)
        AppSignerPolicy.PARANOID -> stringRes(Res.string.napplet_policy_paranoid)
    }

private val LiveGreen = Color(0xFF3DDC84)

/** A colored dot: green when the signer currently reaches the relay(s), muted otherwise. */
@Composable
fun Nip46StatusDot(online: Boolean) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) LiveGreen else MaterialTheme.colorScheme.outline))
}

/**
 * A small "Reconnect" pill shown when an app's relays are offline. Forces the whole relay pool to
 * re-dial now (ignoring backoff), which re-establishes the offline relays. Consumes its own tap so a
 * pill inside a clickable card doesn't also open the card.
 */
@Composable
fun Nip46ReconnectPill(onClick: () -> Unit) {
    Text(
        stringRes(Res.string.nip46_signer_app_reconnect),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 11.dp, vertical = 4.dp),
    )
}

/** A [Nip46StatusDot] + Connected/Offline label showing whether the signer currently reaches an app's relays. */
@Composable
fun Nip46LiveStatus(online: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Nip46StatusDot(online)
        Text(
            stringRes(if (online) Res.string.nip46_signer_app_online else Res.string.nip46_signer_app_offline),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Whether the signer currently reaches [appRelays] (an app's own `nostrconnect://` relays). An app
 * that brought no relays of its own rides the account [inboxRelays], so it's judged on those. Returns
 * `null` when neither resolves to any relay (nothing to show a status for). "Online" means at least
 * one of the app's relays is in [connectedRelays].
 */
fun nip46AppOnline(
    appRelays: Set<String>,
    inboxRelays: Set<NormalizedRelayUrl>,
    connectedRelays: Set<NormalizedRelayUrl>,
): Boolean? {
    val effective = appRelays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet().ifEmpty { inboxRelays }
    if (effective.isEmpty()) return null
    return effective.any { it in connectedRelays }
}

/**
 * The avatar for a NIP-46 client: its self-declared [image] (the app/site icon it advertised in its
 * connect metadata) drawn in a circle, falling back to the Key glyph when it has none or the image
 * fails to load. Shared by the list card and the detail header. We only render an icon the app itself
 * provided — we never fetch a site favicon from the main app, which would bypass Tor and leak the
 * user's IP (see BrowserIconRegistry).
 */
@Composable
fun Nip46AppIcon(
    image: String?,
    modifier: Modifier = Modifier,
) {
    val model = image?.takeIf { it.isNotBlank() }
    Box(
        modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Icon(
                MaterialSymbols.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        } else {
            val glyph = rememberMaterialSymbolPainter(MaterialSymbols.Key, MaterialTheme.colorScheme.onPrimaryContainer)
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = glyph,
                error = glyph,
                fallback = glyph,
            )
        }
    }
}

/**
 * The identity line shown for a NIP-46 client: its self-declared website (host only) when it
 * advertised one, otherwise its npub. Shared by the list card and the detail header so a row and
 * the screen it opens always agree.
 */
fun nip46ClientSubtitle(
    url: String?,
    clientPubKey: HexKey,
): String {
    val host =
        url
            ?.ifBlank { null }
            ?.removePrefix("https://")
            ?.removePrefix("http://")
            ?.substringBefore('/')
            ?.ifBlank { null }
    return host ?: runCatching { NPub.create(clientPubKey) }.getOrDefault(clientPubKey.take(12) + "…")
}

private suspend fun loadNip46Apps(
    services: AppServices,
    signerPubKey: HexKey,
): List<Nip46AppEntry> {
    val store = services.signerPermissionStore
    val clientStore = services.nip46ClientStore
    // allPolicies() already carries each app's policy — read it from the map instead of a second
    // loadPolicy() call per app.
    return store
        .allPolicies()
        .filterKeys { Nip46PermissionAuthorizer.belongsTo(it, signerPubKey) }
        .mapNotNull { (coordinate, policy) ->
            val clientPubKey = Nip46PermissionAuthorizer.clientPubKeyOf(coordinate) ?: return@mapNotNull null
            Nip46AppEntry(
                coordinate = coordinate,
                clientPubKey = clientPubKey,
                policy = policy,
                info = clientStore.load(coordinate),
                lastUsedSeconds = store.loadLastUsed(coordinate),
            )
        }.sortedByDescending { it.lastUsedSeconds ?: 0L }
}
