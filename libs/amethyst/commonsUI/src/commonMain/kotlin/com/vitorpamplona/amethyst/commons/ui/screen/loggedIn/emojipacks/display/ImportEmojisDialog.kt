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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiArchiveLimits
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiImport
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.emoji_import_added
import com.vitorpamplona.amethyst.commons.resources.emoji_import_choose_files
import com.vitorpamplona.amethyst.commons.resources.emoji_import_done
import com.vitorpamplona.amethyst.commons.resources.emoji_import_example_file
import com.vitorpamplona.amethyst.commons.resources.emoji_import_existing_skipped
import com.vitorpamplona.amethyst.commons.resources.emoji_import_failed
import com.vitorpamplona.amethyst.commons.resources.emoji_import_more
import com.vitorpamplona.amethyst.commons.resources.emoji_import_naming
import com.vitorpamplona.amethyst.commons.resources.emoji_import_none_added
import com.vitorpamplona.amethyst.commons.resources.emoji_import_pick_formats
import com.vitorpamplona.amethyst.commons.resources.emoji_import_pick_title
import com.vitorpamplona.amethyst.commons.resources.emoji_import_private
import com.vitorpamplona.amethyst.commons.resources.emoji_import_private_hint
import com.vitorpamplona.amethyst.commons.resources.emoji_import_public
import com.vitorpamplona.amethyst.commons.resources.emoji_import_public_hint
import com.vitorpamplona.amethyst.commons.resources.emoji_import_reading
import com.vitorpamplona.amethyst.commons.resources.emoji_import_skipped
import com.vitorpamplona.amethyst.commons.resources.emoji_import_title
import com.vitorpamplona.amethyst.commons.resources.emoji_import_too_large
import com.vitorpamplona.amethyst.commons.resources.emoji_import_truncated
import com.vitorpamplona.amethyst.commons.resources.emoji_import_truncated_hint
import com.vitorpamplona.amethyst.commons.resources.emoji_import_unnamed
import com.vitorpamplona.amethyst.commons.resources.emoji_import_unnamed_hint
import com.vitorpamplona.amethyst.commons.resources.emoji_import_unreadable
import com.vitorpamplona.amethyst.commons.resources.emoji_import_unsupported
import com.vitorpamplona.amethyst.commons.resources.emoji_import_uploading
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.MultiDocumentSelect
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.StringResource

// Providers label .zip files inconsistently, octet-stream included; the view model tells files
// apart by name and lists anything else as unsupported.
private val EMOJI_IMPORT_MIME_TYPES =
    listOf("image/*", "application/zip", "application/x-zip-compressed", "application/x-zip", "application/octet-stream")

/** Names shown per leftover group before the rest collapse into "+N more". */
private const val MAX_NAMES_PER_GROUP = 12

/**
 * Bulk import into one of the user's packs: pick images and .zip archives, watch each one
 * upload, then see what came in and what was left out. The import runs in [viewModel], so its
 * state outlasts a recomposition of the screen.
 */
@Composable
fun ImportEmojisDialog(
    viewModel: EmojiPackViewModel,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    var isPrivate by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        MultiDocumentSelect(EMOJI_IMPORT_MIME_TYPES) { files ->
            picking = false
            if (files.isNotEmpty()) {
                accountViewModel.launchSigner {
                    viewModel.importFiles(files, accountViewModel.mediaUploader, isPrivate)
                }
            }
        }
    }

    ImportEmojisDialogView(
        running = viewModel.importRunning,
        items = viewModel.importItems,
        result = viewModel.importResult,
        isPrivate = isPrivate,
        onPrivateChange = { isPrivate = it },
        onChooseFiles = { picking = true },
        onClose = {
            viewModel.clearImport()
            onDismiss()
        },
    )
}

/**
 * The dialog's three faces: options before an import, the live upload grid while [running],
 * and the outcome once there is a [result].
 */
@Composable
fun ImportEmojisDialogView(
    running: Boolean,
    items: ImmutableList<EmojiImportItem>,
    result: EmojiImportResult?,
    isPrivate: Boolean,
    onPrivateChange: (Boolean) -> Unit,
    onChooseFiles: () -> Unit,
    onClose: () -> Unit,
) {
    // The outcome, once the import that produced it is over.
    val finished = result?.takeIf { !running }

    AlertDialog(
        // Dismissing mid-import would only hide the uploads, not stop them.
        onDismissRequest = { if (!running) onClose() },
        icon = { HeroIcon(finished?.heroKind() ?: HeroKind.IMPORT) },
        title = {
            Text(
                text =
                    when {
                        finished == null -> stringRes(Res.string.emoji_import_title)
                        finished.added > 0 -> pluralStringRes(Res.plurals.emoji_import_added, finished.added, finished.added)
                        else -> stringRes(Res.string.emoji_import_none_added)
                    },
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (!running && result == null) {
                    ImportOptions(isPrivate, onPrivateChange, onChooseFiles)
                } else {
                    if (running) UploadProgress(items)
                    if (items.isNotEmpty()) ImportGrid(items, running)
                    if (finished != null) ImportLeftovers(finished, showDivider = items.isNotEmpty())
                }
            }
        },
        confirmButton = {
            when {
                running -> {}
                finished != null -> Button(onClick = onClose) { Text(stringRes(Res.string.emoji_import_done)) }
                else ->
                    Button(onClick = onChooseFiles) {
                        Icon(symbol = MaterialSymbols.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringRes(Res.string.emoji_import_choose_files))
                    }
            }
        },
        dismissButton = {
            if (!running && result == null) {
                TextButton(onClick = onClose) { Text(stringRes(Res.string.cancel)) }
            }
        },
    )
}

private enum class HeroKind { IMPORT, SUCCESS, NOTHING_ADDED }

private fun EmojiImportResult.heroKind() = if (added > 0) HeroKind.SUCCESS else HeroKind.NOTHING_ADDED

@Composable
private fun HeroIcon(kind: HeroKind) {
    Crossfade(targetState = kind) { current ->
        val colors = MaterialTheme.colorScheme
        val (symbol, container, content) =
            when (current) {
                HeroKind.IMPORT -> Triple(MaterialSymbols.AddReaction, colors.primaryContainer, colors.onPrimaryContainer)
                HeroKind.SUCCESS -> Triple(MaterialSymbols.AutoAwesome, colors.primaryContainer, colors.onPrimaryContainer)
                HeroKind.NOTHING_ADDED -> Triple(MaterialSymbols.ErrorOutline, colors.errorContainer, colors.onErrorContainer)
            }
        // Springs up from smaller each time the face changes (Crossfade composes it afresh).
        val pop = remember { Animatable(0.6f) }
        LaunchedEffect(Unit) {
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }.clip(CircleShape)
                    .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(symbol = symbol, contentDescription = null, tint = content, modifier = Modifier.size(30.dp))
        }
    }
}

// ---- before: what to import and how ----

@Composable
private fun ImportOptions(
    isPrivate: Boolean,
    onPrivateChange: (Boolean) -> Unit,
    onChooseFiles: () -> Unit,
) {
    PickFilesZone(onChooseFiles)
    NamingExample()
    VisibilityChooser(isPrivate, onPrivateChange)
}

/** A dashed drop-zone card: the big, obvious way in. */
@Composable
private fun PickFilesZone(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dash = colors.primary.copy(alpha = 0.55f)
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        color = dash,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(20.dp.toPx()),
                        style =
                            Stroke(
                                width = stroke,
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                            ),
                    )
                }.clip(shape)
                .background(colors.primary.copy(alpha = 0.06f))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = MaterialSymbols.AddPhotoAlternate,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringRes(Res.string.emoji_import_pick_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringRes(Res.string.emoji_import_pick_formats),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The naming rule, shown rather than told: `party parrot.gif → :party_parrot:`. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NamingExample() {
    val colors = MaterialTheme.colorScheme
    val file = stringRes(Res.string.emoji_import_example_file)
    val code = remember(file) { EmojiImport.shortcodeFromFileName(file) ?: "party_parrot" }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringRes(Res.string.emoji_import_naming), style = MaterialTheme.typography.labelLarge)
        // Wraps rather than truncates on a narrow screen: the example is the whole point.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill(text = file, container = colors.onSurface.copy(alpha = 0.08f), content = colors.onSurfaceVariant)
            // The arrow travels with the result when the line wraps.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoMirrored.ArrowForward,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Pill(
                    text = ":$code:",
                    container = colors.secondaryContainer,
                    content = colors.onSecondaryContainer,
                    monospace = true,
                )
            }
        }
        Text(
            text = stringRes(Res.string.emoji_import_existing_skipped),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun VisibilityChooser(
    isPrivate: Boolean,
    onPrivateChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            VisibilityOption(0, !isPrivate, MaterialSymbols.Public, Res.string.emoji_import_public) { onPrivateChange(false) }
            VisibilityOption(1, isPrivate, MaterialSymbols.Lock, Res.string.emoji_import_private) { onPrivateChange(true) }
        }
        Text(
            text = stringRes(if (isPrivate) Res.string.emoji_import_private_hint else Res.string.emoji_import_public_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SingleChoiceSegmentedButtonRowScope.VisibilityOption(
    index: Int,
    selected: Boolean,
    symbol: MaterialSymbol,
    label: StringResource,
    onClick: () -> Unit,
) {
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
        icon = { Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(18.dp)) },
    ) {
        Text(stringRes(label))
    }
}

// ---- during: the live grid ----

@Composable
private fun UploadProgress(items: ImmutableList<EmojiImportItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (items.isEmpty()) {
            Text(text = stringRes(Res.string.emoji_import_reading), style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
        } else {
            val finished by remember(items) {
                derivedStateOf { items.count { it.status == EmojiImportItem.Status.DONE || it.status == EmojiImportItem.Status.FAILED } }
            }
            val fraction by animateFloatAsState(finished.toFloat() / items.size)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringRes(Res.string.emoji_import_uploading, minOf(finished + 1, items.size), items.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Lazy and height-capped: an archive can hold thousands of images, and only the visible tiles
 * should be composed and decoded. Scrolls inside the dialog's own scroll.
 */
@Composable
private fun ImportGrid(
    items: ImmutableList<EmojiImportItem>,
    running: Boolean,
) {
    val gridState = rememberLazyGridState()
    // Follows the upload in flight: past the first rows it would otherwise run out of sight.
    if (running) {
        LaunchedEffect(items, gridState) {
            snapshotFlow { items.indexOfFirst { it.status == EmojiImportItem.Status.UPLOADING } }
                .collect { if (it >= 0) gridState.animateScrollToItem(it) }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 64.dp),
        state = gridState,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.shortcode }) { item -> ImportTile(item) }
    }
}

/** One image: dim while waiting, a ring while uploading, a check that pops in when it lands. */
@Composable
private fun ImportTile(item: EmojiImportItem) {
    val colors = MaterialTheme.colorScheme
    val done = item.status == EmojiImportItem.Status.DONE
    val failed = item.status == EmojiImportItem.Status.FAILED
    val imageAlpha by animateFloatAsState(
        when (item.status) {
            EmojiImportItem.Status.DONE -> 1f
            EmojiImportItem.Status.UPLOADING -> 0.55f
            else -> 0.35f
        },
    )
    val imageScale by animateFloatAsState(
        targetValue = if (done) 1f else 0.86f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(56.dp)) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.onSurface.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = item.preview,
                    contentDescription = item.shortcode,
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .graphicsLayer {
                                alpha = imageAlpha
                                scaleX = imageScale
                                scaleY = imageScale
                            },
                )
                if (item.status == EmojiImportItem.Status.UPLOADING) {
                    // A ring around the image rather than over it, so the emoji stays visible.
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp,
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
            StatusBadge(done, failed)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.shortcode,
            style = MaterialTheme.typography.labelSmall,
            color = if (failed) colors.error else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The corner check (or cross) that springs in once an upload is over. */
@Composable
private fun BoxScope.StatusBadge(
    done: Boolean,
    failed: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = done || failed,
        enter =
            fadeIn() +
                scaleIn(
                    initialScale = 0.3f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                ),
        modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (failed) colors.error else colors.primary)
                    .border(2.dp, AlertDialogDefaults.containerColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = if (failed) MaterialSymbols.Close else MaterialSymbols.Check,
                contentDescription = null,
                tint = if (failed) colors.onError else colors.onPrimary,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

// ---- after: what didn't come in, grouped by why ----

@Composable
private fun ImportLeftovers(
    result: EmojiImportResult,
    showDivider: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val hasAny =
        result.failed > 0 || result.skipped.isNotEmpty() || result.unnamed.isNotEmpty() ||
            result.tooLarge.isNotEmpty() || result.unsupported.isNotEmpty() || result.unreadable.isNotEmpty() ||
            result.truncated.isNotEmpty()
    if (!hasAny) return

    if (showDivider) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (result.failed > 0) {
            LeftoverGroup(
                symbol = MaterialSymbols.ErrorOutline,
                tint = colors.error,
                title = pluralStringRes(Res.plurals.emoji_import_failed, result.failed, result.failed),
                hint = result.failureReason,
                hintColor = colors.error,
            )
        }
        LeftoverGroup(MaterialSymbols.Info, colors.onSurfaceVariant, stringRes(Res.string.emoji_import_skipped), names = result.skipped.map { ":$it:" }, monospace = true)
        LeftoverGroup(MaterialSymbols.Warning, colors.onSurfaceVariant, stringRes(Res.string.emoji_import_unnamed), hint = stringRes(Res.string.emoji_import_unnamed_hint), names = result.unnamed)
        LeftoverGroup(MaterialSymbols.Warning, colors.onSurfaceVariant, stringRes(Res.string.emoji_import_too_large), names = result.tooLarge)
        LeftoverGroup(MaterialSymbols.Block, colors.onSurfaceVariant, stringRes(Res.string.emoji_import_unsupported), names = result.unsupported)
        LeftoverGroup(MaterialSymbols.ErrorOutline, colors.error, stringRes(Res.string.emoji_import_unreadable), names = result.unreadable)
        LeftoverGroup(
            MaterialSymbols.Warning,
            colors.onSurfaceVariant,
            stringRes(Res.string.emoji_import_truncated),
            hint = stringRes(Res.string.emoji_import_truncated_hint, EmojiArchiveLimits.MAX_IMAGES, EmojiArchiveLimits.MAX_TOTAL_BYTES / (1024 * 1024)),
            names = result.truncated,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeftoverGroup(
    symbol: MaterialSymbol,
    tint: Color,
    title: String,
    hint: String? = null,
    hintColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    names: List<String>? = null,
    monospace: Boolean = false,
) {
    // A group with names to list only shows when it has some.
    if (names != null && names.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(symbol = symbol, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(text = title, style = MaterialTheme.typography.labelLarge)
        }
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = hintColor,
                modifier = Modifier.padding(start = 26.dp),
            )
        }
        if (!names.isNullOrEmpty()) {
            FlowRow(
                modifier = Modifier.padding(start = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                names.take(MAX_NAMES_PER_GROUP).forEach { name ->
                    Pill(name, container = colors.onSurface.copy(alpha = 0.08f), content = colors.onSurfaceVariant, monospace = monospace, small = true)
                }
                if (names.size > MAX_NAMES_PER_GROUP) {
                    Pill(
                        text = stringRes(Res.string.emoji_import_more, names.size - MAX_NAMES_PER_GROUP),
                        container = Color.Transparent,
                        content = colors.primary,
                        small = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(
    text: String,
    container: Color,
    content: Color,
    monospace: Boolean = false,
    small: Boolean = false,
) {
    Text(
        text = text,
        style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
        fontFamily = if (monospace) FontFamily.Monospace else null,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .clip(CircleShape)
                .background(container)
                .padding(horizontal = if (small) 8.dp else 12.dp, vertical = if (small) 3.dp else 6.dp),
    )
}
