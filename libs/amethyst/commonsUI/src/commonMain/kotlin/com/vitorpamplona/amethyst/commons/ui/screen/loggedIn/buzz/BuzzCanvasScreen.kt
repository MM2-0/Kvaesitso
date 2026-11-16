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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzCanvasWriter
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaceStates
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_canvas_body_label
import com.vitorpamplona.amethyst.commons.resources.buzz_canvas_conflict
import com.vitorpamplona.amethyst.commons.resources.buzz_canvas_edit
import com.vitorpamplona.amethyst.commons.resources.buzz_canvas_empty
import com.vitorpamplona.amethyst.commons.resources.buzz_canvas_head_in_future
import com.vitorpamplona.amethyst.commons.resources.buzz_canvas_save
import com.vitorpamplona.amethyst.commons.resources.buzz_canvas_save_failed
import com.vitorpamplona.amethyst.commons.resources.buzz_canvas_title
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.stream.CanvasEvent
import com.vitorpamplona.quartz.buzz.workspace.isBuzzDm
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A Buzz **canvas** (kind 40100): the newest shared markdown document for ONE channel. The relay
 * requires an `h` channel tag on every canvas (its own `channel_scoped_content_kinds_require_h_tags`
 * invariant), so each channel has its own — this is not a workspace-wide document.
 *
 * The canvas is overlay state held in [BuzzWorkspaceStates] (never a timeline row — a channel has
 * one live canvas, last-write-wins), so this reads the registry by the channel's `h` id and
 * re-composes off `canvasUpdates` when a newer revision lands.
 *
 * The edit FAB flips into a plain markdown editor; saving publishes a fresh [CanvasEvent] to the
 * channel's host [relayUrl] through [BuzzCanvasWriter], which asserts the revision the editor was
 * opened on (`expected-revision`) so a concurrent edit is refused by the relay instead of silently
 * overwritten. An accepted revision is folded back into [BuzzWorkspaceStates] so the view updates
 * without a manual refresh; a conflict keeps the editor open with the draft intact.
 */
@Composable
fun BuzzCanvasScreen(
    channelId: String,
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val state = remember(channelId) { BuzzWorkspaceStates.getOrCreate(channelId) }
    // Re-read canvasNote whenever a newer canvas is consumed.
    val version by state.canvasUpdates.collectAsStateWithLifecycle()
    val canvas = remember(version) { state.canvasNote }
    val content = canvas?.event?.content

    // The channel this canvas belongs to, for the subtitle and the edit gate. Null until its
    // kind-39000 lands, which only costs the subtitle — the canvas itself is keyed by the raw id.
    val channel =
        remember(channelId, relayUrl) {
            RelayUrlNormalizer.normalizeOrNull(relayUrl)?.let { relay ->
                LocalCache.getRelayGroupChannelIfExists(GroupId(channelId, relay))
            }
        }
    val channelName = channel?.toBestDisplayName()

    // Buzz never lets a DM's canvas be written: its editor's `canEdit` is `canEditNarrative`, which
    // excludes `channelType === "dm"` outright. A DM reaching this screen at all means a canvas
    // already exists (see the top bar's gate), so render it read-only rather than offering an edit
    // that Buzz's own client would never show.
    val canEdit = channel?.event?.isBuzzDm() != true

    // The head the editor was opened on, snapshotted at edit start (not the live head): a newer
    // revision landing while the editor is open must surface as a conflict, not be overwritten.
    var editBase by remember { mutableStateOf<CanvasEditBase?>(null) }

    editBase?.let { base ->
        CanvasEditor(
            channelId = channelId,
            relayUrl = relayUrl,
            base = base,
            accountViewModel = accountViewModel,
            onClose = { editBase = null },
        )
        return
    }

    Scaffold(
        // Name the channel under the title, like the Threads screen: a canvas belongs to one channel,
        // and arriving here from a chat should not lose track of which.
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    Column {
                        Text(
                            text = stringRes(Res.string.buzz_canvas_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (channelName != null) {
                            Text(
                                text = channelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                nav = nav,
            )
        },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = { editBase = CanvasEditBase(content.orEmpty(), canvas?.idHex, canvas?.createdAt()) },
                    shape = CircleShape,
                ) {
                    Icon(symbol = MaterialSymbols.Edit, contentDescription = stringRes(Res.string.buzz_canvas_edit))
                }
            }
        },
    ) { padding ->
        if (content.isNullOrBlank()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringRes(Res.string.buzz_canvas_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }

        val bgColor = remember { mutableStateOf(Color.Transparent) }
        Box(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            TranslatableRichTextViewer(
                content = content,
                canPreview = true,
                quotesLeft = 1,
                modifier = Modifier,
                tags = EmptyTagList,
                backgroundColor = bgColor,
                id = canvas.idHex,
                callbackUri = canvas.toNostrUri(),
                authorPubKey = canvas.author?.pubkeyHex,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

/** The canvas revision an edit started from: its text, and its id/created_at (null when there was no canvas). */
private class CanvasEditBase(
    val content: String,
    val headId: HexKey?,
    val headCreatedAt: Long?,
)

/** The markdown editor: a full-height text field for the canvas body with a Save action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CanvasEditor(
    channelId: String,
    relayUrl: String,
    base: CanvasEditBase,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
) {
    var text by remember { mutableStateOf(base.content) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Back cancels the edit and returns to the rendered canvas rather than leaving the screen.
    PlatformBackHandler(enabled = !saving, onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.buzz_canvas_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !saving) {
                        Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.cancel))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                shape = CircleShape,
                onClick = {
                    val relay =
                        RelayUrlNormalizer.normalizeOrNull(relayUrl) ?: run {
                            error = "Invalid relay url"
                            return@FloatingActionButton
                        }
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            val outcome =
                                withContext(Dispatchers.IO) {
                                    BuzzCanvasWriter.save(
                                        account = accountViewModel.account,
                                        relay = relay,
                                        channelId = channelId,
                                        markdown = text,
                                        headId = base.headId,
                                        headCreatedAt = base.headCreatedAt,
                                    )
                                }
                            when (outcome) {
                                is BuzzCanvasWriter.Outcome.Saved -> {
                                    onClose()
                                }

                                is BuzzCanvasWriter.Outcome.Conflict -> {
                                    saving = false
                                    error = loadStringRes(Res.string.buzz_canvas_conflict)
                                }

                                BuzzCanvasWriter.Outcome.HeadTooFarInFuture -> {
                                    saving = false
                                    error = loadStringRes(Res.string.buzz_canvas_head_in_future)
                                }

                                is BuzzCanvasWriter.Outcome.Failed -> {
                                    saving = false
                                    error = loadStringRes(Res.string.buzz_canvas_save_failed, outcome.message)
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            saving = false
                            error = loadStringRes(Res.string.buzz_canvas_save_failed, e.message ?: e::class.simpleName ?: "")
                        }
                    }
                },
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.padding(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(symbol = MaterialSymbols.Check, contentDescription = stringRes(Res.string.buzz_canvas_save))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            SpellCheckedOutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                modifier = Modifier.fillMaxSize(),
                enabled = !saving,
                label = { Text(stringRes(Res.string.buzz_canvas_body_label)) },
            )
            error?.let {
                SelectionContainer(Modifier.align(Alignment.BottomStart)) {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
