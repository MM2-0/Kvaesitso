package de.mm20.launcher2.ui.launcher.search.location

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.i18n.R
import de.mm20.launcher2.ui.animation.animateTextStyleAsState
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.locals.LocalGridSettings
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun LocationItem(
    modifier: Modifier = Modifier,
    location: Location,
    showDetails: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${location.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()

    val userLocation by viewModel.getUserLocation(context).collectAsStateWithLifecycle(null)
    val distance = userLocation?.distanceTo(location.toAndroidLocation())

    LaunchedEffect(location) {
        viewModel.init(location, iconSize.toInt())
    }

    val closedColor = MaterialTheme.colorScheme.secondary
    val openColor = MaterialTheme.colorScheme.tertiary

    Row(modifier = modifier) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row {
                val padding by animateDpAsState(if (showDetails) 16.dp else 12.dp)
                Column(
                    modifier = Modifier.padding(
                        top = padding,
                        start = padding,
                        bottom = 12.dp,
                        end = padding
                    )
                ) {
                    val textStyle by animateTextStyleAsState(
                        if (showDetails) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.titleSmall
                    )
                    Row {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = location.category.getImageVector(),
                            contentDescription = null
                        )
                        Text(
                            modifier = Modifier.fillMaxHeight(),
                            text = location.labelOverride ?: location.label,
                            style = textStyle
                        )
                    }
                    AnimatedVisibility(!showDetails) {
                        Row(modifier = Modifier.padding(top = 2.dp)) {
                            if (!location.openingHours.isNullOrEmpty()) {
                                val isOpen = location.openingHours!!.any { it.isOpen }
                                Text(
                                    modifier = Modifier.padding(end = 8.dp),
                                    text = context.getString(if (isOpen) R.string.location_open else R.string.location_closed),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOpen) openColor else closedColor
                                )
                            }
                            Text(
                                text = location.getSummary(context, distance),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    AnimatedVisibility(showDetails) {
                        val tags by viewModel.tags.collectAsState(emptyList())
                        if (tags.isNotEmpty()) {
                            Text(
                                modifier = Modifier.padding(top = 1.dp, bottom = 4.dp),
                                text = tags.joinToString(separator = " #", prefix = "#"),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(showDetails) {
                Column {
                    Row(horizontalArrangement = Arrangement.SpaceEvenly) {

                        if (!location.openingHours.isNullOrEmpty()) {
                            val today = LocalDateTime.now().dayOfWeek
                            val daysOfWeek = enumValues<DayOfWeek>()

                            val nextOpeningTime =
                                (0 until DayOfWeek.SUNDAY.ordinal)
                                    .firstNotNullOfOrNull {
                                        val dow =
                                            daysOfWeek[(today.ordinal + it) % DayOfWeek.SUNDAY.ordinal]
                                        location.openingHours!!.filter { it.dayOfWeek == dow }
                                            .firstOrNull {
                                                it.dayOfWeek != today || it.startTime.isAfter(
                                                    LocalTime.now()
                                                )
                                            }
                                    } ?: location.openingHours!!.first()

                            val openIndex = location.openingHours!!.indexOfFirst { it.isOpen }
                            val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                            if (openIndex != -1) {
                                Text(
                                    text = stringResource(
                                        R.string.location_open_until,
                                        (LocalTime.now() + location.openingHours!![openIndex].duration).format(
                                            timeFormatter
                                        )
                                    )
                                )
                            }
                            Text(
                                text = stringResource(
                                    if (nextOpeningTime.dayOfWeek == today) R.string.location_open_next
                                    else R.string.location_open_next_day,
                                    if (nextOpeningTime.dayOfWeek == today) Duration.between(
                                        nextOpeningTime.startTime,
                                        LocalTime.now()
                                    )
                                    else "${
                                        nextOpeningTime.dayOfWeek.getDisplayName(
                                            TextStyle.SHORT,
                                            Locale.getDefault()
                                        )
                                    } ${nextOpeningTime.startTime.format(timeFormatter)}"
                                )
                            )
                        }

                        val userHeading by viewModel.getUserHeading(context).collectAsStateWithLifecycle(null)
                        if (userLocation != null && userHeading != null) {
                            val directionArrowAngle by animateFloatAsState(
                                targetValue = userLocation!!.bearingTo(location.toAndroidLocation()) - userHeading!!
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    modifier = Modifier
                                        .padding(top = 8.dp, bottom = 8.dp, start = 16.dp)
                                        .rotate(directionArrowAngle),
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = null
                                )
                                if (distance != null) {
                                    Text(
                                        text = distance.metersToString(context)
                                    )
                                }
                            }
                        }


                    }

                    Toolbar(
                        leftActions = listOf(
                            DefaultToolbarAction(
                                label = stringResource(id = R.string.menu_back),
                                icon = Icons.Rounded.ArrowBack
                            ) {
                                onBack()
                            }
                        ),
                        rightActions = listOfNotNull(
                            DefaultToolbarAction(
                                label = stringResource(id = R.string.menu_map),
                                icon = Icons.Rounded.Map
                            ) {
                                viewModel.viewModelScope.launch {
                                    location.launch(context, null)
                                }
                            },
                            location.websiteUrl.runCatching {
                                val uri = Uri.parse(this)
                                DefaultToolbarAction(
                                    label = stringResource(id = R.string.menu_website),
                                    icon = Icons.Rounded.TravelExplore
                                ) {
                                    viewModel.viewModelScope.launch {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }
                                }
                            }.getOrNull(),
                        )
                    )

                }


            }
        }
    }
}

private fun Location.getSummary(context: Context, distance: Float?): String {
    val summary = StringBuilder()
    if (distance != null) {
        summary.append(distance.metersToString(context), ' ')
    }
    if (this.street != null) {
        summary.append(this.street, ' ')
        if (this.houseNumber != null) {
            summary.append(this.houseNumber, ' ')
        }
    }
    return summary.toString()
}

private fun Float.metersToString(context: Context): String {
    val isKm = this >= 1000f
    val value =
        if (isKm) DecimalFormat().apply { maximumFractionDigits = 1; minimumFractionDigits = 0 }
            .format(this / 1000f)
        else this.roundToInt().toString()
    val unit =
        context.getString(if (isKm) R.string.unit_kilometer_symbol else R.string.unit_meter_symbol)

    return "$value $unit"
}

private fun LocationCategory?.getImageVector(): ImageVector = when (this) {
    LocationCategory.RESTAURANT -> Icons.Rounded.Restaurant
    LocationCategory.FAST_FOOD -> Icons.Rounded.Fastfood
    LocationCategory.BAR -> Icons.Rounded.LocalBar
    LocationCategory.CAFE -> Icons.Rounded.LocalCafe
    LocationCategory.HOTEL -> Icons.Rounded.Hotel
    LocationCategory.SUPERMARKET -> Icons.Rounded.LocalGroceryStore
    else -> Icons.Rounded.Place
}
