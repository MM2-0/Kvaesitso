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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common

import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.export_relay_settings
import com.vitorpamplona.amethyst.commons.resources.relay_settings
import com.vitorpamplona.amethyst.commons.ui.components.TextSharer
import com.vitorpamplona.amethyst.commons.ui.loadStringRes

class RelayExporter(
    val sharer: TextSharer,
) {
    suspend fun export(collection: RelayListCollection) {
        val title = loadStringRes(Res.string.export_relay_settings)
        sharer.share(buildExportText(collection), title, title)
    }

    suspend fun buildExportText(collection: RelayListCollection): String {
        val builder = StringBuilder()
        builder.appendLine("# ${loadStringRes(Res.string.relay_settings)}")
        builder.appendLine()

        collection.sections().forEach { section ->
            formatSection(section, builder)
        }

        return builder.toString().trimEnd()
    }

    private suspend fun formatSection(
        section: RelaySection,
        builder: StringBuilder,
    ) {
        if (section.relays.isEmpty()) return
        builder.appendLine("## ${loadStringRes(section.titleRes)}")
        builder.appendLine("# ${loadStringRes(section.descriptionRes)}")
        builder.appendLine()
        section.relays.forEach { relay ->
            builder.appendLine(relay.relay.url)
        }
        builder.appendLine()
    }
}
