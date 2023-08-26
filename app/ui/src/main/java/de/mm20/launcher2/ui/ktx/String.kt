package de.mm20.launcher2.ui.ktx

import com.sigpwned.emoji4j.core.Grapheme
import com.sigpwned.emoji4j.core.GraphemeMatcher

fun String.splitLeadingEmoji(): Pair<String?, String?> {
    val matcher = GraphemeMatcher(this)
    if (!matcher.find()) return null to this.trim()
    val grapheme = matcher.grapheme()
    if (grapheme?.type == Grapheme.Type.EMOJI && matcher.start() == 0) {
        val end = matcher.end()
        val emoji = this.substring(0, end)
        val tagName = this.substring(end)
        return emoji to tagName.takeIf { it.isNotBlank() }
    }
    return null to this.trim()
}