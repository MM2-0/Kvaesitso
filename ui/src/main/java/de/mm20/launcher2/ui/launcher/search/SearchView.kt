package de.mm20.launcher2.ui.launcher.search

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.MdcLauncherTheme
import de.mm20.launcher2.ui.base.ProvideSettings
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
import org.koin.core.component.KoinComponent

class SearchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs), KoinComponent {

    init {
        val view = ComposeView(context)
        view.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        )
        view.setContent {
            MdcLauncherTheme {
                ProvideSettings {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(8.dp)
                    ) {
                        FavoritesResults()
                        AppResults()
                        AppShortcutResults()
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
        addView(view)
    }
}