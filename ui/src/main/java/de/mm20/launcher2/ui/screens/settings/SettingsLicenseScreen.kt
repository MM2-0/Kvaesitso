package de.mm20.launcher2.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.licenses.AppLicense
import de.mm20.launcher2.licenses.OpenSourceLicenses
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun SettingsLicenseScreen(libraryName: String? = null) {
    val context = LocalContext.current
    val library = if (libraryName == null) {
        AppLicense.get(context)
    } else {
        OpenSourceLicenses.first { it.name == libraryName }
    }
    PreferenceScreen(title = stringResource(id = R.string.preference_screen_about)) {
        item {
            PreferenceCategory {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(text = library.name, style = MaterialTheme.typography.subtitle1)
                    library.description?.let { Text(text = it) }
                }
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colors.primary
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse(library.url)
                                })
                            }
                            .padding(all = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Rounded.OpenInBrowser, contentDescription = null)
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = stringResource(id = R.string.open_webpage),
                            style = MaterialTheme.typography.button
                        )
                    }
                }
            }
        }
        item {
            PreferenceCategory {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(id = library.licenseName),
                        style = MaterialTheme.typography.subtitle2
                    )
                    library.copyrightNote?.let {
                        Text(
                            text = it,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    Text(
                        text = context.resources.openRawResource(library.licenseText).reader()
                            .readText()
                    )
                }
            }
        }
    }
}