package de.mm20.launcher2.fragment

import android.Manifest
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.customview.customView
import de.mm20.launcher2.R
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.UserCalendar

class PreferencesCalendarFragment : PreferenceFragmentCompat() {

    private var hasCalendarPermission = false
    private val calendars = mutableListOf<UserCalendar>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_calendar)
        init()
    }


    fun init(requestPermission: Boolean = true) {
        val context = context ?: return
        hasCalendarPermission = context.checkPermission(Manifest.permission.READ_CALENDAR)
        if (hasCalendarPermission) {
            calendars.clear()
            calendars.addAll(CalendarEvent.getCalendars(context))
            val unselectedCalendars = LauncherPreferences.instance.unselectedCalendars.toMutableList()
            findPreference<Preference>("calendar_calendars")?.apply {
                var count = calendars.size - unselectedCalendars.size
                summary = resources.getQuantityString(R.plurals.preference_calendar_calendars_summary, count, count)
                isEnabled = true
                setOnPreferenceClickListener {
                    val sheetView = LinearLayout(activity)
                    sheetView.setPadding((8 * context.dp).toInt())
                    sheetView.orientation = LinearLayout.VERTICAL
                    sheetView.setBackgroundColor(ContextCompat.getColor(context, R.color.bottom_sheet))
                    var owner = ""
                    val padding = (8 * context.dp).toInt()
                    for (c in calendars) {
                        if (owner != c.owner) {
                            owner = c.owner
                            val text = TextView(activity)
                            text.setTextColor(ContextCompat.getColor(context, R.color.text_color_secondary_normal))
                            text.setPadding(padding, 2 * padding, padding, padding)
                            text.text = owner
                            sheetView.addView(text)
                        }
                        val checkbox = CheckBox(activity)
                        checkbox.text = c.name
                        checkbox.buttonTintList = ColorStateList.valueOf(CalendarEvent.getDisplayColor(context, c.color))
                        checkbox.setPadding(padding)
                        checkbox.isChecked = !unselectedCalendars.contains(c.id)
                        checkbox.setOnCheckedChangeListener { _, checked ->
                            if (checked) {
                                unselectedCalendars.remove(c.id)
                            } else {
                                unselectedCalendars.add(c.id)
                            }
                            LauncherPreferences.instance.unselectedCalendars = unselectedCalendars
                            count = calendars.size - unselectedCalendars.size
                            summary = resources.getQuantityString(R.plurals.preference_calendar_calendars_summary, count, count)
                        }
                        sheetView.addView(checkbox)
                    }
                    val scrollView = ScrollView(context)
                    scrollView.isNestedScrollingEnabled = true
                    scrollView.addView(sheetView)
                    MaterialDialog(context, BottomSheet()).show {
                        customView(view = scrollView)
                        title(R.string.preference_calendar_calendars)
                                .negativeButton(R.string.close) {
                                    dismiss()
                                }
                    }
                    true
                }
            }
        } else {
            if (requestPermission) {
                ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                        0)
            }
            findPreference<Preference>("calendar_calendars")?.apply {
                isEnabled = false
                setSummary(R.string.preference_permission_denied)
            }
        }
    }


    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).supportActionBar?.setTitle(R.string.preference_screen_calendarwidget)
        hasCalendarPermission = requireActivity().checkPermission(Manifest.permission.READ_CALENDAR)
                && requireActivity().checkPermission(Manifest.permission.WRITE_CALENDAR)
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        init(false)
    }
}