package de.mm20.launcher2.searchactions.actions

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import de.mm20.launcher2.ktx.tryStartActivity
import java.time.LocalTime

data class SetAlarmAction(
    override val label: String,
    val time: LocalTime
) : SearchAction {
    override val icon: SearchActionIcon = SearchActionIcon.Alarm
    override val iconColor: Int = 0
    override val customIcon: String? = null
    override fun start(context: Context) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, time.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
        }
        context.tryStartActivity(intent)
    }
}