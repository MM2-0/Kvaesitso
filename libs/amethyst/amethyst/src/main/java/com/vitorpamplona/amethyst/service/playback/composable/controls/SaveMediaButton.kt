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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.download_to_phone
import com.vitorpamplona.amethyst.commons.resources.video_download_has_started_toast
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.PinBottomIconSize
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import kotlinx.coroutines.launch

@Preview
@Composable
fun AnimatedSaveButtonPreview() {
    ThemeComparisonColumn {
        Box(Modifier.background(BitcoinOrange)) {
            AnimatedSaveButton(
                controllerVisible = remember { mutableStateOf(true) },
                modifier = Modifier,
            ) {}
        }
    }
}

@Composable
fun AnimatedSaveButton(
    controllerVisible: State<Boolean>,
    modifier: Modifier = Modifier,
    onSaveClick: (localContext: Context) -> Unit,
) {
    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() },
    ) {
        SaveMediaButton(onSaveClick)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberSaveMediaAction(onSaveClick: (Context) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val writeStoragePermission =
        rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE) { isGranted ->
            if (isGranted) onSaveClick(context)
        }
    return {
        scope.launch {
            Toast
                .makeText(
                    context,
                    loadStringRes(Res.string.video_download_has_started_toast),
                    Toast.LENGTH_SHORT,
                ).show()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || writeStoragePermission.status.isGranted) {
            onSaveClick(context)
        } else {
            writeStoragePermission.launchPermissionRequest()
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SaveMediaButton(onSaveClick: (localContext: Context) -> Unit) {
    val saveAction = rememberSaveMediaAction(onSaveClick)
    Box(modifier = PinBottomIconSize) {
        Box(
            Modifier
                .clip(CircleShape)
                .fillMaxSize(0.7f)
                .align(Alignment.Center)
                .background(MaterialTheme.colorScheme.background),
        )
        IconButton(
            onClick = saveAction,
            modifier = Size50Modifier,
        ) {
            Icon(
                symbol = MaterialSymbols.Download,
                modifier = Size20Modifier,
                contentDescription = stringRes(Res.string.download_to_phone),
            )
        }
    }
}
