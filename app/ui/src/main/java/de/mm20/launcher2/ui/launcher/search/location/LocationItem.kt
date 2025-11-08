package de.mm20.launcher2.ui.launcher.search.location

import android.content.Context
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.times
import androidx.compose.ui.util.fastFilterNotNull
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import blend.Blend.harmonize
import coil.compose.AsyncImage
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.isOpen
import de.mm20.launcher2.search.location.Attribution
import de.mm20.launcher2.search.location.Departure
import de.mm20.launcher2.search.location.LineNameComparator
import de.mm20.launcher2.search.location.LineType
import de.mm20.launcher2.search.location.OpeningHours
import de.mm20.launcher2.search.location.OpeningSchedule
import de.mm20.launcher2.search.location.PaymentMethod
import de.mm20.launcher2.search.location.isNotEmpty
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalTime
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.MarqueeText
import de.mm20.launcher2.ui.component.RatingBar
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.ktx.atTone
import de.mm20.launcher2.ui.ktx.blendIntoViewScale
import de.mm20.launcher2.ui.ktx.metersToLocalizedString
import de.mm20.launcher2.ui.ktx.toComposeColor
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.modifier.scale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.pow
import java.time.format.TextStyle as JavaTextStyle

@Composable
fun LocationItem(
    modifier: Modifier = Modifier,
    location: Location,
    showDetails: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${location.key}")

    val userLocation by remember {
        viewModel.devicePoseProvider.getLocation()
    }.collectAsStateWithLifecycle(null)

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
        viewModel.devicePoseProvider.getAzimuthDegrees()
    }.collectAsStateWithLifecycle(null)

    val icon by viewModel.icon.collectAsStateWithLifecycle()
    val badge by viewModel.badge.collectAsStateWithLifecycle(null)

    val imperialUnits by viewModel.imperialUnits.collectAsState()

    val showMap by viewModel.showMap.collectAsState()

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
                        verticalArrangement = Arrangement.Center,
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
                        val formattedDistance =
                            distance?.metersToLocalizedString(context, imperialUnits)
                        val sublabel = listOf(location.category, formattedDistance)
                            .fastFilterNotNull()
                            .joinToString(" • ")
                        val isOpenString = location.openingSchedule?.isOpen()
                            ?.let { stringResource(if (it) R.string.location_open else R.string.location_closed) }

                        Row(modifier = Modifier.padding(top = 2.dp)) {
                            if (sublabel.isNotBlank()) {
                                Text(
                                    sublabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .sharedElement(
                                            rememberSharedContentState("sublabel"),
                                            this@AnimatedContent
                                        )
                                )
                            }
                            if (!isOpenString.isNullOrBlank()) {
                                Text(
                                    " • $isOpenString",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.animateEnterExit()
                                )
                            }
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
                        verticalAlignment = Alignment.Top,
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                val category = location.category
                                val acceptedPaymentMethods = location.acceptedPaymentMethods
                                val formattedDistance = distance?.metersToLocalizedString(
                                    context, imperialUnits
                                )
                                if (category != null || formattedDistance != null) {
                                    Text(
                                        when {
                                            category != null && formattedDistance != null -> "$category • $formattedDistance"

                                            category != null -> category
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
                                if (!acceptedPaymentMethods.isNullOrEmpty()) {
                                    Text(
                                        " • ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.animateEnterExit()
                                    )
                                    for ((method, available) in acceptedPaymentMethods) {
                                        Icon(
                                            painterResource(
                                                when (method) {
                                                    PaymentMethod.Cash -> if (available) R.drawable.toll_20px else R.drawable.toll_off_20px
                                                    PaymentMethod.Card -> if (available) R.drawable.credit_card_20px else R.drawable.credit_card_off_20px
                                                }
                                            ),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier
                                                .size(14.5.dp)
                                                .padding(end = 2.dp)
                                                .animateEnterExit()
                                        )
                                    }
                                }
                            }
                            if (location.userRating != null) {
                                RatingBar(
                                    location.userRating!!,
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .offset((-2).dp)
                                )
                            }

                            if (!showMap) {
                                val attribution = location.attribution
                                if (attribution != null) {
                                    Attribution(
                                        attribution,
                                        reverse = true,
                                        modifier = Modifier
                                            .padding(
                                                top = 16.dp,
                                                bottom = 0.dp,
                                            )
                                            .clickable(
                                                enabled = attribution.url != null
                                            ) {
                                                context.tryStartActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse(attribution.url)
                                                    )
                                                )
                                            }
                                    )
                                }
                            }
                        }

                        if (!showMap) {
                            Compass(
                                targetHeading = targetHeading,
                                modifier = Modifier
                                    .sharedBounds(
                                        rememberSharedContentState("compass"),
                                        this@AnimatedContent
                                    ),
                                size = 56.dp,
                            )
                        } else {
                            val attribution = location.attribution
                            if (attribution != null) {
                                Attribution(
                                    attribution,
                                    modifier = Modifier
                                        .padding(
                                            top = 4.dp,
                                            bottom = 4.dp,
                                            start = 12.dp
                                        )
                                        .clickable(
                                            enabled = attribution.url != null
                                        ) {
                                            context.tryStartActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    attribution.url!!.toUri()
                                                )
                                            )
                                        }
                                )
                            }
                        }
                    }

                    val openingSchedule = location.openingSchedule
                    val departures = remember(location.departures) {
                        location.departures?.sortedBy { it.time }
                    }
                    if (departures != null) {
                        Departures(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                            departures = departures,
                        )
                    }
                    if (openingSchedule?.isNotEmpty() == true) {
                        OpeningSchedule(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                            openingSchedule = openingSchedule,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 12.dp, top = 8.dp)
                    ) {
                        val navigationIntent = Intent(
                            Intent.ACTION_VIEW,
                            "google.navigation:q=${location.latitude},${location.longitude}".toUri()
                        )
                        val canResolveNavigationIntent = remember {
                            null != context.packageManager.resolveActivity(navigationIntent, 0)
                        }
                        if (canResolveNavigationIntent) {
                            AssistChip(
                                modifier = Modifier.padding(end = 12.dp),
                                onClick = { context.tryStartActivity(navigationIntent) },
                                label = { Text(stringResource(R.string.menu_navigation)) },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.directions_20px), null,
                                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                                    )
                                }
                            )
                        }
                        if (location.phoneNumber != null) {
                            AssistChip(
                                modifier = Modifier.padding(end = 12.dp),
                                onClick = {
                                    context.tryStartActivity(
                                        Intent(
                                            Intent.ACTION_DIAL,
                                            "tel:${location.phoneNumber}".toUri()
                                        )
                                    )
                                },
                                label = { Text(stringResource(R.string.menu_dial)) },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.call_20px), null,
                                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                                    )
                                }
                            )
                        }

                        if (location.websiteUrl != null) {
                            AssistChip(
                                modifier = Modifier.padding(end = 12.dp),
                                onClick = {
                                    context.tryStartActivity(
                                        Intent(
                                            Intent.ACTION_VIEW, location.websiteUrl!!.toUri()
                                        )
                                    )
                                },
                                label = { Text(stringResource(R.string.menu_website)) },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.language_20px), null,
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
                                icon = R.drawable.star_24px_filled,
                                action = {
                                    viewModel.unpin()
                                }
                            )
                        } else {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_pin),
                                icon = R.drawable.star_24px,
                                action = {
                                    viewModel.pin()
                                })
                        }
                        toolbarActions.add(favAction)
                    }

                    toolbarActions += DefaultToolbarAction(
                        label = stringResource(id = R.string.menu_map),
                        icon = R.drawable.open_in_new_24px,
                    ) {
                        viewModel.launch(context)
                    }

                    val sheetManager = LocalBottomSheetManager.current

                    toolbarActions.add(
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_customize),
                            icon = R.drawable.tune_24px,
                            action = { sheetManager.showCustomizeSearchableModal(location) }
                        ))

                    if (location.fixMeUrl != null) {
                        toolbarActions += DefaultToolbarAction(
                            label = stringResource(id = R.string.menu_bugreport),
                            icon = R.drawable.bug_report_24px,
                        ) {
                            context.tryStartActivity(
                                Intent(
                                    Intent.ACTION_VIEW, location.fixMeUrl!!.toUri()
                                )
                            )
                        }
                    }

                    Toolbar(
                        modifier = Modifier.fillMaxWidth(),
                        leftActions = listOf(
                            DefaultToolbarAction(
                                label = stringResource(id = R.string.menu_back),
                                icon = R.drawable.arrow_back_24px,
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
private fun Departures(
    modifier: Modifier = Modifier,
    departures: List<Departure>,
) {
    val context = LocalContext.current

    val nextDeparture = key(LocalTime.current) {
        departures.firstOrNull {
            it.time.plus(it.delay ?: Duration.ZERO).isAfter(ZonedDateTime.now())
        }
    }
    var animateFilterChipsOnce by remember { mutableStateOf(true) }
    if (nextDeparture != null) {
        var showDepartureList by remember { mutableStateOf(false) }
        OutlinedCard(
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            onClick = { showDepartureList = true }
        ) {
            val listState = rememberLazyListState()

            AnimatedContent(showDepartureList) { showList ->
                if (!showList) {
                    Row(
                        Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LineIcon(
                            departure = nextDeparture,
                            Modifier.padding(end = 8.dp)
                        )
                        val lastStop = nextDeparture.lastStop
                        if (lastStop != null) {
                            MarqueeText(
                                modifier = Modifier.weight(1f),
                                text = lastStop,
                                style = MaterialTheme.typography.labelMedium,
                                iterations = Int.MAX_VALUE,
                                repeatDelayMillis = 0,
                                velocity = 20.dp,
                                fadeLeft = 5.dp,
                                fadeRight = 5.dp,
                            )
                        }

                        Text(
                            text = key(LocalTime.current) {
                                departureInMinutes(context, nextDeparture)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Icon(painterResource(R.drawable.chevron_forward_24px), null)
                    }
                } else {
                    val (lines, groupedDepartures) = remember(departures) {
                        val dict = departures.groupBy { it.line to it.type }
                        dict.keys.toList().sortedWith { (line1, type1), (line2, type2) ->
                            if (type1 != type2) compareValues(type1?.ordinal, type2?.ordinal)
                            else LineNameComparator.compare(line1, line2)
                        } to dict
                    }

                    var showMinutes by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { showMinutes = !showMinutes },
                                onLongClick = { showDepartureList = false },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            )
                            .padding(bottom = 8.dp)
                    ) {

                        Column {
                            val filterChipListState =
                                rememberLazyListState()
                            var selectedLine by remember {
                                mutableStateOf(
                                    nextDeparture.line to nextDeparture.type
                                )
                            }
                            LaunchedEffect(Unit) {
                                val itemIdx = lines.indexOf(selectedLine)
                                if (itemIdx != -1) {
                                    if (animateFilterChipsOnce) {
                                        delay(500)
                                        filterChipListState.animateScrollToItem(
                                            itemIdx
                                        )
                                        animateFilterChipsOnce = false
                                    } else
                                        filterChipListState.scrollToItem(
                                            itemIdx
                                        )
                                }
                            }
                            LazyRow(
                                state = filterChipListState,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                itemsIndexed(
                                    lines,
                                    key = { idx, _ -> idx }
                                ) { idx, it ->
                                    val (lineName, _) = it
                                    val firstDeparture =
                                        groupedDepartures[it]?.first()
                                    if (firstDeparture != null) {
                                        LineFilterChip(
                                            lineName = lineName,
                                            lineColor = firstDeparture.lineColor?.toComposeColor(),
                                            lineType = firstDeparture.type,
                                            selected = selectedLine == it,
                                            onClick = {
                                                selectedLine = it
                                            },
                                            modifier = Modifier
                                                .padding(
                                                    top = 12.dp,
                                                    bottom = 12.dp,
                                                    start = 4.dp,
                                                    end = 4.dp,
                                                )
                                                .graphicsLayer {
                                                    alpha =
                                                        filterChipListState.layoutInfo
                                                            .blendIntoViewScale(
                                                                idx,
                                                                0.5f
                                                            )
                                                }
                                        )
                                    }
                                }
                            }
                            AnimatedContent(
                                selectedLine,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(220, delayMillis = 90))
                                        .togetherWith(fadeOut(animationSpec = tween(90)))
                                }
                            ) { line ->

                                val selectedDepartures = groupedDepartures[line]

                                if (selectedDepartures != null) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    ) {
                                        for (i in 0..<selectedDepartures.size.coerceAtMost(8)) {
                                            if (i > 0) {
                                                HorizontalDivider()
                                            }
                                            val dep = selectedDepartures[i]
                                            DepartureRow(
                                                departure = dep,
                                                lineWidth = remember(
                                                    selectedDepartures
                                                ) {
                                                    selectedDepartures.maxOfOrNull { it.line.length }
                                                },
                                                withIcon = false,
                                                minutesInsteadOfTime = showMinutes,
                                                modifier = Modifier.fillMaxWidth(),
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
    }
}

@Composable
private fun OpeningSchedule(
    modifier: Modifier = Modifier,
    openingSchedule: OpeningSchedule,
) {
    val context = LocalContext.current

    var showOpeningSchedule by remember(openingSchedule) { mutableStateOf(false) }
    OutlinedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        onClick = {
            if (openingSchedule !is OpeningSchedule.TwentyFourSeven) {
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
                    when (openingSchedule) {
                        is OpeningSchedule.TwentyFourSeven -> {
                            Text(
                                text = stringResource(R.string.location_open_24_7),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }

                        is OpeningSchedule.Hours -> {
                            val text = remember(openingSchedule) {
                                val currentOpeningTime =
                                    openingSchedule.getCurrentOpeningHours()
                                val timeFormat =
                                    DateTimeFormatter.ofLocalizedTime(
                                        FormatStyle.SHORT
                                    )
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
                                                JavaTextStyle.SHORT,
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
                                                JavaTextStyle.SHORT,
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

                            Icon(painterResource(R.drawable.chevron_forward_24px), null)
                        }
                    }
                }
            } else if (openingSchedule is OpeningSchedule.Hours) {
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
                                    JavaTextStyle.FULL,
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

@Composable
private fun Compass(
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
                painterResource(R.drawable.assistant_navigation_24px),
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
    origin: IntRect,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        show,
        enter = expandIn(
            animationSpec = tween(300),
            expandFrom = Alignment.TopEnd,
        ) { origin.size },
        exit = shrinkOut(
            animationSpec = tween(300),
            shrinkTowards = Alignment.TopEnd,
        ) { origin.size },
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

private fun OpeningSchedule.Hours.getCurrentOpeningHours(): OpeningHours? {
    return openingHours.find { it.isOpen() }
}

private fun OpeningSchedule.Hours.getNextOpeningHours(): OpeningHours {
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

@Composable
fun LineMarqueeText(
    lineName: String,
    lineForeground: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) = MarqueeText(
    text = lineName,
    style = style,
    color = lineForeground,
    textAlign = TextAlign.Center,
    fadeLeft = 2.5.dp,
    fadeRight = 2.5.dp,
    iterations = Int.MAX_VALUE,
    repeatDelayMillis = 0,
    spacing = MarqueeSpacing(10.dp),
    velocity = 20.dp,
    modifier = modifier
)

@Composable
fun LineTypeIcon(
    lineType: LineType?,
    tint: Color,
    modifier: Modifier = Modifier
) = Icon(
    painter = painterResource(
        when (lineType) {
            LineType.Bus -> R.drawable.directions_bus_20px
            LineType.Tram -> R.drawable.tram_20px
            LineType.Subway -> R.drawable.subway_20px
            LineType.Monorail -> R.drawable.monorail_20px
            LineType.CommuterTrain -> R.drawable.directions_railway_20px
            LineType.Train, LineType.RegionalTrain, LineType.HighSpeedTrain -> R.drawable.train_20px
            LineType.Boat -> R.drawable.directions_boat_20px
            LineType.CableCar -> R.drawable.cable_car_20px
            LineType.AerialTramway -> R.drawable.gondola_lift_20px
            LineType.Airplane -> R.drawable.flight_20px
            null -> R.drawable.commute_20px
        }
    ),
    contentDescription = lineType?.name, // TODO localize (maybe) with ?.let{ stringResource("departure_line_type_$it") }
    modifier = modifier,
    tint = tint
)

@Composable
fun LineFilterChip(
    lineName: String,
    lineColor: Color?,
    lineType: LineType?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = 0.875f

    val dark = LocalDarkTheme.current
    val color =
        if (lineColor == null) MaterialTheme.colorScheme.primary
        else Color(harmonize(lineColor.toArgb(), MaterialTheme.colorScheme.primary.toArgb()))

    InputChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(lineName, style = MaterialTheme.typography.labelMedium)
        },
        avatar = {
            Box(
                modifier = Modifier
                    .background(color.atTone(if (dark) 80 else 40))
                    .clip(CircleShape)
                    .requiredSize(
                        InputChipDefaults.AvatarSize * scale
                    )
            ) {
                LineTypeIcon(
                    lineType = lineType,
                    tint = color.atTone(if (dark) 20 else 100),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedLabelColor = color.atTone(if (dark) 90 else 30),
            selectedContainerColor = color.atTone(if (dark) 30 else 90),
        ),
        modifier = modifier.height(FilterChipDefaults.Height * scale)
    )
}

@Composable
fun LineIcon(
    lineName: String,
    lineType: LineType?,
    lineColor: android.graphics.Color?,
    hasDeparted: Boolean,
    modifier: Modifier = Modifier
) {
    val dark = LocalDarkTheme.current
    val color =
        if (lineColor == null) MaterialTheme.colorScheme.primary
        else Color(harmonize(lineColor.toArgb(), MaterialTheme.colorScheme.primary.toArgb()))

    Row(
        modifier = modifier
            .wrapContentWidth(Alignment.Start)
            .background(
                color.atTone(if (dark) 80 else 40),
                MaterialTheme.shapes.small
            )
            .padding(top = 4.dp, bottom = 4.dp, start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val foregroundColor = color.atTone(if (dark) 20 else 100)
        LineTypeIcon(
            lineType,
            foregroundColor,
            Modifier
                .padding(end = 2.dp)
                .size(16.dp)
        )
        LineMarqueeText(
            lineName,
            foregroundColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .wrapContentSize()
                .widthIn(max = 34.dp)
        )
    }
}

@Composable
fun LineIcon(
    departure: Departure,
    modifier: Modifier = Modifier,
) = LineIcon(
    lineName = departure.line,
    lineType = departure.type,
    lineColor = departure.lineColor,
    hasDeparted = ZonedDateTime.now().isAfter(departure.time + (departure.delay ?: Duration.ZERO)),
    modifier = modifier
)


@Composable
fun DepartureRow(
    departure: Departure,
    lineWidth: Int?,
    withIcon: Boolean,
    minutesInsteadOfTime: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            // HACK: the extents of this row should be properly calculated.
            // but how?
            modifier = Modifier.fillMaxWidth(0.66f)
        ) {
            if (withIcon) {
                LineIcon(
                    departure = departure,
                    Modifier
                        .padding(end = 8.dp)
                        .widthIn(
                            min = if (lineWidth == null) 0.dp
                            else max(64.dp, lineWidth * 8.dp)
                        )
                )
            }
            if (departure.lastStop != null) {
                MarqueeText(
                    text = departure.lastStop!!,
                    style = MaterialTheme.typography.labelMedium,
                    iterations = Int.MAX_VALUE,
                    repeatDelayMillis = 0,
                    velocity = 20.dp,
                    fadeLeft = 5.dp,
                    fadeRight = 5.dp,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (minutesInsteadOfTime) {
                    key(LocalTime.current) { departureInMinutes(context, departure) }
                } else {
                    departure.time.format(
                        DateTimeFormatter.ofPattern(
                            "HH:mm",
                            Locale.getDefault()
                        )
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = 2.dp)
            )
            if (!minutesInsteadOfTime) {
                val delayMinutes = departure.delay?.toMinutes()
                if (null != delayMinutes && 0L < delayMinutes) {
                    Text(
                        text = "+$delayMinutes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = TextUnit(2f, TextUnitType.Em),
                    )
                }
            }
        }
    }
}

@Composable
private fun Attribution(
    attribution: Attribution,
    modifier: Modifier = Modifier,
    reverse: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (attribution.text != null && !reverse) {
            Text(
                text = attribution.text!!,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        if (attribution.iconUrl != null) {
            AsyncImage(
                modifier = Modifier
                    .padding(
                        start = if (reverse) 0.dp else 8.dp,
                        end = if (reverse) 8.dp else 0.dp
                    )
                    .requiredHeight(16.dp),
                model = attribution.iconUrl!!,
                contentDescription = null,
            )
        }
        if (attribution.text != null && reverse) {
            Text(
                text = attribution.text!!,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun departureInMinutes(context: Context, departure: Departure): String {
    val delayedDepartureTime =
        departure.time + (departure.delay
            ?: Duration.ZERO)
    val now = ZonedDateTime.now()

    if (delayedDepartureTime < now)
        return context.getString(R.string.departure_time_departed)

    val timeLeft =
        Duration.between(now, delayedDepartureTime)
            .toMinutes().toInt()
    return if (timeLeft < 1) {
        context.getString(R.string.departure_time_now)
    } else {
        context.resources.getQuantityString(
            R.plurals.departure_time_in,
            timeLeft,
            timeLeft
        )
    }
}