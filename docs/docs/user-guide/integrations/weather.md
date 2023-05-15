# Weather

The weather integration provides the weather data that is used by
the [weather widget](/docs/user-guide/widgets/weather-widget). It can be configured at Settings >
Integrations > Weather.

## Providers

The service that provides the weather data. Weather data might differ in quality depending on your
location so pick whatever is working best for you.

## Location

You can either use your current location automatically (you need to grant location permission for
this) or set a fixed location manually.

### Cannot find any locations!

Some weather providers rely on the Android Geocoding API to convert location names to coordinates
and vice verca. This API is usually backed by the Google Play Services and Google Maps. It might not
be available if you use a degoogled Android distribution. In this case, you can try one of the
providers that ship their own geocoders (OpenWeatherMap or HERE), or you can specify the coordinates
manually using the following format: `<lat> <lon> <display name>`. For example: `-90 0 South pole`
