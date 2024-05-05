package de.mm20.launcher2.ui.launcher.search.location

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.i18n.R
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.OpeningHours
import de.mm20.launcher2.search.OpeningSchedule
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.ktx.metersToLocalizedString
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.modifier.scale
import kotlinx.coroutines.flow.emptyFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
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

    val targetHeading by remember(userLocation, location) {
        if (userLocation != null) {
            viewModel.devicePoseProvider.getHeadingToDegrees(
                userLocation!!.bearingTo(
                    location.toAndroidLocation()
                )
            )
        } else emptyFlow()
    }.collectAsStateWithLifecycle(null)

    val userHeading by remember {
        if (userLocation != null) {
            viewModel.devicePoseProvider.getAzimuthDegrees()
        } else emptyFlow()
    }.collectAsStateWithLifecycle(null)

    val icon by viewModel.icon.collectAsStateWithLifecycle()
    val badge by viewModel.badge.collectAsStateWithLifecycle(null)

    val imperialUnits by viewModel.imperialUnits.collectAsState()

    val isUpToDate by viewModel.isUpToDate.collectAsState()

    val distance = userLocation?.distanceTo(location.toAndroidLocation())

    SharedTransitionLayout(
        modifier = modifier,
    ) {
        AnimatedContent(showDetails) { showDetails ->
            if (!showDetails) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShapedLauncherIcon(
                        modifier = Modifier
                            .animateEnterExit(
                                enter = slideIn { IntOffset(-it.width, 0) } + fadeIn(),
                                exit = slideOut { IntOffset(-it.width, 0) } + fadeOut(),
                            )
                            .padding(8.dp),
                        size = 48.dp,
                        icon = { icon },
                        badge = { badge },
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Text(
                            text = location.labelOverride ?: location.label,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .sharedBounds(
                                    rememberSharedContentState("label"),
                                    this@AnimatedContent
                                )
                        )
                        val category = location.category
                        val formattedDistance = distance?.metersToLocalizedString(
                            context, imperialUnits
                        )
                        if (category != null || formattedDistance != null) {
                            Text(
                                when {
                                    category != null && formattedDistance != null -> "${category} • ${formattedDistance}"
                                    category != null -> category.toString()
                                    formattedDistance != null -> formattedDistance
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .sharedElement(
                                        rememberSharedContentState("sublabel"),
                                        this@AnimatedContent
                                    )
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .animateEnterExit(
                                enter = slideIn { IntOffset(it.width, 0) } + fadeIn(),
                                exit = slideOut { IntOffset(it.width, 0) } + fadeOut(),
                            )
                            .padding(end = 8.dp)
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.shapes.small
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (targetHeading != null) Icons.Rounded.Navigation else Icons.Rounded.FiberManualRecord,
                            null,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(targetHeading ?: 0f),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            } else {
                val showMap by viewModel.showMap.collectAsState()
                Column {
                    if (showMap) {
                        val tileServerUrl by viewModel.mapTileServerUrl.collectAsState()
                        val shape = MaterialTheme.shapes.small

                        val applyTheming by viewModel.applyMapTheming.collectAsState()
                        val showPositionOnMap by viewModel.showPositionOnMap.collectAsState()
                        MapTiles(
                            modifier = Modifier
                                .animateEnterExit(
                                    enter = expandVertically(),
                                    exit = shrinkOut(),
                                )
                                .padding(12.dp)
                                .align(Alignment.CenterHorizontally)
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    viewModel.launch(context)
                                },
                            tileServerUrl = tileServerUrl,
                            location = location,
                            maxZoomLevel = 19,
                            tiles = IntSize(3, 2),
                            applyTheming = applyTheming,
                            userLocation = {
                                if (showPositionOnMap) userLocation?.let {
                                    UserLocation(
                                        it.latitude,
                                        it.longitude,
                                        heading = userHeading,
                                    )
                                } else null
                            },
                        )
                    }

                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = location.labelOverride ?: location.label,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .sharedBounds(
                                        rememberSharedContentState("label"),
                                        this@AnimatedContent
                                    )
                            )
                            val category = location.category
                            val formattedDistance = distance?.metersToLocalizedString(
                                context, imperialUnits
                            )
                            if (category != null || formattedDistance != null) {
                                Text(
                                    when {
                                        category != null && formattedDistance != null -> "${category} • ${formattedDistance}"
                                        category != null -> category.toString()
                                        formattedDistance != null -> formattedDistance
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .sharedElement(
                                            rememberSharedContentState("sublabel"),
                                            this@AnimatedContent
                                        )
                                )
                            }
                        }
                    }

                    val openingSchedule = location.openingSchedule
                    if (openingSchedule != null && (openingSchedule.isTwentyFourSeven || openingSchedule.openingHours.isNotEmpty())) {
                        var showOpeningSchedule by remember(openingSchedule) {
                            mutableStateOf(false)
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            onClick = {
                                if (!openingSchedule.isTwentyFourSeven) {
                                    showOpeningSchedule = true
                                }
                            }
                        ) {
                            AnimatedContent(showOpeningSchedule) { showSchedule ->
                                if (!showSchedule) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (openingSchedule.isTwentyFourSeven) {
                                            Text(
                                                text = stringResource(R.string.location_open_24_7),
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        } else {
                                            val text = remember(openingSchedule) {
                                                val currentOpeningTime =
                                                    openingSchedule.getCurrentOpeningHours()
                                                val timeFormat =
                                                    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                                                return@remember if (currentOpeningTime != null) {
                                                    val isSameDay =
                                                        currentOpeningTime.dayOfWeek == LocalDateTime.now().dayOfWeek
                                                    val formattedTime =
                                                        timeFormat.format(currentOpeningTime.startTime + currentOpeningTime.duration)
                                                    val closingTime = if (isSameDay) {
                                                        context.getString(
                                                            R.string.location_closes,
                                                            formattedTime
                                                        )
                                                    } else {
                                                        val dow = currentOpeningTime.dayOfWeek.getDisplayName(
                                                            TextStyle.SHORT,
                                                            Locale.getDefault()
                                                        )
                                                        context.getString(
                                                            R.string.location_closes_other_day,
                                                            dow,
                                                            formattedTime
                                                        )
                                                    }
                                                    "${context.getString(R.string.location_open)} • $closingTime"
                                                } else {
                                                    val nextOpeningTime =
                                                        openingSchedule.getNextOpeningHours()
                                                    val isSameDay = nextOpeningTime.dayOfWeek == LocalDateTime.now().dayOfWeek
                                                    val formattedTime = timeFormat.format(nextOpeningTime.startTime)
                                                    val openingTime = if (isSameDay) {
                                                        context.getString(
                                                            R.string.location_opens,
                                                            formattedTime
                                                        )
                                                    } else {
                                                        val dow = nextOpeningTime.dayOfWeek.getDisplayName(
                                                            TextStyle.SHORT,
                                                            Locale.getDefault()
                                                        )
                                                        context.getString(
                                                            R.string.location_opens_other_day,
                                                            dow,
                                                            formattedTime
                                                        )
                                                    }
                                                    "${context.getString(R.string.location_closed)} • $openingTime"
                                                }
                                            }

                                            Text(text = text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))

                                            Icon(Icons.AutoMirrored.Rounded.NavigateNext, null)
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    ) {
                                        val groups = remember(openingSchedule) {
                                            openingSchedule.openingHours
                                                .groupBy { it.dayOfWeek }.entries
                                                .sortedBy { it.key }
                                        }

                                        for (group in groups) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    vertical = 2.dp,
                                                    horizontal = 12.dp
                                                ),
                                            ) {
                                                Text(
                                                    modifier = Modifier.weight(1f),
                                                    text = group.key.getDisplayName(
                                                        TextStyle.FULL,
                                                        Locale.getDefault()
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                                val times = remember(group.value) {
                                                    val dateFormatter =
                                                        DateTimeFormatter.ofLocalizedTime(
                                                            FormatStyle.SHORT
                                                        )
                                                    group.value.sortedBy { it.startTime }
                                                        .joinToString(separator = ", ") {
                                                            "${it.startTime.format(dateFormatter)}–${
                                                                it.startTime.plus(
                                                                    it.duration
                                                                ).format(dateFormatter)
                                                            }"
                                                        }
                                                }
                                                Text(
                                                    text = times,
                                                    style = MaterialTheme.typography.labelMedium,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 12.dp, top = 8.dp)
                    ) {
                        AssistChip(
                            modifier = Modifier.padding(end = 12.dp),
                            onClick = {
                                context.tryStartActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("google.navigation:q=${location.latitude},${location.longitude}")
                                    ),
                                )
                            },
                            label = { Text(stringResource(R.string.menu_navigation)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Navigation, null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            }
                        )
                        location.phoneNumber?.let {
                            AssistChip(
                                modifier = Modifier.padding(end = 12.dp),
                                onClick = {
                                    context.tryStartActivity(
                                        Intent(
                                            Intent.ACTION_DIAL, Uri.parse("tel:$it")
                                        )
                                    )
                                },
                                label = { Text(stringResource(R.string.menu_dial)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Phone, null,
                                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                                    )
                                }
                            )
                        }

                        location.websiteUrl?.let {
                            AssistChip(
                                modifier = Modifier.padding(end = 12.dp),
                                onClick = {
                                    context.tryStartActivity(
                                        Intent(
                                            Intent.ACTION_VIEW, Uri.parse(it)
                                        )
                                    )
                                },
                                label = { Text(stringResource(R.string.menu_website)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Language, null,
                                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                                    )
                                }
                            )
                        }
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

    /*Row(modifier = modifier) {
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
                                context, imperialUnits
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


                    HorizontalDivider()


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

                    HorizontalDivider(Modifier.padding(top = 8.dp))

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
                }
            }
        }
    }*/
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

private fun OpeningSchedule.getCurrentOpeningHours(): OpeningHours? {
    return openingHours.find { it.isOpen }
}

private fun OpeningSchedule.getNextOpeningHours(): OpeningHours {
    val now = LocalDateTime.now()
    val sortedSchedule = this
        .openingHours
        .sortedWith { a, b ->
            if (a.dayOfWeek == b.dayOfWeek) {
                a.startTime.compareTo(b.startTime)
            } else {
                a.dayOfWeek.compareTo(b.dayOfWeek)
            }
        }

    return sortedSchedule
        .find {
            now.dayOfWeek < it.dayOfWeek || now.dayOfWeek == it.dayOfWeek && now.toLocalTime() < it.startTime
        } ?: sortedSchedule.first()
}