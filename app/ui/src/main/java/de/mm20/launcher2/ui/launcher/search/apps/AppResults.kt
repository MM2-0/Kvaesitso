package de.mm20.launcher2.ui.launcher.search.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.search.common.grid.GridResults
import de.mm20.launcher2.ui.launcher.search.common.list.ListItem
import de.mm20.launcher2.ui.launcher.search.common.list.ListResults
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.locals.LocalGridSettings
import java.util.Locale
import kotlin.math.ceil

data class AppAlphabetJumpTarget(
    val letter: String,
    val relativeListIndex: Int,
)

private data class SectionedApp(
    val app: Application,
    val originalIndex: Int,
)

private data class AppSection(
    val letter: String,
    val items: List<SectionedApp>,
)

fun buildAppAlphabetJumpTargets(
    apps: List<Application>,
    showList: Boolean,
    columns: Int,
    hasBeforeItem: Boolean,
): List<AppAlphabetJumpTarget> {
    if (apps.isEmpty()) return emptyList()
    val sections = buildAppSections(apps)
    var index = 0
    val targets = mutableListOf<AppAlphabetJumpTarget>()
    for ((sectionIndex, section) in sections.withIndex()) {
        targets.add(AppAlphabetJumpTarget(section.letter, index))
        if (showList) {
            index += 1 + section.items.size
        } else {
            val rows = ceil(section.items.size / columns.toFloat()).toInt()
            index += rows + 1
        }
        if (sectionIndex == 0 && hasBeforeItem) {
            // The profile tabs are part of the first section header item.
        }
    }
    return targets
}

private fun buildAppSections(apps: List<Application>): List<AppSection> {
    val sections = mutableListOf<AppSection>()
    var currentLetter: String? = null
    var currentItems = mutableListOf<SectionedApp>()
    for ((index, app) in apps.withIndex()) {
        val letter = app.labelForGrouping()
        if (currentLetter != letter) {
            if (currentLetter != null) {
                sections.add(AppSection(currentLetter, currentItems))
            }
            currentLetter = letter
            currentItems = mutableListOf()
        }
        currentItems.add(SectionedApp(app, index))
    }
    if (currentLetter != null) {
        sections.add(AppSection(currentLetter, currentItems))
    }
    return sections
}

private fun Application.labelForGrouping(): String {
    val source = (labelOverride ?: label).trim()
    val firstLetter = source.firstOrNull { it.isLetter() } ?: return "#"
    return firstLetter.uppercaseChar().toString().uppercase(Locale.getDefault())
}

@Composable
private fun AppSectionHeader(
    letter: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = letter,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

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
    showAlphabetScroller: Boolean = true,
) {
    val visibleApps = if (isProfileLocked) emptyList() else apps
    val sections = if (showAlphabetScroller) {
        buildAppSections(visibleApps)
    } else {
        if (visibleApps.isEmpty()) emptyList()
        else listOf(
            AppSection(
                letter = "",
                items = visibleApps.mapIndexed { index, app -> SectionedApp(app, index) },
            )
        )
    }
    val before = if (profiles.size > 1) {
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
                        val selected = selectedProfileIndex == profiles.indexOf(profile)
                        LeadingIconTab(
                            selected = selected,
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
                                        painterResource(if (selected) R.drawable.person_24px_filled else R.drawable.person_24px),
                                        contentDescription = null
                                    )

                                    Profile.Type.Work -> Icon(
                                        painterResource(if (selected) R.drawable.enterprise_24px_filled else R.drawable.enterprise_24px),
                                        contentDescription = null
                                    )

                                    Profile.Type.Private -> Icon(
                                        painterResource(if (selected) R.drawable.encrypted_24px_filled else R.drawable.encrypted_24px),
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
                                painterResource(if (profileType == Profile.Type.Work) R.drawable.enterprise_off_48px else R.drawable.lock_48px),
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
                                        painterResource(if (profileType == Profile.Type.Work) R.drawable.enterprise_20px else R.drawable.lock_open_20px),
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
                                painterResource(if (profileType == Profile.Type.Work) R.drawable.enterprise_off_20px else R.drawable.lock_20px),
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
    } else null
    if (showList) {
        if (before != null) {
            ListResults(
                key = "apps-before",
                items = emptyList<Application>(),
                before = { before() },
                reverse = reverse,
                itemContent = { _, _, _ -> },
            )
        }
        for ((sectionIndex, section) in sections.withIndex()) {
            if (showAlphabetScroller) {
                item(
                    key = "apps-${section.letter}-$sectionIndex-header",
                    contentType = { "apps-section-header" },
                ) {
                    AppSectionHeader(section.letter)
                }
            }
            ListResults(
                key = "apps-${section.letter}-$sectionIndex",
                items = section.items.map { it.app },
                selectedIndex = section.items.indexOfFirst { it.originalIndex == selectedIndex },
                itemContent = { app, showDetails, index ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth(),
                        item = app,
                        showDetails = showDetails,
                        onShowDetails = {
                            onSelect(
                                if (it) section.items[index].originalIndex
                                else -1
                            )
                        },
                        highlight = highlightedItem?.key == app.key
                    )
                },
                reverse = reverse,
            )
        }
    } else {
        for ((sectionIndex, section) in sections.withIndex()) {
            GridResults(
                key = "apps-${section.letter}-$sectionIndex",
                items = section.items.map { it.app },
                before = {
                    Column(
                        verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top,
                    ) {
                        if (sectionIndex == 0) {
                            before?.invoke()
                        }
                        if (showAlphabetScroller) {
                            AppSectionHeader(section.letter)
                        }
                    }
                },
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
        if (sections.isEmpty() && before != null) {
            GridResults(
                key = "apps-empty",
                items = emptyList<Application>(),
                before = before,
                reverse = reverse,
                columns = columns,
                itemContent = {},
            )
        }
    }

}
