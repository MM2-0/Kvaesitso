package de.mm20.launcher2.ui.tools

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.app.Activity
import android.content.Context
import android.widget.Toast
import android.content.pm.ActivityInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.component.TelosDotMatrixText
import de.mm20.launcher2.ui.component.TelosRadialGauge
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.telosTechCard
import de.mm20.launcher2.ui.layout.AdaptiveLayoutPolicy
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable

@Serializable
data object FlashlightRoute : NavKey

enum class FlashlightTarget(val label: String) {
    Flash("Camera Flash"),
    Screen("Screen Light"),
    Both("Flash + Screen")
}

enum class FlashlightOutputMode(val label: String) {
    LightOnly("Light Only"),
    SoundOnly("Sound Only"),
    LightAndSound("Light + Sound")
}

val SCREEN_COLORS = listOf(
    "White" to Color.White,
    "Red" to Color(0xFFFF0000),
    "Blue" to Color(0xFF0000FF),
    "Amber" to Color(0xFFFFBF00),
    "Green" to Color(0xFF00FF00)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashlightScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()

    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val cameraId = remember {
        try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    var isTorchOn by remember { mutableStateOf(false) }
    var targetMode by rememberSaveable { mutableStateOf(if (cameraId == null) FlashlightTarget.Screen else FlashlightTarget.Flash) }
    var allowLandscape by rememberSaveable { mutableStateOf(false) }

    var screenBrightness by remember { mutableFloatStateOf(1.0f) }
    var selectedScreenColor by remember { mutableStateOf(Color.White) }
    var isScreenFlashOn by remember { mutableStateOf(false) }

    var torchStrength by remember { mutableFloatStateOf(1f) }
    var maxStrength by remember { mutableIntStateOf(1) }

    var outputMode by rememberSaveable { mutableStateOf(FlashlightOutputMode.LightAndSound) }
    var strobeFreqHz by remember { mutableFloatStateOf(0f) }
    var morseText by rememberSaveable { mutableStateOf("SOS") }
    var isMorsePlaying by remember { mutableStateOf(false) }

    var isSirenPlaying by remember { mutableStateOf(false) }
    var sirenAlternatingColors by remember { mutableStateOf(true) }

    var morseJob by remember { mutableStateOf<Job?>(null) }
    var strobeJob by remember { mutableStateOf<Job?>(null) }
    var sirenJob by remember { mutableStateOf<Job?>(null) }

    val toneGenerator = remember {
        try { ToneGenerator(AudioManager.STREAM_MUSIC, 100) } catch (_: Exception) { null }
    }

    LaunchedEffect(toneGenerator) { if (toneGenerator == null) outputMode = FlashlightOutputMode.LightOnly }

    DisposableEffect(isMorsePlaying) {
        val active = isMorsePlaying
        val alreadyKept = activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (active) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (active && !alreadyKept) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    DisposableEffect(activity) {
        val previousStream = activity?.volumeControlStream ?: AudioManager.USE_DEFAULT_STREAM_TYPE
        activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        onDispose { activity?.volumeControlStream = previousStream }
    }

    DisposableEffect(allowLandscape, activity) {
        val previous = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = if (allowLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            activity?.requestedOrientation = previous
        }
    }

    // Hardware Torch Strength (Dimming) API 33+
    LaunchedEffect(cameraId) {
        if (cameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val chars = cameraManager.getCameraCharacteristics(cameraId)
                maxStrength = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            } catch (_: Exception) {}
        }
    }

    fun setHardwareTorch(on: Boolean, strengthLevel: Int = 1) {
        if (cameraId == null) return
        try {
            if (on) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxStrength > 1) {
                    cameraManager.turnOnTorchWithStrengthLevel(cameraId, strengthLevel.coerceIn(1, maxStrength))
                } else {
                    cameraManager.setTorchMode(cameraId, true)
                }
            } else {
                cameraManager.setTorchMode(cameraId, false)
            }
        } catch (_: Exception) {
            if (targetMode == FlashlightTarget.Flash) isTorchOn = false
        }
    }

    val originalBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
    fun applyScreenBrightness(brightness: Float) {
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = if (brightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) originalBrightness else brightness.coerceIn(0.01f, 1.0f)
        }
    }

    var signalGeneration by remember { mutableIntStateOf(0) }
    fun stopSignals() {
        signalGeneration++
        morseJob?.cancel(); strobeJob?.cancel(); sirenJob?.cancel()
        toneGenerator?.stopTone()
        setHardwareTorch(false)
        isTorchOn = false; isScreenFlashOn = false
        isMorsePlaying = false; isSirenPlaying = false; strobeFreqHz = 0f
        applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    fun startMorse(code: String, repeat: Boolean) {
        stopSignals()
        if (code.isBlank()) return
        val generation = signalGeneration
        val mode = outputMode
        val target = targetMode
        val screen = mode != FlashlightOutputMode.SoundOnly && target != FlashlightTarget.Flash
        isMorsePlaying = true
        val previous = morseJob
        morseJob = scope.launch {
            previous?.join()
            isMorsePlaying = true
            try {
                if (screen) applyScreenBrightness(screenBrightness)
                val pulses = withContext(Dispatchers.Default) { MorseCodec.pulses(code) }
                do {
                    for (pulse in pulses) {
                        ensureActive()
                        if (mode != FlashlightOutputMode.SoundOnly) {
                            if (target != FlashlightTarget.Screen) setHardwareTorch(pulse.on)
                            if (screen) isScreenFlashOn = pulse.on
                        }
                        if (mode != FlashlightOutputMode.LightOnly) {
                            if (pulse.on) toneGenerator?.startTone(ToneGenerator.TONE_DTMF_0)
                            else toneGenerator?.stopTone()
                        }
                        delay(pulse.units * 100L)
                    }
                    toneGenerator?.stopTone(); setHardwareTorch(false); isScreenFlashOn = false
                    if (repeat) delay(700L)
                } while (repeat && isActive)
            } finally {
                if (generation == signalGeneration) {
                    toneGenerator?.stopTone(); setHardwareTorch(false)
                    isScreenFlashOn = false; isMorsePlaying = false
                    applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                }
            }
        }
    }

    // Auto-switch Light Target Sources
    fun switchLightTarget(newTarget: FlashlightTarget) {
        targetMode = newTarget
        if (isTorchOn) {
            // Turn off camera flash if not needed
            if (newTarget != FlashlightTarget.Flash && newTarget != FlashlightTarget.Both) {
                setHardwareTorch(false)
            } else {
                setHardwareTorch(true, (torchStrength * maxStrength).toInt())
            }

            // Turn off screen light if not needed
            if (newTarget != FlashlightTarget.Screen && newTarget != FlashlightTarget.Both) {
                applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                isScreenFlashOn = false
            } else {
                applyScreenBrightness(screenBrightness)
                isScreenFlashOn = true
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        fun stopOutputs() {
            signalGeneration++
            strobeJob?.cancel()
            morseJob?.cancel()
            sirenJob?.cancel()
            toneGenerator?.stopTone()
            setHardwareTorch(false)
            applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            isTorchOn = false
            isScreenFlashOn = false
            isMorsePlaying = false
            isSirenPlaying = false
            strobeFreqHz = 0f
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stopOutputs()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopOutputs()
            strobeJob?.cancel()
            morseJob?.cancel()
            sirenJob?.cancel()
            toneGenerator?.release()
            setHardwareTorch(false)
            applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
    }

    val neonCyan = Color(0xFF00F0FF)
    val neonGreen = Color(0xFF00E676)
    val neonOrange = Color(0xFFFF9100)
    val neonMagenta = Color(0xFFFF007F)
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Fullscreen Screen Light Overlay
    if (isScreenFlashOn || (isMorsePlaying && outputMode != FlashlightOutputMode.SoundOnly && targetMode != FlashlightTarget.Flash)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isScreenFlashOn) selectedScreenColor else Color.Black)
                .clickable { stopSignals() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(I18nR.string.app_polish_exit_light),
                color = if (isScreenFlashOn && selectedScreenColor == Color.White) Color.Black else Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TelosCyberHeading {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(I18nR.string.telos_flashlight_app_label), color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(8.dp))
                            TelosDotMatrixText(
                                if (isTorchOn || isScreenFlashOn) stringResource(I18nR.string.net_hub_state_on)
                                else stringResource(I18nR.string.hc_off),
                                fontSize = 9.sp,
                                color = if (isTorchOn || isScreenFlashOn) neonGreen else neonCyan,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (backStack.size > 1) {
                        IconButton(onClick = { backStack.removeLastOrNull() }) {
                            Icon(painterResource(BaseR.drawable.arrow_back_24px), contentDescription = stringResource(I18nR.string.hc_back), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var showFontDialog by remember { mutableStateOf(false) }
                        if (showFontDialog) {
                            de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
                                appKey = "flashlight",
                                appName = stringResource(I18nR.string.telos_flashlight_app_label),
                                onDismiss = { showFontDialog = false }
                            )
                        }
                        IconButton(onClick = { showFontDialog = true }) {
                            Icon(painterResource(BaseR.drawable.settings_24px), contentDescription = stringResource(I18nR.string.per_app_font_size), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(stringResource(I18nR.string.desktop_mode_orientation_landscape), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Switch(
                            checked = allowLandscape,
                            onCheckedChange = { allowLandscape = it },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .testTag("flashlight-controls")
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            val sideBySide = AdaptiveLayoutPolicy.sideBySide(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale)
            val shortHeight = maxHeight < 420.dp
            val primaryColor = MaterialTheme.colorScheme.primary
            val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
            val isActive = isTorchOn || isScreenFlashOn

            @Composable
            fun PowerCluster(clusterMod: Modifier = Modifier) {
                Column(
                    modifier = clusterMod.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (shortHeight) 72.dp else 110.dp)
                            .telosTechCard(borderColor = if (isActive) neonCyan else neonMagenta, alpha = if (isActive) 0.7f else 0.4f),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TelosRadialGauge(
                                progress = torchStrength,
                                valueText = "${(torchStrength * 100).roundToInt()}%",
                                labelText = stringResource(I18nR.string.hc_brightness),
                                size = if (shortHeight) 72.dp else 96.dp,
                                accentColor = neonCyan
                            )
                            IconButton(
                                onClick = {
                                    val on = !isTorchOn
                                    stopSignals()
                                    isTorchOn = on
                                    if (targetMode == FlashlightTarget.Flash || targetMode == FlashlightTarget.Both) {
                                        setHardwareTorch(isTorchOn, (torchStrength * maxStrength).roundToInt().coerceAtLeast(1))
                                    }
                                    if (targetMode == FlashlightTarget.Screen || targetMode == FlashlightTarget.Both) {
                                        if (isTorchOn) {
                                            applyScreenBrightness(screenBrightness)
                                            isScreenFlashOn = true
                                        } else {
                                            applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                                            isScreenFlashOn = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(if (shortHeight) 88.dp else 112.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) primaryColor else surfaceColor)
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures { change, dragAmount ->
                                            change.consume()
                                            val delta = -dragAmount / 400f
                                            torchStrength = (torchStrength + delta).coerceIn(0.1f, 1.0f)
                                            if (isTorchOn) {
                                                setHardwareTorch(true, (torchStrength * maxStrength).roundToInt().coerceAtLeast(1))
                                            }
                                        }
                                    }
                            ) {
                                Icon(
                                    painter = painterResource(BaseR.drawable.light_mode_24px),
                                    contentDescription = stringResource(I18nR.string.telos_flashlight_app_label),
                                    modifier = Modifier.size(if (shortHeight) 40.dp else 52.dp),
                                    tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FlashlightTarget.entries.forEach { target ->
                            val enabled = cameraId != null || target == FlashlightTarget.Screen
                            if (!enabled) return@forEach
                            TelosTechPill(
                                text = stringResource(when (target) {
                                    FlashlightTarget.Flash -> I18nR.string.camera_native_flash
                                    FlashlightTarget.Screen -> I18nR.string.app_polish_screen_light
                                    FlashlightTarget.Both -> I18nR.string.preference_wallpaper_set_both
                                }),
                                isSelected = targetMode == target,
                                onClick = { stopSignals(); switchLightTarget(target) },
                                accentColor = neonGreen
                            )
                        }
                    }
                    if (targetMode != FlashlightTarget.Flash) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .telosTechCard(borderColor = neonOrange, alpha = 0.35f)
                                .padding(12.dp)
                        ) {
                            TelosDotMatrixText(stringResource(I18nR.string.app_polish_screen_light), fontSize = 10.sp, color = neonOrange)
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(SCREEN_COLORS) { (_, color) ->
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .clickable { selectedScreenColor = color },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selectedScreenColor == color) {
                                            Icon(painterResource(BaseR.drawable.check_24px), contentDescription = null, tint = if (color == Color.White) Color.Black else Color.White)
                                        }
                                    }
                                }
                            }
                            Slider(
                                value = screenBrightness,
                                onValueChange = {
                                    screenBrightness = it
                                    applyScreenBrightness(it)
                                }
                            )
                        }
                    }
                    Button(
                        onClick = { showAdvanced = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        Text(stringResource(I18nR.string.hc_more))
                    }
                }
            }

            if (sideBySide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PowerCluster(Modifier.weight(1f).fillMaxHeight())
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .telosTechCard(borderColor = neonMagenta, alpha = 0.35f)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelosDotMatrixText(stringResource(I18nR.string.app_polish_strobe), fontSize = 10.sp, color = neonMagenta)
                        Slider(
                            value = strobeFreqHz,
                            onValueChange = { hz ->
                                stopSignals()
                                strobeFreqHz = hz
                                if (hz > 0f) {
                                    isTorchOn = false
                                    strobeJob = scope.launch {
                                        val intervalMs = (1000f / hz / 2f).toLong().coerceAtLeast(20L)
                                        var state = false
                                        while (isActive) {
                                            state = !state
                                            if (targetMode == FlashlightTarget.Flash || targetMode == FlashlightTarget.Both) {
                                                setHardwareTorch(state)
                                            }
                                            delay(intervalMs)
                                        }
                                    }
                                } else {
                                    setHardwareTorch(false)
                                }
                            },
                            valueRange = 0f..15f
                        )
                        Text("${strobeFreqHz.toInt()} Hz", color = MaterialTheme.colorScheme.onSurface)
                        Button(
                            onClick = {
                                if (isSirenPlaying) {
                                    sirenJob?.cancel()
                                    isSirenPlaying = false
                                    setHardwareTorch(false)
                                    applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                                } else {
                                    stopSignals()
                                    isSirenPlaying = true
                                    sirenJob = scope.launch {
                                        playSiren(
                                            target = targetMode,
                                            alternating = sirenAlternatingColors,
                                            toneGenerator = toneGenerator,
                                            setTorch = { setHardwareTorch(it) }
                                        )
                                        isSirenPlaying = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSirenPlaying) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(if (isSirenPlaying) stringResource(I18nR.string.hc_stop) else stringResource(I18nR.string.app_polish_siren), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                PowerCluster(Modifier.fillMaxSize())
            }

            if (showAdvanced) {
                ModalBottomSheet(
                    onDismissRequest = { showAdvanced = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    FlashlightAdvancedSheet(
                        outputMode = outputMode,
                        onOutputMode = { stopSignals(); outputMode = it },
                        toneGeneratorReady = toneGenerator != null,
                        morseText = morseText,
                        onMorseText = { morseText = it.take(2000) },
                        isMorsePlaying = isMorsePlaying,
                        onPlayMorse = { code, repeat -> if (isMorsePlaying) stopSignals() else startMorse(code, repeat) },
                        onSos = {
                            morseText = "SOS"
                            startMorse("... --- ...", true)
                        },
                        strobeFreqHz = strobeFreqHz,
                        onStrobe = { hz ->
                            stopSignals()
                            strobeFreqHz = hz
                            if (hz > 0f) {
                                isTorchOn = false
                                strobeJob = scope.launch {
                                    val intervalMs = (1000f / hz / 2f).toLong().coerceAtLeast(20L)
                                    var state = false
                                    while (isActive) {
                                        state = !state
                                        if (targetMode == FlashlightTarget.Flash || targetMode == FlashlightTarget.Both) {
                                            setHardwareTorch(state)
                                        }
                                        delay(intervalMs)
                                    }
                                }
                            } else setHardwareTorch(false)
                        },
                        isSirenPlaying = isSirenPlaying,
                        sirenAlternatingColors = sirenAlternatingColors,
                        onSirenAlternating = { sirenAlternatingColors = it },
                        onSiren = {
                            if (isSirenPlaying) {
                                sirenJob?.cancel()
                                isSirenPlaying = false
                                setHardwareTorch(false)
                                applyScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                            } else {
                                stopSignals()
                                isSirenPlaying = true
                                sirenJob = scope.launch {
                                    playSiren(
                                        target = targetMode,
                                        alternating = sirenAlternatingColors,
                                        toneGenerator = toneGenerator,
                                        setTorch = { setHardwareTorch(it) }
                                    )
                                    isSirenPlaying = false
                                }
                            }
                        },
                        stopSignals = { stopSignals() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlashlightAdvancedSheet(
    outputMode: FlashlightOutputMode,
    onOutputMode: (FlashlightOutputMode) -> Unit,
    toneGeneratorReady: Boolean,
    morseText: String,
    onMorseText: (String) -> Unit,
    isMorsePlaying: Boolean,
    onPlayMorse: (String, Boolean) -> Unit,
    onSos: () -> Unit,
    strobeFreqHz: Float,
    onStrobe: (Float) -> Unit,
    isSirenPlaying: Boolean,
    sirenAlternatingColors: Boolean,
    onSirenAlternating: (Boolean) -> Unit,
    onSiren: () -> Unit,
    stopSignals: () -> Unit,
) {
    val neonCyan = Color(0xFF00F0FF)
    val neonOrange = Color(0xFFFF9100)
    val neonMagenta = Color(0xFFFF007F)
    val conversion by produceState<Pair<String, MorseCodec.Encoded>?>(null, morseText) {
        val input = morseText
        value = input to withContext(Dispatchers.IO) { MorseCodec.encode(input) }
    }
    val encoded = conversion?.takeIf { it.first == morseText }?.second
    val morseCode = encoded?.code.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp)
            .testTag("flashlight-advanced"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TelosDotMatrixText(stringResource(I18nR.string.hc_more), fontSize = 11.sp, color = neonCyan)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .telosTechCard(borderColor = neonOrange, alpha = 0.4f)
                .padding(12.dp)
        ) {
            Text(stringResource(I18nR.string.app_polish_strobe), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Slider(value = strobeFreqHz, onValueChange = onStrobe, valueRange = 0f..15f)
            Text("${strobeFreqHz.toInt()} Hz", color = MaterialTheme.colorScheme.onSurface)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .telosTechCard(borderColor = MaterialTheme.colorScheme.error, alpha = 0.5f)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(I18nR.string.app_polish_siren), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sirenAlternatingColors, onCheckedChange = onSirenAlternating)
                Text(stringResource(I18nR.string.app_polish_alternating), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = onSiren,
                colors = ButtonDefaults.buttonColors(containerColor = if (isSirenPlaying) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(if (isSirenPlaying) stringResource(I18nR.string.hc_stop) else stringResource(I18nR.string.app_polish_siren), fontWeight = FontWeight.Bold)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .telosTechCard(borderColor = neonMagenta, alpha = 0.4f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(I18nR.string.app_polish_morse), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            OutlinedTextField(
                value = morseText,
                onValueChange = onMorseText,
                label = { Text(stringResource(I18nR.string.app_polish_message)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("morse-input")
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlashlightOutputMode.entries.forEach { mode ->
                    TelosTechPill(
                        text = stringResource(
                            when (mode) {
                                FlashlightOutputMode.LightOnly -> I18nR.string.morse_light
                                FlashlightOutputMode.SoundOnly -> I18nR.string.morse_sound
                                FlashlightOutputMode.LightAndSound -> I18nR.string.morse_light_sound
                            }
                        ),
                        isSelected = outputMode == mode,
                        onClick = { onOutputMode(mode) },
                        accentColor = neonCyan,
                        // Distinct from MorseDecoderCard's optical "Light" button.
                        modifier = Modifier.testTag(
                            when (mode) {
                                FlashlightOutputMode.LightOnly -> "morse-output-light"
                                FlashlightOutputMode.SoundOnly -> "morse-output-sound"
                                FlashlightOutputMode.LightAndSound -> "morse-output-light-sound"
                            },
                        ),
                    )
                }
            }
            if (!toneGeneratorReady) Text(stringResource(I18nR.string.virtual_telemetry_unavailable), color = MaterialTheme.colorScheme.error)
            Text(stringResource(I18nR.string.morse_language_help), style = MaterialTheme.typography.bodySmall)
            if (encoded?.romanized == true) Text(encoded.readable, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
            if (encoded?.unsupported == true) Text(stringResource(I18nR.string.morse_unsupported), color = MaterialTheme.colorScheme.error)
            Text(morseCode, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onPlayMorse(morseCode, false) },
                    enabled = isMorsePlaying || morseCode.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(if (isMorsePlaying) stringResource(I18nR.string.hc_stop) else stringResource(I18nR.string.hc_send))
                }
                Button(
                    onClick = onSos,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("morse-sos"),
                ) {
                    Text(stringResource(I18nR.string.app_polish_sos))
                }
            }
        }
        MorseDecoderCard()
        Spacer(Modifier.height(8.dp))
    }
}

private suspend fun playSiren(
    target: FlashlightTarget,
    alternating: Boolean,
    toneGenerator: ToneGenerator?,
    setTorch: (Boolean) -> Unit
) {
    var flashOn = false
    while (currentCoroutineContext().isActive) {
        flashOn = !flashOn
        if (target == FlashlightTarget.Flash || target == FlashlightTarget.Both) {
            setTorch(flashOn)
        }
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 250)
        } catch (_: Exception) {}
        delay(200L)
    }
}

@Composable
private fun MorseDecoderCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isAudioDecoding by remember { mutableStateOf(false) }
    var isOpticalDecoding by remember { mutableStateOf(false) }
    var decodedMorse by remember { mutableStateOf("") }
    var decodedText by remember { mutableStateOf("") }

    var audioJob by remember { mutableStateOf<Job?>(null) }
    var opticalJob by remember { mutableStateOf<Job?>(null) }

    fun stopDecoder() {
        audioJob?.cancel()
        isAudioDecoding = false
    }

    fun stopOpticalDecoder() {
        opticalJob?.cancel()
        isOpticalDecoding = false
    }

    fun startOpticalDecoder() {
        stopOpticalDecoder()
        isOpticalDecoding = true
        opticalJob = scope.launch(Dispatchers.IO) {
            val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
            val cameraProvider = cameraProviderFuture.get()
            val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            var isHigh = false
            var pulseStartMs = System.currentTimeMillis()
            var gapStartMs = System.currentTimeMillis()
            var currentWordMorse = ""
            var baselineLuminance = 50.0

            imageAnalysis.setAnalyzer(androidx.core.content.ContextCompat.getMainExecutor(context)) { imageProxy ->
                val buffer = imageProxy.planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)
                
                var sum = 0L
                for(b in data) {
                    sum += (b.toInt() and 0xFF)
                }
                val avgLuminance = sum.toDouble() / data.size
                val now = System.currentTimeMillis()
                
                if (!isHigh) {
                    baselineLuminance = baselineLuminance * 0.95 + avgLuminance * 0.05
                }
                val isFlashOn = avgLuminance > maxOf(130.0, baselineLuminance + 25.0)

                if (isFlashOn) { // bright flash detected
                    if (!isHigh) {
                        isHigh = true
                        val gapDuration = now - gapStartMs
                        if (gapDuration > 600 && currentWordMorse.isNotBlank()) {
                            currentWordMorse += " / "
                        } else if (gapDuration > 180 && currentWordMorse.isNotBlank() && !currentWordMorse.endsWith(" ") && !currentWordMorse.endsWith("/ ")) {
                            currentWordMorse += " "
                        }
                        pulseStartMs = now
                    }
                } else { // dark
                    if (isHigh) {
                        isHigh = false
                        val pulseDuration = now - pulseStartMs
                        val symbol = if (pulseDuration > 160) "-" else "."
                        currentWordMorse += symbol
                        gapStartMs = now

                        scope.launch(Dispatchers.Main) {
                            decodedMorse = currentWordMorse
                            decodedText = MorseCodec.decode(currentWordMorse)
                        }
                    }
                }
                imageProxy.close()
            }
            
            try {
                withContext(Dispatchers.Main) {
                    val lifecycleOwner = context as androidx.lifecycle.LifecycleOwner
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                        imageAnalysis
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isOpticalDecoding = false }
            }
            
            // Cleanup on cancel
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                withContext(Dispatchers.Main) {
                    cameraProvider.unbindAll()
                }
            }
        }
    }

    fun startAudioDecoder() {
        stopDecoder()
        isAudioDecoding = true
        audioJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 8000
            val bufferSize = android.media.AudioRecord.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1024)

            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Microphone permission required for audio Morse decoding", Toast.LENGTH_SHORT).show()
                    isAudioDecoding = false
                }
                return@launch
            }

            var recorder: android.media.AudioRecord? = null
            try {
                recorder = android.media.AudioRecord(
                    android.media.MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                recorder.startRecording()
                val buffer = ShortArray(bufferSize)

                var isHigh = false
                var pulseStartMs = System.currentTimeMillis()
                var gapStartMs = System.currentTimeMillis()

                var currentWordMorse = ""

                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) sum += buffer[i] * buffer[i]
                        val rms = Math.sqrt(sum / read)
                        val now = System.currentTimeMillis()

                        if (rms > 2500) { // Sound pulse detected
                            if (!isHigh) {
                                isHigh = true
                                val gapDuration = now - gapStartMs
                                if (gapDuration > 600 && currentWordMorse.isNotBlank()) {
                                    currentWordMorse += " / "
                                } else if (gapDuration > 180 && currentWordMorse.isNotBlank() && !currentWordMorse.endsWith(" ") && !currentWordMorse.endsWith("/ ")) {
                                    currentWordMorse += " "
                                }
                                pulseStartMs = now
                            }
                        } else { // Silence
                            if (isHigh) {
                                isHigh = false
                                val pulseDuration = now - pulseStartMs
                                val symbol = if (pulseDuration > 160) "-" else "."
                                currentWordMorse += symbol
                                gapStartMs = now

                                withContext(Dispatchers.Main) {
                                    decodedMorse = currentWordMorse
                                    decodedText = MorseCodec.decode(currentWordMorse)
                                }
                            }
                        }
                    }
                    delay(30L)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isAudioDecoding = false }
            } finally {
                runCatching {
                    recorder?.stop()
                    recorder?.release()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .telosTechCard(borderColor = Color(0xFF00E676), alpha = 0.4f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TelosDotMatrixText(stringResource(I18nR.string.app_polish_morse), fontSize = 10.sp, color = Color(0xFF00E676))
        Text(
            stringResource(I18nR.string.morse_language_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { if (isAudioDecoding) stopDecoder() else startAudioDecoder() },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isAudioDecoding) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            ) {
                Text(if (isAudioDecoding) stringResource(I18nR.string.hc_stop) else stringResource(I18nR.string.morse_sound))
            }
            Button(
                onClick = { if (isOpticalDecoding) stopOpticalDecoder() else startOpticalDecoder() },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isOpticalDecoding) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            ) {
                Text(if (isOpticalDecoding) stringResource(I18nR.string.hc_stop) else stringResource(I18nR.string.morse_light))
            }
            OutlinedButton(
                onClick = {
                    if (decodedMorse.isNotBlank()) {
                        decodedMorse = ""
                        decodedText = ""
                    }
                },
                enabled = decodedMorse.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(I18nR.string.hc_clear))
            }
        }
        if (decodedMorse.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(I18nR.string.app_polish_morse), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        decodedMorse,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(I18nR.string.app_polish_message), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        decodedText.ifBlank { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
