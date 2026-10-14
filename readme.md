# Intelia Launcher

A native Android home for local apps and explicit commands. Intelia has its own
interface, identity and transitions: system wallpaper, a quiet home, one bottom
LENS for local search and user-owned FLOW aliases.

## This version

- Own portrait Compose home, app list, settings, mark/icon and runtime identity.
- Tap LENS for search; hold for a shallow ARC of up to five user-selected apps.
- Stable ARC positions, explicit release selection and safe cancellation.
- System wallpaper with conditional public OS blur and contrast fallback.
- Offline app discovery/search and exact FLOW aliases with visible destinations.
- Explicit activation, hidden-target guards and local create/edit/delete persistence.
- Dark/light/system appearance, optional clock/date and reduced-motion control.
- Local `.intelia` backup import/export, bounded archive validation and clear errors.
- Legal information and an exportable project source snapshot inside the APK.

The active interface has no account connection, online search, notification
collection, Accessibility automation or background workflow service. Existing
infrastructure remains available in source for measured, separately scoped
extensions. The current active app library is the personal profile; work/private
profile UI, widgets and typed multi-step workflows are not claimed as complete.

## Build

Use a full JDK 21, Android SDK platform 37.1 and build-tools 36.0.0, then the
checksum-pinned Gradle wrapper. The detailed portable commands are in
[WORKFLOW](docs/intelia/WORKFLOW.md).

```sh
./gradlew :app:app:assembleDefaultDebug :app:ui:testDebugUnitTest \
  :data:search-actions:testDebugUnitTest :core:base:testDebugUnitTest \
  :data:locations:testDebugUnitTest --console=plain --max-workers=4
```

The debug package is `dev.intelia.launcher.debug`. Output:
`app/app/build/outputs/apk/default/debug/app-default-debug.apk`.
A successful build is not a device, performance or production-readiness claim.

## Source and attribution

The application is a modified GPL work. Its original native interface is built
above a preserved open-source launcher foundation; legal provenance and original
notices are in [ATTRIBUTIONS](docs/intelia/ATTRIBUTIONS.md) and the license below.
They are not presented as product branding. Source export includes this build's
project sources/resources and build scripts; it excludes private machine files.

## License

This software is free software licensed under the GNU General Public License 3.0.

```

Copyright (C) 2021–2026 MM2-0 and the Kvaesitso contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

The plugin SDK modules (`plugins/sdk` and `core/shared`) are licensed under the Apache License 2.0.
