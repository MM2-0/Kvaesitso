package dev.intelia.launcher.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.searchactions.flow.FlowAlias
import de.mm20.launcher2.searchactions.flow.FlowAliasTarget
import de.mm20.launcher2.searchactions.flow.FlowAliasAction
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.settings.searchactions.FlowAliasSheet
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteliaHome(
    model: InteliaHomeViewModel,
    homePulse: Int,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onSource: () -> Unit,
    onDefaultHome: () -> Unit,
) {
    val preferences by model.ui.collectAsStateWithLifecycle()
    val dark = when (preferences.theme) { 0 -> isSystemInDarkTheme(); 2 -> false; else -> true }
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFE8E8E8), onPrimary = Color(0xFF222222), background = Color(0xFF121212),
        surface = Color(0xFF1C1C1C), surfaceContainer = Color(0xFF252525), surfaceContainerHigh = Color(0xFF353535),
        onSurface = Color(0xFFF5F5F5), onSurfaceVariant = Color(0xFFD0D0D0), outlineVariant = Color(0xFF404040),
        outline = Color(0xFF707070), secondaryContainer = Color(0xFF333333), onSecondaryContainer = Color(0xFFEEEEEE),
    ) else lightColorScheme(
        primary = Color(0xFF292929), background = Color(0xFFF5F5F5), surface = Color.White,
        surfaceContainer = Color(0xFFEEEEEE), onSurface = Color(0xFF161616), onSurfaceVariant = Color(0xFF4B4B4B),
        outlineVariant = Color(0xFFD3D3D3),
        outline = Color(0xFF999999), secondaryContainer = Color(0xFFE0E0E0), onSecondaryContainer = Color(0xFF292929),
    )
    MaterialTheme(colorScheme = scheme) {
        val context = LocalContext.current
        DisposableEffect(context, dark) {
            (context as? android.app.Activity)?.let { activity ->
                androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            onDispose { }
        }
        val query by model.query.collectAsStateWithLifecycle()
        val apps by model.apps.collectAsStateWithLifecycle()
        val shortcuts by model.shortcuts.collectAsStateWithLifecycle()
        val aliases by model.aliases.collectAsStateWithLifecycle()
        val arcApps by model.arcApps.collectAsStateWithLifecycle()
        val targets by model.targets.collectAsStateWithLifecycle()
        val actions by model.actions.collectAsStateWithLifecycle()
        val editor by model.editor.collectAsStateWithLifecycle()
        val editingAlias by model.editingAlias.collectAsStateWithLifecycle()
        val aliasError by model.aliasError.collectAsStateWithLifecycle()
        val saving by model.savingAlias.collectAsStateWithLifecycle()
        val message by model.message.collectAsStateWithLifecycle()
        val restoreReady by model.restoreReady.collectAsStateWithLifecycle()
        var mode by rememberSaveable { mutableIntStateOf(0) }
        var sheet by rememberSaveable { mutableIntStateOf(0) }
        var selectedApp by remember { mutableStateOf<Application?>(null) }
        var lensPulse by remember { mutableIntStateOf(0) }
        val searchFocus = remember { FocusRequester() }
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        val duration = if (preferences.reduceMotion) 0 else 160
        val page = if (query.isNotBlank()) 1 else mode
        val blurred = InteliaWallpaper(preferences.wallpaper, page != 0 || sheet != 0)
        val backdrop = if (preferences.wallpaper == 2) scheme.background else scheme.background.copy(
            alpha = if (page != 0) 0.93f else if (!dark) 0.80f else if (blurred) 0.52f else 0.62f)
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { scope.coroutineContext.cancelChildren() }
        LaunchedEffect(homePulse) { mode = 0; sheet = 0; model.query.value = ""; model.dismissEditor(); selectedApp = null; focus.clearFocus(); keyboard?.hide() }
        LaunchedEffect(mode) { if (mode == 1) { searchFocus.requestFocus(); keyboard?.show() } }
        LaunchedEffect(message) {
            message?.let { snackbar.showSnackbar(context.getString(it)); model.message.value = null }
        }
        LaunchedEffect(apps, selectedApp) {
            if (selectedApp != null && apps.none { it.key == selectedApp?.key }) selectedApp = null
        }
        BackHandler {
            when { editor -> model.dismissEditor(); restoreReady -> model.cancelRestore(); selectedApp != null -> selectedApp = null
                sheet != 0 -> sheet = 0
                else -> { lensPulse++; mode = 0; model.query.value = ""; focus.clearFocus(); keyboard?.hide() } }
        }
        Scaffold(containerColor = backdrop, contentColor = scheme.onSurface, snackbarHost = { SnackbarHost(snackbar) }) { inset ->
            Column(Modifier.fillMaxSize().padding(inset).consumeWindowInsets(inset).imePadding().padding(horizontal = 20.dp)) {
                if (page != 0) Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page != 0) IconButton(onClick = { mode = 0; model.query.value = ""; keyboard?.hide(); focus.clearFocus() }) {
                        HomeGlyph(3, stringResource(R.string.intelia_home))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { mode = 2; model.query.value = ""; keyboard?.hide(); focus.clearFocus() }) { HomeGlyph(2, stringResource(R.string.intelia_flow)) }
                    IconButton(onClick = { sheet = 1; keyboard?.hide(); focus.clearFocus() }) { HomeGlyph(4, stringResource(R.string.intelia_settings)) }
                }
                Box(Modifier.weight(1f)) {
                    Crossfade(targetState = page, animationSpec = tween(duration), label = "intelia-morph") { page ->
                        when (page) {
                            0 -> Box(Modifier.fillMaxSize()) {
                                ClockHome(preferences.showClock, onLibrary = { mode = 1 })
                                key(homePulse, lensPulse) { InteliaLens(arcApps, preferences.reduceMotion, onSearch = { mode = 1 },
                                    onLaunch = { app -> scope.launch { if (!model.launch(app, context)) snackbar.showSnackbar(context.getString(R.string.intelia_launch_failed)) } },
                                    modifier = Modifier.align(Alignment.BottomCenter)) }
                            }
                            2 -> FlowLibrary(aliases, targets, onCreate = { model.edit(null) }, onEdit = model::edit)
                            else -> Column(Modifier.fillMaxSize()) {
                                if (actions.isNotEmpty()) {
                                    actions.forEach { action -> CommandPreview(action) { scope.launch { action.activate(context) } } }
                                    Spacer(Modifier.height(18.dp))
                                }
                                Text(stringResource(R.string.intelia_library), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(20.dp))
                                if (apps.isEmpty()) Text(stringResource(R.string.intelia_no_results), color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                                    items(apps, key = { it.key }) { app -> AppRow(app,
                                        onClick = { scope.launch { if (!model.launch(app, context)) snackbar.showSnackbar(context.getString(R.string.intelia_launch_failed)) } },
                                        onLongClick = { selectedApp = app; keyboard?.hide(); focus.clearFocus() }) }
                                    if (shortcuts.isNotEmpty()) item { Text(stringResource(R.string.intelia_shortcuts), Modifier.padding(top = 16.dp, bottom = 8.dp), fontSize = 13.sp, color = scheme.onSurfaceVariant) }
                                    items(shortcuts, key = { "shortcut:${it.key}" }) { shortcut ->
                                        ListItem(headlineContent = { Text(shortcut.labelOverride ?: shortcut.label) }, supportingContent = { Text(shortcut.appName ?: "") },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable {
                                                scope.launch { if (!model.launchShortcut(shortcut, context)) snackbar.showSnackbar(context.getString(R.string.intelia_launch_failed)) }
                                            })
                                    }
                                }
                            }
                        }
                    }
                }
                if (page != 0) Surface(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp), shape = RoundedCornerShape(22.dp), color = scheme.surface.copy(alpha = 0.94f)) {
                    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(value = query, onValueChange = { model.query.value = it }, singleLine = true,
                            modifier = Modifier.weight(1f).focusRequester(searchFocus).semantics { contentDescription = context.getString(R.string.intelia_lens_description) },
                            placeholder = { Text(stringResource(R.string.intelia_search), fontSize = 14.sp) },
                            leadingIcon = { HomeGlyph(0, null) },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
                        if (query.isNotEmpty()) IconButton(onClick = { model.query.value = "" }) { Text("×", Modifier.semantics { contentDescription = context.getString(R.string.intelia_clear) }, fontSize = 24.sp) }
                        else {
                            IconButton(onClick = { mode = 1; keyboard?.hide(); focus.clearFocus() }) { HomeGlyph(1, stringResource(R.string.intelia_apps)) }
                            IconButton(onClick = { mode = 2; keyboard?.hide(); focus.clearFocus() }) { HomeGlyph(2, stringResource(R.string.intelia_flow)) }
                        }
                    }
                }
            }
        }
        if (sheet == 1) InteliaSettings(model, onBackup, onRestore, onDefaultHome, onLegal = { sheet = 2 }, onHidden = { sheet = 3 }, onDismiss = { sheet = 0 })
        if (sheet == 2) InteliaLegal(onSource, onDismiss = { sheet = 0 })
        if (sheet == 3) {
            val hidden by model.hiddenApps.collectAsStateWithLifecycle()
            ModalBottomSheet(onDismissRequest = { sheet = 0 }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                Text(stringResource(R.string.intelia_hidden), Modifier.padding(24.dp), style = MaterialTheme.typography.headlineSmall)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 500.dp), contentPadding = PaddingValues(24.dp)) {
                    if (hidden.isEmpty()) item { Text(stringResource(R.string.intelia_no_hidden)) }
                    items(hidden, key = { it.key }) { app ->
                        ListItem(headlineContent = { Text(app.label) }, trailingContent = { TextButton(onClick = { model.unhide(app) }) { Text(stringResource(R.string.intelia_unhide)) } })
                    }
                }
            }
        }
        if (editor) FlowAliasSheet(initial = editingAlias, targets = targets, error = aliasError, saving = saving,
            onSave = model::save, onDelete = model::delete, onDismiss = model::dismissEditor)
        if (restoreReady) AlertDialog(onDismissRequest = model::cancelRestore,
            title = { Text(stringResource(R.string.intelia_restore_title)) }, text = { Text(stringResource(R.string.intelia_restore_warning)) },
            confirmButton = { TextButton(onClick = model::restore) { Text(stringResource(R.string.intelia_restore)) } },
            dismissButton = { TextButton(onClick = model::cancelRestore) { Text(stringResource(android.R.string.cancel)) } })
        selectedApp?.let { app -> ModalBottomSheet(onDismissRequest = { selectedApp = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Text(app.label, Modifier.padding(24.dp), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { model.toggleArc(app); selectedApp = null }, enabled = app.key in preferences.arc || preferences.arc.size < 5,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(stringResource(if (app.key in preferences.arc) R.string.intelia_arc_remove else R.string.intelia_arc_add)) }
            TextButton(onClick = { model.hide(app); selectedApp = null }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(stringResource(R.string.intelia_hide)) }
            TextButton(onClick = { selectedApp = null; app.openAppDetails(context) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(stringResource(R.string.intelia_details)) }
            Spacer(Modifier.height(24.dp))
        } }
    }
}

@Composable
internal fun InteliaMark(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        drawLine(accent, Offset(size.width * .29f, size.height * .15f), Offset(size.width * .29f, size.height * .85f), size.width * .17f, StrokeCap.Round)
        drawLine(foreground, Offset(size.width * .69f, size.height * .15f), Offset(size.width * .69f, size.height * .65f), size.width * .17f, StrokeCap.Round)
        drawLine(foreground, Offset(size.width * .69f, size.height * .65f), Offset(size.width * .55f, size.height * .85f), size.width * .17f, StrokeCap.Round)
    }
}

@Composable
internal fun HomeGlyph(kind: Int, label: String?) {
    val ink = MaterialTheme.colorScheme.onSurface
    val background = MaterialTheme.colorScheme.surface
    Canvas(Modifier.size(22.dp).semantics { if (label != null) contentDescription = label }) {
        val unit = size.width / 24f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x: Float, y: Float, endX: Float, endY: Float) = drawLine(ink, point(x, y), point(endX, endY), 1.7f * unit, StrokeCap.Round)
        when (kind) {
            0 -> { drawCircle(ink, 6.5f * unit, point(10f, 10f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.7f * unit)); line(15f, 15f, 21f, 21f) }
            1 -> for (x in listOf(4f, 15f)) for (y in listOf(4f, 15f)) {
                drawRoundRect(ink, point(x, y), androidx.compose.ui.geometry.Size(5f * unit, 5f * unit), androidx.compose.ui.geometry.CornerRadius(unit))
            }
            2 -> {
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(13f * unit, 3f * unit); lineTo(6f * unit, 13f * unit); lineTo(12f * unit, 13f * unit); lineTo(11f * unit, 21f * unit); lineTo(18f * unit, 11f * unit); lineTo(12f * unit, 11f * unit); close() }
                drawPath(path, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * unit, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            3 -> { line(20f, 12f, 4f, 12f); line(4f, 12f, 11f, 5f); line(4f, 12f, 11f, 19f) }
            else -> {
                for (y in listOf(5f, 12f, 19f)) line(3f, y, 21f, y)
                for ((x, y) in listOf(8f to 5f, 16f to 12f, 8f to 19f)) {
                    drawCircle(background, 3f * unit, point(x, y))
                    drawCircle(ink, 2.5f * unit, point(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.7f * unit))
                }
            }
        }
    }
}

@Composable
private fun ClockHome(showClock: Boolean, onLibrary: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (isActive) { now = LocalDateTime.now(); delay(60_000L - System.currentTimeMillis() % 60_000L) }
    } }
    var drag by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) {
        detectVerticalDragGestures(onDragStart = { drag = 0f }, onVerticalDrag = { _, amount -> drag += amount }, onDragEnd = { if (drag < -100f) onLibrary() })
    }) {
        val compact = maxHeight < 280.dp
        Column(Modifier.fillMaxSize().padding(top = if (compact) 18.dp else 94.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showClock) {
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = if (compact) 48.sp else 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-3).sp, maxLines = 1)
                Text(now.format(DateTimeFormatter.ofPattern("EEE d. MMMM", Locale.getDefault())), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun CommandPreview(action: FlowAliasAction, onRun: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FLOW", fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                Text(action.label, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium)
            }
            FilledTonalButton(onClick = onRun, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.intelia_run)) }
        }
    }
}

@Composable
private fun FlowLibrary(aliases: List<FlowAlias>, targets: List<FlowAliasTarget>, onCreate: () -> Unit, onEdit: (FlowAlias) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.intelia_commands), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.intelia_flow_hint), modifier = Modifier.padding(top = 10.dp, bottom = 22.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (aliases.isEmpty()) item { Text(stringResource(R.string.intelia_flow_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(aliases, key = { it.id }) { alias ->
                Surface(onClick = { onEdit(alias) }, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(alias.alias, style = MaterialTheme.typography.titleMedium)
                        Text(targets.find { it.key == alias.targetKey }?.label ?: stringResource(R.string.flow_alias_target_unavailable),
                            Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
        }
        FilledTonalButton(onClick = onCreate, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), shape = RoundedCornerShape(20.dp)) {
            Text(stringResource(R.string.flow_alias_create), Modifier.padding(vertical = 6.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(app: Application, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(role = androidx.compose.ui.semantics.Role.Button,
        onLongClickLabel = stringResource(R.string.intelia_app_options), onClick = onClick, onLongClick = onLongClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app, Modifier.size(36.dp))
        Text(app.labelOverride ?: app.label, modifier = Modifier.padding(start = 18.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
    }
}

@Composable
internal fun AppIcon(app: Application, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, app.key) {
        value = withContext(Dispatchers.IO) {
            try { InteliaIconCache.load(context, app) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
            ?: Text(app.label.take(1), fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
    }
}
