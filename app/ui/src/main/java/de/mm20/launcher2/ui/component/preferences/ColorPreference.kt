package de.mm20.launcher2.ui.component.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godaddy.android.colorpicker.ClassicColorPicker
import de.mm20.launcher2.ui.ktx.toHexString

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
        var color by remember(value) { mutableStateOf(value) }
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

                    ClassicColorPicker(
                        color = value ?: Color.Black,
                        onColorChanged = {
                            color = it.toColor()
                        },
                        showAlphaBar = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )

                    var hexValue by remember(color) {
                        mutableStateOf(
                            color?.toHexString() ?: "#000000"
                        )
                    }

                    TextField(
                        modifier = Modifier.padding(top = 16.dp),
                        value = hexValue,
                        onValueChange = {
                            hexValue = it
                            if (Regex("#[0-9a-fA-F]{6}").matches(it)) {
                                val hex = it.substring(1).toIntOrNull(16) ?: return@TextField
                                color = Color(hex).copy(alpha = 1f)
                            }
                        }
                    )
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