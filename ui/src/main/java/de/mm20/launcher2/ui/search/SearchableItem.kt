package de.mm20.launcher2.ui.search

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.search.data.File
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.search.data.Wikipedia
import de.mm20.launcher2.ui.component.DefaultSwipeActions

@Composable
fun SearchableItem(
    modifier: Modifier = Modifier,
    item: Searchable,
    initialRepresentation: Representation = Representation.List
) {
    var representation by remember { mutableStateOf(initialRepresentation) }
    SearchableItem(
        modifier = modifier,
        item = item,
        representation = representation,
        initialRepresentation = initialRepresentation,
        onRepresentationChange = { representation = it }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchableItem(
    modifier: Modifier = Modifier,
    item: Searchable,
    representation: Representation,
    initialRepresentation: Representation,
    onRepresentationChange: ((Representation) -> Unit)
) {

    DefaultSwipeActions(
        modifier = modifier
            .padding(vertical = 4.dp, horizontal = 4.dp),
        item = item,
        enabled = representation == Representation.List
    ) {

        val transition = updateTransition(representation, label = "SearchableItem")

        val cardElevation by transition.animateDp(
            label = "cardElevation",
            transitionSpec = {
                if (targetState == Representation.Full) tween(200,  delayMillis = 200)
                else tween(200)
            }) {
            if (it == Representation.Full) 4.dp else 0.dp
        }


        val cardAlpha by transition.animateFloat(
            label = "cardAlpha",
            transitionSpec = {
                if (targetState == Representation.Full) tween(300)
                else tween(300, delayMillis = 100)
            }) {
            if (it == Representation.Grid) 0f else 1f
        }


        Card(
            backgroundColor = MaterialTheme.colors.surface.copy(alpha = cardAlpha),
            elevation = cardElevation
        ) {

            when (item) {
                is Application -> {
                    ApplicationItem(
                        app = item,
                        representation = representation,
                        initialRepresentation = initialRepresentation,
                        onRepresentationChange = onRepresentationChange
                    )
                }
                is File -> {
                    FileItem(
                        file = item,
                        representation = representation,
                        initialRepresentation = initialRepresentation,
                        onRepresentationChange = onRepresentationChange
                    )
                }
                is Wikipedia -> {
                    WikipediaItem(
                        wikipedia = item,
                        representation = representation,
                        initialRepresentation = initialRepresentation,
                        onRepresentationChange = onRepresentationChange)
                }
            }
        }
    }
}

enum class Representation {
    Grid,
    List,
    Full
}