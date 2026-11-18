/*
 * Copyright (c) 2026 Telos contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package de.mm20.launcher2.ui.aether

import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.ui.component.TelosDotMatrixText
import de.mm20.launcher2.ui.component.telosTechCard
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
    VirtualAppWindow {
        EmbeddedAether(
        card = { modifier, shape, container -> modifier.telosTechCard(shape = shape, backgroundColor = container) },
        heading = { content -> TelosCyberHeading { content() } },
        label = { text, modifier -> TelosDotMatrixText(text, modifier, color = MaterialTheme.colorScheme.primary) },
        brandIcon = { modifier -> Icon(painterResource(de.mm20.launcher2.base.R.drawable.ic_glyph_aether), null, modifier, tint = MaterialTheme.colorScheme.primary) },
        )
    }
}
