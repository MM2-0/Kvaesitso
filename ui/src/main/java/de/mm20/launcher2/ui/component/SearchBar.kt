package de.mm20.launcher2.ui.component

import android.content.Intent
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.PagerState
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.locals.LocalWindowSize
import org.koin.androidx.compose.viewModel

/**
 * Search bar
 * @param pageTransition 0..1 how much the search bar should be shown (this will be 0 on widget page, 1 on
 * search page, and anything in between while swiping between those two pages
 */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@ExperimentalPagerApi
@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    widgetColumnState: ScrollState,
    offScreen: Float,
    onFocus: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }

    val viewModel: SearchVM by viewModel()

    LaunchedEffect(searchQuery) {
        viewModel.search(searchQuery)
    }

    val pageTransition = (pagerState.currentPage + pagerState.currentPageOffset).coerceIn(0f, 1f)


    val elevationTransition =
        (2 * widgetColumnState.value / LocalWindowSize.current.height).coerceIn(0f, 1f)

    Card(
        modifier = modifier
            .offset(y = (-100.dp * offScreen * (1 - pageTransition))),
        elevation = 8.dp * (pageTransition + elevationTransition).coerceIn(0f, 1f),
    ) {
        val textStyle = TextStyle(
            color = LocalContentColor.current,
            fontSize = 16.sp
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(48.dp)
                .fillMaxWidth()
        ) {
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    contentDescription = null
                )
            }
            Box(
                modifier = Modifier.weight(1f)
            ) {


                BasicTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                    },
                    cursorBrush = SolidColor(LocalContentColor.current),
                    textStyle = textStyle,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) onFocus()
                        }
                )
                if (searchQuery.isEmpty()) {
                    BasicText(
                        text = stringResource(id = R.string.edit_text_search_hint),
                        style = textStyle,
                        modifier = Modifier.alpha(ContentAlpha.medium)
                    )
                }
            }
            var showOverflowMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = {
                        if (searchQuery.isNotEmpty()) {
                            searchQuery = ""
                        } else {
                            showOverflowMenu = true
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    val menuClearIcon = AnimatedImageVector.animatedVectorResource(R.drawable.anim_ic_menu_clear)
                    Icon(
                        painter = rememberAnimatedVectorPainter(menuClearIcon, atEnd = searchQuery.isNotEmpty()),
                        null
                    )
                }
                val navController = LocalNavController.current
                val context = LocalContext.current
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false }) {
                    DropdownMenuItem(onClick = {
                        showOverflowMenu = false
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SET_WALLPAPER),
                                null
                            )
                        )
                    }) {
                        Text(
                            stringResource(id = R.string.wallpaper),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    DropdownMenuItem(onClick = {
                        showOverflowMenu = false
                        navController?.navigate("settings")
                    }) {
                        Text(
                            stringResource(id = R.string.title_activity_settings),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
