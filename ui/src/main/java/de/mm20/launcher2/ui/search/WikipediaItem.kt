package de.mm20.launcher2.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.Wikipedia
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.favoritesToolbarAction

@Composable
fun WikipediaItem(
    wikipedia: Wikipedia,
    representation: Representation,
    initialRepresentation: Representation,
    onRepresentationChange: ((Representation) -> Unit)
) {
    Column(
        modifier = Modifier
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 24.dp, top = 16.dp)
        ) {
            Text(
                text = wikipedia.label,
                style = MaterialTheme.typography.titleLarge,
            )
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(
                    modifier = Modifier.padding(vertical = 4.dp),
                    text = stringResource(R.string.wikipedia_source),
                )
            }
            Text(
                text = wikipedia.text
            )
        }
        val leftActions = if (initialRepresentation == Representation.Full) {
            emptyList()
        } else {
            listOf(
                DefaultToolbarAction(
                    stringResource(id = R.string.menu_back),
                    Icons.Rounded.ArrowBack
                ) { onRepresentationChange(initialRepresentation) }
            )
        }
        val rightActions = listOf(
            favoritesToolbarAction(wikipedia),
            DefaultToolbarAction(
                stringResource(id = R.string.menu_share),
                Icons.Rounded.Share
            ) {
            }
        )
        Toolbar(
            modifier = Modifier.fillMaxWidth(),
            leftActions = leftActions,
            rightActions = rightActions
        )
    }
}