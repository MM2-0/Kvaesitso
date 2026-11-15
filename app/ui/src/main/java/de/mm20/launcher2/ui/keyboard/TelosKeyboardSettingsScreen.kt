package de.mm20.launcher2.ui.keyboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberAppBarTitle
import de.mm20.launcher2.ui.component.TelosCyberSectionLabel
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.telosTechSurface
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable data object TelosKeyboardRoute : NavKey

@Composable
fun TelosKeyboardSettingsScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current

    VirtualAppWindow {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (backStack.size > 1) {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(
                            painterResource(BaseR.drawable.arrow_back_24px),
                            contentDescription = stringResource(I18nR.string.hc_back),
                        )
                    }
                }
                TelosCyberAppBarTitle(
                    title = stringResource(I18nR.string.settings_keyboard),
                    status = stringResource(I18nR.string.keyboard_settings_category),
                    statusColor = TelosCyber.NeonCyan,
                    modifier = Modifier.weight(1f),
                )
            }

            TelosCyberSectionLabel(
                stringResource(I18nR.string.keyboard_enable_title),
                accent = TelosCyber.NeonGreen,
            )

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .telosTechSurface(
                            borderColor = TelosCyber.NeonCyan,
                            shape = RoundedCornerShape(18.dp),
                        )
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(I18nR.string.keyboard_enable_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(I18nR.string.keyboard_enable_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                ) {
                    Text(stringResource(I18nR.string.keyboard_enable_action))
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        context
                            .getSystemService(
                                android.view.inputmethod.InputMethodManager::class.java
                            )
                            ?.showInputMethodPicker()
                    }
                ) {
                    Text(stringResource(I18nR.string.keyboard_choose_action))
                }
            }

            PreferenceCategory(title = stringResource(I18nR.string.keyboard_settings_category)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.keyboard_incognito_title),
                    summary = stringResource(I18nR.string.preference_private_keyboard_summary),
                    value = TelosKeyboardState.isIncognitoMode,
                    onValueChanged = { TelosKeyboardState.isIncognitoMode = it },
                )
            }
        }
    }
}
