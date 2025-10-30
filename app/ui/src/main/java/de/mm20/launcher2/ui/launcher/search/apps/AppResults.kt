package de.mm20.launcher2.ui.launcher.search.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.WorkOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.icons.PrivateSpace
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.search.common.grid.GridResults
import de.mm20.launcher2.ui.launcher.search.common.list.ListItem
import de.mm20.launcher2.ui.launcher.search.common.list.ListResults
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.locals.LocalGridSettings

fun LazyListScope.AppResults(
    onProfileSelected: (Int) -> Unit,
    profiles: List<Profile> = emptyList(),
    selectedProfileIndex: Int = -1,
    showProfileLockControls: Boolean = false,
    isProfileLocked: Boolean = false,
    onProfileLockChange: ((Profile, Boolean) -> Unit)? = null,
    apps: List<Application>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    highlightedItem: Application? = null,
    columns: Int,
    reverse: Boolean,
    showList: Boolean,
    hidePrivateProfile: Boolean = false,
    isPrivateProfileLocked: Boolean = false,
    showUnlockPrivateProfile: Boolean = false,
    privateProfileIndex: Int = -1
) {
    val before = if (profiles.size > 1 && privateProfileIndex == -1) {
         @Composable {
            Column(
                verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top,
            ) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedProfileIndex,
                    containerColor = Color.Transparent,
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    for ((i, profile) in profiles.withIndex()) {
                        if (hidePrivateProfile && profile.type == Profile.Type.Private && isPrivateProfileLocked) {
                            continue
                        }
                        LeadingIconTab(
                            selected = selectedProfileIndex == profiles.indexOf(profile),
                            text = {
                                Text(
                                    when (profile.type) {
                                        Profile.Type.Personal -> stringResource(R.string.apps_profile_main)
                                        Profile.Type.Work -> stringResource(R.string.apps_profile_work)
                                        Profile.Type.Private -> stringResource(R.string.apps_profile_private)
                                    }
                                )
                            },
                            icon = {
                                when (profile.type) {
                                    Profile.Type.Personal -> Icon(
                                        Icons.Rounded.Person,
                                        contentDescription = null
                                    )

                                    Profile.Type.Work -> Icon(
                                        Icons.Rounded.Work,
                                        contentDescription = null
                                    )

                                    Profile.Type.Private -> Icon(
                                        Icons.Rounded.PrivateSpace,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = {
                                onProfileSelected(i)
                            }
                        )
                    }
                }

                if (!showList || isProfileLocked) {
                    HorizontalDivider()
                }

                val profileType = profiles[selectedProfileIndex].type
                if (profileType != Profile.Type.Personal) {
                    if (isProfileLocked) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    MaterialTheme.shapes.small
                                )
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainer,
                                    MaterialTheme.shapes.small
                                )
                                .padding(vertical = 64.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                if (profileType == Profile.Type.Work) Icons.Rounded.WorkOff else Icons.Rounded.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                stringResource(
                                    if (profileType == Profile.Type.Work) R.string.profile_work_profile_state_locked
                                    else R.string.profile_private_profile_state_locked
                                ),
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (showProfileLockControls) {
                                Button(
                                    modifier = Modifier.padding(top = 32.dp),
                                    onClick = {
                                        onProfileLockChange?.invoke(
                                            profiles[selectedProfileIndex],
                                            false
                                        )
                                    },
                                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                                ) {
                                    Icon(
                                        if (profileType == Profile.Type.Work) Icons.Rounded.Work else Icons.Rounded.LockOpen,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(end = ButtonDefaults.IconSpacing)
                                            .size(ButtonDefaults.IconSize)
                                    )
                                    Text(
                                        stringResource(
                                            if (profileType == Profile.Type.Work) R.string.profile_work_profile_action_unlock
                                            else R.string.profile_private_profile_action_unlock
                                        )
                                    )
                                }
                            }
                        }
                    } else if (showProfileLockControls) {
                        FilledTonalButton(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            onClick = {
                                onProfileLockChange?.invoke(
                                    profiles[selectedProfileIndex],
                                    true
                                )
                            },
                            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                        ) {
                            Icon(
                                if (profileType == Profile.Type.Work) Icons.Rounded.WorkOff else Icons.Rounded.Lock,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = ButtonDefaults.IconSpacing)
                                    .size(ButtonDefaults.IconSize)
                            )
                            Text(
                                stringResource(
                                    if (profileType == Profile.Type.Work) R.string.profile_work_profile_action_lock
                                    else R.string.profile_private_profile_action_lock
                                )
                            )
                        }
                    }
                }
            }
        }
    } else {
        if (hidePrivateProfile && isPrivateProfileLocked && showUnlockPrivateProfile) {
            @Composable {
                OutlinedCard(
                    modifier = Modifier.padding(8.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.secondary,
                    ),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier.padding(end = 16.dp),
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null
                            )
                            Text(
                                text = stringResource(R.string.profile_private_profile_state_locked),
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        FlowRow(
                            Modifier
                                .align(Alignment.End)
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalArrangement = Arrangement.Bottom,
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.padding(start = 8.dp)) {
                                if (showProfileLockControls) {
                                    Button(
//                                        modifier = Modifier.padding(top = 32.dp),
                                        onClick = {
                                            onProfileLockChange?.invoke(
                                                profiles[privateProfileIndex],
                                                false
                                            )
                                        },
                                        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                                    ) {
                                        Icon(
                                            Icons.Rounded.LockOpen,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .padding(end = ButtonDefaults.IconSpacing)
                                                .size(ButtonDefaults.IconSize)
                                        )
                                        Text(
                                            stringResource(
                                                R.string.profile_private_profile_action_unlock
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else null
    }
    if (showList) {
        ListResults(
            key = "apps",
            items = if (isProfileLocked) emptyList() else apps,
            before = before?.let { { it() } },
            selectedIndex = selectedIndex,
            itemContent = { app, showDetails, index ->
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth(),
                    item = app,
                    showDetails = showDetails,
                    onShowDetails = { onSelect(if(it) index else -1) },
                    highlight = highlightedItem?.key == app.key
                )
            },
            reverse = reverse,
        )
    } else {
        GridResults(
            key = "apps",
            items = if (isProfileLocked) emptyList() else apps,
            before = before,
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

}