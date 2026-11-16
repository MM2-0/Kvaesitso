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
package com.vitorpamplona.amethyst.commons.ui.note.creators.uploads

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_content
import com.vitorpamplona.amethyst.commons.resources.add_sensitive_content_description
import com.vitorpamplona.amethyst.commons.resources.add_sensitive_content_label
import com.vitorpamplona.amethyst.commons.resources.ai_suggested_alt_text_dismiss
import com.vitorpamplona.amethyst.commons.resources.ai_suggested_alt_text_hint
import com.vitorpamplona.amethyst.commons.resources.content_description
import com.vitorpamplona.amethyst.commons.resources.content_description_add_document
import com.vitorpamplona.amethyst.commons.resources.content_description_add_image
import com.vitorpamplona.amethyst.commons.resources.content_description_add_media
import com.vitorpamplona.amethyst.commons.resources.content_description_add_video
import com.vitorpamplona.amethyst.commons.resources.content_description_example
import com.vitorpamplona.amethyst.commons.resources.convert_gif_to_mp4_description
import com.vitorpamplona.amethyst.commons.resources.convert_gif_to_mp4_label
import com.vitorpamplona.amethyst.commons.resources.file_server
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_explainer
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_label
import com.vitorpamplona.amethyst.commons.resources.strip_metadata_description
import com.vitorpamplona.amethyst.commons.resources.strip_metadata_label
import com.vitorpamplona.amethyst.commons.resources.video_codec_h265_description
import com.vitorpamplona.amethyst.commons.resources.video_codec_h265_label
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.VideoUploadControls
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.ShowImageUploadGallery
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.TextSpinner
import com.vitorpamplona.amethyst.commons.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.commons.ui.note.CancelIcon
import com.vitorpamplona.amethyst.commons.ui.note.creators.contentWarning.SettingSwitchItem
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.toImmutableList

@Composable
fun ImageVideoDescription(
    uris: MultiOrchestrator,
    defaultServer: ServerName,
    isUploading: Boolean,
    onAdd: (String, ServerName, Boolean, Int, Boolean, Boolean, Boolean) -> Unit,
    onDelete: (SelectedMediaProcessing) -> Unit,
    onCancel: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val fileServers by accountViewModel.account.blossomServers.hostNameFlow
        .collectAsState()

    val fileServerOptions =
        remember(fileServers) {
            fileServers.map { TitleExplainer(it.name, it.baseUrl) }.toImmutableList()
        }

    var selectedServer by remember {
        mutableStateOf(
            fileServers.firstOrNull { it == defaultServer } ?: fileServers.firstOrNull() ?: DEFAULT_MEDIA_SERVERS[0],
        )
    }
    var message by remember { mutableStateOf("") }
    var sensitiveContent by remember { mutableStateOf(false) }

    val firstImageUri =
        remember(uris) {
            uris
                .first()
                .takeIf { it.media.isImage() == true && it.media.isGif().not() }
                ?.media
                ?.uri
        }
    val appServices = LocalAppServices.current
    val labelService = remember { appServices.createAltTextSuggester() }
    var isLabeling by remember { mutableStateOf(false) }
    var aiSuggested by remember { mutableStateOf(false) }

    DisposableEffect(labelService) {
        onDispose { labelService?.close() }
    }

    LaunchedEffect(firstImageUri) {
        if (firstImageUri == null || message.isNotEmpty()) return@LaunchedEffect
        isLabeling = true
        val suggestion =
            try {
                labelService?.suggestAltText(firstImageUri)
            } finally {
                isLabeling = false
            }
        if (suggestion != null && message.isEmpty()) {
            message = suggestion
            aiSuggested = true
        }
    }

    var mediaQualitySlider by remember {
        mutableIntStateOf(if (uris.hasNonMedia()) CompressorQuality.uncompressedSliderPosition else CompressorQuality.defaultSliderPosition)
    }

    // Codec selection: false = H264, true = H265
    var useH265Codec by remember { mutableStateOf(false) }

    var stripMetadata by remember { mutableStateOf(accountViewModel.account.settings.stripLocationOnUpload) }

    var convertGifToMp4 by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 30.dp, end = 30.dp)
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(30.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
            ) {
                val text =
                    if (uris.size() == 1) {
                        if (uris.first().media.isImage() == true) {
                            Res.string.content_description_add_image
                        } else {
                            if (uris.first().media.isVideo() == true) {
                                Res.string.content_description_add_video
                            } else {
                                Res.string.content_description_add_document
                            }
                        }
                    } else {
                        Res.string.content_description_add_media
                    }

                Text(
                    text = stringRes(text),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W500,
                    modifier =
                        Modifier
                            .padding(start = 10.dp)
                            .weight(1.0f)
                            .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
                )

                IconButton(
                    modifier =
                        Modifier
                            .size(30.dp)
                            .padding(end = 5.dp),
                    onClick = onCancel,
                ) {
                    CancelIcon()
                }
            }

            HorizontalDivider(thickness = DividerThickness)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
            ) {
                ShowImageUploadGallery(uris, onDelete, accountViewModel)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextSpinner(
                    label = stringRes(id = Res.string.file_server),
                    placeholder =
                        fileServers
                            .firstOrNull { it == defaultServer }
                            ?.name
                            ?: fileServers.firstOrNull()?.name
                            ?: DEFAULT_MEDIA_SERVERS[0].name,
                    options = fileServerOptions,
                    onSelect = { selectedServer = fileServers[it] },
                    modifier =
                        Modifier
                            .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                            .weight(1f),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SettingSwitchItem(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    checked = sensitiveContent,
                    onCheckedChange = { sensitiveContent = it },
                    title = Res.string.add_sensitive_content_label,
                    description = Res.string.add_sensitive_content_description,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
            ) {
                SpellCheckedOutlinedTextField(
                    label = { Text(text = stringRes(Res.string.content_description)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
                    value = message,
                    onValueChange = {
                        message = it
                        aiSuggested = false
                    },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.content_description_example),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    trailingIcon = {
                        if (isLabeling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                )
            }

            if (aiSuggested) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                ) {
                    AssistChip(
                        onClick = {
                            message = ""
                            aiSuggested = false
                        },
                        label = { Text(text = stringRes(Res.string.ai_suggested_alt_text_hint)) },
                        leadingIcon = {
                            Icon(
                                symbol = MaterialSymbols.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                        trailingIcon = {
                            Icon(
                                symbol = MaterialSymbols.Close,
                                contentDescription = stringRes(Res.string.ai_suggested_alt_text_dismiss),
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }

            // Hide the privacy toggle only when a selected video will really be compressed on this
            // platform (compression strips metadata); without ffmpeg the toggle is the only strip.
            // The choice is still passed through, because the encode can fall back to the original
            // and the stripper is then the only strip.
            val controls = VideoUploadControls(uris, accountViewModel.mediaUploader.videoCompression, mediaQualitySlider)
            val isVideoWithCompression = controls.videoWillBeCompressed

            if (!isVideoWithCompression) {
                SettingSwitchItem(
                    title = Res.string.strip_metadata_label,
                    description = Res.string.strip_metadata_description,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    checked = stripMetadata,
                    onCheckedChange = { stripMetadata = it },
                )
            }

            if (controls.showsSlider && !convertGifToMp4) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                            .padding(vertical = 8.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1.0f),
                        verticalArrangement = Arrangement.spacedBy(Size5dp),
                    ) {
                        Text(
                            text = stringRes(Res.string.media_compression_quality_label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringRes(Res.string.media_compression_quality_explainer),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                MediaQualitySlider(
                    position = mediaQualitySlider,
                    onPositionChange = { mediaQualitySlider = it },
                    media = uris,
                    uploader = accountViewModel.mediaUploader,
                )
            }

            VideoCompressionNeedsFfmpegHint(controls)

            if (isVideoWithCompression) {
                SettingSwitchItem(
                    title = Res.string.video_codec_h265_label,
                    description = Res.string.video_codec_h265_description,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    checked = useH265Codec,
                    onCheckedChange = { useH265Codec = it },
                )
                VideoHdrHint(uris, accountViewModel.mediaUploader, useH265Codec)
            }

            if (controls.showsGifSwitch) {
                SettingSwitchItem(
                    title = Res.string.convert_gif_to_mp4_label,
                    description = Res.string.convert_gif_to_mp4_description,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    checked = convertGifToMp4,
                    onCheckedChange = { convertGifToMp4 = it },
                )
            }

            Button(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                enabled = !isUploading,
                onClick = {
                    onAdd(message, selectedServer, sensitiveContent, mediaQualitySlider, useH265Codec, stripMetadata, convertGifToMp4)
                },
                shape = QuoteBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text(text = stringRes(Res.string.add_content), color = Color.White, fontSize = 20.sp)
            }
        }
    }
}
