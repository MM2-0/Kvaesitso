package de.mm20.launcher2.ui.media.youtube
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.youtube.YouTubePlaylistSummary
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import de.mm20.launcher2.ui.R

@Composable
fun YouTubeScreen(music: Boolean, vm: YouTubeViewModel, initialLink: String? = null, linkRequestId: Long = 0L) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    var query by rememberSaveable(music) { mutableStateOf("") }
    var searchExpanded by rememberSaveable(music) { mutableStateOf(false) }
    var registeringLinks by remember { mutableStateOf(false) }
    var linkStatus by remember { mutableStateOf<Int?>(null) }
    var accountPanel by rememberSaveable { mutableStateOf(false) }
    var browserUnavailable by rememberSaveable { mutableStateOf(false) }
    val allEntries by vm.results.collectAsStateWithLifecycle()
    val favorites by vm.library.favorites.collectAsStateWithLifecycle()
    val playlists by vm.library.playlists.collectAsStateWithLifecycle()
    val userPlaylists by vm.userPlaylists.collectAsStateWithLifecycle(emptyList<de.mm20.launcher2.comms.youtube.YouTubePlaylistSummary>())
    val sessionExpired by vm.sessionExpired.collectAsStateWithLifecycle()
    val loadingAccount by vm.isLoadingPlaylists.collectAsStateWithLifecycle()

    var shelf by rememberSaveable(music) { mutableStateOf("search") }
    var consumedLinkRequest by rememberSaveable(music) { mutableStateOf<Long?>(null) }
    LaunchedEffect(music, initialLink, linkRequestId) {
        vm.loadUserPlaylists(context)
        val newRequest = consumedLinkRequest != linkRequestId
        if (initialLink != null && (newRequest || (vm.results.value[music].isNullOrEmpty() && !vm.searching.value))) {
            consumedLinkRequest = linkRequestId
            query = (de.mm20.launcher2.comms.youtube.flow.parseYouTubeLink(initialLink) as?
                de.mm20.launcher2.comms.youtube.flow.YouTubeLink.Search)?.query ?: initialLink
            searchExpanded = true
            shelf = "search"
            vm.openLink(context, initialLink, music, autoplay = newRequest)
        } else if (initialLink == null) {
            vm.loadHome(context, music)
        }
    }

    var playlistTarget by remember { mutableStateOf<de.mm20.launcher2.comms.youtube.YouTubeEntry?>(null) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    val entries = when {
        shelf == "search" || shelf.startsWith("online:") -> allEntries[music].orEmpty()
        shelf == "favorites" -> favorites
        else -> playlists[shelf.removePrefix("playlist:")].orEmpty()
    }
    val searching by vm.searching.collectAsStateWithLifecycle()
    val resolving by vm.resolving.collectAsStateWithLifecycle()
    val buffering by vm.buffering.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val controller by vm.controller.collectAsStateWithLifecycle()
    val now by vm.nowPlaying.collectAsStateWithLifecycle()

    de.mm20.launcher2.ui.media.MediaFrame(
        stringResource(if (music) I18nR.string.telos_youtube_music_label else I18nR.string.telos_youtube_label),
        actions = {
            de.mm20.launcher2.ui.media.MediaSearchAction(searchExpanded, query.isNotBlank()) {
                searchExpanded = !searchExpanded
                if (searchExpanded) scope.launch { gridState.animateScrollToItem(0) }
            }
            IconButton(onClick = { accountPanel = true }) {
                Icon(
                    painterResource(BaseR.drawable.person_24px),
                    stringResource(I18nR.string.hc_account)
                )
            }
        },
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive((if (music) 360.dp else 320.dp) * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!music && now?.mediaMetadata?.extras?.getBoolean("audioOnly") == false && controller != null) {
                item(key = "youtube-player", span = { GridItemSpan(maxLineSpan) }) {
                        AndroidView(
                            factory = { PlayerView(it).apply { player = controller; useController = true } },
                            update = { it.player = controller },
                            onRelease = { it.player = null },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9)
                                .clip(RoundedCornerShape(24.dp))
                        )
                }
                    }

            if (sessionExpired) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(I18nR.string.hf_remote_sign_in_again),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = stringResource(I18nR.string.media_native_account_help),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                            TextButton(onClick = { accountPanel = true }) {
                                Text(stringResource(I18nR.string.hf_remote_sign_in), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    de.mm20.launcher2.ui.media.MediaSearchBar(
                        query, { query = it }, stringResource(I18nR.string.telos_youtube_search),
                        expanded = searchExpanded, onClose = { searchExpanded = false }, searchEnabled = !searching,
                        onSearch = {
                            if (query.isNotBlank() && !searching) {
                                shelf = "search"
                                vm.search(context, query, music)
                            }
                        },
                    )

                    // YouTube Online Library Glass Cards
                    if (userPlaylists.isNotEmpty()) {
                        YouTubePlaylistChipsRow(
                            userPlaylists = userPlaylists,
                            selectedUrl = if (shelf.startsWith("online:")) shelf.removePrefix("online:") else null,
                            onSelectPlaylist = { pl ->
                                shelf = "online:${pl.url}"
                                vm.selectPlaylist(context, pl.url, music)
                            }
                        )
                    }

                    // Flow-style Navigation Filter Bar
                    run {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                de.mm20.launcher2.ui.media.MediaFilterButton(
                                    selected = shelf == "search" && query.isBlank(),
                                    onClick = { shelf = "search"; query = ""; vm.loadHome(context, music, refresh = true) },
                                    label = stringResource(I18nR.string.au10_music_tab_home))
                            }
                            item {
                                de.mm20.launcher2.ui.media.MediaFilterButton(
                                    selected = shelf == "favorites",
                                    onClick = { shelf = "favorites" }, label = stringResource(I18nR.string.hc_favorites))
                            }
                            items(playlists.keys.toList(), key = { it }) { name ->
                                InputChip(
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    selected = shelf == "playlist:$name",
                                    onClick = { shelf = "playlist:$name" },
                                    label = { Text(name) },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            vm.library.delete(name)
                                            if (shelf == "playlist:$name") shelf = "search"
                                        }) {
                                            Icon(
                                                painterResource(BaseR.drawable.close_24px),
                                                stringResource(I18nR.string.hc_delete),
                                                Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        if (shelf != "search" && entries.isNotEmpty()) {
                            Button(
                                onClick = { vm.play(context, entries.first(), music, entries); if (!music) scope.launch { gridState.animateScrollToItem(0) } },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(painterResource(BaseR.drawable.play_arrow_24px), contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(I18nR.string.hc_play))
                            }
                        }
                    }

                    if (shelf == "favorites" || shelf.startsWith("playlist:")) {
                        Text(
                            stringResource(I18nR.string.telos_youtube_library_local),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (searching || resolving || buffering || loadingAccount) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            stringResource(if (resolving || buffering) I18nR.string.telos_youtube_preparing
                                else if (loadingAccount && !searching) I18nR.string.telos_youtube_tab_playlists else I18nR.string.telos_youtube_searching),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    if (entries.isEmpty() && !searching) {
                        Text(
                            stringResource(I18nR.string.telos_youtube_intro),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Flow-style Media Cards
            gridItems(entries.distinctBy { it.url }, key = { it.url }) { entry ->
                FlowMediaCard(
                    entry = entry,
                    music = music,
                    isFavorite = favorites.any { it.url == entry.url },
                    onPlay = {
                        vm.play(context, entry, music, entries)
                        if (!music) scope.launch { gridState.animateScrollToItem(0) }
                    },
                    onFavorite = { vm.library.favorite(entry) },
                    onAddPlaylist = { playlistTarget = entry; playlistName = "" },
                    onRadio = { shelf = "search"; vm.artistRadio(context, entry) },
                    onMoodRadio = { mood -> shelf = "search"; vm.moodRadio(context, entry, mood) },
                    canRemoveFromPlaylist = shelf.startsWith("playlist:"),
                    onDeleteFromPlaylist = {
                        if (shelf.startsWith("playlist:")) {
                            vm.library.remove(shelf.removePrefix("playlist:"), entry)
                        }
                    }
                )
            }
        }
    }

    playlistTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { playlistTarget = null },
            title = { Text(stringResource(I18nR.string.telos_youtube_add_playlist)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        playlistName,
                        { playlistName = it },
                        label = { Text(stringResource(I18nR.string.telos_youtube_playlist_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(playlists.keys.toList()) { name ->
                            TextButton(
                                onClick = {
                                    vm.library.add(name, entry)
                                    playlistTarget = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = playlistName.isNotBlank(),
                    onClick = {
                        vm.library.add(playlistName, entry)
                        playlistTarget = null
                    }
                ) {
                    Text(stringResource(I18nR.string.hc_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistTarget = null }) {
                    Text(stringResource(I18nR.string.hc_cancel))
                }
            }
        )
    }

    var loginPanel by rememberSaveable { mutableStateOf(false) }

    if (loginPanel) {
        YouTubeLoginDialog(
            onDismiss = { loginPanel = false },
            onSuccess = {
                loginPanel = false
                vm.reconnect(context)
                vm.loadHome(context, music, refresh = true)
            }
        )
    }

    if (accountPanel) {
        var importUrl by remember { mutableStateOf("") }
        var importing by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { accountPanel = false },
            title = { Text(stringResource(I18nR.string.hc_account)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(I18nR.string.media_native_account_help), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        enabled = !registeringLinks,
                        onClick = {
                            registeringLinks = true
                            scope.launch {
                                try {
                                    linkStatus = if (registerYouTubeLinks(context)) I18nR.string.hc_done
                                        else I18nR.string.youtube_links_registration_failed
                                } finally { registeringLinks = false }
                            }
                        }, modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(I18nR.string.youtube_register_links_shizuku)) }
                    linkStatus?.let { Text(stringResource(it)) }


                    Button(
                        onClick = { loginPanel = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(I18nR.string.hf_remote_sign_in))
                    }

                    if (browserUnavailable) {
                        Text(stringResource(I18nR.string.telos_youtube_browser_unavailable), color = MaterialTheme.colorScheme.error)
                    }
                    listOf(false, true).forEach { browserMusic ->
                        OutlinedButton(
                            onClick = {
                                browserUnavailable = !YouTubeBrowserAccount.open(context, browserMusic)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(if (browserMusic) I18nR.string.telos_youtube_browser_music else I18nR.string.telos_youtube_browser_video))
                        }
                    }

                    TextButton(onClick = {
                        val cookies = android.webkit.CookieManager.getInstance()
                        listOf("https://youtube.com", "https://www.youtube.com", "https://music.youtube.com").forEach { origin ->
                            cookies.getCookie(origin).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotBlank() }.forEach { name ->
                                cookies.setCookie(origin, "$name=; Max-Age=0; Path=/; Secure")
                                cookies.setCookie(origin, "$name=; Max-Age=0; Path=/; Secure; Domain=.youtube.com")
                            }
                        }
                        scope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                de.mm20.launcher2.comms.youtube.YouTubeAccountManager.clear(context.applicationContext)
                                cookies.flush()
                            }
                            vm.userPlaylists.value = emptyList()
                            vm.selectedPlaylistTracks.value = null
                            vm.sessionExpired.value = false
                            vm.results.value = emptyMap()
                            vm.loadHome(context, music, refresh = true)
                        }
                    }) { Text(stringResource(I18nR.string.hc_sign_out)) }
                    HorizontalDivider()
                    Text("${stringResource(I18nR.string.action_import)} · ${stringResource(I18nR.string.telos_youtube_tab_playlists)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = importUrl,
                        onValueChange = { importUrl = it },
                        label = { Text(stringResource(I18nR.string.dl_detail_url)) },
                        placeholder = { Text("https://www.youtube.com/playlist?list=...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = importUrl.contains("list=") && !importing,
                        onClick = {
                            importing = true
                            vm.importPlaylistUrl(importUrl) { ok ->
                                importing = false
                                if (ok) {
                                    android.widget.Toast.makeText(context, context.getString(I18nR.string.hc_done), android.widget.Toast.LENGTH_SHORT).show()
                                    accountPanel = false
                                } else {
                                    android.widget.Toast.makeText(context, context.getString(I18nR.string.hc_failed_try_again), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (importing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(I18nR.string.action_import))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { accountPanel = false }) {
                    Text(stringResource(I18nR.string.telos_youtube_browser_close))
                }
            }
        )
    }
}

@Composable
internal fun FlowMediaCard(
    entry: de.mm20.launcher2.comms.youtube.YouTubeEntry,
    music: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onAddPlaylist: () -> Unit,
    onRadio: () -> Unit,
    onMoodRadio: (String) -> Unit,
    onDeleteFromPlaylist: () -> Unit,
    canRemoveFromPlaylist: Boolean = true,
) {
    var menuOpen by remember(entry.url) { mutableStateOf(false) }

    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(if (music) 12.dp else 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (!music) {
                AsyncImage(model = entry.thumbnail, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
            }
            Row(Modifier.fillMaxWidth().padding(if (music) 10.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (music) AsyncImage(model = entry.thumbnail, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.weight(1f).padding(horizontal = if (music) 12.dp else 0.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(entry.artist, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painterResource(BaseR.drawable.more_vert_24px),
                        contentDescription = stringResource(I18nR.string.au10_music_more)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isFavorite) I18nR.string.hf_files_remove_favorite else I18nR.string.hc_add_to_favorites)) },
                        onClick = { onFavorite(); menuOpen = false }
                    )
                    if (music) {
                        DropdownMenuItem(
                            text = { Text(stringResource(I18nR.string.telos_youtube_add_playlist)) },
                            onClick = { onAddPlaylist(); menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(I18nR.string.telos_youtube_artist_radio)) },
                            onClick = { onRadio(); menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Chill Mood Radio") },
                            onClick = { onMoodRadio("Chill"); menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Workout Mood Radio") },
                            onClick = { onMoodRadio("Workout"); menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Party Mood Radio") },
                            onClick = { onMoodRadio("Party"); menuOpen = false }
                        )
                    }
                    if (canRemoveFromPlaylist) DropdownMenuItem(
                        text = { Text(stringResource(I18nR.string.hc_delete)) },
                        onClick = { onDeleteFromPlaylist(); menuOpen = false }
                    )
                }
            }
        }
    }
    }
}

@Composable
fun YouTubePlaylistChipsRow(
    userPlaylists: List<YouTubePlaylistSummary>,
    selectedUrl: String?,
    onSelectPlaylist: (YouTubePlaylistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (userPlaylists.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(I18nR.string.telos_youtube_tab_playlists), style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(userPlaylists, key = { it.id }) { pl ->
                val isSelected = selectedUrl == pl.url
                val title = when (pl.id) {
                    "LM", "LL" -> stringResource(I18nR.string.hc_favorites)
                    else -> pl.title
                }
                val subtitle = when (pl.id) {
                    "LM" -> stringResource(I18nR.string.telos_youtube_music_label)
                    "LL" -> stringResource(I18nR.string.telos_youtube_label)
                    else -> pl.subtitle
                }
                Surface(onClick = { onSelectPlaylist(pl) }, shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.width(160.dp).semantics { selected = isSelected }) {
                    Column(Modifier.padding(8.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                            if (pl.thumbnail != null) AsyncImage(pl.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Icon(painterResource(if (pl.id == "LL") BaseR.drawable.video_library_24px else BaseR.drawable.music_note_24px), null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(40.dp))
                        }
                        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun YouTubeMiniPlayer(vm: YouTubeViewModel, music: Boolean, onOpen: (Boolean) -> Unit) {
    val now by vm.nowPlaying.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()
    val savedMusic by vm.library.lastMusic.collectAsStateWithLifecycle()
    val savedVideo by vm.library.lastVideo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    if (showPlayer) YouTubeMusicPlayerSheet(vm) { showPlayer = false }
    val matches = now != null && (now?.mediaMetadata?.extras?.getBoolean("audioOnly") == true) == music
    val saved = if (music) savedMusic else savedVideo
    val item = if (matches) now!! else saved?.let {
        androidx.media3.common.MediaItem.Builder().setMediaId(it.url).setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist)
                .setArtworkUri(it.thumbnail?.let(android.net.Uri::parse)).setExtras(android.os.Bundle().apply { putBoolean("audioOnly", music) }).build()).build()
    } ?: return

    Surface(
        onClick = { if (matches && music) showPlayer = true else onOpen(item.mediaMetadata.extras?.getBoolean("audioOnly") == true) },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.mediaMetadata.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    item.mediaMetadata.title?.toString().orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    item.mediaMetadata.artist?.toString().orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { if (matches) vm.toggle() else saved?.let { vm.play(context, it, music) } }) {
                Icon(
                    painterResource(if (playing && matches) BaseR.drawable.pause_24px else BaseR.drawable.play_arrow_24px),
                    stringResource(if (playing && matches) I18nR.string.au_music_pause else I18nR.string.hc_play)
                )
            }
            if (matches) {
                IconButton(onClick = vm::stop) {
                    Icon(painterResource(BaseR.drawable.close_24px), stringResource(I18nR.string.hc_stop))
                }
            }
        }
    }
}
