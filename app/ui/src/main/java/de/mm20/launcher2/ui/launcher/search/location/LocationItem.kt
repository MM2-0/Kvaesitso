package de.mm20.launcher2.ui.launcher.search.location

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.times
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.i18n.R
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.PublicTransportStop
import de.mm20.launcher2.ui.animation.animateHorizontalAlignmentAsState
import de.mm20.launcher2.ui.animation.animateTextStyleAsState
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.ktx.DegreesConverter
import de.mm20.launcher2.ui.ktx.metersToLocalizedString
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.modifier.scale
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlin.math.pow

@Composable
fun LocationItem(
    modifier: Modifier = Modifier,
    location: Location,
    showDetails: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${location.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()

    val userLocation by remember {
        viewModel.devicePoseProvider.getLocation()
    }.collectAsStateWithLifecycle(viewModel.devicePoseProvider.lastLocation)
    val insaneUnits by viewModel.useInsaneUnits.collectAsState()

    val isUpToDate by viewModel.isUpToDate.collectAsState()

    val distance = userLocation?.distanceTo(location.toAndroidLocation())

    Row(modifier = modifier) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val icon by viewModel.icon.collectAsStateWithLifecycle()
                    val badge by viewModel.badge.collectAsState(null)
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .aspectRatio(1f)
                    ) {
                        ShapedLauncherIcon(
                            size = 48.dp,
                            icon = { icon },
                            badge = { badge },
                        )
                        val targetIconAnimationValue = if (isUpToDate) 0f else 1f
                        val animatedIconAlpha by animateFloatAsState(
                            targetValue = targetIconAnimationValue,
                            animationSpec = tween(delayMillis = 275)
                        )
                        val animatedIconSize by animateDpAsState(
                            targetValue = targetIconAnimationValue * 20.dp,
                            animationSpec = tween(delayMillis = 275, easing = EaseOutBack)
                        )
                        Box(
                            Modifier
                                .size(22.dp)
                                .align(Alignment.BottomEnd)
                        ) {
                            Icon(
                                modifier = Modifier
                                    .size(animatedIconSize)
                                    .alpha(animatedIconAlpha)
                                    .align(Alignment.Center)
                                    .clickable(!isUpToDate) {
                                        Toast
                                            .makeText(
                                                context,
                                                R.string.cached_searchable,
                                                Toast.LENGTH_SHORT
                                            )
                                            .show()
                                    },
                                imageVector = Icons.TwoTone.CloudOff,
                                contentDescription = null
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(.75f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val textStyle by animateTextStyleAsState(
                        if (showDetails) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.titleSmall
                    )
                    val titleAlignment by animateHorizontalAlignmentAsState(
                        targetAlignment = if (showDetails) Alignment.CenterHorizontally else Alignment.Start
                    )
                    Text(
                        text = location.labelOverride ?: location.label,
                        modifier = Modifier.align(titleAlignment),
                        style = textStyle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true,
                    )
                    if (!location.openingSchedule?.openingHours.isNullOrEmpty()) {
                        val isOpen = location.openingSchedule!!.isOpen
                        AnimatedVisibility(!showDetails) {
                            Text(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .fillMaxWidth(),
                                text = context.getString(if (isOpen) R.string.location_open else R.string.location_closed),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOpen) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Start,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.padding(end = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceAround,
                ) {
                    val targetHeading by remember(userLocation, location) {
                        if (userLocation != null)
                            viewModel.devicePoseProvider.getHeadingToDegrees(
                                userLocation!!.bearingTo(
                                    location.toAndroidLocation()
                                )
                            )
                        else
                            emptyFlow()
                    }.collectAsStateWithLifecycle(null)

                    if (targetHeading != null) {
                        val directionArrowAngle by animateValueAsState(
                            targetValue = targetHeading!!,
                            typeConverter = Float.DegreesConverter
                        )
                        Icon(
                            modifier = Modifier.rotate(directionArrowAngle),
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = null
                        )
                    }
                    if (distance != null) {
                        Text(
                            text = distance.metersToLocalizedString(
                                context, insaneUnits
                            ), style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            AnimatedVisibility(showDetails) {
                Column {
                    val isTwentyFourSeven = location.openingSchedule?.isTwentyFourSeven ?: false
                    val hasOpeningHours = !location.openingSchedule?.openingHours.isNullOrEmpty()
                    val daysOfWeek = enumValues<DayOfWeek>()

                    val javaLocale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
                    val timeFormatter = DateTimeFormatter
                        .ofLocalizedTime(FormatStyle.SHORT)
                        .withLocale(javaLocale)

                    if (isTwentyFourSeven) {
                        Text(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 8.dp),
                            text = stringResource(id = R.string.location_open_24_7),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    } else if (hasOpeningHours) {
                        val oh = location.openingSchedule!!.openingHours
                        val openIndex = oh.indexOfFirst { it.isOpen }
                        if (openIndex != -1) {
                            val todaySchedule = oh[openIndex]
                            Text(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(bottom = 8.dp),
                                text = stringResource(
                                    R.string.location_open_until,
                                    (todaySchedule.startTime + todaySchedule.duration).format(
                                        timeFormatter
                                    )
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }

                    val showMap by viewModel.showMap.collectAsState()
                    if (showMap) {
                        val zoomLevel = 19
                        val nTiles = 4

                        val tileServerUrl by viewModel.mapTileServerUrl.collectAsState()
                        val shape = MaterialTheme.shapes.small

                        val applyTheming by viewModel.applyMapTheming.collectAsState()
                        val showPositionOnMap by viewModel.showPositionOnMap.collectAsState()

                        HorizontalDivider()

                        MapTiles(
                            modifier = Modifier
                                .padding(top = 16.dp, bottom = 8.dp)
                                .align(Alignment.CenterHorizontally)
                                .fillMaxWidth(.9125f)
                                .aspectRatio(1f)
                                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                                .clip(shape)
                                .clickable {
                                    viewModel.launch(context)
                                },
                            tileServerUrl = tileServerUrl,
                            location = location,
                            initialZoomLevel = zoomLevel,
                            numberOfTiles = nTiles,
                            applyTheming = applyTheming,
                            userLocation = if (showPositionOnMap) userLocation?.let {
                                UserLocation(
                                    it.latitude,
                                    it.longitude
                                )
                            } else null,
                        )

                        val address = buildAddress(location.street, location.houseNumber)
                        if (address != null) {
                            Text(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(bottom = 8.dp),
                                text = address,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(top = 8.dp))

                    if (location is PublicTransportStop) {

                        LazyColumn(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceAround,
                            modifier = Modifier.height(50.dp)
                        ) {
                            items(location.departures) { dep ->
                                Text(
                                    "${dep.type.toString()} ${dep.line} to ${dep.lastStop} at ${dep.time}"
                                )
                            }
                        }

                        HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 8.dp))
                    }

                    if (!isTwentyFourSeven && hasOpeningHours) {
                        val today = LocalDateTime.now().dayOfWeek
                        val oh = location.openingSchedule!!.openingHours
                        val nextOpeningTime =
                            (0..DayOfWeek.SUNDAY.ordinal)
                                .firstNotNullOfOrNull {
                                    val dow =
                                        daysOfWeek[(today.ordinal + it) % (DayOfWeek.SUNDAY.ordinal + 1)]
                                    oh.filter {
                                        it.dayOfWeek == dow
                                    }.firstOrNull {
                                        it.dayOfWeek != today || it.startTime.isAfter(LocalTime.now())
                                    }
                                } ?: oh.first()

                        Text(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 8.dp),
                            text = stringResource(
                                if (nextOpeningTime.dayOfWeek == today) R.string.location_open_next
                                else R.string.location_open_next_day,
                                if (nextOpeningTime.dayOfWeek == today) {
                                    val untilOpenToday = Duration.between(
                                        LocalTime.now(),
                                        nextOpeningTime.startTime,
                                    )
                                    val hours = untilOpenToday.toHours()
                                    val minutes = untilOpenToday.toMinutes() % 60L
                                    if (hours > 0L) "${hours}h ${minutes}m"
                                    else "${minutes}m"
                                } else "${
                                    nextOpeningTime.dayOfWeek.getDisplayName(
                                        TextStyle.FULL_STANDALONE,
                                        javaLocale
                                    )
                                } ${nextOpeningTime.startTime.format(timeFormatter)}"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    val toolbarActions = mutableListOf<ToolbarAction>()

                    if (LocalFavoritesEnabled.current) {
                        val isPinned by viewModel.isPinned.collectAsState(false)
                        val favAction = if (isPinned) {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_unpin),
                                icon = Icons.Rounded.Star,
                                action = {
                                    viewModel.unpin()
                                }
                            )
                        } else {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_pin),
                                icon = Icons.Rounded.StarOutline,
                                action = {
                                    viewModel.pin()
                                })
                        }
                        toolbarActions.add(favAction)
                    }

                    if (!showMap) {
                        toolbarActions += DefaultToolbarAction(
                            label = stringResource(id = R.string.menu_map),
                            icon = Icons.Rounded.Map
                        ) {
                            viewModel.launch(context)
                        }

                    }

                    location.phoneNumber?.let {
                        toolbarActions += DefaultToolbarAction(
                            label = stringResource(id = R.string.menu_dial),
                            icon = Icons.Rounded.Phone
                        ) {
                            viewModel.viewModelScope.launch {
                                context.tryStartActivity(
                                    Intent(
                                        Intent.ACTION_DIAL, Uri.parse("tel:$it")
                                    )
                                )
                            }
                        }
                    }

                    location.websiteUrl?.let {
                        toolbarActions += DefaultToolbarAction(
                            label = stringResource(id = R.string.menu_website),
                            icon = Icons.Rounded.TravelExplore
                        ) {
                            viewModel.viewModelScope.launch {
                                context.tryStartActivity(
                                    Intent(
                                        Intent.ACTION_VIEW, Uri.parse(it)
                                    )
                                )
                            }
                        }
                    }

                    location.fixMeUrl?.let {
                        toolbarActions += DefaultToolbarAction(
                            label = stringResource(id = R.string.menu_bugreport),
                            icon = Icons.Rounded.BugReport,
                        ) {
                            context.tryStartActivity(
                                Intent(
                                    Intent.ACTION_VIEW, Uri.parse(location.fixMeUrl)
                                )
                            )
                        }
                    }

                    Toolbar(
                        modifier = Modifier.fillMaxWidth(),
                        leftActions = listOf(DefaultToolbarAction(
                            label = stringResource(id = R.string.menu_back),
                            icon = Icons.AutoMirrored.Rounded.ArrowBack
                        ) {
                            onBack()
                        }),
                        rightActions = toolbarActions,
                    )
                }
            }
        }
    }
}

@Composable
fun LocationItemGridPopup(
    location: Location,
    show: MutableTransitionState<Boolean>,
    animationProgress: Float,
    origin: Rect,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        show,
        enter = expandIn(
            animationSpec = tween(300),
            expandFrom = Alignment.TopEnd,
        ) { origin.roundToIntRect().size },
        exit = shrinkOut(
            animationSpec = tween(300),
            shrinkTowards = Alignment.TopEnd,
        ) { origin.roundToIntRect().size },
    ) {
        LocationItem(
            modifier = Modifier
                .fillMaxWidth()
                .scale(
                    1 - (1 - LocalGridSettings.current.iconSize / 84f) * (1 - animationProgress),
                    transformOrigin = TransformOrigin(1f, 0f)
                )
                .offset(
                    x = 16.dp * (1 - animationProgress).pow(10),
                    y = (-16).dp * (1 - animationProgress),
                ),
            location = location,
            showDetails = true,
            onBack = onDismiss,
        )
    }
}

private fun buildAddress(
    street: String?,
    houseNumber: String?,
): String? {
    val summary = StringBuilder()
    if (street != null) {
        summary.append(street, ' ')
        if (houseNumber != null) {
            summary.append(houseNumber, ' ')
        }
    }
    return if (summary.isEmpty()) null else summary.toString()
}
