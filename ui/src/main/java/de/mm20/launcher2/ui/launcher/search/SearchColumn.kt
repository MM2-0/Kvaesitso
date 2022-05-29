package de.mm20.launcher2.ui.launcher.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.LauncherActivityVM
import de.mm20.launcher2.ui.launcher.search.apps.AppResults
import de.mm20.launcher2.ui.launcher.search.appshortcuts.AppShortcutResults
import de.mm20.launcher2.ui.launcher.search.calculator.CalculatorResults
import de.mm20.launcher2.ui.launcher.search.calendar.CalendarResults
import de.mm20.launcher2.ui.launcher.search.contacts.ContactResults
import de.mm20.launcher2.ui.launcher.search.favorites.FavoritesResults
import de.mm20.launcher2.ui.launcher.search.files.FileResults
import de.mm20.launcher2.ui.launcher.search.unitconverter.UnitConverterResults
import de.mm20.launcher2.ui.launcher.search.website.WebsiteResults
import de.mm20.launcher2.ui.launcher.search.wikipedia.WikipediaResults
import de.mm20.launcher2.ui.layout.BottomReversed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchColumn(
    modifier: Modifier = Modifier,
    reverse: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top
    ) {
        FavoritesResults(reverse)
        AppResults(reverse)
        AppShortcutResults(reverse)
        UnitConverterResults(reverse)
        CalculatorResults(reverse)
        CalendarResults(reverse)
        ContactResults(reverse)
        WikipediaResults(reverse)
        WebsiteResults(reverse)
        FileResults(reverse)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            val viewModel: LauncherActivityVM = viewModel()
            Surface(
                shadowElevation = 2.dp,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium,
                onClick = { viewModel.showHiddenItems() }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                    )
                    Text(stringResource(R.string.show_hidden_items),
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

            }
        }
    }
}