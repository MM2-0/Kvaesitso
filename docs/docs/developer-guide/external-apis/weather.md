# Weather Services

1. Copy the `weather/src/main/res/values/config_example.xml` to `weather/src/main/res/values/config.xml`

#### OpenWeatherMap

OpenWeatherMap offers 1 000 000 free API calls per month. However forecasts are only available for
the next 5 days and only every 3 hours. Also note that each weather update uses two API calls (
current weather + forecast).

1. Register at [OpenWeatherMap](https://openweathermap.org/)
1. Navigate to your user profile > API keys
1. Create a new API key
1. Uncomment the resource `openweather_key` in config.xml and paste your key as value.

#### Meteorologisk institutt[^1]

The Norwegian Meteorological Institute offers a free weather API. It has a rate limit of 20
requests/s. You do not need an API key, however they require an
identification and contact data to be present in the User Agent header in each request. Read the exact rules in their
[Terms of Service](https://api.met.no/doc/TermsOfService).

1. Uncomment `metno_contact` in config.xml. Fill in your contact data (an email address or a website
   where your contact data can be found). This will be sent in the User Agent header, which will be
   composed like
   this:
   ```
    User-Agent: "{app package name}/signature:{app siganture hash} {@string/metno_contact}"
   ```

#### Bright Sky / Deutscher Wetterdienst[^1]

Bright Sky is an API that converts data published by the Deutscher Wetterdienst to an easier
to work with JSON format. The API is free to use and requires no additional configuration, however
it only provides weather data for locations in Germany.

---

[^1]:
    These weather providers do not provide any means of geocoding or location lookup. Instead, the
    Android Geocoder API is used to lookup locations (in fixed location mode) and location names (in
    auto location mode). While most devices ship a Geocoder as part of the Google Play service, some
    might not have a Geocoder installed. In these cases, this provider might not work properly (no
    support for fixed locations and lat/lon values will be displayed instead of location names).
