# Clock Widget

The clock widget is always the top-most widget in the widget list. It can be customized in several ways at Settings > Home screen > Clock.

## Layout

The clock widget has two layouts:

- **Default**: a vertical layout with a large clock and dynamic components below it
- **Compact**: a horizontal layout that shows clock and dynamic components in a single row

## Style

There are six different clock styles:

- Fat digital clock
- Boring digital clock
- Orbit clock
- [Binary clock](https://en.wikipedia.org/wiki/Binary_clock#Binary-coded_sexagesimal_clocks)
- Analog clock
- Empty clock; in case you want to disable the clock altogether

## Dynamic components

The clock widget can show one dynamic component at a time. Clock widget components can be enabled or disabled in settings.

Base components, you can only enable one of them at a time:

- **Date**: show the current date
- **Favorites**: show the first row of favorites. The number of shown items depends on the grid columns setting (but in compact layout, it's 2 items less than the grid columns setting)

Additionally, these conditional components are available:

- **Media**: show media controls, only when there are active media sessions
- **Alarm**: show the remaining time to the next alarm, only if there is an alarm scheduled to ring within the next 12 hours
- **Battery**: shows the current battery level and remaining charging time, only if battery is charging or battery level is less than 15%
