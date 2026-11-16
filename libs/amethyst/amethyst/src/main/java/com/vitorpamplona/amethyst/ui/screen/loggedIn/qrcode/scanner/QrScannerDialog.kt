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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.SystemClock
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.vitorpamplona.amethyst.commons.qrcode.ImageCodesOutcome
import com.vitorpamplona.amethyst.commons.qrcode.ScanResult
import com.vitorpamplona.amethyst.commons.qrcode.classifyScannedPayload
import com.vitorpamplona.amethyst.commons.qrcode.ui.QrImageCodeChooser
import com.vitorpamplona.amethyst.commons.qrcode.ui.ScanOutcomeSheet
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.point_to_the_qr_code
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_camera_blocked
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_camera_rationale
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_clipboard_empty
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_grant_camera
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_no_code_in_image
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_open_settings
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_unavailable
import com.vitorpamplona.amethyst.commons.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.ScanOutcome
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.ui.call.openAppSettings
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen QR scanner.
 *
 * A dialog rather than a navigation route on purpose: one of its callers is the logged-out login
 * field, which lives outside the navigation graph entirely.
 */
@Composable
fun QrCodeScannerDialog(
    onDismiss: () -> Unit,
    onScan: (String) -> ScanOutcome,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        SetDialogToEdgeToEdge()
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            QrScannerScreen(onDismiss = onDismiss, onScan = onScan)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun QrScannerScreen(
    onDismiss: () -> Unit,
    onScan: (String) -> ScanOutcome,
) {
    // Set from the permission result, not when the request is launched: `shouldShowRationale` is
    // false both before the first answer and after a permanent denial, so flipping this before
    // the system dialog returned showed "camera blocked, open settings" behind the very first ask.
    var answered by rememberSaveable { mutableStateOf(false) }
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA) { answered = true }

    if (cameraPermission.status.isGranted) {
        QrCameraScanner(onDismiss = onDismiss, onScan = onScan)
    } else {
        CameraPermissionGate(permission = cameraPermission, answered = answered, onDismiss = onDismiss)
    }
}

/**
 * Asks for the camera, and explains itself when refused.
 *
 * The old scanner simply closed its activity when the permission was denied, which from the
 * user's side is a button that does nothing.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun CameraPermissionGate(
    permission: PermissionState,
    answered: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        permission.launchPermissionRequest()
    }

    // `shouldShowRationale` is false both before the first answer and after a permanent denial,
    // so the two are only distinguishable once a request has actually come back.
    val blocked = answered && !permission.status.shouldShowRationale

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                if (blocked) {
                    stringRes(Res.string.qr_scanner_camera_blocked)
                } else {
                    stringRes(Res.string.qr_scanner_camera_rationale)
                },
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )

        if (blocked) {
            Button(onClick = { openAppSettings(context) }) {
                Text(stringRes(Res.string.qr_scanner_open_settings))
            }
        } else {
            Button(onClick = { permission.launchPermissionRequest() }) {
                Text(stringRes(Res.string.qr_scanner_grant_camera))
            }
        }

        // Closes the scanner, so it says so: with the camera refused there is nothing to scan again.
        TextButton(onClick = onDismiss) {
            Text(stringRes(Res.string.close), color = Color.White)
        }
    }
}

@Composable
private fun QrCameraScanner(
    onDismiss: () -> Unit,
    onScan: (String) -> ScanOutcome,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val state = remember { QrScannerState() }
    val decoder = remember { runCatching { ZxingCppBarcodeDecoder() }.getOrNull() }

    val currentOnScan by rememberUpdatedState(onScan)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val noCodeInImage = stringRes(Res.string.qr_scanner_no_code_in_image)
    val clipboardEmpty = stringRes(Res.string.qr_scanner_clipboard_empty)
    val decoderUnavailable = stringRes(Res.string.qr_scanner_unavailable)

    // One shared accept path: camera frames, a tapped candidate and an imported image all land
    // here, so the haptic, the dedupe and the "we can't open this" branch behave identically
    // however the payload arrived.
    val submit: (String) -> Unit = { scanned ->
        // Trimmed once here so every caller gets what was classified: a code ending in a newline
        // classified fine but then failed to parse downstream.
        val text = scanned.trim()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when (currentOnScan(text)) {
            ScanOutcome.Handled -> currentOnDismiss()
            ScanOutcome.NotSupported -> state.onRejected(classifyScannedPayload(text))
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    // CONFLATED: the analysis thread outruns the UI, and an old frame is worthless — the only
    // one worth acting on is the newest.
    val frames = remember { Channel<FrameScan>(Channel.CONFLATED) }

    val previewView =
        remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    var analysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var cameraOpens by remember { mutableIntStateOf(0) }

    // Read on the analysis thread, so it is a plain volatile flag mirrored from the snapshot
    // state rather than the state itself.
    val paused = remember { AtomicBoolean(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.isAwaitingUser }.collect { paused.set(it) }
    }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var focusRing by remember { mutableStateOf<Offset?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            frames.close()
            runCatching { provider?.unbindAll() }
        }
    }

    LaunchedEffect(decoder) {
        if (decoder == null) state.notice = decoderUnavailable
    }

    // Bind once the preview has a size: ViewPort needs a laid-out view, and binding both use
    // cases under one viewport is what makes the overlay's coordinate mapping exact.
    LaunchedEffect(decoder, viewSize) {
        if (decoder == null || viewSize == IntSize.Zero) return@LaunchedEffect

        // A rebind restarts the camera. Rotation needs one (the viewport changes shape), but a
        // freeform or laptop window being dragged reports a new size every frame, so wait for
        // the size to settle; each new size restarts this effect and cancels the wait.
        if (camera != null) delay(RESIZE_SETTLE_MS)

        val cameraProvider =
            try {
                ProcessCameraProvider.awaitInstance(context)
            } catch (e: CancellationException) {
                // The size changed again while CameraX was still initialising. Not a failure.
                throw e
            } catch (e: Exception) {
                Log.w("QrScanner", "Camera provider unavailable", e)
                state.notice = decoderUnavailable
                return@LaunchedEffect
            }
        provider = cameraProvider

        val newPreview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }

        val newAnalysis =
            ImageAnalysis
                .Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setResolutionSelector(ANALYSIS_RESOLUTION)
                .build()
                .apply {
                    setAnalyzer(analysisExecutor, QrFrameAnalyzer(decoder, isPaused = paused::get) { frames.trySend(it) })
                }

        val group =
            UseCaseGroup
                .Builder()
                .addUseCase(newPreview)
                .addUseCase(newAnalysis)
                .apply { previewView.viewPort?.let { setViewPort(it) } }
                .build()

        try {
            cameraProvider.unbindAll()
            camera =
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group).also {
                    state.torchAvailable = it.cameraInfo.hasFlashUnit()
                    state.maxZoomRatio = it.cameraInfo.zoomState.value
                        ?.maxZoomRatio ?: 1f
                }
            preview = newPreview
            analysis = newAnalysis
        } catch (e: Exception) {
            Log.w("QrScanner", "Could not bind the camera", e)
            state.notice = decoderUnavailable
        }
    }

    LaunchedEffect(Unit) {
        for (scan in frames) {
            state.onFrame(scan, SystemClock.elapsedRealtime())?.let(submit)
        }
    }

    // CameraX resets zoom to 1x and the torch to off whenever the camera closes -- a rebind, or
    // the app going to the background -- and hands back the same Camera object afterwards, so
    // keying on `camera` alone never noticed. Counting opens re-applies what the UI is showing
    // each time the camera comes back.
    DisposableEffect(camera, lifecycleOwner) {
        val cameraState = camera?.cameraInfo?.cameraState
        val observer = Observer<CameraState> { if (it.type == CameraState.Type.OPEN) cameraOpens++ }
        cameraState?.observe(lifecycleOwner, observer)
        onDispose { cameraState?.removeObserver(observer) }
    }

    // One writer each for torch and zoom, driven off state rather than from the gesture handlers,
    // so the auto-zoom sweep and a pinch cannot fight over the camera control.
    LaunchedEffect(camera, cameraOpens) {
        val control = camera?.cameraControl ?: return@LaunchedEffect
        snapshotFlow { state.torchOn }.collect { runCatching { control.enableTorch(it) } }
    }

    LaunchedEffect(camera, cameraOpens) {
        val control = camera?.cameraControl ?: return@LaunchedEffect
        snapshotFlow { state.zoomRatio }.collect { runCatching { control.setZoomRatio(it) } }
    }

    // A flip between landscape and reverse landscape keeps the view's size, so nothing above
    // rebinds -- but the frames now arrive upside down relative to the screen, and the outlines
    // and tap targets were drawn mirrored through the centre. Follow the display instead.
    DisposableEffect(preview, analysis) {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val listener =
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = Unit

                override fun onDisplayRemoved(displayId: Int) = Unit

                override fun onDisplayChanged(displayId: Int) {
                    val display = previewView.display ?: return
                    if (display.displayId != displayId) return
                    preview?.targetRotation = display.rotation
                    analysis?.targetRotation = display.rotation
                }
            }
        displayManager?.registerDisplayListener(listener, null)
        onDispose { displayManager?.unregisterDisplayListener(listener) }
    }

    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(NOTICE_DURATION_MS)
            state.notice = null
        }
    }

    LaunchedEffect(focusRing) {
        if (focusRing != null) {
            delay(FOCUS_RING_DURATION_MS)
            focusRing = null
        }
    }

    // One picture can hold several codes. Taking the first silently is the same mistake the
    // camera refuses to make, so anything past one goes to the picker. Multi-part codes are
    // joined first, so a fragment is never offered or submitted on its own.
    val readImage: (Uri) -> Unit = { uri ->
        if (decoder != null) {
            scope.launch {
                when (val outcome = QrImageImport.outcome(context, uri, decoder)) {
                    ImageCodesOutcome.NothingFound -> state.notice = noCodeInImage
                    is ImageCodesOutcome.OnlyPartial -> state.notice = QrImageImport.partialProgressText(outcome.captured, outcome.total)
                    is ImageCodesOutcome.Open -> submit(outcome.text)
                    is ImageCodesOutcome.Choose -> {
                        state.imageCodesNote = QrImageImport.partialNote(outcome.partial)
                        state.imageCodes = outcome.texts.map(::classifyScannedPayload)
                    }
                }
            }
        }
    }

    val pickImage =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) readImage(uri)
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            state.zoomRatio = (state.zoomRatio * zoom).coerceIn(1f, maxOf(1f, state.maxZoomRatio))
                        }
                    }
                }.pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val picked = pickCandidateAt(tap, state, viewSize)
                        if (picked != null) {
                            state.onCandidateTapped(picked, SystemClock.elapsedRealtime())?.let(submit)
                        } else {
                            focusRing = tap
                            focusAt(previewView, camera, tap)
                        }
                    }
                },
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        ScanOverlayCanvas(state = state, viewSize = viewSize, focusRing = focusRing)

        Text(
            text = stringRes(Res.string.point_to_the_qr_code),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp).fillMaxWidth(0.8f),
        )

        QrScannerControls(
            state = state,
            onClose = onDismiss,
            onToggleTorch = { state.torchOn = !state.torchOn },
            onPickImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPaste = {
                scope.launch {
                    pasteFromClipboard(
                        context = context,
                        decoder = decoder,
                        onText = submit,
                        onImage = readImage,
                        onEmpty = { state.notice = clipboardEmpty },
                    )
                }
            },
        )
    }

    if (state.imageCodes.isNotEmpty()) {
        QrImageCodeChooser(
            codes = state.imageCodes,
            note = state.imageCodesNote,
            onPick = { picked ->
                state.imageCodes = emptyList()
                submit(picked.raw)
            },
            onDismiss = { state.imageCodes = emptyList() },
        )
    }

    state.rejected?.let { payload ->
        ScanOutcomeSheet(
            payload = payload,
            onDismiss = { state.dismissRejection(SystemClock.elapsedRealtime()) },
            onOpenLink = { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                state.dismissRejection(SystemClock.elapsedRealtime())
                currentOnDismiss()
            },
            onCopy = { text ->
                copyToClipboard(context, text)
                state.dismissRejection(SystemClock.elapsedRealtime())
            },
            onOpenProfile = { npub ->
                state.dismissRejection(SystemClock.elapsedRealtime())
                submit(npub)
            },
        )
    }
}

/** Draws the aiming brackets, any codes we can see, and the tap-to-focus ring. */
@Composable
private fun ScanOverlayCanvas(
    state: QrScannerState,
    viewSize: IntSize,
    focusRing: Offset?,
) {
    val highlight = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val mapping = ScanViewMapping.of(state.frame, viewSize)

        if (state.candidates.isEmpty()) {
            drawViewfinderBrackets(Color.White.copy(alpha = 0.65f))
        } else if (mapping != null) {
            state.candidates.forEach { candidate ->
                candidate.bounds?.let {
                    drawPath(
                        path = it.toViewPath(mapping),
                        color = highlight,
                        style = Stroke(width = 4.dp.toPx()),
                    )
                }
            }
        }

        focusRing?.let {
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = 28.dp.toPx(),
                center = it,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

/** The visible code nearest the tap, or null when the tap was not on one. */
private fun pickCandidateAt(
    tap: Offset,
    state: QrScannerState,
    viewSize: IntSize,
): ScanResult? {
    if (state.candidates.size <= 1) return null
    val mapping = ScanViewMapping.of(state.frame, viewSize) ?: return null

    // Parts of a multi-part code are outlined so the user can see them being read, but each is a
    // fragment of something else: tapping one must not submit it.
    return state.candidates
        .filterNot { it.isPartOfSequence }
        .mapNotNull { candidate ->
            val bounds = candidate.bounds ?: return@mapNotNull null
            val center = bounds.centerInView(mapping)
            // In view pixels, to match `distance`. bounds.longestSide is image-space.
            val radius = maxOf(bounds.longestSideInView(mapping), MIN_TAP_RADIUS_PX)
            val distance = (center - tap).getDistance()
            if (distance <= radius) candidate to distance else null
        }.minByOrNull { it.second }
        ?.first
}

private fun focusAt(
    previewView: PreviewView,
    camera: Camera?,
    tap: Offset,
) {
    val control = camera?.cameraControl ?: return
    runCatching {
        val point = previewView.meteringPointFactory.createPoint(tap.x, tap.y)
        control.startFocusAndMetering(
            FocusMeteringAction
                .Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(FOCUS_AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
                .build(),
        )
    }
}

/**
 * Reads the clipboard off the main thread: the MIME lookup is an IPC to the clip's provider, and
 * coercing a content-URI clip to text reads its stream. The callbacks run back on the caller's.
 */
private suspend fun pasteFromClipboard(
    context: Context,
    decoder: BarcodeDecoder?,
    onText: (String) -> Unit,
    onImage: (Uri) -> Unit,
    onEmpty: () -> Unit,
) {
    val image = withContext(Dispatchers.IO) { QrImageImport.clipboardImage(context) }
    if (image != null && decoder != null) {
        onImage(image)
        return
    }

    val text = withContext(Dispatchers.IO) { QrImageImport.clipboardText(context) }
    if (!text.isNullOrBlank()) onText(text) else onEmpty()
}

/** Shared with the shared-image scan screen, which offers the same action on the same sheet. */
internal fun copyToClipboard(
    context: Context,
    text: String,
) {
    runCatching {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("", text))
    }
}

/**
 * 1280x720 is the sweet spot: plenty of pixels per module for a code at arm's length, while
 * staying inside what every device can sustain at full frame rate through an analysis pipeline.
 * Falling back higher before lower keeps detail on devices that cannot produce exactly this.
 */
private val ANALYSIS_RESOLUTION =
    ResolutionSelector
        .Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
        ).build()

private const val NOTICE_DURATION_MS = 3_000L
private const val RESIZE_SETTLE_MS = 300L
private const val FOCUS_RING_DURATION_MS = 800L
private const val FOCUS_AUTO_CANCEL_SECONDS = 4L
private const val MIN_TAP_RADIUS_PX = 120f
