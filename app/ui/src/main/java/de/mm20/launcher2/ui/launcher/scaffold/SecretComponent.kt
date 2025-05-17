package de.mm20.launcher2.ui.launcher.scaffold

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.settings.SettingsActivity

internal object SecretComponent : ScaffoldComponent() {
    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        val context = LocalContext.current
        Column(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val color = MaterialTheme.colorScheme.onSurface

            Text(
                "\uD83E\uDEF5\uD83D\uDE06",
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center
            )

            Text(
                "Congratulations, you've locked yourself out!",
                color = color,
                modifier = Modifier.padding(vertical = 16.dp),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Text(
                "You've discovered a combination of settings that makes both the search and settings inaccessible — effectively locking you out of the launcher.",
                color = color,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )

            Button(
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                onClick = {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            }, modifier = Modifier.padding(top = 24.dp)) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = null,
                    modifier = Modifier.padding(ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize)
                )
                Text(stringResource(R.string.settings))
            }
        }
    }
}