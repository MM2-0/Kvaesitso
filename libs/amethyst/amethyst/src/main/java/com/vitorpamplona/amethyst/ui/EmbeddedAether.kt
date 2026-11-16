/*
 * Copyright (c) 2026 Telos contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vitorpamplona.amethyst.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.ui.app.AmethystApp
import com.vitorpamplona.amethyst.commons.ui.note.elements.NowProvider
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme

/** Full upstream client. The host awaits initializeEmbeddedClient before composing it. */
@Composable
fun EmbeddedAether() {
    check(Amethyst.isInitialized)
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val density = LocalDensity.current
    AmethystTheme {
        // Keep upstream composition locals while inheriting the Telos user's appearance.
        CompositionLocalProvider(LocalDensity provides density) {
            MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes) {
                NowProvider {
                    AmethystApp(Amethyst.instance.sessionManager, AndroidAppRoot)
                }
            }
        }
    }
}
