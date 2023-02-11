package de.mm20.launcher2.ui.launcher.widgets.weather

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.North
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.WeatherLocationSearchDialog
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.weather.AnimatedWeatherIcon
import de.mm20.launcher2.ui.component.weather.WeatherIcon
import de.mm20.launcher2.ui.modifier.verticalFadingEdges
import de.mm20.launcher2.weather.DailyForecast
import de.mm20.launcher2.weather.Forecast
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import kotlin.math.roundToInt

@Composable
fun WeatherWidget() {
    val viewModel: WeatherWidgetWM = viewModel()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(null) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.selectNow()
        }
    }

    val selectedForecast by viewModel.currentForecast.observeAsState()

    val imperialUnits by viewModel.imperialUnits.observeAsState(false)

    var showLocationDialog by remember { mutableStateOf(false) }

    if (showLocationDialog) {
        WeatherLocationSearchDialog(onDismissRequest = { showLocationDialog = false })
    }

    val forecast = selectedForecast ?: run {
        val hasPermission by viewModel.hasLocationPermission.observeAsState()
        val autoLocation by viewModel.autoLocation.observeAsState()
        Column {
            AnimatedVisibility(hasPermission == false && autoLocation == true) {
                MissingPermissionBanner(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                    text = stringResource(id = R.string.missing_permission_auto_location),
                    onClick = {
                        viewModel.requestLocationPermission(context as AppCompatActivity)
                    },
                    secondaryAction = {
                        OutlinedButton(onClick = {
                            showLocationDialog = true
                        }) {
                            Text(
                                stringResource(R.string.weather_widget_set_location),
                            )
                        }
                    }
                )
            }
            NoData()
        }
        return
    }

    Column {
        Surface(
            shape = MaterialTheme.shapes.extraSmall.copy(
                bottomEnd = CornerSize(0),
                topEnd = CornerSize(0)
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = "${forecast.provider} (${
                    formatTime(
                        LocalContext.current,
                        forecast.updateTime
                    )
                })",
                style = TextStyle(
                    fontSize = 10.sp
                ),
                modifier = Modifier
                    .clickable(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(forecast.providerUrl)
                                ?: return@clickable
                        }
                        context.tryStartActivity(intent)
                    })
                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 16.dp)
            )
        }

        CurrentWeather(forecast, imperialUnits)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dailyForecasts by viewModel.dailyForecasts.observeAsState(emptyList())
            val selectedDayForecast by viewModel.currentDailyForecast.observeAsState()
            selectedDayForecast?.let {
                WeatherDaySelector(
                    days = dailyForecasts,
                    selectedDay = it,
                    onDaySelected = {
                        viewModel.selectDay(it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    imperialUnits = imperialUnits
                )
            }
            val currentDayForecasts by viewModel.currentDayForecasts.observeAsState(emptyList())
            WeatherTimeSelector(
                forecasts = currentDayForecasts,
                selectedForecast = forecast,
                imperialUnits = imperialUnits,
                onTimeSelected = {
                    viewModel.selectForecast(it)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
fun CurrentWeather(forecast: Forecast, imperialUnits: Boolean) {
    Row {
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationCity,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.padding(6.dp))
                Text(
                    text = forecast.location,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Text(
                modifier = Modifier.padding(vertical = 8.dp),
                text = convertTemperature(
                    imperialUnits = imperialUnits,
                    temp = forecast.temperature
                ).toString() + "°" + if (imperialUnits) "F" else "C",
                style = MaterialTheme.typography.headlineLarge,
            )
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = forecast.condition,
                    style = MaterialTheme.typography.bodyMedium
                )
                AnimatedWeatherIcon(
                    modifier = Modifier.padding(
                        top = 6.dp,
                        bottom = 6.dp,
                        start = 16.dp,
                        end = 12.dp
                    ),
                    icon = weatherIconById(forecast.icon),
                    night = forecast.night
                )
            }
        }
    }
    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.humidity_percentage_fill0_rounded),
                modifier = Modifier.size(24.dp),
                contentDescription = null
            )
            Spacer(modifier = Modifier.padding(3.dp))
            Text(
                text = "${forecast.humidity.roundToInt()} %",
                style = MaterialTheme.typography.titleSmall
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // windDirection is "fromDirection"; Wind (arrow) blows into opposite direction.
            val angle by animateFloatAsState(forecast.windDirection.toFloat() + 180f)
            Icon(
                imageVector = Icons.Rounded.North,
                modifier = Modifier
                    .rotate(angle)
                    .size(24.dp),
                contentDescription = null
            )
            Spacer(modifier = Modifier.padding(3.dp))
            Text(
                text = formatWindSpeed(imperialUnits, forecast),
                style = MaterialTheme.typography.titleSmall
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.rainy_fill0_rounded),
                modifier = Modifier.size(24.dp),
                contentDescription = null
            )
            Spacer(modifier = Modifier.padding(3.dp))
            Text(
                text = formatPrecipitation(imperialUnits, forecast) ?: "?",
                style = MaterialTheme.typography.titleSmall
            )
        }

    }
}

@Composable
fun WeatherDaySelector(
    modifier: Modifier = Modifier,
    days: List<DailyForecast>,
    selectedDay: DailyForecast,
    onDaySelected: (Int) -> Unit,
    imperialUnits: Boolean
) {
    val menuExpanded = remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("EEE")
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        TextButton(
            onClick = {
                menuExpanded.value = true
            },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            WeatherIcon(icon = weatherIconById(selectedDay.icon))
            Text(
                text = dateFormat.format(selectedDay.timestamp),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 16.dp, end = 8.dp)
            )
            Text(
                text = "${
                    convertTemperature(
                        imperialUnits,
                        selectedDay.minTemp
                    )
                }° / ${convertTemperature(imperialUnits, selectedDay.maxTemp)}°",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null
            )
        }
        DropdownMenu(
            expanded = menuExpanded.value,
            offset = DpOffset(0.dp, 0.dp),
            onDismissRequest = {
                menuExpanded.value = false
            }) {

            for ((i, d) in days.withIndex()) {
                DropdownMenuItem(
                    onClick = {
                        menuExpanded.value = false
                        onDaySelected(i)
                    },
                    text = {
                        Row {
                            WeatherIcon(icon = weatherIconById(d.icon))
                            Text(
                                text = dateFormat.format(d.timestamp),
                                style = MaterialTheme.typography.titleSmall,
                                softWrap = false,
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 8.dp)
                                    .weight(1f)
                            )
                            Text(
                                text = "${
                                    convertTemperature(
                                        imperialUnits,
                                        d.minTemp
                                    )
                                }° / ${convertTemperature(imperialUnits, d.maxTemp)}°",
                                softWrap = false,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun WeatherTimeSelector(
    modifier: Modifier = Modifier,
    forecasts: List<Forecast>,
    selectedForecast: Forecast,
    imperialUnits: Boolean,
    onTimeSelected: (Int) -> Unit
) {
    val menuExpanded = remember { mutableStateOf(false) }

    val dateFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd
    ) {
        TextButton(
            onClick = {
                menuExpanded.value = true
            },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Text(
                text = dateFormat.format(selectedForecast.timestamp),
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null
            )
        }
        DropdownMenu(
            expanded = menuExpanded.value,
            offset = DpOffset(0.dp, 0.dp),
            onDismissRequest = {
                menuExpanded.value = false
            }) {

            for ((i, fc) in forecasts.withIndex()) {
                DropdownMenuItem(
                    onClick = {
                        menuExpanded.value = false
                        onTimeSelected(i)
                    },
                    text = {
                        Row {
                            WeatherIcon(icon = weatherIconById(fc.icon), night = fc.night)
                            Text(
                                text = dateFormat.format(fc.timestamp),
                                style = MaterialTheme.typography.titleSmall,
                                softWrap = false,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(start = 16.dp, end = 8.dp)
                                    .weight(1f)
                            )
                            Text(
                                text = "${convertTemperature(imperialUnits, fc.temperature)}°",
                                softWrap = false,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                )
            }

        }
    }
}

private fun formatTime(context: Context, timestamp: Long): String {
    return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME)
}

private fun convertTemperature(imperialUnits: Boolean, temp: Double): Int {
    return (if (imperialUnits) temp * 9.0 / 5.0 - 459.67 else temp + -273.15).roundToInt()
}

@Composable
private fun formatWindSpeed(imperialUnits: Boolean, forecast: Forecast): String {
    val formatter = DecimalFormat("0.#")
    val speedValue = formatter.format(forecast.windSpeed * if (imperialUnits) 0.621371 else 1.0)
    val speedUnit =
        stringResource(id = if (imperialUnits) R.string.unit_mile_per_hour_symbol else R.string.unit_meter_per_second_symbol)
    return "$speedValue $speedUnit"
}

@Composable
private fun formatPrecipitation(imperialUnits: Boolean, forecast: Forecast): String? {
    val formatter = if (imperialUnits) DecimalFormat("#.##") else DecimalFormat("#.#")
    val precipUnit =
        if (imperialUnits) stringResource(id = R.string.unit_inch_symbol) else stringResource(id = R.string.unit_millimeter_symbol)
    if (forecast.precipitation >= 0.0) {
        return "${formatter.format(forecast.precipitation)} $precipUnit"
    }
    return null
}

@Composable
private fun windDirectionAsWord(direction: Double): String {
    return stringResource(
        when (direction) {
            in 11.25..33.75 -> R.string.wind_north_north_east
            in 33.75..56.25 -> R.string.wind_north_east
            in 56.25..78.75 -> R.string.wind_east_north_east
            in 78.75..101.25 -> R.string.wind_east
            in 101.25..123.75 -> R.string.wind_east_south_east
            in 123.75..146.25 -> R.string.wind_south_east
            in 146.25..168.75 -> R.string.wind_south_south_east
            in 168.75..191.25 -> R.string.wind_south
            in 191.25..213.75 -> R.string.wind_south_south_west
            in 213.75..236.25 -> R.string.wind_south_west
            in 236.25..258.75 -> R.string.wind_west_south_west
            in 258.75..281.25 -> R.string.wind_west
            in 281.25..303.75 -> R.string.wind_west_north_west
            in 303.75..326.25 -> R.string.wind_north_west
            in 326.25..348.75 -> R.string.wind_north_north_west
            else -> R.string.wind_north
        }
    )
}

private fun weatherIconById(id: Int): WeatherIcon {
    return when (id) {
        Forecast.CLEAR -> WeatherIcon.Clear
        Forecast.CLOUDY -> WeatherIcon.Cloudy
        Forecast.COLD -> WeatherIcon.Cold
        Forecast.DRIZZLE -> WeatherIcon.Drizzle
        Forecast.HAZE -> WeatherIcon.Haze
        Forecast.FOG -> WeatherIcon.Fog
        Forecast.HAIL -> WeatherIcon.Hail
        Forecast.HEAVY_THUNDERSTORM -> WeatherIcon.HeavyThunderstorm
        Forecast.HEAVY_THUNDERSTORM_WITH_RAIN -> WeatherIcon.HeavyThunderstormWithRain
        Forecast.HOT -> WeatherIcon.Hot
        Forecast.MOSTLY_CLOUDY -> WeatherIcon.MostlyCloudy
        Forecast.PARTLY_CLOUDY -> WeatherIcon.PartlyCloudy
        Forecast.SHOWERS -> WeatherIcon.Showers
        Forecast.SLEET -> WeatherIcon.Sleet
        Forecast.SNOW -> WeatherIcon.Snow
        Forecast.STORM -> WeatherIcon.Storm
        Forecast.THUNDERSTORM -> WeatherIcon.Thunderstorm
        Forecast.THUNDERSTORM_WITH_RAIN -> WeatherIcon.ThunderstormWithRain
        Forecast.WIND -> WeatherIcon.Wind
        Forecast.BROKEN_CLOUDS -> WeatherIcon.BrokenClouds
        else -> WeatherIcon.None
    }
}

@Composable
fun NoData() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.LightMode,
            contentDescription = "",
            modifier = Modifier
                .padding(24.dp)
                .size(32.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = stringResource(id = R.string.weather_no_data),
            style = MaterialTheme.typography.bodySmall
        )
    }
}