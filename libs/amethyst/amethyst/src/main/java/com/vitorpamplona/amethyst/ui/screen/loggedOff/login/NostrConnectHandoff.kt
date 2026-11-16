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
package com.vitorpamplona.amethyst.ui.screen.loggedOff.login

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.commons.ui.platform.NostrConnectApp
import com.vitorpamplona.quartz.utils.Log

/**
 * The NIP-46 signer apps (Amber and the like) that take a `nostrconnect://` address. Never
 * Amethyst itself, nor another Amethyst build: they handle the scheme too, as a bunker pairing an
 * app, which is the opposite of what the login screen asks.
 */
@SuppressLint("QueryPermissionsNeeded") // The manifest's <queries> declares the nostrconnect scheme.
fun nostrConnectSignerApps(
    context: Context,
    uri: String,
): List<NostrConnectApp> {
    if (!uri.startsWith("nostrconnect://")) return emptyList()
    val packageManager = context.packageManager
    // Only what an implicit startActivity can launch: a filter without CATEGORY_DEFAULT would
    // count as a signer here and then refuse the address.
    return packageManager
        .queryIntentActivities(viewIntent(uri), PackageManager.MATCH_DEFAULT_ONLY)
        .filter { !it.activityInfo.packageName.startsWith(AMETHYST_PACKAGE_PREFIX) }
        .distinctBy { it.activityInfo.packageName }
        .map { NostrConnectApp(id = it.activityInfo.packageName, label = it.loadLabel(packageManager).toString()) }
}

/**
 * Hands a `nostrconnect://` address to [packageName], one of [nostrConnectSignerApps], so the user
 * approves there instead of scanning; with no [packageName], to the only signer app installed.
 * Never through the system chooser: on Android 16, picking a target for this VIEW intent there
 * crashes the system's share-target ranking and restarts the phone. False when no app took it.
 */
fun openNostrConnectInSigner(
    context: Context,
    uri: String,
    packageName: String? = null,
): Boolean {
    if (!uri.startsWith("nostrconnect://")) return false
    val target =
        packageName?.takeIf { !it.startsWith(AMETHYST_PACKAGE_PREFIX) }
            ?: nostrConnectSignerApps(context, uri).singleOrNull()?.id
            ?: return false

    return try {
        context.startActivity(viewIntent(uri).setPackage(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        // Not the exception: its message prints the intent, nostrconnect:// address and secret included.
        Log.w("NostrConnectHandoff") { "No app took the nostrconnect address: ${e::class.simpleName}" }
        false
    }
}

private fun viewIntent(uri: String) = Intent(Intent.ACTION_VIEW, uri.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)

/** Every Amethyst build's package (play, fdroid, debug, benchmark) starts with this. */
private const val AMETHYST_PACKAGE_PREFIX = "com.vitorpamplona.amethyst"
