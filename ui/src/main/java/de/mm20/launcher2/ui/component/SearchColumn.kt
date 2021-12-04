package de.mm20.launcher2.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.Divider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.insets.navigationBarsWithImePadding
import com.google.accompanist.insets.statusBarsPadding
import de.mm20.launcher2.ui.search.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchColumn(
    modifier: Modifier = Modifier,
    listState: LazyListState
) {

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsWithImePadding()
    ) {
        val apps = applicationResults()
        val favorites = favoriteResults()
        val files = fileResults()

        val calculator = calculatorItem()
        val wikipedia = wikipediaResult()


        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                state = listState
            ) {

                item {
                    // Search bar space
                    Spacer(
                        modifier = Modifier.requiredHeight(
                            64.dp
                        )
                    )
                }
                favorites(listState)
                apps(listState)
                calculator()
                wikipedia()
                files()
            }
        }
    }
}

fun LazyListScope.SectionDivider() {
    item {
        Divider(
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }
}