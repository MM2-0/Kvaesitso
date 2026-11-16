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
package com.vitorpamplona.amethyst.commons.appupdate

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.StateFlow

/** How this install gets a new version. Decided by how the app was installed. */
@Immutable
sealed interface UpdateMethod {
    /** The app downloads the new version, swaps it in and restarts (DMG, Windows zip, AppImage, tarball). */
    data object SelfInstall : UpdateMethod

    /**
     * The app downloads the new version and hands it to the system installer, which asks for
     * permission (the MSI's UAC prompt; PackageKit for a .deb or .rpm).
     */
    data object SystemInstaller : UpdateMethod

    /** A package manager owns the install; the app only says which [command] updates it. */
    data class PackageManager(
        val name: String,
        val command: String,
    ) : UpdateMethod

    /** macOS runs the app from a read-only copy (App Translocation) until it is moved to Applications. */
    data object MoveToApplications : UpdateMethod

    /** Nothing the app can do from here: it points at the download page. */
    data object DownloadPage : UpdateMethod
}

val UpdateMethod.canInstall get() = this == UpdateMethod.SelfInstall || this == UpdateMethod.SystemInstaller

enum class UpdateFailure {
    /** No source could be reached. */
    CHECK_FAILED,

    /** The download broke off. */
    DOWNLOAD_FAILED,

    /** The file is not the one the signed release names. Nothing was installed. */
    VERIFICATION_FAILED,

    /** The app cannot write where it is installed. */
    NOT_WRITABLE,

    /** Staging or starting the installer failed. */
    INSTALL_FAILED,
}

@Immutable
sealed interface AppUpdateState {
    data object Idle : AppUpdateState

    data object Checking : AppUpdateState

    data class UpToDate(
        val checkedAt: Long,
    ) : AppUpdateState

    data class Available(
        val update: AvailableUpdate,
    ) : AppUpdateState

    data class Downloading(
        val update: AvailableUpdate,
        val bytesRead: Long,
        val totalBytes: Long?,
    ) : AppUpdateState

    /** Downloaded and checked; the app restarts into it when asked. */
    data class ReadyToRestart(
        val update: AvailableUpdate,
    ) : AppUpdateState

    data class Failed(
        val failure: UpdateFailure,
        val update: AvailableUpdate?,
    ) : AppUpdateState
}

/** What the shared UI drives: the platform's updater, or nothing where the store updates the app. */
interface AppUpdateController {
    val currentVersion: String
    val method: UpdateMethod
    val state: StateFlow<AppUpdateState>

    /** Whether the app checks on its own (at start and twice a day). */
    val autoCheck: StateFlow<Boolean>

    /** The version the user chose to skip; the prompt stays away for it, the settings still show it. */
    val skippedVersion: StateFlow<String?>

    /** When a check last got an answer, in seconds; 0 when none ever did. */
    val lastCheckAt: StateFlow<Long>

    fun setAutoCheck(enabled: Boolean)

    fun checkNow()

    /** Downloads and checks the available update. Only for a method that [canInstall]. */
    fun install()

    /** Quits and restarts into a [AppUpdateState.ReadyToRestart] update. */
    fun restartToUpdate()

    fun skip(version: String)
}
