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
package com.vitorpamplona.amethyst.commons.drawing.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.drawing.DrawingBackgroundStore
import com.vitorpamplona.amethyst.commons.drawing.DrawingEditorState
import com.vitorpamplona.amethyst.commons.drawing.RenderedDrawing
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ai_suggested_alt_text_dismiss
import com.vitorpamplona.amethyst.commons.resources.ai_suggested_alt_text_hint
import com.vitorpamplona.amethyst.commons.resources.drawing_add_to_post
import com.vitorpamplona.amethyst.commons.resources.drawing_alt_text
import com.vitorpamplona.amethyst.commons.resources.drawing_change_paper
import com.vitorpamplona.amethyst.commons.resources.drawing_discard
import com.vitorpamplona.amethyst.commons.resources.drawing_discard_message
import com.vitorpamplona.amethyst.commons.resources.drawing_discard_title
import com.vitorpamplona.amethyst.commons.resources.drawing_export_failed
import com.vitorpamplona.amethyst.commons.resources.drawing_keep_drawing
import com.vitorpamplona.amethyst.commons.resources.drawing_making_gif
import com.vitorpamplona.amethyst.commons.resources.drawing_next
import com.vitorpamplona.amethyst.commons.resources.drawing_open
import com.vitorpamplona.amethyst.commons.resources.drawing_preview
import com.vitorpamplona.amethyst.commons.resources.drawing_preview_caption
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.mediaUriOfFile
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.commons.ui.components.edgeToEdgeDialogProperties
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.note.buttons.CloseButton
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The composer's "draw by hand" tool: opens the editor and hands back the finished drawing. */
@Composable
fun DrawingButton(
    readMedia: suspend (MediaUri) -> ByteArray?,
    backgrounds: DrawingBackgroundStore?,
    enabled: Boolean = true,
    onDrawing: (drawing: RenderedDrawing, alt: String?) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    if (show) {
        DrawingEditorDialog(
            readMedia = readMedia,
            backgrounds = backgrounds,
            onDismiss = { show = false },
            onDone = { drawing, alt ->
                show = false
                onDrawing(drawing, alt)
            },
        )
    }

    val label = stringRes(Res.string.drawing_open)
    HoverTooltip(label) {
        IconButton(onClick = { show = true }, enabled = enabled) {
            Icon(
                symbol = MaterialSymbols.Draw,
                contentDescription = label,
                modifier = Modifier.height(22.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** [DrawingButton] for a composer: pictures open and papers are saved through [accountViewModel]'s account. */
@Composable
fun DrawingButton(
    accountViewModel: AccountViewModel,
    enabled: Boolean = true,
    onDrawing: (drawing: RenderedDrawing, alt: String?) -> Unit,
) {
    val pubKey = accountViewModel.account.pubKey
    DrawingButton(
        readMedia = accountViewModel.mediaUploader::readBytes,
        backgrounds = remember(pubKey) { accountViewModel.host.drawingBackgroundsDir?.let { DrawingBackgroundStore(it / pubKey) } },
        enabled = enabled,
        onDrawing = onDrawing,
    )
}

private enum class Step { DRAW, PREVIEW }

/**
 * Full-window editor for a hand drawing, in two steps.
 *
 * **Draw**: the paper fills the window and a floating dock holds the pens; the
 * paper itself is picked from a sheet behind the chip in the top bar.
 * **Preview**: the drawing replays exactly as the posted GIF will, next to the
 * alt text and "Add to post", which renders the GIF and PNG and hands them over.
 *
 * [readMedia] reads a picture the user picks as a background; [backgrounds]
 * keeps it for next time (null: the picture is only kept while the editor is open).
 */
@Composable
fun DrawingEditorDialog(
    readMedia: suspend (MediaUri) -> ByteArray?,
    backgrounds: DrawingBackgroundStore?,
    onDismiss: () -> Unit,
    onDone: (drawing: RenderedDrawing, alt: String?) -> Unit,
) {
    val state = remember { DrawingEditorState() }
    // One painter for the canvas, the chip and the preview: a photo becomes a bitmap once, not once per view.
    val background = state.background
    val painter = remember(background) { BackgroundPainter(background) }
    val picker = rememberPaperPicker(state, backgrounds)
    var step by remember { mutableStateOf(Step.DRAW) }
    var showPaper by remember { mutableStateOf(false) }
    var pickingPicture by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var exportFailed by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // The on-device describer the upload screen uses (Gemini Nano where the device has it, none
    // elsewhere): it reads the finished drawing once the user reaches the preview.
    val appServices = LocalAppServices.current
    val describer = remember { appServices.createAltTextSuggester() }
    var describing by remember { mutableStateOf(false) }
    var aiSuggested by remember { mutableStateOf(false) }
    // Which drawing was last described to the end: going back to draw and returning unchanged must
    // not run the model again. Strokes only change at the end, so the count and the last one tell.
    var describedFor by remember { mutableStateOf<List<Any?>?>(null) }
    DisposableEffect(describer) { onDispose { describer?.close() } }

    LaunchedEffect(step, state.strokes.size, state.background) {
        // The user's own words win: describe only an empty field or replace our own suggestion.
        if (describer == null || step != Step.PREVIEW || state.isEmpty || (alt.isNotEmpty() && !aiSuggested)) return@LaunchedEffect
        val drawing = listOf(state.strokes.size, state.strokes.lastOrNull(), state.background)
        if (drawing == describedFor) return@LaunchedEffect
        describing = true
        try {
            val still = DrawingExporter.writeStill(state.background, state.strokes.toList())
            val suggestion =
                try {
                    describer.suggestAltText(mediaUriOfFile(still))
                } finally {
                    platformFileSystem.delete(still, mustExist = false)
                }
            // Only a run that finished counts: one cut short by going back to draw must run again.
            describedFor = drawing
            // Once "Add to post" is pressed, the alt text being posted must not change under the user.
            if (progress == null && (alt.isEmpty() || aiSuggested)) {
                if (suggestion != null) {
                    alt = suggestion
                    aiSuggested = true
                } else if (aiSuggested) {
                    // Our earlier suggestion described a drawing that has since changed.
                    alt = ""
                    aiSuggested = false
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("DrawingEditorDialog", "Could not describe the drawing", e)
        } finally {
            describing = false
        }
    }

    val back = {
        when {
            progress != null -> {}
            showPaper -> showPaper = false
            step == Step.PREVIEW -> step = Step.DRAW
            state.isEmpty -> onDismiss()
            else -> confirmDiscard = true
        }
    }

    val export: () -> Unit = {
        exportFailed = false
        progress = 0f
        val strokes = state.strokes.toList()
        val exported = state.background
        scope.launch {
            try {
                val drawing = DrawingExporter.export(exported, strokes) { progress = it }
                onDone(drawing, alt.trim().ifEmpty { null })
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("DrawingEditorDialog", "Could not export the drawing", e)
                exportFailed = true
            } finally {
                progress = null
            }
        }
    }

    if (pickingPicture) {
        GallerySelectSingle(imagesOnly = true) { media ->
            pickingPicture = false
            if (media != null) {
                scope.launch {
                    val bytes =
                        try {
                            readMedia(media.uri)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w("DrawingEditorDialog", "Could not read the picked picture", e)
                            null
                        }
                    picker.addPicture(bytes)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = back,
        properties = edgeToEdgeDialogProperties(dismissOnClickOutside = false),
    ) {
        SetDialogToEdgeToEdge()
        Box(Modifier.fillMaxSize()) {
            Crossfade(step) { current ->
                when (current) {
                    Step.DRAW -> {
                        DrawStep(
                            state = state,
                            painter = painter,
                            onClose = back,
                            onPaper = { showPaper = true },
                            onNext = { step = Step.PREVIEW },
                        )
                    }

                    Step.PREVIEW -> {
                        PreviewStep(
                            state = state,
                            painter = painter,
                            alt = alt,
                            onAltChange = {
                                alt = it
                                aiSuggested = false
                            },
                            describing = describing,
                            aiSuggested = aiSuggested,
                            onDismissSuggestion = {
                                alt = ""
                                aiSuggested = false
                            },
                            progress = progress,
                            failed = exportFailed,
                            onBack = back,
                            onAdd = export,
                        )
                    }
                }
            }

            PaperSheet(
                visible = showPaper,
                state = state,
                picker = picker,
                onAddPicture = { pickingPicture = true },
                onDismiss = { showPaper = false },
            )
        }

        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text(stringRes(Res.string.drawing_discard_title)) },
                text = { Text(stringRes(Res.string.drawing_discard_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDiscard = false
                        onDismiss()
                    }) { Text(stringRes(Res.string.drawing_discard)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) { Text(stringRes(Res.string.drawing_keep_drawing)) }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawStep(
    state: DrawingEditorState,
    painter: BackgroundPainter,
    onClose: () -> Unit,
    onPaper: () -> Unit,
    onNext: () -> Unit,
) {
    Scaffold(
        topBar = {
            ShorterTopAppBar(
                navigationIcon = { CloseButton(onPress = onClose, modifier = Modifier.padding(horizontal = 8.dp)) },
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PaperChip(painter, onPaper)
                    }
                },
                actions = {
                    Button(onClick = onNext, enabled = !state.isEmpty, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringRes(Res.string.drawing_next))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { pad ->
        Surface(Modifier.padding(pad).consumeWindowInsets(pad)) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                DrawingCanvas(state, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), painter)
                DrawingDock(state, Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewStep(
    state: DrawingEditorState,
    painter: BackgroundPainter,
    alt: String,
    onAltChange: (String) -> Unit,
    describing: Boolean,
    aiSuggested: Boolean,
    onDismissSuggestion: () -> Unit,
    progress: Float?,
    failed: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
) {
    Scaffold(
        topBar = {
            ShorterTopAppBar(
                navigationIcon = {
                    val keepDrawing = stringRes(Res.string.drawing_keep_drawing)
                    IconButton(onClick = onBack, enabled = progress == null) {
                        Icon(MaterialSymbols.AutoMirrored.ArrowBack, keepDrawing)
                    }
                },
                title = {
                    Text(stringRes(Res.string.drawing_preview), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                },
                actions = { Spacer(Modifier.width(48.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { pad ->
        Surface(Modifier.padding(pad).consumeWindowInsets(pad).imePaddingSafe()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                // A snapshot of the strokes: the preview replays what will be posted.
                val strokes = remember(state.strokes.size) { state.strokes.toList() }
                ReplayPreview(
                    painter.background,
                    strokes,
                    Modifier
                        .weight(1f)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    painter,
                )
                Text(
                    stringRes(Res.string.drawing_preview_caption),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = alt,
                            onValueChange = onAltChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringRes(Res.string.drawing_alt_text)) },
                            trailingIcon = {
                                if (describing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(16.dp),
                        )
                        if (aiSuggested) {
                            AssistChip(
                                onClick = onDismissSuggestion,
                                label = { Text(stringRes(Res.string.ai_suggested_alt_text_hint)) },
                                leadingIcon = {
                                    Icon(MaterialSymbols.AutoAwesome, null, Modifier.size(AssistChipDefaults.IconSize))
                                },
                                trailingIcon = {
                                    Icon(
                                        MaterialSymbols.Close,
                                        stringRes(Res.string.ai_suggested_alt_text_dismiss),
                                        Modifier.size(AssistChipDefaults.IconSize),
                                    )
                                },
                            )
                        }
                        Button(onClick = onAdd, enabled = progress == null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            if (progress != null) {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(stringRes(Res.string.drawing_making_gif))
                            } else {
                                Icon(MaterialSymbols.AutoMirrored.Send, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringRes(Res.string.drawing_add_to_post))
                            }
                        }
                        if (failed) Text(stringRes(Res.string.drawing_export_failed), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** The current paper as a small sample and its name; opens the paper sheet. */
@Composable
private fun PaperChip(
    painter: BackgroundPainter,
    onClick: () -> Unit,
) {
    val label = stringRes(Res.string.drawing_change_paper)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Row(Modifier.padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(26.dp).clip(CircleShape)) {
                with(painter) {
                    drawBase()
                    drawOverlay()
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(stringRes(paperLabel(painter.background)), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Icon(MaterialSymbols.ExpandMore, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
