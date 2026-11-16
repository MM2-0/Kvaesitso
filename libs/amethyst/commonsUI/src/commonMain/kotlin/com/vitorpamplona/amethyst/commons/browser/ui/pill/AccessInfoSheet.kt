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
package com.vitorpamplona.amethyst.commons.browser.ui.pill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_pill_access
import com.vitorpamplona.amethyst.commons.resources.browser_pill_access_desc
import com.vitorpamplona.amethyst.commons.resources.browser_pill_access_keys_desc
import com.vitorpamplona.amethyst.commons.resources.browser_pill_access_manage
import com.vitorpamplona.amethyst.commons.resources.browser_pill_access_napplet
import com.vitorpamplona.amethyst.commons.resources.browser_pill_access_none
import com.vitorpamplona.amethyst.commons.resources.browser_pill_access_none_desc
import com.vitorpamplona.amethyst.commons.resources.browser_pill_access_nsite
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_connection
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_open
import com.vitorpamplona.amethyst.commons.resources.browser_pill_info_tor
import com.vitorpamplona.amethyst.commons.resources.browser_pill_ok
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_off
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_on
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * "What it can access" for a sandboxed nSite or nApplet: the capabilities it was launched with, how it
 * reaches the network (nSites only), and the promise that the keys stay in Amethyst. The sandboxed
 * counterpart of [PageInfoSheet], drawn with the same header and grouped cards.
 *
 * [capabilities] are already-localized labels; empty means a static site with no special access.
 * [torOn] is null when the app has no network route of its own to show.
 */
@Composable
fun AccessInfoSheet(
    title: String,
    isWebsite: Boolean,
    capabilities: List<String>,
    torOn: Boolean?,
    onManagePermissions: (() -> Unit)?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        border = PillDefaults.hairline(),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(PillDefaults.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SiteMonogram(title, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SecurityIcon(BrowserChrome.Security.SANDBOX, size = 14.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringRes(if (isWebsite) Res.string.browser_pill_access_nsite else Res.string.browser_pill_access_napplet),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            GroupCard(heading = stringRes(Res.string.browser_pill_access)) {
                if (capabilities.isEmpty()) {
                    GroupRow(
                        icon = { Icon(MaterialSymbols.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        iconContainer = MaterialTheme.colorScheme.surfaceContainerHighest,
                        title = stringRes(Res.string.browser_pill_access_none),
                        supporting = stringRes(Res.string.browser_pill_access_none_desc),
                        onClick = null,
                    )
                } else {
                    capabilities.forEachIndexed { index, label ->
                        if (index > 0) GroupDivider()
                        GroupRow(
                            icon = { Icon(MaterialSymbols.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) },
                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                            title = label,
                            supporting = null,
                            onClick = null,
                        )
                    }
                }
                GroupDivider()
                GroupRow(
                    icon = { Icon(MaterialSymbols.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    iconContainer = MaterialTheme.colorScheme.surfaceContainerHighest,
                    title = stringRes(Res.string.browser_pill_access_desc),
                    supporting = stringRes(Res.string.browser_pill_access_keys_desc),
                    onClick = null,
                )
            }

            torOn?.let { tor ->
                GroupCard(heading = stringRes(Res.string.browser_pill_info_connection)) {
                    GroupRow(
                        icon = { PillActionIcon(BrowserChrome.Action.TOR, tint = if (tor) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp) },
                        iconContainer = if (tor) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        title = stringRes(if (tor) Res.string.browser_pill_info_tor else Res.string.browser_pill_info_open),
                        supporting = stringRes(if (tor) Res.string.browser_pill_tor_on else Res.string.browser_pill_tor_off),
                        onClick = null,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (onManagePermissions != null) {
                    TextButton(onClick = onManagePermissions) { Text(stringRes(Res.string.browser_pill_access_manage)) }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = onDone) { Text(stringRes(Res.string.browser_pill_ok)) }
            }
        }
    }
}
