package de.mm20.launcher2.ui.calendar

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberAppBarTitle
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.telosTechSurface
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchEmptyState
import de.mm20.launcher2.ui.component.TelosSearchBar
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Serializable
data object CalendarRoute : NavKey

class CalendarViewModel(app: android.app.Application) : AndroidViewModel(app) {
    val repo = CalendarRepository(app)
    val calendars = MutableStateFlow<List<DeviceCalendar>>(emptyList())
    val events = MutableStateFlow<List<CalEvent>>(emptyList())
    val month = MutableStateFlow(YearMonth.now())
    val selected = MutableStateFlow(LocalDate.now())
    val upcoming = MutableStateFlow<List<CalEvent>>(emptyList())
    val message = MutableStateFlow<String?>(null)

    fun reload() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            try {
                val cals = repo.calendars()
                val first = month.value.atDay(1)
                events.value = repo.events(first.minusDays(7), month.value.atEndOfMonth().plusDays(7), cals)
                calendars.value = cals
                upcoming.value = repo.events(LocalDate.now(), LocalDate.now().plusDays(30), cals)
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
        }
    }

    fun go(m: YearMonth) { month.value = m; reload() }
    fun io(block: suspend () -> Unit) = viewModelScope.launch { withContext(Dispatchers.IO) { try { block() } catch (e: Exception) { message.value = "error:${e.message}" } }; reload() }
}

private fun hasPermission(c: android.content.Context) =
    ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(c, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED


private fun parseNaturalLanguageEvent(input: String, defaultCalendarId: Long, selectedDate: LocalDate): Draft {
    var title = input.trim()
    var eventDate = selectedDate
    var eventTime = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)

    val lower = input.lowercase()

    if (lower.contains("αύριο") || lower.contains("αυριο") || lower.contains("tomorrow")) {
        eventDate = LocalDate.now().plusDays(1)
        title = title.replace(Regex("(?i)\\b(αύριο|αυριο|tomorrow)\\b"), "").trim()
    } else if (lower.contains("μεθαύριο") || lower.contains("μεθαυριο")) {
        eventDate = LocalDate.now().plusDays(2)
        title = title.replace(Regex("(?i)\\b(μεθαύριο|μεθαυριο)\\b"), "").trim()
    } else if (lower.contains("σήμερα") || lower.contains("σημερα") || lower.contains("today")) {
        eventDate = LocalDate.now()
        title = title.replace(Regex("(?i)\\b(σήμερα|σημερα|today)\\b"), "").trim()
    }

    val timeRegex = Regex("(?i)(?:στις\\s*|at\\s*)?(\\d{1,2})(?::(\\d{2}))?\\s*(pm|am|μμ|πμ)?")
    val timeMatch = timeRegex.find(lower)
    if (timeMatch != null && timeMatch.value.isNotBlank()) {
        val hourStr = timeMatch.groupValues[1]
        val minStr = timeMatch.groupValues[2]
        val ampm = timeMatch.groupValues[3]

        var hour = hourStr.toIntOrNull() ?: 12
        val min = minStr.toIntOrNull() ?: 0

        if (ampm == "pm" || ampm == "μμ") {
            if (hour < 12) hour += 12
        } else if (ampm == "am" || ampm == "πμ") {
            if (hour == 12) hour = 0
        } else if (hour < 8 && !lower.contains("πμ")) {
            hour += 12
        }

        eventTime = LocalTime.of(hour.coerceIn(0, 23), min.coerceIn(0, 59))
        title = title.replace(timeMatch.value, "").replace(Regex("(?i)\\bστις\\b"), "").trim()
    }

    val start = LocalDateTime.of(eventDate, eventTime)
    return Draft(
        calendarId = defaultCalendarId,
        title = title.ifBlank { "New Event" },
        start = start,
        end = start.plusHours(1)
    )
}

private data class Draft(
    val id: Long? = null, val calendarId: Long, val title: String = "", val description: String = "", val location: String = "",
    val start: LocalDateTime, val end: LocalDateTime, val allDay: Boolean = false, val repeat: Int = 0, val reminder: Int? = 10,
    /** The rule of the edited event and the start of the edited occurrence, so that editing one occurrence does not move or simplify the series. */
    val rrule: String? = null, val instanceStart: Long = 0,
)

private val draftSaver = androidx.compose.runtime.saveable.Saver<Draft?, String>(
    save = { d ->
        if (d == null) "" else org.json.JSONObject().apply {
            d.id?.let { put("id", it) }
            put("cal", d.calendarId); put("title", d.title); put("desc", d.description); put("loc", d.location)
            put("start", d.start.toString()); put("end", d.end.toString()); put("allDay", d.allDay); put("repeat", d.repeat)
            d.reminder?.let { put("rem", it) }
            d.rrule?.let { put("rrule", it) }
            put("inst", d.instanceStart)
        }.toString()
    },
    restore = { s ->
        if (s.isEmpty()) null else runCatching {
            val o = org.json.JSONObject(s)
            Draft(
                id = if (o.has("id")) o.getLong("id") else null, calendarId = o.getLong("cal"), title = o.getString("title"),
                description = o.getString("desc"), location = o.getString("loc"),
                start = LocalDateTime.parse(o.getString("start")), end = LocalDateTime.parse(o.getString("end")),
                allDay = o.getBoolean("allDay"), repeat = o.getInt("repeat"), reminder = if (o.has("rem")) o.getInt("rem") else null,
                rrule = if (o.has("rrule")) o.getString("rrule") else null, instanceStart = o.getLong("inst"),
            )
        }.getOrNull()
    },
)

private val repeatRules = listOf(null, "FREQ=DAILY", "FREQ=WEEKLY", "FREQ=MONTHLY", "FREQ=YEARLY")

private fun repeatIndex(rrule: String?) = repeatRules.indexOf(rrule?.split(';')?.firstOrNull { it.startsWith("FREQ") }).coerceAtLeast(0)

@Composable
fun CalendarScreen() {
    val vm: CalendarViewModel = viewModel()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasPermission(context)) }
    var blocked by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasPermission(context); vm.reload()
        // denied and no rationale left: the system will not ask again, only the app settings can grant it
        val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()
        blocked = !granted && activity != null && listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            .none { p -> androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, p) }
    }
    // coming back from the app settings
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { granted = hasPermission(context) }
    LaunchedEffect(granted) { if (granted) vm.reload() }

    Box(Modifier.fillMaxSize().background(de.mm20.launcher2.ui.component.virtualAppSurface()).systemBarsPadding()) {
        if (!granted) {
            Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text(stringResource(I18nR.string.cal_permission), textAlign = TextAlign.Center)
                if (blocked) {
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(I18nR.string.au_planner_open_settings)) }
                } else Button(onClick = { permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(I18nR.string.cal_grant))
                }
            }
        } else CalendarContent(vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarContent(vm: CalendarViewModel) {
    val context = LocalContext.current
    val calendars by vm.calendars.collectAsState()
    val events by vm.events.collectAsState()
    var pickMonth by remember { mutableStateOf(false) }
    val month by vm.month.collectAsState()
    val selected by vm.selected.collectAsState()
    val message by vm.message.collectAsState()
    var draft by rememberSaveable(stateSaver = draftSaver) { mutableStateOf<Draft?>(null) }
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    var menu by remember { mutableStateOf(false) }
    var showCalendars by remember { mutableStateOf(false) }
    var agenda by rememberSaveable { mutableStateOf(false) }
    val upcoming by vm.upcoming.collectAsState()
    val snack = remember { SnackbarHostState() }

    val text = message?.let { m ->
        val p = m.split(':', limit = 2)
        when (p[0]) { "imported" -> stringResource(I18nR.string.cal_imported, p[1].toInt()); "sync" -> stringResource(I18nR.string.cal_sync_requested); else -> stringResource(I18nR.string.cal_error, p.getOrElse(1) { "" }) }
    }
    LaunchedEffect(text) { if (text != null) { snack.showSnackbar(text); vm.message.value = null } }

    val writable = calendars.filter { it.writable }
    val importTarget = remember { mutableStateOf<Long?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.io {
            val target = writable.firstOrNull { it.visible }?.id ?: writable.firstOrNull()?.id ?: vm.repo.createLocalCalendar("Telos", 0xFF1E88E5.toInt())
            val t = context.contentResolver.openInputStream(uri)?.use { de.mm20.launcher2.ui.notes.NotesImport.readLimited(it, 32 * 1024 * 1024)?.toString(Charsets.UTF_8) }.orEmpty()
            vm.message.value = "imported:${vm.repo.importEvents(target, Ics.read(t))}"
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri != null) vm.io {
            val all = calendars.filter { it.visible }.flatMap { vm.repo.rawEvents(it.id) }
            context.contentResolver.openOutputStream(uri)?.use { it.write(Ics.write("Telos", all).toByteArray()) }
        }
    }

    BackHandler(enabled = draft != null) { draft = null }

    var searching by rememberSaveable { mutableStateOf(false) }
    var calQuery by rememberSaveable { mutableStateOf("") }
    var searchPool by remember { mutableStateOf<List<CalEvent>>(emptyList()) }
    LaunchedEffect(searching, calendars) {
        if (searching) searchPool = withContext(Dispatchers.IO) {
            try { vm.repo.events(LocalDate.now().minusYears(1), LocalDate.now().plusYears(1), calendars) } catch (e: SecurityException) { emptyList() }
        }
    }
    BackHandler(enabled = searching && draft == null) { searching = false; calQuery = "" }
    fun openEvent(ev: CalEvent) {
        val zone = ZoneId.systemDefault()
        val s = if (ev.allDay) LocalDateTime.ofInstant(Instant.ofEpochMilli(ev.begin), ZoneOffset.UTC) else LocalDateTime.ofInstant(Instant.ofEpochMilli(ev.begin), zone)
        val en = if (ev.allDay) LocalDateTime.ofInstant(Instant.ofEpochMilli(ev.end - 1), ZoneOffset.UTC) else LocalDateTime.ofInstant(Instant.ofEpochMilli(ev.end), zone)
        draft = Draft(ev.id, ev.calendarId, ev.title, ev.description, ev.location, s, maxOf(en, s), ev.allDay,
            repeatIndex(ev.rrule), vm.repo.reminderOf(ev.id), ev.rrule, ev.begin)
    }

    val e = draft
    if (e != null) {
        EventEditor(e, writable, calendars, onChange = { draft = it }, onDismiss = { draft = null },
            onSave = {
                vm.io {
                    val zone = ZoneId.systemDefault()
                    val s = if (it.allDay) it.start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() else it.start.atZone(zone).toInstant().toEpochMilli()
                    val en = if (it.allDay) it.end.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() else it.end.atZone(zone).toInstant().toEpochMilli()
                    // a repeating event keeps its rule (BYDAY, INTERVAL, UNTIL ...) unless the repeat choice was changed
                    val rule = if (it.id != null && it.rrule != null && it.repeat == repeatIndex(it.rrule)) it.rrule else repeatRules[it.repeat]
                    var start = s; var end = maxOf(en, s)
                    if (it.id != null && it.rrule != null) {
                        // the editor shows one occurrence: move the start of the series by the same amount
                        vm.repo.seriesStart(it.id)?.let { base -> start = base + (s - it.instanceStart); end = start + (end - s) }
                    }
                    vm.repo.save(it.id, it.calendarId, it.title, it.description, it.location, start, end, it.allDay, rule, it.reminder)
                }
                draft = null
            },
            onDelete = { id -> if (e.rrule != null) confirmDelete = id else { vm.io { vm.repo.delete(id) }; draft = null } })
        confirmDelete?.let { id ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au_planner_delete_series_title))  } },
                text = { Text(stringResource(I18nR.string.au_planner_delete_series_message)) },
                confirmButton = { TextButton(onClick = { confirmDelete = null; vm.io { vm.repo.delete(id) }; draft = null }) { Text(stringResource(I18nR.string.cal_delete)) } },
                dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(I18nR.string.cal_cancel)) } },
            )
        }
        return
    }

    if (pickMonth) MonthYearDialog(month, { vm.go(it); pickMonth = false }, { pickMonth = false })
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            if (writable.isNotEmpty()) {
                var showQuickAdd by remember { mutableStateOf(false) }
                var quickText by remember { mutableStateOf("") }
                
                if (showQuickAdd) {
                    AlertDialog(
                        onDismissRequest = { showQuickAdd = false },
                        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.quick_event_title))  } },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(I18nR.string.quick_event_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(
                                    value = quickText,
                                    onValueChange = { quickText = it },
                                    placeholder = { Text(stringResource(I18nR.string.quick_event_hint)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = quickText.isNotBlank(),
                                onClick = {
                                    val defaultId = (writable.firstOrNull { it.visible } ?: writable.first()).id
                                    draft = parseNaturalLanguageEvent(quickText, defaultId, selected)
                                    showQuickAdd = false
                                }
                            ) { Text(stringResource(I18nR.string.quick_event_create)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showQuickAdd = false }) { Text(stringResource(I18nR.string.cal_cancel)) }
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(
                        onClick = { showQuickAdd = true },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Icon(painterResource(BaseR.drawable.auto_awesome_20dp), contentDescription = "Quick AI Add")
                    }
                    FloatingActionButton(onClick = {
                        val start = LocalDateTime.of(selected, LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0))
                        draft = Draft(calendarId = (writable.firstOrNull { it.visible } ?: writable.first()).id, start = start, end = start.plusHours(1))
                    }) { Icon(painterResource(BaseR.drawable.add_24px), stringResource(I18nR.string.cal_new_event)) }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.go(month.minusMonths(1)) }) { Icon(painterResource(BaseR.drawable.chevron_backward_24px), stringResource(I18nR.string.cal_previous)) }
                TelosCyberAppBarTitle(
                    title = month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy")).replaceFirstChar { it.uppercase() },
                    status = stringResource(if (agenda) I18nR.string.cal_view_agenda else I18nR.string.cal_view_month),
                    statusColor = if (agenda) TelosCyber.NeonOrange else TelosCyber.NeonCyan,
                    modifier = Modifier.weight(1f).clickable { pickMonth = true },
                )
                IconButton(onClick = { vm.go(month.plusMonths(1)) }) { Icon(painterResource(BaseR.drawable.chevron_forward_24px), stringResource(I18nR.string.cal_next)) }
                IconButton(onClick = { vm.selected.value = LocalDate.now(); vm.go(YearMonth.now()) }) { Icon(painterResource(BaseR.drawable.today_24px), stringResource(I18nR.string.cal_today)) }
                var showFontDialog by remember { mutableStateOf(false) }
                if (showFontDialog) {
                    de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
                        appKey = "calendar",
                        appName = stringResource(I18nR.string.backup_group_calendar),
                        onDismiss = { showFontDialog = false }
                    )
                }
                IconButton(onClick = { searching = !searching; if (!searching) calQuery = "" }) {
                    Icon(painterResource(if (searching) BaseR.drawable.close_24px else BaseR.drawable.search_24px), stringResource(I18nR.string.ts_search))
                }
                IconButton(onClick = { showFontDialog = true }) {
                    Icon(painterResource(BaseR.drawable.settings_24px), contentDescription = stringResource(I18nR.string.per_app_font_size))
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(painterResource(BaseR.drawable.more_vert_24px), null) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(if (agenda) I18nR.string.cal_view_month else I18nR.string.cal_view_agenda)) }, onClick = { menu = false; agenda = !agenda })
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.cal_calendars)) }, onClick = { menu = false; showCalendars = true })
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.cal_sync_now)) }, onClick = { menu = false; vm.repo.requestSync(); vm.message.value = "sync" })
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.cal_add_account)) }, onClick = {
                            menu = false
                            context.startActivity(Intent(Settings.ACTION_ADD_ACCOUNT).putExtra(Settings.EXTRA_AUTHORITIES, arrayOf(CalendarContract.AUTHORITY)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        })
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.cal_import)) }, onClick = { menu = false; importer.launch(arrayOf("text/calendar", "*/*")) })
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.cal_export)) }, onClick = { menu = false; exporter.launch("telos-calendar.ics") })
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TelosTechPill(
                    text = stringResource(I18nR.string.cal_view_month),
                    isSelected = !agenda,
                    onClick = { agenda = false },
                    accentColor = TelosCyber.NeonCyan,
                )
                TelosTechPill(
                    text = stringResource(I18nR.string.cal_view_agenda),
                    isSelected = agenda,
                    onClick = { agenda = true },
                    accentColor = TelosCyber.NeonOrange,
                )
            }
            if (searching) {
                TelosSearchBar(calQuery, { calQuery = it }, stringResource(I18nR.string.tsp_search_events), autoFocus = true)
                val results = remember(searchPool, calQuery) {
                    if (calQuery.isBlank()) emptyList() else de.mm20.launcher2.comms.search.TelosSearch
                        .filter(searchPool, calQuery) { listOf(it.title, it.location, it.description) }
                        .sortedBy { it.begin }
                }
                if (calQuery.isNotBlank() && results.isEmpty()) SearchEmptyState(calQuery)
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { "s${it.id}-${it.begin}" }) { ev ->
                        Text(ev.firstDay.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        EventRow(ev, writable.any { it.id == ev.calendarId }) { openEvent(ev) }
                    }
                }
                return@Scaffold
            }
            if (agenda) {
                val byDay = remember(upcoming) {
                    val today = LocalDate.now()
                    upcoming.flatMap { ev -> generateSequence(maxOf(ev.firstDay, today)) { it.plusDays(1) }.takeWhile { it <= ev.lastDay && it <= today.plusDays(30) }.map { it to ev } }
                        .groupBy({ it.first }, { it.second }).toSortedMap()
                }
                if (byDay.isEmpty()) Text(stringResource(I18nR.string.cal_no_events), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "kmp-month") {
                        de.mm20.launcher2.ui.kmp.SharedCalendarHost(
                            modifier = Modifier.fillMaxWidth().height(280.dp),
                            month = month,
                            today = if (month == YearMonth.now()) LocalDate.now().dayOfMonth else 0,
                        )
                    }
                    byDay.forEach { (day, evs) ->
                        item(key = "h$day") { Text(day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                        items(evs.sortedBy { it.begin }, key = { "$day-${it.id}-${it.begin}" }) { ev -> EventRow(ev, writable.any { it.id == ev.calendarId }) { openEvent(ev) } }
                    }
                }
                return@Scaffold
            }
            val dayEvents = remember(events, selected) {
                events.filter { selected in it.firstDay..it.lastDay }.sortedWith(compareByDescending<CalEvent> { it.allDay }.thenBy { it.begin })
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "month") { MonthGrid(month, selected, events, onSelect = {
                vm.selected.value = it
                // a day of the previous or next month shown in the grid: move to that month
                if (YearMonth.from(it) != month) vm.go(YearMonth.from(it))
            }) }
            item(key = "selected-day") {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (calendars.isEmpty()) {
                item(key = "no-calendars") { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(I18nR.string.cal_no_calendars), textAlign = TextAlign.Center)
                    Button(onClick = { vm.io { vm.repo.createLocalCalendar("Telos", 0xFF1E88E5.toInt()) } }, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(I18nR.string.cal_create_local)) }
                }
                }
            } else if (dayEvents.isEmpty()) {
                item(key = "no-events") { Text(stringResource(I18nR.string.cal_no_events), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(dayEvents, key = { "${it.id}-${it.begin}" }) { ev ->
                    EventRow(ev, writable.any { it.id == ev.calendarId }) { openEvent(ev) }
                }
            }
            }
        }
    }
    if (showCalendars) CalendarsDialog(vm, calendars) { showCalendars = false }
}

@Composable
private fun MonthGrid(month: YearMonth, selected: LocalDate, events: List<CalEvent>, onSelect: (LocalDate) -> Unit) {
    val week = WeekFields.of(Locale.getDefault())
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - week.firstDayOfWeek.value + 7) % 7
    val start = first.minusDays(offset.toLong())
    val today = LocalDate.now()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val minCellWidth = maxOf(48.dp, 48.dp * density.fontScale)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val calendarWidth = maxOf(maxWidth, minCellWidth * 7 + 16.dp)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(calendarWidth).padding(horizontal = 8.dp)) {
            Row {
                for (i in 0 until 7) {
                    val dayName = week.firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    Text(
                        text = dayName,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
            for (w in 0 until 6) {
                Row {
                    for (d in 0 until 7) {
                        val day = start.plusDays((w * 7 + d).toLong())
                        val inMonth = day.month == month.month
                        val colors = events.filter { day in it.firstDay..it.lastDay }.map { it.color }.distinct().take(3)
                        Column(
                            Modifier.weight(1f).heightIn(min = 52.dp).padding(2.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (day == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { onSelect(day) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when { day == today -> MaterialTheme.colorScheme.primary; inMonth -> MaterialTheme.colorScheme.onSurface; else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f) },
                                fontWeight = if (day == today) androidx.compose.ui.text.font.FontWeight.Bold else null,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                            Row(Modifier.height(8.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                colors.forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(Color(it))) }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun EventRow(ev: CalEvent, editable: Boolean, onClick: () -> Unit) {
    val tf = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val zone = ZoneId.systemDefault()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .clickable(enabled = editable, onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(Color(ev.color)))
        Column(Modifier.padding(start = 12.dp)) {
            Text(ev.title.ifBlank { stringResource(I18nR.string.cal_no_title) }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (ev.allDay) stringResource(I18nR.string.cal_all_day) else "${Instant.ofEpochMilli(ev.begin).atZone(zone).format(tf)} – ${Instant.ofEpochMilli(ev.end).atZone(zone).format(tf)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ev.location.isNotBlank()) Text(ev.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditor(d: Draft, writable: List<DeviceCalendar>, all: List<DeviceCalendar>, onChange: (Draft) -> Unit, onDismiss: () -> Unit, onSave: (Draft) -> Unit, onDelete: (Long) -> Unit) {
    val context = LocalContext.current
    val df = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val tf = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    fun pickDate(v: LocalDateTime, set: (LocalDateTime) -> Unit) = DatePickerDialog(context, { _, y, m, day -> set(v.withYear(y).withMonth(m + 1).withDayOfMonth(day)) }, v.year, v.monthValue - 1, v.dayOfMonth).show()
    fun pickTime(v: LocalDateTime, set: (LocalDateTime) -> Unit) = TimePickerDialog(context, { _, h, m -> set(v.withHour(h).withMinute(m)) }, v.hour, v.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
    fun setStart(s: LocalDateTime) = onChange(d.copy(start = s, end = if (d.end.isBefore(s)) s.plusHours(1) else d.end))
    var repeatMenu by remember { mutableStateOf(false) }
    var calMenu by remember { mutableStateOf(false) }
    var remMenu by remember { mutableStateOf(false) }
    val repeatLabels = listOf(I18nR.string.cal_repeat_none, I18nR.string.cal_repeat_daily, I18nR.string.cal_repeat_weekly, I18nR.string.cal_repeat_monthly, I18nR.string.cal_repeat_yearly)
    val reminders = listOf(null, 0, 10, 30, 60, 1440)
    val cal = all.firstOrNull { it.id == d.calendarId }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(painterResource(BaseR.drawable.close_24px), stringResource(I18nR.string.cal_cancel)) }
            Text(stringResource(if (d.id == null) I18nR.string.cal_new_event else I18nR.string.cal_edit_event), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (d.id != null) IconButton(onClick = { onDelete(d.id) }) { Icon(painterResource(BaseR.drawable.delete_24px), stringResource(I18nR.string.cal_delete)) }
            Button(onClick = { onSave(d) }) { Text(stringResource(I18nR.string.cal_save)) }
        }
        OutlinedTextField(d.title, { onChange(d.copy(title = it)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(I18nR.string.cal_title)) }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(I18nR.string.cal_all_day), Modifier.weight(1f)); Switch(d.allDay, { onChange(d.copy(allDay = it)) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate(d.start, ::setStart) }, Modifier.weight(1f)) { Text(d.start.format(df)) }
            if (!d.allDay) OutlinedButton(onClick = { pickTime(d.start, ::setStart) }) { Text(d.start.format(tf)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate(d.end) { onChange(d.copy(end = it)) } }, Modifier.weight(1f)) { Text(d.end.format(df)) }
            if (!d.allDay) OutlinedButton(onClick = { pickTime(d.end) { onChange(d.copy(end = it)) } }) { Text(d.end.format(tf)) }
        }
        OutlinedTextField(d.location, { onChange(d.copy(location = it)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(I18nR.string.cal_location)) }, singleLine = true)
        OutlinedTextField(d.description, { onChange(d.copy(description = it)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(I18nR.string.cal_description)) }, minLines = 3)
        Box {
            OutlinedButton(onClick = { repeatMenu = true }) { Text(stringResource(I18nR.string.cal_repeat) + ": " + stringResource(repeatLabels[d.repeat])) }
            DropdownMenu(repeatMenu, { repeatMenu = false }) { repeatLabels.forEachIndexed { i, r -> DropdownMenuItem(text = { Text(stringResource(r)) }, onClick = { repeatMenu = false; onChange(d.copy(repeat = i)) }) } }
        }
        Box {
            OutlinedButton(onClick = { remMenu = true }) {
                Text(stringResource(I18nR.string.cal_reminder) + ": " + (d.reminder?.let { if (it == 0) stringResource(I18nR.string.cal_reminder_at_time) else stringResource(I18nR.string.cal_reminder_before, it) } ?: stringResource(I18nR.string.cal_reminder_none)))
            }
            DropdownMenu(remMenu, { remMenu = false }) {
                reminders.forEach { m -> DropdownMenuItem(text = { Text(m?.let { if (it == 0) stringResource(I18nR.string.cal_reminder_at_time) else stringResource(I18nR.string.cal_reminder_before, it) } ?: stringResource(I18nR.string.cal_reminder_none)) }, onClick = { remMenu = false; onChange(d.copy(reminder = m)) }) }
            }
        }
        Box {
            OutlinedButton(onClick = { calMenu = true }, enabled = d.id == null) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(cal?.color ?: 0))); Spacer(Modifier.width(8.dp)); Text(cal?.name ?: "")
            }
            DropdownMenu(calMenu, { calMenu = false }) { writable.forEach { c -> DropdownMenuItem(text = { Text("${c.name} (${c.account})") }, onClick = { calMenu = false; onChange(d.copy(calendarId = c.id)) }) } }
        }
        if (d.repeat != 0 && d.id != null) Text(stringResource(I18nR.string.cal_series_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalendarsDialog(vm: CalendarViewModel, calendars: List<DeviceCalendar>, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.cal_calendars))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                calendars.groupBy { it.account to it.accountType }.forEach { (acc, list) ->
                    Text(if (list.first().isLocal) stringResource(I18nR.string.cal_on_phone) else acc.first, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    list.forEach { c ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.color)))
                            Text(c.name, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (c.isLocal) IconButton(onClick = { vm.io { vm.repo.deleteLocalCalendar(c.id) } }) { Icon(painterResource(BaseR.drawable.delete_24px), stringResource(I18nR.string.cal_delete)) }
                            Switch(c.visible, { v -> vm.io { vm.repo.setVisible(c.id, v) } })
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(I18nR.string.cal_new_local)) }, singleLine = true)
                TextButton(onClick = { if (name.isNotBlank()) { vm.io { vm.repo.createLocalCalendar(name.trim(), 0xFF1E88E5.toInt()) }; name = "" } }) { Text(stringResource(I18nR.string.cal_create_local)) }
                Text(stringResource(I18nR.string.cal_sync_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.cal_ok)) } },
    )
}


/** Native wheel controls also accept keyboard entry without a separate mode. */
@Composable
private fun MonthYearDialog(initial: YearMonth, onChoose: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    val hostActivity = androidx.activity.compose.LocalActivity.current
    var chosenMonth by remember { mutableIntStateOf(initial.monthValue) }
    var chosenYear by remember { mutableIntStateOf(initial.year.coerceIn(1, 9999)) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val names = remember(locale) { java.text.DateFormatSymbols(locale).months.take(12).toTypedArray() }
    AlertDialog(onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(initial.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)))  } },
        text = {
            Row(Modifier.fillMaxWidth()) {
                androidx.compose.ui.viewinterop.AndroidView(modifier = Modifier.weight(1f).height(180.dp), factory = { context ->
                    android.widget.NumberPicker(context).apply {
                        minValue = 1; maxValue = 12; displayedValues = names; value = chosenMonth
                        setOnValueChangedListener { _, _, value -> chosenMonth = value }
                    }
                })
                androidx.compose.ui.viewinterop.AndroidView(modifier = Modifier.weight(1f).height(180.dp), factory = { context ->
                    android.widget.NumberPicker(context).apply {
                        minValue = 1; maxValue = 9999; value = chosenYear; wrapSelectorWheel = false
                        setOnValueChangedListener { _, _, value -> chosenYear = value }
                    }
                })
            }
        },
        confirmButton = { TextButton(onClick = { hostActivity?.currentFocus?.clearFocus(); onChoose(YearMonth.of(chosenYear, chosenMonth)) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}
