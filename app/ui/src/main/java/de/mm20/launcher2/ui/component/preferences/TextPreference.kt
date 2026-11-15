package de.mm20.launcher2.ui.component.preferences

import de.mm20.launcher2.ui.component.TelosCyberHeading

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource

@Composable
fun TextPreference(
    title: String,
    value: String,
    summary: String? = value,
    enabled: Boolean = true,
    onValueChanged: (String) -> Unit,
    placeholder: String? = null
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    Preference(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        var textFieldValue by rememberSaveable { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            title = {
                TelosCyberHeading {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                OutlinedTextField(
                    value = textFieldValue,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    onValueChange = { textFieldValue = it },
                    placeholder = placeholder?.let {
                        {
                            Text(
                                text = it,
                            )
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChanged(textFieldValue)
                    showDialog = false
                }) {
                    Text(
                        text = stringResource(android.R.string.ok),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                }) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                    )
                }
            }
        )
    }
}