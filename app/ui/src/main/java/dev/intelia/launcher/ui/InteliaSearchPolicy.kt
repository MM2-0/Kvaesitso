package dev.intelia.launcher.ui

import java.text.Normalizer
import java.util.Locale

private val searchMarks = Regex("\\p{M}+")
private fun searchText(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(searchMarks, "").lowercase(Locale.ROOT)

/** Do not surface unrelated app shortcuts merely because fuzzy scoring is permissive. */
internal fun matchesInteliaShortcut(query: String, label: String, appName: String?): Boolean {
    val normalized = searchText(query).trim()
    if (normalized.length < 3) return false
    val fields = searchText("$label ${appName.orEmpty()}")
    return normalized.split(Regex("\\s+")).all { fields.contains(it) }
}
