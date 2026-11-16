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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.vanish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip62Vanish.ComplianceStatus
import com.vitorpamplona.amethyst.commons.model.nip62Vanish.VanishEventItem
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.vanish_all_relays
import com.vitorpamplona.amethyst.commons.resources.vanish_all_relays_compliance_hint
import com.vitorpamplona.amethyst.commons.resources.vanish_compliant
import com.vitorpamplona.amethyst.commons.resources.vanish_date_label
import com.vitorpamplona.amethyst.commons.resources.vanish_events_description
import com.vitorpamplona.amethyst.commons.resources.vanish_events_empty
import com.vitorpamplona.amethyst.commons.resources.vanish_events_empty_hint
import com.vitorpamplona.amethyst.commons.resources.vanish_events_title
import com.vitorpamplona.amethyst.commons.resources.vanish_non_compliant
import com.vitorpamplona.amethyst.commons.resources.vanish_target_relays_label
import com.vitorpamplona.amethyst.commons.resources.vanish_test_button
import com.vitorpamplona.amethyst.commons.resources.vanish_test_error
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import kotlinx.coroutines.launch

@Composable
fun VanishEventsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(
                stringRes(id = Res.string.vanish_events_title),
                nav,
            )
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            VanishEventsBody(accountViewModel, nav)
        }
    }
}

@Composable
fun VanishEventsBody(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vanishEvents by accountViewModel.account.vanish.testableFlow
        .collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    if (vanishEvents.isEmpty()) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                MaterialSymbols.PublicOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringRes(Res.string.vanish_events_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringRes(Res.string.vanish_events_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    text = stringRes(Res.string.vanish_events_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            items(vanishEvents, key = { it.event.id }) { item ->
                VanishEventCard(
                    item = item,
                    onTestCompliance = { relay ->
                        scope.launch {
                            accountViewModel.account.vanish.testVanishCompliance(item, relay)
                        }
                    },
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun VanishEventCard(
    item: VanishEventItem,
    onTestCompliance: (NormalizedRelayUrl) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(Res.string.vanish_date_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDateTime(item.event.createdAt * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider(thickness = DividerThickness)

            Spacer(modifier = Modifier.height(8.dp))

            if (item.isAllRelays) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        MaterialSymbols.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringRes(Res.string.vanish_all_relays),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringRes(Res.string.vanish_all_relays_compliance_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringRes(Res.string.vanish_target_relays_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(4.dp))

                RenderRelaysWithComplianceResults(item, onTestCompliance)
            }

            if (item.event.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(thickness = DividerThickness)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.event.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RenderRelaysWithComplianceResults(
    item: VanishEventItem,
    onTestCompliance: (NormalizedRelayUrl) -> Unit,
) {
    val complianceResults by item.complianceResults.collectAsStateWithLifecycle()
    item.relays.forEach { relay ->
        RelayComplianceRow(
            relay = relay,
            status = complianceResults[relay] ?: ComplianceStatus.UNTESTED,
            onTest = { onTestCompliance(relay) },
        )
    }
}

@Composable
private fun RelayComplianceRow(
    relay: NormalizedRelayUrl,
    status: ComplianceStatus,
    onTest: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = relay.displayUrl(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.width(8.dp))

        when (status) {
            ComplianceStatus.UNTESTED -> {
                FilledTonalButton(
                    onClick = onTest,
                    modifier = Modifier.height(32.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Icon(
                        MaterialSymbols.Science,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringRes(Res.string.vanish_test_button),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            ComplianceStatus.TESTING -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }

            ComplianceStatus.COMPLIANT -> {
                Icon(
                    MaterialSymbols.CheckCircle,
                    contentDescription = stringRes(Res.string.vanish_compliant),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringRes(Res.string.vanish_compliant),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            ComplianceStatus.NON_COMPLIANT -> {
                Icon(
                    MaterialSymbols.Error,
                    contentDescription = stringRes(Res.string.vanish_non_compliant),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringRes(Res.string.vanish_non_compliant),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            ComplianceStatus.ERROR -> {
                Icon(
                    MaterialSymbols.Error,
                    contentDescription = stringRes(Res.string.vanish_test_error),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringRes(Res.string.vanish_test_error),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
