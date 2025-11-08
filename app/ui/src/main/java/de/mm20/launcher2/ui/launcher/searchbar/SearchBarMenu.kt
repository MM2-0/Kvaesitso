package de.mm20.launcher2.ui.launcher.searchbar

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.LauncherScaffoldVM
import de.mm20.launcher2.ui.launcher.widgets.WidgetsVM
import de.mm20.launcher2.ui.settings.SettingsActivity

@Composable
fun RowScope.SearchBarMenu(
    searchBarValue: String,
    onInputClear: () -> Unit,
) {
    val context = LocalContext.current
    var showOverflowMenu by remember { mutableStateOf(false) }
    val rightIcon = AnimatedImageVector.animatedVectorResource(R.drawable.anim_ic_menu_clear)
    val launcherVM: LauncherScaffoldVM = viewModel()
    val widgetsVM: WidgetsVM = viewModel()

    IconButton(onClick = {
        if (searchBarValue.isNotBlank()) onInputClear()
        else showOverflowMenu = true
    }) {
        Icon(
            painter = rememberAnimatedVectorPainter(
                rightIcon,
                atEnd = searchBarValue.isNotEmpty()
            ),
            contentDescription = stringResource(if (searchBarValue.isNotBlank()) R.string.action_clear else R.string.action_more_actions),
            tint = LocalContentColor.current
        )
    }
    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
        DropdownMenuItem(
            onClick = {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SET_WALLPAPER),
                        null
                    )
                )
                showOverflowMenu = false
            },
            text = {
                Text(stringResource(R.string.wallpaper))
            },
            leadingIcon = {
                Icon(painterResource(R.drawable.wallpaper_24px), contentDescription = null)
            }
        )
        DropdownMenuItem(
            onClick = {
                context.startActivity(Intent(context, SettingsActivity::class.java))
                showOverflowMenu = false
            },
            text = {
                Text(stringResource(R.string.settings))
            },
            leadingIcon = {
                Icon(painterResource(R.drawable.settings_24px), contentDescription = null)
            }
        )
        val colorScheme = MaterialTheme.colorScheme
        DropdownMenuItem(
            onClick = {
                CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(
                        CustomTabColorSchemeParams.Builder()
                            .setToolbarColor(colorScheme.primaryContainer.toArgb())
                            .setSecondaryToolbarColor(colorScheme.secondaryContainer.toArgb())
                            .build()
                    )
                    .build()
                    .launchUrl(context, Uri.parse("https://kvaesitso.mm20.de/docs/user-guide"))
                showOverflowMenu = false
            },
            text = {
                Text(stringResource(R.string.help))
            },
            leadingIcon = {
                Icon(painterResource(R.drawable.help_24px), contentDescription = null)
            }
        )
    }
}