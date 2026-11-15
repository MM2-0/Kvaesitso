package de.mm20.launcher2.ui.settings.protection

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R

@Composable
fun CustomLockDialog(
    title: String,
    confirmTitle: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null,
    onPinChanged: () -> Unit = {},
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(if (isConfirming) confirmTitle ?: title else title)  } },
        text = {
            Column {
                TextField(
                    value = if (isConfirming) confirmPin else pin,
                    onValueChange = {
                        showError = false
                        onPinChanged()
                        if (it.length <= 8) {
                            if (isConfirming) confirmPin = it else pin = it
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = showError || errorMessage != null,
                    supportingText = if (showError) {
                        { Text(stringResource(I18nR.string.custom_lock_error_mismatch)) }
                    } else if (errorMessage != null) {
                        { Text(errorMessage) }
                    } else null
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (confirmTitle != null && !isConfirming) {
                        isConfirming = true
                    } else {
                        if (confirmTitle != null && pin != confirmPin) {
                            showError = true
                        } else {
                            onConfirm(pin)
                        }
                    }
                },
                enabled = (if (isConfirming) confirmPin else pin).length >= 4
            ) {
                Text(stringResource(if (confirmTitle != null && !isConfirming) I18nR.string.action_next else android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
