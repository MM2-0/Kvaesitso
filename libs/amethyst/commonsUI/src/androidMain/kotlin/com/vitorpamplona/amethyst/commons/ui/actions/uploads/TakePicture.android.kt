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

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.no_camera_app_found
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual val canCaptureFromCamera: Boolean = true

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun TakePicture(onPictureTaken: (ImmutableList<SelectedMedia>) -> Unit) {
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
        ) { success ->
            if (success) {
                cameraUri?.let {
                    onPictureTaken(persistentListOf(SelectedMedia(it, "image/jpeg")))
                }
            } else {
                onPictureTaken(persistentListOf())
            }
            cameraUri = null
        }

    val cameraPermissionState =
        rememberPermissionState(
            Manifest.permission.CAMERA,
            onPermissionResult = {
                if (it) {
                    scope.launch(Dispatchers.IO) {
                        cameraUri = getPhotoUri(context)
                        cameraUri?.let { uri ->
                            launcher.launchOrToast(context, uri) { onPictureTaken(persistentListOf()) }
                        }
                    }
                }
            },
        )

    if (cameraPermissionState.status.isGranted) {
        LaunchedEffect(key1 = Unit) {
            launch(Dispatchers.IO) {
                cameraUri = getPhotoUri(context)
                cameraUri?.let { uri ->
                    launcher.launchOrToast(context, uri) { onPictureTaken(persistentListOf()) }
                }
            }
        }
    } else {
        LaunchedEffect(key1 = Unit) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
}

/**
 * Launches the camera capture intent, switching to the main thread so the
 * [ActivityResultLauncher] is invoked safely. Devices without a camera app throw
 * [ActivityNotFoundException] from the underlying [android.app.Activity.startActivityForResult];
 * in that case we surface a toast and run [onUnavailable] to dismiss the capture flow instead of
 * crashing.
 */
private suspend fun ActivityResultLauncher<Uri>.launchOrToast(
    context: Context,
    uri: Uri,
    onUnavailable: () -> Unit,
) {
    withContext(Dispatchers.Main) {
        try {
            this@launchOrToast.launch(uri)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, loadStringRes(Res.string.no_camera_app_found), Toast.LENGTH_LONG).show()
            onUnavailable()
        }
    }
}

fun getPhotoUri(context: Context): Uri =
    getMediaUri(
        context = context,
        filePrefix = "JPEG",
        fileExtension = ".jpg",
        storageDir = Environment.DIRECTORY_PICTURES,
    )

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun TakeVideo(onVideoTaken: (ImmutableList<SelectedMedia>) -> Unit) {
    val context = LocalContext.current
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CaptureVideo(),
        ) { success ->
            if (success) {
                videoUri?.let {
                    onVideoTaken(persistentListOf(SelectedMedia(it, "video/mp4")))
                }
            } else {
                onVideoTaken(persistentListOf())
            }
            videoUri = null
        }

    val cameraPermissionState =
        rememberPermissionState(
            Manifest.permission.CAMERA,
            onPermissionResult = {
                if (it) {
                    scope.launch(Dispatchers.IO) {
                        videoUri = getVideoUri(context)
                        videoUri?.let { uri ->
                            launcher.launchOrToast(context, uri) { onVideoTaken(persistentListOf()) }
                        }
                    }
                }
            },
        )

    if (cameraPermissionState.status.isGranted) {
        LaunchedEffect(key1 = Unit) {
            launch(Dispatchers.IO) {
                videoUri = getVideoUri(context)
                videoUri?.let { uri ->
                    launcher.launchOrToast(context, uri) { onVideoTaken(persistentListOf()) }
                }
            }
        }
    } else {
        LaunchedEffect(key1 = Unit) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
}

fun getVideoUri(context: Context): Uri =
    getMediaUri(
        context = context,
        filePrefix = "MP4",
        fileExtension = ".mp4",
        storageDir = Environment.DIRECTORY_MOVIES,
    )

private fun getMediaUri(
    context: Context,
    filePrefix: String,
    fileExtension: String,
    storageDir: String,
): Uri {
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDirectory: File? = context.getExternalFilesDir(storageDir)
    return File
        .createTempFile(
            "${filePrefix}_${timeStamp}_",
            fileExtension,
            storageDirectory,
        ).let {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                it,
            )
        }
}
