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
package com.vitorpamplona.amethyst.commons.ui.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.model.VideoPostKind
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_caption
import com.vitorpamplona.amethyst.commons.resources.add_caption_example
import com.vitorpamplona.amethyst.commons.resources.add_sensitive_content_description
import com.vitorpamplona.amethyst.commons.resources.add_sensitive_content_label
import com.vitorpamplona.amethyst.commons.resources.file_server
import com.vitorpamplona.amethyst.commons.resources.file_server_description
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_explainer
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_label
import com.vitorpamplona.amethyst.commons.resources.strip_metadata_description
import com.vitorpamplona.amethyst.commons.resources.strip_metadata_label
import com.vitorpamplona.amethyst.commons.resources.video_codec_h265_description
import com.vitorpamplona.amethyst.commons.resources.video_codec_h265_label
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.VideoUploadControls
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.ShowImageUploadGallery
import com.vitorpamplona.amethyst.commons.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.TextSpinner
import com.vitorpamplona.amethyst.commons.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.commons.ui.components.edgeToEdgeDialogProperties
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.commons.ui.note.creators.contentWarning.SettingSwitchItem
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.MediaQualitySlider
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.VideoCompressionNeedsFfmpegHint
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.VideoHdrHint
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMediaView(
    uris: ImmutableList<SelectedMedia>,
    onClose: () -> Unit,
    postViewModel: NewMediaModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    videoKind: VideoPostKind = VideoPostKind.AUTO,
    initialCaption: String = "",
) {
    val account = accountViewModel.account

    val scrollState = rememberScrollState()

    LaunchedEffect(uris, videoKind, initialCaption) {
        postViewModel.load(account, uris, videoKind, initialCaption)
    }

    StrippingFailureDialog(postViewModel.strippingFailureConfirmation)

    Dialog(
        onDismissRequest = { onClose() },
        properties = edgeToEdgeDialogProperties(dismissOnClickOutside = false),
    ) {
        SetDialogToEdgeToEdge()
        Scaffold(
            topBar = {
                PostingTopBar(
                    isActive = postViewModel::canPost,
                    onCancel = {
                        postViewModel.cancelModel()
                        onClose()
                    },
                    onPost = {
                        postViewModel.upload(accountViewModel.mediaUploader, accountViewModel, onClose, accountViewModel.toastManager::toast)
                        postViewModel.selectedServer?.let {
                            account.settings.changeDefaultFileServer(it)
                        }
                        account.settings.changeStripLocationOnUpload(postViewModel.stripMetadata)
                    },
                )
            },
        ) { pad ->
            Surface(
                modifier =
                    Modifier
                        .padding(pad)
                        .consumeWindowInsets(pad)
                        .imePaddingSafe(),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                    ) {
                        ImageVideoPost(postViewModel, accountViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun ImageVideoPost(
    postViewModel: NewMediaModel,
    accountViewModel: AccountViewModel,
) {
    val fileServers by accountViewModel.account.blossomServers.hostNameFlow
        .collectAsState()

    val fileServerOptions =
        remember(fileServers) {
            fileServers
                .map {
                    TitleExplainer(it.name, it.baseUrl)
                }.toImmutableList()
        }

    postViewModel.multiOrchestrator?.let {
        ShowImageUploadGallery(
            it,
            postViewModel::deleteMediaToUpload,
            accountViewModel,
        )
    }

    SpellCheckedOutlinedTextField(
        label = { Text(text = stringRes(Res.string.add_caption)) },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 3.dp)
                .height(150.dp),
        maxLines = 10,
        value = postViewModel.caption,
        onValueChange = { postViewModel.caption = it },
        placeholder = {
            Text(
                text = stringRes(Res.string.add_caption_example),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
    )

    SettingSwitchItem(
        title = Res.string.add_sensitive_content_label,
        description = Res.string.add_sensitive_content_description,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        checked = postViewModel.sensitiveContent,
        onCheckedChange = { postViewModel.sensitiveContent = it },
    )

    SettingsRow(Res.string.file_server, Res.string.file_server_description) {
        TextSpinner(
            label = "",
            placeholder =
                fileServers
                    .firstOrNull { it == accountViewModel.account.settings.defaultFileServer }
                    ?.name
                    ?: fileServers.firstOrNull()?.name
                    ?: DEFAULT_MEDIA_SERVERS[0].name,
            options = fileServerOptions,
            onSelect = { postViewModel.selectedServer = fileServers[it] },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
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

    MediaQualitySlider(
        position = postViewModel.mediaQualitySlider,
        onPositionChange = { postViewModel.mediaQualitySlider = it },
        media = postViewModel.multiOrchestrator,
        uploader = accountViewModel.mediaUploader,
    )

    // The H.265 switch only where the platform will compress the video; a hint where it needs ffmpeg.
    val controls = VideoUploadControls(postViewModel.multiOrchestrator, accountViewModel.mediaUploader.videoCompression, postViewModel.mediaQualitySlider)
    VideoCompressionNeedsFfmpegHint(controls)
    if (controls.videoWillBeCompressed) {
        SettingSwitchItem(
            title = Res.string.video_codec_h265_label,
            description = Res.string.video_codec_h265_description,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            checked = postViewModel.useH265Codec,
            onCheckedChange = { postViewModel.useH265Codec = it },
        )
        VideoHdrHint(postViewModel.multiOrchestrator, accountViewModel.mediaUploader, postViewModel.useH265Codec)
    }

    SettingSwitchItem(
        title = Res.string.strip_metadata_label,
        description = Res.string.strip_metadata_description,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        checked = postViewModel.stripMetadata,
        onCheckedChange = { postViewModel.stripMetadata = it },
    )
}
