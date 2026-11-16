/*
 * Copyright (c) 2026 Telos contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package de.mm20.launcher2.ui.aether

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import com.vitorpamplona.amethyst.ui.EmbeddedAether
import kotlinx.serialization.Serializable

@Serializable
data class AetherDashboardRoute(
    val sharedText: String? = null,
    val sharedMediaUri: String? = null,
) : NavKey

@Composable
fun AetherDashboardScreen() {
    EmbeddedAether()
}
