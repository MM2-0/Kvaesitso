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
package com.vitorpamplona.amethyst.commons.napplet.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentInfo
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_allow_always
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_allow_once
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_deny_always
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_hide_event
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_not_now
import com.vitorpamplona.amethyst.commons.resources.napplet_consent_show_event
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * What a napplet asks permission for, and the answers: the body of the consent dialog on every
 * platform. The caller supplies the window or dialog around it and scrolls nothing itself: the card
 * scrolls when it is taller than the space it gets.
 *
 * [onDecision] gets [GrantState.ALLOW_ALWAYS], [GrantState.ALLOW_ONCE], [GrantState.ASK] (not now)
 * or [GrantState.DENY] (deny always).
 */
@Composable
fun NappletConsentCard(
    info: NappletConsentInfo,
    onDecision: (GrantState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
    ) {
        // Centered header: icon + app name + capability category + coordinate
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val isBrowser = info.coordinate.startsWith("browser:")
            FavoriteAppIcon(
                app =
                    if (isBrowser) {
                        FavoriteApp.WebApp(info.coordinate.substringAfter(':'), info.appletTitle, 0L, info.iconUrl)
                    } else {
                        FavoriteApp.NostrApp(info.coordinate, info.appletTitle, 0L, info.iconUrl)
                    },
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
            Text(
                info.appletTitle,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (info.capabilityLabel.isNotBlank()) {
                Text(
                    info.capabilityLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            // A website's title is already its host; repeating the URL under it says nothing.
            if (!info.coordinate.startsWith("browser:")) {
                Text(
                    info.coordinate.substringAfter(':', "").ifBlank { info.coordinate.substringBefore(':').take(12) + "…" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Operation detail box (may include content preview), plus the full event behind a
        // toggle: the summary truncates content and cannot spell out every tag, so for kinds
        // whose payload IS the tags (3, 5, 10000, 10002) this is the only complete disclosure.
        if (info.operationSummary.isNotBlank() || info.rawData.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (info.operationSummary.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                info.operationSummary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    // A single-account follow/mute change: show who, so the user recognizes
                    // the face rather than parsing a name they may not read carefully.
                    info.subject?.let { subject ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RobohashFallbackAsyncImage(
                                robot = subject.pubKey,
                                model = subject.pictureUrl,
                                contentDescription = subject.name,
                                modifier = Modifier.size(36.dp).clip(CircleShape),
                                loadProfilePicture = true,
                                loadRobohash = true,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                subject.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (info.rawData.isNotBlank()) {
                        var showRawData by remember { mutableStateOf(false) }
                        if (showRawData) {
                            Spacer(Modifier.height(8.dp))
                            Surface(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                SelectionContainer {
                                    Text(
                                        info.rawData,
                                        style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                            ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { showRawData = !showRawData }) {
                            Text(
                                if (showRawData) {
                                    stringRes(Res.string.napplet_consent_hide_event)
                                } else {
                                    stringRes(Res.string.napplet_consent_show_event)
                                },
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        if (info.allowAlways) {
            Button(
                onClick = { onDecision(GrantState.ALLOW_ALWAYS) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(stringRes(Res.string.napplet_consent_allow_always))
            }
            OutlinedButton(
                onClick = { onDecision(GrantState.ALLOW_ONCE) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(stringRes(Res.string.napplet_consent_allow_once))
            }
        } else {
            Button(
                onClick = { onDecision(GrantState.ALLOW_ONCE) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(stringRes(Res.string.napplet_consent_allow_once))
            }
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { onDecision(GrantState.ASK) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        ) {
            Text(
                stringRes(Res.string.napplet_consent_not_now),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(
            onClick = { onDecision(GrantState.DENY) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(
                stringRes(Res.string.napplet_consent_deny_always),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
