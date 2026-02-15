# Calendar Provider

Calendar provider plugins need to extend
the <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.calendar/-calendar-provider/index.html" target="_blank">
`CalendarProvider`</a>
class:

```kt
class MyCalendarPlugin() : CalendarProvider(
    QueryPluginConfig()
)
```

In the super constructor call, pass
a <a href="/reference/core/shared/de.mm20.launcher2.plugin.config/-query-plugin-config/index.html" target="_blank">
`QueryPluginConfig`</a>
object.

## Plugin config

<!--@include: ./common/_query_plugin_config.md-->

## Calendar lists

Calendar lists are collections of calendar entries (i.e. "Private", "Work", "Family"). Users can
choose, which lists to include in search results and the calendar widget. To implement calendar
lists, override

```kt
suspend fun getCalendarLists(): List<CalendarList>
```

This method should return a list
of <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.calendar/-calendar-list/index.html">
`CalendarList`s</a>. At least one list should be returned.

### The `CalendarList` object

The `CalendarEvent` has the following properties:

- `id`: A unique ID for this list.
- `name`: A human-readable name for this list.
- `contentTypes`: A list
  of <a href="/reference/core/shared/de.mm20.launcher2.search.calendar/-calendar-list-type/index.html">
  `CalendarListType`s</a> (`Calendar`, `Tasks`) that this list includes.
- `accountName` (optional): The name of the account this list belongs to. Lists that belong to the
  same account are grouped together in the launcher UI.
- `color` (optional): The color of this list, in `0xAARRGGBB` format.

## Search calendar events

Calendar search is used by both search and calendar widget.
To implement calendar search, override

```kt
suspend fun search(query: CalendarQuery, params: SearchParams): List<CalendarEvent>
```

- `query` includes the query parameters:
    - `query`: The search term. Can be null if the query was started by the calendar widget.
    - `start`: The timestamp (ms since epoch) of the start of the search window.
    - `end`: The timestamp (ms since epoch) of the end of the search window.
    - `excludedCalendars`: List of calendar list IDs that should be excluded from the search
      results.

<!--@include: ./common/_search_params.md-->

`search` returns a list of `CalendarEvent`s. The list can be empty if no results were found.

### The `CalendarEvent` object

A `CalendarEvent` has the following properties:

- `id`: A unique ID for this event.
- `title`: The title of the event.
- `calendarName`: The name of the calendar the event belongs to.
- `description` (optional): A description of the event.
- `location` (optional): The location of the event.
- `color` (optional): The color of the event, in `0xAARRGGBB` format.
- `startTime`: Start time of the event in milliseconds since epoch. For tasks, this can be null.
- `endTime`: End time of the event in milliseconds since epoch. For tasks, this is the due date.
- `includeTime`: If false, only the date will be shown for the event.
- `attendees`: A list of human-readable names, representing the attendees.
- `uri`: A URI that opens the event. Can be a URI that your app can handle, or a https link.
- `isCompleted` (optional): If this is not null, the event is treated as a task, indicated by a
  checkmark in the UI.

## Refresh an event

If you have set `config.storageStrategy` to `StorageStrategy.StoreCopy`, the launcher will
periodically
try to refresh the stored copy. This happens for example when a user long-presses an event to view
its details. To update the event, you can override

```kt
suspend fun refresh(item: CalendarEvent, params: RefreshParams): CalendarEvent?
```

The stored event will be replaced with the return value of this method. If the event is no
longer available, it should return `null`. In this case, the launcher will remove it from its
database. If the event is temporarily unavailable, an exception should be thrown.

- `item` is the version that the launcher has currently stored

<!--@include: ./common/_refresh_params.md-->

The default implementation returns `item` without any changes.

## Get an event

If you have set `config.storageStrategy` to `StorageStrategy.StoreReference`, you must override

```kt
suspend fun get(id: String, params: GetParams): CalendarEvent?
```

This method is used to look up a event by its `id`. If the event is no longer available, it should
return `null`. In this case, the launcher will remove it from its database.

- `id` is the ID of the event that is being requested

<!--@include: ./common/_get_params.md-->

## Plugin state

<!--@include: ./common/_plugin_state.md-->

## Additional notes

### Calendar widget

The calendar widget uses the same `search` method that the search uses. The only difference is that
`query.query` is always `null` and that `params.allowNetwork` is always `false`.

### Tasks

Tasks are a special type of calendar event, because they can be completed or uncompleted. A
`CalendarEvent` is treated as a task, when its `isCompleted` property is not `null`. For tasks, the
`endTime` is the due date. The `startTime` is the time from which the task should be displayed, and
it can be `null` to always display the task.

## Examples

- **[Tasks.org plugin (deprecated)](https://github.com/Kvaesitso/Plugin-TasksOrg)**  