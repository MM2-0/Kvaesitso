package de.mm20.launcher2.ui.settings.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.StringNormalizer
import de.mm20.launcher2.search.fuzzy.FzfMatcher
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data object FuzzyMatchTestRoute : NavKey

private class ScoredCandidate(
    val label: AnnotatedString,
    val score: Float,
)

@Composable
fun FuzzyMatchTestScreen() {
    PreferenceScreen(
        title = "Fuzzy match test"
    ) {
        item {
            val normalizer: StringNormalizer = koinInject()
            var query by remember { mutableStateOf("") }
            var candidates by remember {
                mutableStateOf(
                    listOf(
                        "Calendar", "Camera", "Chrome", "Clock", "Contacts",
                        "Drive", "Files", "Gemini", "Glasses", "Gmail",
                        "Google", "Kvaesitso", "Maps", "Messages", "Phone",
                        "Photos", "Play Store", "Safety", "Settings",
                        "YouTube", "YT Music",
                    ).joinToString("\n")
                )
            }

            val results by remember {
                derivedStateOf {
                    val normalizedQuery = normalizer.normalize(query)
                    candidates
                        .lineSequence()
                        .filter { it.isNotBlank() }
                        .mapNotNull { candidate ->
                            val normalized = normalizer.normalize(candidate)
                            val score = ResultScore.from(normalizedQuery, primaryFields = listOf(normalized)).score
                            if (score < ResultScore.MatchThreshold) return@mapNotNull null
                            val match = FzfMatcher.match(normalizedQuery, normalized, withPositions = true)
                            ScoredCandidate(
                                label = highlight(normalized, match?.positions),
                                score = score,
                            )
                        }
                        .sortedByDescending { it.score }
                        .toList()
                }
            }

            PreferenceCategory {
                Surface {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Query") },
                        singleLine = true,
                    )
                }
                Surface {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        value = candidates,
                        onValueChange = { candidates = it },
                        label = { Text("Candidates (one per line)") },
                    )
                }
                Surface {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        if (results.isEmpty()) {
                            Text(
                                text = "No matches",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        for (result in results) {
                            Text(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                text = buildAnnotatedString {
                                    append(result.label)
                                    append("  ")
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                        append(String.format("%.3f", result.score))
                                        append("  ")
                                        append(tierOf(result.score))
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Mirrors the score bands documented on [ResultScore.from].
 */
private fun tierOf(score: Float): String = when {
    score >= 0.95f -> "strong"
    score >= 0.9f -> "weak"
    else -> "typo"
}

private fun highlight(text: String, positions: IntArray?): AnnotatedString {
    if (positions == null || positions.isEmpty()) return AnnotatedString(text)
    val matched = positions.toHashSet()
    return buildAnnotatedString {
        for (i in text.indices) {
            if (matched.contains(i)) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text[i]) }
            } else {
                append(text[i])
            }
        }
    }
}
