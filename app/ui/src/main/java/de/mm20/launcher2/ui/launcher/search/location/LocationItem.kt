package de.mm20.launcher2.ui.launcher.search.location

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.i18n.R
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.OpeningSchedule
import de.mm20.launcher2.ui.animation.animateHorizontalAlignmentAsState
import de.mm20.launcher2.ui.animation.animateTextStyleAsState
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.ktx.DegreesConverter
import de.mm20.launcher2.ui.ktx.metersToLocalizedString
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.modifier.scale
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun LocationItem(
    modifier: Modifier = Modifier,
    location: Location,
    showDetails: Boolean,
    onBack: () -> Unit,
    priorityCallback: ((key: String, priority: Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${location.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()

    val userLocation by remember {
        viewModel.devicePoseProvider.getLocation()
    }.collectAsStateWithLifecycle(null)
    val insaneUnits by viewModel.useInsaneUnits.collectAsState()

    val distance = userLocation?.distanceTo(location.toAndroidLocation())
    if (distance != null) priorityCallback?.invoke(location.key, distance.roundToInt())

    var openingSchedule by remember { mutableStateOf<OpeningSchedule?>(null) }
    var phoneNumber by remember { mutableStateOf<String?>(null) }
    var websiteUrl by remember { mutableStateOf<String?>(null) }
    var street by remember { mutableStateOf<String?>(null) }
    var houseNumber by remember { mutableStateOf<String?>(null) }
    var showBugreportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(location) {
        viewModel.init(location, iconSize.toInt())
        openingSchedule = location.getOpeningSchedule()
        websiteUrl = location.getWebsiteUrl()
        street = location.getStreet()
        houseNumber = location.getHouseNumber()
        phoneNumber = location.getPhoneNumber()
    }

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
                    ShapedLauncherIcon(
                        size = 48.dp,
                        icon = { icon },
                        badge = { badge },
                    )
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
                    if (!openingSchedule?.openingHours.isNullOrEmpty()) {
                        val isOpen = openingSchedule!!.isOpen
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
                    val userHeading by remember {
                        viewModel.devicePoseProvider.getNorthHeading()
                    }.collectAsStateWithLifecycle(null)

                    if (userLocation != null && userHeading != null) {
                        val directionArrowAngle by animateValueAsState(
                            targetValue = userLocation!!.bearingTo(location.toAndroidLocation()) - userHeading!!,
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
                    val isTwentyFourSeven = openingSchedule?.isTwentyFourSeven ?: false
                    val hasOpeningHours = !openingSchedule?.openingHours.isNullOrEmpty()
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
                        val oh = openingSchedule!!.openingHours
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
                            zoomLevel = zoomLevel,
                            numberOfTiles = nTiles,
                            applyTheming = applyTheming,
                            userLocation = if (showPositionOnMap) userLocation?.let { it.latitude to it.longitude } else null,
                        )

                        val address = buildAddress(street, houseNumber)
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

                    if (!isTwentyFourSeven && hasOpeningHours) {
                        val today = LocalDateTime.now().dayOfWeek
                        val oh = openingSchedule!!.openingHours
                        val nextOpeningTime =
                            (0 until DayOfWeek.SUNDAY.ordinal)
                                .firstNotNullOfOrNull {
                                    val dow =
                                        daysOfWeek[(today.ordinal + it) % DayOfWeek.SUNDAY.ordinal]
                                    oh.filter { it.dayOfWeek == dow }
                                        .firstOrNull {
                                            it.dayOfWeek != today || it.startTime.isAfter(
                                                LocalTime.now()
                                            )
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

                    Toolbar(
                        modifier = Modifier.fillMaxWidth(),
                        leftActions = listOf(DefaultToolbarAction(
                            label = stringResource(id = R.string.menu_back),
                            icon = Icons.Rounded.ArrowBack
                        ) {
                            onBack()
                        }),
                        rightActions = listOfNotNull(

                            if (!showMap) {
                                DefaultToolbarAction(
                                    label = stringResource(id = R.string.menu_map),
                                    icon = Icons.Rounded.Map
                                ) {
                                    viewModel.launch(context)
                                }
                            } else null,
                            phoneNumber?.let {
                                DefaultToolbarAction(
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
                            },
                            websiteUrl?.let {
                                DefaultToolbarAction(
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
                            },
                            location.fixMeUrl?.let {
                                DefaultToolbarAction(
                                    label = stringResource(id = R.string.menu_bugreport),
                                    icon = Icons.Rounded.BugReport,
                                ) {
                                    showBugreportDialog = true
                                }
                            }
                        )
                    )
                }
            }
        }
    }

    if (showBugreportDialog && location.fixMeUrl != null) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 35.dp,
            confirmButton = {
                TextButton(
                    onClick = { showBugreportDialog = false },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = stringResource(id = android.R.string.ok),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
            onDismissRequest = {
                showBugreportDialog = false
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.alert_bugreport),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = true,
                        textAlign = TextAlign.Justify
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        modifier = modifier.clickable {
                            showBugreportDialog = false
                            viewModel.viewModelScope.launch {
                                context.tryStartActivity(
                                    Intent(
                                        Intent.ACTION_VIEW, Uri.parse(location.fixMeUrl)
                                    )
                                )
                            }
                        },
                        text = location.fixMeUrl!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        )
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
            modifier = Modifier.fillMaxWidth()
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
