---
sidebar_position: 3
---

# Modules

The project contains of multiple Gradle modules. The structure is kinda scuffed because I didn't know shit when I
started this project so future refactorings are to be expected. This is the current structure:

- `:app`:
  - `:app`: The app module. Contains almost nothing except the `Application` class (`de.mm20.launcher2.LauncherApplication`)
  - `:ui`: Contains almost the entire user interface (except for account sign-in UIs). The only module that uses Jetpack Compose.
- `:services`: Higher level APIs for the app's business logic. Each module represents a specific functionality of the launcher
  - `:accounts`: Common APIs to manage different account types (Google, Microsoft, Nextcloud, …)
  - `:backup`: Backup and restore functionality
  - `:badges`: Provide different types of badges that are displayed on app icons
  - `:icons`: Used to retrieve icons for items. Handles icon packs, themed icons and also custom icons (on a higher level)
  - `:music`: Manage media sessions and extract metadata
  - `:search`: The search.
  - `:tags`: Edit, copy and delete tags
  - `:widgets`: High-level APIs to manage widgets
- `:data`: Lower level APIs. Generally, these modules are more multi-purpose and provide the data that is consumed by the `:services` modules.
  - `:applications`: Installed apps and app search
  - `:appshortcuts`: Query apps shortcuts for apps and shortcut search
  - `:calendar`: query calendar events for the calendar widget and calendar search
  - `:calculator`: Implements the calculator. Should probably be moved to `:services`
  - `:unitconverter`: Unit and currency converter. Should probably be moved to `:services`
  - `:contacts`: Query contacts on the device
  - `:currencies`: APIs to fetch currency conversion rates, used by `:data:unitconverter`
  - `:customattrs`: common (low-level) APIs to store per-app customizations (custom labels, custom icons, tags)
  - `:favorites`: Handles pinned, frequently used and hidden items and serialization / deserialization of items. Depends on most of the search modules (`:applications`, `:calendar`, `:contacts`, etc.)´. This module needs to be refactored and split into at least two different modules.
  - `:files`: Manage and find files (local and cloud)
  - `:notifications`: APIs to read notifications. Contains the app's `NotificationListenerService`
  - `:websites`: Website search
  - `:weather`: APIs to fetch weather data
  - `:widgets`: CRUD operations to store and retrieve widgets in/from the database
  - `:wikipedia`: APIs to search Wikipedia
- `:core`
  - `:base`: Commonly used data classes, helper functions and utilities. Also icon resources (if they do not need localization).
  - `:i18n`: All resources that require localization. Mainly strings but can also be used for icon resources if they need localization.
  - `:ktx`: Commonly used Kotlin extension functions
  - `:compat`: Compatibility helpers for old Android versions
  - `:crashreporter`: Crash reporter; based on https://github.com/MindorksOpenSource/CrashReporter
  - `:permissions`: Request and observe permission status for this app
  - `:preferences`: Store user preferences; uses AndroidX Datastore
  - `:database`: the launcher database, uses AndroidX Room
- `:libs`: Somewhat standalone modules and 3rd party libraries that do not depend on `:core:base`
  - `:g-services`: Google APIs and Google sign-in; used by `:accounts` and `:files`
  - `:ms-services`: Microsoft APIs and Microsoft sign-in; used by `:accounts` and `:files`
  - `:material-color-utilities`: This library: https://github.com/material-foundation/material-color-utilities (not available as Gradle package yet)
  - `:webdav`: common APIs for WebDAV search, used by `:nextcloud` and `:owncloud`
  - `:nextcloud`: Nextcloud APIs and Nextcloud sign-in; used by `:accounts` and `:files`
  - `:owncloud`: Owncloud APIs and Owncloud sign-in; used by `:accounts` and `:files`


Most of the modules have a `Module.kt` file in their root which contains Koin definitions to make the APIs accessible to other modules.

[![](/img/dependency-graph.dot.png)](/img/dependency-graph.dot.png))
