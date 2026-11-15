package de.mm20.launcher2.ui.component.preferences

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.colorpicker.HsvColorPicker
import de.mm20.launcher2.ui.component.colorpicker.rememberHsvColorPickerState

@Composable
fun ColorPreference(
    title: String,
    summary: String? = null,
    value: Color?,
    onValueChanged: (Color?) -> Unit = {}
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    Preference(
        title = title,
        summary = summary,
        controls = {
            value?.let {
                Surface(
                    color = it,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(36.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {}
            }
        },
        onClick = {
            showDialog = true
        }
    )
    if (showDialog) {
        var color by remember(value) { mutableStateOf(value ?: Color.Black) }
        // Only commit a colour the user actually picked; "OK" on an untouched dialog must not
        // turn an unset (default) colour into black
        var changed by remember(value) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            title = {
                TelosCyberHeading {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val state = rememberHsvColorPickerState(value ?: Color.Black) {
                        color = it
                        changed = true
                    }
                    HsvColorPicker(state = state)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (changed) onValueChanged(color)
                    showDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onValueChanged(null)
                    showDialog = false
                }) {
                    Text(stringResource(I18nR.string.reset))
                }
            }
        )
    }
}