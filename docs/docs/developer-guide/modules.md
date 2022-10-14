---
sidebar_position: 3
---

# Modules

The source code consists of a number of Gradle submodules which all depend on each other in some way or another:

- `:accounts`: Common APIs to manage different account types (Google, Microsoft, Nextcloud, …)
- `:app`: The app module. Contains almost nothing except for the `Application` class (`de.mm20.launcher2.LauncherApplication`)
- `:applications`: Installed apps and app search
- `:appshortcuts`: Query apps shortcuts for apps and shortcut search
- `:backup`: Backup and restore functionality
- `:badges`: Provide different types of badges that are displayed on app icons
- `:base`: Commonly used data classes, helper functions and utilities. Also icon resources (if they do not need localization).
- `:calculator`: Implements the calculator
- `:calendar`: query calendar events for the calendar widget and calendar search
- `:compat`: Compatibility helpers for old Android versions
- `:contacts`: Query contacts on the device
- `:crashreporter`: Crash reporter; based on https://github.com/MindorksOpenSource/CrashReporter
- `:currencies`: APIs to fetch currency conversion rates, used by `:unitconverter`
- `:customattrs`: common (low-level) APIs to store per-app customizations (custom labels, custom icons, tags)
- `:database`: the launcher database, uses AndroidX Room
- `:favorites`: Handles pinned, frequently used and hidden items and serialization / deserialization of items. Depends on most of the search modules (`:apps`, `:calendar`, `:contacts`, etc.)
- `:files`: Manage and find files (local and cloud)
- `:g-services`: Google APIs and Google sign-in; used by `:accounts` and `:files`
- `:i18n`: All resources that require localization. Mainly strings but can also be used for icon resources if they need localization.
- `:icons`: Used to retrieve icons for items. Handles icon packs, themed icons and also custom icons (on a higher level)
- `:ktx`: Commonly used Kotlin extension functions
- `:material-color-utilities`: This library: https://github.com/material-foundation/material-color-utilities (not available as Gradle package yet)
- `:ms-services`: Microsoft APIs and Microsoft sign-in; used by `:accounts` and `:files`
- `:music`: Manage media sessions and extract metadata
- `:nextcloud`: Nextcloud APIs and Nextcloud sign-in; used by `:accounts` and `:files`
- `:notifications`: APIs to read notifications. Contains the app's `NotificationListenerService`
- `:owncloud`: Owncloud APIs and Owncloud sign-in; used by `:accounts` and `:files`
- `:permissions`: Request and observe permission status for this app
- `:preferences`: Store user preferences; uses AndroidX Datastore
- `:search`: The search. Also websearches.
- `:ui`: Contains almost the entire user interface (except for account sign-in UIs). Uses Jetpack Compose.
- `:unitconverter`: Unit and currency converter
- `:weather`: APIs to fetch weather data
- `:webdav`: common APIs for WebDAV search, used by `:nextcloud` and `:owncloud`
- `:websites`: Website search
- `:widgets`: Manages configuration of widgets (which widgets and which order). Actual widget implementation (for built-in widgets) is in `:ui`.
- `:wikipedia`: APIs to search Wikipedia

Most of the modules have a `Module.kt` file in their root which contains Koin definitions to make the APIs accessable for other modules.

[![](/img/dependency-graph.dot.png)](/img/dependency-graph.dot.png))
