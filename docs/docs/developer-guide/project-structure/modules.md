---
sidebar_position: 3
---

# Modules

The project contains of multiple Gradle modules. The structure is kinda scuffed because I didn't
know shit when I
started this project so future refactorings are to be expected. This is the current structure:

- `:app`:
    - `:app`: The app module. Contains almost nothing except the `Application` class (
      `de.mm20.launcher2.LauncherApplication`)
    - `:ui`: Contains almost the entire user interface (except for account sign-in UIs). The only
      module that uses Jetpack Compose.
- `:services`: Higher level APIs for the app's business logic. Each module represents a specific
  functionality of the launcher
    - `:accounts`: Common APIs to manage different account types (Nextcloud, Owncloud)
    - `:backup`: Backup and restore functionality
    - `:badges`: Provide different types of badges that are displayed on app icons
    - `:favorites`: Handles pinned items and item visibility
    - `:global-actions`: Handles global system actions like turning the screen off, and opening the
      notification drawer
    - `:icons`: Used to retrieve icons for items. Handles icon packs, themed icons and also custom
      icons (on a higher level)
    - `:music`: Manage media sessions and extract metadata
    - `:plugins`: Plugin service to list, enable and disable plugins.
    - `:search`: Search service
    - `:tags`: Edit, copy and delete tags
    - `:widgets`: High-level APIs to manage widgets
- `:data`: Lower level APIs. Usually, these modules implement interfaces from the `:core:base`
  module, so that `:services` don't need to depend on `:data` modules directly.
    - `:applications`: Implements APIs for app grid and app search
    - `:appshortcuts`: Implements app shortcuts (search and shortcuts)
    - `:calendar`: Implements calendar search
    - `:calculator`: Implements the calculator.
    - `:contacts`: Implements contact search
    - `:currencies`: APIs to fetch currency conversion rates, used by `:data:unitconverter`
    - `:customattrs`: common (low-level) APIs to store per-app customizations (custom labels, custom
      icons, tags)
    - `:database`: The launcher database, uses AndroidX Room
    - `:files`: Implements file search
    - `:locations`: Implements location search
    - `:notifications`: APIs to read notifications. Contains the app's `NotificationListenerService`
    - `:plugins`: Low level plugin APIs.
    - `:unitconverter`: Implements unit and currency converter.
    - `:weather`: APIs to fetch weather data
    - `:websites`: Implements website search
    - `:widgets`: CRUD operations to store and retrieve widgets in/from the database
    - `:wikipedia`: Implements Wikipedia search
- `:core`
    - `:base`: Interface definitions for the most commonly used data types. Commonly used data
      classes, helper functions and utilities.
    - `:compat`: Compatibility helpers for old Android versions
    - `:crashreporter`: Crash reporter; based on https://github.com/MindorksOpenSource/CrashReporter
    - `:devicepose`: Location and positioning APIs.
    - `:i18n`: All resources that require localization. Mainly strings but can also be used for icon
      resources if they need localization.
    - `:ktx`: Commonly used Kotlin extension functions
    - `:permissions`: Request and observe permission status for this app
    - `:preferences`: Store user preferences; uses AndroidX Datastore
    - `:profiles`: Manage user profiles on the device
- `:libs`: Somewhat standalone modules and 3rd party libraries that do not depend on `:core:base`
    - `:address-formatter`: Fork of https://github.com/woheller69/AndroidAddressFormatter (because
      the upstream library is only available as a `-SNAPSHOT` version)
    - `:material-color-utilities`: This
      library: https://github.com/material-foundation/material-color-utilities (not available as
      Gradle package yet)
    - `:nextcloud`: Nextcloud APIs and Nextcloud sign-in; used by `:accounts` and `:files`
    - `:owncloud`: Owncloud APIs and Owncloud sign-in; used by `:accounts` and `:files`
    - `:webdav`: common APIs for WebDAV search, used by `:nextcloud` and `:owncloud`

Most of the modules have a `Module.kt` file in their root which contains Koin definitions to make
the APIs accessible to other modules.
