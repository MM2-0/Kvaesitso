package de.mm20.launcher2.ui.searchable

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.favorites.FavoritesViewModel
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DefaultSwipeActions
import de.mm20.launcher2.ui.search.Representation
import de.mm20.launcher2.ui.toPixels
import java.net.URLEncoder

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CalendarEventItem(
    event: CalendarEvent,
    initialRepresentation: Representation,
    modifier: Modifier
) {

    var representation by remember { mutableStateOf(initialRepresentation) }

    val favViewModel: FavoritesViewModel = viewModel()
    val isPinned by favViewModel.isPinned(event).observeAsState()

    val borderWidth = 8.dp.toPixels()

    val context = LocalContext.current
    DefaultSwipeActions(item = event, enabled = representation == Representation.List) {
        Card(
            elevation = animateDpAsState(if (representation == Representation.Full) 4.dp else 0.dp).value,
            border = BorderStroke(
                width = animateDpAsState(if (representation == Representation.List) 1.dp else 0.dp).value,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = animateFloatAsState(if (representation == Representation.List) 0.18f else 0f).value)
            ),
            modifier = modifier
        ) {
            Row(
                modifier = (if (representation == Representation.List) Modifier.clickable(
                    onClick = {
                        representation = Representation.Full
                    }) else Modifier)
                    .fillMaxWidth()
                    .drawWithCache {
                        val color = Color(CalendarEvent.getDisplayColor(context, event.color))
                        onDrawWithContent {
                            drawContent()
                            drawRect(color, size = size.copy(width = borderWidth))
                        }
                    }
            ) {
                Column {
                    Column(
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = event.label,
                            style = MaterialTheme.typography.titleMedium
                        )
                        AnimatedVisibility(
                            representation == Representation.List
                        ) {
                            Text(
                                text = formatEventTime(event = event),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    AnimatedVisibility(
                        representation == Representation.Full
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_time),
                                    contentDescription = null
                                )
                                Text(
                                    text = if (event.allDay) {
                                        stringResource(id = R.string.calendar_event_allday)
                                    } else {
                                        DateUtils.formatDateRange(
                                            LocalContext.current,
                                            event.startTime,
                                            event.endTime,
                                            DateUtils.FORMAT_SHOW_TIME
                                        )
                                    },
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                )
                            }

                            if (event.description.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_description),
                                        contentDescription = null
                                    )
                                    Text(
                                        text = event.description,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                            }

                            if (event.location.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW)
                                            intent.data = Uri.parse(
                                                "geo:0,0?q=${
                                                    URLEncoder.encode(
                                                        event.location,
                                                        "utf8"
                                                    )
                                                }"
                                            )
                                            context.tryStartActivity(intent, null)
                                        })
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_location),
                                        contentDescription = null
                                    )
                                    Text(
                                        text = event.location,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                IconButton(onClick = { representation = Representation.List }) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowBack,
                                        contentDescription = null
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    if (isPinned == true) {
                                        favViewModel.unpinItem(event)
                                    } else {
                                        favViewModel.pinItem(event)
                                    }
                                }) {
                                    Icon(
                                        painter = if (isPinned == true) rememberVectorPainter(
                                            Icons.Rounded.Star
                                        ) else painterResource(id = R.drawable.ic_star_outline),
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }


}

@Composable
fun formatEventTime(event: CalendarEvent): String {
    val isToday = DateUtils.isToday(event.startTime) && DateUtils.isToday(event.endTime)
    return if (isToday) {
        if (event.allDay) {
            stringResource(R.string.calendar_event_allday)
        } else {
            DateUtils.formatDateRange(
                LocalContext.current,
                event.startTime,
                event.endTime,
                DateUtils.FORMAT_SHOW_TIME
            )
        }
    } else {
        if (event.allDay) {
            DateUtils.formatDateRange(
                LocalContext.current,
                event.startTime,
                event.endTime,
                DateUtils.FORMAT_SHOW_DATE
            )
        } else {
            DateUtils.formatDateRange(
                LocalContext.current,
                event.startTime,
                event.endTime,
                DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE
            )
        }
    }
}