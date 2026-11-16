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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.vitorpamplona.amethyst.commons.drawing.DrawingBackground
import com.vitorpamplona.amethyst.commons.drawing.DrawingBackgroundStore
import com.vitorpamplona.amethyst.commons.drawing.DrawingEditorState
import com.vitorpamplona.amethyst.commons.drawing.DrawingPaper
import com.vitorpamplona.amethyst.commons.drawing.PaperPattern
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drawing_add_picture
import com.vitorpamplona.amethyst.commons.resources.drawing_background_chalkboard
import com.vitorpamplona.amethyst.commons.resources.drawing_background_dots
import com.vitorpamplona.amethyst.commons.resources.drawing_background_grid
import com.vitorpamplona.amethyst.commons.resources.drawing_background_notebook
import com.vitorpamplona.amethyst.commons.resources.drawing_background_photo
import com.vitorpamplona.amethyst.commons.resources.drawing_background_photo_failed
import com.vitorpamplona.amethyst.commons.resources.drawing_background_plain
import com.vitorpamplona.amethyst.commons.resources.drawing_background_remove
import com.vitorpamplona.amethyst.commons.resources.drawing_background_saved
import com.vitorpamplona.amethyst.commons.resources.drawing_background_sticky_mint
import com.vitorpamplona.amethyst.commons.resources.drawing_background_sticky_pink
import com.vitorpamplona.amethyst.commons.resources.drawing_background_sticky_sky
import com.vitorpamplona.amethyst.commons.resources.drawing_background_sticky_yellow
import com.vitorpamplona.amethyst.commons.resources.drawing_paper
import com.vitorpamplona.amethyst.commons.resources.drawing_your_pictures
import com.vitorpamplona.amethyst.commons.service.image.encodeArgbAsPng
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import org.jetbrains.compose.resources.StringResource

/**
 * A picture saved in [DrawingBackgroundStore]. Only its path is held: the thumbnail loads the file
 * through Coil (which caches it by path) and its bytes are read when it is picked.
 */
class SavedBackground(
    val path: Path,
)

/**
 * The user's own pictures for the editor: the ones saved on the device, the one
 * picked this session when nothing can be saved, which one is on the paper, and
 * whether one is loading. Lives as long as the editor so the sheet can come and go.
 */
@Stable
class PaperPicker(
    private val state: DrawingEditorState,
    private val backgrounds: DrawingBackgroundStore?,
    private val context: PlatformContext,
) {
    var saved by mutableStateOf(emptyList<SavedBackground>())
        private set
    var unsaved by mutableStateOf<DrawingBackground.Photo?>(null)
        private set
    var selectedSaved by mutableStateOf<Path?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    suspend fun reloadSaved() {
        val store = backgrounds ?: return
        saved = store.list().map { SavedBackground(it) }
    }

    fun choosePaper(paper: DrawingPaper) {
        selectedSaved = null
        state.changeBackground(DrawingBackground.Paper(paper))
    }

    fun chooseUnsaved(photo: DrawingBackground.Photo) {
        selectedSaved = null
        state.changeBackground(photo)
    }

    suspend fun chooseSaved(item: SavedBackground) {
        val png = backgrounds?.read(item.path)
        if (png == null) {
            failed = true
            return
        }
        if (load(png) != null) selectedSaved = item.path
    }

    /** A picture the user just picked: loads it onto the paper and keeps a copy for next time. */
    suspend fun addPicture(bytes: ByteArray?) {
        if (bytes == null) {
            failed = true
            return
        }
        val loaded = load(bytes) ?: return
        val store = backgrounds
        if (store == null) {
            unsaved = loaded
        } else {
            // Keep the cropped, scaled copy, not the original: it is what the editor needs next time.
            try {
                val png = withContext(Dispatchers.Default) { encodeArgbAsPng(loaded.pixels, loaded.width, loaded.height) }
                selectedSaved = store.save(png)
                reloadSaved()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // The picture is already on the paper; it just will not come back next time.
                Log.w("PaperPicker", "Could not save the background picture", e)
                unsaved = loaded
            }
        }
    }

    suspend fun remove(item: SavedBackground) {
        backgrounds?.delete(item.path)
        if (selectedSaved == item.path) choosePaper(DrawingPaper.PLAIN)
        reloadSaved()
    }

    private suspend fun load(bytes: ByteArray): DrawingBackground.Photo? {
        loading = true
        failed = false
        val loaded =
            try {
                PhotoBackgroundLoader.load(context, bytes)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("PaperPicker", "Could not load the background picture", e)
                null
            }
        loading = false
        if (loaded == null) {
            failed = true
        } else {
            // Whatever saved picture was on the paper is not any more; the caller marks the new one.
            selectedSaved = null
            state.changeBackground(loaded)
        }
        return loaded
    }
}

@Composable
fun rememberPaperPicker(
    state: DrawingEditorState,
    backgrounds: DrawingBackgroundStore?,
): PaperPicker {
    val context = LocalPlatformContext.current
    val picker = remember(state, backgrounds) { PaperPicker(state, backgrounds, context) }
    LaunchedEffect(picker) { picker.reloadSaved() }
    return picker
}

/** The paper's name, for the chip that opens the sheet. */
fun paperLabel(background: DrawingBackground): StringResource =
    when (background) {
        is DrawingBackground.Photo -> Res.string.drawing_background_photo
        is DrawingBackground.Paper -> PAPERS.first { it.first == background.paper }.second
    }

/**
 * A sheet rising from the bottom of the editor with every paper drawn as a real
 * sample, sticky notes as little notes, and the user's own pictures below them.
 */
@Composable
fun PaperSheet(
    visible: Boolean,
    state: DrawingEditorState,
    picker: PaperPicker,
    onAddPicture: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(SCRIM)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Surface(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    // Taps inside the sheet must not fall through to the scrim.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 12.dp,
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                ) {
                    Box(
                        Modifier
                            .padding(vertical = 12.dp)
                            .align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                    Text(stringRes(Res.string.drawing_paper), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        PAPERS.forEach { (paper, label) ->
                            PaperCard(
                                background = remember(paper) { DrawingBackground.Paper(paper) },
                                label = stringRes(label),
                                selected = (state.background as? DrawingBackground.Paper)?.paper == paper,
                                onClick = { picker.choosePaper(paper) },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(stringRes(Res.string.drawing_your_pictures), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AddPictureCard(picker.loading, onAddPicture)
                        picker.unsaved?.let { photo ->
                            PaperCard(photo, stringRes(Res.string.drawing_background_photo), selected = state.background === photo, showLabel = false) {
                                picker.chooseUnsaved(photo)
                            }
                        }
                        picker.saved.forEach { item ->
                            SavedPictureCard(
                                item = item,
                                selected = picker.selectedSaved == item.path && state.background is DrawingBackground.Photo,
                                onClick = { if (!picker.loading) scope.launch { picker.chooseSaved(item) } },
                                onRemove = { scope.launch { picker.remove(item) } },
                            )
                        }
                    }
                    if (picker.failed) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringRes(Res.string.drawing_background_photo_failed), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** A paper drawn as a sample of itself. Sticky notes sit slightly askew with a shadow, like real ones. */
@Composable
private fun PaperCard(
    background: DrawingBackground,
    label: String,
    selected: Boolean,
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    val painter = remember(background) { BackgroundPainter(background) }
    val sticky = (background as? DrawingBackground.Paper)?.paper?.pattern == PaperPattern.GLUE_STRIP
    val shape = RoundedCornerShape(if (sticky) 3.dp else 10.dp)
    val height = (CARD_WIDTH * background.aspect).coerceAtMost(CARD_WIDTH * 1.25f)
    Column(
        Modifier
            .width(CARD_WIDTH + 12.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(CARD_WIDTH * 1.25f + 6.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .size(CARD_WIDTH, height)
                    .rotate(if (sticky) STICKY_TILT else 0f)
                    .shadow(if (sticky) 6.dp else 1.dp, shape)
                    .clip(shape)
                    .border(selectionBorder(selected), shape),
            ) {
                with(painter) {
                    drawBase()
                    drawOverlay()
                }
            }
        }
        if (showLabel) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun selectionBorder(selected: Boolean) = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun AddPictureCard(
    loading: Boolean,
    onClick: () -> Unit,
) {
    val label = stringRes(Res.string.drawing_add_picture)
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(CARD_WIDTH)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = !loading, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(MaterialSymbols.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary)
                Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
            }
        }
    }
}

/** A saved picture's thumbnail; once selected, it also offers to remove the picture from the device. */
@Composable
private fun SavedPictureCard(
    item: SavedBackground,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalPlatformContext.current
    val label = stringRes(Res.string.drawing_background_saved)
    val shape = RoundedCornerShape(10.dp)
    val request =
        remember(item.path) {
            ImageRequest
                .Builder(context)
                .data(item.path)
                .size(THUMBNAIL_PX)
                .build()
        }
    Box(Modifier.size(CARD_WIDTH)) {
        AsyncImage(
            model = request,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(CARD_WIDTH)
                    .clip(shape)
                    .border(selectionBorder(selected), shape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        )
        if (selected) {
            val remove = stringRes(Res.string.drawing_background_remove)
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .clickable(role = Role.Button, onClickLabel = remove, onClick = onRemove)
                    .semantics { contentDescription = remove },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MaterialSymbols.Close, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}

internal val PAPERS =
    listOf(
        DrawingPaper.PLAIN to Res.string.drawing_background_plain,
        DrawingPaper.STICKY_YELLOW to Res.string.drawing_background_sticky_yellow,
        DrawingPaper.STICKY_PINK to Res.string.drawing_background_sticky_pink,
        DrawingPaper.STICKY_MINT to Res.string.drawing_background_sticky_mint,
        DrawingPaper.STICKY_SKY to Res.string.drawing_background_sticky_sky,
        DrawingPaper.NOTEBOOK to Res.string.drawing_background_notebook,
        DrawingPaper.GRID to Res.string.drawing_background_grid,
        DrawingPaper.DOTS to Res.string.drawing_background_dots,
        DrawingPaper.CHALKBOARD to Res.string.drawing_background_chalkboard,
    )

private val SCRIM = Color(0x99000000)
private val CARD_WIDTH = 72.dp
private const val STICKY_TILT = -3f
private const val THUMBNAIL_PX = 216
