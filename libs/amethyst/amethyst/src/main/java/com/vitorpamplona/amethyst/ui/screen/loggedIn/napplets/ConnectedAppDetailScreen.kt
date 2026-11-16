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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.connectedApps.consent.counterpartyLabel
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientInfo
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrOpDecision
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletSignerKey
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.resolveNappletMeta
import com.vitorpamplona.amethyst.commons.napplet.ui.PolicyCard
import com.vitorpamplona.amethyst.commons.napplet.ui.descriptionRes
import com.vitorpamplona.amethyst.commons.napplet.ui.labelRes
import com.vitorpamplona.amethyst.commons.napplet.ui.symbol
import com.vitorpamplona.amethyst.commons.nip46RemoteSigner.ui.Nip46ActivityCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_permission_allowed
import com.vitorpamplona.amethyst.commons.resources.browser_permission_ask
import com.vitorpamplona.amethyst.commons.resources.browser_permission_blocked
import com.vitorpamplona.amethyst.commons.resources.browser_permission_camera
import com.vitorpamplona.amethyst.commons.resources.browser_permission_location
import com.vitorpamplona.amethyst.commons.resources.browser_permission_microphone
import com.vitorpamplona.amethyst.commons.resources.browser_permission_section
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.confirm
import com.vitorpamplona.amethyst.commons.resources.napplet_connected_app_capabilities
import com.vitorpamplona.amethyst.commons.resources.napplet_connected_app_forget
import com.vitorpamplona.amethyst.commons.resources.napplet_connected_app_op_overrides
import com.vitorpamplona.amethyst.commons.resources.napplet_connected_app_trust_level
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_allow_always
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_deny_always
import com.vitorpamplona.amethyst.commons.resources.napplet_decision_allow
import com.vitorpamplona.amethyst.commons.resources.napplet_decision_ask
import com.vitorpamplona.amethyst.commons.resources.napplet_decision_deny
import com.vitorpamplona.amethyst.commons.resources.napplet_op_blossom
import com.vitorpamplona.amethyst.commons.resources.napplet_op_decrypt
import com.vitorpamplona.amethyst.commons.resources.napplet_op_decrypt_from
import com.vitorpamplona.amethyst.commons.resources.napplet_op_encrypt
import com.vitorpamplona.amethyst.commons.resources.napplet_op_sign_in
import com.vitorpamplona.amethyst.commons.resources.napplet_op_sign_kind
import com.vitorpamplona.amethyst.commons.resources.napplet_permissions_ask_each_time
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_full_trust
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_full_trust_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_paranoid
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_paranoid_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_reasonable
import com.vitorpamplona.amethyst.commons.resources.napplet_policy_reasonable_desc
import com.vitorpamplona.amethyst.commons.resources.napplet_signer_permissions_revoke_all
import com.vitorpamplona.amethyst.commons.resources.napplet_untitled
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_activity_title
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_app_relays_inbox
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_app_relays_own_hint
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_app_relays_title
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_reconnecting
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_remote_app
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.rememberManifestIconModel
import com.vitorpamplona.amethyst.commons.ui.platform.rememberWebAppIconModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.nip46.Nip46AppIcon
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.nip46.Nip46LiveStatus
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.nip46.Nip46ReconnectPill
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.nip46.Nip46StatusDot
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.nip46.nip46AppOnline
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.nip46.nip46ClientSubtitle
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.napplet.NappletBrokerService
import com.vitorpamplona.amethyst.napplet.WebSitePermissionRegistry
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource

private data class ConnectedAppDetailState(
    val title: String,
    val coordinate: String,
    val iconUrl: String?,
    val signerPolicy: AppSignerPolicy?,
    val opOverrides: Map<String, NostrOpDecision>,
    val capabilities: List<Pair<NappletCapability, GrantState>>,
)

@Composable
fun ConnectedAppDetailScreen(
    coordinate: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val capabilityLedger = Amethyst.instance.nappletPermissionLedger
    val signerLedger = remember { NostrSignerPermissionLedger(Amethyst.instance.signerPermissionStore) }
    val untitled = stringRes(Res.string.napplet_untitled)
    // Where this app's signer decisions live: NIP-46 clients under their own coordinate, everything
    // else under the per-account key the broker writes (NappletSignerKey).
    val signerKey =
        remember(coordinate, accountViewModel.account) {
            if (Nip46PermissionAuthorizer.clientPubKeyOf(coordinate) != null) {
                coordinate
            } else {
                NappletSignerKey.of(accountViewModel.account.signer.pubKey, coordinate)
            }
        }

    var state by remember { mutableStateOf<ConnectedAppDetailState?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(coordinate, reload) {
        state =
            withContext(Dispatchers.IO) {
                loadDetailState(coordinate, signerKey, capabilityLedger, signerLedger, untitled)
            }
    }

    fun mutate(block: suspend () -> Unit) {
        scope.launch {
            block()
            reload++
        }
    }

    val identity =
        remember(coordinate) {
            NappletIdentity(
                authorPubKey = coordinate.substringBefore(':'),
                identifier = coordinate.substringAfter(':', ""),
            )
        }

    // NIP-46 remote clients are tailored: their self-declared metadata (name/url) heads the screen and
    // their serviced-request history is shown, instead of the napplet manifest path this coordinate can't
    // be resolved through. `null` for napplet/browser coordinates, which keep the generic rendering.
    val nip46Client = remember(coordinate) { Nip46PermissionAuthorizer.clientPubKeyOf(coordinate) }
    var nip46Info by remember(coordinate) { mutableStateOf<Nip46ClientInfo?>(null) }
    LaunchedEffect(coordinate) {
        if (nip46Client != null) {
            nip46Info = withContext(Dispatchers.IO) { Amethyst.instance.nip46ClientStore.load(coordinate) }
        }
    }
    val allActivity by accountViewModel.account.nip46Signer.activityLog.entries
        .collectAsStateWithLifecycle()
    val nip46Activity = remember(allActivity, nip46Client) { allActivity.filter { nip46Client != null && it.clientPubKey == nip46Client } }
    val nip46Title = nip46Info?.name?.ifBlank { null } ?: stringRes(Res.string.nip46_signer_remote_app)

    Scaffold(
        topBar = {
            val title =
                if (nip46Client != null) {
                    nip46Title
                } else {
                    state?.title ?: coordinate.substringAfter(':', "").ifBlank { coordinate.take(12) + "…" }
                }
            TopBarWithBackButton(title, nav)
        },
    ) { padding ->
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // App identity header
            if (nip46Client != null) {
                Nip46AppHeader(title = nip46Title, url = nip46Info?.url, image = nip46Info?.image, clientPubKey = nip46Client)
            } else {
                AppIdentityHeader(current)
            }

            // Relays this remote client is reached on — the whole reason it costs a background
            // connection, so surface them (with live status) for debugging relay footprint.
            if (nip46Client != null) {
                val connectedRelays by accountViewModel.account.client
                    .connectedRelaysFlow()
                    .collectAsStateWithLifecycle()
                val inboxRelays by accountViewModel.account.nip46Signer.inboxRelays
                    .collectAsStateWithLifecycle()
                Nip46RelaysSection(
                    relays = nip46Info?.relays.orEmpty(),
                    inboxRelays = inboxRelays,
                    connectedRelays = connectedRelays,
                    onReconnect = { accountViewModel.account.client.reconnect(ignoreRetryDelays = true) },
                )
            }

            // Signing trust level section
            if (current.signerPolicy != null) {
                SectionHeader(stringRes(Res.string.napplet_connected_app_trust_level))
                PolicyPicker(
                    selected = current.signerPolicy,
                    onSelect = { newPolicy ->
                        mutate {
                            signerLedger.setPolicy(signerKey, newPolicy)
                            // Live session grants are consulted BEFORE the policy, so tightening an app
                            // to PARANOID would not have stopped it signing — the grant it already holds
                            // short-circuits the check the new policy would fail. Changing the trust
                            // level is a decision about how this app is treated from now on, so drop
                            // what it is holding and let the new policy actually apply.
                            NappletBrokerService.revokeSessionGrants(coordinate)
                        }
                    },
                )
            }

            // Signing operation overrides section
            if (current.opOverrides.isNotEmpty()) {
                SectionHeader(stringRes(Res.string.napplet_connected_app_op_overrides))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        current.opOverrides.entries.forEachIndexed { index, (opKey, decision) ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                            OpOverrideRow(
                                opKey = opKey,
                                decision = decision,
                                onRevoke = {
                                    mutate {
                                        signerLedger.revokeOpDecision(signerKey, NostrSignerOp.fromKey(opKey) ?: return@mutate)
                                        // The persisted override is gone, but a live "allow for this session"
                                        // grant would keep authorizing this app until the broker dies.
                                        NappletBrokerService.revokeSessionGrants(coordinate)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // Capabilities section
            if (current.capabilities.isNotEmpty()) {
                SectionHeader(stringRes(Res.string.napplet_connected_app_capabilities))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        current.capabilities.forEachIndexed { index, (cap, grant) ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                            CapabilityDetailRow(
                                capability = cap,
                                grant = grant,
                                onSetGrant = { newGrant ->
                                    mutate {
                                        if (newGrant == null) {
                                            capabilityLedger.revoke(identity, cap)
                                        } else {
                                            capabilityLedger.record(identity, cap, newGrant)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // Camera / microphone / location answers for a website (`browser:<origin>`), editable here.
            if (coordinate.startsWith(BROWSER_PREFIX)) {
                SitePermissionsSection(coordinate.removePrefix(BROWSER_PREFIX))
            }

            // Recent activity (NIP-46 clients only)
            if (nip46Client != null && nip46Activity.isNotEmpty()) {
                SectionHeader(stringRes(Res.string.nip46_signer_activity_title))
                Nip46ActivityCard(nip46Activity)
            }

            // Forget button
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = {
                    mutate {
                        val nip46Client = Nip46PermissionAuthorizer.clientPubKeyOf(coordinate)
                        if (nip46Client != null) {
                            // Route NIP-46 clients through the host so the client store + the running
                            // listen set are cleared too, not just the permission ledger.
                            accountViewModel.account.nip46Signer.forgetClient(nip46Client)
                        } else {
                            signerLedger.revokeAll(signerKey)
                        }
                        capabilityLedger.revokeAll(identity)
                        // A forgotten website also loses its camera / microphone / location answers.
                        if (coordinate.startsWith(BROWSER_PREFIX)) {
                            val origin = coordinate.removePrefix(BROWSER_PREFIX)
                            BrowserSitePermission.entries.forEach { WebSitePermissionRegistry.set(origin, it, BrowserSitePermission.Decision.ASK) }
                        }
                        // Forgetting an app has to stop it signing *now*. The two ledgers above only
                        // drop persisted + capability grants; the broker separately holds the signer's
                        // in-memory "allow for this session" grants, which would otherwise keep the
                        // app authorized for as long as any applet surface stays open.
                        NappletBrokerService.revokeSessionGrants(coordinate)
                    }
                    nav.popBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) {
                Icon(MaterialSymbols.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringRes(Res.string.napplet_connected_app_forget))
            }
        }
    }
}

/**
 * Lists the relays a NIP-46 remote client is reached on, each with a live status dot. When the client
 * brought its own relays (the `nostrconnect://` flow) each one is a background connection Amethyst
 * keeps open while the app stays connected — exactly what a user debugging relay footprint wants to
 * see, including which are actually up right now. An empty set means the client talks over the
 * account's inbox relays (the bunker flow), so it adds no extra connection.
 */
@Composable
private fun Nip46RelaysSection(
    relays: Set<String>,
    inboxRelays: Set<NormalizedRelayUrl>,
    connectedRelays: Set<NormalizedRelayUrl>,
    onReconnect: () -> Unit,
) {
    val reconnectingStr = stringRes(Res.string.nip46_signer_reconnecting)
    val context = LocalContext.current
    val anyOffline =
        remember(relays, inboxRelays, connectedRelays) {
            if (relays.isEmpty()) {
                nip46AppOnline(emptySet(), inboxRelays, connectedRelays) == false
            } else {
                relays.any { RelayUrlNormalizer.normalizeOrNull(it)?.let { r -> r !in connectedRelays } ?: true }
            }
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SectionHeader(stringRes(Res.string.nip46_signer_app_relays_title)) }
        if (anyOffline) {
            Nip46ReconnectPill {
                onReconnect()
                Toast.makeText(context, reconnectingStr, Toast.LENGTH_SHORT).show()
            }
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (relays.isEmpty()) {
                // Bunker-flow app: rides the inbox relays. Show the overall inbox liveness so the row
                // still reflects whether the signer can be reached at all.
                val online = nip46AppOnline(emptySet(), inboxRelays, connectedRelays)
                Text(
                    stringRes(Res.string.nip46_signer_app_relays_inbox),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                online?.let { Nip46LiveStatus(it) }
            } else {
                relays.forEach { relay ->
                    val relayOnline =
                        remember(relay, connectedRelays) {
                            RelayUrlNormalizer.normalizeOrNull(relay)?.let { it in connectedRelays } ?: false
                        }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Nip46StatusDot(relayOnline)
                        Text(
                            relay,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    stringRes(Res.string.nip46_signer_app_relays_own_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Nip46AppHeader(
    title: String,
    url: String?,
    image: String?,
    clientPubKey: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Nip46AppIcon(image, Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    nip46ClientSubtitle(url, clientPubKey),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringRes(Res.string.nip46_signer_remote_app),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppIdentityHeader(state: ConnectedAppDetailState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val isBrowserEntry = state.coordinate.startsWith("browser:")
            val author = state.coordinate.substringBefore(':')
            val identifier = state.coordinate.substringAfter(':', "")
            val iconModel: String?
            val appForIcon: FavoriteApp
            if (isBrowserEntry) {
                iconModel = rememberWebAppIconModel(identifier)
                appForIcon = FavoriteApp.WebApp(identifier, state.title, 0L)
            } else {
                iconModel = rememberManifestIconModel(author, identifier)
                appForIcon = FavoriteApp.NostrApp(state.coordinate, state.title, 0L, state.iconUrl)
            }
            FavoriteAppIcon(
                app = appForIcon,
                iconModel = iconModel,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val domain =
                    if (author == "browser") {
                        identifier
                            .removePrefix("https://")
                            .removePrefix("http://")
                            .substringBefore('/')
                            .ifBlank { identifier }
                    } else {
                        identifier.ifBlank { author.take(12) + "…" }
                    }
                Text(
                    domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val BROWSER_PREFIX = "browser:"

/**
 * The site's camera / microphone / location answers ([WebSitePermissionRegistry]), each switchable between
 * Ask, Allow and Block — Chrome's per-site permission list.
 */
@Composable
private fun SitePermissionsSection(origin: String) {
    val all by WebSitePermissionRegistry.decisions.collectAsStateWithLifecycle()
    val decisions = all[origin].orEmpty()
    SectionHeader(stringRes(Res.string.browser_permission_section))
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            BrowserSitePermission.entries.forEachIndexed { index, permission ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                val decision = decisions[permission] ?: BrowserSitePermission.Decision.ASK
                var menuOpen by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringRes(
                            when (permission) {
                                BrowserSitePermission.CAMERA -> Res.string.browser_permission_camera
                                BrowserSitePermission.MICROPHONE -> Res.string.browser_permission_microphone
                                BrowserSitePermission.LOCATION -> Res.string.browser_permission_location
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text(stringRes(decision.labelRes())) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            BrowserSitePermission.Decision.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringRes(option.labelRes())) },
                                    onClick = {
                                        menuOpen = false
                                        WebSitePermissionRegistry.set(origin, permission, option)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun BrowserSitePermission.Decision.labelRes(): StringResource =
    when (this) {
        BrowserSitePermission.Decision.ASK -> Res.string.browser_permission_ask
        BrowserSitePermission.Decision.ALLOW -> Res.string.browser_permission_allowed
        BrowserSitePermission.Decision.BLOCK -> Res.string.browser_permission_blocked
    }

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PolicyPicker(
    selected: AppSignerPolicy,
    onSelect: (AppSignerPolicy) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PolicyCard(
            selected = selected == AppSignerPolicy.FULL_TRUST,
            symbol = MaterialSymbols.LockOpen,
            label = stringRes(Res.string.napplet_policy_full_trust),
            description = stringRes(Res.string.napplet_policy_full_trust_desc),
            onClick = { onSelect(AppSignerPolicy.FULL_TRUST) },
        )
        PolicyCard(
            selected = selected == AppSignerPolicy.REASONABLE,
            symbol = MaterialSymbols.Shield,
            label = stringRes(Res.string.napplet_policy_reasonable),
            description = stringRes(Res.string.napplet_policy_reasonable_desc),
            onClick = { onSelect(AppSignerPolicy.REASONABLE) },
        )
        PolicyCard(
            selected = selected == AppSignerPolicy.PARANOID,
            symbol = MaterialSymbols.Lock,
            label = stringRes(Res.string.napplet_policy_paranoid),
            description = stringRes(Res.string.napplet_policy_paranoid_desc),
            onClick = { onSelect(AppSignerPolicy.PARANOID) },
        )
    }
}

@Composable
private fun OpOverrideRow(
    opKey: String,
    decision: NostrOpDecision,
    onRevoke: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                NostrSignerOp.fromKey(opKey)?.opLabel() ?: opKey,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            decision.decisionLabel(),
            style = MaterialTheme.typography.labelSmall,
            color =
                when (decision) {
                    NostrOpDecision.DENY -> MaterialTheme.colorScheme.error
                    NostrOpDecision.ALLOW -> MaterialTheme.colorScheme.primary
                    NostrOpDecision.ASK -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        IconButton(onClick = onRevoke) {
            Icon(
                MaterialSymbols.Delete,
                contentDescription = stringRes(Res.string.napplet_signer_permissions_revoke_all),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CapabilityDetailRow(
    capability: NappletCapability,
    grant: GrantState,
    onSetGrant: (GrantState?) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        CapabilityPermissionDialog(
            capability = capability,
            current = grant,
            onSetGrant = { newGrant ->
                showDialog = false
                onSetGrant(newGrant)
            },
            onDismiss = { showDialog = false },
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Icon(
            capability.symbol(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringRes(capability.labelRes()), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringRes(capability.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            when (grant) {
                GrantState.ALLOW_ALWAYS -> stringRes(Res.string.napplet_consent_allow_always)
                GrantState.DENY -> stringRes(Res.string.napplet_consent_deny_always)
                else -> stringRes(Res.string.napplet_permissions_ask_each_time)
            },
            style = MaterialTheme.typography.labelSmall,
            color =
                when (grant) {
                    GrantState.ALLOW_ALWAYS -> MaterialTheme.colorScheme.primary
                    GrantState.DENY -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Icon(
            MaterialSymbols.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CapabilityPermissionDialog(
    capability: NappletCapability,
    current: GrantState,
    onSetGrant: (GrantState?) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial =
        when (current) {
            GrantState.ALLOW_ALWAYS -> GrantState.ALLOW_ALWAYS
            GrantState.DENY -> GrantState.DENY
            else -> GrantState.ASK
        }
    var selected by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(capability.labelRes())) },
        text = {
            Column {
                GrantOption(
                    label = stringRes(Res.string.napplet_permissions_ask_each_time),
                    selected = selected == GrantState.ASK,
                    onClick = { selected = GrantState.ASK },
                )
                if (!capability.requiresPerUseConsent) {
                    GrantOption(
                        label = stringRes(Res.string.napplet_consent_allow_always),
                        selected = selected == GrantState.ALLOW_ALWAYS,
                        onClick = { selected = GrantState.ALLOW_ALWAYS },
                    )
                }
                GrantOption(
                    label = stringRes(Res.string.napplet_consent_deny_always),
                    selected = selected == GrantState.DENY,
                    onClick = { selected = GrantState.DENY },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSetGrant(if (selected == GrantState.ASK) null else selected) },
            ) {
                Text(stringRes(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun GrantOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NostrSignerOp.opLabel(): String =
    when (this) {
        is NostrSignerOp.SignKind ->
            if (kind == RelayAuthEvent.KIND) {
                stringRes(Res.string.napplet_op_sign_in)
            } else if (kind == BlossomAuthorizationEvent.KIND) {
                // One grant covers every media-server verb (upload, delete, list), so name the server use.
                stringRes(Res.string.napplet_op_blossom)
            } else {
                stringRes(Res.string.napplet_op_sign_kind, kind)
            }
        NostrSignerOp.Encrypt -> stringRes(Res.string.napplet_op_encrypt)
        NostrSignerOp.Decrypt -> stringRes(Res.string.napplet_op_decrypt)
        is NostrSignerOp.DecryptFrom -> stringRes(Res.string.napplet_op_decrypt_from, counterpartyLabel(counterparty))
    }

@Composable
private fun NostrOpDecision.decisionLabel(): String =
    when (this) {
        NostrOpDecision.ALLOW -> stringRes(Res.string.napplet_decision_allow)
        NostrOpDecision.ASK -> stringRes(Res.string.napplet_decision_ask)
        NostrOpDecision.DENY -> stringRes(Res.string.napplet_decision_deny)
    }

private suspend fun loadDetailState(
    coordinate: String,
    signerKey: String,
    capabilityLedger: NappletPermissionLedger,
    signerLedger: NostrSignerPermissionLedger,
    untitled: String,
): ConnectedAppDetailState {
    val author = coordinate.substringBefore(':')
    val identifier = coordinate.substringAfter(':', "")
    val identity = NappletIdentity(authorPubKey = author, identifier = identifier)

    val allGrants = capabilityLedger.allPersistedGrants()
    val capGrants =
        allGrants[coordinate]
            ?.entries
            ?.sortedBy { it.key.ordinal }
            ?.map { it.key to it.value }
            ?: emptyList()
    val signerPolicy = signerLedger.store.loadPolicy(signerKey)
    val opOverrides = signerLedger.store.allOpDecisions(signerKey)

    val (title, iconUrl) =
        if (author == "browser") {
            val host = OmniboxInput.hostOf(identifier) ?: identifier
            host to Amethyst.instance.browserIcons.iconModelFor(host)
        } else {
            resolveNappletMeta(author, identifier, untitled)
        }
    return ConnectedAppDetailState(
        title = title,
        coordinate = coordinate,
        iconUrl = iconUrl,
        signerPolicy = signerPolicy,
        opOverrides = opOverrides,
        capabilities = capGrants,
    )
}
