package de.mm20.launcher2.ui.launcher.search

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.SearchBarSettings
import de.mm20.launcher2.search.data.Websearch
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.launcher.LauncherActivityVM
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.locals.LocalCardStyle
import de.mm20.launcher2.ui.settings.SettingsActivity
import kotlinx.coroutines.awaitCancellation
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

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(focused) {
        val f = focused
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                if (f) focusRequester.requestFocus()
                awaitCancellation()
            } finally {
                focusManager.clearFocus()
            }
        }
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
                        activityViewModel.showEditFavorites()
                        onDismissRequest()
                    },
                    text = {
                        Text(stringResource(R.string.menu_item_edit_favs))
                    }
                )
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
                    }
                )
                DropdownMenuItem(
                    onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                        onDismissRequest()
                    },
                    text = {
                        Text(stringResource(R.string.settings))
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
        reverse = reverse
    )
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
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
            it == SearchBarLevel.Resting -> Color.White
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
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(websearches) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clickable {
                                        it
                                            .getLaunchIntent()
                                            ?.let {
                                                context.tryStartActivity(it)
                                            }
                                    }
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                Text(
                                    it.label,
                                    modifier = Modifier.padding(start = 4.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
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