package de.mm20.launcher2.ui.launcher.search

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.SearchBarSettings
import de.mm20.launcher2.preferences.Settings.SearchBarSettings.SearchBarColors
import de.mm20.launcher2.search.data.Websearch
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.launcher.LauncherActivityVM
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.locals.LocalCardStyle
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import de.mm20.launcher2.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.inject
import java.io.File

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    level: () -> SearchBarLevel,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    reverse: Boolean = false,
) {
    val searchViewModel: SearchVM = viewModel()
    val activityViewModel: LauncherActivityVM = viewModel()

    val dataStore: LauncherDataStore by inject()

    val style by remember { dataStore.data.map { it.searchBar.searchBarStyle } }
        .collectAsState(SearchBarSettings.SearchBarStyle.Hidden)

    val color by remember { dataStore.data.map { it.searchBar.color } }
        .collectAsState(SearchBarSettings.SearchBarColors.Auto)

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val context = LocalContext.current

    LaunchedEffect(focused) {
        if (focused) focusRequester.requestFocus()
        else focusManager.clearFocus()
    }

    val query by searchViewModel.searchQuery.observeAsState("")

    val websearches by searchViewModel.websearchResults.observeAsState(emptyList())

    SearchBar(
        modifier,
        level(),
        websearches,
        value = query,
        onValueChange = {
            searchViewModel.search(it)
        },
        style = style,
        overflowMenu = { show, onDismissRequest ->
            DropdownMenu(expanded = show, onDismissRequest = onDismissRequest) {
                DropdownMenuItem(
                    onClick = {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SET_WALLPAPER),
                                null
                            )
                        )
                        onDismissRequest()
                    },
                    text = {
                        Text(stringResource(R.string.wallpaper))
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Wallpaper, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                        onDismissRequest()
                    },
                    text = {
                        Text(stringResource(R.string.settings))
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Settings, contentDescription = null)
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
                            .build().launchUrl(context, Uri.parse("https://kvaesitso.mm20.de/docs/user-guide"))
                        onDismissRequest()
                    },
                    text = {
                        Text(stringResource(R.string.help))
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.HelpOutline, contentDescription = null)
                    }
                )
            }
        },
        focusRequester = focusRequester,
        onFocus = {
            onFocusChange(true)
        },
        onUnfocus = {
            onFocusChange(false)
        },
        reverse = reverse,
        darkColors = color == SearchBarColors.Dark || color == SearchBarColors.Auto && LocalPreferDarkContentOverWallpaper.current
    )
}

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    level: SearchBarLevel,
    websearches: List<Websearch>,
    overflowMenu: @Composable (show: Boolean, onDismissRequest: () -> Unit) -> Unit = { _, _ -> },
    value: String,
    style: SearchBarSettings.SearchBarStyle,
    onValueChange: (String) -> Unit,
    onFocus: () -> Unit = {},
    onUnfocus: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    reverse: Boolean = false,
    darkColors: Boolean = false,
) {
    val context = LocalContext.current

    var showOverflowMenu by remember { mutableStateOf(false) }

    val transition = updateTransition(level, label = "Searchbar")


    val elevation by transition.animateDp(
        label = "elevation",
        transitionSpec = {
            when {
                initialState == SearchBarLevel.Resting -> tween(
                    durationMillis = 200,
                    delayMillis = 200
                )

                targetState == SearchBarLevel.Resting -> tween(durationMillis = 200)
                else -> tween(durationMillis = 500)
            }
        }
    ) {
        when {
            it == SearchBarLevel.Resting && style != SearchBarSettings.SearchBarStyle.Solid -> 0.dp
            it == SearchBarLevel.Raised -> 8.dp
            else -> 2.dp
        }
    }

    val backgroundOpacity by transition.animateFloat(label = "backgroundOpacity",
        transitionSpec = {
            when {
                initialState == SearchBarLevel.Resting -> tween(durationMillis = 200)
                targetState == SearchBarLevel.Resting -> tween(
                    durationMillis = 200,
                    delayMillis = 200
                )

                else -> tween(durationMillis = 200)
            }
        }) {
        when {
            it == SearchBarLevel.Active -> LocalCardStyle.current.opacity
            style != SearchBarSettings.SearchBarStyle.Transparent -> 1f
            it == SearchBarLevel.Resting -> 0f
            else -> 1f
        }
    }

    val contentColor by transition.animateColor(label = "textColor",
        transitionSpec = {
            when {
                initialState == SearchBarLevel.Resting -> tween(durationMillis = 200)
                targetState == SearchBarLevel.Resting -> tween(
                    durationMillis = 200,
                    delayMillis = 200
                )

                else -> tween(durationMillis = 500)
            }
        }) {
        when {
            style != SearchBarSettings.SearchBarStyle.Transparent -> MaterialTheme.colorScheme.onSurface
            it == SearchBarLevel.Resting -> if (darkColors) Color(0, 0, 0, 180) else Color.White
            else -> MaterialTheme.colorScheme.onSurface
        }
    }

    val opacity by transition.animateFloat(label = "opacity") {
        if (style == SearchBarSettings.SearchBarStyle.Hidden && it == SearchBarLevel.Resting) 0f
        else 1f
    }

    val rightIcon = AnimatedImageVector.animatedVectorResource(R.drawable.anim_ic_menu_clear)

    LauncherCard(
        modifier = modifier
            .alpha(opacity),
        backgroundOpacity = backgroundOpacity,
        elevation = elevation
    ) {
        Column(
            verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top
        ) {
            Row(
                modifier = Modifier.height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.padding(12.dp),
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = contentColor
                )
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_bar_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor
                        )
                    }
                    LaunchedEffect(level) {
                        if (level == SearchBarLevel.Resting) onUnfocus()
                    }
                    BasicTextField(
                        modifier = Modifier
                            .onFocusChanged {
                                if (it.hasFocus) onFocus()
                            }
                            .focusRequester(focusRequester)
                            .fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = contentColor
                        ),
                        singleLine = true,
                        value = value,
                        onValueChange = onValueChange,
                    )
                }
                Box {
                    IconButton(onClick = {
                        if (value.isNotBlank()) onValueChange("")
                        else showOverflowMenu = true
                    }) {
                        Icon(
                            painter = rememberAnimatedVectorPainter(
                                rightIcon,
                                atEnd = value.isNotBlank()
                            ),
                            contentDescription = null,
                            tint = contentColor
                        )
                    }
                    overflowMenu(showOverflowMenu) { showOverflowMenu = false }
                }
            }
            AnimatedVisibility(websearches.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .height(48.dp)
                        .padding(bottom = 12.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    items(websearches) {
                        AssistChip(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            onClick = {
                                it
                                    .getLaunchIntent()
                                    ?.let {
                                        context.tryStartActivity(it)
                                    }
                            },
                            label = { Text(it.label) },
                            leadingIcon = {
                                val icon = it.icon
                                if (icon == null) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = if (it.color == 0) MaterialTheme.colorScheme.primary else Color(
                                            it.color
                                        )
                                    )
                                } else {
                                    AsyncImage(
                                        modifier = Modifier.size(24.dp),
                                        model = File(icon),
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

enum class SearchBarLevel {
    /**
     * The default, "hidden" state, when the launcher is in its initial state (scroll position is 0
     * and search is closed)
     */
    Resting,

    /**
     * When the search is open but there is no content behind the search bar (scroll position is 0)
     */
    Active,

    /**
     * When there is content below the search bar which requires the search bar to be raised above
     * this content (scroll position is not 0)
     */
    Raised
}