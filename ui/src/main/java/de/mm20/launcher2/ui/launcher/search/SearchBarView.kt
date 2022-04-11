package de.mm20.launcher2.ui.launcher.search

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.LauncherTheme
import de.mm20.launcher2.ui.locals.LocalCardStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), KoinComponent {

    var level: SearchBarLevel = SearchBarLevel.Resting
        set(value) {
            levelState.value = value
            field = value
        }

    private val dataStore: LauncherDataStore by inject()
    private val levelState = MutableLiveData(level)

    init {
        val view = ComposeView(context)
        view.setContent {
            val level by levelState.observeAsState(SearchBarLevel.Resting)
            val cardStyle by remember {
                dataStore.data.map { it.cards }.distinctUntilChanged()
            }.collectAsState(
                Settings.CardSettings.getDefaultInstance()
            )
            CompositionLocalProvider(
                LocalCardStyle provides cardStyle
            ) {
                LauncherTheme {
                    Box(contentAlignment = Alignment.TopCenter) {
                        SearchBar(
                            level
                        )
                    }
                }
            }
        }
        addView(view)
    }
}