package de.mm20.launcher2.ui.launcher.scaffold

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.settings.SettingsActivity

internal object SecretComponent : ScaffoldComponent() {
    override var isAtTop: State<Boolean?> = mutableStateOf(true)
    override var isAtBottom: State<Boolean?> = mutableStateOf(true)

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        val context = LocalContext.current
        Column(
            modifier = modifier
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
                stringResource(R.string.bad_configuration_title),
                color = color,
                modifier = Modifier.padding(vertical = 16.dp),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Text(
                stringResource(R.string.bad_configuration_summary),
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
                    painterResource(R.drawable.settings_20px),
                    contentDescription = null,
                    modifier = Modifier.padding(ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize)
                )
                Text(stringResource(R.string.settings))
            }
        }
    }
}