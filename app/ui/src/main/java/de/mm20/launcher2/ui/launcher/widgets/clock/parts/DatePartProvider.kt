package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.icu.text.DateFormat
import android.icu.util.ULocale
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.base.LocalTime
import de.mm20.launcher2.ui.locals.LocalCalendarSystems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*

class DatePartProvider : PartProvider {
    override fun getRanking(context: Context): Flow<Int> = flow {
        emit(1)
    }

    @Composable
    override fun Component(compactLayout: Boolean) {
        val time = LocalTime.current
        val date = Date(time)
        val verticalLayout = !compactLayout
        val context = LocalContext.current

        val calendars = LocalCalendarSystems.current

        val primaryCal = calendars.first()
        val secondaryCal = calendars.getOrNull(1)

        TextButton(
            colors = ButtonDefaults.textButtonColors(
                contentColor = LocalContentColor.current
            ),
            onClick = {
                val startMillis = System.currentTimeMillis()
                val builder = CalendarContract.CONTENT_URI.buildUpon()
                builder.appendPath("time")
                ContentUris.appendId(builder, startMillis)
                val intent = Intent(Intent.ACTION_VIEW)
                    .setData(builder.build())
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                context.tryStartActivity(intent)
            }) {
            if (verticalLayout) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = DateFormat.getInstanceForSkeleton(
                            primaryCal,
                            "EEEE, MMMM d, yyyy",
                            ULocale.getDefault(),
                        ).format(date),
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (secondaryCal != null) {
                        Text(
                            text = DateFormat.getInstanceForSkeleton(
                                secondaryCal,
                                "MMMM dd, yyyy",
                                ULocale.getDefault(),
                            ).format(date),
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalContentColor.current.copy(
                                LocalContentColor.current.alpha * 0.7f
                            )
                        )
                    }
                }
            } else {
                if (secondaryCal == null) {
                    val line1 = DateFormat.getInstanceForSkeleton(
                        primaryCal,
                        "EEEE",
                        ULocale.getDefault(),
                    ).format(date)
                    val line2 =
                        DateFormat.getInstanceForSkeleton(
                            primaryCal,
                            "MMMM d",
                            ULocale.getDefault(),
                        ).format(date)
                    Text(
                        text = "$line1\n$line2",
                        lineHeight = 1.2.em,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium,
                        )
                    )
                } else {
                    val line2 =
                        DateFormat.getInstanceForSkeleton(
                            secondaryCal,
                            "MMMM d",
                            ULocale.getDefault(),
                        ).format(date)
                    Column {
                        Text(
                            text = DateFormat.getInstanceForSkeleton(
                                primaryCal,
                                "E MMMM d",
                                ULocale.getDefault(),
                            ).format(date),
                            lineHeight = 1.2.em,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = DateFormat.getInstanceForSkeleton(
                                secondaryCal,
                                "MMMM d",
                                ULocale.getDefault(),
                            ).format(date),
                            lineHeight = 1.2.em,
                            style = MaterialTheme.typography.labelLarge,
                            color = LocalContentColor.current.copy(
                                LocalContentColor.current.alpha * 0.7f
                            )
                        )
                    }
                }
            }
        }
    }

}