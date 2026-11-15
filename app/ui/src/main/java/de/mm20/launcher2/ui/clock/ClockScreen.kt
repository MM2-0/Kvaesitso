package de.mm20.launcher2.ui.clock

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import de.mm20.launcher2.ui.component.TelosDotMatrixText
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.telosTechCard
import de.mm20.launcher2.ui.component.virtualAppPopup
import de.mm20.launcher2.ui.layout.AdaptiveLayoutPolicy
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.clock.ClockAlarm
import de.mm20.launcher2.comms.clock.ClockAlarms
import de.mm20.launcher2.comms.clock.WorldClockSearch
import de.mm20.launcher2.comms.clock.WorldClockCity
import de.mm20.launcher2.comms.repository.RadioRepository
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data object ClockRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val backStack = LocalBackStack.current
    var selectedTab by de.mm20.launcher2.ui.component.rememberPersistentUiState("clock.tab", 0)
    var night by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (isActive) { now = ZonedDateTime.now(); delay(1000) } }
    if (night) {
        DisposableEffect(Unit) {
            val previous = activity?.window?.attributes?.screenBrightness
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = .1f }
            onDispose {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
        val nightTimeFormat = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
        val nightDateFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy") }
        Column(Modifier.fillMaxSize().background(Color.Black).clickable { night = false }
            .verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            ClockDigits(now.format(nightTimeFormat), Color.Red)
            Text(now.format(nightDateFormat), color = Color.Gray)
            Text(stringResource(I18nR.string.clock_night_exit), color = Color.Gray)
        }
        return
    }
    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(title = { TelosCyberHeading {  Text(stringResource(I18nR.string.preference_screen_clockwidget))  } }, navigationIcon = {
            if (backStack.size > 1) {
                IconButton(onClick = { backStack.removeLastOrNull() }) { Icon(painterResource(BaseR.drawable.arrow_back_24px), stringResource(I18nR.string.hc_back)) }
            }
        }, actions = { IconButton(onClick = { night = true }) {
            Icon(painterResource(BaseR.drawable.light_mode_24px), stringResource(I18nR.string.clock_night))
        }         }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
    }) { padding ->
        val neonCyan = Color(0xFF00F0FF)
        val neonMagenta = Color(0xFFFF007F)
        val neonGreen = Color(0xFF00E676)
        val titles = listOf(I18nR.string.clock_world, I18nR.string.clock_alarm, I18nR.string.clock_timer, I18nR.string.clock_stopwatch)
        val tabAccents = listOf(neonCyan, neonMagenta, neonGreen, neonGreen)
        val timeHudFormat = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }

        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            val sideBySide = AdaptiveLayoutPolicy.sideBySide(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale)
            val shortHeight = maxHeight < 420.dp

            @Composable
            fun ClockTabs(modifier: Modifier = Modifier) {
                Row(
                    modifier
                        .fillMaxWidth()
                        .then(if (!sideBySide) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    titles.forEachIndexed { index, title ->
                        TelosTechPill(
                            text = stringResource(title),
                            isSelected = selectedTab == index,
                            onClick = { selectedTab = index },
                            accentColor = tabAccents[index],
                        )
                    }
                }
            }

            @Composable
            fun TabBody(modifier: Modifier = Modifier) {
                Box(modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 -> WorldClockTab()
                        1 -> AlarmTab()
                        2 -> TimerTab()
                        else -> StopwatchTab()
                    }
                }
            }

            if (sideBySide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CyberAnalogClock(now, Modifier.padding(vertical = 8.dp))
                        ClockTabs()
                    }
                    TabBody(Modifier.weight(0.6f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (shortHeight) {
                        TelosDotMatrixText(now.format(timeHudFormat), fontSize = 11.sp, color = neonCyan, modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        CyberAnalogClock(now, Modifier.padding(vertical = 8.dp))
                    }
                    ClockTabs(Modifier.padding(vertical = 8.dp))
                    TabBody(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun CyberAnalogClock(now: ZonedDateTime, modifier: Modifier = Modifier) {
    val cyan = Color(0xFF00F2FE)
    val pink = Color(0xFFFF007F)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .telosTechCard(borderColor = cyan, alpha = 0.55f)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(140.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 - 8.dp.toPx()

                // Dial track
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )

                // Outer neon ring
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                        0.0f to cyan,
                        0.5f to pink,
                        1.0f to cyan
                    ),
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )

                // Hour ticks
                for (i in 0 until 12) {
                    val angle = Math.toRadians((i * 30).toDouble())
                    val startX = center.x + (radius - 10.dp.toPx()) * Math.cos(angle).toFloat()
                    val startY = center.y + (radius - 10.dp.toPx()) * Math.sin(angle).toFloat()
                    val endX = center.x + radius * Math.cos(angle).toFloat()
                    val endY = center.y + radius * Math.sin(angle).toFloat()
                    drawLine(
                        color = if (i % 3 == 0) cyan else Color.White.copy(alpha = 0.3f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (i % 3 == 0) 3.dp.toPx() else 1.5.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }

                // Hours hand
                val hourAngle = Math.toRadians(((now.hour % 12 + now.minute / 60f) * 30 - 90).toDouble())
                val hourLength = radius * 0.5f
                drawLine(
                    color = Color.White,
                    start = center,
                    end = Offset(center.x + hourLength * Math.cos(hourAngle).toFloat(), center.y + hourLength * Math.sin(hourAngle).toFloat()),
                    strokeWidth = 4.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // Minutes hand
                val minAngle = Math.toRadians(((now.minute + now.second / 60f) * 6 - 90).toDouble())
                val minLength = radius * 0.75f
                drawLine(
                    color = cyan,
                    start = center,
                    end = Offset(center.x + minLength * Math.cos(minAngle).toFloat(), center.y + minLength * Math.sin(minAngle).toFloat()),
                    strokeWidth = 3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // Seconds hand
                val secAngle = Math.toRadians((now.second * 6 - 90).toDouble())
                val secLength = radius * 0.85f
                drawLine(
                    color = pink,
                    start = center,
                    end = Offset(center.x + secLength * Math.cos(secAngle).toFloat(), center.y + secLength * Math.sin(secAngle).toFloat()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                drawCircle(color = pink, radius = 5.dp.toPx(), center = center)
            }
        }

        Spacer(Modifier.height(8.dp))

        val analogTimeFormat = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
        val analogDateFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy") }
        Text(
            text = now.format(analogTimeFormat),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            letterSpacing = 2.sp
        )
        Text(
            text = now.format(analogDateFormat),
            style = MaterialTheme.typography.bodyMedium,
            color = cyan,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ClockDigits(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    // Important time values remain full-size and can be panned on narrow windows.
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.displayMedium,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = color,
            maxLines = 1, softWrap = false)
    }
}

@Composable
private fun WorldClockTab() {
    var now by remember { mutableStateOf(Instant.now()) }
    var query by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.getDefault()
    val resources = LocalContext.current.resources
    val packagedLocales = remember(resources) { resources.assets.locales.toList() }
    var cities by remember(locale) { mutableStateOf(emptyList<WorldClockCity>()) }
    LaunchedEffect(locale, packagedLocales) { cities = withContext(Dispatchers.Default) { WorldClockSearch.index(locale, packagedLocales) } }
    val countrySettings: de.mm20.launcher2.preferences.comms.CommsSettings = org.koin.compose.koinInject()
    val homeCountry by countrySettings.homeCountry.collectAsState(initial = "")
    val results = remember(query, cities, searchFocused, locale, homeCountry) {
        if (query.isNotBlank()) WorldClockSearch.search(cities, query)
        else if (searchFocused) {
            val zones = listOf("Europe/Athens", "Europe/London", "Europe/Paris", "Europe/Berlin", "Europe/Rome", "Europe/Madrid", "Asia/Tokyo")
            zones.filterNot { it == "Europe/Athens" && (homeCountry.equals("GR", true) || locale.country == "GR" || locale.language == "el") }
                .mapNotNull { zone -> cities.firstOrNull { it.zoneId == zone } }
        } else emptyList()
    }
    val localTimeFormat = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
    val localDateFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMMM yyyy") }
    val cityTimeFormat = remember(locale) { DateTimeFormatter.ofPattern("HH:mm · EEE, d MMM", locale) }
    LaunchedEffect(Unit) { while (isActive) { now = Instant.now(); delay(1000) } }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val local = ZonedDateTime.ofInstant(now, ZoneId.systemDefault())
            Column(Modifier.fillMaxWidth().telosTechCard(borderColor = Color(0xFF00F0FF)).padding(4.dp)) {
                de.mm20.launcher2.ui.kmp.SharedClockHost(
                    title = stringResource(I18nR.string.clock_local),
                    time = local.format(localTimeFormat),
                    date = local.format(localDateFormat),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            de.mm20.launcher2.ui.component.TelosSearchBar(
                value = query, onValueChange = { query = it },
                placeholder = stringResource(I18nR.string.clock_world),
                onFocusChanged = { searchFocused = it },
            )
        }
        items(results, key = { it.zoneId }) { city ->
            Column(Modifier.fillMaxWidth().telosTechCard(borderColor = Color(0xFFFF007F), alpha = 0.4f).padding(16.dp)) {
                Text(city.city, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(listOf(city.country, city.zoneId).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                Text(ZonedDateTime.ofInstant(now, ZoneId.of(city.zoneId)).format(cityTimeFormat), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun AlarmTab() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val operations = remember { Mutex() }
    var alarms by remember { mutableStateOf(emptyList<ClockAlarm>()) }
    var exact by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ClockAlarm?>(null) }
    fun refresh() { scope.launch {
        operations.withLock {
            loading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val allowed = ClockAlarms.canSchedule(appContext)
                    val loaded = if (allowed) ClockAlarms.update(appContext) { it } else ClockAlarms.load(appContext)
                    loaded to allowed
                }
                alarms = result.first; exact = result.second; failed = false
            } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
            finally { loading = false }
        }
    } }
    fun save(transform: (List<ClockAlarm>) -> List<ClockAlarm>) { scope.launch {
        operations.withLock {
            loading = true
            try {
                alarms = withContext(Dispatchers.IO) { ClockAlarms.update(appContext, transform) }
                failed = false
            } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
            finally { loading = false }
        }
    } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) refresh()
        onDispose { lifecycle.removeObserver(observer) }
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (failed) item { TextButton(onClick = { refresh() }, enabled = !loading) { Text(stringResource(I18nR.string.hc_failed_try_again)) } }
        if (!exact && !loading) item { OutlinedButton(onClick = {
            if (android.os.Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(I18nR.string.clock_exact)) } }
        items(alarms, key = { it.id }) { alarm ->
            Column(Modifier.fillMaxWidth().telosTechCard(borderColor = Color(0xFF00E676), alpha = 0.4f).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { editing = alarm }) {
                        Text("%02d:%02d".format(alarm.hour, alarm.minute), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                        Text(alarm.label.ifBlank { context.getString(I18nR.string.clock_alarm) })
                        Text(alarm.soundName.ifBlank { context.getString(I18nR.string.clock_default_sound) }, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(alarm.enabled, { enabled -> save { current -> current.map { if (it.id == alarm.id) it.copy(enabled = enabled) else it } } }, enabled = exact && !loading)
                }
                TextButton(onClick = { save { current -> current.filterNot { it.id == alarm.id } } }, enabled = !loading, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(I18nR.string.hc_delete)) }
            }
        }
        item { Button(onClick = { editing = ClockAlarm(enabled = exact) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !loading) { Text(stringResource(I18nR.string.hc_add)) } }
    }
    editing?.let { alarm -> AlarmEditor(alarm, onDismiss = { editing = null }, onSave = { updated ->
        save { current -> current.filterNot { it.id == updated.id } + updated }; editing = null
    }) }
}

@Composable
private fun AlarmEditor(initial: ClockAlarm, onDismiss: () -> Unit, onSave: (ClockAlarm) -> Unit) {
    val context = LocalContext.current
    val repository: RadioRepository = koinInject()
    val stations by repository.observeFavorites().collectAsState(emptyList())
    var alarm by remember(initial.id) { mutableStateOf(initial) }
    var hour by remember { mutableStateOf(initial.hour.toString()) }
    var minute by remember { mutableStateOf(initial.minute.toString()) }
    var radio by remember { mutableStateOf(false) }
    var stream by remember { mutableStateOf(initial.soundUri.takeIf { it.startsWith("http") }.orEmpty()) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            alarm = alarm.copy(soundUri = uri.toString(), soundName = context.getString(I18nR.string.clock_audio_file))
        }
    }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION") val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) alarm = alarm.copy(soundUri = uri.toString(), soundName = runCatching { RingtoneManager.getRingtone(context, uri).getTitle(context) }.getOrDefault(context.getString(I18nR.string.clock_default_sound)))
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(de.mm20.launcher2.ui.component.LocalGlassSurface provides false) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = virtualAppPopup(),
                tonalElevation = 16.dp
            ) {
            Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .85f).dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(I18nR.string.clock_alarm), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("HH") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("mm") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
                OutlinedTextField(alarm.label, { alarm = alarm.copy(label = it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(I18nR.string.au3_appearance_typo_label)) })
                Text(alarm.soundName.ifBlank { context.getString(I18nR.string.clock_default_sound) })
                OutlinedButton(onClick = { ringtonePicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)) }, Modifier.fillMaxWidth()) { Text(stringResource(I18nR.string.clock_default_sound)) }
                OutlinedButton(onClick = { filePicker.launch(arrayOf("audio/*")) }, Modifier.fillMaxWidth()) { Text(stringResource(I18nR.string.clock_audio_file)) }
                OutlinedButton(onClick = { radio = !radio }, Modifier.fillMaxWidth()) { Text(stringResource(I18nR.string.hc_radio)) }
                if (radio) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(stations, key = { it.streamUrl + it.name }) { station ->
                            TextButton(
                                onClick = {
                                    alarm = alarm.copy(soundUri = station.streamUrl, soundName = station.name)
                                    radio = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(station.name) }
                        }
                    }
                    OutlinedTextField(stream, { stream = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(I18nR.string.clock_stream_url)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    TextButton(onClick = { alarm = alarm.copy(soundUri = stream.trim(), soundName = context.getString(I18nR.string.hc_radio)); radio = false }, enabled = Uri.parse(stream).scheme in listOf("http", "https")) { Text(stringResource(I18nR.string.hc_save)) }
                }
                Text(stringResource(I18nR.string.clock_sound_fallback), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onSave(alarm.copy(hour = hour.toInt(), minute = minute.toInt(), enabled = alarm.enabled && ClockAlarms.canSchedule(context))) }, enabled = hour.toIntOrNull() in 0..23 && minute.toIntOrNull() in 0..59, modifier = Modifier.fillMaxWidth()) { Text(stringResource(I18nR.string.hc_save)) }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(I18nR.string.hc_cancel)) }
            }
        }
      }
    }
}
@Composable
private fun TimerTab() {
    var minutesInput by remember { mutableStateOf("5") }
    var totalSeconds by remember { mutableIntStateOf(300) }
    var remainingMs by remember { mutableLongStateOf(300000L) }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        while (isRunning) {
            if (remainingMs <= 0L) {
                isRunning = false
                remainingMs = 0L
                break
            }
            delay(100L)
            remainingMs = (remainingMs - 100L).coerceAtLeast(0L)
            if (remainingMs == 0L) {
                isRunning = false
                break
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 380.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (compact) Arrangement.Top else Arrangement.Center,
        ) {
        val totalSec = remainingMs / 1000
        val mins = totalSec / 60
        val secs = totalSec % 60
        val tenths = (remainingMs % 1000) / 100

        ClockDigits("%02d:%02d.%d".format(mins, secs, tenths))

        Spacer(Modifier.height(24.dp))

        if (!isRunning) {
            OutlinedTextField(
                value = minutesInput,
                onValueChange = {
                    minutesInput = it
                    val m = it.toIntOrNull() ?: 0
                    totalSeconds = m * 60
                    remainingMs = totalSeconds * 1000L
                },
                label = { Text(stringResource(I18nR.string.clock_minutes)) },
                singleLine = true,
                modifier = Modifier.width(200.dp)
            )
            Spacer(Modifier.height(16.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    if (isRunning) {
                        isRunning = false
                    } else {
                        if (remainingMs <= 0L) remainingMs = totalSeconds * 1000L
                        isRunning = true
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(if (isRunning) stringResource(I18nR.string.au_music_pause) else stringResource(I18nR.string.clock_start))
            }

            Button(
                onClick = {
                    isRunning = false
                    remainingMs = totalSeconds * 1000L
                },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(stringResource(I18nR.string.clock_reset), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
    }
}

@Composable
private fun StopwatchTab() {
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    val laps = remember { mutableStateListOf<Long>() }

    LaunchedEffect(isRunning) {
        val startTime = SystemClock.elapsedRealtime() - elapsedMs
        while (isRunning) {
            elapsedMs = SystemClock.elapsedRealtime() - startTime
            delay(10L)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 400.dp
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        if (!compact) Spacer(Modifier.height(16.dp))

        val minutes = (elapsedMs / 1000) / 60
        val seconds = (elapsedMs / 1000) % 60
        val millis = (elapsedMs % 1000) / 10

        ClockDigits("%02d:%02d.%02d".format(minutes, seconds, millis))

        Spacer(Modifier.height(24.dp))

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { isRunning = !isRunning }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (isRunning) stringResource(I18nR.string.au_music_pause) else stringResource(I18nR.string.clock_start))
            }

            Button(onClick = { if (isRunning) laps.add(0, elapsedMs) }, enabled = isRunning, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(I18nR.string.clock_lap))
            }

            Button(
                onClick = {
                    isRunning = false
                    elapsedMs = 0L
                    laps.clear()
                },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(stringResource(I18nR.string.clock_reset), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(laps) { lapMs ->
                val m = (lapMs / 1000) / 60
                val s = (lapMs / 1000) % 60
                val ms = (lapMs % 1000) / 10
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .telosTechCard(borderColor = Color(0xFF00F0FF), alpha = 0.35f)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(I18nR.string.clock_lap), fontWeight = FontWeight.Bold)
                    Text("%02d:%02d.%02d".format(m, s, ms), fontFamily = FontFamily.Monospace)
                }
            }
        }
        }
    }
}
