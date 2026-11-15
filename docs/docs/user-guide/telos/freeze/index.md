# Smart Freeze

![Telos Freeze](https://raw.githubusercontent.com/koukomin/Telos-Launcher/main/docs/public/img/telos-development-22-system.webp)


Freeze or hide apps you do not need right now. A frozen app cannot run in the background until you unfreeze it, and it
is launched again (and unfrozen) the next time you open it from the launcher.

::: info This section
- This page: where to find it, the screens, what you can do, work profile and Sandbox, widget and feature matrix.
- [Backends and profiles](./backends-profiles): the five backends and their setup, freeze methods, auto-freeze profiles,
  exclusion rules and the settings tables.
:::

::: info Where
Smart Freeze is a feature of the launcher settings, not a separate Telos app, so it does not appear in
[Telos Store](../store/). Open **Settings > Freeze Manager**. The legacy dashboard and manager routes both resolve to this same screen.

Per-app freeze now lives on **Telos System → Apps (Freeze Desk)**: search, tech-pill filters (All / Frozen / Running / System / Bloat), a frozen badge, and one-tap freeze/unfreeze through the existing Freeze backend, with deep links to AppOps Next and the package firewall. Freeze Manager’s first tab is a **Rules** alias that opens Freeze Desk and keeps backend status plus list visibility switches; Auto-freeze and Settings tabs are unchanged.

| Tab | Purpose |
| --- | --- |
| Apps | Search name/package, show system/iconless entries, filter frozen/auto-freeze, select all shown, clear selection, explicit Freeze / Unfreeze / Auto-freeze actions |
| Auto-freeze | Master switch, profiles, triggers and activity exclusions |
| Settings | Backend/permissions, original-state update maintenance, history, icon style, visibility and database update preference |

Tap an app row to change its policy, timing, companion apps or read its UAD-NG description. Checkboxes select entries for bulk actions; selecting alone never changes Android state. A result such as 4/5 means one operation was not completed; it is not silently treated as success.

There is also a **Freeze** home screen widget with a "Freeze now" button, and Freeze / Unfreeze entries in the context
menu of an app. Updating a frozen Play Store app from Telos Store thaws it, then freezes it again as soon as Play
installs a new version. If Play Store itself is frozen, Telos unfreezes it for that session and freezes it again when
the updates finish. With Telos Network VPN on and the Store setting enabled, that app has no internet until you
open it. Opening or explicitly unfreezing an app can first thaw its **explicitly enabled** companions. Choose them from the installed-app picker in the app row. Every rule is off by default, including after upgrading. If Gemini and Google are installed, Gemini shows Google as an inactive example. Play Store for a paid app and a game optimizer for a game must be chosen by the user; installation from Play Store alone never enables a rule. Successfully thawed companions are held until their master is frozen again. Shared companions remain held while another master still needs them. Rules apply only to the current Android user.

Auto-freeze skips the foreground app (on by default), the app that was in front when the screen turned off, music, and apps with network traffic.
:::

## What it is

Smart Freeze talks to the system through one **backend** (Shizuku, root, device owner or Island, and a Dhizuku
backend that is not finished) to
**suspend** or **disable** apps. It can freeze apps by hand, or automatically when the screen is off, the phone idles
or Battery Saver turns on, while it protects apps that you are using.

## Requirements and permissions

| Needed for | Requirement | Notes |
| --- | --- | --- |
| Any freezing | One working backend | See [Backends](./backends-profiles#backends) |
| Not freezing the app in use | **Usage access** (`PACKAGE_USAGE_STATS`) | Settings shows a hint until granted. Without it an app could be frozen while you use it |
| The "ready to freeze" notification and Island notification | Notifications | Android 13 and later |
| Work Mode switch | "Manage profiles" access (`INTERACT_ACROSS_PROFILES`) | See [Work profile](#work-profile-android-work-mode) |
| Force stop and clear cache | Shizuku or root | Device owner, Dhizuku and Island cannot do these |

::: tip Grant usage access
Without Usage access Telos cannot know which app is in the foreground, so an app could be frozen while you use it.
Settings shows a hint until it is granted.
:::

## A tour of the screens

### Freeze Manager (Settings > Advanced)

| Section | Contents |
| --- | --- |
| Dashboard | A row "Currently frozen apps and freeze history" that opens the history screen |
| Backend | The backend picker, the active backend, its permission state, and the device owner setup |
| Auto-freeze | Enable switch, Usage access, Profile, triggers, exclusion strictness and the optional exclusions |
| Apps | Switches for system apps, apps without an icon, advanced features, hiding frozen apps and the frozen style, then every app with its freeze state |

### Dashboard (history)

- **Currently frozen**: each frozen app with a "Frozen" badge, or "Nothing is frozen right now".
- **History**: apps that were frozen or unfrozen, with counters ("Frozen N times, unfrozen N times"), "Last frozen" and
  "Last unfrozen" times, or "No apps have been frozen or unfrozen yet".
- **App status**: every app, whether it is Normal, Suspended, Disabled or Hidden, how long it ran today ("1h 5m today",
  "Under 1m", "Not used today") and "Running now". It needs Usage access and has a **Refresh** action.

### Apps tab

| Element | What it does |
| --- | --- |
| Backend status | Shows the current backend; setup and permission controls are in Settings |
| Search | Matches app label and package name |
| Filter chips | All, Frozen and Auto-freeze list |
| System / iconless switches | Include installed entries beyond ordinary launcher apps |
| App row | Opens policy, timing, companions and community notes |
| Checkbox | Selects the row for bulk actions; selection is preserved across tabs |
| Select all shown / Clear selection | Adds currently filtered rows or clears selection |
| Freeze / Unfreeze / Auto-freeze actions | Explicit operations with successful/selected totals; no mixed-state toggle |

## What it can do

- Freeze or unfreeze single apps, or several at once, from the app list and the context menu.
- **Protected packages**: the Android system (`android`), System UI, Settings, the keyguard, the telecom server, Google
  Play services, the Play Store and Telos itself are never frozen, force stopped or cleared.
- **Advanced features** switch (needs Shizuku or root) adds **Force stop** and **Clear cache** to context menus.
- Show or hide system apps and apps without an icon in the list. Hide frozen apps entirely, or show them grayscale or
  with a snowflake badge.
- A frozen app is **unfrozen automatically** when you launch it from the launcher; the launcher waits a moment so the
  system sees the app is enabled again.
- Frozen apps can be driven by a [context profile](../launcher/desktop-and-overlays#context-profiles), which can override the freeze profile.

### Per-app states

Each app in the Freeze Manager list has a state, chosen from its menu:

| State | Meaning |
| --- | --- |
| Not managed | Never frozen automatically. You can still freeze it by hand |
| Auto-freeze (Suspend) | A candidate, frozen by suspending it. The default method |
| Auto-freeze (Disable) | A candidate, frozen by disabling it. Only for Shizuku, root and device owner |
| Never freeze | Never frozen by the automatic triggers. Wins over every other rule |

## Work profile (Android work mode)

Under **Settings > Advanced > Work profile**:

| Feature | What it does |
| --- | --- |
| Status | Not set up, Active or Paused |
| Set up / manage | Opens the system user settings. Telos cannot create a work profile itself. Look under System > Multiple users |
| **Work Mode** switch | Pauses or resumes the work apps (Android quiet mode). Needs the "manage profiles" permission |
| Lock work profile toggle | Asks for authentication before pausing or resuming from the app drawer |
| **Initialize Telos Sandbox** | Starts Android's managed profile setup so an isolated work profile can hold cloned apps. Shown only while no work profile exists |

Shizuku and root apply freezing to the user (profile) of the target app, so apps in a work profile can be targeted.
Dhizuku and device owner act on the current user only.

::: warning Experimental
"Clone to Sandbox" in an app's menu copies the app's APK into the work profile through a small bridge service of Telos.
It is new and <Badge type="warning" text="experimental" /> and <Badge type="info" text="untested" /> across devices.
Whether it works depends on your device and Android version, and it needs a work profile.
:::

## The Freeze widget

A home screen widget with a **Freeze now** button. It shows how many apps are selected ("N apps selected") and the result
("Frozen N of M"). With no candidates it says "No apps selected. Choose apps to freeze in settings."

## Feature matrix

| Feature | Backends | Status |
| --- | --- | --- |
| Suspend an app | Shizuku, Dhizuku, root, device owner, Island (request only) | stable |
| Disable an app | Shizuku, Dhizuku, root, device owner | stable |
| Force stop, clear cache | Shizuku, root | stable |
| Auto-freeze on screen off, idle, Battery Saver | all | stable |
| Profiles and exclusion rules | all | stable |
| "Ready to freeze" notification | all | stable |
| Freeze history and app status | all | stable |
| Frozen apps hidden, grayscale or badged | all | stable |
| Work Mode and Sandbox | needs a work profile | <Badge type="warning" text="experimental" /> |
| Root, device owner | | <Badge type="info" text="untested" /> on many devices |
| Dhizuku | | <Badge type="warning" text="experimental" />: implemented, untested on a device |

## Limitations

- With Island Telos can only send the request. It cannot confirm that the app really ended up frozen, and Android blocks
  it when no screen is visible.
- What works depends on the device and Android version. Root and device owner are
  <Badge type="info" text="untested" /> on many devices. The Dhizuku backend is experimental and untested on a device,
  see [Backends](./backends-profiles#dhizuku).
- Freezing system apps can cause problems. Disabling or force stopping one can break core phone functions and, in the
  worst case, leave the device unable to boot.
- Frozen apps do not run in the background, so they do not deliver notifications until unfrozen.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| "No freeze backend available. Install and set up Shizuku, or use a rooted device." | Start Shizuku and grant it, or set up another backend |
| "Tap to grant permission" | Tap it and allow the backend (Shizuku or root) |
| An app froze while I was using it | Enable foreground-app protection and grant Usage access |
| Auto-freeze does nothing | The app is not a candidate, an exclusion rule applies, or auto-freeze is off |
| Force stop is missing | Turn on **Advanced Features** and use Shizuku or root |

## Dock and foreground protection

Dock entries deliberately show their normal icon and other badges without grayscale or a
snowflake, independently of the drawer's frozen-app style. Launching resolves the current launcher
activity after thawing, including retries while Android refreshes enabled-state information.

**Do not freeze the foreground app** is off by default. Enable it in Freeze settings to protect
the current foreground app from automatic and manual freeze operations. It requires Usage access;
without it Telos cannot reliably identify that app. Other existing notification, media and core
system-package protections remain separate. The check applies to the current Android profile.

Frozen application targets assigned to icon shutters and folder children use the same app-lock and verified thaw-before-launch path as drawer and dock icons. An authorized freeze backend is required to thaw an app.

## Foreground permission and notification

Usage access is requested when enabling foreground-app protection and is only listed while that protection is enabled and access is missing. The quick-freeze notification is hidden when no installed, thawed, eligible candidates remain; expand it to see their app names. Virtual app targets can be saved in dock shutters.


## Per-app screen-off timing

Set the app policy to Auto-freeze, enable the global auto-freeze switch, then choose **Delay after screen-off** in that app’s row. **Use profile timing** is the default. Immediately, 1, 5, 10, 15, 30, 60 or 120 minutes replace the profile’s screen-off/idle timing for that app. For example, a 10-minute app delay wins even when the profile freezes the rest immediately. An explicit app delay also works if the profile has neither screen-off nor idle enabled. Never-freeze, active-use exclusions and update/companion holds still win.

Turning the screen on cancels pending deadlines. Alarms use elapsed time, persist through launcher process recreation, and are discarded across device reboot. Android may defer inexact alarms during Doze; these are not exact wall-clock appointments. Manual Freeze now and the separate battery-saver trigger keep their own behavior.

## Community descriptions and database updates

Descriptions and the Recommended / Advanced / Expert / Unsafe removal labels come from [UAD-NG](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation), the same upstream used by Canta and Thor. They describe removal impacts and may be in English. They are not a guarantee that freezing is safe on your particular ROM.

**Update app descriptions automatically** is enabled by default in the Settings tab. Every entry into Freeze Manager checks the upstream HTTPS endpoint; unchanged content uses ETag/304 instead of downloading the list again. Disable this preference to use the installed database without automatic network checks. A bundled snapshot works on first use offline. Failed downloads, invalid or oversized responses never replace the last validated cache.

The freeze service blocks Unsafe **system** apps and requires explicit per-app consent for Expert system apps. If no valid database can be loaded, system freezing fails closed; unfreezing remains available. Packages without a description are shown as unknown, never as safe. These gates also cover bulk and automatic freeze paths.


## Freeze after removal from Recents

Enable **Auto-freeze > Freeze after removal from Recents** if desired; it is **off by default**, including after an upgrade. The setting uses privileged task-removal callbacks on Android 10+ through running, authorized Shizuku. It can use Shizuku only for detection while your configured freezing backend performs the operation. Root alone, Usage access, device owner and Accessibility do not provide this listener through their ordinary APIs.

A real task-removal event triggers a fresh task read. Telos only refreezes a current-user auto-freeze candidate or an app that Telos actually thawed for the user while this option was enabled, and only after its last task has gone. Multiple tasks, work-profile events, Never freeze, active-use exclusions, maintenance sessions and companion holds remain respected. This option is independent of the screen-off master switch and timing overrides. It does not freeze every arbitrary installed app.

Android’s callback reports task removal, including an app calling finishAndRemoveTask; it does not reveal whether the user swiped the card. Switching apps or returning Home only refreshes the task cache and does not trigger freezing. Missing permission, unknown task identity, a failed task read or a full/truncated task page causes Telos to skip the action. The Settings warning indicates unavailable detection rather than claiming it works. OEM execution and reconnection still require device acceptance.


Recents removal observation now supports Root when Root is the selected freeze backend, as well as Shizuku. Enable the existing Recents removal option and authorize the selected backend. Telos only freezes an eligible package after a real removal callback and a fresh complete snapshot confirm its last task is gone. Other remaining windows, maintenance/update holds and safety exclusions still prevent freezing.

## Import existing frozen apps

When enabling auto-freeze, Telos scans already frozen apps and offers to add eligible current-user apps that are not already candidates or excluded. **Import** imports and enables together. **Skip** enables using the existing list. Dismissing the question leaves auto-freeze disabled. Never-freeze still wins if its settings change while the question is open.

Companion rules are read when an app is opened, even if no settings screen is observing them. For Gemini → Google, select Google in Gemini’s companion picker and enable the rule; the suggested dependency alone does not enable it.

The Recents listener uses a privileged helper for both Shizuku and root. Clear-all removal checks proceed independently, with bounded concurrency. A fresh task snapshot must confirm the last task is gone. Stale foreground usage history alone does not veto this confirmed removal; active playback, notifications, network policy, Never-freeze and maintenance holds remain in force. Missing task visibility fails closed.
