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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.actions.mediaServers.FileServerSelectionRow
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.UploadProgressIndicator
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.VoiceAnonymizationSection
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.platform.VoiceMessagePreview
import com.vitorpamplona.amethyst.commons.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceReplyScreen(
    replyToNoteId: String,
    recordingFilePath: String,
    mimeType: String,
    duration: Int,
    amplitudesJson: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: VoiceReplyViewModel = rememberViewModel { VoiceReplyViewModel() }

    LaunchedEffect(replyToNoteId, recordingFilePath, accountViewModel) {
        viewModel.init(accountViewModel)
        viewModel.load(replyToNoteId, recordingFilePath, mimeType, duration, amplitudesJson)
    }

    PlatformBackHandler {
        viewModel.cancel()
        nav.popBack()
    }

    Scaffold(
        topBar = {
            PostingTopBar(
                isActive = viewModel::canSend,
                onPost = {
                    viewModel.sendVoiceReply {
                        nav.popBack()
                    }
                },
                onCancel = {
                    viewModel.cancel()
                    nav.popBack()
                },
            )
        },
    ) { pad ->
        Surface(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad),
        ) {
            VoiceReplyScreenBody(viewModel, accountViewModel, nav)
        }
    }
}

@Composable
private fun VoiceReplyScreenBody(
    viewModel: VoiceReplyViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = Size10dp),
    ) {
        // Show the note being replied to
        viewModel.replyToNote?.let { note ->
            Spacer(modifier = StdVertSpacer)
            NoteCompose(
                baseNote = note,
                modifier = MaterialTheme.colorScheme.replyModifier,
                isQuotedNote = true,
                unPackReply = ReplyRenderType.NONE,
                makeItShort = true,
                quotesLeft = 1,
                accountViewModel = accountViewModel,
                nav = nav,
            )
            Spacer(modifier = StdVertSpacer)
        }

        // Voice preview or upload progress
        viewModel.voiceOrchestrator?.let { orchestrator ->
            UploadProgressIndicator(orchestrator)
        } ?: run {
            viewModel.getVoicePreviewMetadata()?.let { metadata ->
                val displayMetadata =
                    metadata.copy(
                        waveform = viewModel.activeWaveform ?: metadata.waveform,
                    )
                VoiceMessagePreview(
                    voiceMetadata = displayMetadata,
                    localFile = viewModel.activeFile,
                    onReRecord = { recording -> viewModel.selectRecording(recording) },
                    isUploading = viewModel.isUploading,
                    onRemove = {
                        viewModel.cancel()
                        nav.popBack()
                    },
                )
            }

            // Voice anonymization section
            VoiceAnonymizationSection(
                selectedPreset = viewModel.selectedPreset,
                processingPreset = viewModel.processingPreset,
                onPresetSelected = { viewModel.selectPreset(it) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Server selection
        val fileServers by
            accountViewModel.account.blossomServers.hostNameFlow
                .collectAsState()

        FileServerSelectionRow(
            fileServers = fileServers,
            selectedServer = viewModel.voiceSelectedServer ?: accountViewModel.account.settings.defaultFileServer,
            onSelect = { viewModel.voiceSelectedServer = it },
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}
