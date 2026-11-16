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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ecash_mint_cashu
import com.vitorpamplona.amethyst.commons.resources.ecash_mint_fedimint
import com.vitorpamplona.amethyst.commons.resources.ecash_mint_generic
import com.vitorpamplona.amethyst.commons.resources.ecash_mint_modules
import com.vitorpamplona.amethyst.commons.resources.ecash_mint_recommendation
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.quartz.nip87Ecash.cashu.CashuMintEvent
import com.vitorpamplona.quartz.nip87Ecash.fedimint.FedimintEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent

@Composable
fun RenderCashuMint(noteEvent: CashuMintEvent) {
    val mintUrl = remember(noteEvent) { noteEvent.mintUrl() }
    val nuts = remember(noteEvent) { noteEvent.nuts() }
    val network = remember(noteEvent) { noteEvent.network() }

    MintAnnouncementCard(
        title = stringRes(Res.string.ecash_mint_cashu),
        url = mintUrl,
        network = network.code,
        capabilities = nuts,
        capabilitiesLabel = "NUTs",
        metadata = noteEvent.content.ifBlank { null },
    )
}

@Composable
fun RenderFedimint(noteEvent: FedimintEvent) {
    val inviteCodes = remember(noteEvent) { noteEvent.inviteCodes() }
    val modules = remember(noteEvent) { noteEvent.modules() }
    val network = remember(noteEvent) { noteEvent.network() }

    MintAnnouncementCard(
        title = stringRes(Res.string.ecash_mint_fedimint),
        url = inviteCodes.firstOrNull(),
        network = network.code,
        capabilities = modules,
        capabilitiesLabel = stringRes(Res.string.ecash_mint_modules),
        metadata = noteEvent.content.ifBlank { null },
    )
}

@Composable
fun RenderMintRecommendation(noteEvent: MintRecommendationEvent) {
    val mintUrls = remember(noteEvent) { noteEvent.mintUrls() }
    val mintType =
        remember(noteEvent) {
            when {
                noteEvent.isCashuRecommendation() -> Res.string.ecash_mint_cashu
                noteEvent.isFedimintRecommendation() -> Res.string.ecash_mint_fedimint
                else -> Res.string.ecash_mint_generic
            }
        }

    MintAnnouncementCard(
        title = stringRes(Res.string.ecash_mint_recommendation, stringRes(mintType)),
        url = mintUrls.firstOrNull(),
        network = null,
        capabilities = emptyList(),
        capabilitiesLabel = null,
        metadata = noteEvent.content.ifBlank { null },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MintAnnouncementCard(
    title: String,
    url: String?,
    network: String?,
    capabilities: List<String>,
    capabilitiesLabel: String?,
    metadata: String?,
) {
    Row(
        modifier =
            Modifier
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ).fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (network != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = network,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier
                                .clip(SmallBorder)
                                .border(1.dp, MaterialTheme.colorScheme.primary, SmallBorder)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            url?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (capabilities.isNotEmpty() && capabilitiesLabel != null) {
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "$capabilitiesLabel: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                    )
                    capabilities.forEach { capability ->
                        Text(
                            text = capability,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier =
                                Modifier
                                    .clip(SmallBorder)
                                    .border(1.dp, Color.Gray.copy(alpha = 0.3f), SmallBorder)
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            metadata?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
