# Places Search

Places search provider plugins need to extend
the <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.locations/-location-provider/index.html" target="_blank">`LocationProvider`</a>
class:

```kt
class MyplaceSearchPlugin() : LocationProvider(
    QueryPluginConfig()
)

```

In the super constructor call, pass
a <a href="/reference/core/shared/de.mm20.launcher2.plugin.config/-query-plugin-config/index.html" target="_blank">`QueryPluginConfig`</a>
object.

## Plugin config

<!--@include: ./common/_query_plugin_config.md-->

## Search places

To implement place search, override

```kt
suspend fun search(query: LocationQuery, params: SearchParams): List<Location>
```

- `query` includes the query parameters:
  - `query`: The search term
  - `userLatitude`: The latitude of the user's current location
  - `userLongitude`: The longitude of the user's current location
  - `radius`: The search radius in meters

<!--@include: ./common/_search_params.md-->

`search` returns a list of `Location`s. The list can be empty if no results were found.

### The `Location` object

A `Location` has the following properties:

- `id`: A unique and stable identifier for this location. This is used to track usage stats so if
  two
  places are identical, they must have the same ID, and if they are different, they need to have
  different IDs.
- `label`: The name that is shown to the user
- `latitude`: The latitude of the location
- `longitude`: The longitude of the location
- `icon`: An enum value of `LocationIcon` that determines the icon that is shown for this location
- `category`: A human readable category of the location. For example, _"Restaurant"_, _"Hotel"_, or
  _"Museum"_. This should be localized with the `params.lang` parameter.
- `address`: The address of the location
- `websiteUrl`: The URL of the location's website
- `phoneNumber`: The phone number of the location
- `emailAddress`: The email address of the location
- `openingSchedule`: Either
  - `OpeningSchedule.TwentyFourSeven` if the location is open at all times
  - `OpeningSchedule.Hours(openingHours: List<OpeningHours>)` if the location has specific opening
    hours, with each `OpeningHours` object containing:
    - `dayOfWeek`: The day of the week, as `DayOfWeek` enum value. If a location is open past
      midnight, `dayOfWeek` should refer to the day when the opening hours start.
    - `startTime`: The time the location opens, as a `LocalTime` object
    - `duration`: The duration the location is open, as a `Duration` object
- `departures`: If the place is a public transport station, this field contains the next departures
  from this station. This is a list of `Departure` objects, each containing:
  - `time`: The scheduled departure time as `ZonedDateTime`
  - `delay`: The delay as `Duration`. If the departure is on time, this must be `Duration.ZERO`.
    If no real-time data is available, this should be `null`.
  - `line`: The line name (e.g. _"S1"_, _"U2"_, or _"73"_)
  - `lastStop`: The destination of the line
  - `type`: The type of the line, as `LineType` enum value
  - `lineColor`: The color of the line, as a `Color`
  - `userRating`: A user rating of this location, on a scale from 0 to 1. This is multiplied by 5
    and shown as a star rating bar in the launcher.
  - `userRatingCount`: The number of user ratings that were used to calculate the `userRating`
  - `fixMeUrl`: A URL where users can report incorrect data for this location.
  - `attribution`: Attribution that should be shown alongside the search result (read the data
    provider's terms of service to find out if this is required).

## Refresh a place

If you have set `config.storageStrategy` to `StorageStrategy.StoreCopy`, the launcher will
periodically try to refresh the stored copy. This happens for example when a user long-presses a
place to view its details. To update the place, you can override

```kt
suspend fun refresh(item: Location, params: RefreshParams): Location?
```

The stored place will be replaced with the return value of this method. If the place is no longer
available, it should return `null`. In this case, the launcher will remove it from its database. If
the place is temporarily unavailable, an exception should be thrown.

- `item` is the version that the launcher has currently stored

<!--@include: ./common/_refresh_params.md-->

The default implementation returns `item` without any changes.

## Get a place

If you have set `config.storageStrategy` to `StorageStrategy.StoreReference`, you must override

```kt
suspend fun get(id: String, params: GetParams): Location?
```

This method is used to lookup a place by its `id`. If the place is no longer available, it should
return `null`. In this case, the launcher will remove it from its database.

- `id` is the ID of the place that is being requested

<!--@include: ./common/_get_params.md-->

## Plugin state

<!--@include: ./common/_plugin_state.md-->

## Examples

- [Foursquare plugin](https://github.com/Kvaesitso/Plugin-Foursquare)
- [HERE plugin](https://github.com/Kvaesitso/Plugin-HERE)
- [Public transport plugin](https://github.com/shtrophic/KvaesitsoPlugin-PublicTransport)