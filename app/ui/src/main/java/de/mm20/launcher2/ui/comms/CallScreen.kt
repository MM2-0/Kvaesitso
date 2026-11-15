package de.mm20.launcher2.ui.comms

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import de.mm20.launcher2.comms.recording.CallAudioRecorder
import de.mm20.launcher2.comms.recording.RecordingCoordinator
import de.mm20.launcher2.comms.recording.RecordingQuality
import de.mm20.launcher2.comms.telephony.InCallUiState
import de.mm20.launcher2.comms.telephony.TelosCallSession
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberAppBarTitle
import de.mm20.launcher2.ui.component.telosTechSurface
import de.mm20.launcher2.ui.component.virtualAppPopup
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun CallScreen(
    onFinished: () -> Unit,
    onTerminationBounds: (androidx.compose.ui.geometry.Rect?) -> Unit = {},
    stateSource: kotlinx.coroutines.flow.StateFlow<InCallUiState> = TelosCallSession.ui,
    onHangup: () -> Unit = { TelosCallSession.hangup() },
) {
    val state by stateSource.collectAsStateWithLifecycle()
    DisposableEffect(state.incoming) { onDispose { onTerminationBounds(null) } }
    val commsSettings: CommsSettings = koinInject()
    val recording by CallAudioRecorder.isRecording.collectAsStateWithLifecycle()
    val qualityKey by commsSettings.recordingQuality.collectAsStateWithLifecycle("BALANCED")
    val rejectSms by commsSettings.rejectSmsTemplate.collectAsStateWithLifecycle("")
    val notesEnabled by commsSettings.inCallNotes.collectAsStateWithLifecycle(true)
    val notesMap by commsSettings.callerNotes.collectAsStateWithLifecycle(emptyMap())
    val answerStyle by commsSettings.answerStyle.collectAsStateWithLifecycle("buttons")
    val directory: de.mm20.launcher2.comms.repository.ContactDirectoryRepository = koinInject()
    val hiddenMap by remember(commsSettings, directory) {
        kotlinx.coroutines.flow.combine(commsSettings.hiddenNumbers, directory.observeContacts().catch { emit(emptyList()) }) { hidden, contacts ->
            de.mm20.launcher2.comms.privacy.HiddenContacts.expanded(hidden, contacts)
        }
    }.collectAsStateWithLifecycle(emptyMap())
    val maskHidden by commsSettings.maskHiddenIncoming.collectAsStateWithLifecycle(true)
    val context = LocalContext.current
    val recordScope = rememberCoroutineScope()
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.hasCall) { if (!state.hasCall) onFinished() }
    // The InCallService owns automatic recording, including calls answered from notifications.
    // Composing/changing this screen must not race a second recording start on answer.
    LaunchedEffect(state.connectedAtEpochMs, state.active) {
        while (state.active && state.connectedAtEpochMs != null) {
            elapsed = ((System.currentTimeMillis() - state.connectedAtEpochMs!!) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    val hiddenIncoming = maskHidden && de.mm20.launcher2.comms.privacy.HiddenContacts.matches(state.number, hiddenMap)
    val publicState = if (hiddenIncoming) state.copy(
        name = stringResource(I18nR.string.au_phoneb_private_caller), number = "", photoUri = null,
        callerNameSuggested = false, likelySpam = false, callerSourceUrl = null,
    ) else state
    val displayName = when {
        publicState.callerNameSuggested && !publicState.name.isNullOrBlank() -> stringResource(I18nR.string.caller_suggested_name, publicState.name!!)
        !publicState.name.isNullOrBlank() -> publicState.name!!
        publicState.number.isNotBlank() -> publicState.number
        else -> stringResource(I18nR.string.widget_name_unknown)
    }
    val status = when {
        state.incoming -> stringResource(I18nR.string.comms_incoming_call)
        state.connecting -> stringResource(I18nR.string.comms_calling)
        state.onHold -> stringResource(I18nR.string.comms_on_hold)
        state.active -> formatElapsed(elapsed)
        else -> ""
    }
    CallScreenContent(publicState, displayName, status, onHangup, onTerminationBounds,
        incomingActions = { IncomingCallActions(rejectSms, answerStyle, onTerminationBounds) },
        activeControls = {
            if (showKeypad) {
                InCallKeypad { TelosCallSession.playDtmf(it) }
                TextButton(onClick = { showKeypad = false }) { Text(stringResource(I18nR.string.hc_close)) }
            } else {
            val muteLabel = stringResource(I18nR.string.comms_mute)
            val keypadLabel = stringResource(I18nR.string.comms_keypad)
            val speakerLabel = stringResource(I18nR.string.comms_speaker)
            val bluetoothLabel = stringResource(I18nR.string.au_phoneb_bluetooth)
            val holdLabel = stringResource(I18nR.string.comms_hold)
            val recordLabel = stringResource(
                if (recording) I18nR.string.au_phoneb_recording_short else I18nR.string.au_phoneb_record
            )
            val addLabel = stringResource(I18nR.string.comms_add_call)
            val mergeLabel = stringResource(I18nR.string.comms_merge)
            val swapLabel = stringResource(I18nR.string.comms_swap)
            val noteLabel = stringResource(I18nR.string.hc_note)
            val micNeeded = stringResource(I18nR.string.au_phoneb_mic_needed)
            val recordFailed = stringResource(I18nR.string.au_phoneb_record_failed)
            val controls = remember(state, recording, showKeypad, showNotes, notesEnabled, qualityKey,
                muteLabel, keypadLabel, speakerLabel, bluetoothLabel, holdLabel, recordLabel, addLabel,
                mergeLabel, swapLabel, noteLabel, micNeeded, recordFailed) { buildList {
                add(
                    CallControlSpec(
                        icon = if (state.muted) BaseR.drawable.rd_ic_microphone_off_vector else BaseR.drawable.mic_24px,
                        label = muteLabel,
                        selected = state.muted,
                        onClick = { TelosCallSession.toggleMute() },
                    )
                )
                add(
                    CallControlSpec(
                        icon = BaseR.drawable.dialpad_24px,
                        label = keypadLabel,
                        selected = showKeypad,
                        onClick = { showKeypad = !showKeypad },
                    )
                )
                add(
                    CallControlSpec(
                        icon = if (state.speaker) BaseR.drawable.volume_up_24px else BaseR.drawable.volume_off_24px,
                        label = speakerLabel,
                        selected = state.speaker,
                        onClick = { TelosCallSession.toggleSpeaker() },
                    )
                )
                add(
                    CallControlSpec(
                        icon = BaseR.drawable.rd_ic_bluetooth_audio_vector,
                        label = bluetoothLabel,
                        selected = state.bluetooth,
                        onClick = { TelosCallSession.cycleAudioRoute() },
                    )
                )
                add(
                    CallControlSpec(
                        icon = BaseR.drawable.rd_ic_pause_vector,
                        label = holdLabel,
                        selected = state.onHold,
                        onClick = { TelosCallSession.toggleHold() },
                    )
                )
                add(
                    CallControlSpec(
                        icon = if (recording) BaseR.drawable.mic_24px else BaseR.drawable.mic_off_24px,
                        label = recordLabel,
                        selected = recording,
                        onClick = {
                            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (!hasMic) {
                                Toast.makeText(context, micNeeded, Toast.LENGTH_SHORT).show()
                            } else {
                                recordScope.launch {
                                    if (recording) RecordingCoordinator.stop()
                                    else if (!RecordingCoordinator.start(
                                            context,
                                            state.number,
                                            RecordingQuality.fromKey(qualityKey),
                                        )
                                    ) {
                                        Toast.makeText(context, recordFailed, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                    )
                )
                add(
                    CallControlSpec(
                        icon = BaseR.drawable.rd_ic_add_call_vector,
                        label = addLabel,
                        selected = false,
                        onClick = { TelosCallSession.addCall(context) },
                    )
                )
                add(
                    CallControlSpec(
                        icon = BaseR.drawable.rd_ic_call_merge_vector,
                        label = mergeLabel,
                        selected = state.canMerge,
                        onClick = { TelosCallSession.merge() },
                    )
                )
                add(
                    CallControlSpec(
                        icon = BaseR.drawable.rd_ic_call_swap_vector,
                        label = swapLabel,
                        selected = state.canSwap,
                        onClick = { TelosCallSession.swap() },
                    )
                )
                if (notesEnabled) {
                    add(
                        CallControlSpec(
                            icon = BaseR.drawable.rd_ic_note,
                            label = noteLabel,
                            selected = showNotes,
                            onClick = { showNotes = true },
                        )
                    )
                }
            } }
            CallControls(controls)
            }
            if (state.canSwap && state.secondNumber.isNotBlank()) {
                Text(state.secondName ?: state.secondNumber, style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
        if (showNotes) {
            var draft by remember(state.number) {
                mutableStateOf(notesMap[state.number].orEmpty())
            }
            AlertDialog(
                onDismissRequest = { showNotes = false },
                containerColor = virtualAppPopup(),
                title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_call_note))  } },
                text = {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        commsSettings.setCallerNote(state.number, draft)
                        showNotes = false
                    }) { Text(stringResource(I18nR.string.hc_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { showNotes = false }) { Text(stringResource(I18nR.string.hc_close)) }
                },
            )
        }

}

/** Stateless call scene: rendering a ringing -> active transition never invokes Telecom or capture. */
@Composable
internal fun CallScreenContent(
    state: InCallUiState,
    displayName: String,
    status: String,
    onHangup: () -> Unit,
    onTerminationBounds: (androidx.compose.ui.geometry.Rect?) -> Unit = {},
    incomingActions: @Composable () -> Unit = {},
    activeControls: @Composable () -> Unit = {},
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .windowInsetsPadding(WindowInsets.safeDrawing).testTag("call-scene")) {
        val sideBySide = maxWidth >= 560.dp && maxWidth > maxHeight
        val statusAccent = when {
            state.incoming -> TelosCyber.NeonGreen
            state.onHold -> TelosCyber.NeonOrange
            state.connecting -> TelosCyber.NeonMagenta
            else -> TelosCyber.NeonCyan
        }
        val identity: @Composable ColumnScope.() -> Unit = {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TelosCyberAppBarTitle(
                    title = displayName,
                    status = status.takeIf { it.isNotBlank() },
                    statusColor = statusAccent,
                )
                if (state.number.isNotBlank() && state.number != displayName) {
                    Text(state.number, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                } else {
                    Spacer(Modifier.height(12.dp))
                }
                // Large contact photo stays free of neon card borders so prominence is preserved.
                CallerPhoto(displayName, state.photoUri, Modifier.fillMaxWidth().aspectRatio(if (sideBySide) 1.2f else 0.95f))
                if (state.likelySpam) Text(stringResource(I18nR.string.caller_likely_spam), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp))
                state.callerSourceUrl?.let { url ->
                    val context = LocalContext.current
                    TextButton(onClick = { context.tryStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                        Text("white-pages.gr")
                    }
                }
            }
        }
        val controlsShape = RoundedCornerShape(32.dp)
        val actions: @Composable ColumnScope.() -> Unit = {
            if (!state.incoming) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .telosTechSurface(borderColor = TelosCyber.NeonCyan, shape = RoundedCornerShape(24.dp))
                        .padding(12.dp),
                ) {
                    activeControls()
                }
            } else Spacer(Modifier.weight(0.001f))
            Surface(
                color = Color.Transparent,
                shape = controlsShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .telosTechSurface(borderColor = TelosCyber.NeonMagenta, shape = controlsShape),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.incoming) incomingActions() else {
                        FloatingActionButton(onClick = onHangup, containerColor = RdRedCall, contentColor = Color.White,
                            shape = RoundedCornerShape(24.dp), modifier = Modifier.size(width = 96.dp, height = 64.dp)
                                .onGloballyPositioned { onTerminationBounds(it.boundsInWindow()) }) {
                            Icon(painterResource(BaseR.drawable.rd_ic_call_end), stringResource(I18nR.string.comms_hangup))
                        }
                    }
                }
            }
        }
        if (sideBySide) Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight(), content = identity)
            Column(Modifier.weight(1f).fillMaxHeight(), content = actions)
        } else Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(if (state.incoming) 1f else 0.52f).fillMaxWidth(), content = identity)
            if (state.incoming) {
                // Incoming critical actions grow naturally rather than occupying a weighted, clipped pane.
                Surface(
                    color = Color.Transparent,
                    shape = controlsShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .telosTechSurface(borderColor = TelosCyber.NeonGreen, shape = controlsShape),
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { incomingActions() }
                }
            } else Column(Modifier.weight(0.48f).fillMaxWidth(), content = actions)
        }
    }
}
}

@Composable
private fun CallerPhoto(name: String, photoUri: String?, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(context, photoUri) {
        ImageRequest.Builder(context).data(photoUri).size(1280, 1280).crossfade(true).build()
    }
    val fallback: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            val initials = remember(name) { name.trim().split(' ').filter(String::isNotBlank).take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { "?" } }
            Text(initials, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
    Box(modifier.clip(RoundedCornerShape(32.dp)).testTag("caller-photo")) {
        if (photoUri.isNullOrBlank()) fallback() else {
            SubcomposeAsyncImage(model = request, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit, loading = { fallback() }, error = { fallback() },
                success = { SubcomposeAsyncImageContent(Modifier.testTag("caller-photo-loaded")) })
        }
    }
}

@Composable
private fun CallControls(controls: List<CallControlSpec>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / (80.dp * LocalDensity.current.fontScale)).toInt().coerceIn(1, 4)
        val rows = remember(controls, columns) { controls.chunked(columns) }
        Column { rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { control ->
                    Box(Modifier.weight(1f)) { CallControl(control.icon, control.label, control.selected, control.onClick) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IncomingCallActions(rejectSms: String, answerStyle: String,
    onTerminationBounds: (androidx.compose.ui.geometry.Rect?) -> Unit = {},
    onAnswer: () -> Unit = { TelosCallSession.answer() },
    onReject: () -> Unit = { TelosCallSession.reject() },
) {
    val context = LocalContext.current
    var remindOpen by remember { mutableStateOf(false) }
    if (answerStyle == "swipe") SwipeAnswer(onAnswer, onReject) else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(false, true).forEach { answer ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(onClick = { if (answer) onAnswer() else onReject() },
                        containerColor = if (answer) RdCallGreen else RdRedCall, contentColor = Color.White,
                        shape = RoundedCornerShape(24.dp), modifier = Modifier.size(72.dp).then(
                            if (answer) Modifier else Modifier.onGloballyPositioned { onTerminationBounds(it.boundsInWindow()) })) {
                        Icon(painterResource(if (answer) BaseR.drawable.rd_ic_call_accept else BaseR.drawable.rd_ic_call_end),
                            stringResource(if (answer) I18nR.string.comms_answer else I18nR.string.comms_reject))
                    }
                    Text(stringResource(if (answer) I18nR.string.comms_answer else I18nR.string.comms_reject),
                        modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { remindOpen = true }) { Text(stringResource(I18nR.string.hc_remind_me)) }
        if (rejectSms.isNotBlank()) TextButton(onClick = {
            val number = TelosCallSession.ui.value.number
            TelosCallSession.reject()
            de.mm20.launcher2.comms.sms.QuickSms.send(context, number, rejectSms)
        }) { Text(stringResource(I18nR.string.hc_reject_sms)) }
    }
    if (remindOpen) AlertDialog(onDismissRequest = { remindOpen = false },
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_remind_me_to_call_back))  } },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            listOf(5, 15, 30, 60).forEach { minutes ->
                TextButton(onClick = {
                    val state = TelosCallSession.ui.value
                    de.mm20.launcher2.comms.reminder.CallbackReminder.schedule(context, state.number, state.name, minutes)
                    remindOpen = false
                    TelosCallSession.reject()
                }) { Text(stringResource(I18nR.string.hc_in_minutes, minutes)) }
            }
        } }, confirmButton = {}, dismissButton = {
            TextButton(onClick = { remindOpen = false }) { Text(stringResource(I18nR.string.hc_cancel)) }
        })
}

private class CallControlSpec(
    val icon: Int,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun CallControl(icon: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (selected) TelosCyber.NeonCyan.copy(alpha = 0.22f)
            else MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .size(56.dp)
                .then(
                    if (selected) Modifier.border(1.2.dp, TelosCyber.NeonCyan, CircleShape)
                    else Modifier
                )
                .clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(icon),
                    contentDescription = label,
                    tint = if (selected) TelosCyber.NeonCyan
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InCallKeypad(onDigit: (Char) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
    Column {
        keys.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .clickable { onDigit(key.first()) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = key, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

@Composable
private fun SwipeAnswer(onAnswer: () -> Unit, onReject: () -> Unit) {
    val thumb = 64.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    var offsetX by remember { mutableStateOf(0f) }
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .height(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val maxPx = with(density) { ((maxWidth - thumb).coerceAtLeast(0.dp) / 2).toPx() }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("<", color = RdRedCall, style = MaterialTheme.typography.headlineSmall)
            Text(">", color = RdCallGreen, style = MaterialTheme.typography.headlineSmall)
        }
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface)
                .draggable(
                    orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                        offsetX = (offsetX + delta).coerceIn(-maxPx, maxPx)
                    },
                    onDragStopped = {
                        when {
                            offsetX > maxPx * 0.7f -> onAnswer()
                            offsetX < -maxPx * 0.7f -> onReject()
                        }
                        offsetX = 0f
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(BaseR.drawable.rd_ic_call_accept),
                contentDescription = null,
                tint = RdCallGreen,
            )
        }
    }
}
