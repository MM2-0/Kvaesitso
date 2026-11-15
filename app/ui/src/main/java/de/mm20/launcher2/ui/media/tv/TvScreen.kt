package de.mm20.launcher2.ui.media.tv

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.comms.tv.TvChannel
import de.mm20.launcher2.comms.tv.TvIndex
import de.mm20.launcher2.comms.tv.TvSaveResult
import de.mm20.launcher2.preferences.ui.PerformanceSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.share.ShareActions
import de.mm20.launcher2.ui.media.MediaFrame
import de.mm20.launcher2.ui.media.MediaSearchBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.Locale

private class EditTarget(val channel: TvChannel?)

/** Telos TV: live channels of the iptv-org catalog and the user's own channels. Lives inside Telos Media. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvScreen() {
    val vm: TvViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val performanceSettings = koinInject<PerformanceSettings>()
    val reduceAnimations by performanceSettings.reduceAnimations.collectAsState(false)

    val disclaimer by vm.disclaimerShown.collectAsStateWithLifecycle()
    val load by vm.load.collectAsStateWithLifecycle()
    val disclaimerGreece by vm.disclaimerGreece.collectAsStateWithLifecycle()
    val playerOpen by vm.playerOpen.collectAsStateWithLifecycle()
    val current by vm.controller.currentChannel.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()

    // nothing is downloaded before the disclaimer was accepted
    LaunchedEffect(disclaimer) { if (disclaimer) vm.load() }
    LaunchedEffect(current, playerOpen) { if (current == null && playerOpen) vm.minimizePlayer() }

    var edit by remember { mutableStateOf<EditTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<TvChannel?>(null) }
    var showCountries by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showPlayback by rememberSaveable { mutableStateOf(false) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    val importM3uLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = vm.importM3u(uri)
            when {
                r == null -> toast(context.getString(I18nR.string.au12_tvui_import_failed))
                r.tooLarge -> toast(context.getString(I18nR.string.au11_tvdata_import_too_large))
                else -> {
                    val base = context.getString(I18nR.string.au11_tvdata_import_result, r.added, r.skipped)
                    toast(if (r.truncated) base + "\n" + context.getString(I18nR.string.au11_tvdata_import_truncated) else base)
                }
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            toast(context.getString(if (vm.exportBackup(uri)) I18nR.string.au_radio_backup_saved else I18nR.string.au_radio_backup_save_failed))
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = vm.restoreBackup(uri)
            toast(if (n >= 0) context.getString(I18nR.string.au12_tvui_restored, n) else context.getString(I18nR.string.au_radio_restore_failed))
        }
    }

    MediaFrame(
        title = stringResource(I18nR.string.au12_tvui_title),
        actions = {
            if (disclaimer) {
                if (load == TvLoad.Ready) {
                    IconButton(onClick = { edit = EditTarget(null) }) {
                        Icon(painterResource(BaseR.drawable.add_24px), contentDescription = stringResource(I18nR.string.au12_tvui_add_channel))
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(painterResource(BaseR.drawable.autorenew_24px), contentDescription = stringResource(I18nR.string.hc_refresh))
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(BaseR.drawable.more_vert_24px), contentDescription = stringResource(I18nR.string.hc_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(I18nR.string.au15_tvbg_menu_playback)) },
                            onClick = { menuOpen = false; showPlayback = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(I18nR.string.au12_tvui_import_m3u)) },
                            onClick = {
                                menuOpen = false
                                importM3uLauncher.launch(
                                    arrayOf("text/*", "audio/x-mpegurl", "application/vnd.apple.mpegurl", "application/octet-stream")
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(I18nR.string.au12_tvui_export_tv)) },
                            onClick = { menuOpen = false; exportLauncher.launch("telos-tv-backup.json") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(I18nR.string.au12_tvui_import_tv)) },
                            onClick = { menuOpen = false; restoreLauncher.launch(arrayOf("*/*")) },
                        )
                    }
                }
            }
        },
    ) {
        when {
            !disclaimer -> DisclaimerCard(onAccept = { vm.accept() }, greece = disclaimerGreece)
            load == TvLoad.Ready -> TvHomeContent(
                vm = vm,
                reduceAnimations = reduceAnimations,
                onEdit = { edit = EditTarget(it) },
                onDelete = { deleteTarget = it },
                onShowCountries = { showCountries = true },
            )
            load == TvLoad.Failed -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(I18nR.string.au12_tvui_load_failed), textAlign = TextAlign.Center)
                Button(onClick = { vm.retryLoad() }, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(I18nR.string.hc_retry))
                }
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TvSkeleton(reduceAnimations)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(I18nR.string.au12_tvui_loading), modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }

    if (showCountries) {
        TvCountrySheet(vm, onDismiss = { showCountries = false })
    }
    if (showPlayback) {
        TvPlaybackSheet(vm, onDismiss = { showPlayback = false })
    }
    edit?.let { target ->
        TvChannelDialog(vm, target.channel, onDismiss = { edit = null })
    }
    deleteTarget?.let { ch ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { TelosCyberHeading {  Text(ch.name, maxLines = 2, overflow = TextOverflow.Ellipsis)  } },
            confirmButton = {
                TextButton(onClick = { vm.deleteCustom(ch.id); deleteTarget = null }) { Text(stringResource(I18nR.string.hc_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(I18nR.string.hc_cancel)) }
            },
        )
    }
    if (playerOpen && current != null) {
        TvPlayerDialog(vm, favoriteIds, reduceAnimations, onMinimize = { vm.minimizePlayer() })
    }
}

@Composable
private fun DisclaimerCard(onAccept: () -> Unit, greece: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(I18nR.string.au12_tvui_disclaimer_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(I18nR.string.au12_tvui_disclaimer_text), style = MaterialTheme.typography.bodyMedium)
                if (greece) {
                    Text(stringResource(I18nR.string.au13_tvextraui_disclaimer_extra), style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = onAccept, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(I18nR.string.au12_tvui_accept))
                }
            }
        }
    }
}

/**
 * A key that changes when the programme guide changed, when it was switched, when Greece was (de)selected and
 * every minute while the screen is at least started. [refresh] also asks for a (silent, rate limited) guide
 * refresh each time the screen starts or resumes.
 */
@Composable
fun rememberTvEpgKey(vm: TvViewModel, refresh: Boolean): Any {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val version by vm.epgVersion.collectAsStateWithLifecycle()
    val enabled by vm.epgEnabled.collectAsStateWithLifecycle()
    val greece by vm.greeceSelected.collectAsStateWithLifecycle()
    var minute by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (refresh) vm.refreshEpg()
            minute++
            while (true) {
                delay(60_000)
                minute++
            }
        }
    }
    return remember(version, enabled, greece, minute) { Any() }
}

@Composable
private fun TvHomeContent(
    vm: TvViewModel,
    reduceAnimations: Boolean,
    onEdit: (TvChannel) -> Unit,
    onDelete: (TvChannel) -> Unit,
    onShowCountries: () -> Unit,
) {
    val key = rememberTvEpgKey(vm, refresh = true)
    val playerOpen by vm.playerOpen.collectAsStateWithLifecycle()
    val bad = remember(key, playerOpen) { vm.badStreamUrls() }
    val info = remember(key, bad) { TvCardInfo(key, { vm.nowNext(it) }, bad) }
    val context = LocalContext.current
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    // last used view; null = never chosen (Favorites when there is at least one favorite, otherwise Browse)
    var stored by remember { mutableStateOf(readTvView(context)) }
    val showFavorites = stored?.let { it == VIEW_FAVORITES } ?: favorites.isNotEmpty()
    CompositionLocalProvider(LocalTvCardInfo provides info) {
        Column(Modifier.fillMaxSize()) {
            TvSegmentedControl(
                selected = if (showFavorites) 0 else 1,
                labels = listOf(
                    stringResource(I18nR.string.au14_tvhome_view_favorites),
                    stringResource(I18nR.string.au14_tvhome_view_browse),
                ),
                icons = listOf(BaseR.drawable.star_24px_filled, BaseR.drawable.travel_explore_24px),
                reduceAnimations = reduceAnimations,
                onSelect = {
                    val v = if (it == 0) VIEW_FAVORITES else VIEW_BROWSE
                    stored = v
                    saveTvView(context, v)
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { contentDescription = context.getString(I18nR.string.au14_tvhome_views_description) },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (showFavorites) {
                    TvFavoritesBody(vm, reduceAnimations, onEdit, onDelete) {
                        stored = VIEW_BROWSE
                        saveTvView(context, VIEW_BROWSE)
                    }
                } else {
                    TvHomeBody(vm, reduceAnimations, onEdit, onDelete, onShowCountries)
                }
            }
        }
    }
}

private const val VIEW_FAVORITES = "favorites"
private const val VIEW_BROWSE = "browse"

private fun readTvView(context: android.content.Context): String? = try {
    context.getSharedPreferences("telos_tv_ui", android.content.Context.MODE_PRIVATE)
        .getString("view", null)?.takeIf { it == VIEW_FAVORITES || it == VIEW_BROWSE }
} catch (e: Exception) { null }

private fun saveTvView(context: android.content.Context, view: String) {
    try {
        context.getSharedPreferences("telos_tv_ui", android.content.Context.MODE_PRIVATE).edit().putString("view", view).apply()
    } catch (_: Exception) {
    }
}

/** Favorites first: hero "Continue watching", big favorites grid, then Recently watched and Your channels */
@Composable
private fun TvFavoritesBody(
    vm: TvViewModel,
    reduceAnimations: Boolean,
    onEdit: (TvChannel) -> Unit,
    onDelete: (TvChannel) -> Unit,
    onBrowse: () -> Unit,
) {
    val context = LocalContext.current
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val custom by vm.customChannels.collectAsStateWithLifecycle()
    val playingChannel by vm.controller.currentChannel.collectAsStateWithLifecycle()
    val isPlaying by vm.controller.isPlaying.collectAsStateWithLifecycle()
    val playingId = if (isPlaying) playingChannel?.id else null
    val info = LocalTvCardInfo.current

    fun actionsFor(ch: TvChannel, moveBefore: (() -> Unit)? = null, moveAfter: (() -> Unit)? = null) = TvCardActions(
        onFavorite = { vm.setFavorite(ch.id, it) },
        onShare = { ShareActions.shareText(context, shareText(ch)) },
        onEdit = if (ch.isCustom) ({ onEdit(ch) }) else null,
        onDelete = if (ch.isCustom) ({ onDelete(ch) }) else null,
        onMoveBefore = moveBefore,
        onMoveAfter = moveAfter,
    )

    val hero = recents.firstOrNull() ?: favorites.firstOrNull()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(224.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (favorites.isEmpty()) {
            item(key = "empty:fav", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    TvEmptyState(
                        BaseR.drawable.star_24px_outlined,
                        stringResource(I18nR.string.au14_tvhome_empty_favorites_title),
                        stringResource(I18nR.string.au14_tvhome_empty_favorites_text),
                    )
                    Button(onClick = onBrowse) { Text(stringResource(I18nR.string.au14_tvhome_view_browse)) }
                }
            }
        }
        if (hero != null) {
            item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                val now = remember(info.key, hero.id) { info.nowNext(hero.id)?.current?.title }
                TvHeroCard(
                    hero, now, reduceAnimations,
                    onPlay = { vm.play(hero, if (hero.id in favoriteIds) favorites else recents) },
                )
            }
        }
        if (favorites.isNotEmpty()) {
            header("h:fav", { stringResource(I18nR.string.au12_tvui_shelf_favorites) })
            itemsIndexed(favorites, key = { _, c -> "f:" + c.id }) { i, ch ->
                TvChannelCard(
                    ch, playing = ch.id == playingId, favorite = true,
                    reduceAnimations = reduceAnimations,
                    actions = actionsFor(
                        ch,
                        moveBefore = if (i > 0) ({ vm.moveFavorite(ch.id, i - 1) }) else null,
                        moveAfter = if (i < favorites.lastIndex) ({ vm.moveFavorite(ch.id, i + 1) }) else null,
                    ),
                    onClick = { vm.play(ch, favorites) },
                    logoHeight = 96.dp,
                )
            }
        }
        if (recents.isNotEmpty()) {
            header("h:rec", { stringResource(I18nR.string.au12_tvui_shelf_recents) })
            item(key = "s:rec", span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(recents.distinctBy { it.id }, key = { it.id }) { ch ->
                        TvChannelCard(
                            ch, playing = ch.id == playingId, favorite = ch.id in favoriteIds,
                            reduceAnimations = reduceAnimations, actions = actionsFor(ch),
                            onClick = { vm.play(ch, recents) },
                            modifier = Modifier.width(120.dp),
                        )
                    }
                }
            }
        }
        if (custom.isNotEmpty()) {
            header("h:cus", { stringResource(I18nR.string.au12_tvui_shelf_custom) })
            item(key = "s:cus", span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(custom.distinctBy { it.id }, key = { it.id }) { ch ->
                        TvChannelCard(
                            ch, playing = ch.id == playingId, favorite = ch.id in favoriteIds,
                            reduceAnimations = reduceAnimations, actions = actionsFor(ch),
                            onClick = { vm.play(ch, custom) },
                            modifier = Modifier.width(120.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvHomeBody(
    vm: TvViewModel,
    reduceAnimations: Boolean,
    onEdit: (TvChannel) -> Unit,
    onDelete: (TvChannel) -> Unit,
    onShowCountries: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val home by vm.home.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val custom by vm.customChannels.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val countries by vm.selectedCountries.collectAsStateWithLifecycle()
    val status by vm.refreshStatus.collectAsStateWithLifecycle()
    val playingChannel by vm.controller.currentChannel.collectAsStateWithLifecycle()
    val isPlaying by vm.controller.isPlaying.collectAsStateWithLifecycle()
    val playingId = if (isPlaying) playingChannel?.id else null

    var topHeight by remember { mutableStateOf(0.dp) }
    val localizedCategories = categories.map { it to tvCategoryName(it.id, it.name) }
        .sortedBy { it.second.lowercase(Locale.getDefault()) }

    fun actionsFor(
        ch: TvChannel,
        moveBefore: (() -> Unit)? = null,
        moveAfter: (() -> Unit)? = null,
    ) = TvCardActions(
        onFavorite = { vm.setFavorite(ch.id, it) },
        onShare = { ShareActions.shareText(context, shareText(ch)) },
        onEdit = if (ch.isCustom) ({ onEdit(ch) }) else null,
        onDelete = if (ch.isCustom) ({ onDelete(ch) }) else null,
        onMoveBefore = moveBefore,
        onMoveAfter = moveAfter,
    )

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(224.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topHeight, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val results = home.results
            if (results != null) {
                header("h:results", { stringResource(I18nR.string.au12_tvui_shelf_results) })
                if (results.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        TvEmptyState(BaseR.drawable.search_24px, stringResource(I18nR.string.au12_tvui_no_results), null)
                    }
                }
                items(results.distinctBy { it.id }, key = { "r:" + it.id }) { ch ->
                    TvChannelCard(
                        ch, playing = ch.id == playingId, favorite = ch.id in favoriteIds,
                        reduceAnimations = reduceAnimations, actions = actionsFor(ch),
                        onClick = { vm.play(ch, results) },
                    )
                }
            } else {
                if (category == null) {
                    if (favorites.isEmpty()) {
                        item(key = "hint:star", span = { GridItemSpan(maxLineSpan) }) { TvStarHint(Modifier.padding(vertical = 4.dp)) }
                    }
                    if (favorites.isEmpty() && recents.isNotEmpty()) {
                        header("h:rec", { stringResource(I18nR.string.au12_tvui_shelf_recents) })
                        item(key = "s:rec", span = { GridItemSpan(maxLineSpan) }) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(recents.distinctBy { it.id }, key = { it.id }) { ch ->
                                    TvChannelCard(
                                        ch, playing = ch.id == playingId, favorite = ch.id in favoriteIds,
                                        reduceAnimations = reduceAnimations, actions = actionsFor(ch),
                                        onClick = { vm.play(ch, recents) },
                                        modifier = Modifier.width(136.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (favorites.isEmpty() && custom.isNotEmpty()) {
                        header("h:cus", { stringResource(I18nR.string.au12_tvui_shelf_custom) })
                        item(key = "s:cus", span = { GridItemSpan(maxLineSpan) }) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(custom.distinctBy { it.id }, key = { it.id }) { ch ->
                                    TvChannelCard(
                                        ch, playing = ch.id == playingId, favorite = ch.id in favoriteIds,
                                        reduceAnimations = reduceAnimations, actions = actionsFor(ch),
                                        onClick = { vm.play(ch, custom) },
                                        modifier = Modifier.width(136.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                val title: @Composable () -> String = {
                    when {
                        category != null -> categories.firstOrNull { it.id == category }
                            ?.let { tvCategoryName(it.id, it.name) }
                            ?: stringResource(I18nR.string.au12_tvui_shelf_filtered)
                        countries.size == 1 -> stringResource(
                            I18nR.string.au12_tvui_shelf_country, TvIndex.countryName(countries.first(), Locale.getDefault())
                        )
                        countries.isNotEmpty() -> stringResource(I18nR.string.au12_tvui_shelf_filtered)
                        else -> stringResource(I18nR.string.au12_tvui_shelf_suggested)
                    }
                }
                header("h:main", title)
                if (home.channels.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        TvEmptyState(BaseR.drawable.search_24px, stringResource(I18nR.string.au12_tvui_no_results), null)
                    }
                }
                items(home.channels, key = { "m:" + it.id }) { ch ->
                    TvChannelCard(
                        ch, playing = ch.id == playingId, favorite = ch.id in favoriteIds,
                        reduceAnimations = reduceAnimations, actions = actionsFor(ch),
                        onClick = { vm.play(ch, home.channels) },
                    )
                }
            }
        }

        // translucent top: search, chips and the status line
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .onSizeChanged { topHeight = with(density) { it.height.toDp() } }
                .padding(top = 4.dp, bottom = 4.dp),
        ) {
            MediaSearchBar(
                value = query,
                onValueChange = { vm.setQuery(it) },
                placeholder = stringResource(I18nR.string.au12_tvui_search_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "countries") {
                    val flags = countries.take(3).joinToString("") { TvIndex.flagEmoji(it) }
                    AssistChip(
                        onClick = onShowCountries,
                        label = {
                            Text(
                                (if (flags.isNotEmpty()) "$flags " else "") + stringResource(I18nR.string.au12_tvui_countries),
                                maxLines = 1,
                            )
                        },
                    )
                }
                item(key = "cat:all") {
                    FilterChip(
                        selected = category == null,
                        onClick = { vm.setCategory(null) },
                        label = { Text(stringResource(I18nR.string.au12_tvui_chip_all)) },
                    )
                }
                items(localizedCategories, key = { "cat:" + it.first.id }) { (c, label) ->
                    FilterChip(
                        selected = category == c.id,
                        onClick = { vm.setCategory(if (category == c.id) null else c.id) },
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }
            val statusText = when {
                status.refreshing -> stringResource(I18nR.string.au12_tvui_updating)
                status.failed -> stringResource(I18nR.string.au11_tvdata_refresh_failed)
                status.lastCheckedAt == 0L -> stringResource(I18nR.string.au11_tvdata_refresh_never)
                else -> stringResource(
                    I18nR.string.au12_tvui_last_checked,
                    DateUtils.getRelativeTimeSpanString(status.lastCheckedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                )
            }
            Text(
                statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (status.failed && !status.refreshing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.header(key: String, title: @Composable () -> String) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) {
        Text(
            title(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Name, and the first stream address for custom channels and public https streams */
private fun shareText(ch: TvChannel): String {
    val url = ch.streams.firstOrNull()?.url.orEmpty()
    return if (url.isNotEmpty() && (ch.isCustom || url.startsWith("https://"))) ch.name + "\n" + url else ch.name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvCountrySheet(vm: TvViewModel, onDismiss: () -> Unit) {
    val countries by vm.countries.collectAsStateWithLifecycle()
    val languages by vm.languages.collectAsStateWithLifecycle()
    val selected by vm.selectedCountries.collectAsStateWithLifecycle()
    val selectedLangs by vm.selectedLanguages.collectAsStateWithLifecycle()
    val greece by vm.greeceSelected.collectAsStateWithLifecycle()
    val extraPlaylists by vm.extraPlaylistsEnabled.collectAsStateWithLifecycle()
    val epgOn by vm.epgEnabled.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    val shown = remember(countries, search) {
        val q = search.trim()
        (if (q.isEmpty()) countries else countries.filter { it.name.contains(q, ignoreCase = true) || it.code.equals(q, ignoreCase = true) })
            .distinctBy { it.code } // lazy keys must be unique
    }
    val langChips = remember(languages, selectedLangs) {
        (languages.sortedByDescending { it.channelCount }.take(40) + languages.filter { it.code in selectedLangs }).distinctBy { it.code }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(I18nR.string.au12_tvui_countries), style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (selected.isEmpty()) stringResource(I18nR.string.au12_tvui_countries_none)
                        else stringResource(I18nR.string.au12_tvui_countries_selected, selected.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected.isNotEmpty() || selectedLangs.isNotEmpty()) {
                    TextButton(onClick = { vm.setCountries(emptyList()); vm.setLanguages(emptyList()) }) {
                        Text(stringResource(I18nR.string.au12_tvui_clear))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_done)) }
            }
            if (langChips.isNotEmpty()) {
                Text(
                    stringResource(I18nR.string.au12_tvui_languages),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(langChips, key = { it.code }) { l ->
                        FilterChip(
                            selected = l.code in selectedLangs,
                            onClick = {
                                vm.setLanguages(if (l.code in selectedLangs) selectedLangs - l.code else selectedLangs + l.code)
                            },
                            label = { Text(l.name, maxLines = 1) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it.take(60) },
                singleLine = true,
                placeholder = { Text(stringResource(I18nR.string.au12_tvui_countries_search)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                if (greece) {
                    item(key = "extra:playlists") {
                        ExtraSwitchRow(
                            stringResource(I18nR.string.au13_tvextra_playlists_title),
                            stringResource(I18nR.string.au13_tvextra_playlists_summary),
                            extraPlaylists,
                        ) { vm.setExtraPlaylists(it) }
                    }
                    item(key = "extra:epg") {
                        ExtraSwitchRow(
                            stringResource(I18nR.string.au13_tvextra_epg_title),
                            stringResource(I18nR.string.au13_tvextra_epg_summary),
                            epgOn,
                        ) { vm.setEpg(it) }
                    }
                }
                items(shown, key = { it.code }) { c ->
                    val isSel = c.code in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.setCountries(if (isSel) selected - c.code else selected + c.code) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = isSel, onCheckedChange = null)
                        Text(c.flag, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(I18nR.string.au12_tvui_channel_count, c.channelCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Keep playing in the background"; asks for the notification permission (Android 13+) when it is switched on */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvPlaybackSheet(vm: TvViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val keep by vm.keepInBackground.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(I18nR.string.au15_tvbg_menu_playback),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ExtraSwitchRow(
                stringResource(I18nR.string.au15_tvbg_title),
                stringResource(I18nR.string.au15_tvbg_summary),
                keep,
            ) { on ->
                vm.setKeepInBackground(on)
                if (on && de.mm20.launcher2.comms.tv.TvBackgroundPolicy.needsNotificationPermission(
                        android.os.Build.VERSION.SDK_INT,
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                    )
                ) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}

@Composable
private fun ExtraSwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@StringRes
private fun TvSaveResult.messageRes(): Int? = when (this) {
    TvSaveResult.SAVED -> null
    TvSaveResult.INVALID_NAME -> I18nR.string.au11_tvdata_save_invalid_name
    TvSaveResult.INVALID_STREAM_URL -> I18nR.string.au11_tvdata_save_invalid_url
    TvSaveResult.INVALID_LOGO_URL -> I18nR.string.au11_tvdata_save_invalid_logo
    TvSaveResult.DUPLICATE_URL -> I18nR.string.au11_tvdata_save_duplicate
    TvSaveResult.LIMIT_REACHED -> I18nR.string.au11_tvdata_save_limit
}

@Composable
private fun TvChannelDialog(vm: TvViewModel, channel: TvChannel?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(channel?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(channel?.streams?.firstOrNull()?.url.orEmpty()) }
    var logo by rememberSaveable { mutableStateOf(channel?.logoUrl.orEmpty()) }
    var group by rememberSaveable { mutableStateOf(channel?.group.orEmpty()) }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            TelosCyberHeading {
                Text(stringResource(if (channel == null) I18nR.string.au12_tvui_add_channel else I18nR.string.au12_tvui_edit_channel))
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it.take(120) }, singleLine = true,
                    label = { Text(stringResource(I18nR.string.au12_tvui_field_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    url, { url = it.take(2048) }, singleLine = true,
                    label = { Text(stringResource(I18nR.string.au12_tvui_field_stream)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    logo, { logo = it.take(2048) }, singleLine = true,
                    label = { Text(stringResource(I18nR.string.au12_tvui_field_logo)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    group, { group = it.take(60) }, singleLine = true,
                    label = { Text(stringResource(I18nR.string.au12_tvui_field_group)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val r = vm.saveCustom(channel?.id.orEmpty(), name, url, logo, group)
                        busy = false
                        val res = r.messageRes()
                        if (res == null) onDismiss() else error = res
                    }
                },
            ) { Text(stringResource(I18nR.string.hc_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}
