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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.routstr

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.agents.AgentProviderPresets
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeyEditState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.action_paste
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copy_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.no_wallet_found
import com.vitorpamplona.amethyst.commons.resources.refresh
import com.vitorpamplona.amethyst.commons.resources.retry
import com.vitorpamplona.amethyst.commons.resources.routstr_all_nodes
import com.vitorpamplona.amethyst.commons.resources.routstr_amount_custom
import com.vitorpamplona.amethyst.commons.resources.routstr_amount_placeholder
import com.vitorpamplona.amethyst.commons.resources.routstr_amount_title
import com.vitorpamplona.amethyst.commons.resources.routstr_amount_under_reserve
import com.vitorpamplona.amethyst.commons.resources.routstr_badge_https
import com.vitorpamplona.amethyst.commons.resources.routstr_badge_onion
import com.vitorpamplona.amethyst.commons.resources.routstr_cashu_placeholder
import com.vitorpamplona.amethyst.commons.resources.routstr_cashu_use
import com.vitorpamplona.amethyst.commons.resources.routstr_change_unclaimed
import com.vitorpamplona.amethyst.commons.resources.routstr_check_payment
import com.vitorpamplona.amethyst.commons.resources.routstr_checking_key
import com.vitorpamplona.amethyst.commons.resources.routstr_copy_invoice
import com.vitorpamplona.amethyst.commons.resources.routstr_copy_token
import com.vitorpamplona.amethyst.commons.resources.routstr_done
import com.vitorpamplona.amethyst.commons.resources.routstr_edition_sats
import com.vitorpamplona.amethyst.commons.resources.routstr_enter_url
import com.vitorpamplona.amethyst.commons.resources.routstr_err_unsupported
import com.vitorpamplona.amethyst.commons.resources.routstr_expired
import com.vitorpamplona.amethyst.commons.resources.routstr_forget_change
import com.vitorpamplona.amethyst.commons.resources.routstr_get_key
import com.vitorpamplona.amethyst.commons.resources.routstr_have_key
import com.vitorpamplona.amethyst.commons.resources.routstr_hide_stale
import com.vitorpamplona.amethyst.commons.resources.routstr_key_is_cashu
import com.vitorpamplona.amethyst.commons.resources.routstr_key_not_saved
import com.vitorpamplona.amethyst.commons.resources.routstr_key_placeholder
import com.vitorpamplona.amethyst.commons.resources.routstr_key_saved
import com.vitorpamplona.amethyst.commons.resources.routstr_key_works
import com.vitorpamplona.amethyst.commons.resources.routstr_loading_nodes
import com.vitorpamplona.amethyst.commons.resources.routstr_make_invoice
import com.vitorpamplona.amethyst.commons.resources.routstr_mints_count
import com.vitorpamplona.amethyst.commons.resources.routstr_mismatch
import com.vitorpamplona.amethyst.commons.resources.routstr_model_disabled
import com.vitorpamplona.amethyst.commons.resources.routstr_models_failed
import com.vitorpamplona.amethyst.commons.resources.routstr_models_list
import com.vitorpamplona.amethyst.commons.resources.routstr_models_loading
import com.vitorpamplona.amethyst.commons.resources.routstr_models_none
import com.vitorpamplona.amethyst.commons.resources.routstr_models_title
import com.vitorpamplona.amethyst.commons.resources.routstr_no_nodes
import com.vitorpamplona.amethyst.commons.resources.routstr_nodes_failed
import com.vitorpamplona.amethyst.commons.resources.routstr_onion_only
import com.vitorpamplona.amethyst.commons.resources.routstr_open_wallet
import com.vitorpamplona.amethyst.commons.resources.routstr_or_cashu
import com.vitorpamplona.amethyst.commons.resources.routstr_paid_by_wallet
import com.vitorpamplona.amethyst.commons.resources.routstr_paid_no_key
import com.vitorpamplona.amethyst.commons.resources.routstr_pay_cashu_wallet
import com.vitorpamplona.amethyst.commons.resources.routstr_pay_failed
import com.vitorpamplona.amethyst.commons.resources.routstr_pay_no_answer
import com.vitorpamplona.amethyst.commons.resources.routstr_pay_requested
import com.vitorpamplona.amethyst.commons.resources.routstr_pay_with_wallet
import com.vitorpamplona.amethyst.commons.resources.routstr_paying
import com.vitorpamplona.amethyst.commons.resources.routstr_pending_cashu
import com.vitorpamplona.amethyst.commons.resources.routstr_pending_invoice
import com.vitorpamplona.amethyst.commons.resources.routstr_pending_key
import com.vitorpamplona.amethyst.commons.resources.routstr_pending_paid
import com.vitorpamplona.amethyst.commons.resources.routstr_pending_title
import com.vitorpamplona.amethyst.commons.resources.routstr_pick_node
import com.vitorpamplona.amethyst.commons.resources.routstr_reserve_sats
import com.vitorpamplona.amethyst.commons.resources.routstr_reveal
import com.vitorpamplona.amethyst.commons.resources.routstr_search_nodes
import com.vitorpamplona.amethyst.commons.resources.routstr_seen_days
import com.vitorpamplona.amethyst.commons.resources.routstr_seen_today
import com.vitorpamplona.amethyst.commons.resources.routstr_send_again
import com.vitorpamplona.amethyst.commons.resources.routstr_show_invoice
import com.vitorpamplona.amethyst.commons.resources.routstr_show_key
import com.vitorpamplona.amethyst.commons.resources.routstr_show_key_warning
import com.vitorpamplona.amethyst.commons.resources.routstr_show_stale
import com.vitorpamplona.amethyst.commons.resources.routstr_tagline
import com.vitorpamplona.amethyst.commons.resources.routstr_title
import com.vitorpamplona.amethyst.commons.resources.routstr_token_not_taken
import com.vitorpamplona.amethyst.commons.resources.routstr_token_returned
import com.vitorpamplona.amethyst.commons.resources.routstr_top_up_title
import com.vitorpamplona.amethyst.commons.resources.routstr_topped_up
import com.vitorpamplona.amethyst.commons.resources.routstr_turn_token_into_key
import com.vitorpamplona.amethyst.commons.resources.routstr_typed
import com.vitorpamplona.amethyst.commons.resources.routstr_unknown
import com.vitorpamplona.amethyst.commons.resources.routstr_url_not_node
import com.vitorpamplona.amethyst.commons.resources.routstr_url_open
import com.vitorpamplona.amethyst.commons.resources.routstr_url_placeholder
import com.vitorpamplona.amethyst.commons.resources.routstr_verified
import com.vitorpamplona.amethyst.commons.resources.routstr_verified_hint
import com.vitorpamplona.amethyst.commons.resources.routstr_version
import com.vitorpamplona.amethyst.commons.resources.routstr_waiting_payment
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.routstr.RoutstrBuyUi
import com.vitorpamplona.amethyst.commons.routstr.RoutstrFailure
import com.vitorpamplona.amethyst.commons.routstr.RoutstrKeyCheck
import com.vitorpamplona.amethyst.commons.routstr.RoutstrModel
import com.vitorpamplona.amethyst.commons.routstr.RoutstrModelsUi
import com.vitorpamplona.amethyst.commons.routstr.RoutstrNode
import com.vitorpamplona.amethyst.commons.routstr.RoutstrNodeDetail
import com.vitorpamplona.amethyst.commons.routstr.RoutstrNodes
import com.vitorpamplona.amethyst.commons.routstr.RoutstrPendingPurchase
import com.vitorpamplona.amethyst.commons.routstr.RoutstrPricing
import com.vitorpamplona.amethyst.commons.routstr.RoutstrPurchaseOutcome
import com.vitorpamplona.amethyst.commons.routstr.RoutstrPurchases
import com.vitorpamplona.amethyst.commons.routstr.RoutstrSetupViewModel
import com.vitorpamplona.amethyst.commons.routstr.RoutstrStage
import com.vitorpamplona.amethyst.commons.routstr.RoutstrTarget
import com.vitorpamplona.amethyst.commons.routstr.RoutstrVerification
import com.vitorpamplona.amethyst.commons.ui.components.PillGood
import com.vitorpamplona.amethyst.commons.ui.components.PillTextField
import com.vitorpamplona.amethyst.commons.ui.components.PillTone
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.util.getText
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.wallet.payInvoice
import com.vitorpamplona.amethyst.commons.ui.wallet.rememberWalletAppLauncher
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the node panel is showing under the node: nothing yet, buying a key, or adding one. */
private enum class NodeMode { NONE, BUY, HAVE_KEY }

/**
 * Routstr, the sats-paid AI nodes: pick a node (kind 38421; no node contacted to list them), see
 * its models priced in sats per edition, then get a key (Lightning, with the app's wallet or a
 * QR, or Cashu) or add one. With [Route.RoutstrSetup.topUpUrl] it tops up that saved key.
 */
@Composable
fun RoutstrSetupScreen(
    route: Route.RoutstrSetup,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val routstr = rememberRoutstr(accountViewModel)
    if (routstr == null) {
        Scaffold(topBar = { TopBarWithBackButton(stringRes(Res.string.routstr_title), nav) }) { padding ->
            Box(Modifier.padding(padding).padding(16.dp)) { Text(stringRes(Res.string.routstr_err_unsupported)) }
        }
        return
    }
    val viewModel = rememberViewModel(key = "routstr:${accountViewModel.account.signer.pubKey}") { RoutstrSetupViewModel(routstr) }
    val keys by accountViewModel.account.agentKeys.flow
        .collectAsStateWithLifecycle()
    // The key to top up is found again from what the back stack holds: its URL and its masked form.
    val topUpKey =
        route.topUpUrl?.let { url ->
            keys.firstOrNull { RoutstrNodes.sameNode(it.url, url) && AgentProviderPresets.maskKey(it.key) == route.topUpMask }
        }
    LaunchedEffect(route.node) { route.node?.let { viewModel.openAddress(it) } }
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(NodeMode.NONE) }

    PlatformBackHandler(enabled = topUpKey == null && detail != null && route.node == null) {
        viewModel.closeDetail()
        viewModel.resetBuy()
        mode = NodeMode.NONE
    }

    Scaffold(
        topBar = {
            TopBarWithBackButton(
                if (topUpKey != null) {
                    stringRes(Res.string.routstr_top_up_title, rememberRoutstrNodeName(routstr, topUpKey.url) ?: AgentProviderPresets.hostOf(topUpKey.url) ?: topUpKey.url)
                } else {
                    stringRes(Res.string.routstr_title)
                },
                nav,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PendingPurchases(viewModel)
            val shown = detail
            when {
                topUpKey != null -> {
                    val topUp = rememberRoutstrTopUp(routstr, topUpKey.url)
                    Text(AgentProviderPresets.maskKey(topUpKey.key), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BuyPanel(viewModel, topUp.target, topUpKey, topUp.mints, reserve = null, nav = nav)
                }
                shown == null -> {
                    Text(stringRes(Res.string.routstr_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NodePicker(viewModel) { node ->
                        mode = NodeMode.NONE
                        viewModel.resetBuy()
                        viewModel.open(node)
                    }
                }
                else ->
                    NodeDetailPanel(
                        viewModel = viewModel,
                        detail = shown,
                        mode = mode,
                        onMode = { mode = it },
                        onAllNodes = {
                            viewModel.closeDetail()
                            viewModel.resetBuy()
                            mode = NodeMode.NONE
                        },
                        nav = nav,
                    )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// --- The picker ----------------------------------------------------------------------------

@Composable
private fun NodePicker(
    viewModel: RoutstrSetupViewModel,
    onPick: (RoutstrNode) -> Unit,
) {
    val ui by viewModel.nodes.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var typing by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringRes(Res.string.routstr_pick_node), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = viewModel::refresh) {
                Icon(MaterialSymbols.Refresh, contentDescription = stringRes(Res.string.refresh))
            }
        }
        PillTextField(
            value = query,
            onValueChange = {
                query = it
                viewModel.setQuery(it)
            },
            placeholder = stringRes(Res.string.routstr_search_nodes),
            leading = MaterialSymbols.Search,
        )
        when {
            ui.loading -> LoadingLine(stringRes(Res.string.routstr_loading_nodes))
            ui.failed -> Text(stringRes(Res.string.routstr_nodes_failed), color = MaterialTheme.colorScheme.error)
            ui.nodes.isEmpty() && ui.staleCount == 0 -> Text(stringRes(Res.string.routstr_no_nodes), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ui.nodes.forEach { node -> NodeRow(node) { onPick(node) } }
        if (ui.staleCount > 0 || ui.showingStale) {
            TextButton(onClick = { viewModel.setShowStale(!ui.showingStale) }) {
                Text(
                    if (ui.showingStale) {
                        stringRes(Res.string.routstr_hide_stale)
                    } else {
                        pluralStringRes(Res.plurals.routstr_show_stale, ui.staleCount, ui.staleCount)
                    },
                )
            }
        }
        HorizontalDivider()
        TypedNodeUrl(viewModel, typing) { typing = it }
    }
}

@Composable
private fun TypedNodeUrl(
    viewModel: RoutstrSetupViewModel,
    open: Boolean,
    onOpen: (Boolean) -> Unit,
) {
    val checking by viewModel.checkingUrl.collectAsStateWithLifecycle()
    val problem by viewModel.urlCheck.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }
    if (!open) {
        TextButton(onClick = { onOpen(true) }) {
            Icon(MaterialSymbols.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringRes(Res.string.routstr_enter_url))
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PillTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = stringRes(Res.string.routstr_url_placeholder),
            leading = MaterialSymbols.Link,
            keyboardType = KeyboardType.Uri,
            tone = if (problem != null) PillTone.Bad else PillTone.Neutral,
        ) {
            if (checking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { viewModel.openUrl(url) }, enabled = url.isNotBlank()) { Text(stringRes(Res.string.routstr_url_open)) }
            }
        }
        problem?.let { error ->
            Text(
                if (error.status == null && error.failure == RoutstrFailure.UNEXPECTED) stringRes(Res.string.routstr_url_not_node) else routstrErrorText(error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodeRow(
    node: RoutstrNode,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(node.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(MaterialSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            node.about?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(seenText(node.lastSeen), if (node.isFresh()) PillGood else MaterialTheme.colorScheme.onSurfaceVariant)
                node.version?.let { Badge(stringRes(Res.string.routstr_version, it.substringBefore('+'))) }
                Badge(pluralStringRes(Res.plurals.routstr_mints_count, node.mints.size, node.mints.size))
                if (node.hasHttps) Badge(stringRes(Res.string.routstr_badge_https))
                if (node.hasOnion) Badge(stringRes(Res.string.routstr_badge_onion))
            }
            Text(
                if (node.models.isEmpty()) stringRes(Res.string.routstr_models_none) else stringRes(Res.string.routstr_models_list, node.models.take(4).joinToString(", ") + if (node.models.size > 4) "…" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Badge(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun seenText(lastSeen: Long): String {
    val days = ((TimeUtils.now() - lastSeen) / 86_400).toInt().coerceAtLeast(0)
    return if (days == 0) stringRes(Res.string.routstr_seen_today) else pluralStringRes(Res.plurals.routstr_seen_days, days, days)
}

@Composable
private fun LoadingLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --- The node ------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodeDetailPanel(
    viewModel: RoutstrSetupViewModel,
    detail: RoutstrNodeDetail,
    mode: NodeMode,
    onMode: (NodeMode) -> Unit,
    onAllNodes: () -> Unit,
    nav: INav,
) {
    val node = detail.node
    var chosen by rememberSaveable(node.address) { mutableStateOf<String?>(null) }
    val models = (detail.models as? RoutstrModelsUi.Loaded)?.models.orEmpty()
    val chosenModel = models.firstOrNull { it.id == chosen } ?: models.firstOrNull { it.enabled }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onAllNodes) {
            Icon(MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringRes(Res.string.routstr_all_nodes))
        }
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(node.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (detail.verification == RoutstrVerification.Verified) {
                        Icon(MaterialSymbols.CheckCircle, contentDescription = null, tint = PillGood, modifier = Modifier.size(18.dp), filled = true)
                        Spacer(Modifier.width(4.dp))
                        Text(stringRes(Res.string.routstr_verified), style = MaterialTheme.typography.labelLarge, color = PillGood)
                    } else if (detail.verification == RoutstrVerification.Checking) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
                node.about?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    when (detail.verification) {
                        RoutstrVerification.Verified -> stringRes(Res.string.routstr_verified_hint)
                        RoutstrVerification.Mismatch -> stringRes(Res.string.routstr_mismatch)
                        RoutstrVerification.Typed -> stringRes(Res.string.routstr_typed)
                        RoutstrVerification.Unknown -> stringRes(Res.string.routstr_unknown)
                        RoutstrVerification.Checking -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (detail.verification == RoutstrVerification.Mismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (detail.verification != RoutstrVerification.Typed) Badge(seenText(node.lastSeen))
                    node.version?.let { Badge(stringRes(Res.string.routstr_version, it.substringBefore('+'))) }
                    Badge(pluralStringRes(Res.plurals.routstr_mints_count, node.mints.size, node.mints.size))
                }
                listOfNotNull(node.httpsUrl, node.onionUrl).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Text(stringRes(Res.string.routstr_models_title), style = MaterialTheme.typography.titleMedium)
        when (val m = detail.models) {
            RoutstrModelsUi.Loading -> LoadingLine(stringRes(Res.string.routstr_models_loading))
            is RoutstrModelsUi.Failed -> Text(stringRes(Res.string.routstr_models_failed) + " " + routstrErrorText(m.error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            is RoutstrModelsUi.Loaded -> m.models.take(MODELS_SHOWN).forEach { model -> ModelRow(model, model.id == chosenModel?.id) { chosen = model.id } }
        }

        val target = detail.target
        if (target == null) {
            Text(stringRes(Res.string.routstr_onion_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == NodeMode.BUY, onClick = { onMode(if (mode == NodeMode.BUY) NodeMode.NONE else NodeMode.BUY) }, label = { Text(stringRes(Res.string.routstr_get_key)) }, leadingIcon = { Icon(MaterialSymbols.Bolt, contentDescription = null, modifier = Modifier.size(18.dp)) })
            FilterChip(selected = mode == NodeMode.HAVE_KEY, onClick = { onMode(if (mode == NodeMode.HAVE_KEY) NodeMode.NONE else NodeMode.HAVE_KEY) }, label = { Text(stringRes(Res.string.routstr_have_key)) }, leadingIcon = { Icon(MaterialSymbols.Key, contentDescription = null, modifier = Modifier.size(18.dp)) })
        }
        when (mode) {
            NodeMode.BUY -> BuyPanel(viewModel, target, null, node.mints, reserve = chosenModel?.let { (it.name ?: it.id) to RoutstrPricing.maxCostSats(it.sats) }, nav = nav)
            NodeMode.HAVE_KEY -> HaveKeyPanel(viewModel, target, nav) { onMode(NodeMode.BUY) }
            NodeMode.NONE -> {}
        }
    }
}

private const val MODELS_SHOWN = 40

@Composable
private fun ModelRow(
    model: RoutstrModel,
    chosen: Boolean,
    onChoose: () -> Unit,
) {
    val edition = RoutstrSetupViewModel.editionSats(model)
    val reserve = RoutstrPricing.maxCostSats(model.sats)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (chosen) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (chosen) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onChoose),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(model.name ?: model.id, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (!model.enabled) stringRes(Res.string.routstr_model_disabled) else model.id,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 200.dp)) {
                edition?.let { Text(pluralStringRes(Res.plurals.routstr_edition_sats, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), formatGrouped(it)), style = MaterialTheme.typography.labelLarge) }
                reserve?.let { Text(pluralStringRes(Res.plurals.routstr_reserve_sats, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), formatGrouped(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

// --- Buying --------------------------------------------------------------------------------

private val AMOUNTS = listOf(1_000L, 5_000L, 20_000L)

/**
 * Get a key, or top up [topUpKey]: an amount, then a Lightning invoice (the app's wallet, or a
 * QR with Copy and Open in wallet), or Cashu. [reserve] is the chosen model and its `max_cost`:
 * an amount under it can't run that model, and the panel says so.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuyPanel(
    viewModel: RoutstrSetupViewModel,
    target: RoutstrTarget,
    topUpKey: AgentKeyTag?,
    mints: List<String>,
    reserve: Pair<String, Long?>?,
    nav: INav,
) {
    val buy by viewModel.buy.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableStateOf(5_000L) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var customText by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    val sats = if (custom) customText.filter { it.isDigit() }.take(7).toLongOrNull() ?: 0 else amount
    val cashu = remember(mints, buy) { viewModel.cashuBalances(mints).filterValues { it >= sats } }

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val state = buy) {
                is RoutstrBuyUi.Invoice -> InvoiceView(viewModel, state)
                is RoutstrBuyUi.Finished -> FinishedView(viewModel, state, nav)
                is RoutstrBuyUi.Error -> {
                    ProblemLine(routstrErrorText(state.error, state.notKept))
                    OutlinedButton(onClick = viewModel::resetBuy) { Text(stringRes(Res.string.retry)) }
                }
                RoutstrBuyUi.Working -> LoadingLine(stringRes(Res.string.routstr_waiting_payment))
                RoutstrBuyUi.Idle -> {
                    Text(stringRes(Res.string.routstr_amount_title), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AMOUNTS.forEach { option ->
                            FilterChip(
                                selected = !custom && amount == option,
                                onClick = {
                                    custom = false
                                    amount = option
                                },
                                label = { Text(satsText(option)) },
                            )
                        }
                        FilterChip(selected = custom, onClick = { custom = true }, label = { Text(stringRes(Res.string.routstr_amount_custom)) })
                    }
                    if (custom) {
                        PillTextField(
                            value = customText,
                            onValueChange = { customText = it.filter { c -> c.isDigit() }.take(7) },
                            placeholder = stringRes(Res.string.routstr_amount_placeholder),
                            leading = MaterialSymbols.Bolt,
                            keyboardType = KeyboardType.Number,
                        )
                    }
                    // max_cost dominates: a key under the model's reserve can't run it at all.
                    val (modelName, maxCost) = reserve ?: (null to null)
                    if (modelName != null && maxCost != null && sats in 1 until maxCost) {
                        ProblemLine(pluralStringRes(Res.plurals.routstr_amount_under_reserve, maxCost.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), modelName, formatGrouped(maxCost)))
                    }
                    Button(
                        onClick = { viewModel.makeInvoice(target, sats, topUpKey) },
                        enabled = sats > 0,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(MaterialSymbols.Bolt, contentDescription = null, modifier = Modifier.size(20.dp), filled = true)
                        Spacer(Modifier.width(8.dp))
                        Text(stringRes(Res.string.routstr_make_invoice))
                    }

                    HorizontalDivider()
                    Text(stringRes(Res.string.routstr_or_cashu), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    cashu.keys.firstOrNull()?.let { mint ->
                        OutlinedButton(onClick = { viewModel.payFromCashuWallet(target, mint, sats, topUpKey) }, enabled = sats > 0, modifier = Modifier.fillMaxWidth()) {
                            Icon(MaterialSymbols.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringRes(Res.string.routstr_pay_cashu_wallet, AgentProviderPresets.hostOf(mint) ?: mint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    PillTextField(
                        value = token,
                        onValueChange = { token = it.trim() },
                        placeholder = stringRes(Res.string.routstr_cashu_placeholder),
                        leading = MaterialSymbols.Paid,
                        visualTransformation = if (token.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
                    )
                    if (token.isNotEmpty()) {
                        OutlinedButton(onClick = { viewModel.payWithToken(target, token, topUpKey) }, enabled = RoutstrPurchases.isCashuToken(token)) {
                            Text(stringRes(Res.string.routstr_cashu_use))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceView(
    viewModel: RoutstrSetupViewModel,
    state: RoutstrBuyUi.Invoice,
) {
    val bolt11 = state.purchase.bolt11 ?: return
    val hasWallet by viewModel.hasLightningWallet.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val launcher = rememberWalletAppLauncher()
    val noWallet = stringRes(Res.string.no_wallet_found)
    var copied by remember(bolt11) { mutableStateOf(false) }
    var launchError by remember(bolt11) { mutableStateOf<String?>(null) }

    Text(satsText(state.purchase.amountSats), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    if (hasWallet && !state.paidByWallet && state.payRequested && !state.paying) {
        // The wallet was asked once: it may still pay, so it is never asked again. The node is.
        OutlinedButton(onClick = viewModel::checkPayment, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(MaterialSymbols.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringRes(Res.string.routstr_check_payment))
        }
    } else if (hasWallet && !state.paidByWallet) {
        Button(onClick = viewModel::payWithWallet, enabled = state.canPay, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            if (state.paying) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                Spacer(Modifier.width(8.dp))
                Text(stringRes(Res.string.routstr_paying))
            } else {
                Icon(MaterialSymbols.Bolt, contentDescription = null, modifier = Modifier.size(20.dp), filled = true)
                Spacer(Modifier.width(8.dp))
                Text(stringRes(Res.string.routstr_pay_with_wallet))
            }
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            QrCodeDrawer(contents = "lightning:$bolt11", modifier = Modifier.padding(12.dp).size(220.dp))
        }
    }
    Text(bolt11, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            scope.launch {
                clipboard.setText(bolt11)
                copied = true
            }
        }) {
            Icon(MaterialSymbols.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringRes(if (copied) Res.string.copied_to_clipboard else Res.string.routstr_copy_invoice))
        }
        OutlinedButton(onClick = { launcher.payInvoice(bolt11, noWallet, onPaid = {}, onError = { launchError = it }) }) {
            Icon(MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringRes(Res.string.routstr_open_wallet))
        }
    }
    launchError?.let { ProblemLine(it) }
    state.payError?.let { ProblemLine(stringRes(Res.string.routstr_pay_failed, it)) }
    if (state.payNoAnswer) ProblemLine(stringRes(Res.string.routstr_pay_no_answer))
    val waiting =
        when {
            state.outcome == RoutstrPurchaseOutcome.PaidNoKeyYet -> stringRes(Res.string.routstr_paid_no_key)
            state.paidByWallet -> stringRes(Res.string.routstr_paid_by_wallet)
            // Not final: a late payment still settles for a day, and it is still checked.
            state.outcome == RoutstrPurchaseOutcome.Expired -> stringRes(Res.string.routstr_expired)
            state.outcome is RoutstrPurchaseOutcome.Failed -> routstrErrorText((state.outcome as RoutstrPurchaseOutcome.Failed).error)
            state.payRequested -> stringRes(Res.string.routstr_pay_requested)
            else -> stringRes(Res.string.routstr_waiting_payment)
        }
    LoadingLine(waiting)
    if (!state.paidByWallet && !state.payRequested && !state.paying && state.outcome != RoutstrPurchaseOutcome.PaidNoKeyYet) {
        TextButton(onClick = viewModel::abandonInvoice) { Text(stringRes(Res.string.cancel)) }
    }
}

@Composable
private fun FinishedView(
    viewModel: RoutstrSetupViewModel,
    state: RoutstrBuyUi.Finished,
    nav: INav,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    when (val outcome = state.outcome) {
        is RoutstrPurchaseOutcome.KeySaved -> DoneLine(stringRes(Res.string.routstr_key_saved), nav)
        RoutstrPurchaseOutcome.ToppedUp -> DoneLine(stringRes(Res.string.routstr_topped_up), nav)
        RoutstrPurchaseOutcome.Expired -> {
            Text(stringRes(Res.string.routstr_expired))
            OutlinedButton(onClick = viewModel::resetBuy) { Text(stringRes(Res.string.retry)) }
        }
        RoutstrPurchaseOutcome.KeyNotSaved -> {
            ProblemLine(stringRes(Res.string.routstr_key_not_saved))
            state.purchaseId?.let { id -> Row { OutlinedButton(onClick = { viewModel.retry(id) }) { Text(stringRes(Res.string.retry)) } } }
        }
        is RoutstrPurchaseOutcome.TokenNotTaken -> {
            val token = outcome.token
            ProblemLine(stringRes(if (token != null) Res.string.routstr_token_not_taken else Res.string.routstr_token_returned) + " " + routstrErrorText(outcome.error))
            if (token != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { clipboard.setText(token) } }) { Text(stringRes(Res.string.routstr_copy_token)) }
                    OutlinedButton(onClick = viewModel::resetBuy) { Text(stringRes(Res.string.routstr_send_again)) }
                }
            } else {
                OutlinedButton(onClick = viewModel::resetBuy) { Text(stringRes(Res.string.retry)) }
            }
        }
        is RoutstrPurchaseOutcome.Failed -> {
            ProblemLine(routstrErrorText(outcome.error))
            OutlinedButton(onClick = viewModel::resetBuy) { Text(stringRes(Res.string.retry)) }
        }
        else -> OutlinedButton(onClick = viewModel::resetBuy) { Text(stringRes(Res.string.retry)) }
    }
}

@Composable
private fun DoneLine(
    text: String,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(MaterialSymbols.CheckCircle, contentDescription = null, tint = PillGood, filled = true)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
    Button(onClick = { nav.popBack() }) { Text(stringRes(Res.string.routstr_done)) }
}

@Composable
private fun ProblemLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(MaterialSymbols.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp), filled = true)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

// --- I have a key --------------------------------------------------------------------------

@Composable
private fun HaveKeyPanel(
    viewModel: RoutstrSetupViewModel,
    target: RoutstrTarget,
    nav: INav,
    onBuyInstead: () -> Unit,
) {
    val check by viewModel.keyCheck.collectAsStateWithLifecycle()
    val edit by viewModel.editState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var key by rememberSaveable { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(key) {
        delay(600)
        viewModel.checkKey(target, key)
    }

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillTextField(
                value = key,
                onValueChange = { key = it.trim() },
                placeholder = stringRes(Res.string.routstr_key_placeholder),
                leading = MaterialSymbols.Key,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (key.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
                tone =
                    when (check) {
                        is RoutstrKeyCheck.Works -> PillTone.Good
                        is RoutstrKeyCheck.Failed, RoutstrKeyCheck.IsCashuToken -> PillTone.Bad
                        else -> PillTone.Neutral
                    },
            ) {
                if (key.isEmpty()) {
                    TextButton(onClick = { scope.launch { clipboard.getText()?.trim()?.let { key = it } } }) { Text(stringRes(Res.string.action_paste)) }
                }
            }
            when (val c = check) {
                RoutstrKeyCheck.Checking -> LoadingLine(stringRes(Res.string.routstr_checking_key))
                RoutstrKeyCheck.IsCashuToken -> {
                    // A token is money: it is never saved as a key. It can buy one.
                    ProblemLine(stringRes(Res.string.routstr_key_is_cashu))
                    OutlinedButton(onClick = {
                        viewModel.payWithToken(target, key)
                        key = ""
                        onBuyInstead()
                    }) { Text(stringRes(Res.string.routstr_turn_token_into_key)) }
                }
                is RoutstrKeyCheck.Works -> {
                    Text(
                        pluralStringRes(
                            Res.plurals.routstr_key_works,
                            c.balance.balanceSats
                                .coerceAtMost(Int.MAX_VALUE.toLong())
                                .toInt(),
                            formatGrouped(c.balance.balanceSats),
                        ),
                        color = PillGood,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (saved && edit is AgentKeyEditState.Done) {
                        DoneLine(stringRes(Res.string.routstr_key_saved), nav)
                    } else {
                        Button(onClick = {
                            if (viewModel.saveKey(target, key) != null) saved = true
                        }, enabled = !edit.isBusy) {
                            if (edit.isBusy) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringRes(Res.string.save))
                        }
                    }
                }
                is RoutstrKeyCheck.Failed -> ProblemLine(routstrErrorText(c.error))
                RoutstrKeyCheck.Idle -> {}
            }
        }
    }
}

// --- What is on its way ----------------------------------------------------------------------

/** Purchases this device holds: an invoice out, a key not saved yet, change the wallet didn't take. */
@Composable
private fun PendingPurchases(viewModel: RoutstrSetupViewModel) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    if (pending.isEmpty()) return
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var revealing by remember { mutableStateOf<RoutstrPendingPurchase?>(null) }

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringRes(Res.string.routstr_pending_title), style = MaterialTheme.typography.titleSmall)
            pending.forEach { p ->
                val node = p.nodeName ?: AgentProviderPresets.hostOf(p.nodeRoot) ?: p.nodeRoot
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        when (p.stage) {
                            RoutstrStage.AWAITING_PAYMENT -> pluralStringRes(Res.plurals.routstr_pending_invoice, p.amountSats.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), formatGrouped(p.amountSats), node)
                            RoutstrStage.PAID -> stringRes(Res.string.routstr_pending_paid, node)
                            RoutstrStage.KEY_UNSAVED -> stringRes(Res.string.routstr_pending_key, node)
                            RoutstrStage.CHANGE_UNCLAIMED -> pluralStringRes(Res.plurals.routstr_change_unclaimed, p.amountSats.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), formatGrouped(p.amountSats))
                            RoutstrStage.CASHU_SENT -> stringRes(Res.string.routstr_pending_cashu, node)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        when (p.stage) {
                            RoutstrStage.AWAITING_PAYMENT -> TextButton(onClick = { viewModel.showInvoice(p.id) }) { Text(stringRes(Res.string.routstr_show_invoice)) }
                            RoutstrStage.PAID -> TextButton(onClick = { viewModel.retry(p.id) }) { Text(stringRes(Res.string.retry)) }
                            RoutstrStage.KEY_UNSAVED -> {
                                TextButton(onClick = { viewModel.retry(p.id) }) { Text(stringRes(Res.string.retry)) }
                                TextButton(onClick = { revealing = p }) { Text(stringRes(Res.string.routstr_show_key)) }
                            }
                            RoutstrStage.CHANGE_UNCLAIMED -> {
                                TextButton(onClick = { p.token?.let { scope.launch { clipboard.setText(it) } } }) { Text(stringRes(Res.string.routstr_copy_token)) }
                                TextButton(onClick = { viewModel.retry(p.id) }) { Text(stringRes(Res.string.retry)) }
                                TextButton(onClick = { viewModel.forgetChange(p.id) }) { Text(stringRes(Res.string.routstr_forget_change)) }
                            }
                            RoutstrStage.CASHU_SENT -> TextButton(onClick = { viewModel.retry(p.id) }) { Text(stringRes(Res.string.retry)) }
                        }
                    }
                }
            }
        }
    }

    revealing?.let { p -> ShowKeyDialog(p.apiKey.orEmpty()) { revealing = null } }
}

/** "Show key", behind a warning: the full key is never on screen otherwise. */
@Composable
private fun ShowKeyDialog(
    key: String,
    onDismiss: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.routstr_show_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringRes(Res.string.routstr_show_key_warning))
                if (shown) SelectionContainer { Text(key, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) }
            }
        },
        confirmButton = {
            if (shown) {
                TextButton(onClick = { scope.launch { clipboard.setText(key) } }) { Text(stringRes(Res.string.copy_to_clipboard)) }
            } else {
                TextButton(onClick = { shown = true }) { Text(stringRes(Res.string.routstr_reveal), color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.close)) } },
    )
}
