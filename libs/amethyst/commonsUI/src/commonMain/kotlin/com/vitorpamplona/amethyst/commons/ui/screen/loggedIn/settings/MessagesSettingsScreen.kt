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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.concord.ConcordViewMode
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupViewMode
import com.vitorpamplona.amethyst.commons.napplet.ui.PolicyCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chat_type_concord_desc
import com.vitorpamplona.amethyst.commons.resources.chat_type_concord_title
import com.vitorpamplona.amethyst.commons.resources.chat_type_cordn_desc
import com.vitorpamplona.amethyst.commons.resources.chat_type_cordn_title
import com.vitorpamplona.amethyst.commons.resources.chat_type_ephemeral_desc
import com.vitorpamplona.amethyst.commons.resources.chat_type_ephemeral_title
import com.vitorpamplona.amethyst.commons.resources.chat_type_geohash_desc
import com.vitorpamplona.amethyst.commons.resources.chat_type_geohash_title
import com.vitorpamplona.amethyst.commons.resources.chat_type_marmot_desc
import com.vitorpamplona.amethyst.commons.resources.chat_type_marmot_title
import com.vitorpamplona.amethyst.commons.resources.chat_type_nip04_desc
import com.vitorpamplona.amethyst.commons.resources.chat_type_nip04_title
import com.vitorpamplona.amethyst.commons.resources.chat_type_nip17_desc
import com.vitorpamplona.amethyst.commons.resources.chat_type_nip17_title
import com.vitorpamplona.amethyst.commons.resources.chat_type_nip28_desc
import com.vitorpamplona.amethyst.commons.resources.chat_type_nip28_title
import com.vitorpamplona.amethyst.commons.resources.chat_type_nip29_desc
import com.vitorpamplona.amethyst.commons.resources.chat_type_nip29_title
import com.vitorpamplona.amethyst.commons.resources.concord_view_grouped
import com.vitorpamplona.amethyst.commons.resources.concord_view_grouped_desc
import com.vitorpamplona.amethyst.commons.resources.concord_view_inline
import com.vitorpamplona.amethyst.commons.resources.concord_view_inline_desc
import com.vitorpamplona.amethyst.commons.resources.concord_view_mode_title
import com.vitorpamplona.amethyst.commons.resources.messages_load_types_desc
import com.vitorpamplona.amethyst.commons.resources.messages_load_types_title
import com.vitorpamplona.amethyst.commons.resources.messages_settings
import com.vitorpamplona.amethyst.commons.resources.relay_group_view_grouped
import com.vitorpamplona.amethyst.commons.resources.relay_group_view_grouped_desc
import com.vitorpamplona.amethyst.commons.resources.relay_group_view_inline
import com.vitorpamplona.amethyst.commons.resources.relay_group_view_inline_desc
import com.vitorpamplona.amethyst.commons.resources.relay_group_view_mode_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import org.jetbrains.compose.resources.StringResource

@Composable
@Preview(device = "spec:width=2100px,height=2340px,dpi=440")
fun MessagesSettingsScreenPreview() {
    ThemeComparisonRow {
        MessagesSettingsScreen(mockAccountViewModel(), EmptyNav())
    }
}

/**
 * A single toggleable Messages conversation type, with a distinct accent color that carries over onto
 * its Switch so the (long) list reads as a colorful palette instead of a wall of grey rows.
 */
private data class ChatFeedTypeUi(
    val type: ChatFeedType,
    val titleRes: StringResource,
    val descRes: StringResource,
    val accent: Color,
)

// Ordered by how central each type is to the inbox (private first, exotic last).
private val CHAT_FEED_TYPES =
    listOf(
        ChatFeedTypeUi(ChatFeedType.NIP17, Res.string.chat_type_nip17_title, Res.string.chat_type_nip17_desc, Color(0xFF2EBD85)),
        ChatFeedTypeUi(ChatFeedType.NIP04, Res.string.chat_type_nip04_title, Res.string.chat_type_nip04_desc, Color(0xFFF6A609)),
        ChatFeedTypeUi(ChatFeedType.NIP28, Res.string.chat_type_nip28_title, Res.string.chat_type_nip28_desc, Color(0xFF2E90FA)),
        ChatFeedTypeUi(ChatFeedType.NIP29, Res.string.chat_type_nip29_title, Res.string.chat_type_nip29_desc, Color(0xFF9E77ED)),
        ChatFeedTypeUi(ChatFeedType.MARMOT, Res.string.chat_type_marmot_title, Res.string.chat_type_marmot_desc, Color(0xFF5B6AD0)),
        ChatFeedTypeUi(ChatFeedType.CONCORD, Res.string.chat_type_concord_title, Res.string.chat_type_concord_desc, Color(0xFFEC4899)),
        ChatFeedTypeUi(ChatFeedType.CORDN, Res.string.chat_type_cordn_title, Res.string.chat_type_cordn_desc, Color(0xFF14B8A6)),
        ChatFeedTypeUi(ChatFeedType.GEOHASH, Res.string.chat_type_geohash_title, Res.string.chat_type_geohash_desc, Color(0xFFEF4444)),
        ChatFeedTypeUi(ChatFeedType.EPHEMERAL, Res.string.chat_type_ephemeral_title, Res.string.chat_type_ephemeral_desc, Color(0xFF06B6D4)),
    )

/**
 * User preferences for the Messages tab. The main control is a set of per-conversation-type load
 * toggles: each turns a chat kind (NIP-04/17/28/29, Marmot, Concord, geohash, ephemeral) both off the
 * inbox AND off the always-on downloading routes. Below that, the NIP-29 and Concord display modes
 * only surface while their type is enabled, so the screen stays focused.
 */
@Composable
fun MessagesSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val enabled by accountViewModel.account.settings.enabledChatFeeds
        .collectAsStateWithLifecycle()
    val mode by accountViewModel.account.settings.relayGroupViewMode
        .collectAsStateWithLifecycle()
    val concordMode by accountViewModel.account.settings.concordViewMode
        .collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(Res.string.messages_settings), nav)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
        ) {
            item {
                SectionHeader(
                    title = stringRes(Res.string.messages_load_types_title),
                    description = stringRes(Res.string.messages_load_types_desc),
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                ) {
                    Column {
                        CHAT_FEED_TYPES.forEachIndexed { index, ui ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 68.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }
                            ChatTypeToggleRow(
                                ui = ui,
                                checked = ui.type in enabled,
                                onCheckedChange = { accountViewModel.account.settings.setChatFeedEnabled(ui.type, it) },
                            )
                        }
                    }
                }
            }

            if (ChatFeedType.NIP29 in enabled) {
                item {
                    SectionHeader(title = stringRes(Res.string.relay_group_view_mode_title))
                }
                item {
                    ViewModeCards {
                        PolicyCard(
                            selected = mode == RelayGroupViewMode.INLINE,
                            symbol = MaterialSymbols.AutoMirrored.ViewList,
                            label = stringRes(Res.string.relay_group_view_inline),
                            description = stringRes(Res.string.relay_group_view_inline_desc),
                            onClick = { accountViewModel.account.settings.updateRelayGroupViewMode(RelayGroupViewMode.INLINE) },
                        )
                        PolicyCard(
                            selected = mode == RelayGroupViewMode.GROUPED,
                            symbol = MaterialSymbols.Folder,
                            label = stringRes(Res.string.relay_group_view_grouped),
                            description = stringRes(Res.string.relay_group_view_grouped_desc),
                            onClick = { accountViewModel.account.settings.updateRelayGroupViewMode(RelayGroupViewMode.GROUPED) },
                        )
                    }
                }
            }

            if (ChatFeedType.CONCORD in enabled) {
                item {
                    SectionHeader(title = stringRes(Res.string.concord_view_mode_title))
                }
                item {
                    ViewModeCards {
                        PolicyCard(
                            selected = concordMode == ConcordViewMode.INLINE,
                            symbol = MaterialSymbols.AutoMirrored.ViewList,
                            label = stringRes(Res.string.concord_view_inline),
                            description = stringRes(Res.string.concord_view_inline_desc),
                            onClick = { accountViewModel.account.settings.updateConcordViewMode(ConcordViewMode.INLINE) },
                        )
                        PolicyCard(
                            selected = concordMode == ConcordViewMode.GROUPED,
                            symbol = MaterialSymbols.Folder,
                            label = stringRes(Res.string.concord_view_grouped),
                            description = stringRes(Res.string.concord_view_grouped_desc),
                            onClick = { accountViewModel.account.settings.updateConcordViewMode(ConcordViewMode.GROUPED) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    description: String? = null,
) {
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ChatTypeToggleRow(
    ui: ChatFeedTypeUi,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // Fade the accent badge out when off, so the palette itself signals what's active.
    val badgeColor by animateColorAsState(
        targetValue = if (checked) ui.accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
        label = "badgeColor",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 12.dp),
        ) {
            Text(
                text = stringRes(ui.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringRes(ui.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ui.accent,
                    checkedBorderColor = ui.accent,
                ),
        )
    }
}

/** Stacks the bordered selection cards (one per mode) with the same spacing the Relay Authentication
 *  "When to authenticate" picker uses. */
@Composable
private fun ViewModeCards(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
