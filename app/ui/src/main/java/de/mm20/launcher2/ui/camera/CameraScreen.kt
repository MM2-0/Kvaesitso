package de.mm20.launcher2.ui.camera

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.extensions.ExtensionMode
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import de.mm20.launcher2.ui.component.virtualAppPopup
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberAppBarTitle
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.TelosDotMatrixText
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object CameraRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen() {
    val scanner: DocumentScannerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val scanPages by scanner.pages.collectAsState()
    val scanBusy by scanner.busy.collectAsState()
    var scanMode by remember { mutableStateOf(false) }
    var reviewScan by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("native_camera", 0) }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var front by remember { mutableStateOf(false) }
    var mode by remember { mutableIntStateOf(NativeCamera.MODE_PHOTO) }
    var ready by remember { mutableStateOf(false) }
    var supported by remember { mutableStateOf(emptySet<Int>()) }
    var settings by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(preferences.getBoolean("grid", false)) }
    var histogram by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var discreet by remember { mutableStateOf(false) }
    var keepExif by remember { mutableStateOf(preferences.getBoolean("exif", true)) }
    var audio by remember { mutableStateOf(true) }
    var format by remember { mutableStateOf(preferences.getString("format", "JPEG") ?: "JPEG") }
    var timer by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var bins by remember { mutableStateOf(emptyList<Float>()) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var aeLock by remember { mutableStateOf(false) }
    var whiteBalance by remember { mutableIntStateOf(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO) }
    var manual by remember { mutableStateOf(false) }
    var iso by remember { mutableFloatStateOf(100f) }
    var shutter by remember { mutableFloatStateOf(10f) }
    var exposure by remember { mutableFloatStateOf(0f) }
    var rebind by remember { mutableIntStateOf(0) }
    var faceTrigger by remember { mutableStateOf(false) }
    var motionTrigger by remember { mutableStateOf(false) }
    var geotag by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf(false) }
    var panorama by remember { mutableStateOf(false) }
    val slices = remember { mutableStateListOf<android.net.Uri>() }
    var captureJob by remember { mutableStateOf<Job?>(null) }
    var webcamUrl by remember { mutableStateOf<String?>(null) }
    var webcamStarting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var detectedCode by remember { mutableStateOf<CameraCode?>(null) }
    val preview = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val camera = remember(owner) { NativeCamera(context, owner) }
    var webcam by remember { mutableStateOf<CameraWebcam?>(null) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    fun requestPermission() {
        permissions.launch(buildList { add(Manifest.permission.CAMERA); if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE) }.toTypedArray())
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> audio = granted }
    fun error(value: String) { busy = false; recording = false; message = context.getString(I18nR.string.camera_native_failed, value) }
    fun saved() { busy = false; recording = false; Toast.makeText(context, I18nR.string.camera_native_saved, Toast.LENGTH_SHORT).show() }
    DisposableEffect(camera) {
        onDispose { webcam?.close(); webcam = null; camera.close() }
    }
    DisposableEffect(owner, camera) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) {
            captureJob?.cancel(); countdown = 0; webcam?.close(); webcam = null; webcamUrl = null
            camera.recording?.stop(); recording = false; busy = false
        } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(permission, front, mode, rebind) {
        if (!permission) return@LaunchedEffect
        ready = false; manual = false; zoom = 1f; exposure = 0f
        camera.bind(preview, front, mode, onReady = { supported = it; ready = true }, onHistogram = { bins = it }, onError = ::error)
    }
    LaunchedEffect(manual, iso, shutter, ready, aeLock, whiteBalance) { if (ready) camera.manual(manual, iso.toInt(), shutter, aeLock, whiteBalance) }
    fun freshLocation(): android.location.Location? {
        if (!geotag || !keepExif || format != "JPEG" || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(android.location.LocationManager::class.java)
        return runCatching { manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }
            .filter { System.currentTimeMillis() - it.time in 0..600_000 && it.accuracy <= 500f }
            .minByOrNull { it.accuracy } }.getOrNull()
    }
    fun capture() {
        if (busy || !ready || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (Build.VERSION.SDK_INT <= 28 && !hidden && !raw && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermission(); return
        }
        if (scanMode) {
            if (scanBusy) return
            scanner.prepareCapture { file ->
                if (!scanMode || mode != NativeCamera.MODE_PHOTO || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    scanner.failedCapture(file)
                    return@prepareCapture
                }
                busy = true
                camera.captureDocument(file, onDone = {
                    scanner.acceptCapture(file); busy = false; reviewScan = true
                }, onError = { scanner.failedCapture(file); error(it) })
            }
            return
        }
        if (mode == NativeCamera.MODE_VIDEO) {
            if (recording) { camera.recording?.stop(); return }
            if (audio && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                microphone.launch(Manifest.permission.RECORD_AUDIO); return
            }
            busy = true
            camera.startVideo(audio, flash, onStarted = { busy = false; recording = true }, onDone = { saved() }, onError = ::error)
        } else {
            busy = true
            captureJob = scope.launch {
                for (second in timer downTo 1) { countdown = second; delay(1000) }
                countdown = 0
                if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { busy = false; return@launch }
                val id = camera.cameraId
                if (raw && id != null && camera.rawAvailable) {
                    val rotation = camera.camera?.cameraInfo?.getSensorRotationDegrees(preview.display?.rotation ?: 0) ?: 0
                    camera.unbind()
                    try { withContext(Dispatchers.IO) { CameraRawCapture.capture(context, id, rotation) }; saved() }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (failure: Exception) { error(failure.message.orEmpty()) }
                    finally { rebind++ }
                } else camera.capture(format, keepExif, flash, hidden, freshLocation(), onDone = { uri ->
                    if (panorama && uri != null) { if (slices.size < 8) slices.add(uri); busy = false } else { saved(); if (hidden) Toast.makeText(context, I18nR.string.camera_native_hidden_saved, Toast.LENGTH_LONG).show() }
                }, onError = ::error)
            }
        }
    }
    SideEffect {
        val permitted = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !busy && !recording && ready && mode == NativeCamera.MODE_PHOTO && !raw && !panorama && !scanMode && !reviewScan
        camera.onCode = if (permitted && detectedCode == null && !settings) ({ detectedCode = it }) else null
        camera.onFace = if (faceTrigger && permitted) ({ capture() }) else null
        camera.onMotion = if (motionTrigger && permitted) ({ capture() }) else null
    }
    if (reviewScan) DocumentScannerDialog(scanner, onDismiss = { reviewScan = false }, onCapture = {
        scanMode = true; raw = false; panorama = false; mode = NativeCamera.MODE_PHOTO; reviewScan = false
    })
    detectedCode?.let { code ->
        val webAddress = remember(code.text) {
            runCatching { android.net.Uri.parse(code.text) }.getOrNull()?.takeIf {
                it.scheme?.lowercase(java.util.Locale.ROOT) in setOf("https", "http") && !it.host.isNullOrBlank()
            }
        }
        AlertDialog(
            onDismissRequest = { detectedCode = null },
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.camera_scan_code))  } },
            text = { androidx.compose.foundation.text.selection.SelectionContainer {
                Text(code.text, Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()))
            } },
            confirmButton = { TextButton(onClick = {
                context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText(code.format.name, code.text))
                detectedCode = null
            }) { Text(stringResource(I18nR.string.hc_copy)) } },
            dismissButton = { Row {
                if (webAddress != null) TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, webAddress)) }
                        .onFailure { message = context.getString(I18nR.string.pdft_error_title) }
                    detectedCode = null
                }) { Text(stringResource(I18nR.string.hc_open)) }
                TextButton(onClick = { detectedCode = null }) { Text(stringResource(I18nR.string.hc_close)) }
            } })
    }
    Scaffold(containerColor = Color.Black, contentColor = Color.White, topBar = {
        TopAppBar(
            title = {
                TelosCyberHeading {
                    Column {
                        Text("TELOS", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        TelosDotMatrixText(
                            text = stringResource(
                                when {
                                    recording -> I18nR.string.camera_native_record
                                    scanMode -> I18nR.string.camera_scan_pdf
                                    mode == NativeCamera.MODE_VIDEO -> I18nR.string.camera_mode_video
                                    else -> I18nR.string.camera_mode_photo
                                }
                            ).uppercase(),
                            fontSize = 9.sp,
                            color = if (recording) TelosCyber.NeonMagenta else TelosCyber.NeonCyan,
                        )
                    }
                }
            },
            navigationIcon = {
            if (backStack.size > 1) {
                IconButton(onClick = { backStack.removeLastOrNull() }) { Icon(painterResource(BaseR.drawable.arrow_back_24px), stringResource(I18nR.string.hc_back), tint = Color.White) }
            }
        }, actions = {
            IconButton(onClick = { reviewScan = true }, enabled = !busy && !recording) {
                BadgedBox(badge = { if (scanPages.isNotEmpty()) Badge { Text(scanPages.size.toString()) } }) {
                    Icon(painterResource(BaseR.drawable.description_24px), stringResource(I18nR.string.camera_scan_pdf), tint = Color.White)
                }
            }
            IconButton(onClick = { settings = true }) { Icon(painterResource(BaseR.drawable.settings_24px), stringResource(I18nR.string.camera_native_settings), tint = Color.White) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black))
    }) { padding ->
        if (!permission) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(I18nR.string.camera_need_permission))
                Button(onClick = ::requestPermission) { Text(stringResource(I18nR.string.hc_allow)) }
            }
        } else BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val landscape = maxWidth > maxHeight
            @Composable fun Viewfinder(modifier: Modifier) {
                Box(modifier.background(Color.Black)) {
                    AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().pointerInput(ready) {
                        detectTransformGestures { _, _, scale, _ ->
                            val state = camera.camera?.cameraInfo?.zoomState?.value
                            zoom = (zoom * scale).coerceIn(state?.minZoomRatio ?: 1f, state?.maxZoomRatio ?: 4f)
                            camera.zoom(zoom)
                        }
                    }.pointerInput(ready) { detectTapGestures { camera.focus(preview, it.x, it.y) } })
                    if (discreet) Box(Modifier.fillMaxSize().background(Color.Black))
                    if (grid && !discreet) Canvas(Modifier.fillMaxSize()) {
                        for (third in 1..2) {
                            drawLine(Color.White.copy(alpha = .5f), Offset(size.width * third / 3f, 0f), Offset(size.width * third / 3f, size.height), 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = .5f), Offset(0f, size.height * third / 3f), Offset(size.width, size.height * third / 3f), 1.dp.toPx())
                        }
                    }
                    if (histogram && bins.isNotEmpty() && mode == NativeCamera.MODE_PHOTO && !discreet) Canvas(Modifier.align(Alignment.TopStart).padding(12.dp).size(128.dp, 56.dp).background(Color.Black.copy(alpha = .5f))) {
                        bins.forEachIndexed { index, height -> drawRect(Color.White.copy(alpha = .8f), Offset(index * size.width / bins.size, size.height * (1f - height)), Size(size.width / bins.size, size.height * height)) }
                    }
                    if (detectedCode != null && !discreet) Canvas(Modifier.fillMaxSize()) {
                        drawRect(
                            color = Color(0xFF00FF66),
                            topLeft = Offset(size.width * 0.15f, size.height * 0.25f),
                            size = Size(size.width * 0.7f, size.height * 0.5f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                        )
                    }
                    if (countdown > 0) Text(countdown.toString(), Modifier.align(Alignment.Center), style = MaterialTheme.typography.displayLarge, color = Color.White)
                    message?.let { Text(it, Modifier.align(Alignment.TopCenter).background(Color.Black.copy(alpha = .8f)).padding(12.dp), color = Color.White) }
                    if (!ready) CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
            @Composable fun Controls(modifier: Modifier) {
                Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val modes = buildList<Pair<Int, Int>> {
                            add(NativeCamera.MODE_PHOTO to I18nR.string.camera_mode_photo); add(NativeCamera.MODE_VIDEO to I18nR.string.camera_mode_video)
                            if (ExtensionMode.BOKEH in supported) add(ExtensionMode.BOKEH to I18nR.string.camera_mode_portrait)
                            if (ExtensionMode.NIGHT in supported) add(ExtensionMode.NIGHT to I18nR.string.camera_mode_night)
                        }
                        TelosTechPill(stringResource(I18nR.string.camera_scan_pdf), scanMode, { if (!recording && !busy) { scanMode = !scanMode; raw = false; panorama = false; mode = NativeCamera.MODE_PHOTO } }, accentColor = TelosCyber.NeonMagenta)
                        modes.forEachIndexed { idx, (value, label) ->
                            TelosTechPill(
                                stringResource(label),
                                mode == value && !scanMode,
                                { if (!recording && !busy) { message = null; scanMode = false; mode = value } },
                                accentColor = TelosCyber.accentFor(idx),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${"%.1f".format(zoom)}×", color = Color.White)
                        Slider(value = zoom, onValueChange = { zoom = it; camera.zoom(it) }, modifier = Modifier.weight(1f), valueRange = 1f..(camera.camera?.cameraInfo?.zoomState?.value?.maxZoomRatio?.coerceAtLeast(1.01f) ?: 4f))
                    }
                    if (panorama) {
                        Text(stringResource(I18nR.string.camera_native_panorama_hint), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            busy = true
                            scope.launch {
                                try { withContext(Dispatchers.IO) { CameraPanorama.stitch(context, slices.toList()) }; slices.clear(); saved() }
                                catch (failure: Exception) { error(failure.message.orEmpty()) }
                            }
                        }, enabled = slices.size >= 2 && !busy) { Text(stringResource(I18nR.string.camera_pano_done, slices.size)) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { front = !front }, enabled = !busy && !recording) { Text(stringResource(I18nR.string.camera_flip)) }
                        FilledIconButton(onClick = ::capture, enabled = ready && (!busy || recording), modifier = Modifier.size(72.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (recording) Color.Red else Color.White, contentColor = Color.Black)) {
                            Icon(painterResource(if (recording) de.mm20.launcher2.base.R.drawable.crop_square_24px else de.mm20.launcher2.base.R.drawable.ic_app_camera_fg), stringResource(if (recording) I18nR.string.camera_native_stop else if (mode == NativeCamera.MODE_VIDEO) I18nR.string.camera_native_record else I18nR.string.camera_native_capture), Modifier.size(36.dp))
                        }
                        TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")) } }) { Text(stringResource(I18nR.string.camera_native_gallery)) }
                    }
                }
            }
            if (landscape) Row(Modifier.fillMaxSize()) { Viewfinder(Modifier.weight(1f).fillMaxHeight()); Controls(Modifier.widthIn(max = 300.dp).fillMaxHeight()) }
            else Column(Modifier.fillMaxSize()) { Viewfinder(Modifier.weight(1f).fillMaxWidth()); Controls(Modifier.fillMaxWidth().heightIn(max = 260.dp)) }
        }
    }
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(I18nR.string.camera_native_settings), style = MaterialTheme.typography.titleLarge)
            @Composable fun Toggle(label: Int, value: Boolean, change: (Boolean) -> Unit) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(label), Modifier.weight(1f)); Switch(value, change) }
            }
            if (mode == NativeCamera.MODE_PHOTO) {
                val rawSupported = camera.rawAvailable
                if (rawSupported && !hidden) Toggle(I18nR.string.camera_raw, raw) { raw = it; if (it) panorama = false }
                Toggle(I18nR.string.camera_mode_pano, panorama) { panorama = it; if (it) { raw = false; hidden = false } else slices.clear() }
            }
            if (webcamUrl == null) Button(onClick = {
                webcamStarting = true
                webcam?.close()
                val server = CameraWebcam(context) { preview.bitmap }
                webcam = server
                scope.launch {
                    try {
                        val url = withContext(Dispatchers.IO) { server.start() }
                        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) webcamUrl = url
                        else { server.close(); webcam = null }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        server.close(); throw cancelled
                    } catch (failure: Exception) {
                        server.close(); webcam = null
                        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) error(failure.message.orEmpty())
                    } finally { webcamStarting = false }
                }
            }, enabled = ready && !recording && !busy && !webcamStarting) { Text(stringResource(I18nR.string.camera_webcam_start)) }
            else {
                Text(webcamUrl.orEmpty(), modifier = Modifier.fillMaxWidth())
                Button(onClick = { webcam?.close(); webcam = null; webcamUrl = null }) { Text(stringResource(I18nR.string.camera_webcam_stop)) }
                Text(stringResource(I18nR.string.camera_native_webcam_lan), style = MaterialTheme.typography.bodySmall)
            }
            if (mode == NativeCamera.MODE_PHOTO && !raw && !panorama) {
                if (camera.faceAvailable) Toggle(I18nR.string.camera_native_face_trigger, faceTrigger) { faceTrigger = it }
                if (camera.analysisAvailable) Toggle(I18nR.string.camera_native_motion_trigger, motionTrigger) { motionTrigger = it }
                if (faceTrigger || motionTrigger) Text(stringResource(I18nR.string.camera_native_trigger_hint), style = MaterialTheme.typography.bodySmall)
            }
            if (!raw) {
                Toggle(I18nR.string.camera_hide_folder, hidden) { hidden = it; if (it) panorama = false }
                Toggle(I18nR.string.camera_geotag, geotag) { requested ->
                    geotag = requested
                    if (requested && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                if (geotag) Text(stringResource(I18nR.string.camera_native_geo_hint), style = MaterialTheme.typography.bodySmall)
            }
            Toggle(I18nR.string.camera_grid, grid) { grid = it; preferences.edit().putBoolean("grid", it).apply() }
            if (camera.analysisAvailable) Toggle(I18nR.string.camera_native_histogram, histogram) { histogram = it }
            Toggle(I18nR.string.camera_discreet, discreet) { discreet = it }
            if (discreet) Text(stringResource(I18nR.string.camera_native_discreet_hint), style = MaterialTheme.typography.bodySmall)
            if (camera.camera?.cameraInfo?.hasFlashUnit() == true) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(I18nR.string.camera_native_flash), Modifier.weight(1f)); Switch(flash, { flash = it }, enabled = !recording) }
            if (mode == NativeCamera.MODE_VIDEO) Toggle(I18nR.string.camera_native_audio, audio) { audio = it }
            Text(stringResource(I18nR.string.camera_native_timer))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0, 3, 5, 10).forEach { seconds -> FilterChip(timer == seconds, { timer = seconds }, { Text("${seconds}s") }) } }
            Text(stringResource(I18nR.string.camera_format))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("JPEG", "PNG", "WebP").forEach { value -> FilterChip(format == value, { format = value; preferences.edit().putString("format", value).apply() }, { Text(value) }) } }
            Toggle(I18nR.string.camera_exif, keepExif) { keepExif = it; preferences.edit().putBoolean("exif", it).apply() }
            Text(stringResource(I18nR.string.camera_native_formats), style = MaterialTheme.typography.bodySmall)
            camera.camera?.cameraInfo?.exposureState?.let { state ->
                if (state.isExposureCompensationSupported) {
                    Text(stringResource(I18nR.string.camera_native_exposure))
                    Slider(exposure, { exposure = it; camera.exposure(it.toInt()) }, valueRange = state.exposureCompensationRange.lower.toFloat()..state.exposureCompensationRange.upper.toFloat(), enabled = !manual)
                }
            }
            if (camera.aeLockAvailable) Toggle(I18nR.string.camera_ae_lock, aeLock) { aeLock = it }
            if (camera.awbModes.size > 1) {
                val options = listOf(1 to "Auto", 2 to "2800K", 3 to "4000K", 5 to "5500K", 6 to "6500K", 8 to "7500K").filter { it.first in camera.awbModes }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (value, label) -> FilterChip(whiteBalance == value, { whiteBalance = value }, { Text(stringResource(I18nR.string.camera_wb, if (value == 1) stringResource(I18nR.string.camera_native_auto) else label)) }) }
                }
            }
            if (camera.manualAvailable && !raw) {
                Toggle(I18nR.string.camera_native_auto, !manual) { manual = !it }
                if (manual) {
                    camera.isoRange?.let { range -> Text("${stringResource(I18nR.string.camera_native_iso)} · ${iso.toInt()}"); Slider(iso.coerceIn(range.lower.toFloat(), range.upper.toFloat()), { iso = it }, valueRange = range.lower.toFloat()..range.upper.toFloat()) }
                    camera.exposureRange?.let { range ->
                        val low = (range.lower / 1_000_000f).coerceAtLeast(.01f); val high = (range.upper / 1_000_000f).coerceAtMost(1000f).coerceAtLeast(low + .01f)
                        Text("${stringResource(I18nR.string.camera_native_shutter)} · ${"%.1f".format(shutter)}"); Slider(shutter.coerceIn(low, high), { shutter = it }, valueRange = low..high)
                    }
                }
            }
            Text(stringResource(I18nR.string.camera_native_capabilities), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }
}
