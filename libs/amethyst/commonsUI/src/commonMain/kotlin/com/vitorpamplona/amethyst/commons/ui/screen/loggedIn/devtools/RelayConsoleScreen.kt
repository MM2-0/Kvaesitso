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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.devtools.ConsoleConnection
import com.vitorpamplona.amethyst.commons.devtools.ConsoleFrame
import com.vitorpamplona.amethyst.commons.devtools.ConsoleLink
import com.vitorpamplona.amethyst.commons.devtools.ConsoleSystemEvent
import com.vitorpamplona.amethyst.commons.devtools.EventInspectResult
import com.vitorpamplona.amethyst.commons.devtools.FilterDraft
import com.vitorpamplona.amethyst.commons.devtools.FilterField
import com.vitorpamplona.amethyst.commons.devtools.FrameDirection
import com.vitorpamplona.amethyst.commons.devtools.RelayConsoleViewModel
import com.vitorpamplona.amethyst.commons.devtools.SinceWindow
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.devtools_console_auth
import com.vitorpamplona.amethyst.commons.resources.devtools_console_auth_body
import com.vitorpamplona.amethyst.commons.resources.devtools_console_auth_review
import com.vitorpamplona.amethyst.commons.resources.devtools_console_auth_sign
import com.vitorpamplona.amethyst.commons.resources.devtools_console_auth_title
import com.vitorpamplona.amethyst.commons.resources.devtools_console_build
import com.vitorpamplona.amethyst.commons.resources.devtools_console_clear
import com.vitorpamplona.amethyst.commons.resources.devtools_console_close_sub
import com.vitorpamplona.amethyst.commons.resources.devtools_console_closed
import com.vitorpamplona.amethyst.commons.resources.devtools_console_composer
import com.vitorpamplona.amethyst.commons.resources.devtools_console_composer_offline
import com.vitorpamplona.amethyst.commons.resources.devtools_console_connect
import com.vitorpamplona.amethyst.commons.resources.devtools_console_connected
import com.vitorpamplona.amethyst.commons.resources.devtools_console_connecting
import com.vitorpamplona.amethyst.commons.resources.devtools_console_disconnect
import com.vitorpamplona.amethyst.commons.resources.devtools_console_edit_as_text
import com.vitorpamplona.amethyst.commons.resources.devtools_console_empty
import com.vitorpamplona.amethyst.commons.resources.devtools_console_event_field
import com.vitorpamplona.amethyst.commons.resources.devtools_console_failed
import com.vitorpamplona.amethyst.commons.resources.devtools_console_invalid_url
import com.vitorpamplona.amethyst.commons.resources.devtools_console_open_subs
import com.vitorpamplona.amethyst.commons.resources.devtools_console_preview
import com.vitorpamplona.amethyst.commons.resources.devtools_console_send_failed
import com.vitorpamplona.amethyst.commons.resources.devtools_console_title
import com.vitorpamplona.amethyst.commons.resources.devtools_console_tor
import com.vitorpamplona.amethyst.commons.resources.devtools_console_url
import com.vitorpamplona.amethyst.commons.resources.devtools_console_your_relays
import com.vitorpamplona.amethyst.commons.resources.devtools_event_invalid
import com.vitorpamplona.amethyst.commons.resources.devtools_event_unparseable
import com.vitorpamplona.amethyst.commons.resources.devtools_event_valid
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_any
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_authors
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_authors_hint
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_day
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_hour
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_ids
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_ids_hint
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_invalid
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_kinds
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_kinds_hint
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_limit
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_me
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_month
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_search
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_since
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_tag
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_tag_values
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_week
import com.vitorpamplona.amethyst.commons.resources.send
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource

/** What the builder sheet is building. REQ and COUNT share the filter form. */
private enum class BuilderMode(
    val verb: String,
) {
    REQ("REQ"),
    COUNT("COUNT"),
    EVENT("EVENT"),
}

/**
 * A websocketking.com for relays: one raw socket, every frame both ways as the relay sent it.
 *
 * Laid out like a chat. A connection bar on top (an address field until connected, then one line
 * of status), the frame log, the subscriptions still open as closable chips, and a composer: type
 * a raw frame, or tap + to build a REQ, COUNT or EVENT in a sheet that previews the exact frame.
 * AUTH is never automatic: the bar offers it and a dialog confirms who signs.
 */
@Composable
fun RelayConsoleScreen(
    initialUrl: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel =
        rememberViewModel(key = "RelayConsole") {
            RelayConsoleViewModel(accountViewModel.host.websocketBuilder, accountViewModel.account.signer)
        }
    val frames by viewModel.frames.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val link by viewModel.link.collectAsStateWithLifecycle()
    val challenge by viewModel.pendingChallenge.collectAsStateWithLifecycle()
    val openSubs by viewModel.openSubs.collectAsStateWithLifecycle()

    val myRelays =
        remember {
            (accountViewModel.account.homeRelays.flow.value + accountViewModel.account.outboxRelays.flow.value)
                .sortedBy { it.url }
                .toImmutableList()
        }

    var url by rememberSaveable { mutableStateOf(initialUrl ?: "") }
    var composer by rememberSaveableText()
    var builderOpen by remember { mutableStateOf(false) }
    var authDialog by remember { mutableStateOf(false) }
    val connected = connection == ConsoleConnection.CONNECTED

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(Res.string.devtools_console_title), nav) {
                if (frames.isNotEmpty()) {
                    IconButton(onClick = viewModel::clear) {
                        Icon(MaterialSymbols.DeleteSweep, contentDescription = stringRes(Res.string.devtools_console_clear))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize(),
        ) {
            val current = link
            if (current != null) {
                ConnectedBar(
                    link = current,
                    openSubs = openSubs.size,
                    authPending = challenge != null && viewModel.canSign,
                    onReviewAuth = { authDialog = true },
                    onDisconnect = viewModel::disconnect,
                )
            } else {
                AddressBar(
                    url = url,
                    connecting = connection == ConsoleConnection.CONNECTING,
                    suggestions = myRelays,
                    onUrlChange = { url = it },
                    onConnect = { viewModel.connect(url) },
                    onPick = {
                        url = it.url
                        viewModel.connect(it.url)
                    },
                )
            }

            FrameLog(frames, Modifier.weight(1f))

            if (openSubs.isNotEmpty()) {
                OpenSubscriptions(openSubs, onClose = viewModel::close)
            }

            Composer(
                text = composer,
                enabled = connected,
                onTextChange = { composer = it },
                onBuild = { builderOpen = true },
                onSend = { if (viewModel.send(composer)) composer = "" },
            )
        }
    }

    if (builderOpen) {
        BuilderSheet(
            myPubKey = accountViewModel.account.signer.pubKey,
            viewModel = viewModel,
            onSent = { builderOpen = false },
            onEditInComposer = {
                composer = it
                builderOpen = false
            },
            onDismiss = { builderOpen = false },
        )
    }

    val pending = challenge
    if (authDialog && pending != null) {
        AuthConfirmDialog(
            relay = link?.relay?.url ?: url,
            challenge = pending,
            npub = NPub.create(accountViewModel.account.signer.pubKey),
            onConfirm = {
                viewModel.answerAuth()
                authDialog = false
            },
            onDismiss = { authDialog = false },
        )
    }
}

// ---- top: the connection bar ----

/** Disconnected: one address field with Connect inside it, and the user's own relays one tap away. */
@Composable
private fun AddressBar(
    url: String,
    connecting: Boolean,
    suggestions: ImmutableList<NormalizedRelayUrl>,
    onUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    onPick: (NormalizedRelayUrl) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            placeholder = { Text(stringRes(Res.string.devtools_console_url)) },
            singleLine = true,
            enabled = !connecting,
            shape = RoundedCornerShape(28.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            leadingIcon = { Icon(MaterialSymbols.Dns, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (connecting) {
                    CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onConnect, enabled = url.isNotBlank(), modifier = Modifier.padding(end = 4.dp)) {
                        Text(stringRes(Res.string.devtools_console_connect))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (url.isNotBlank()) onConnect() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )

        if (suggestions.isNotEmpty() && !connecting) {
            Text(
                stringRes(Res.string.devtools_console_your_relays),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestions, key = { it.url }) { relay ->
                    SuggestionChip(
                        onClick = { onPick(relay) },
                        label = { Text(relay.displayUrl(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                }
            }
        }
    }
}

/** Connected: one line of status, plus the AUTH offer when the relay asks for it. */
@Composable
private fun ConnectedBar(
    link: ConsoleLink,
    openSubs: Int,
    authPending: Boolean,
    onReviewAuth: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column {
            Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        link.relay.displayUrl(),
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val details =
                        buildList {
                            add("${link.pingMillis} ms")
                            if (link.compression) add("deflate")
                            if (openSubs > 0) add(stringRes(Res.string.devtools_console_open_subs, openSubs))
                        }
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDisconnect) {
                    Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.devtools_console_disconnect))
                }
            }
            if (authPending) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(MaterialSymbols.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringRes(Res.string.devtools_console_auth),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                        TextButton(onClick = onReviewAuth) { Text(stringRes(Res.string.devtools_console_auth_review)) }
                    }
                }
            }
        }
    }
}

// ---- middle: the log ----

@Composable
private fun FrameLog(
    frames: ImmutableList<ConsoleFrame>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Follow new frames only while the reader is at the bottom, so scrolling up to read one
    // holds still during a busy REQ. Keyed on the newest id: at the 500-frame cap the size stops
    // changing while frames keep arriving.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - FOLLOW_SLACK
        }
    }
    LaunchedEffect(frames.lastOrNull()?.id) {
        if (frames.isNotEmpty() && atBottom) listState.scrollToItem(frames.lastIndex)
    }

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
    ) {
        if (frames.isEmpty()) {
            Text(
                stringRes(Res.string.devtools_console_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(8.dp)) {
            items(frames, key = { it.id }) { FrameRow(it) }
        }
    }
}

/** How many frames behind the end still counts as "at the bottom": a burst lands several at once. */
private const val FOLLOW_SLACK = 4

// ---- bottom: open subscriptions and the composer ----

/** The REQs still open, newest first; the x sends their CLOSE. */
@Composable
private fun OpenSubscriptions(
    subs: ImmutableList<String>,
    onClose: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        items(subs.reversed(), key = { it }) { sub ->
            val closeLabel = stringRes(Res.string.devtools_console_close_sub, sub)
            InputChip(
                selected = false,
                onClick = { onClose(sub) },
                label = { Text(sub, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = { Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) },
                trailingIcon = { Icon(MaterialSymbols.Close, contentDescription = closeLabel, modifier = Modifier.size(InputChipDefaults.IconSize)) },
            )
        }
    }
}

/** A chat-style input: + opens the builder, the field takes any raw frame, the arrow sends it. */
@Composable
private fun Composer(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onBuild: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilledTonalIconButton(onClick = onBuild, enabled = enabled, modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(MaterialSymbols.Add, contentDescription = stringRes(Res.string.devtools_console_build))
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = enabled,
            placeholder = {
                Text(
                    stringRes(if (enabled) Res.string.devtools_console_composer else Res.string.devtools_console_composer_offline),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            shape = RoundedCornerShape(24.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 6,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = onSend, enabled = enabled && text.isNotBlank(), modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(MaterialSymbols.AutoMirrored.Send, contentDescription = stringRes(Res.string.send))
        }
    }
}

// ---- the builder sheet ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuilderSheet(
    myPubKey: String,
    viewModel: RelayConsoleViewModel,
    onSent: () -> Unit,
    onEditInComposer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(BuilderMode.REQ) }
    var draft by remember { mutableStateOf(FilterDraft(kinds = "1", limit = "10")) }
    var eventJson by rememberSaveableText()
    // One id per sheet, so the preview and the sent frame agree.
    val subId = remember { RelayConsoleViewModel.newSubId() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BuilderMode.entries.forEachIndexed { index, it ->
                    SegmentedButton(
                        selected = mode == it,
                        onClick = { mode = it },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = BuilderMode.entries.size),
                    ) { Text(it.verb) }
                }
            }

            if (mode == BuilderMode.EVENT) {
                EventForm(
                    json = eventJson,
                    onJsonChange = { eventJson = it },
                    onSend = {
                        viewModel.sendEvent(it)
                        onSent()
                    },
                    onEditInComposer = { onEditInComposer("[\"EVENT\"," + it.toJson() + "]") },
                )
            } else {
                FilterForm(
                    verb = mode.verb,
                    subId = subId,
                    draft = draft,
                    myPubKey = myPubKey,
                    viewModel = viewModel,
                    onDraftChange = { draft = it },
                    onSend = {
                        viewModel.send(it)
                        onSent()
                    },
                    onEditInComposer = onEditInComposer,
                )
            }
        }
    }
}

@Composable
private fun FilterForm(
    verb: String,
    subId: String,
    draft: FilterDraft,
    myPubKey: String,
    viewModel: RelayConsoleViewModel,
    onDraftChange: (FilterDraft) -> Unit,
    onSend: (String) -> Unit,
    onEditInComposer: (String) -> Unit,
) {
    val result = remember(draft) { draft.build() }
    val invalid = result as? FilterDraft.Result.Invalid
    val frame = (result as? FilterDraft.Result.Ok)?.let { viewModel.frameFor(verb, subId, it.filter) }

    // Ascii, not Number: a numeric keyboard has no comma to separate kinds with.
    FormField(Res.string.devtools_filter_kinds, Res.string.devtools_filter_kinds_hint, draft.kinds, invalid, FilterField.KINDS, KeyboardType.Ascii) {
        onDraftChange(draft.copy(kinds = it))
    }
    FormField(
        Res.string.devtools_filter_authors,
        Res.string.devtools_filter_authors_hint,
        draft.authors,
        invalid,
        FilterField.AUTHORS,
        trailing = {
            IconButton(onClick = { onDraftChange(draft.copy(authors = (draft.authors.trim() + " " + NPub.create(myPubKey)).trim())) }) {
                Icon(MaterialSymbols.Person, contentDescription = stringRes(Res.string.devtools_filter_me))
            }
        },
    ) { onDraftChange(draft.copy(authors = it)) }
    FormField(Res.string.devtools_filter_ids, Res.string.devtools_filter_ids_hint, draft.ids, invalid, FilterField.IDS) {
        onDraftChange(draft.copy(ids = it))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft.tagName.removePrefix("#"),
            onValueChange = { onDraftChange(draft.copy(tagName = it.removePrefix("#").take(1))) },
            label = { Text(stringRes(Res.string.devtools_filter_tag)) },
            prefix = { Text("#") },
            singleLine = true,
            isError = invalid?.field == FilterField.TAG,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.width(88.dp),
        )
        OutlinedTextField(
            value = draft.tagValues,
            onValueChange = { onDraftChange(draft.copy(tagValues = it)) },
            label = { Text(stringRes(Res.string.devtools_filter_tag_values)) },
            singleLine = true,
            isError = invalid?.field == FilterField.TAG,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringRes(Res.string.devtools_filter_since), style = MaterialTheme.typography.labelLarge)
        val windows = listOf(null to Res.string.devtools_filter_any) + SinceWindow.entries.map { it to it.label() }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            windows.forEachIndexed { index, (window, label) ->
                SegmentedButton(
                    selected = draft.since == window,
                    onClick = { onDraftChange(draft.copy(since = window)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = windows.size),
                    icon = {},
                ) { Text(stringRes(label), maxLines = 1) }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FormField(Res.string.devtools_filter_limit, null, draft.limit, invalid, FilterField.LIMIT, KeyboardType.Number, Modifier.width(112.dp)) {
            onDraftChange(draft.copy(limit = it.filter(Char::isDigit)))
        }
        FormField(Res.string.devtools_filter_search, null, draft.search, null, FilterField.KINDS, modifier = Modifier.weight(1f)) {
            onDraftChange(draft.copy(search = it))
        }
    }

    // The other fields say what is wrong right under themselves; the tag pair has no room to.
    if (invalid?.field == FilterField.TAG) {
        Text(stringRes(Res.string.devtools_filter_invalid, invalid.token), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    Preview(frame)

    SheetActions(
        sendLabel = verb,
        enabled = frame != null,
        onEditInComposer = { frame?.let(onEditInComposer) },
        onSend = { frame?.let(onSend) },
    )
}

@Composable
private fun EventForm(
    json: String,
    onJsonChange: (String) -> Unit,
    onSend: (Event) -> Unit,
    onEditInComposer: (Event) -> Unit,
) {
    val result = rememberEventInspection(json).value
    val parsed = (result as? EventInspectResult.Parsed)?.inspection

    DevToolsInput(json, onJsonChange, stringRes(Res.string.devtools_console_event_field), minLines = 6, maxLines = 12)
    when {
        result is EventInspectResult.Unparseable ->
            DevToolsCheckRow(MaterialSymbols.ErrorOutline, stringRes(Res.string.devtools_event_unparseable, result.message), MaterialTheme.colorScheme.error)

        // An invalid event can still go out: seeing the relay reject it is the point.
        parsed != null ->
            DevToolsCheckRow(
                if (parsed.isValid) MaterialSymbols.CheckCircle else MaterialSymbols.Error,
                stringRes(if (parsed.isValid) Res.string.devtools_event_valid else Res.string.devtools_event_invalid),
                if (parsed.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
    }

    SheetActions(
        sendLabel = BuilderMode.EVENT.verb,
        enabled = parsed != null,
        onEditInComposer = { parsed?.let { onEditInComposer(it.event) } },
        onSend = { parsed?.let { onSend(it.event) } },
    )
}

@Composable
private fun FormField(
    label: StringResource,
    hint: StringResource?,
    value: String,
    invalid: FilterDraft.Result.Invalid?,
    field: FilterField,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    trailing: (@Composable () -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    val isError = invalid?.field == field
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringRes(label), maxLines = 1) },
        placeholder = hint?.let { { Text(stringRes(it), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingIcon = trailing,
        isError = isError,
        supportingText = if (isError) ({ Text(stringRes(Res.string.devtools_filter_invalid, invalid.token)) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = modifier,
    )
}

/** The exact frame the Send button puts on the wire, wrapped so nothing is hidden. */
@Composable
private fun Preview(frame: String?) {
    if (frame == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringRes(Res.string.devtools_console_preview), style = MaterialTheme.typography.labelLarge)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
            Text(
                frame,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().padding(10.dp),
            )
        }
    }
}

@Composable
private fun SheetActions(
    sendLabel: String,
    enabled: Boolean,
    onEditInComposer: () -> Unit,
    onSend: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onEditInComposer, enabled = enabled) { Text(stringRes(Res.string.devtools_console_edit_as_text)) }
        Button(onClick = onSend, enabled = enabled, modifier = Modifier.padding(start = 8.dp)) {
            Icon(MaterialSymbols.AutoMirrored.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringRes(Res.string.send) + " " + sendLabel, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun SinceWindow.label(): StringResource =
    when (this) {
        SinceWindow.HOUR -> Res.string.devtools_filter_hour
        SinceWindow.DAY -> Res.string.devtools_filter_day
        SinceWindow.WEEK -> Res.string.devtools_filter_week
        SinceWindow.MONTH -> Res.string.devtools_filter_month
    }

@Composable
private fun AuthConfirmDialog(
    relay: String,
    challenge: String,
    npub: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(MaterialSymbols.Lock, contentDescription = null) },
        title = { Text(stringRes(Res.string.devtools_console_auth_title)) },
        text = { Text(stringRes(Res.string.devtools_console_auth_body, relay, challenge, npub)) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringRes(Res.string.devtools_console_auth_sign)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

// ---- frames ----

/** One frame; long ones show six lines until tapped. */
@Composable
private fun FrameRow(frame: ConsoleFrame) {
    var expanded by rememberSaveable(frame.id) { mutableStateOf(false) }

    if (frame.direction == FrameDirection.SYSTEM) {
        Text(
            systemText(frame),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        return
    }

    val sent = frame.direction == FrameDirection.SENT
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 3.dp),
    ) {
        Icon(
            if (sent) MaterialSymbols.ArrowUpward else MaterialSymbols.ArrowDownward,
            contentDescription = null,
            tint = if (sent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(14.dp).padding(top = 2.dp),
        )
        frame.label?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = labelColor(it),
                modifier = Modifier.padding(start = 4.dp, end = 6.dp),
            )
        }
        Text(
            frame.text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (expanded) CopyIconButton(frame.text)
    }
}

@Composable
private fun labelColor(label: String): Color =
    when (label) {
        "EVENT" -> MaterialTheme.colorScheme.primary
        "EOSE", "COUNT" -> MaterialTheme.colorScheme.secondary
        "OK", "AUTH" -> MaterialTheme.colorScheme.tertiary
        "CLOSED", "NOTICE" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun systemText(frame: ConsoleFrame): String =
    when (frame.system) {
        ConsoleSystemEvent.CONNECTING -> stringRes(Res.string.devtools_console_connecting, frame.text)
        ConsoleSystemEvent.CONNECTED -> stringRes(Res.string.devtools_console_connected, frame.text)
        ConsoleSystemEvent.CLOSED -> stringRes(Res.string.devtools_console_closed, frame.text)
        ConsoleSystemEvent.FAILED -> stringRes(Res.string.devtools_console_failed, frame.text)
        ConsoleSystemEvent.INVALID_URL -> stringRes(Res.string.devtools_console_invalid_url, frame.text)
        ConsoleSystemEvent.SEND_FAILED -> stringRes(Res.string.devtools_console_send_failed)
        ConsoleSystemEvent.WAITING_FOR_TOR -> stringRes(Res.string.devtools_console_tor, frame.text)
        null -> frame.text
    }
