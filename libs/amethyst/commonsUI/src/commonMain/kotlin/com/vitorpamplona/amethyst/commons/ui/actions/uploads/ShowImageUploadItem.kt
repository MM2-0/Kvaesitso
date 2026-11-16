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
package com.vitorpamplona.amethyst.commons.ui.actions.uploads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.image_upload_video_thumbnail_description
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.resources.upload_error_title
import com.vitorpamplona.amethyst.commons.resources.uploading_state_compressing
import com.vitorpamplona.amethyst.commons.resources.uploading_state_downloading
import com.vitorpamplona.amethyst.commons.resources.uploading_state_error
import com.vitorpamplona.amethyst.commons.resources.uploading_state_finished
import com.vitorpamplona.amethyst.commons.resources.uploading_state_hashing
import com.vitorpamplona.amethyst.commons.resources.uploading_state_ready
import com.vitorpamplona.amethyst.commons.resources.uploading_state_server_processing
import com.vitorpamplona.amethyst.commons.resources.uploading_state_uploading
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.rememberVideoThumbnail
import com.vitorpamplona.amethyst.commons.ui.components.AutoNonlazyGrid
import com.vitorpamplona.amethyst.commons.ui.note.CloseIcon
import com.vitorpamplona.amethyst.commons.ui.note.platform.VideoView
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size40Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun ShowImageUploadGallery(
    list: MultiOrchestrator,
    onDelete: (SelectedMediaProcessing) -> Unit,
    accountViewModel: AccountViewModel,
) {
    AutoNonlazyGrid(list.size()) {
        ShowImageUploadItem(list.get(it), onDelete, accountViewModel)
    }
}

@Composable
fun ShowImageGallery(
    media: SelectedMedia,
    accountViewModel: AccountViewModel,
) {
    when {
        media.isImage() == true -> {
            AsyncImage(
                model = media.uri.toString(),
                contentDescription = media.uri.toString(),
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
            )
        }

        media.isAudio() == true -> {
            FilePreviewPlaceholder(
                icon = MaterialSymbols.AudioFile,
                label = media.mimeType ?: "audio/*",
            )
        }

        media.isDocument() -> {
            FilePreviewPlaceholder(
                icon = MaterialSymbols.PictureAsPdf,
                label = media.mimeType ?: "application/pdf",
            )
        }

        media.isVideo() == true -> {
            val thumbnail = rememberVideoThumbnail(media)
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = stringRes(Res.string.image_upload_video_thumbnail_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                VideoView(
                    videoUri = media.uri.toString(),
                    mimeType = media.mimeType,
                    roundedCorner = false,
                    contentScale = ContentScale.Crop,
                    accountViewModel = accountViewModel,
                )
            }
        }

        else -> {
            VideoView(
                videoUri = media.uri.toString(),
                mimeType = media.mimeType,
                roundedCorner = false,
                contentScale = ContentScale.Crop,
                accountViewModel = accountViewModel,
            )
        }
    }
}

@Composable
fun ShowImageUploadItem(
    item: SelectedMediaProcessing,
    onDelete: (SelectedMediaProcessing) -> Unit,
    accountViewModel: AccountViewModel,
) {
    ShowImageGallery(item.media, accountViewModel)

    OrchestratorOverlay(item.orchestrator) {
        onDelete(item)
    }
}

@Composable
fun OrchestratorOverlay(
    orchestrator: UploadOrchestrator,
    onDelete: () -> Unit,
) {
    val progress by orchestrator.progress.collectAsState()
    val progressState by orchestrator.progressState.collectAsState()

    val state = progressState
    if (state is UploadingState.Ready) {
        DeleteButton(onDelete)
    } else if (state is UploadingState.Error) {
        UploadErrorBadge(progress, state, onDelete)
    } else {
        UploadingState(progress, state)
    }
}

/**
 * A failed upload: the badge alone has room for one word, so tapping it opens the reason
 * (server refused the file type, payment required, no connection, ...). Without it the
 * user had "Error" and nothing to act on, and no way to drop the failed attachment.
 */
@Composable
private fun UploadErrorBadge(
    progress: Double,
    state: UploadingState.Error,
    onDelete: () -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable { showDetails = true },
    ) {
        UploadingState(progress, state)
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(stringRes(Res.string.upload_error_title)) },
            text = { Text(stringRes(state.errorResource, *state.params)) },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) { Text(stringRes(Res.string.close)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDetails = false
                        onDelete()
                    },
                ) { Text(stringRes(Res.string.remove)) }
            },
        )
    }
}

@Composable
fun DeleteButton(onDelete: () -> Unit) {
    Box(
        contentAlignment = Alignment.TopEnd,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Size40Modifier, contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .fillMaxSize(0.6f)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.background),
            )

            IconButton(
                modifier = Size20Modifier,
                onClick = onDelete,
            ) {
                CloseIcon()
            }
        }
    }
}

@Composable
fun UploadingState(
    progress: Double,
    progressState: UploadingState,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.size(55.dp), contentAlignment = Alignment.Center) {
            val animatedProgress by animateFloatAsState(
                targetValue = progress.toFloat(),
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            )

            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier =
                    Size55Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background),
                strokeWidth = 5.dp,
            )

            val txt =
                when (progressState) {
                    is UploadingState.Ready -> stringRes(Res.string.uploading_state_ready)
                    is UploadingState.Compressing -> stringRes(Res.string.uploading_state_compressing)
                    is UploadingState.Uploading -> stringRes(Res.string.uploading_state_uploading)
                    is UploadingState.ServerProcessing -> stringRes(Res.string.uploading_state_server_processing)
                    is UploadingState.Downloading -> stringRes(Res.string.uploading_state_downloading)
                    is UploadingState.Hashing -> stringRes(Res.string.uploading_state_hashing)
                    is UploadingState.Finished -> stringRes(Res.string.uploading_state_finished)
                    is UploadingState.Error -> stringRes(Res.string.uploading_state_error)
                }

            Text(
                txt,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun FilePreviewPlaceholder(
    icon: MaterialSymbol,
    label: String,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp),
        ) {
            Icon(
                symbol = icon,
                contentDescription = label,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
