/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.vitorpamplona.amethyst.commons.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/** Host-owned chrome without a dependency from the client back into the launcher. */
class HostChrome(
    val card: (Modifier, Shape, Color) -> Modifier,
    val heading: @Composable (@Composable () -> Unit) -> Unit,
    val label: @Composable (String, Modifier) -> Unit,
)

val LocalHostChrome = staticCompositionLocalOf<HostChrome?> { null }
