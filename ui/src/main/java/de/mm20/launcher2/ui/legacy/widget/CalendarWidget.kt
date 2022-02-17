package de.mm20.launcher2.ui.legacy.widget

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.MdcLauncherTheme
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.ProvideSettings
import de.mm20.launcher2.ui.launcher.widgets.calendar.CalendarWidget

class CalendarWidget : LauncherWidget {

    override val canResize: Boolean
        get() = false

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleRes
    )

    init {
        val composeView = ComposeView(context)
        composeView.setContent {
            MdcLauncherTheme {
                ProvideSettings {
                    // TODO: Temporary solution until parent widget card is rewritten in Compose
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                        LocalAbsoluteTonalElevation provides 1.dp
                    ) {
                        Column {
                            CalendarWidget()
                        }
                    }
                }
            }
        }
        addView(composeView)

    }


    override val name: String
        get() = resources.getString(R.string.widget_name_calendar)


    companion object {
        const val ID = "calendar"
    }
}