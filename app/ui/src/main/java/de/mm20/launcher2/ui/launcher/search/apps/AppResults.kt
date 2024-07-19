package de.mm20.launcher2.ui.launcher.search.apps

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.icons.PrivateSpace
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.search.common.grid.GridResults
import de.mm20.launcher2.ui.locals.LocalGridSettings

fun LazyListScope.AppResults(
    key: String,
    profile: Profile? = null,
    isProfileLocked: Boolean = false,
    onProfileLockChange: ((Boolean) -> Unit)? = null,
    apps: List<Application>,
    highlightedItem: Application? = null,
    columns: Int,
    reverse: Boolean,
) {

    GridResults(
        key = key,
        items = apps,
        before = if (profile != null) {
            {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp, start = 16.dp, end = 4.dp, bottom = 4.dp)
                        .heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when (profile.type) {
                            Profile.Type.Work -> Icons.Rounded.Work
                            Profile.Type.Private -> Icons.Rounded.PrivateSpace
                            else -> Icons.Rounded.Person
                        },
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = when (profile.type) {
                            Profile.Type.Personal -> stringResource(R.string.apps_profile_main)
                            Profile.Type.Work -> stringResource(R.string.apps_profile_work)
                            Profile.Type.Private -> stringResource(R.string.apps_profile_private)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )
                    if (onProfileLockChange != null) {
                        FilledIconToggleButton(checked = isProfileLocked, onCheckedChange = {
                            onProfileLockChange(it)
                        }) {
                            Icon(
                                if (isProfileLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                null
                            )
                        }
                    }
                }
            }
        } else null,
        itemContent = {
            GridItem(
                item = it,
                showLabels = LocalGridSettings.current.showLabels,
                highlight = it.key == highlightedItem?.key
            )
        },
        reverse = reverse,
        columns = columns,
    )
}