# Weather Provider

Weather provider plugins need to extend
the <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.weather/-weather-provider/index.html" target="_blank">`WeatherProvider`</a>
class:

```kt
class MyWeatherProviderPlugin() : WeatherProvider(
    WeatherPluginConfig()
)

```

In the super constructor call, pass
a <a href="/reference/core/shared/de.mm20.launcher2.plugin.config/-weather-plugin-config/index.html" target="_blank">`WeatherPluginConfig`</a>
object.

## Plugin config

In the plugin config, you can set the following properties:

- `minUpdateInterval`: Minimum time (in ms) that needs to pass before the provider can be queried
  again. The launcher respects this value as long as the user does not change the weather settings (
  provider or location).

- `managedLocation`: If true, the plugin will manage the location itself. This means that the user
  cannot change the location settings in the launcher.

## Location search

If your weather provider service provides an API to lookup locations, you should override

```kt
suspend fun findLocations(query: String, lang: String): List<WeatherLocation>
```

This method is called when a user has _Auto location_ disabled and they are trying to set a new
location.

The default implementation uses the Android Geocoder, but this API has the limitation that it relies
on Google Play Services so you should use your own implementation whenever feasable.

`findLocations` returns a list
of <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.weather/-weather-location/index.html" target="_blank">`WeatherLocation`</a>
s. Return an empty list if no location has been found.

### Location types

There are three types of locations:

- <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.weather/-weather-location/-lat-lon/index.html" target="_blank">`WeatherLocation.LatLon`</a>:
  use this if your weather service identifies locations by their geo coordinates.
- <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.weather/-weather-location/-id/index.html" target="_blank">`WeatherLocation.Id`</a>:
  use this if your weather service has an internal ID system to identify locations.
- <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.weather/-weather-location/-managed/index.html" target="_blank">`WeatherLocation.Managed`</a>:
  a special location that indicates that the plugin should determine the location itself.

## Fetch weather data

Implement both `getWeatherData` methods:

```kt
suspend fun getWeatherData(lat: Double, lon: Double, lang: String?): List<Forecast>?`
```

```kt
suspend fun getWeatherData(location: WeatherLocation, lang: String?): List<Forecast>?
```

The first method is called when the user has _Auto location_ enabled. `lat` and `lon` the last known
coordinates of the user.

The second method is called when the user has set their location to a fixed location. In most cases,
`location` will be a value that has been returned by `findLocations` before, but it's possible that
`location` is a `WeatherLocation.LatLon` if the user has changed the provider after setting a fixed
location. If you haven't overridden `findLocations`, this will always be a `WeatherLocation.LatLon`.
If `managedLocation` is set to `true`, this method is called with `WeatherLocation.Managed`.

Both methods return a list
of <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.weather/-forecast/index.html" target="_blank">`Forecast`</a>
s. If an error occurs, you can throw an exception or return `null`, in this case the launcher will
keep the old data and start another attempt at a later time.

> [!NOTE]
> You should at most return weather data for the next 7 days. Everything after will be dismissed by
> the launcher.


`Forecast` objects need at least a `timestamp` (unix time in millis), a `temperature`,
a `condition`, an `icon`, a `location` name, and a `provider` name.

- The `condition` should preferably be localized in the user's language, which is provided by
  the `lang` parameter.
- To construct
  a <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.weather/-temperature/index.html" target="_blank">`Temperature`</a>,
  you can use the `Double.C`, `Double.F`, or `Double.K` helper functions, depending on whether the
  numeric value returned by your weather service API is in degrees celsius, degrees fahrenheit, or
  kelvin:

  ```kt
  val temp = tempValueInCelcius.C
  val temp2 = tempValueInFahrenheit.F
  val temp3 = tempValueInKelvin.K
  ```

  Similar helper functions are available to construct

  - `Pressure` (`Double.hPa`, and `Double.mbar`), and
  - `WindSpeed` values (`Double.m_s`, `Double.km_h`, and `Double.mph`)

- `location` is the name of the location.
  - In fixed location mode, you should read this value from the `location` parameter, to ensure
    that the name in the weather widget matches the name that the user has set in preferences.
  - In auto location mode, if your weather service does not give you a location name, you can use
    the <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.weather/-weather-provider/get-location-name.html" target="_blank">`getLocationName`</a>
    method to reverse geocode the location name using Android's Geocoder API.

## Plugin state

<!--@include: ./common/_plugin_state.md-->

## Examples

- [OpenWeatherMap plugin](https://github.com/Kvaesitso/Plugin-OpenWeatherMap)
- [Breezy Weather plugin](https://github.com/Kvaesitso/Plugin-BreezyWeather)
- [HERE plugin](https://github.com/Kvaesitso/Plugin-HERE)
