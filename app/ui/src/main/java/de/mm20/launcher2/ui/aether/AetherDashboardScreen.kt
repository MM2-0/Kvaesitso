package de.mm20.launcher2.ui.aether

import de.mm20.launcher2.ui.component.TelosCyberHeading

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.TelosDotMatrixText
import de.mm20.launcher2.ui.component.TelosRadialGauge
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.TelosTelemetryCard
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.component.telosAmbientGlow
import kotlinx.serialization.Serializable

@Serializable
data class AetherDashboardRoute(
    val sharedText: String? = null,
    val sharedMediaUri: String? = null
) : NavKey

data class NostrNote(
    val id: String,
    val authorName: String,
    val npub: String,
    val content: String,
    val timeAgo: String,
    val zapsCount: Int,
    val likesCount: Int,
    val isMedia: Boolean = false
)

data class NostrRelay(
    val url: String,
    val latencyMs: Int,
    val isConnected: Boolean,
    val activeSubscriptions: Int
)

enum class AetherTab { Feed, MediaGrid, DirectMessages, RelaysHUD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherDashboardScreen(route: AetherDashboardRoute = AetherDashboardRoute()) {
    var selectedTab by remember { mutableStateOf(AetherTab.Feed) }
    var zapCount by remember { mutableIntStateOf(1450) }
    var showComposeDialog by remember { mutableStateOf(!route.sharedText.isNullOrEmpty() || !route.sharedMediaUri.isNullOrEmpty()) }
    var postText by remember { mutableStateOf(route.sharedText.orEmpty()) }

    val mockNotes = remember {
        listOf(
            NostrNote("1", "Satoshi_N", "npub1800satoshi...", "Building the future of censorship-resistant money and communications on Nostr + Lightning! ⚡", "2m ago", 120, 45),
            NostrNote("2", "Aether_Node", "npub1aether...", "Telos Aether v1.45.34 connected to 12 Nostr Relays with E2EE (NIP-44) support. 🌌", "15m ago", 89, 34),
            NostrNote("3", "Cyber_Dev", "npub1cyber...", "120 FPS Zero-Allocation UI rendering pass verified on all Telos Virtual Apps! 🚀", "1h ago", 240, 112)
        )
    }

    val mockRelays = remember {
        listOf(
            NostrRelay("wss://relay.damus.io", 42, true, 8),
            NostrRelay("wss://nos.lol", 68, true, 5),
            NostrRelay("wss://relay.nostr.band", 110, true, 4),
            NostrRelay("wss://nostr.wine", 85, true, 6)
        )
    }

    val purple = Color(0xFFA855F7)
    val cyan = Color(0xFF00F2FE)
    val pink = Color(0xFFFF007F)

    if (showComposeDialog) {
        AlertDialog(
            onDismissRequest = { showComposeDialog = false },
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            title = {
                TelosCyberHeading {
                    Text(
                        stringResource(I18nR.string.aether_compose_title),
                        fontWeight = FontWeight.Bold,
                        color = purple,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(I18nR.string.aether_preview_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = postText,
                        onValueChange = { postText = it },
                        placeholder = { Text(stringResource(I18nR.string.aether_compose_hint)) },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                    if (!route.sharedMediaUri.isNullOrEmpty()) {
                        Text(
                            stringResource(I18nR.string.aether_attached_media, route.sharedMediaUri.orEmpty()),
                            fontSize = 11.sp,
                            color = cyan,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showComposeDialog = false },
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(containerColor = purple),
                ) {
                    Text(stringResource(I18nR.string.aether_broadcast_unavailable))
                }
            },
            dismissButton = {
                TextButton(onClick = { showComposeDialog = false }) {
                    Text(stringResource(I18nR.string.hc_cancel))
                }
            },
        )
    }

    VirtualAppWindow {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D0E15).copy(alpha = 0.92f)),
                    title = {
                        TelosCyberHeading {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                TelosDotMatrixText(stringResource(I18nR.string.aether_title).uppercase(), fontSize = 16.sp, color = purple)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Refresh */ }) {
                            Icon(painterResource(BaseR.drawable.settings_backup_restore_24px), contentDescription = "Refresh", tint = cyan)
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showComposeDialog = true },
                    containerColor = purple,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.telosAmbientGlow(glowColor = purple, borderRadius = 18.dp)
                ) {
                    Icon(painterResource(BaseR.drawable.add_24px), contentDescription = "New Post")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelosTechPill("Feed", selectedTab == AetherTab.Feed, { selectedTab = AetherTab.Feed }, accentColor = purple)
                    TelosTechPill("Media", selectedTab == AetherTab.MediaGrid, { selectedTab = AetherTab.MediaGrid }, accentColor = cyan)
                    TelosTechPill("DMs", selectedTab == AetherTab.DirectMessages, { selectedTab = AetherTab.DirectMessages }, accentColor = pink)
                    TelosTechPill("Relays", selectedTab == AetherTab.RelaysHUD, { selectedTab = AetherTab.RelaysHUD }, accentColor = Color(0xFF10B981))
                }

                when (selectedTab) {
                    AetherTab.Feed -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(mockNotes, key = { it.id }) { note ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .telosAmbientGlow(glowColor = purple, borderRadius = 16.dp, alpha = 0.15f)
                                        .border(1.2.dp, purple.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131520).copy(alpha = 0.9f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Box(
                                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(purple.copy(alpha = 0.3f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(note.authorName.take(1), fontWeight = FontWeight.Bold, color = purple)
                                                }
                                                Column {
                                                    Text(note.authorName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Text(note.npub, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Text(note.timeAgo, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        Spacer(Modifier.height(10.dp))
                                        Text(note.content, fontSize = 13.sp, lineHeight = 18.sp)
                                        Spacer(Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.clickable { zapCount += 21 },
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(painterResource(BaseR.drawable.bolt_24px), contentDescription = "Zap", tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                                Text("${note.zapsCount + zapCount} sats", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFF59E0B))
                                            }
                                            Text("❤️ ${note.likesCount}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AetherTab.MediaGrid -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(6) { index ->
                                Box(
                                    modifier = Modifier
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF1B1E2E))
                                        .border(1.2.dp, cyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(painterResource(BaseR.drawable.ic_glyph_music), contentDescription = null, tint = cyan, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }

                    AetherTab.DirectMessages -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painterResource(BaseR.drawable.lock_24px), contentDescription = null, tint = pink, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(I18nR.string.aether_dm_unavailable_title),
                                    fontWeight = FontWeight.Bold,
                                    color = pink,
                                )
                                Text(
                                    stringResource(I18nR.string.aether_dm_unavailable_summary),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    AetherTab.RelaysHUD -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TelosRadialGauge(
                                    progress = 0.92f,
                                    valueText = "12/12",
                                    labelText = "RELAYS",
                                    accentColor = Color(0xFF10B981)
                                )
                                TelosRadialGauge(
                                    progress = 0.42f,
                                    valueText = "42 ms",
                                    labelText = "LATENCY",
                                    accentColor = cyan
                                )
                            }

                            Text("CONNECTED RELAYS", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = cyan)

                            mockRelays.forEach { relay ->
                                TelosTelemetryCard(
                                    title = relay.url,
                                    value = "${relay.latencyMs} ms • ${relay.activeSubscriptions} subs",
                                    bars = listOf(0.4f, 0.6f, 0.3f, 0.8f, 0.5f, 0.9f, 0.2f, 0.7f),
                                    accentColor = if (relay.latencyMs < 60) Color(0xFF10B981) else Color(0xFFF59E0B)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
