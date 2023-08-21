package de.mm20.launcher2.ui.component.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.component.colorpicker.HsvColorPicker
import de.mm20.launcher2.ui.component.colorpicker.rememberHsvColorPickerState

@Composable
fun ColorPreference(
    title: String,
    summary: String? = null,
    value: Color?,
    onValueChanged: (Color?) -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }
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
                        .size(36.dp)
                ) {}
            }
        },
        onClick = {
            showDialog = true
        }
    )
    if (showDialog) {
        var color by remember(value) { mutableStateOf(value ?: Color.Black) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val state = rememberHsvColorPickerState(value ?: Color.Black) {
                        color = it
                    }
                    HsvColorPicker(state = state)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChanged(color)
                    showDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}