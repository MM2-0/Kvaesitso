package de.mm20.launcher2.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.preferences.ui.AppFontSettings
import kotlin.math.roundToInt

@Composable
fun PerAppFontScaleDialog(
    appKey: String,
    appName: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val fontSettings = remember(context) { AppFontSettings(context) }
    var fontScale by remember(appKey) { mutableFloatStateOf(fontSettings.getFontScale(appKey)) }

    PerAppFontControls {
        AlertDialog(
            containerColor = virtualAppPopup(),
            onDismissRequest = onDismiss,
            title = {
                PerAppFontControls {
                    Text(stringResource(I18nR.string.per_app_font_dialog_title, appName))
                }
            },
            text = {
                PerAppFontControls {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        I18nR.string.per_app_font_scale_percent,
                                        (fontScale * 100).roundToInt(),
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton(
                                enabled = fontScale != 1.0f,
                                onClick = {
                                    fontScale = 1.0f
                                    fontSettings.persist(appKey, 1.0f)
                                },
                            ) {
                                Text(stringResource(I18nR.string.per_app_font_reset))
                            }
                        }
                        Slider(
                            value = fontScale,
                            onValueChange = {
                                val next = (it * 100).roundToInt() / 100f
                                fontScale = next
                                fontSettings.setLive(appKey, next)
                            },
                            onValueChangeFinished = { fontSettings.persist(appKey, fontScale) },
                            valueRange = AppFontSettings.MIN..AppFontSettings.MAX,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PerAppFontPreview(fontScale)
                    }
                }
            },
            confirmButton = {
                PerAppFontControls {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            },
        )
    }
}
