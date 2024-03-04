# Restricted Settings on Android 13+

Starting with Android 13, some settings are restricted for sideloaded apps and extra steps are required to enable them.
For Kvaesitso, this affects the notification access permission and the accessibility service.

## Notification Access

The notification access permission is used to display notification badges, and to control music playback.

1. Try to grant the notification access permission as you would normally do. If you sideloaded the APK, all the controls are disabled:

<img src="/img/notification-access-1.png" width="300"/>

2. Tap on the disabled toggle “Allow notification access”. This dialog will show up:

<img src="/img/notification-access-2.png" width="300"/>

3. Tap “OK” to close the dialog.

4. Tap on the app icon. It leads to the app info screen.

<img src="/img/notification-access-3.png" width="300"/>

5. Tap on the 3-dot-menu (<span class="material-symbols-rounded">more_vert</span>) in the top-right corner.

> [!INFO]
> This menu only shows up if you have tried to enable a restricted setting before. **Step 2 is crucial for this to work.**

6. Tap on “Allow restricted settings”

<img src="/img/notification-access-4.png" width="300"/>

7. Go back to the previous screen. The controls are no longer disabled, and you can allow notification access.

## Accessibility Service

The accessibility service is used to perform certain gesture actions, like turning the screen off, or opening the notification shade.

1. Try to enable the accessibility service as you would normally do. If you sideloaded the APK, you will find that Kvaesitso is disabled:

<img src="/img/accessibility-service-1.png" width="300"/>

2. Tap on the disabled entry. This dialog will show up:

<img src="/img/accessibility-service-2.png" width="300"/>

3. Tap “OK” to close the dialog.

4. Navigate to the app info screen (system settings > apps > Kvaesitso).

<img src="/img/notification-access-3.png" width="300"/>

5. Tap on the 3-dot-menu (<span class="material-symbols-rounded">more_vert</span>) in the top-right corner.

> [!INFO]
> This menu only shows up if you have tried to enable a restricted setting before. **Step 2 is crucial for this to work.**

6. Tap on “Allow restricted settings”

<img src="/img/notification-access-4.png" width="300"/>

7. Go back to the accessibility screen. Kvaesitso is no longer disabled, and you can enable it.
