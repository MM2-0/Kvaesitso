package de.mm20.launcher2.ui.launcher.search.common.customattrs

import android.graphics.drawable.InsetDrawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.locals.LocalGridColumns

@Composable
fun CustomizeSearchableSheet(
    searchable: Searchable,
    onDismiss: () -> Unit,
) {
    val viewModel: CustomizeSearchableSheetVM =
        remember(searchable) { CustomizeSearchableSheetVM(searchable) }
    val context = LocalContext.current

    val pickIcon by viewModel.isIconPickerOpen.observeAsState(false)

    BottomSheetDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (pickIcon) R.string.icon_picker_title else R.string.menu_customize))
        },
        confirmButton = {
            if (pickIcon) {
                OutlinedButton(onClick = { viewModel.closeIconPicker() }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            } else {
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.close))
                }
            }
        }
    ) {
        if (!pickIcon) {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                val iconSize = 64.dp
                val iconSizePx = iconSize.toPixels()
                val icon by remember { viewModel.getIcon(iconSizePx.toInt()) }.collectAsState(null)
                val primaryColor = MaterialTheme.colorScheme.onSecondary
                val badgeDrawable = remember {
                    InsetDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.ic_edit),
                        8
                    ).also {
                        it.setTint(primaryColor.toArgb())
                    }
                }

                ShapedLauncherIcon(
                    size = iconSize,
                    icon = icon,
                    badge = Badge(
                        icon = badgeDrawable
                    ),
                    onClick = {
                        viewModel.openIconPicker()
                    }
                )
                OutlinedTextField(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    value = "",
                    onValueChange = {},
                    placeholder = {
                        Text(searchable.label)
                    },
                )
            }
        } else {
            val iconSize = 48.dp
            val iconSizePx = iconSize.toPixels()
            val suggestions by
            remember { viewModel.getIconSuggestions(iconSizePx.toInt()) }
                .observeAsState(emptyList())
            LazyVerticalGrid(columns = GridCells.Fixed(LocalGridColumns.current)) {
                items(suggestions) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        ShapedLauncherIcon(
                            size = iconSize,
                            icon = it.icon,
                            onClick = {
                                viewModel.pickIcon(it.data)
                            }
                        )
                    }
                }
            }
        }
    }

}