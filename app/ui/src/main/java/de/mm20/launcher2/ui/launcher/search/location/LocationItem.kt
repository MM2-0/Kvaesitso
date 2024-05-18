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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.times
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.mm20.launcher2.i18n.R
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.search.OpeningHours
import de.mm20.launcher2.search.OpeningSchedule
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.RatingBar
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.ktx.metersToLocalizedString
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.ui.modifier.scale
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
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

    val showMap by viewModel.showMap.collectAsState()
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
                            .padding(12.dp),
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
                                    category != null && formattedDistance != null -> "${stringResource(category.labelRes)} • ${formattedDistance}"
                                    category != null -> category.toString()
                                    formattedDistance != null -> formattedDistance
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .sharedElement(
                                        rememberSharedContentState("sublabel"),
                                        this@AnimatedContent
                                    )
                            )
                        }
                    }
                    Compass(
                        targetHeading = targetHeading,
                        modifier = Modifier.padding(end = 12.dp) then
                                if (!showMap) {
                                    Modifier.sharedBounds(
                                        rememberSharedContentState("compass"),
                                        this@AnimatedContent
                                    )
                                } else {
                                    Modifier.animateEnterExit(
                                        enter = slideIn { IntOffset(it.width, 0) } + fadeIn(),
                                        exit = slideOut { IntOffset(it.width, 0) } + fadeOut(),
                                    )
                                }
                    )
                }
            } else {
                Column {
                    if (showMap) {
                        val tileServerUrl by viewModel.mapTileServerUrl.collectAsState()
                        val shape = MaterialTheme.shapes.small

                        val applyTheming by viewModel.applyMapTheming.collectAsState()
                        MapTiles(
                            modifier = Modifier
                                .animateEnterExit(
                                    enter = expandVertically(),
                                    exit = shrinkOut(),
                                )
                                .padding(top = 12.dp, start = 12.dp, end = 12.dp)
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
                                userLocation?.let {
                                    UserLocation(
                                        it.latitude,
                                        it.longitude,
                                        heading = userHeading,
                                    )
                                }
                            },
                        )
                    }

                    Row(
                        modifier = Modifier.padding(
                            top = 12.dp,
                            start = 12.dp,
                            end = 12.dp,
                            bottom = 4.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
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
                                        category != null && formattedDistance != null -> "${stringResource(category.labelRes)} • ${formattedDistance}"
                                        category != null -> category.toString()
                                        formattedDistance != null -> formattedDistance
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .sharedElement(
                                            rememberSharedContentState("sublabel"),
                                            this@AnimatedContent
                                        )
                                )
                            }
                            // TODO: add rating to location
                            if (!showMap && false) {
                                RatingBar(0.66f, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        //TODO: add rating to location
                        if (showMap && false) {
                            RatingBar(0.66f)
                        }
                        if (!showMap) {
                            Compass(
                                targetHeading = targetHeading,
                                modifier = Modifier
                                    .align(Alignment.Top)
                                    .sharedBounds(
                                        rememberSharedContentState("compass"),
                                        this@AnimatedContent
                                    ),
                                size = 56.dp,
                            )
                        }
                    }

                    val openingSchedule = location.openingSchedule
                    if (openingSchedule != null && (openingSchedule.isTwentyFourSeven || openingSchedule.openingHours.isNotEmpty())) {
                        var showOpeningSchedule by remember(openingSchedule) {
                            mutableStateOf(false)
                        }
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                            shape = MaterialTheme.shapes.small,
                            onClick = {
                                if (!openingSchedule.isTwentyFourSeven) {
                                    showOpeningSchedule = !showOpeningSchedule
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
                                                        val dow =
                                                            currentOpeningTime.dayOfWeek.getDisplayName(
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
                                                    val isSameDay =
                                                        nextOpeningTime.dayOfWeek == LocalDateTime.now().dayOfWeek
                                                    val formattedTime =
                                                        timeFormat.format(nextOpeningTime.startTime)
                                                    val openingTime = if (isSameDay) {
                                                        context.getString(
                                                            R.string.location_opens,
                                                            formattedTime
                                                        )
                                                    } else {
                                                        val dow =
                                                            nextOpeningTime.dayOfWeek.getDisplayName(
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

                                            Text(
                                                text = text,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.weight(1f)
                                            )

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

                    toolbarActions += DefaultToolbarAction(
                        label = stringResource(id = R.string.menu_map),
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    ) {
                        viewModel.launch(context)
                    }

                    val sheetManager = LocalBottomSheetManager.current
                    val lifecycleOwner = LocalLifecycleOwner.current
                    val snackbarHostState = LocalSnackbarHostState.current

                    toolbarActions.add(DefaultToolbarAction(
                        label = stringResource(de.mm20.launcher2.ui.R.string.menu_customize),
                        icon = Icons.Rounded.Edit,
                        action = { sheetManager.showCustomizeSearchableModal(location) }
                    ))



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

                    val isHidden by viewModel.isHidden.collectAsState(false)
                    val hideAction = if (isHidden) {
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_unhide),
                            icon = Icons.Rounded.Visibility,
                            action = {
                                viewModel.unhide()
                                onBack()
                            }
                        )
                    } else {
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_hide),
                            icon = Icons.Rounded.VisibilityOff,
                            action = {
                                viewModel.hide()
                                onBack()
                                lifecycleOwner.lifecycleScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(
                                            R.string.msg_item_hidden,
                                            location.labelOverride ?: location.label
                                        ),
                                        actionLabel = context.getString(R.string.action_undo),
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.unhide()
                                    }
                                }

                            })
                    }
                    toolbarActions.add(hideAction)

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
fun Compass(
    targetHeading: Float?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.shapes.small
            ),
        contentAlignment = Alignment.Center
    ) {
        if (targetHeading != null) {
            Icon(
                Icons.Rounded.Navigation,
                null,
                modifier = Modifier
                    .size(20f / 48f * size)
                    .rotate(targetHeading)
                    .offset(y = -1f / 48f * size),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
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

private fun OpeningSchedule.getCurrentOpeningHours(): OpeningHours? {
    return openingHours.find { it.isOpen() }
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

private val LocationCategory.labelRes
    get() = when(this) {
        LocationCategory.ART -> R.string.poi_category_art
        LocationCategory.BANK -> R.string.poi_category_bank
        LocationCategory.BAR -> R.string.poi_category_bar
        LocationCategory.BEAUTY -> R.string.poi_category_beauty
        LocationCategory.BICYCLE -> R.string.poi_category_bicycle
        LocationCategory.RESTAURANT -> R.string.poi_category_restaurant
        LocationCategory.FAST_FOOD -> R.string.poi_category_fast_food
        LocationCategory.CAFE -> R.string.poi_category_coffee_shop
        LocationCategory.HOTEL -> R.string.poi_category_hotel
        LocationCategory.SUPERMARKET -> R.string.poi_category_supermarket
        LocationCategory.OTHER -> R.string.poi_category_other
        LocationCategory.SCHOOL -> R.string.poi_category_school
        LocationCategory.PARKING -> R.string.poi_category_parking
        LocationCategory.FUEL -> R.string.poi_category_fuel
        LocationCategory.TOILETS -> R.string.poi_category_toilets
        LocationCategory.PHARMACY -> R.string.poi_category_pharmacy
        LocationCategory.HOSPITAL -> R.string.poi_category_hospital
        LocationCategory.POST_OFFICE -> R.string.poi_category_post_office
        LocationCategory.PUB -> R.string.poi_category_pub
        LocationCategory.GRAVE_YARD -> R.string.poi_category_grave_yard
        LocationCategory.DOCTORS -> R.string.poi_category_doctors
        LocationCategory.POLICE -> R.string.poi_category_police
        LocationCategory.DENTIST -> R.string.poi_category_dentist
        LocationCategory.LIBRARY -> R.string.poi_category_library
        LocationCategory.COLLEGE -> R.string.poi_category_college
        LocationCategory.ICE_CREAM -> R.string.poi_category_ice_cream
        LocationCategory.THEATRE -> R.string.poi_category_theater
        LocationCategory.PUBLIC_BUILDING -> R.string.poi_category_public_building
        LocationCategory.CINEMA -> R.string.poi_category_cinema
        LocationCategory.NIGHTCLUB -> R.string.poi_category_nightclub
        LocationCategory.BIERGARTEN -> R.string.poi_category_biergarten
        LocationCategory.CLINIC -> R.string.poi_category_clinic
        LocationCategory.UNIVERSITY -> R.string.poi_category_university
        LocationCategory.DEPARTMENT_STORE -> R.string.poi_category_department_store
        LocationCategory.CLOTHES -> R.string.poi_category_clothes
        LocationCategory.CONVENIENCE -> R.string.poi_category_convenience
        LocationCategory.HAIRDRESSER -> R.string.poi_category_hairdresser
        LocationCategory.CAR_REPAIR -> R.string.poi_category_car_repair
        LocationCategory.BOOKS -> R.string.poi_category_books
        LocationCategory.BAKERY -> R.string.poi_category_bakery
        LocationCategory.CAR -> R.string.poi_category_car
        LocationCategory.MOBILE_PHONE -> R.string.poi_category_mobile_phone
        LocationCategory.FURNITURE -> R.string.poi_category_furniture
        LocationCategory.ALCOHOL -> R.string.poi_category_alcohol
        LocationCategory.FLORIST -> R.string.poi_category_florist
        LocationCategory.HARDWARE -> R.string.poi_category_hardware
        LocationCategory.ELECTRONICS -> R.string.poi_category_electronics
        LocationCategory.SHOES -> R.string.poi_category_shoes
        LocationCategory.MALL -> R.string.poi_category_mall
        LocationCategory.OPTICIAN -> R.string.poi_category_optician
        LocationCategory.JEWELRY -> R.string.poi_category_jewelry
        LocationCategory.GIFT -> R.string.poi_category_gift
        LocationCategory.LAUNDRY -> R.string.poi_category_laundry
        LocationCategory.COMPUTER -> R.string.poi_category_computer
        LocationCategory.TOBACCO -> R.string.poi_category_tobacco
        LocationCategory.WINE -> R.string.poi_category_wine
        LocationCategory.PHOTO -> R.string.poi_category_photo
        LocationCategory.COFFEE_SHOP -> R.string.poi_category_coffee_shop
        LocationCategory.SOCCER -> R.string.poi_category_soccer
        LocationCategory.BASKETBALL -> R.string.poi_category_basketball
        LocationCategory.TENNIS -> R.string.poi_category_tennis
        LocationCategory.FITNESS -> R.string.poi_category_fitness
        LocationCategory.TRAM_STOP -> R.string.poi_category_tram_stop
        LocationCategory.RAILWAY_STATION -> R.string.poi_category_railway_station
        LocationCategory.RAILWAY_STOP -> R.string.poi_category_railway_stop
        LocationCategory.BUS_STATION -> R.string.poi_category_bus_station
        LocationCategory.ATM -> R.string.poi_category_atm
        LocationCategory.KIOSK -> R.string.poi_category_kiosk
        LocationCategory.BUS_STOP -> R.string.poi_category_bus_stop
        LocationCategory.MUSEUM -> R.string.poi_category_museum
        LocationCategory.PARCEL_LOCKER -> R.string.poi_category_parcel_locker
        LocationCategory.CHEMIST -> R.string.poi_category_chemist
        LocationCategory.TRAVEL_AGENCY -> R.string.poi_category_travel_agency
        LocationCategory.FITNESS_CENTRE -> R.string.poi_category_fitness_center
    }