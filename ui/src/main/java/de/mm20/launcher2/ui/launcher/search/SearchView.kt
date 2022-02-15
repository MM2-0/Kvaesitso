package de.mm20.launcher2.ui.launcher.search

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.MdcLauncherTheme
import de.mm20.launcher2.ui.component.ProvideIconShape
import de.mm20.launcher2.ui.launcher.search.apps.AppResults
import de.mm20.launcher2.ui.launcher.search.calculator.CalculatorResults
import de.mm20.launcher2.ui.launcher.search.calendar.CalendarResults
import de.mm20.launcher2.ui.launcher.search.contacts.ContactResults
import de.mm20.launcher2.ui.launcher.search.favorites.FavoritesResults
import de.mm20.launcher2.ui.launcher.search.files.FileResults
import de.mm20.launcher2.ui.launcher.search.unitconverter.UnitConverterResults
import de.mm20.launcher2.ui.launcher.search.website.WebsiteResults
import de.mm20.launcher2.ui.launcher.search.wikipedia.WikipediaResults
import de.mm20.launcher2.ui.locals.LocalCardStyle
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs), KoinComponent {

    private val dataStore: LauncherDataStore by inject()

    init {
        val view = ComposeView(context)
        view.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        )
        view.setContent {
            MdcLauncherTheme {
                val cardStyle by remember {
                    dataStore.data.map { it.cards }.distinctUntilChanged()
                }.collectAsState(
                    Settings.CardSettings.getDefaultInstance()
                )
                val iconShape by remember {
                    dataStore.data.map {
                        if (it.easterEgg) Settings.IconSettings.IconShape.EasterEgg
                        else it.icons.shape
                    }.distinctUntilChanged()
                }.collectAsState(Settings.IconSettings.IconShape.Circle)

                val favoritesEnabled by remember {
                    dataStore.data.map { it.favorites.enabled }.distinctUntilChanged()
                }.collectAsState(true)

                CompositionLocalProvider(
                    LocalCardStyle provides cardStyle,
                    LocalFavoritesEnabled provides favoritesEnabled
                ) {
                    ProvideIconShape(iconShape) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(8.dp)
                        ) {
                            FavoritesResults()
                            AppResults()
                            UnitConverterResults()
                            CalculatorResults()
                            CalendarResults()
                            ContactResults()
                            WikipediaResults()
                            WebsiteResults()
                            FileResults()
                        }
                    }
                }
            }
        }
        addView(view)
    }
}