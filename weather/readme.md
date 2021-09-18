# :weather

⚠️ Depends on non-free external services.

This module manages weather data.

## Configuration

This module requires additional configuration in order to work properly. You can skip this step but
then weather related features will not be available. You need only configure the providers you plan
to use.

1. Copy the `./src/main/res/values/config_example.xml` to `./src/main/res/values/config.xml`

### OpenWeatherMap

OpenWeatherMap offers 1 000 000 free API calls per month. However forecasts are only available for
the next 5 days and only every 3 hours. Also note that each weather update uses two API calls (
current weather + forecast).

1. Register at [OpenWeatherMap](https://openweathermap.org/)
2. Navigate to your user profile > API keys
3. Create a new API key
4. Uncomment the resource `openweather_key` in config.xml and paste your key as value.

### HERE

HERE offers 250 000 free API calls per month. Each weather update uses one call.

1. Sign up at the [HERE developer portal](https://developer.here.com/)
2. Create a new project.
3. Go to project details and under JavaScript, create a new API key.
4. Uncomment the resource `here_key` in config.xml and paste your key as value.

### Meteorologisk institutt

The Norwegian Meteorological Institute offers a free weather API. It has a rate limit of 20
requests/s. You do not need to register or to provide an API key, however they require an
identification and contact data to be present in the User Agent header in each request.

It should also be noted that MET Norway only supports weather requests by lat/lon and the responses do
not include geocoded names. This means a geocoder has to be present in the operating system for this
provider to fully work (most devices ship a geocoder as part of the Google Play Services). If no
geocoder is present, manual location mode will not be supported and the weather widget will show the
lat/lon values instead of a location name.

1. Uncomment `metno_contact` in config.xml. Fill in your contact data (an email address or a website
   where your contact data can be found). This will be used in the User Agent header, which will be
   composed like
   this: `"{app package name}[signature:{app siganture hash}] {@string/metno_contact}"`