package de.mm20.launcher2.ui.legacy.search

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.text.format.DateUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.transition.Scene
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ktx.setStartCompoundDrawable
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import de.mm20.launcher2.ui.legacy.view.*
import java.net.URLEncoder

class CalendarDetailRepresentation : Representation {
    override fun getScene(rootView: SearchableView, searchable: Searchable, previousRepresentation: Int?): Scene {
        val calendarEvent = searchable as CalendarEvent

        val scene = Scene.getSceneForLayout(rootView, R.layout.view_calendar_detail, rootView.context)
        scene.setEnterAction {
            with(rootView) {
                findViewById<TextView>(R.id.calendarLabel).text = calendarEvent.label
                findViewById<View>(R.id.calendarColor).setBackgroundColor(CalendarEvent.getDisplayColor(context, calendarEvent.color))
                findViewById<SwipeCardView>(R.id.calendarEventCard).also {
                    it.leftAction = FavoriteSwipeAction(context, calendarEvent)
                    it.rightAction = HideSwipeAction(context, calendarEvent)
                    it.setOnClickListener {
                        rootView.representation = SearchableView.REPRESENTATION_FULL
                    }
                }
                val toolbar = findViewById<ToolbarView>(R.id.calendarToolbar)
                /*toolbar.alpha = 0f
                toolbar.animate()
                        .setStartDelay(100)
                        .setDuration(200)
                        .alpha(1f)
                        .start()*/
                setupMenu(rootView, toolbar, calendarEvent)
                addShortcuts(rootView, calendarEvent)
            }
        }
        return scene
    }

    private fun setupMenu(rootView: SearchableView, toolbar: ToolbarView, event: CalendarEvent) {

        val context = rootView.context

        val backAction = ToolbarAction(R.drawable.ic_arrow_back, context.getString(R.string.menu_back))
        backAction.clickAction = {
            rootView.back()
        }
        toolbar.addAction(backAction, ToolbarView.PLACEMENT_START)

        val favAction = FavoriteToolbarAction(context, event)
        toolbar.addAction(favAction, ToolbarView.PLACEMENT_END)

        val hideAction = VisibilityToolbarAction(context, event)
        toolbar.addAction(hideAction, ToolbarView.PLACEMENT_END)

        val openAction = ToolbarAction(R.drawable.ic_open_external, context.getString(R.string.calendar_menu_open_externally))
        openAction.clickAction = {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
            val intent = Intent(Intent.ACTION_VIEW).setData(uri)
            ActivityStarter.start(context, rootView, intent = intent)
        }
        toolbar.addAction(openAction, ToolbarView.PLACEMENT_END)
    }

    private fun addShortcuts(rootView: SearchableView, event: CalendarEvent) {

        val context = rootView.context
        val shortcutContainer = rootView.findViewById<LinearLayout>(R.id.calendarShortcuts)

        val timeView = (View.inflate(context, R.layout.view_list_item, null) as TextView).also {
            it.setStartCompoundDrawable(R.drawable.ic_time)
            it.text = formatTime(context, event)
        }

        shortcutContainer.addView(timeView)


        if (event.description.isNotEmpty()) {
            val descriptionView = (View.inflate(context, R.layout.view_list_item, null) as TextView).also {
                it.setStartCompoundDrawable(R.drawable.ic_description)
                it.text = HtmlCompat.fromHtml(event.description, HtmlCompat.FROM_HTML_MODE_COMPACT)
            }
            shortcutContainer.addView(descriptionView)
        }

        if (event.location.isNotEmpty()) {
            val locationView = (View.inflate(context, R.layout.view_list_item, null) as TextView).also {
                it.setStartCompoundDrawable(R.drawable.ic_location)
                it.text = event.location
                it.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse("geo:0,0?q=${URLEncoder.encode(event.location, "utf8")}")
                    ActivityStarter.start(context, rootView, intent = intent)
                }
            }
            shortcutContainer.addView(locationView)
        }

        if (event.attendees.isNotEmpty()) {
            val attendeesView = (View.inflate(context, R.layout.view_list_item, null) as TextView).also {
                it.setStartCompoundDrawable(R.drawable.ic_attendees)
                it.text = event.attendees.joinToString { it }
            }
        }

    }

    private fun formatTime(context: Context, event: CalendarEvent): String {
        if (event.allDay) return DateUtils.formatDateRange(context, event.startTime, event.endTime, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY)
        return DateUtils.formatDateRange(context, event.startTime, event.endTime, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY)
    }


}