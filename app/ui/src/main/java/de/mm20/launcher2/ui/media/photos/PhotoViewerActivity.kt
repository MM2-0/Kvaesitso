package de.mm20.launcher2.ui.media.photos

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.viewinterop.AndroidView
import de.mm20.launcher2.ui.component.rememberVirtualAppBlur
import de.mm20.launcher2.ui.theme.transparency.transparency
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.common.share.ShareActions
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full screen photo viewer with pinch zoom, EXIF editor and metadata-free sharing. */
class PhotoViewerActivity : BaseActivity() {

    override fun onStart() {
        super.onStart()
        de.mm20.launcher2.base.VirtualAppGuard.enter(this, "telos_photos_app://photos")
    }

    override fun onStop() {
        de.mm20.launcher2.base.VirtualAppGuard.leave(this, "telos_photos_app://photos")
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val uris = intent.getStringArrayListExtra(EXTRA_URIS)?.map { Uri.parse(it) }
            ?: intent.data?.let { listOf(it) }
            // a picture shared to Telos Viewer
            ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { listOf(it) }
        if (uris.isNullOrEmpty()) {
            finish()
            return
        }
        val start = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, uris.size - 1)
        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    PhotoViewer(uris, start, onClose = { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_URIS = "de.mm20.launcher2.photos.URIS"
        const val EXTRA_INDEX = "de.mm20.launcher2.photos.INDEX"
    }
}

@Composable
private fun ZoomableImage(
    uri: Uri,
    onTap: () -> Unit,
    onScale: (Float) -> Unit = {},
    swipeDownEnabled: Boolean = true,
    onSwipeDown: () -> Unit = {},
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var dismissY by remember { mutableFloatStateOf(0f) }
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) { scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero } else {
                            // zoom in around the tapped point
                            val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                            val maxX = size.width * 1.5f / 2f
                            val maxY = size.height * 1.5f / 2f
                            val raw = (tap - c) * (1f - 2.5f)
                            scale = 2.5f
                            offset = androidx.compose.ui.geometry.Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                // Only a pinch, or a drag while zoomed in, is taken here. A single finger on a photo
                // that is not zoomed is left to the pager, so that swiping to the next photo works.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val newScale = (scale * zoom).coerceIn(1f, 8f)
                            val f = newScale / scale
                            // the layer scales around its centre: keep the point between the fingers where it is
                            val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                            scale = newScale
                            offset = if (scale > 1f) {
                                // the photo cannot be dragged out of the screen
                                val maxX = size.width * (scale - 1f) / 2f
                                val maxY = size.height * (scale - 1f) / 2f
                                val raw = (centroid - c) * (1f - f) + offset * f + pan
                                androidx.compose.ui.geometry.Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
                            } else androidx.compose.ui.geometry.Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(scale, swipeDownEnabled) {
                if (!swipeDownEnabled || scale > 1.02f) return@pointerInput
                detectDragGestures(
                    onDragEnd = {
                        if (dismissY > size.height * 0.18f) onSwipeDown()
                        dismissY = 0f
                    },
                    onDragCancel = { dismissY = 0f },
                    onDrag = { change, drag ->
                        if (drag.y > 0 && kotlin.math.abs(drag.y) > kotlin.math.abs(drag.x)) {
                            dismissY += drag.y
                            change.consume()
                        }
                    },
                )
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y + dismissY,
                alpha = (1f - dismissY / 1200f).coerceIn(0.4f, 1f),
            ),
    )
    LaunchedEffect(scale) { onScale(scale) }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PagerVideo(uri: Uri, onTap: () -> Unit) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    DisposableEffect(uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                setOnClickListener { onTap() }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private val staticPhotoWallpaperTargets = listOf(
    I18nR.string.preference_wallpaper_set_home to de.mm20.launcher2.wallpapers.StaticWallpaperTarget.Home,
    I18nR.string.preference_wallpaper_set_lock to de.mm20.launcher2.wallpapers.StaticWallpaperTarget.Lock,
    I18nR.string.preference_wallpaper_set_both to de.mm20.launcher2.wallpapers.StaticWallpaperTarget.Both)

@Composable
private fun PhotoViewer(uris: List<Uri>, start: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = start) { uris.size }
    var chrome by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showOcr by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var starred by remember { mutableStateOf(false) }
    var wallpaperTagged by remember { mutableStateOf(false) }
    var wallpaperUri by remember { mutableStateOf<Uri?>(null) }
    var settingWallpaper by remember { mutableStateOf(false) }
    val wallpaperScope = rememberCoroutineScope()
    val wallpapers = org.koin.compose.koinInject<de.mm20.launcher2.wallpapers.WallpapersService>()
    wallpaperUri?.let { selectedUri ->
        AlertDialog(onDismissRequest = { if (!settingWallpaper) wallpaperUri = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.wallpaper_target_dialog_title))  } },
            text = { Column {
                staticPhotoWallpaperTargets.forEach { (label, target) ->
                    TextButton(enabled = !settingWallpaper, onClick = {
                        settingWallpaper = true
                        wallpaperScope.launch {
                            try {
                                val ok = wallpapers.setStaticWallpaper(selectedUri, target)
                                Toast.makeText(context, context.getString(if (ok) I18nR.string.wallpaper_set_success else I18nR.string.wallpaper_set_failure), Toast.LENGTH_SHORT).show()
                                wallpaperUri = null
                            } finally { settingWallpaper = false }
                        }
                    }) { Text(stringResource(label)) }
                }
                if (settingWallpaper) LinearProgressIndicator(Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(enabled = !settingWallpaper, onClick = { wallpaperUri = null }) { Text(stringResource(I18nR.string.hc_cancel)) } })
    }
    val current = uris[pager.currentPage]
    val mime = remember(current) { context.contentResolver.getType(current).orEmpty() }
    val isVideo = mime.startsWith("video/")
    val blur = rememberVirtualAppBlur()
    val onWhite = Color.White

    LaunchedEffect(current) {
        starred = ViewerLibrary.isFavorite(context, current)
        wallpaperTagged = ViewerLibrary.isWallpaper(context, current)
    }

    if (confirmDelete) de.mm20.launcher2.ui.trash.UriDeleteDialog(current, onClose, { confirmDelete = false })

    var pendingWrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) pendingWrite?.invoke()
        pendingWrite = null
    }
    fun withWriteAccess(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 30 && current.authority == MediaStore.AUTHORITY) {
            pendingWrite = action
            val sender = MediaStore.createWriteRequest(context.contentResolver, listOf(current)).intentSender
            writeLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } else action()
    }

    fun share(uri: Uri) {
        wallpaperScope.launch {
            val ok = withContext(Dispatchers.IO) { ShareActions.shareUris(context, listOf(uri)) }
            if (!ok) Toast.makeText(context, I18nR.string.au20_viewer_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 1f - MaterialTheme.transparency.background * 0.15f))
            ,
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val uri = uris[page]
            val pageMime = remember(uri) { context.contentResolver.getType(uri).orEmpty() }
            if (pageMime.startsWith("video/")) {
                PagerVideo(uri) { chrome = !chrome }
            } else {
                ZoomableImage(
                    uri = uri,
                    onTap = { chrome = !chrome },
                    onSwipeDown = onClose,
                )
            }
        }
        if (chrome) {
            ViewerTopChrome(blur) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(BaseR.drawable.arrow_back_24px), stringResource(I18nR.string.hc_back), tint = onWhite)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${pager.currentPage + 1} / ${uris.size}",
                    color = onWhite,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Box {
                    IconButton(onClick = { more = true }) {
                        Icon(painterResource(BaseR.drawable.more_vert_24px), stringResource(I18nR.string.hc_more), tint = onWhite)
                    }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(I18nR.string.viewer_set_wallpaper)) },
                            onClick = {
                                more = false
                                if (isVideo || mime == "image/gif") {
                                    wallpaperScope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            wallpapers.setVideoWallpaper(current, false)
                                        }
                                        Toast.makeText(
                                            context,
                                            context.getString(if (ok) I18nR.string.wallpaper_set_success else I18nR.string.wallpaper_set_failure),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        if (ok) context.tryStartActivity(wallpapers.getActivationIntent())
                                    }
                                } else wallpaperUri = current
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (wallpaperTagged) I18nR.string.viewer_remove_from_wallpapers
                                        else I18nR.string.viewer_add_to_wallpapers,
                                    ),
                                )
                            },
                            onClick = {
                                more = false
                                wallpaperTagged = ViewerLibrary.toggleWallpaper(context, current)
                            },
                        )
                        if (Build.VERSION.SDK_INT >= 30 && current.authority == MediaStore.AUTHORITY) {
                            DropdownMenuItem(
                                text = { Text(stringResource(I18nR.string.hc_delete)) },
                                onClick = { more = false; confirmDelete = true },
                            )
                        }
                    }
                }
            }
            ViewerPillBar(blur, Modifier.align(Alignment.BottomCenter)) {
                ViewerAction(BaseR.drawable.share_24px, stringResource(I18nR.string.hc_share)) { share(current) }
                if (!isVideo) {
                    ViewerAction(BaseR.drawable.tune_24px, stringResource(I18nR.string.hc_edit)) {
                        context.startActivity(Intent(context, PhotoEditorActivity::class.java).setData(current))
                    }
                    ViewerAction(BaseR.drawable.text_fields_24px, "OCR") { showOcr = true }
                }
                ViewerAction(BaseR.drawable.info_24px, stringResource(I18nR.string.hc_details)) { showInfo = true }
                ViewerAction(
                    if (starred) BaseR.drawable.star_20px_filled else BaseR.drawable.star_20px,
                    stringResource(I18nR.string.viewer_favorite),
                ) {
                    starred = ViewerLibrary.toggleFavorite(context, current)
                }
            }
        }
    }

    if (showInfo) {
        ExifDialog(
            uri = current,
            onDismiss = { showInfo = false },
            withWriteAccess = ::withWriteAccess,
            onShareClean = {
                val file = PhotoExif.cleanCopy(context, current)
                if (file == null) {
                    Toast.makeText(context, context.getString(I18nR.string.hc_could_not_create_a_copy), Toast.LENGTH_SHORT).show()
                } else {
                    share(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                }
            },
        )
    }
    if (showOcr) {
        OcrSheet(current) { showOcr = false }
    }
}

@Composable
private fun ViewerAction(icon: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = Color.White)
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ExifDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    withWriteAccess: (() -> Unit) -> Unit,
    onShareClean: () -> Unit,
) {
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    val info = remember(uri, reload) { PhotoExif.read(context.contentResolver, uri) }
    val edits = remember(uri, reload) {
        mutableStateMapOf<String, String>().apply { info?.values?.let { putAll(it) } }
    }
    var editing by remember { mutableStateOf(false) }

    fun done(ok: Boolean) {
        Toast.makeText(context, context.getString(if (ok) I18nR.string.hc_done else I18nR.string.au_viewer_metadata_unsupported), Toast.LENGTH_SHORT).show()
        reload++
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_details))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (info == null) {
                    Text(stringResource(I18nR.string.hc_no_metadata_available))
                } else {
                    if (info.width > 0 && info.height > 0) Text("${info.width} × ${info.height}", style = MaterialTheme.typography.bodyMedium)
                    info.latLong?.let {
                        Text(
                            stringResource(I18nR.string.au_viewer_location, "%.5f, %.5f".format(it[0], it[1])),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    for ((labelRes, tag) in PhotoExif.tags) {
                        val label = stringResource(labelRes)
                        if (editing) {
                            OutlinedTextField(
                                value = edits[tag].orEmpty(),
                                onValueChange = { edits[tag] = it },
                                label = { Text(label) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            )
                        } else if (info.values[tag].orEmpty().isNotBlank()) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(info.values[tag].orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                    if (!editing) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            if (info.hasGps) {
                                AssistChip(onClick = {
                                    withWriteAccess { done(PhotoExif.strip(context.contentResolver, uri, gpsOnly = true)) }
                                }, label = { Text(stringResource(I18nR.string.hc_remove_location)) })
                            }
                            AssistChip(onClick = {
                                withWriteAccess { done(PhotoExif.strip(context.contentResolver, uri, gpsOnly = false)) }
                            }, label = { Text(stringResource(I18nR.string.hc_remove_all)) })
                        }
                        AssistChip(
                            onClick = onShareClean,
                            label = { Text(stringResource(I18nR.string.hc_share_without_metadata)) },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (editing) {
                TextButton(onClick = {
                    withWriteAccess { done(PhotoExif.write(context.contentResolver, uri, edits.toMap())); editing = false }
                }) { Text(stringResource(I18nR.string.hc_save)) }
            } else if (info != null) {
                TextButton(onClick = { editing = true }) { Text(stringResource(I18nR.string.hc_edit)) }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (editing) editing = false else onDismiss() }) {
                Text(stringResource(if (editing) I18nR.string.hc_cancel else I18nR.string.hc_close))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrSheet(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var extractedText by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    @Suppress("DEPRECATION")
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    LaunchedEffect(uri) {
        loading = true
        extractedText = de.mm20.launcher2.ui.camera.DocumentScanEngine.recognizeText(context, uri)
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(I18nR.string.ocr_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(I18nR.string.ocr_scanning), style = MaterialTheme.typography.bodyMedium)
            } else if (extractedText.isNullOrBlank()) {
                Text(stringResource(I18nR.string.ocr_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(extractedText!!, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()))
                }
                val copiedMessage = stringResource(I18nR.string.ocr_copied)
                Button(
                    onClick = {
                        @Suppress("DEPRECATION")
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(extractedText!!))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(painterResource(BaseR.drawable.content_copy_24px), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(I18nR.string.ocr_copy))
                }
            }
        }
    }
}
