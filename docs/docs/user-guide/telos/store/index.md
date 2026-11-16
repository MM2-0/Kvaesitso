# Telos Store

![Telos Store](https://raw.githubusercontent.com/koukomin/Telos-Launcher/main/docs/public/img/telos-development-07-store.webp)


An app installer and updater that works like [Obtainium](https://github.com/ImranR98/Obtainium): it installs apps
straight from the place where developers publish them, and tells you when a new version exists. It also manages the
Telos apps.

::: info At a glance
<Badge type="tip" text="no account needed" /> <Badge type="tip" text="Obtainium import and export" />
<Badge type="tip" text="silent updates with Shizuku or root" />
:::

::: info This section
- This page: what it is, the screens, permissions, managing the Telos apps and the feature matrix.
- [Sources and updates](./sources-updates): every supported source, per-app settings, update checks, installing,
  import and export.
:::

## What it is

Telos Store keeps a **list of apps you track**. For each one it knows where the developer publishes releases (GitHub,
GitLab, Codeberg, F-Droid and so on), checks that place for new versions, downloads the APK that fits the phone, and
installs it. There is no central Telos catalog and no account. It does not scan or vouch for anything: you choose the
sources.

Telos Store is also where the built-in [Telos apps](../#how-the-built-in-apps-work) are switched on and off. It is
itself a Telos app, but it **cannot be removed**, otherwise there would be no way back.

Available updates sit at the top of the Apps list (and of the Play tab). If a new Telos launcher
build is among them, it is the first row. Automatic checks notify about Telos updates but do not
install them; manual **Update all** runs Telos after the other tracked apps.

## How to open it

| From | What opens |
| --- | --- |
| The Telos Store icon in the app grid or search | The dashboard |
| An `obtainium://add/<address>` link | The dashboard with the Add dialog filled in |
| An `obtainium://app/<definition>` link | The app is added at once (the definition is Obtainium's JSON for one app) |

## Permissions

| Needed for | Permission or access | Notes |
| --- | --- | --- |
| Checking sources and downloading | Internet | Used only for the sources you track |
| Installing apps | "Install unknown apps" (`REQUEST_INSTALL_PACKAGES`) | Android asks the first time. Not needed when Shizuku or root installs silently |
| Uninstalling apps | `REQUEST_DELETE_PACKAGES` | Android shows its own confirmation |
| Update notifications | Notifications | Android 13 and later |
| Silent installs | Shizuku or root | Optional |

## A tour of the screens

### Dashboard

| Element | What it does |
| --- | --- |
| Title "Store" | The screen name |
| **Check for updates** (refresh icon) | Asks every source now |
| **More** menu | **Add installed apps**, **Import (Obtainium file)**, **Export (Obtainium file)**, **Store settings** |
| Tabs **Apps**, **Telos apps**, **Recommended** and **Play Store** | Your tracked apps, the built-in Telos apps, suggested free apps, and (when Play Store is installed) every Play-installed app including frozen ones. Opening Store with Play installed lands on **Play Store**, filters to updates, and starts a scan |
| Search field "Search apps" | Matches the name, the package name and the category |
| Filter chips | **All**, **Updates (N)**, **Installed**, **Not installed**, **Track only**, and one chip per category |
| **Update all (N)** | Installs every available update, except for apps set to Track only; Telos Launcher runs last |
| **Add app** button | Opens the Add dialog |
| App rows | Name, source, the version state and a button: **Install**, **Update**, **Open** or **Retry**, and a progress text "Downloading" or "Installing" |

The list shows apps with updates first, then the others by name. A row says `v1.2 -> v1.3` for an update, `v1.2` for
the installed version, "Not installed", "v1.3 available" for track only apps, and marks "track only" and "pinned" apps.
An empty list says "No apps yet" and a filter without results says "No apps match".

### Play Store

The **Play Store** tab appears only when `com.android.vending` is installed. Opening Telos Store then goes to this tab, starts a scan, and shows the updates filter. It lists Play-installed apps, frozen included. The button **Unfreeze and update in Play Store** thaws frozen Play apps (and Play Store if it was frozen), optionally blocks their network while Telos Network VPN is on, and opens Play Store on the updates list. **Check for updates** compares installed version names with the public listing page. **Update** on a row does the same thaw for that app and opens its Play page. Telos watches the installed version code and freezes freeze-candidates again as soon as Play installs a newer build (one hour is only a fallback). Store settings **Block network until opened** (on by default): when Telos Network VPN is running, those thawed apps have no internet until you launch them. Network block applies only to the thawed apps, not to the whole device. The Store uses the Telos glass/blur theme (wallpaper shows through when transparency and drawer blur are on). Pull down to refresh. Play apps are grouped into **Updates**, **Frozen** and **Up to date**. Swipe a Play row right to thaw and open the Play listing, left to open Play. The Store icon shows a badge with the number of available updates. A **Last check** line stays under the tabs.

### Recommended

Suggested apps in four groups. **Add** puts one into your tracked apps, where it is installed and updated like any other (an app that is already tracked shows "Added"). The Store only adds the place where the app's releases are published; it does not host anything.

| Group | Apps |
| --- | --- |
| Stores | Aurora Store, Obtainium, Accrescent, F-Droid, Droid-ify |
| Tools | Shizuku, Shizuku Next, Morphe Manager, Morphe microG, SD Maid SE |
| Browser | IronFox, Cromite, Titanium Browser, Iceraven, Tor Browser, Waterfox |
| Media | Nova Video Player, Transistor, VLC, BoomingMusic |

Icons: installed apps are shown with their icon as the launcher draws it (your icon pack and icon shape); apps that are not installed yet show a letter. Not every source was checked on a device, so an app can fail to add when its release page changes or has no APK (this may happen with Waterfox, whose project page is for the desktop browser); the error is shown. Tor Browser is read from the Tor Project's download folder, newest version first. There is no "IzzyOnDroid" entry because it is a repository and not an app: add any of its apps through its F-Droid page address instead.

### App page

| Section | Contents |
| --- | --- |
| Header | Name, package name ("Package name known after the first install" for apps added by address), source |
| **Check now** | Checks this app only |
| Versions | Installed (with the version code), Latest, Size, Published, Last checked |
| **Skip this version** | Ignores the latest release. A button **Stop skipping vN** undoes it |
| Changelog | The release notes when the source provides them |
| Settings of this app | Track only, Stay on this version, Exclude from background updates, Category, Note, Rename, file name filter, Include pre-releases, **Apply and check** |
| Source | The address, with **Open**, **Share link** (an `obtainium://add/` link), **Remove from list** and **Uninstall app** |

The details of these settings are in [Sources and updates](./sources-updates#per-app-settings).

### Add dialogs

- **Add an app**: an **Address** and an optional **Package name** ("Fill this in if the app is already installed, so
  it shows as installed"). Paste a link and Telos recognizes the source.
- **Add installed apps**: a list of apps already on the phone. Telos looks them up on F-Droid, then IzzyOnDroid, and
  keeps the ones it finds up to date.
- **Store settings**: see [Updates](./sources-updates#updates).

## Managing the Telos apps

The **Telos apps** tab lists the built-in apps with a description and feature list of each one. Its search field also matches the same English and Greek synonym keywords as the launcher search (for example `pdf` or `gallery` for Telos Viewer).

| Action | Result |
| --- | --- |
| Install | Shows the app's icon in the app grid and in search |
| Open | Opens the app |
| Remove | Hides it. The code stays in Telos, so hidden apps cost nothing |
| Store | Cannot be removed, otherwise there would be no way back |

The apps are Telos Store, Telos Phone, Telos Messages, Telos Radio, Telos Music, Telos Video, Telos TV, Telos Viewer, Telos
Files, Telos Calculator, Telos Screenshot, Telos Screen Recorder and Telos Voice Recorder. [Smart Freeze](../freeze/) is a launcher feature in Settings, not a Telos app, so it is not listed here.

If an app was switched off by the [crash guard](../launcher/privacy-protection#crash-guard), its row says "Switched off
automatically because it crashed repeatedly." Installing it again resets the counter.

For Radio, Music, Video and Viewer "removed" also disables their services and screens, so they use no memory or CPU and
are no longer offered in "Open with". Phone and Messages stay registered with Android either way.

## Feature matrix

| Feature | Where | Notes |
| --- | --- | --- |
| Track apps from GitHub, GitLab, Codeberg, Forgejo, Gitea, F-Droid, IzzyOnDroid, SourceForge, web pages, direct links | Add app | |
| Update checks in the background | Store settings | Periodic job, Wi-Fi only by default |
| Silent updates | Store settings | Shizuku or root |
| Per-app options | App page | Track only, pin, skip, exclude, filter, pre-releases, category, note, rename |
| Search, filters, update all | Dashboard HUD | Search icon expands the field. Filter icons (long-press for the name). Update-all with a count badge. Last check as a HUD readout. Categories as two-letter pills |
| Add installed apps | More menu | F-Droid, then IzzyOnDroid |
| Obtainium import and export | More menu | Both directions |
| `obtainium://` links | Links | add and app |
| Manage the Telos apps | Telos apps tab | |
| Account, reviews, ratings, a catalog | | Not available |

::: warning
Installing APKs from outside an app store means you trust the source. Check the developer and the signature. Telos
refuses an APK whose package name differs from the tracked app, and Android refuses an update signed with another key,
but Telos cannot judge whether the source itself is trustworthy.
:::

## Privacy

- The Store contacts only the sources you track, and GitHub with your token if you added one.
- No analytics, no account, no list of your apps leaves the phone. **Add installed apps** looks up package names at
  F-Droid and IzzyOnDroid.
- APKs are downloaded to the app's cache (`store_downloads`) and installed from there.

## Limitations

- Only the listed sources. Obtainium sources such as APKMirror, Uptodown, Aptoide, APKPure, the Play Store, Huawei,
  Tencent, RuStore and Telegram are skipped when importing.
- Obtainium file imports are limited to 4,194,304 characters. The limit is enforced while reading, so an oversized file is rejected before it is loaded into memory.
- Η εισαγωγή αρχείων Obtainium έχει όριο 4.194.304 χαρακτήρων, το οποίο ελέγχεται κατά την ανάγνωση ώστε ένα υπερβολικά μεγάλο αρχείο να μη φορτωθεί ολόκληρο στη μνήμη.
- Apps in several parts (split APKs, bundles) and apps that need a login to download are not supported.
- Silent installs need Shizuku or root. Otherwise Android shows its installer each time.
- Checks run as a periodic job, so Android decides the exact moment.
- GitHub allows 60 requests per hour without a token.

## Troubleshooting

| Problem | Try this |
| --- | --- |
| "GitHub's request limit is reached" | Add a token in Store settings or wait an hour |
| An app does not show as installed | Fill in its package name, or install it from the Store once |
| "This file is for X, not for Y" | The source delivered an APK for another package. Check the file name filter |
| Installing opens the system installer | Shizuku or root is not available or not allowed |
| An update is not offered | The app is pinned, the version is skipped, or it is set to Track only |
| A Telos app has no icon | Open the Telos apps tab and tap Install |

## Updating frozen applications

Enable **Update frozen apps** in Store settings (off by default). Authorized Root/Shizuku installs
save the original freeze state, stream the private APK through explicit privileged sessions, and
verify/restore the saved restrictions after success or failure. A native update does not depend on
thawing first. Where supported, the enabled-setting preservation flag avoids enabling the target;
restoration is still verified afterwards. Standard Android session and external installers can
thaw through an authorized backend before handoff. Auto-freeze holds journaled targets throughout
the update window. Failed restoration stays pending. Telos configured as **Device Owner** uses Android
PackageInstaller sessions and restores state from the final callback, including cancellation.
Device Owner updates reject package/component disable overrides that this backend cannot restore.
Android may still require user action under its update policies. Plain Device Admin access does
not grant these privileges; it is not advertised as a silent installer.

This switch does not enable automatic updates for any app. Existing per-app/global opt-in rules,
APK package/signature checks and Android update ownership restrictions continue to apply.
Unfinished restoration is retained in Freeze Manager; after an interrupted update, use its
**Freeze again** action once installation has finished. For Play Store updates, use Freeze Manager's
temporary **Unfreeze all / Freeze again** session instead. Current-profile and backend limitations
are documented in [Smart Freeze](../freeze/backends-profiles.md#temporary-update-session).

## Published icons for apps not yet installed

Tracked rows, recommendations and app details prefer the installed application’s icon/icon pack.
For uninstalled apps, Telos looks for published icons in GitHub/GitLab/Gitea README artwork and
Fastlane paths, F-Droid/IzzyOnDroid package metadata, SourceForge project pages and linked HTML
pages. It does not download an APK just to obtain an icon or substitute a GitHub owner avatar.
Lookup responses are limited to 256 KiB, at most four lookups run concurrently, and a bounded
in-memory cache plus the image cache avoids repeated successful downloads. If no usable artwork
is published, the letter placeholder remains. Image-server failures never block installing an app.

Manual installation failures open a scrollable, selectable diagnostic dialog instead of truncating
the installer response in a toast. Shizuku/root process streams are fully read before cleanup so
`Success` and actual failure messages are not lost. Frozen/hidden installed apps are also included
in source discovery; discovery alone never opts them into automatic installation.

## Choosing an installation method

Store settings now let you explicitly select **Android session installer**, **Shizuku**, **Root**,
or an installed, exported APK handler (for example Thor). The Android session installer is the
default. A selected privileged backend must be running and authorized; an unavailable backend
reports a failure rather than silently switching methods. Choose another method and retry the
already downloaded APK. External handlers are discovered from their Android intent filters in
the current profile, not from a hard-coded list of names. Island only appears if that installed
version exposes a compatible APK handler; its work-profile policy is not a universal installer.

Shizuku/root use `install-create`, streamed `install-write`, then `install-commit`, with session
abandonment after failure. No private APK path is handed to the shell. Frozen-app updates on
these paths record the original state and install directly, then verify restoration. Android
session/external installation may temporarily thaw with an authorized freeze backend; its
callback restores the journaled state. If an external installer does not return a result or the
process is interrupted, use pending-update recovery in Freeze settings; Telos does not assume
success or erase the recovery snapshot. External installation is never a silent auto-update path.

Session failure callbacks now clear the pending row and display their diagnostic text. Exact
OEM installation failures still need the complete error shown by the selected backend.

## Built-in application names and icons

Built-in entries now use localized names without the Telos prefix in the launcher, Store,
gesture picker and Recents. Search aliases retain `telos`, English names, Greek names and prior
feature synonyms regardless of display language. Keys, stored dock items and disabled-app choices
are unchanged. First-party feature artwork is preferred over generated icons for synthetic
components; explicit custom icons still take precedence. Recents and Store use full-color artwork
referenced directly so release resource shrinking cannot remove it.

The launcher drawer width is controlled separately under Settings → Icons → App drawer → Full width on tablets. Tablets use adaptive columns unless a custom drawer column count is selected.

Store cards wrap full app names and separate actions from the title for large system fonts. Tabs scroll horizontally instead of squeezing labels. Tap a tracked or recommended card to visit the project or app listing (not the release); Details retains per-app management. Direct APK sources without a known project resolve to the hosting site's home page. This uses source addresses only, without AI or generated descriptions. Shizuku does not bypass Android update-signature validation.

Dock and shutter app pickers show applications only and start a fresh search on every opening. App picker names wrap at large font sizes, overflow action menus scroll, and Telos Media reserves the navigation-bar inset even when no mini player is visible. Other general item pickers retain their supported item types.

Transfer network rules independently block Wi-Fi, mobile data, roaming and metered connections. Downloader has its own profile; torrents, Video and Stremio share a profile because the torrent session is shared. Downloader queues pause and resume as connectivity changes, torrent sessions pause on disallowed networks, and online video data sources check the policy before opening and reading. The player also stops loading on a blocked connection and prepares again when permitted. Existing Downloader Wi-Fi-only preferences and the mirrored Video Wi-Fi-only preference provide initial defaults. VPNs without an identifiable underlying transport are blocked when transport restrictions are active.

## Implementation limits and app details

The installed-package browser is in **System → Apps**. Tracked packages open their Store item ID; untracked packages open Android app settings. Frozen/system filters and UAD notes share the Freeze Manager’s validated offline data. UAD categories describe community removal recommendations, not guaranteed freezing safety.

The Firewall button opens the existing Telos Network per-app rules, scoped to that package. Rules take effect while Telos Network VPN runs and apply to all packages sharing the same UID. The separate audio-focus and component-blocker additions remain unavailable because their enforcement is not implemented. APK installation can inspect package permissions and certificates; this is not a malware verdict. Silent installation still depends on the selected backend. See the [implementation audit](../../../developer-guide/audits/2026-10-25-gemini-review) for the complete feature/UI inventory and remaining work.

### Installed application management

Open **Telos System → Apps** and select any installed package for its internal management page. Store tracking is not needed; installed Store entries link to that same page. Debloating means freeze/unfreeze only. The page opens per-app freeze policies (including delay overrides and companion thaw rules), the bulk freeze hub, Network firewall rules, manifest permissions/certificates, launch/OS settings and APK/link sharing. Split apps share every APK together in a ZIP. Community UAD removal categories are explanatory, not a guarantee that freezing is safe. Component blocking is an explicit Root operation with confirmation and a default-state restore action. Audio focus requests and audio playback can now be allowed, disabled or restored to system default with root/Shizuku. Disabling focus blocks new requests; it does not override the app’s focus-loss callbacks, and some apps stop when focus is denied. Restart playback after changes. Firewall enforcement requires Telos Network VPN on; shared-UID packages share its rules.

### Audio AppOps follow-up

System package details read TAKE_AUDIO_FOCUS and PLAY_AUDIO through an available root/Shizuku command channel, independently of the selected freezing provider. No edit runs on entry. Explicit edits touch only the chosen operation and current user; readback determines success, with best-effort rollback on mismatch. Default sets only that operation to Android’s default; it never resets unrelated app-ops. UID overrides, unsupported output and command failures leave editing unavailable. System-critical packages are protected. Commands are validated and time-bounded; Android persists app-op policy across process restarts. UI explains that denying focus requests can stop apps and does not suppress focus-loss callbacks. Existing playback may need restarting. Physical OEM/audio behavior still needs device validation.


System → Apps and each package page now offer AppOpsNext (Android 15+). The button opens the complete external AppOpsNext application; if it is not installed, it opens its official GitHub project in Telos Store for installation. Choose the target app in AppOpsNext. Its own Shizuku authorization is separate from Telos. The complete upstream suite includes package/UID modes, permission browsing, templates and batch changes, new-install rules, history/archive, experimental monitoring and watcher diagnostics. Native backend support follows upstream’s device/ABI limits (tested ARM64); Telos does not claim that suite is embedded or that every OEM supports every operation. System audio focus/playback controls remain built into Telos and now select a usable privileged command channel independently of the freeze backend. Binder/permission changes and returning to the screen refresh availability.

Root component editing also checks actual Root authorization independently of the freezing provider; it still requires explicit confirmation and retains protected-package checks.
