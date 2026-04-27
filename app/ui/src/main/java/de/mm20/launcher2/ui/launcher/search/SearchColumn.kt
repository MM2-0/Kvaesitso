package de.mm20.launcher2.ui.launcher.search

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Article
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.Contact
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.Website
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.launcher.search.apps.AppAlphabetJumpTarget
import de.mm20.launcher2.ui.launcher.search.apps.AppAlphabetScroller
import de.mm20.launcher2.ui.launcher.search.apps.QuickAccessItem
import de.mm20.launcher2.ui.launcher.search.apps.AppResults
import de.mm20.launcher2.ui.launcher.search.apps.buildAppAlphabetJumpTargets
import de.mm20.launcher2.ui.launcher.search.calculator.CalculatorResults
import de.mm20.launcher2.ui.launcher.search.calendar.CalendarResults
import de.mm20.launcher2.ui.launcher.search.contacts.ContactResults
import de.mm20.launcher2.ui.launcher.search.favorites.SearchFavorites
import de.mm20.launcher2.ui.launcher.search.favorites.SearchFavoritesVM
import de.mm20.launcher2.ui.launcher.search.files.FileResults
import de.mm20.launcher2.ui.launcher.search.filters.SearchFilters
import de.mm20.launcher2.ui.launcher.search.location.LocationResults
import de.mm20.launcher2.ui.launcher.search.shortcut.ShortcutResults
import de.mm20.launcher2.ui.launcher.search.unitconverter.UnitConverterResults
import de.mm20.launcher2.ui.launcher.search.website.WebsiteResults
import de.mm20.launcher2.ui.launcher.search.wikipedia.ArticleResults
import de.mm20.launcher2.ui.launcher.sheets.HiddenItemsSheet
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.theme.transparency.transparency
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.launch

@Composable
fun SearchColumn(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(0.dp),
    state: LazyListState = rememberLazyListState(),
    reverse: Boolean = false,
    userScrollEnabled: Boolean = true,
    onHideKeyboard: () -> Unit = {},
) {

    val columns = LocalGridSettings.current.columnCount
    val showList = LocalGridSettings.current.showList
    val showAlphabetScroller = LocalGridSettings.current.showAlphabetScroller
    val alphabetQuickAccessOnly = LocalGridSettings.current.alphabetQuickAccessOnly
    val context = LocalContext.current

    val viewModel: SearchVM = viewModel()

    val favoritesVM: SearchFavoritesVM = viewModel()
    val favorites by favoritesVM.favorites.collectAsState(emptyList())

    val hideFavs by viewModel.hideFavorites
    val favoritesEnabled by viewModel.favoritesEnabled.collectAsState(false)
    val allAppsEnabled by viewModel.allAppsEnabled.collectAsState(false)

    val apps = viewModel.appResults
    val workApps = viewModel.workAppResults
    val privateApps = viewModel.privateSpaceAppResults
    val profiles by viewModel.profiles.collectAsState(emptyList())
    val profileStates by viewModel.profileStates.collectAsState(emptyList())

    val appShortcuts = viewModel.appShortcutResults
    val contacts = viewModel.contactResults
    val files = viewModel.fileResults
    val events = viewModel.calendarResults
    val unitConverter = viewModel.unitConverterResults
    val calculator = viewModel.calculatorResults
    val wikipedia = viewModel.articleResults
    val locations = viewModel.locationResults
    val website = viewModel.websiteResults
    val hiddenResults = viewModel.hiddenResults

    val bestMatch by viewModel.bestMatch

    val query by viewModel.searchQuery
    val isSearchEmpty by viewModel.isSearchEmpty
    val showAlphabetIndexInCurrentMode = showAlphabetScroller && isSearchEmpty

    val missingCalendarPermission by viewModel.missingCalendarPermission.collectAsState(false)
    val missingShortcutsPermission by viewModel.missingAppShortcutPermission.collectAsState(false)
    val missingContactsPermission by viewModel.missingContactsPermission.collectAsState(false)
    val missingLocationPermission by viewModel.missingLocationPermission.collectAsState(false)
    val missingFilesPermission by viewModel.missingFilesPermission.collectAsState(false)
    val hasProfilesPermission by viewModel.hasProfilesPermission.collectAsState(false)

    val pinnedTags by favoritesVM.pinnedTags.collectAsState(emptyList())
    val selectedTag by favoritesVM.selectedTag.collectAsState(null)
    val compactTags by favoritesVM.compactTags.collectAsState(false)
    val favoritesEditButton by favoritesVM.showEditButton.collectAsState(false)
    val favoritesTagsExpanded by favoritesVM.tagsExpanded.collectAsState(false)

    val expandedCategory: SearchCategory? by viewModel.expandedCategory

    var selectedAppProfileIndex: Int by remember(isSearchEmpty) { mutableIntStateOf(0) }
    var selectedAppIndex: Int by remember(query) { mutableIntStateOf(-1) }
    var selectedContactIndex: Int by remember(query) { mutableIntStateOf(-1) }
    var selectedFileIndex: Int by remember(query) { mutableIntStateOf(-1) }
    var selectedCalendarIndex: Int by remember(query) { mutableIntStateOf(-1) }
    var selectedLocationIndex: Int by remember(query) { mutableIntStateOf(-1) }
    var selectedShortcutIndex: Int by remember(query) { mutableIntStateOf(-1) }
    var selectedArticleIndex: Int by remember(query) { mutableIntStateOf(-1) }
    var selectedWebsiteIndex: Int by remember(query) { mutableIntStateOf(-1) }

    val showFilters by viewModel.showFilters
    val coroutineScope = rememberCoroutineScope()
    val sheetManager = LocalBottomSheetManager.current
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var quickAccessHoldActive by remember { mutableStateOf(false) }

    val profileApps = when (profiles.getOrNull(selectedAppProfileIndex)?.type) {
        Profile.Type.Private -> privateApps
        Profile.Type.Work -> workApps
        else -> apps
    }
    val showProfileResults = isSearchEmpty && profiles.size > 1 && allAppsEnabled
    val isProfileLocked = profileStates.getOrNull(selectedAppProfileIndex)?.locked == true
    val shownApps = when {
        showProfileResults && !isProfileLocked -> profileApps
        showProfileResults -> emptyList()
        !isSearchEmpty || allAppsEnabled -> apps
        else -> emptyList()
    }
    val appResultsHasBeforeItem = showProfileResults
    val appAlphabetTargets = if (!showAlphabetIndexInCurrentMode) {
        emptyList()
    } else {
        listOf(
            AppAlphabetJumpTarget(letter = "*", relativeListIndex = -1)
        ) + buildAppAlphabetJumpTargets(
            apps = shownApps,
            showList = showList,
            columns = columns,
            hasBeforeItem = appResultsHasBeforeItem,
        )
    }
    val displayedAlphabetTargets = if (!showAlphabetIndexInCurrentMode) emptyList()
    else appAlphabetTargets
    val displayedAlphabetLetters = remember(displayedAlphabetTargets) {
        displayedAlphabetTargets.map { it.letter }.distinct()
    }

    val activeAlphabetLetter by remember(state, displayedAlphabetTargets) {
        derivedStateOf {
            val appRelativeIndex = state.firstVisibleItemIndex - 1
            displayedAlphabetTargets
                .lastOrNull { it.relativeListIndex <= appRelativeIndex }
                ?.letter
        }
    }
    val favoritesLabel = stringResource(R.string.favorites)
    val quickAccessItems = remember(pinnedTags, favoritesLabel) {
        buildList {
            add(QuickAccessItem(tag = null, label = favoritesLabel))
            pinnedTags.forEach { tag ->
                add(QuickAccessItem(tag = tag.tag, label = tag.label))
            }
        }
    }

    AnimatedContent(
        showFilters,
        modifier = modifier.padding(horizontal = 8.dp),
    ) {
        if (it) {
            BackHandler {
                viewModel.showFilters.value = false
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = if (reverse) Alignment.BottomCenter else Alignment.TopCenter,
            ) {
                SearchFilters(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                            MaterialTheme.shapes.medium
                        )
                        .padding(12.dp),
                    filters = viewModel.filters.value,
                    onFiltersChange = {
                        viewModel.setFilters(it)
                    }
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = state,
                    userScrollEnabled = userScrollEnabled && !quickAccessHoldActive,
                    contentPadding = paddingValues,
                    reverseLayout = reverse,
                ) {
                    if (!hideFavs && favoritesEnabled) {
                        val hideFavoritesTagSelector = showAlphabetScroller && alphabetQuickAccessOnly
                        val selectedTagLabel = pinnedTags.firstOrNull { it.tag == selectedTag }?.label
                        val quickAccessHeader = if (hideFavoritesTagSelector) {
                            selectedTagLabel ?: favoritesLabel
                        } else null
                        SearchFavorites(
                            favorites = favorites,
                            selectedTag = selectedTag,
                            pinnedTags = if (hideFavoritesTagSelector) emptyList() else pinnedTags,
                            tagsExpanded = favoritesTagsExpanded,
                            onSelectTag = { favoritesVM.selectTag(it) },
                            reverse = reverse,
                            onExpandTags = {
                                favoritesVM.setTagsExpanded(it)
                            },
                            compactTags = compactTags,
                            editButton = favoritesEditButton && !hideFavoritesTagSelector,
                            quickAccessHeader = quickAccessHeader
                        )
                    } else {
                        // Empty item to maintain scroll position
                        item(key = "favorites") {
                        }
                    }

                    if (showProfileResults) {
                        AppResults(
                            apps = profileApps,
                            highlightedItem = bestMatch as? Application,
                            profiles = profiles,
                            selectedProfileIndex = selectedAppProfileIndex,
                            onProfileSelected = {
                                selectedAppProfileIndex = it
                                onHideKeyboard()
                            },
                            isProfileLocked = isProfileLocked,
                            onProfileLockChange = { p, l ->
                                viewModel.setProfileLock(p, l)
                            },
                            columns = columns,
                            reverse = reverse,
                            showProfileLockControls = hasProfilesPermission,
                            showList = showList,
                            showAlphabetScroller = showAlphabetIndexInCurrentMode,
                            selectedIndex = selectedAppIndex,
                            onSelect = { selectedAppIndex = it },
                        )
                    } else if (!isSearchEmpty || allAppsEnabled) {
                        AppResults(
                            apps = apps,
                            highlightedItem = bestMatch as? Application,
                            onProfileSelected = {
                                selectedAppProfileIndex = it
                            },
                            columns = columns,
                            reverse = reverse,
                            showList = showList,
                            showAlphabetScroller = showAlphabetIndexInCurrentMode,
                            selectedIndex = selectedAppIndex,
                            onSelect = { selectedAppIndex = it },
                        )
                    }

                    if (!isSearchEmpty) {

                        ShortcutResults(
                            shortcuts = appShortcuts,
                            missingPermission = missingShortcutsPermission,
                            onPermissionRequest = {
                                viewModel.requestAppShortcutPermission(context as AppCompatActivity)
                            },
                            onPermissionRequestRejected = {
                                viewModel.disableAppShortcutSearch()
                            },
                            reverse = reverse,
                            selectedIndex = selectedShortcutIndex,
                            onSelect = { selectedShortcutIndex = it },
                            highlightedItem = bestMatch as? AppShortcut,
                            truncate = expandedCategory != SearchCategory.Shortcuts,
                            onShowAll = {
                                viewModel.expandCategory(SearchCategory.Shortcuts)
                            },
                        )

                    UnitConverterResults(
                        converters = unitConverter,
                        reverse = reverse,
                        truncate = expandedCategory != SearchCategory.UnitConverter,
                        onShowAll = {
                            viewModel.expandCategory(SearchCategory.UnitConverter)
                        }
                    )

                    CalculatorResults(
                        calculator,
                        reverse = reverse
                    )

                    CalendarResults(
                        events = events,
                        missingPermission = missingCalendarPermission,
                        onPermissionRequest = {
                            viewModel.requestCalendarPermission(context as AppCompatActivity)
                        },
                        onPermissionRequestRejected = {
                            viewModel.disableCalendarSearch()
                        },
                        reverse = reverse,
                        selectedIndex = selectedCalendarIndex,
                        onSelect = { selectedCalendarIndex = it },
                        highlightedItem = bestMatch as? CalendarEvent,
                        truncate = expandedCategory != SearchCategory.Calendar,
                        onShowAll = {
                            viewModel.expandCategory(SearchCategory.Calendar)
                        }
                    )

                    ContactResults(
                        contacts = contacts,
                        missingPermission = missingContactsPermission,
                        onPermissionRequest = {
                            viewModel.requestContactsPermission(context as AppCompatActivity)
                        },
                        onPermissionRequestRejected = {
                            viewModel.disableContactsSearch()
                        },
                        reverse = reverse,
                        selectedIndex = selectedContactIndex,
                        onSelect = { selectedContactIndex = it },
                        highlightedItem = bestMatch as? Contact,
                        truncate = expandedCategory != SearchCategory.Contacts,
                        onShowAll = {
                            viewModel.expandCategory(SearchCategory.Contacts)
                        },
                    )

                    LocationResults(
                        locations = locations,
                        missingPermission = missingLocationPermission,
                        onPermissionRequest = {
                            viewModel.requestLocationPermission(context as AppCompatActivity)
                        },
                        onPermissionRequestRejected = {
                            viewModel.disableLocationSearch()
                        },
                        reverse = reverse,
                        selectedIndex = selectedLocationIndex,
                        onSelect = { selectedLocationIndex = it },
                        highlightedItem = bestMatch as? Location,
                        truncate = expandedCategory != SearchCategory.Location,
                        onShowAll = {
                            viewModel.expandCategory(SearchCategory.Location)
                        }
                    )
                    ArticleResults(
                        articles = wikipedia,
                        selectedIndex = selectedArticleIndex,
                        onSelect = { selectedArticleIndex = it },
                        highlightedItem = bestMatch as? Article,
                        reverse = reverse,
                    )
                    WebsiteResults(
                        websites = website,
                        selectedIndex = selectedWebsiteIndex,
                        onSelect = { selectedWebsiteIndex = it },
                        highlightedItem = bestMatch as? Website,
                        reverse = reverse,
                    )
                    FileResults(
                        files = files,
                        onPermissionRequest = {
                            viewModel.requestFilesPermission(context as AppCompatActivity)
                        },
                        onPermissionRequestRejected = {
                            viewModel.disableFilesSearch()
                        },
                        reverse = reverse,
                        highlightedItem = bestMatch as? File,
                        missingPermission = missingFilesPermission,
                        selectedIndex = selectedFileIndex,
                        onSelect = {
                            selectedFileIndex = it
                        },
                        truncate = expandedCategory != SearchCategory.Files,
                        onShowAll = {
                            viewModel.expandCategory(SearchCategory.Files)
                        }
                        )
                    }
                }

                if (displayedAlphabetLetters.isNotEmpty()) {
                    AppAlphabetScroller(
                        letters = displayedAlphabetLetters,
                        activeLetter = activeAlphabetLetter,
                        maxVisibleLetters = 10,
                        quickAccessItems = quickAccessItems,
                        selectedQuickAccessTag = selectedTag,
                        onQuickAccessSelected = { tag ->
                            favoritesVM.selectTag(tag)
                            coroutineScope.launch {
                                state.animateScrollToItem(0)
                            }
                        },
                        onQuickAccessEdit = {
                            sheetManager.showEditFavoritesSheet()
                        },
                        onQuickAccessHoldChanged = { quickAccessHoldActive = it },
                        modifier = Modifier
                            .align(if (reverse) Alignment.TopEnd else Alignment.BottomEnd)
                            .padding(
                                end = 2.dp,
                                bottom = if (reverse) 0.dp else navigationBarBottomPadding + 42.dp,
                                top = if (reverse) 12.dp else 0.dp,
                            ),
                        onLetterTapped = { letter ->
                            val target = displayedAlphabetTargets.firstOrNull { it.letter == letter } ?: return@AppAlphabetScroller
                            coroutineScope.launch {
                                state.animateScrollToItem(1 + target.relativeListIndex)
                            }
                        },
                        onLetterDragged = { letter ->
                            val target = displayedAlphabetTargets.firstOrNull { it.letter == letter } ?: return@AppAlphabetScroller
                            coroutineScope.launch {
                                state.scrollToItem(1 + target.relativeListIndex)
                            }
                        },
                    )
                }
            }
        }

    }
    HiddenItemsSheet(
        expanded = sheetManager.hiddenItemsSheetShown.value,
        items = hiddenResults,
        onDismiss = { sheetManager.dismissHiddenItemsSheet() })
}


fun LazyListScope.SingleResult(
    highlight: Boolean = false,
    content: @Composable (() -> Unit)?
) {
    if (content == null) return
    item {
        LauncherCard(
            modifier = Modifier
                .padding(
                    horizontal = 8.dp,
                    vertical = 4.dp,
                ),
            color = if (highlight) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface.copy(MaterialTheme.transparency.surface)
        ) {
            content()
        }
    }
}
