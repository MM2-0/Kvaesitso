package de.mm20.launcher2.ui.legacy.component

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.MissingPermission
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.SearchViewModel
import de.mm20.launcher2.ui.legacy.search.SearchListView
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class CalendarView : FrameLayout, KoinComponent {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleRes
    )

    private val calendarEvents: LiveData<List<CalendarEvent>?>

    init {
        val permissionsManager: PermissionsManager = get()
        View.inflate(context, R.layout.view_search_category_list, this)
        layoutTransition = LayoutTransition()
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        val card = findViewById<ViewGroup>(R.id.card)
        card.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        val list = findViewById<SearchListView>(R.id.list)
        val viewModel: SearchViewModel by (context as AppCompatActivity).viewModels()
        calendarEvents = viewModel.calendarResults
        calendarEvents.observe(context as AppCompatActivity, {
            if (it == null) {
                visibility = View.GONE
                return@observe
            }
            if (it.isEmpty() && LauncherPreferences.instance.searchCalendars && !permissionsManager.checkPermission(
                    PermissionGroup.Calendar
                )
            ) {
                visibility = View.VISIBLE
                list.submitItems(
                    listOf(
                        MissingPermission(
                            context.getString(R.string.permission_calendar_search),
                            PermissionGroup.Calendar
                        )
                    )
                )
                return@observe
            }
            visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
            list.submitItems(it)
        })
    }
}