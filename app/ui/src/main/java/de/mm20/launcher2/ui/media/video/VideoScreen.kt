package de.mm20.launcher2.ui.media.video

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import de.mm20.launcher2.comms.media.windowAround
import android.os.Build
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.media.video.EpisodeParser
import de.mm20.launcher2.comms.media.video.ResumeStore
import de.mm20.launcher2.comms.media.video.VideoItem
import de.mm20.launcher2.comms.search.GreekText
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data object VideoRoute : NavKey

/** Raised when the watched marks from Trakt were refreshed, so the rows draw again */
internal val traktVersion = androidx.compose.runtime.mutableIntStateOf(0)

/** Raised when watched marks or the list of videos changed, so the rows draw again */
internal val libraryVersion = androidx.compose.runtime.mutableIntStateOf(0)

internal class VideoActions(val onDelete: (VideoItem) -> Unit, val onShare: (VideoItem) -> Unit)

internal val LocalVideoActions = androidx.compose.runtime.staticCompositionLocalOf<VideoActions?> { null }

internal data class VideoGroup(
    val title: String,
    val subtitle: String,
    val items: List<VideoItem>,
    val series: Boolean = false,
    val year: Int? = null,
)

internal fun metaKey(g: VideoGroup) = (if (g.series) "tv:" else "movie:") + g.title.lowercase() + ":" + g.year

internal fun openPlayer(context: Context, fullList: List<VideoItem>, fullIndex: Int) {
    // a whole library does not fit through an Intent (Binder limit): the player gets the videos around the chosen one
    val (list, index) = fullList.windowAround(fullIndex)
    context.startActivity(
        Intent(context, PlayerChoice.playerClass(context)).apply {
            putStringArrayListExtra(VideoPlayerActivity.EXTRA_URIS, ArrayList(list.map { it.uri.toString() }))
            putStringArrayListExtra(VideoPlayerActivity.EXTRA_TITLES, ArrayList(list.map { it.title }))
            putExtra(VideoPlayerActivity.EXTRA_INDEX, index)
        }
    )
}

@Composable
fun VideoScreen() {
    val viewModel: VideoViewModel = viewModel()
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        if (granted) viewModel.load(context)
    }
    LaunchedEffect(Unit) { if (hasPermission) viewModel.load(context) }
    // back from the player (new watch positions) or from the system settings (permission changed there)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        libraryVersion.intValue++
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (granted != hasPermission) {
            hasPermission = granted
            if (granted) viewModel.load(context)
        }
    }
    // the Trakt login is decrypted with the keystore: not on the main thread
    val traktConnected by produceState(false) {
        value = withContext(Dispatchers.IO) {
            runCatching { de.mm20.launcher2.comms.media.video.trakt.Trakt.login(context).connected }.getOrDefault(false)
        }
    }

    var tab by de.mm20.launcher2.ui.component.rememberPersistentUiState("video.tab", 0)
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var group by remember { mutableStateOf<VideoGroup?>(null) }
    BackHandler(enabled = group != null) { group = null }

    val filtered = remember(items, query) {
        de.mm20.launcher2.comms.search.TelosSearch.filter(items, query) { listOf(it.title, it.fileName, it.folder) }
    }
    val continueWatching = remember(items, libraryVersion.intValue) {
        ResumeStore.continueWatching(context).mapNotNull { uri -> items.firstOrNull { it.uri.toString() == uri } }.take(10)
    }
    // the file names are parsed once per library, not on every letter typed in the search field
    val parsedNames = remember(items) { items.associate { it.id to EpisodeParser.parse(it.fileName, it.locationHint) } }
    val series = remember(filtered, parsedNames) {
        filtered.mapNotNull { item ->
            val parsed = parsedNames[item.id] ?: EpisodeParser.parse(item.fileName, item.locationHint)
            if (parsed.isEpisode) Triple(parsed.title.lowercase(), parsed, item) else null
        }.groupBy { it.first }.values.map { list ->
            val name = list.first().second.title
            val episodes = list.sortedWith(compareBy({ it.second.season }, { it.second.episode })).map { it.third }
            VideoGroup(name, context.getString(I18nR.string.au_video_episodes_count, episodes.size), episodes, series = true)
        }.sortedBy { it.title.lowercase() }
    }
    val movies = remember(filtered, parsedNames) {
        filtered.mapNotNull { item ->
            val parsed = parsedNames[item.id] ?: EpisodeParser.parse(item.fileName, item.locationHint)
            if (parsed.isEpisode || parsed.year == null) null else Triple(parsed.title.lowercase() + "|" + parsed.year, parsed, item)
        }.groupBy { it.first }.values.map { list ->
            val parsed = list.first().second
            VideoGroup(parsed.title, parsed.year?.toString() ?: context.getString(I18nR.string.au_video_files_count, list.size), list.map { it.third }, series = false, year = parsed.year)
        }.sortedBy { it.title.lowercase() }
    }
    // neither an episode nor a film with a year in its name: not recognised
    val others = remember(filtered, parsedNames) {
        filtered.filter { item ->
            val parsed = parsedNames[item.id] ?: EpisodeParser.parse(item.fileName, item.locationHint)
            !parsed.isEpisode && parsed.year == null
        }
    }
    var showOrganize by remember { mutableStateOf(false) }
    if (showOrganize) VideoOrganizeDialog(items) { showOrganize = false; if (it) viewModel.load(context) }
    LaunchedEffect(Unit) {
        de.mm20.launcher2.comms.media.video.trakt.Trakt.refreshWatched(context)
        runCatching { de.mm20.launcher2.comms.media.video.trakt.Trakt.flushQueue(context) }
        traktVersion.intValue++
    }
    var pendingDelete by remember { mutableStateOf<VideoItem?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingDelete?.let { ResumeStore.markUnwatched(context, it.uri) }
            viewModel.load(context)
        }
        pendingDelete = null
    }
    var confirmDelete by remember { mutableStateOf<VideoItem?>(null) }
    fun performDelete(video: VideoItem) {
        pendingDelete = video
        runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                val request = android.provider.MediaStore.createDeleteRequest(context.contentResolver, listOf(video.uri))
                deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                try {
                    context.contentResolver.delete(video.uri, null, null)
                    ResumeStore.markUnwatched(context, video.uri)
                    viewModel.load(context)
                } catch (e: SecurityException) {
                    val action = if (Build.VERSION.SDK_INT >= 29) {
                        (e as? android.app.RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                    } else null
                    if (action != null) deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(action).build()) else throw e
                }
            }
        }.onFailure { toast(context, context.getString(I18nR.string.vn_delete_failed)) }
    }
    var confirmShare by remember { mutableStateOf<VideoItem?>(null) }
    val shareScope = androidx.compose.runtime.rememberCoroutineScope()
    fun performShare(video: VideoItem) {
        val uri = video.uri
        when (uri.scheme?.lowercase()) {
            "http", "https", "magnet" -> {
                if (!de.mm20.launcher2.ui.common.share.ShareActions.shareText(context, uri.toString(), video.title)) {
                    toast(context, context.getString(I18nR.string.au10_video_share_failed))
                }
            }
            // a video on a network storage: its address is private, so only a public link could be shared
            "rem" -> toast(context, context.getString(I18nR.string.au10_video_share_remote_private))
            else -> shareScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    val name = video.fileName.ifBlank { video.title }
                    val mime = de.mm20.launcher2.ui.common.share.ShareActions.mimeOf(name).takeIf { it.startsWith("video/") } ?: "video/*"
                    if (uri.scheme == "file") {
                        de.mm20.launcher2.ui.common.share.ShareActions.shareFile(context, java.io.File(uri.path.orEmpty()), mime, name)
                    } else {
                        de.mm20.launcher2.ui.common.share.ShareActions.shareFile(context, uri, mime, name)
                    }
                }
                if (!ok) toast(context, context.getString(I18nR.string.au10_video_share_failed))
            }
        }
    }
    val videoActions = remember {
        VideoActions(
            onDelete = { confirmDelete = it },
            // big local videos are copied into the cache first: ask before
            onShare = { v ->
                if (v.sizeBytes > 50L * 1024 * 1024 && v.uri.scheme?.lowercase() in listOf("content", "file")) confirmShare = v
                else performShare(v)
            },
        )
    }
    confirmShare?.let { video ->
        AlertDialog(
            onDismissRequest = { confirmShare = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au10_video_share_large_title))  } },
            text = { Text(stringResource(I18nR.string.au10_video_share_large_message, de.mm20.launcher2.ui.files.formatSize(video.sizeBytes))) },
            confirmButton = { TextButton(onClick = { confirmShare = null; performShare(video) }) { Text(stringResource(I18nR.string.au10_video_share_large_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmShare = null }) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
    }
    confirmDelete?.let { video ->
        de.mm20.launcher2.ui.trash.UriDeleteDialog(video.uri,
            onDeleted = { ResumeStore.markUnwatched(context, video.uri); viewModel.load(context) },
            onDismiss = { confirmDelete = null },
            onPermanent = { performDelete(video) })
    }
    var showOpen by remember { mutableStateOf(false) }
    var showServices by remember { mutableStateOf(false) }
    var servicesVersion by remember { mutableStateOf(0) }
    val services by produceState<de.mm20.launcher2.comms.media.video.VideoServicesConfig?>(null, servicesVersion) {
        value = de.mm20.launcher2.comms.media.video.VideoServices.config()
    }
    val metas = remember { mutableStateMapOf<String, de.mm20.launcher2.comms.media.video.VideoMeta?>() }
    LaunchedEffect(services, series, movies) {
        val cfg = services ?: return@LaunchedEffect
        if (!cfg.postersEnabled) return@LaunchedEffect
        val language = cfg.languages.substringBefore(',').ifBlank { "en" }
        // the cache is a JSON file that is read for every title: off the main thread
        withContext(Dispatchers.IO) {
            for (g in series + movies) {
                val k = metaKey(g)
                if (metas.containsKey(k)) continue
                val vm = de.mm20.launcher2.comms.media.video.VideoMetadata
                val tmdb = cfg.tmdbKey.isNotBlank()
                if (vm.known(context, g.series, g.title, g.year, tmdb)) {
                    metas[k] = vm.cached(context, g.series, g.title, g.year, tmdb)
                    continue
                }
                metas[k] = vm.lookup(context, cfg.tmdbKey, g.series, g.title, g.year, language)
                kotlinx.coroutines.delay(150)
            }
        }
    }
    val otherFolder = stringResource(I18nR.string.au_video_other_folder)
    val folders = remember(filtered, otherFolder) {
        filtered.groupBy { it.folder.ifBlank { otherFolder } }
            .map { (name, list) -> VideoGroup(name, context.getString(I18nR.string.au_video_videos_count, list.size), list.sortedBy { it.title.lowercase() }) }
            .sortedBy { it.title.lowercase() }
    }

    var showLibraryActions by remember { mutableStateOf(false) }
    de.mm20.launcher2.ui.media.MediaFrame(stringResource(I18nR.string.au_video_title), guardKey = "telos_video_app://video", actions = {
        if (hasPermission && group == null) de.mm20.launcher2.ui.media.MediaSearchAction(searchExpanded, query.isNotBlank()) {
            if (searchExpanded) query = ""
            searchExpanded = !searchExpanded
        }
        Box {
            IconButton(onClick = { showLibraryActions = true }) {
                Icon(painterResource(BaseR.drawable.more_vert_24px), stringResource(I18nR.string.hc_more))
            }
            DropdownMenu(expanded = showLibraryActions, onDismissRequest = { showLibraryActions = false }) {
                DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_play_from_the_web)) },
                    leadingIcon = { Icon(painterResource(BaseR.drawable.link_24px), null) },
                    onClick = { showLibraryActions = false; showOpen = true })
                DropdownMenuItem(text = { Text(stringResource(I18nR.string.au7_vidfolders_action)) },
                    leadingIcon = { Icon(painterResource(BaseR.drawable.folder_24px), null) },
                    onClick = { showLibraryActions = false; showOrganize = true })
                DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_video_services)) },
                    leadingIcon = { Icon(painterResource(BaseR.drawable.settings_24px), null) },
                    onClick = { showLibraryActions = false; showServices = true })
            }
        }
    }) {
    if (showOpen) OpenSourceDialog { showOpen = false }
    if (showServices) VideoServicesDialog { showServices = false; servicesVersion++; viewModel.load(context) }
    androidx.compose.runtime.CompositionLocalProvider(LocalVideoActions provides videoActions) {
    Column(Modifier.fillMaxSize()) {
        if (!hasPermission) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(I18nR.string.hc_allow_access_to_your_videos_to_build_the), style = MaterialTheme.typography.titleMedium)
                Button(onClick = { permissionLauncher.launch(permission) }, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(I18nR.string.hc_allow))
                }
                if (permissionDenied) {
                    // after two refusals the system no longer shows its dialog: the switch is in the app settings
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", context.packageName, null),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text(stringResource(I18nR.string.au_video_open_settings)) }
                }
            }
            return@Column
        }

        val current = group
        // inside a series or folder the search field would have no effect
        if (current == null) {
            de.mm20.launcher2.ui.media.MediaSearchBar(query, { query = it }, stringResource(I18nR.string.tsm_search_videos),
                    expanded = searchExpanded, onClose = { query = ""; searchExpanded = false })
        }
        if (current != null) {
            // videos deleted in the meantime drop out of the open group
            val present = remember(items) { items.mapTo(HashSet()) { it.id } }
            val shown = remember(current, present) { current.items.filter { it.id in present } }
            LaunchedEffect(shown.isEmpty()) { if (shown.isEmpty()) group = null }
            val meta = metas[metaKey(current)]
            DetailPage(current, shown, meta, traktConnected, onBack = { group = null }) { index -> openPlayer(context, shown, index) }
            return@Column
        }

        PillTabs(
            titles = listOf(I18nR.string.au_video_tab_library, I18nR.string.au_video_tab_movies, I18nR.string.au_video_tab_series, I18nR.string.au7_vidfolders_tab_other, I18nR.string.au_video_tab_folders).map { stringResource(it) },
            selected = tab,
            onSelect = { tab = it },
        )
        when {
            loading -> VideoSkeleton()
            items.isEmpty() -> VideoEmptyState(BaseR.drawable.movie_24px, stringResource(I18nR.string.au14_videoui_empty_title), stringResource(I18nR.string.hc_no_videos_found_on_this_device))
            filtered.isEmpty() && query.isNotBlank() -> de.mm20.launcher2.ui.component.SearchEmptyState(query)
            tab == 0 && query.isBlank() -> TintedHeader(null, Modifier.fillMaxSize()) {
                VideoHome(items, continueWatching, movies, series, others, folders, metas, showContinue = true) { group = it }
            }
            tab == 0 -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.uri.toString() }) { video ->
                    VideoRow(video) { openPlayer(context, filtered, filtered.indexOf(video)) }
                }
            }
            tab == 1 -> PosterGrid(movies, metas, stringResource(I18nR.string.au_video_no_movies)) { group = it }
            tab == 2 -> PosterGrid(series, metas, stringResource(I18nR.string.au_video_no_series)) { group = it }
            tab == 3 -> if (others.isEmpty()) {
                VideoEmptyState(BaseR.drawable.folder_24px, stringResource(I18nR.string.au7_vidfolders_no_other), "")
            } else VideoList(others) { index -> openPlayer(context, others, index) }
            else -> GroupList(folders, "") { group = it }
        }
    }
    }
    }
}

/** The page of a movie, a series or a folder: backdrop header, actions, description, seasons and episodes */
@Composable
private fun DetailPage(
    group: VideoGroup,
    shown: List<VideoItem>,
    meta: de.mm20.launcher2.comms.media.video.VideoMeta?,
    traktConnected: Boolean,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val tint by produceState<androidx.compose.ui.graphics.Color?>(null, meta?.posterUrl) {
        value = meta?.posterUrl?.let { PosterTint.of(context, it) }
    }
    val tintColor by androidx.compose.animation.animateColorAsState(
        tint ?: MaterialTheme.colorScheme.primary,
        if (rememberReduceAnimations()) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(500),
        label = "detailTint",
    )
    val surface = MaterialTheme.colorScheme.surface
    val parsed = remember(shown) { shown.map { EpisodeParser.parse(it.fileName, it.locationHint) } }
    val seasons = remember(parsed) { if (group.series) parsed.mapNotNull { it.season }.distinct().sorted() else emptyList() }
    var seasonIndex by rememberSaveable(group.title) { mutableStateOf(0) }
    val season = seasons.getOrNull(seasonIndex.coerceIn(0, (seasons.size - 1).coerceAtLeast(0)))
    var expanded by rememberSaveable(group.title) { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    val title = meta?.title?.ifBlank { null } ?: group.title
    val shape = RoundedCornerShape(20.dp)

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                val blurMod = if (Build.VERSION.SDK_INT >= 31) Modifier.blur(18.dp) else Modifier
                if (meta?.posterUrl != null) Poster(meta.posterUrl, Modifier.matchParentSize().then(blurMod))
                else VideoThumb(shown.first(), Modifier.matchParentSize().then(blurMod))
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f),
                            0.55f to tintColor.copy(alpha = 0.55f),
                            1f to surface,
                        )
                    )
                )
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)),
                ) { Icon(painterResource(BaseR.drawable.arrow_back_24px), contentDescription = stringResource(I18nR.string.hc_back)) }
                Row(Modifier.align(Alignment.BottomStart).padding(20.dp, 0.dp, 20.dp, 12.dp), verticalAlignment = Alignment.Bottom) {
                    if (meta?.posterUrl != null) {
                        Poster(meta.posterUrl, Modifier.width(112.dp).aspectRatio(2f / 3f).shadow(8.dp, shape).clip(shape))
                        Spacer(Modifier.width(16.dp))
                    }
                    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
        item(key = "chips") {
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meta?.year?.ifBlank { null }?.let { MetaChip(it) }
                meta?.rating?.takeIf { it > 0 }?.let { MetaChip("★ %.1f".format(it)) }
                if (group.series) {
                    if (seasons.isNotEmpty()) MetaChip(stringResource(I18nR.string.au14_videoui_seasons, seasons.size))
                    MetaChip(group.subtitle)
                } else if (shown.size == 1 && shown[0].durationMs > 0) {
                    MetaChip(stringResource(I18nR.string.au14_videoui_minutes, (shown[0].durationMs / 60000).toInt().coerceAtLeast(1)))
                } else MetaChip(group.subtitle)
            }
        }
        item(key = "actions") {
            Row(Modifier.padding(20.dp, 16.dp, 20.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val next = shown.indexOfFirst { !ResumeStore.isWatched(context, it.uri) }.coerceAtLeast(0)
                        onPlay(next)
                    },
                    contentPadding = PaddingValues(start = 18.dp, end = 24.dp),
                ) {
                    Icon(painterResource(BaseR.drawable.play_arrow_24px), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(I18nR.string.hc_play))
                }
                if (traktConnected) {
                    FilledTonalButton(onClick = {
                        scope.launch {
                            val ok = de.mm20.launcher2.comms.media.video.trakt.Trakt.addToWatchlist(context, group.title, group.year, group.series)
                            toast(context, context.getString(if (ok) I18nR.string.au_video_watchlist_added else I18nR.string.au_video_watchlist_failed))
                        }
                    }) {
                        Icon(painterResource(BaseR.drawable.add_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(I18nR.string.hc_add_to_trakt_watchlist), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (!meta?.overview.isNullOrBlank()) {
            item(key = "overview") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        meta!!.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow },
                        modifier = Modifier.animateContentSize(),
                    )
                    if (overflow || expanded) {
                        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(if (expanded) I18nR.string.au14_videoui_less else I18nR.string.au14_videoui_more))
                        }
                    }
                }
            }
        }
        if (seasons.size > 1) {
            item(key = "seasons") {
                PillTabs(seasons.map { stringResource(I18nR.string.au7_vidfolders_season, it) }, seasonIndex.coerceIn(0, seasons.size - 1), { seasonIndex = it })
            }
        }
        val indices = shown.indices.filter { !group.series || seasons.size <= 1 || parsed[it].season == season }
        items(indices, key = { shown[it].uri.toString() }) { index ->
            VideoRow(shown[index], number = if (group.series) parsed[index].episode else null) { onPlay(index) }
        }
    }
}

@Composable
internal fun GroupList(groups: List<VideoGroup>, emptyText: String, onOpen: (VideoGroup) -> Unit) {
    if (groups.isEmpty()) {
        VideoEmptyState(BaseR.drawable.folder_24px, emptyText, "")
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        items(groups.size) { index ->
            val g = groups[index]
            val shape = RoundedCornerShape(20.dp)
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow).pressScale({ onOpen(g) }).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoThumb(g.items.first(), Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(g.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(g.subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun VideoList(list: List<VideoItem>, onPlay: (Int) -> Unit) {
    if (list.isEmpty()) {
        VideoEmptyState(BaseR.drawable.movie_24px, stringResource(I18nR.string.au10_video_nothing_here), "")
        return
    }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(320.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        items(list.size, key = { list[it].uri.toString() }) { index -> VideoRow(list[index], tile = true) { onPlay(index) } }
    }
}

/** A rounded card row: 16:9 thumbnail with progress, title, duration; long press opens the menu */
@Composable
internal fun VideoRow(video: VideoItem, number: Int? = null, tile: Boolean = false, onClick: () -> Unit) {
    val context = LocalContext.current
    val progress = remember(video.uri, libraryVersion.intValue) { ResumeStore.progress(context, video.uri) }
    var menu by remember { mutableStateOf(false) }
    val seen = remember(video.uri, libraryVersion.intValue) { ResumeStore.isWatched(context, video.uri) }
    val watched = seen || remember(video.uri, traktVersion.intValue) {
        de.mm20.launcher2.comms.media.video.trakt.Trakt.isWatched(context, EpisodeParser.parse(video.fileName, video.locationHint))
    }
    if (tile) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).pressScale(onClick) { menu = true }) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                VideoThumb(video, Modifier.fillMaxSize())
                if (progress > 0.02f) LinearProgressIndicator(progress = { progress.coerceAtMost(1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp))
                if (watched) WatchedBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(formatDuration(video.durationMs) + " · " + video.folder,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(BaseR.drawable.more_vert_24px), stringResource(I18nR.string.au10_music_more))
                }
            }
            VideoMenu(video, menu, seen) { menu = false }
        }
        return
    }
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .pressScale(onClick) { menu = true }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))) {
            VideoThumb(video, Modifier.fillMaxSize())
            if (number != null) ImageChip(number.toString(), Modifier.align(Alignment.TopStart).padding(6.dp))
            if (progress > 0.02f) {
                LinearProgressIndicator(
                    progress = { progress.coerceAtMost(1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            VideoMenu(video, menu, seen) { menu = false }
            Text(
                formatDuration(video.durationMs) + " · " + video.folder.ifBlank { stringResource(I18nR.string.au_video_other_folder) },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (watched) WatchedBadge(Modifier.padding(start = 8.dp))
    }
}

/** The long-press menu of a video: watched mark, share, delete */
@Composable
internal fun VideoMenu(video: VideoItem, expanded: Boolean, seen: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val actions = LocalVideoActions.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(20.dp)) {
        DropdownMenuItem(
            text = { Text(stringResource(if (seen) I18nR.string.vn_mark_unwatched else I18nR.string.vn_mark_watched)) },
            onClick = {
                onDismiss()
                if (seen) ResumeStore.markUnwatched(context, video.uri) else ResumeStore.markWatched(context, video.uri, video.durationMs)
                libraryVersion.intValue++
            },
        )
        de.mm20.launcher2.ui.common.share.ShareMenuItem(onClick = { onDismiss(); actions?.onShare?.invoke(video) })
        if (video.id > 0) {
            DropdownMenuItem(
                text = { Text(stringResource(I18nR.string.vn_delete_video)) },
                onClick = { onDismiss(); actions?.onDelete?.invoke(video) },
            )
        }
    }
}

@Composable
internal fun VideoThumb(video: VideoItem, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, video.uri) {
        value = null
        value = de.mm20.launcher2.ui.media.docs.ViewerThumbnails.load(context.applicationContext, video.uri, video.fileName, video.durationMs)
    }
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b == null) {
            Icon(painterResource(BaseR.drawable.play_circle_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        } else {
            Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
internal fun Poster(url: String?, modifier: Modifier, fallback: (@Composable () -> Unit)? = null) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (url == null || failed) {
            if (fallback != null) fallback() else Icon(painterResource(BaseR.drawable.movie_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        } else {
            coil.compose.AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), onError = { failed = true })
        }
    }
}

@Composable
internal fun PosterGrid(
    groups: List<VideoGroup>,
    metas: Map<String, de.mm20.launcher2.comms.media.video.VideoMeta?>,
    emptyText: String,
    onOpen: (VideoGroup) -> Unit,
) {
    if (groups.isEmpty()) {
        VideoEmptyState(BaseR.drawable.movie_24px, emptyText, "")
        return
    }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(116.dp),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(groups.size, key = { metaKey(groups[it]) + "#" + it }) { i ->
            PosterCard(groups[i], metas[metaKey(groups[i])], Modifier, onOpen)
        }
    }
}
