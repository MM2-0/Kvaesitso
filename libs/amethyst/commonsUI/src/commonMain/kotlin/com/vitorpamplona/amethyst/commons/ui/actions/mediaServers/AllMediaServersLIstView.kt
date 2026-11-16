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
package com.vitorpamplona.amethyst.commons.ui.actions.mediaServers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.mediaServers.ServerHealth
import com.vitorpamplona.amethyst.commons.mediaServers.ui.MediaServerEditField
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.missingRecommendedServers
import com.vitorpamplona.amethyst.commons.model.mediaServers.recommendedServer
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relays.ui.RelayDragState
import com.vitorpamplona.amethyst.commons.relays.ui.draggableRelayItem
import com.vitorpamplona.amethyst.commons.relays.ui.relayDragHandle
import com.vitorpamplona.amethyst.commons.relays.ui.rememberRelayDragState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_a_blossom_server
import com.vitorpamplona.amethyst.commons.resources.add_media_server
import com.vitorpamplona.amethyst.commons.resources.blossom_mirror_uploads
import com.vitorpamplona.amethyst.commons.resources.blossom_mirror_uploads_caption
import com.vitorpamplona.amethyst.commons.resources.blossom_optimize_media
import com.vitorpamplona.amethyst.commons.resources.blossom_optimize_media_caption
import com.vitorpamplona.amethyst.commons.resources.delete_media_server
import com.vitorpamplona.amethyst.commons.resources.media_server_primary_badge
import com.vitorpamplona.amethyst.commons.resources.media_server_reorder
import com.vitorpamplona.amethyst.commons.resources.media_server_status_offline
import com.vitorpamplona.amethyst.commons.resources.media_server_status_online
import com.vitorpamplona.amethyst.commons.resources.media_server_status_slow
import com.vitorpamplona.amethyst.commons.resources.media_servers_add_section
import com.vitorpamplona.amethyst.commons.resources.media_servers_add_url_label
import com.vitorpamplona.amethyst.commons.resources.media_servers_all_recommended_added
import com.vitorpamplona.amethyst.commons.resources.media_servers_cache_section
import com.vitorpamplona.amethyst.commons.resources.media_servers_priority_section
import com.vitorpamplona.amethyst.commons.resources.media_servers_recommended_label
import com.vitorpamplona.amethyst.commons.resources.media_servers_reorder_hint
import com.vitorpamplona.amethyst.commons.resources.media_servers_upload_section
import com.vitorpamplona.amethyst.commons.resources.my_blossom_data
import com.vitorpamplona.amethyst.commons.resources.no_blossom_server_message
import com.vitorpamplona.amethyst.commons.resources.use_default_servers
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertPadding
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import org.jetbrains.compose.resources.StringResource

/** Vibrant palette for server monograms; picked deterministically from the display name. */
private val MonogramColors =
    listOf(
        Color(0xFF8B5CF6),
        Color(0xFF0EA5A0),
        Color(0xFFE07B00),
        Color(0xFF4169E1),
        Color(0xFFD16D8F),
        Color(0xFF4F9D4F),
        Color(0xFFB66605),
        Color(0xFF7C6FE0),
    )

@Composable
fun AllMediaBody(
    blossomServersViewModel: BlossomServersViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val blossomServersState by blossomServersViewModel.fileServers.collectAsStateWithLifecycle()
    val healthState by blossomServersViewModel.health.collectAsStateWithLifecycle()

    val dragState =
        rememberRelayDragState(
            onMove = { from, to -> blossomServersViewModel.moveServer(from, to) },
            itemCount = { blossomServersState.size },
        )

    // Auto-save the reordering once the drag finishes, rather than on every intermediate swap.
    LaunchedEffect(dragState.isDragging) {
        if (!dragState.isDragging) blossomServersViewModel.persistPending()
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = FeedPadding,
        userScrollEnabled = !dragState.isDragging,
    ) {
        item {
            SectionLabel(
                title = stringRes(id = Res.string.media_servers_priority_section),
                caption = stringRes(id = Res.string.media_servers_reorder_hint),
                topPadding = 4.dp,
            )
        }

        if (blossomServersState.isEmpty()) {
            item {
                Text(
                    text = stringRes(id = Res.string.no_blossom_server_message),
                    modifier = DoubleVertPadding,
                )
            }
        } else {
            itemsIndexed(
                blossomServersState,
                key = { _, server -> "blossom" + server.baseUrl },
            ) { index, entry ->
                MediaServerRow(
                    index = index,
                    serverEntry = entry,
                    health = healthState[entry.baseUrl] ?: ServerHealth.Unknown,
                    dragState = dragState,
                    onDelete = { blossomServersViewModel.removeServer(serverUrl = it) },
                )
            }
        }

        item {
            // Server entries carry their host in `name`.
            val missing =
                remember(blossomServersState) {
                    missingRecommendedServers(blossomServersState.mapTo(HashSet()) { it.name })
                }
            AddServerSection(
                missing = missing,
                onAddServer = { blossomServersViewModel.addServer(it) },
                onAddAll = { blossomServersViewModel.addServerList(missing.map { it.baseUrl }) },
            )
        }

        item {
            SectionLabel(title = stringRes(id = Res.string.media_servers_upload_section))
            UploadBehaviorSection(accountViewModel, nav)
        }

        item {
            SectionLabel(title = stringRes(id = Res.string.media_servers_cache_section))
            MediaCacheSection(accountViewModel)
        }

        item {
            Spacer(DoubleHorzSpacer)
        }
    }
}

/**
 * Upload-side Blossom controls: mirror uploads across the user's servers (BUD-04),
 * optimize via `/media` (BUD-05), and a shortcut into the blob manager.
 */
@Composable
private fun UploadBehaviorSection(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val mirror by accountViewModel.account.settings.mirrorUploadsToAllServers
        .collectAsStateWithLifecycle()
    val optimize by accountViewModel.account.settings.optimizeMediaOnUpload
        .collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        UploadToggleRow(
            title = stringRes(id = Res.string.blossom_mirror_uploads),
            caption = stringRes(id = Res.string.blossom_mirror_uploads_caption),
            checked = mirror,
            onCheckedChange = { accountViewModel.account.settings.changeMirrorUploadsToAllServers(it) },
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        UploadToggleRow(
            title = stringRes(id = Res.string.blossom_optimize_media),
            caption = stringRes(id = Res.string.blossom_optimize_media_caption),
            checked = optimize,
            onCheckedChange = { accountViewModel.account.settings.changeOptimizeMediaOnUpload(it) },
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { nav.nav(Route.ManageBlossomBlobs) }
                    .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringRes(id = Res.string.my_blossom_data),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                symbol = MaterialSymbols.Storage,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun UploadToggleRow(
    title: String,
    caption: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Compact section header: an accent label with an optional gray caption below. */
@Composable
private fun SectionLabel(
    title: String,
    caption: String? = null,
    topPadding: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = topPadding, bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

/**
 * A draggable, ranked server card. Position in the list is the upload/fallback priority
 * (row #1 is tried first), so each card carries its rank on the monogram and a drag handle
 * wired into the shared [RelayDragState], plus a live reachability dot. The primary target
 * (#1) is called out with an accent border, tint, and badge.
 */
@Composable
fun MediaServerRow(
    index: Int,
    serverEntry: ServerName,
    health: ServerHealth,
    dragState: RelayDragState,
    onDelete: (serverUrl: String) -> Unit,
) {
    val isPrimary = index == 0
    val shape = RoundedCornerShape(16.dp)
    // A recommended server shows the same friendly name its chip had; any other shows its host.
    val title = recommendedServer(serverEntry.name)?.name ?: serverEntry.name.replaceFirstChar(Char::titlecase)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .draggableRelayItem(index, dragState)
                .clip(shape)
                .background(
                    if (isPrimary) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                    } else {
                        Color.Transparent
                    },
                ).border(
                    width = if (isPrimary) 1.5.dp else 1.dp,
                    color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                ).padding(start = 6.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = MaterialSymbols.DragIndicator,
            contentDescription = stringRes(id = Res.string.media_server_reorder),
            modifier = Modifier.size(22.dp).relayDragHandle(index, dragState),
            tint = MaterialTheme.colorScheme.grayText,
        )

        Spacer(Modifier.size(8.dp))

        ServerAvatar(name = title, rank = index + 1, isPrimary = isPrimary)

        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isPrimary) {
                    Spacer(Modifier.size(6.dp))
                    PrimaryBadge()
                }
            }
            Text(
                text = serverEntry.baseUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        HealthIndicator(health)

        IconButton(onClick = { onDelete(serverEntry.baseUrl) }) {
            Icon(
                symbol = MaterialSymbols.Delete,
                contentDescription = stringRes(id = Res.string.delete_media_server),
                tint = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

/**
 * Inline "add a server" area: the recommended servers not yet in the list as a horizontal
 * strip of add-chips, then a URL field. Once every recommended server is in the list the
 * strip and its "Use Default List" button give way to a single line saying so.
 */
@Composable
private fun AddServerSection(
    missing: List<ServerName>,
    onAddServer: (String) -> Unit,
    onAddAll: () -> Unit,
) {
    SectionLabel(title = stringRes(id = Res.string.media_servers_add_section))

    // Recommended servers first — one tap adds a known-good host.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(id = Res.string.media_servers_recommended_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.grayText,
            modifier = Modifier.weight(1f),
        )
        if (missing.isNotEmpty()) {
            TextButton(onClick = onAddAll) {
                Text(text = stringRes(id = Res.string.use_default_servers))
            }
        }
    }

    if (missing.isEmpty()) {
        Text(
            text = stringRes(id = Res.string.media_servers_all_recommended_added),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.grayText,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    } else {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(
                missing,
                key = { it.baseUrl },
            ) { server ->
                RecommendedChip(
                    serverEntry = server,
                    onAdd = { onAddServer(server.baseUrl) },
                )
            }
        }
    }

    // ...or paste any server address.
    Text(
        text = stringRes(id = Res.string.media_servers_add_url_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.grayText,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
    MediaServerEditField(Res.string.add_a_blossom_server) { onAddServer(it) }
}

/**
 * A recommended server as a tappable pill. Only servers missing from the list get one, so a
 * tap adds it, the chip leaves the strip and the server appears as a row above.
 */
@Composable
private fun RecommendedChip(
    serverEntry: ServerName,
    onAdd: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier =
            Modifier
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(onClick = onAdd)
                .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ServerMonogram(name = serverEntry.name, size = 24.dp)
        Text(
            text = serverEntry.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        Icon(
            symbol = MaterialSymbols.Add,
            contentDescription = stringRes(id = Res.string.add_media_server),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** A colored letter tile identifying a server, derived from its display name. */
@Composable
private fun ServerMonogram(
    name: String,
    size: Dp,
) {
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    val color = MonogramColors[((name.hashCode() % MonogramColors.size) + MonogramColors.size) % MonogramColors.size]
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 3))
                .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = if (size >= 30.dp) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/**
 * The server's monogram with its priority rank as a small corner badge — one visual unit
 * for identity + position. The rank chip is accent-filled for the primary target (#1).
 */
@Composable
private fun ServerAvatar(
    name: String,
    rank: Int,
    isPrimary: Boolean,
) {
    Box(modifier = Modifier.size(40.dp)) {
        ServerMonogram(name = name, size = 36.dp)

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(1.5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            if (isPrimary) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rank.toString(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (isPrimary) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

/** Small "Primary" pill shown on the #1 server. */
@Composable
private fun PrimaryBadge() {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = stringRes(id = Res.string.media_server_primary_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Colored reachability dot + label, or a spinner while a probe is in flight. */
@Composable
private fun HealthIndicator(health: ServerHealth) {
    if (health == ServerHealth.Unknown) return

    if (health == ServerHealth.Checking) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.grayText,
        )
        return
    }

    val color: Color
    val label: StringResource
    when (health) {
        ServerHealth.Online -> {
            color = MaterialTheme.colorScheme.allGoodColor
            label = Res.string.media_server_status_online
        }
        ServerHealth.Slow -> {
            color = MaterialTheme.colorScheme.warningColor
            label = Res.string.media_server_status_slow
        }
        else -> {
            color = MaterialTheme.colorScheme.error
            label = Res.string.media_server_status_offline
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Text(
            text = stringRes(id = label),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}
