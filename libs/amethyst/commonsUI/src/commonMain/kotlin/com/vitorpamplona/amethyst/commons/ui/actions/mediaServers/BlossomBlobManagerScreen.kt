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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.mediaServers.BlobRow
import com.vitorpamplona.amethyst.commons.mediaServers.BlossomBlobManagerViewModel
import com.vitorpamplona.amethyst.commons.mediaServers.PresenceState
import com.vitorpamplona.amethyst.commons.mediaServers.ServerPresence
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.blossom_delete_from_host
import com.vitorpamplona.amethyst.commons.resources.blossom_file_details
import com.vitorpamplona.amethyst.commons.resources.blossom_import_menu
import com.vitorpamplona.amethyst.commons.resources.blossom_mirror_to_missing
import com.vitorpamplona.amethyst.commons.resources.blossom_more_actions
import com.vitorpamplona.amethyst.commons.resources.blossom_not_on_all_servers
import com.vitorpamplona.amethyst.commons.resources.blossom_on_all_servers
import com.vitorpamplona.amethyst.commons.resources.blossom_open
import com.vitorpamplona.amethyst.commons.resources.blossom_pay
import com.vitorpamplona.amethyst.commons.resources.blossom_pay_sats
import com.vitorpamplona.amethyst.commons.resources.blossom_payment_message
import com.vitorpamplona.amethyst.commons.resources.blossom_payment_server_says
import com.vitorpamplona.amethyst.commons.resources.blossom_payment_title
import com.vitorpamplona.amethyst.commons.resources.blossom_refresh
import com.vitorpamplona.amethyst.commons.resources.blossom_report
import com.vitorpamplona.amethyst.commons.resources.blossom_report_comment_hint
import com.vitorpamplona.amethyst.commons.resources.blossom_report_title
import com.vitorpamplona.amethyst.commons.resources.blossom_send
import com.vitorpamplona.amethyst.commons.resources.blossom_stored_on
import com.vitorpamplona.amethyst.commons.resources.blossom_sync_all
import com.vitorpamplona.amethyst.commons.resources.blossom_sync_gaps
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.copy
import com.vitorpamplona.amethyst.commons.resources.manage_stored_files_empty
import com.vitorpamplona.amethyst.commons.resources.my_blossom_data
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.resources.retry
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.edgeToEdgeDialogProperties
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.platform.FullscreenVideoView
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip56Reports.ReportType
import kotlinx.coroutines.launch
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

private const val MIME_IMAGE_PREFIX = "image/"
private const val MIME_VIDEO_PREFIX = "video/"

@Composable
fun BlossomBlobManagerScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: BlossomBlobManagerViewModel = rememberViewModel { BlossomBlobManagerViewModel() }
    vm.init(accountViewModel, LocalAppServices.current)

    LaunchedEffect(accountViewModel) { vm.refresh() }

    val blobs by vm.blobs.collectAsStateWithLifecycle()
    val loading by vm.isLoading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val pendingPayment by vm.pendingPayment.collectAsStateWithLifecycle()

    // The tapped file, if any. We keep only the hash and re-resolve the row from the
    // live list each recomposition so the open viewer/sheet stays in sync with
    // mirror/delete updates (and closes itself when the last copy of the blob is deleted).
    var selectedHash by remember { mutableStateOf<HexKey?>(null) }

    pendingPayment?.let { pending ->
        BlossomPaymentDialog(
            host = pending.targetHost,
            amountSats = pending.amountSats,
            reason = pending.payment.sanitizedReason(),
            onConfirm = { vm.confirmPendingPayment() },
            onDismiss = { vm.cancelPendingPayment() },
        )
    }

    selectedHash?.let { hash ->
        val selected = blobs.firstOrNull { it.hash == hash }
        when {
            selected == null -> selectedHash = null

            // Images and videos open in the full-screen zoomable viewer, which carries
            // the actions in its own bottom drawer. Everything else (PDFs, arbitrary
            // blobs) has nothing to zoom, so it goes straight to the actions sheet.
            selected.url != null && selected.isViewable ->
                BlossomBlobViewer(
                    row = selected,
                    vm = vm,
                    accountViewModel = accountViewModel,
                    onDismiss = { selectedHash = null },
                )

            else ->
                BlobDetailSheet(
                    row = selected,
                    vm = vm,
                    onDismiss = { selectedHash = null },
                )
        }
    }

    Scaffold(
        topBar = {
            TopBarExtensibleWithBackButton(
                title = { Text(stringRes(Res.string.my_blossom_data)) },
                nav = nav,
                actions = {
                    if (loading) {
                        // Keep the spinner as immediate feedback that a refresh is in flight; the
                        // overflow menu returns once it settles.
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        BlobManagerOverflowMenu(
                            onRefresh = { vm.refresh() },
                            onImport = { nav.nav(Route.ImportBlossomBlobs) },
                        )
                    }
                },
            )
        },
        bottomBar = {
            AppBottomBar(Route.ManageBlossomBlobs, nav, accountViewModel) { route ->
                if (route != Route.ManageBlossomBlobs) nav.navBottomBar(route)
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding(),
                    ),
        ) {
            when {
                loading && blobs.isEmpty() -> CenteredState { CircularProgressIndicator() }

                error != null && blobs.isEmpty() ->
                    CenteredState {
                        StatusGlyph(MaterialSymbols.Warning, MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Text(error ?: "", color = MaterialTheme.colorScheme.grayText)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.refresh() }) { Text(stringRes(Res.string.retry)) }
                    }

                blobs.isEmpty() ->
                    CenteredState {
                        StatusGlyph(MaterialSymbols.Storage, MaterialTheme.colorScheme.grayText)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringRes(Res.string.manage_stored_files_empty),
                            color = MaterialTheme.colorScheme.grayText,
                        )
                    }

                else ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (blobs.any { it.hasMissing }) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SyncAllBanner(onSyncAll = { vm.syncAll() })
                            }
                        }
                        items(blobs, key = { it.hash }) { row ->
                            GalleryTile(row, onClick = { selectedHash = row.hash })
                        }
                    }
            }
        }
    }
}

/**
 * The top-bar overflow: "Refresh" re-reads the presence matrix; "Import" opens the flow
 * that pulls the user's files off other Blossom servers into their own.
 */
@Composable
private fun BlobManagerOverflowMenu(
    onRefresh: () -> Unit,
    onImport: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(symbol = MaterialSymbols.MoreVert, contentDescription = stringRes(Res.string.blossom_more_actions))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.blossom_refresh)) },
                leadingIcon = { OverflowMenuIcon(MaterialSymbols.Sync) },
                onClick = {
                    open = false
                    onRefresh()
                },
            )
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.blossom_import_menu)) },
                leadingIcon = { OverflowMenuIcon(MaterialSymbols.CloudDownload) },
                onClick = {
                    open = false
                    onImport()
                },
            )
        }
    }
}

@Composable
private fun OverflowMenuIcon(symbol: MaterialSymbol) {
    Icon(
        symbol = symbol,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Whether a blob is an image or a video, i.e. it can be previewed and shown full-screen. */
private val BlobRow.isViewable: Boolean
    get() = type?.let { it.startsWith(MIME_IMAGE_PREFIX) || it.startsWith(MIME_VIDEO_PREFIX) } == true

@Composable
private fun CenteredState(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun StatusGlyph(
    symbol: MaterialSymbol,
    tint: Color,
) {
    Box(
        modifier =
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(34.dp), tint = tint)
    }
}

@Composable
private fun SyncAllBanner(onSyncAll: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringRes(Res.string.blossom_sync_gaps), style = MaterialTheme.typography.bodyMedium)
        }
        FilledTonalButton(onClick = onSyncAll) {
            Icon(symbol = MaterialSymbols.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringRes(Res.string.blossom_sync_all))
        }
    }
}

/**
 * One gallery cell: a square preview — the image itself, or a decoded first frame for a
 * video (with a play badge) — plus a corner badge summarizing how many of the user's
 * servers hold this blob. Tapping it opens the full-screen viewer / actions.
 */
@Composable
private fun GalleryTile(
    row: BlobRow,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onClick),
    ) {
        BlobPreview(row = row, modifier = Modifier.fillMaxSize())

        SyncBadge(
            row = row,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        )
    }
}

/**
 * Renders a blob's visual preview inside [modifier]'s bounds: the image, or a video's
 * first frame (decoded by Coil's VideoFrameDecoder) with a centered play glyph. Falls
 * back to a type glyph while loading fails or for non-visual blobs (e.g. an HLS playlist
 * Coil can't decode).
 */
@Composable
private fun BlobPreview(
    row: BlobRow,
    modifier: Modifier = Modifier,
    glyphSize: Dp = 34.dp,
    playIconSize: Dp = 40.dp,
) {
    val isVideo = row.type?.startsWith(MIME_VIDEO_PREFIX) == true
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (row.url != null && row.isViewable) {
            SubcomposeAsyncImage(
                model = row.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                val state by painter.state.collectAsState()
                when (state) {
                    is AsyncImagePainter.State.Success -> {
                        SubcomposeAsyncImageContent()
                        if (isVideo) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    symbol = MaterialSymbols.PlayCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(playIconSize),
                                    tint = Color.White,
                                )
                            }
                        }
                    }

                    is AsyncImagePainter.State.Error -> BlobGlyph(row, glyphSize)

                    else -> {}
                }
            }
        } else {
            BlobGlyph(row, glyphSize)
        }
    }
}

@Composable
private fun BlobGlyph(
    row: BlobRow,
    size: Dp,
) {
    Icon(
        symbol = glyphFor(row.type),
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Corner chip over a gallery tile: a green check when the blob is on every server, an
 * amber cloud when some server is still missing it, plus a `present/total` count so the
 * spread is legible at a glance without opening the file.
 */
@Composable
private fun SyncBadge(
    row: BlobRow,
    modifier: Modifier = Modifier,
) {
    val synced = !row.hasMissing
    val accent = if (synced) MaterialTheme.colorScheme.allGoodColor else MaterialTheme.colorScheme.tertiary
    Row(
        modifier =
            modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            symbol = if (synced) MaterialSymbols.CheckCircle else MaterialSymbols.CloudUpload,
            contentDescription =
                stringRes(
                    if (synced) Res.string.blossom_on_all_servers else Res.string.blossom_not_on_all_servers,
                ),
            modifier = Modifier.size(13.dp),
            tint = accent,
        )
        Text(
            text = "${row.presentCount}/${row.servers.size}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

/**
 * Full-screen viewer opened from a gallery tile: the image is zoomable/pannable and a
 * video plays inline, matching the app's [com.vitorpamplona.amethyst.ui.components.ZoomableImageDialog].
 * The blob's storage matrix and its sync/copy/open/share/report/delete actions live in a
 * bottom drawer reached from the top bar, so they don't cover the media until asked for.
 */
@Composable
private fun BlossomBlobViewer(
    row: BlobRow,
    vm: BlossomBlobManagerViewModel,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val sharer = rememberTextSharer()
    val shareTitle = stringRes(Res.string.quick_action_share)
    var drawerOpen by remember { mutableStateOf(false) }
    val isVideo = row.type?.startsWith(MIME_VIDEO_PREFIX) == true

    Dialog(
        onDismissRequest = onDismiss,
        properties = edgeToEdgeDialogProperties(),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val url = row.url
            if (url != null && isVideo) {
                val controllerVisible = remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FullscreenVideoView(
                        videoUri = url,
                        mimeType = row.type,
                        contentScale = ContentScale.Fit,
                        controllerVisible = controllerVisible,
                        accountViewModel = accountViewModel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (url != null) {
                val zoomState = rememberZoomState()
                AsyncImage(
                    model = url,
                    contentDescription = row.hash,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().zoomable(zoomState),
                )
            }

            // Top bar: back, share, and the drawer toggle.
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ViewerIconButton(MaterialSymbols.AutoMirrored.ArrowBack, stringRes(Res.string.back), onDismiss)
                Spacer(Modifier.weight(1f))
                if (url != null) {
                    ViewerIconButton(MaterialSymbols.Share, stringRes(Res.string.quick_action_share)) {
                        sharer.share(url, null, shareTitle)
                    }
                }
                ViewerIconButton(MaterialSymbols.Info, stringRes(Res.string.blossom_file_details)) {
                    drawerOpen = true
                }
            }

            // Bottom drawer with the file's storage matrix and actions.
            if (drawerOpen) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(onClick = { drawerOpen = false }),
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    BlobActionsContent(
                        row = row,
                        vm = vm,
                        modifier =
                            Modifier
                                .fillMaxHeight(0.7f)
                                .navigationBarsPadding()
                                // Swallow taps so the scrim behind doesn't dismiss the drawer.
                                .clickable(enabled = false) {},
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerIconButton(
    symbol: MaterialSymbol,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
    ) {
        Icon(symbol = symbol, contentDescription = contentDescription, tint = Color.White)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlobDetailSheet(
    row: BlobRow,
    vm: BlossomBlobManagerViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        BlobActionsContent(row = row, vm = vm, modifier = Modifier.navigationBarsPadding())
    }
}

/**
 * The blob's detail + action list, shared by the [BlobDetailSheet] (non-visual blobs)
 * and by [BlossomBlobViewer]'s bottom drawer: a preview + hash/size header, the "Stored
 * on" per-server matrix, the sync (mirror-to-missing) button, and the
 * copy/open/share/report/delete actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlobActionsContent(
    row: BlobRow,
    vm: BlossomBlobManagerViewModel,
    modifier: Modifier = Modifier,
) {
    var reportOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val sharer = rememberTextSharer()
    val uriHandler = LocalUriHandler.current
    val shareTitle = stringRes(Res.string.quick_action_share)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Preview + identity.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                BlobPreview(row = row, modifier = Modifier.fillMaxSize(), glyphSize = 28.dp, playIconSize = 28.dp)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = row.hash.take(12) + "…" + row.hash.takeLast(6),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
                Text(
                    text = listOfNotNull(row.type, row.size?.let { humanBytes(it) }).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }

        // Where the file lives.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringRes(Res.string.blossom_stored_on),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.grayText,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.servers.forEach { ServerPill(it) }
            }
        }

        // Primary CTA: fill the gaps for this file.
        if (row.hasMissing && row.url != null) {
            FilledTonalButton(
                onClick = { vm.mirrorToMissing(row) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(symbol = MaterialSymbols.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringRes(Res.string.blossom_mirror_to_missing))
            }
        }

        // Secondary actions.
        val url = row.url
        if (url != null) {
            DetailAction(MaterialSymbols.ContentCopy, stringRes(Res.string.copy)) {
                scope.launch { clipboard.setText(url) }
            }
            DetailAction(MaterialSymbols.Share, stringRes(Res.string.quick_action_share)) {
                sharer.share(url, null, shareTitle)
            }
            DetailAction(MaterialSymbols.AutoMirrored.OpenInNew, stringRes(Res.string.blossom_open)) {
                runCatching { uriHandler.openUri(url) }
            }
        }

        if (row.hasPresent) {
            DetailAction(MaterialSymbols.Report, stringRes(Res.string.blossom_report)) { reportOpen = true }

            HorizontalDivider()

            row.presentServers.forEach { server ->
                DetailAction(
                    symbol = MaterialSymbols.Delete,
                    label = stringRes(Res.string.blossom_delete_from_host, vm.hostOf(server)),
                    tint = MaterialTheme.colorScheme.error,
                ) { vm.delete(row.hash, server) }
            }
        }
    }

    if (reportOpen) {
        BlossomReportDialog(row = row, vm = vm, onDismiss = { reportOpen = false })
    }
}

@Composable
private fun DetailAction(
    symbol: MaterialSymbol,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(22.dp), tint = tint)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

private fun glyphFor(type: String?): MaterialSymbol =
    when {
        type?.startsWith(MIME_IMAGE_PREFIX) == true -> MaterialSymbols.Image
        type?.startsWith(MIME_VIDEO_PREFIX) == true -> MaterialSymbols.PlayCircle
        else -> MaterialSymbols.Storage
    }

@Composable
private fun ServerPill(presence: ServerPresence) {
    val present = presence.state == PresenceState.PRESENT
    val pending = presence.state == PresenceState.PENDING
    val accent = MaterialTheme.colorScheme.allGoodColor
    val bg =
        if (present) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(9.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            val dot = if (present) accent else MaterialTheme.colorScheme.grayText
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dot))
        }
        Text(
            text = presence.host,
            style = MaterialTheme.typography.labelMedium,
            color = if (present) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.grayText,
        )
    }
}

private fun humanBytes(bytes: Long): String =
    when {
        bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
        bytes >= 1_000 -> "${bytes / 1_000} KB"
        else -> "$bytes B"
    }

@Composable
private fun BlossomPaymentDialog(
    host: String,
    amountSats: Long?,
    reason: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(symbol = MaterialSymbols.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.allGoodColor) },
        title = { Text(stringRes(Res.string.blossom_payment_title)) },
        text = {
            Column {
                Text(text = stringRes(Res.string.blossom_payment_message, host))
                // X-Reason is server-controlled: it is sanitized upstream and rendered
                // here attributed to the server, in a dimmer italic, so it can never be
                // mistaken for Amethyst's own wording (e.g. a fake "Pay 1 sat").
                reason?.let {
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = stringRes(Res.string.blossom_payment_server_says, host, it),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onConfirm) {
                Icon(symbol = MaterialSymbols.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    if (amountSats != null) {
                        pluralStringRes(Res.plurals.blossom_pay_sats, amountSats.toInt(), amountSats.toInt())
                    } else {
                        stringRes(Res.string.blossom_pay)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}

@Composable
private fun BlossomReportDialog(
    row: BlobRow,
    vm: BlossomBlobManagerViewModel,
    onDismiss: () -> Unit,
) {
    var comment by remember { mutableStateOf("") }
    var typeMenuOpen by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(ReportType.OTHER) }
    // Report to the first server that actually holds the blob.
    val server = row.presentServers.firstOrNull() ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(symbol = MaterialSymbols.Report, contentDescription = null) },
        title = { Text(stringRes(Res.string.blossom_report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { typeMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(type.code)
                    }
                    DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                        ReportType.entries.forEach { rt ->
                            DropdownMenuItem(
                                text = { Text(rt.code) },
                                onClick = {
                                    type = rt
                                    typeMenuOpen = false
                                },
                            )
                        }
                    }
                }
                SpellCheckedOutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringRes(Res.string.blossom_report_comment_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                vm.report(row.hash, server, type, comment)
                onDismiss()
            }) { Text(stringRes(Res.string.blossom_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}
