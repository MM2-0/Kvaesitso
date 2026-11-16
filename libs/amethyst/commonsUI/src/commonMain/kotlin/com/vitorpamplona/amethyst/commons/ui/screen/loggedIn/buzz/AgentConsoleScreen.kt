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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.buzz.AgentConsoleViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.buzz.AgentFleetMetrics
import com.vitorpamplona.amethyst.commons.model.buzz.AgentUsageSummary
import com.vitorpamplona.amethyst.commons.model.buzz.TokenTotals
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.agent_console_attest
import com.vitorpamplona.amethyst.commons.resources.agent_console_empty_metrics
import com.vitorpamplona.amethyst.commons.resources.agent_console_empty_observer
import com.vitorpamplona.amethyst.commons.resources.agent_console_estimated_totals_warning
import com.vitorpamplona.amethyst.commons.resources.agent_console_fleet_total
import com.vitorpamplona.amethyst.commons.resources.agent_console_no_personas
import com.vitorpamplona.amethyst.commons.resources.agent_console_row_agents
import com.vitorpamplona.amethyst.commons.resources.agent_console_row_cache_read
import com.vitorpamplona.amethyst.commons.resources.agent_console_row_cache_write
import com.vitorpamplona.amethyst.commons.resources.agent_console_row_harness
import com.vitorpamplona.amethyst.commons.resources.agent_console_row_input_tokens
import com.vitorpamplona.amethyst.commons.resources.agent_console_row_last_turn
import com.vitorpamplona.amethyst.commons.resources.agent_console_row_model
import com.vitorpamplona.amethyst.commons.resources.agent_console_row_models
import com.vitorpamplona.amethyst.commons.resources.agent_console_row_output_tokens
import com.vitorpamplona.amethyst.commons.resources.agent_console_row_provider
import com.vitorpamplona.amethyst.commons.resources.agent_console_row_runtime
import com.vitorpamplona.amethyst.commons.resources.agent_console_row_sessions
import com.vitorpamplona.amethyst.commons.resources.agent_console_row_total_tokens
import com.vitorpamplona.amethyst.commons.resources.agent_console_row_turns
import com.vitorpamplona.amethyst.commons.resources.agent_console_tab_costs
import com.vitorpamplona.amethyst.commons.resources.agent_console_tab_observer
import com.vitorpamplona.amethyst.commons.resources.agent_console_tab_personas
import com.vitorpamplona.amethyst.commons.resources.agent_console_title
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_new_title
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.AgentAttestationScreen
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The workspace owner's agent-owner console — a dashboard over the fleet of AI agents
 * that publish to them. Three tabs:
 * - **Costs** — fleet totals and a per-agent breakdown (turns, sessions, tokens, spend),
 *   derived from decrypted NIP-AM turn metrics (`kind:44200`).
 * - **Personas** — the owner's published persona definitions (NIP-AP `kind:30175`).
 * - **Observer** — a live stream of ephemeral NIP-AO telemetry frames (`kind:24200`).
 *
 * A "Attest" FAB opens [AgentAttestationScreen] to issue a NIP-OA authorization for an
 * agent key. Data comes from [AgentConsoleViewModel], keyed by the owner pubkey so the
 * fetch/decrypt work survives navigation. The tabs are read-only; persona editing is a
 * follow-up.
 */
@Composable
fun AgentConsoleScreen(
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pubkey = accountViewModel.account.userProfile().pubkeyHex
    val viewModel: AgentConsoleViewModel = rememberViewModel(key = "AgentConsole-$relayUrl-$pubkey") { AgentConsoleViewModel() }

    viewModel.bind(accountViewModel.account, relayUrl)

    // Keep Costs + Personas live while the console is open, so they populate once the relay's NIP-42
    // auth completes (the one-shot fetch on bind can return before the authenticated read delivers).
    DisposableEffect(relayUrl) {
        viewModel.startWatching()
        onDispose { viewModel.stopWatching() }
    }

    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    val observerFrames by viewModel.observerFrames.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(stringRes(Res.string.agent_console_tab_costs), stringRes(Res.string.agent_console_tab_personas), stringRes(Res.string.agent_console_tab_observer))

    // The observer stream is a live REQ; only keep it open while its tab is on screen.
    if (selectedTab == 2) {
        DisposableEffect(Unit) {
            viewModel.startObserving()
            onDispose { viewModel.stopObserving() }
        }
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.agent_console_title), nav) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { nav.nav(Route.AgentAttestation) },
                shape = CircleShape,
            ) {
                Icon(symbol = MaterialSymbols.Key, contentDescription = stringRes(Res.string.agent_console_attest))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> CostsTab(metrics)
                    1 -> PersonasTab(personas, nav)
                    else -> ObserverTab(observerFrames)
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CostsTab(metrics: AgentFleetMetrics) {
    if (metrics.agents.isEmpty()) {
        EmptyState(stringRes(Res.string.agent_console_empty_metrics))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FleetSummaryCard(metrics)
        }
        items(metrics.agents) { agent ->
            AgentCard(agent)
        }
    }
}

@Composable
private fun FleetSummaryCard(metrics: AgentFleetMetrics) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringRes(Res.string.agent_console_fleet_total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = formatCost(metrics.totals.costUsd),
                style = MaterialTheme.typography.headlineMedium,
            )
            MetaRow(stringRes(Res.string.agent_console_row_agents), metrics.agents.size.toString())
            MetaRow(stringRes(Res.string.agent_console_row_sessions), metrics.totalSessions.toString())
            MetaRow(stringRes(Res.string.agent_console_row_turns), metrics.totalTurns.toString())
            TokenBreakdown(metrics.totals)
            if (metrics.hasUnreliableEstimates) {
                Text(
                    text = stringRes(Res.string.agent_console_estimated_totals_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AgentCard(agent: AgentUsageSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = shortKey(agent.agentPubKey),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = formatCost(agent.totals.costUsd),
                style = MaterialTheme.typography.titleLarge,
            )
            MetaRow(stringRes(Res.string.agent_console_row_sessions), agent.sessions.toString())
            MetaRow(stringRes(Res.string.agent_console_row_turns), agent.turns.toString())
            if (agent.models.isNotEmpty()) MetaRow(stringRes(Res.string.agent_console_row_models), agent.models.sorted().joinToString(", "))
            if (agent.harnesses.isNotEmpty()) MetaRow(stringRes(Res.string.agent_console_row_harness), agent.harnesses.sorted().joinToString(", "))
            agent.lastActivity?.let { MetaRow(stringRes(Res.string.agent_console_row_last_turn), it) }
            TokenBreakdown(agent.totals)
        }
    }
}

@Composable
private fun TokenBreakdown(totals: TokenTotals) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    MetaRow(stringRes(Res.string.agent_console_row_input_tokens), formatCount(totals.inputTokens))
    MetaRow(stringRes(Res.string.agent_console_row_output_tokens), formatCount(totals.outputTokens))
    MetaRow(stringRes(Res.string.agent_console_row_total_tokens), formatCount(totals.totalTokens))
    // Null = no turn reported the component. NIP-AM forbids reading that as zero, so show a dash
    // rather than hide the row (which would look the same as a reported 0).
    MetaRow(stringRes(Res.string.agent_console_row_cache_read), totals.cacheReadTokens?.let(::formatCount) ?: "—")
    MetaRow(stringRes(Res.string.agent_console_row_cache_write), totals.cacheWriteTokens?.let(::formatCount) ?: "—")
}

@Composable
private fun PersonasTab(
    personas: List<AgentConsoleViewModel.PersonaCard>,
    nav: INav,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedButton(
                onClick = { nav.nav(Route.AgentPersonaEdit()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(symbol = MaterialSymbols.AddCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringRes(Res.string.buzz_persona_new_title))
            }
        }
        if (personas.isEmpty()) {
            item {
                Text(
                    text = stringRes(Res.string.agent_console_no_personas),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        items(personas) { persona ->
            PersonaCardView(persona) { nav.nav(Route.AgentPersonaEdit(persona.slug)) }
        }
    }
}

@Composable
private fun PersonaCardView(
    persona: AgentConsoleViewModel.PersonaCard,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = persona.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = persona.slug,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            persona.model?.let { MetaRow(stringRes(Res.string.agent_console_row_model), it) }
            persona.provider?.let { MetaRow(stringRes(Res.string.agent_console_row_provider), it) }
            persona.runtime?.let { MetaRow(stringRes(Res.string.agent_console_row_runtime), it) }
            persona.systemPrompt?.takeIf { it.isNotBlank() }?.let {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ObserverTab(frames: List<AgentConsoleViewModel.ObserverRow>) {
    if (frames.isEmpty()) {
        EmptyState(stringRes(Res.string.agent_console_empty_observer))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(frames) { frame ->
            ObserverFrameRow(frame)
            HorizontalDivider()
        }
    }
}

@Composable
private fun ObserverFrameRow(frame: AgentConsoleViewModel.ObserverRow) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = frame.kind,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = frame.timestamp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val context =
            buildString {
                append(shortKey(frame.agentPubKey))
                frame.sessionId?.let { append("  ·  session ${it.take(8)}") }
                frame.turnId?.let { append("  ·  turn ${it.take(8)}") }
                append("  ·  #${frame.seq}")
            }
        Text(
            text = context,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetaRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatCost(cost: Double): String = "$" + ((cost * 100).toLong() / 100.0).toString().let { padCents(it) }

/** Pads a dollar string to exactly two decimals (e.g. "1.5" -> "1.50", "3" -> "3.00"). */
private fun padCents(value: String): String {
    val dot = value.indexOf('.')
    if (dot < 0) return "$value.00"
    val decimals = value.length - dot - 1
    return when {
        decimals >= 2 -> value.substring(0, dot + 3)
        decimals == 1 -> value + "0"
        else -> value + "00"
    }
}

/** Groups a token count with thin spaces every three digits (1234567 -> "1 234 567"). */
private fun formatCount(n: Long): String {
    val s = n.toString()
    if (s.length <= 3) return s
    val sb = StringBuilder()
    val firstGroup = s.length % 3
    if (firstGroup > 0) {
        sb.append(s, 0, firstGroup)
    }
    var i = firstGroup
    while (i < s.length) {
        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(s, i, i + 3)
        i += 3
    }
    return sb.toString()
}

private fun shortKey(hex: String): String = if (hex.length <= 16) hex else hex.take(8) + "…" + hex.takeLast(8)
