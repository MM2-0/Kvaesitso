package de.mm20.launcher2.ui.launcher.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.launcher.LauncherActivityVM
import de.mm20.launcher2.ui.launcher.search.apps.AppResults
import de.mm20.launcher2.ui.launcher.search.appshortcuts.AppShortcutResults
import de.mm20.launcher2.ui.launcher.search.calculator.CalculatorResults
import de.mm20.launcher2.ui.launcher.search.calendar.CalendarResults
import de.mm20.launcher2.ui.launcher.search.contacts.ContactResults
import de.mm20.launcher2.ui.launcher.search.favorites.FavoritesResults
import de.mm20.launcher2.ui.launcher.search.files.FileResults
import de.mm20.launcher2.ui.launcher.search.hidden.HiddenResults
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
        HiddenResults()
    }
}