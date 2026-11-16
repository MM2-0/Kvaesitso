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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.auth_approval_always
import com.vitorpamplona.amethyst.commons.resources.auth_approval_more_relays_pending
import com.vitorpamplona.amethyst.commons.resources.auth_approval_never
import com.vitorpamplona.amethyst.commons.resources.auth_approval_once
import com.vitorpamplona.amethyst.commons.resources.auth_approval_requires_auth_many
import com.vitorpamplona.amethyst.commons.resources.auth_approval_requires_auth_one
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

/**
 * Inline AUTH approval banner.
 *
 * Renders one row per pending tier-2 NIP-42 AUTH challenge with three
 * actions: `[Once]` `[Always]` `[Never]`. Each press calls [onResolve]
 * with the user's choice, which the parent (typically a coordinator)
 * uses to complete the underlying [PendingAuthApproval.decision]
 * deferred and persist the scope.
 *
 * Stacks up to 3 entries inline; the rest collapse into a `+N more` row
 * (a future iteration may expand them on click — keep simple for now).
 *
 * The component is platform-agnostic and lives in `commons` so Android
 * and Desktop can render the same UX once the wire-up is built on each
 * platform.
 */
@Composable
fun AuthApprovalBanner(
    pending: List<PendingAuthApproval>,
    onResolve: (NormalizedRelayUrl, AuthApprovalScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = pending.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val visible = pending.take(3)
            val hidden = pending.size - visible.size

            visible.forEach { approval ->
                AuthApprovalRow(approval = approval, onResolve = onResolve)
            }

            if (hidden > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = pluralStringRes(Res.plurals.auth_approval_more_relays_pending, hidden, hidden),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthApprovalRow(
    approval: PendingAuthApproval,
    onResolve: (NormalizedRelayUrl, AuthApprovalScope) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = approval.relayUrl.displayUrl(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text =
                        if (approval.pendingCount > 1) {
                            pluralStringRes(Res.plurals.auth_approval_requires_auth_many, approval.pendingCount, approval.pendingCount)
                        } else {
                            stringRes(Res.string.auth_approval_requires_auth_one)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onResolve(approval.relayUrl, AuthApprovalScope.ONCE) }) {
                    Text(stringRes(Res.string.auth_approval_once), style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = { onResolve(approval.relayUrl, AuthApprovalScope.ALWAYS) }) {
                    Text(stringRes(Res.string.auth_approval_always), style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = { onResolve(approval.relayUrl, AuthApprovalScope.BLOCKED) }) {
                    Text(stringRes(Res.string.auth_approval_never), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
