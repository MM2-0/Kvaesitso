package de.mm20.launcher2.ui.settings.debug

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
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.search.StringNormalizer
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data object StringNormalizerTestRoute: NavKey

@Composable
fun StringNormalizerTestScreen() {
    PreferenceScreen(
        title = "String normalization test"
    ) {
        item {
            val normalizer: StringNormalizer = koinInject()
            var string by remember { mutableStateOf("") }
            val normalizedString by remember {
                derivedStateOf {
                    normalizer.normalize(string)
                }
            }

            PreferenceCategory {
                Surface {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        value = string,
                        onValueChange = { string = it },
                    )
                }
                Surface {
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        text = normalizedString,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

    }
}