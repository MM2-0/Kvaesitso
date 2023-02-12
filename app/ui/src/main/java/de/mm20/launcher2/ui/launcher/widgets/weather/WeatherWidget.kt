package de.mm20.launcher2.ui.launcher.widgets.weather

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.North
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
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
import de.mm20.launcher2.ui.icons.HumidityPercentage
import de.mm20.launcher2.ui.icons.Rain
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

    val compactMode by viewModel.compactMode.observeAsState(false)

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
        CurrentWeather(forecast, imperialUnits)

        if (!compactMode) {

            val dailyForecasts by viewModel.dailyForecasts.observeAsState(emptyList())
            val selectedDayForecast by viewModel.currentDailyForecast.observeAsState()
            val currentDayForecasts by viewModel.currentDayForecasts.observeAsState(emptyList())

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    WeatherTimeSelector(
                        forecasts = currentDayForecasts,
                        selectedForecast = forecast,
                        imperialUnits = imperialUnits,
                        onTimeSelected = {
                            viewModel.selectForecast(it)
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    selectedDayForecast?.let {
                        WeatherDaySelector(
                            days = dailyForecasts,
                            selectedDay = it,
                            onDaySelected = {
                                viewModel.selectDay(it)
                            },
                            imperialUnits = imperialUnits,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentWeather(forecast: Forecast, imperialUnits: Boolean) {
    val context = LocalContext.current
    Column {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationCity,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    text = forecast.location,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Surface(
                shape = MaterialTheme.shapes.extraSmall.copy(
                    topStart = CornerSize(0),
                    topEnd = CornerSize(0),
                    bottomEnd = CornerSize(0)
                ),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "${forecast.provider} (${
                        formatTime(
                            LocalContext.current,
                            forecast.updateTime
                        )
                    })",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp),
                    modifier = Modifier
                        .clickable(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse(forecast.providerUrl)
                                    ?: return@clickable
                            }
                            context.tryStartActivity(intent)
                        })
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
                )
            }
        }
        Row(
            modifier = Modifier.padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = convertTemperature(
                    imperialUnits = imperialUnits,
                    temp = forecast.temperature
                ).toString() + "°" + if (imperialUnits) "F" else "C",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = forecast.condition,
                style = MaterialTheme.typography.bodySmall
            )
            AnimatedWeatherIcon(
                modifier = Modifier.padding(
                    start = 8.dp,
                    end = 8.dp
                ),
                icon = weatherIconById(forecast.icon),
                night = forecast.night
            )
        }
    }
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.HumidityPercentage,
                modifier = Modifier.size(20.dp),
                contentDescription = null
            )
            Spacer(modifier = Modifier.padding(3.dp))
            Text(
                text = "${forecast.humidity.roundToInt()} %",
                style = MaterialTheme.typography.labelMedium
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
                    .size(20.dp),
                contentDescription = null
            )
            Spacer(modifier = Modifier.padding(3.dp))
            Text(
                text = formatWindSpeed(imperialUnits, forecast),
                style = MaterialTheme.typography.labelMedium
            )
        }
        val precipitation = formatPrecipitation(imperialUnits, forecast)
        if (precipitation != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Rain,
                    modifier = Modifier.size(20.dp),
                    contentDescription = null
                )
                Spacer(modifier = Modifier.padding(3.dp))
                Text(
                    text = precipitation,
                    style = MaterialTheme.typography.labelMedium
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
    val dateFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(forecasts) { idx, fc ->
            val alpha = if (fc == selectedForecast) 0.12f else 0.0f
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha)
            ) {
                Column(
                    modifier = Modifier
                        .clickable { onTimeSelected(idx) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    WeatherIcon(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        icon = weatherIconById(fc.icon),
                        night = fc.night
                    )
                    Text(
                        text = dateFormat.format(fc.timestamp),
                        style = MaterialTheme.typography.labelMedium,
                        softWrap = false,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text(
                        text = "${convertTemperature(imperialUnits, fc.temperature)}°",
                        softWrap = false,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
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
    val dateFormat = SimpleDateFormat("EEE")

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
    ) {
        itemsIndexed(days) { idx, day ->
            val alpha = if (day == selectedDay) 0.12f else 0.0f

            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha)
            ) {
                Row(
                    modifier = Modifier
                        .clickable { onDaySelected(idx) }
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WeatherIcon(icon = weatherIconById(day.icon))
                    Text(
                        text = dateFormat.format(day.timestamp),
                        style = MaterialTheme.typography.labelMedium,
                        softWrap = false,
                        modifier = Modifier.padding(start = 12.dp, end = 6.dp)
                    )
                    Text(
                        text = "${
                            convertTemperature(
                                imperialUnits,
                                day.minTemp
                            )
                        }° / ${convertTemperature(imperialUnits, day.maxTemp)}°",
                        softWrap = false,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
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