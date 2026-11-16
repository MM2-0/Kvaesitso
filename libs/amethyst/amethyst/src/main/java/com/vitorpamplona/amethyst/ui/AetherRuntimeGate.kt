/*
 * Copyright (c) 2026 Telos contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vitorpamplona.amethyst.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.error_generic
import com.vitorpamplona.amethyst.commons.resources.try_again
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

/** Every client window awaits the same IO initialization, including a restored auxiliary task. */
@Composable
internal fun AetherRuntimeGate(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    var ready by remember { mutableStateOf(Amethyst.isInitialized) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(context, attempt) {
        failed = false
        try {
            Amethyst.initialize(context)
            ready = true
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e("AetherRuntime", "Client initialization failed", exception)
            failed = true
        }
    }
    if (ready) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (failed) {
                Text(stringResource(Res.string.error_generic))
                Button(onClick = { attempt++ }) { Text(stringResource(Res.string.try_again)) }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}
