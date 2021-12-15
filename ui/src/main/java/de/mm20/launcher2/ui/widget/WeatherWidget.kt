package de.mm20.launcher2.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.weather.AnimatedWeatherIcon
import de.mm20.launcher2.ui.weather.WeatherIcon
import de.mm20.launcher2.weather.DailyForecast
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.WeatherViewModel
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WeatherWidget() {
    val viewModel: WeatherViewModel = viewModel()
    val weatherData by viewModel.forecasts.observeAsState(initial = emptyList())
    var selectedDayIndex by remember { mutableStateOf(0) }
    var selectedForecastIndex by remember { mutableStateOf(0) }
    var detailsExpanded by remember { mutableStateOf(false) }


    if (weatherData.isNotEmpty() && weatherData.size <= selectedDayIndex) {
        selectedDayIndex = 0
        return
    }

    if (weatherData.isNotEmpty() && weatherData[selectedDayIndex].hourlyForecasts.size <= selectedForecastIndex) {
        selectedForecastIndex = 0
        return
    }

    if (weatherData.isEmpty()) {
        NoData()
        return
    }

    val selectedForecast = weatherData[selectedDayIndex].hourlyForecasts[selectedForecastIndex]
    val imperialUnits = LauncherPreferences.instance.imperialUnits

    Column {
        Row {
            Column(
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = selectedForecast.location,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = convertTemperature(
                        imperialUnits = imperialUnits,
                        temp = selectedForecast.temperature
                    ).toString() + "°",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = selectedForecast.condition,
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    modifier = Modifier
                        .clickable(onClick = {
                            detailsExpanded = !detailsExpanded
                        })
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(id = if (detailsExpanded) R.string.weather_widget_hide_details else R.string.weather_widget_show_details),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = detailsExpanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        WeatherDetailRow(
                            title = stringResource(id = R.string.weather_humidity),
                            value = "${selectedForecast.humidity.roundToInt()} %"
                        )
                        WeatherDetailRow(
                            title = stringResource(id = R.string.weather_wind),
                            value = formatWindSpeed(imperialUnits, selectedForecast)
                        )
                        val precipitation = formatPrecipitation(imperialUnits, selectedForecast)
                        if (precipitation != null) {
                            WeatherDetailRow(
                                title = stringResource(id = R.string.weather_precipitation),
                                value = precipitation
                            )
                        }
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                val context = LocalContext.current
                AnimatedWeatherIcon(
                    modifier = Modifier.padding(all = 16.dp),
                    icon = weatherIconById(selectedForecast.icon),
                    night = selectedForecast.night
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    modifier = Modifier.align(Alignment.End)

                ) {

                    Text(
                        text = "${selectedForecast.provider} (${
                            formatTime(
                                LocalContext.current,
                                selectedForecast.updateTime
                            )
                        })",
                        style = TextStyle(
                            fontSize = 10.sp
                        ),
                        modifier = Modifier
                            .clickable(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse(selectedForecast.providerUrl)
                                        ?: return@clickable
                                }
                                context.tryStartActivity(intent)
                            })
                            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 16.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherDaySelector(
                days = weatherData,
                selectedDay = weatherData[selectedDayIndex],
                onDaySelected = {
                    selectedDayIndex = it
                    selectedForecastIndex = 0
                },
                modifier = Modifier.weight(1f)
            )
            WeatherTimeSelector(
                forecasts = weatherData[selectedDayIndex].hourlyForecasts,
                selectedForecast = selectedForecast,
                onTimeSelected = {
                    selectedForecastIndex = it
                }
            )
        }
    }
}

@Composable
fun WeatherDetailRow(title: String, value: String) {
    Row {
        Text(
            text = title,
            modifier = Modifier.padding(end = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun WeatherDaySelector(
    modifier: Modifier = Modifier,
    days: List<DailyForecast>,
    selectedDay: DailyForecast,
    onDaySelected: (Int) -> Unit
) {
    val menuExpanded = remember { mutableStateOf(false) }
    val imperialUnits = LauncherPreferences.instance.imperialUnits
    val dateFormat = SimpleDateFormat("EEE")
    Row(modifier = modifier) {
        Row(
            modifier = Modifier
                .clickable(onClick = {
                    menuExpanded.value = true
                })
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
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
                    }
                ) {
                    Row {
                        WeatherIcon(icon = weatherIconById(d.icon))
                        Text(
                            text = dateFormat.format(d.timestamp),
                            style = MaterialTheme.typography.titleSmall,
                            softWrap = false,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
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
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherTimeSelector(
    modifier: Modifier = Modifier,
    forecasts: List<Forecast>,
    selectedForecast: Forecast,
    onTimeSelected: (Int) -> Unit
) {
    val menuExpanded = remember { mutableStateOf(false) }

    val dateFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val imperialUnits = LauncherPreferences.instance.imperialUnits

    Row(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = {
                    menuExpanded.value = true
                })
                .padding(16.dp)
        ) {
            Text(
                text = dateFormat.format(selectedForecast.timestamp),
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

            for ((i, fc) in forecasts.withIndex()) {
                DropdownMenuItem(onClick = {
                    menuExpanded.value = false
                    onTimeSelected(i)
                }) {
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
    return "$speedValue $speedUnit • ${windDirectionAsWord(forecast.windDirection)}"
}

@Composable
private fun formatPrecipitation(imperialUnits: Boolean, forecast: Forecast): String? {
    val formatter = if (imperialUnits) DecimalFormat("#.##") else DecimalFormat("#.#")
    val precipUnit =
        if (imperialUnits) stringResource(id = R.string.unit_inch_symbol) else stringResource(id = R.string.unit_millimeter_symbol)
    if (forecast.precipProbability >= 0 && forecast.precipitation >= 0.0) {
        return "${formatter.format(forecast.precipitation)} $precipUnit • ${forecast.precipProbability} %"
    }
    if (forecast.precipProbability >= 0) {
        return "${forecast.precipProbability} %"
    }
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
        Icon(imageVector = Icons.Rounded.LightMode,
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