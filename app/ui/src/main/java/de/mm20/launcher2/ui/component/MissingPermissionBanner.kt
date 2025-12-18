package de.mm20.launcher2.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R

@Composable
fun MissingPermissionBanner(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    secondaryAction: @Composable () -> Unit = {}
) {
    Banner(
        modifier = modifier,
        text = text,
        icon = R.drawable.lock_24px,
        primaryAction = {
            Button(
                modifier = Modifier.padding(start = 8.dp),
                onClick = onClick
            ) {
                Text(
                    stringResource(R.string.grant_permission),
                )
            }
        },
        secondaryAction = secondaryAction
    )
}