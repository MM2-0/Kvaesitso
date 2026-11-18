# Changelog

## 1.45.70

- Fix a cold-start dependency-registration race that could crash Telos before
  a directly opened app appeared; preserve deferred background initialization.

- Aether embeds the Amethyst F-Droid client in the Telos APK, with account
  creation/import, multiple-account switching/removal, settings, feeds, relays,
  messaging and media. No external Amethyst APK is required.
- Isolate client credentials from launcher preferences and Android backup; defer
  initialization, preserve the keyless browser sandbox and forward Nostr links/shares.
- Keep internal Telos task identifiers out of the logged-out Nostr link handler.
- Inherit Telos appearance and fonts, add Cybertech settings chrome and Aether
  branding, and keep client notifications separate from alarms and other players.
- Align embedded parser dependencies and fix duplicate Vue runtimes in the docs build.

## 1.45.69

- Store Shizuku/root installs stream the APK through a real root shell (not `su -c`) and time out
  instead of hanging.
- Launcher search shows favorite radio stations and TV channels (on by default).
- Wallpaper restore notifications use an explicit activity; secret scanning CI and gitignore for
  google-services.json.

## 1.45.68

- AAB CI preinstalls NDK 27 and times out hung Gradle instead of sitting for 40+ minutes.

## 1.45.67

- Fix System Battery tab compile (`PreferenceCategory` content) so release APKs build.
- Preinstall NDK 27 in the APK workflow so armeabi-v7a is not killed by a truncated SDK zip.

## 1.45.66

- Release Gradle resolves Kotlin from Google/Maven Central first and skips the JetBrains
  Compose Space repo for stdlib, so APK builds survive a Space DNS failure.

## 1.45.65

- Restore video/GIF live wallpaper after Telos APK updates (retry bind, else a restore
  notification). Static images stay as the system wallpaper.
- Telos System → Battery: opt-in screen-off GPS/data/mic/camera/sync with Maps and
  messaging-call hold-outs, plus hourly weather/sync windows (Shizuku or root).
- Idle GPS/weather/mail/FX/sensor sampling uses less battery without changing Call Screening.

## 1.45.64

- Store, Downloads, Freeze and ART Optimizer use a compact command HUD: icon search, icon
  filters with long-press labels, update-all badge, and last-check readout.

## 1.45.63

- Restore Telos Store Shizuku installs: parse session IDs from more `pm` outputs, treat prefixed
  Success as OK, fall back to stdin `pm install` when sessions fail, and restart the Shizuku
  UserService after launcher updates.

## 1.45.62

- Fix Stremio Fork compile on Kotlin 2.4: upcoming-episode load uses `async` on a coroutine scope,
  so F-Droid, AAB and CodeQL builds succeed.

## 1.45.61

- Stremio Fork: `stremio://` detail links, add-on configuration pages, continue-watching progress,
  calendar of upcoming library episodes, Open with / Telos Downloads on streams, local watch
  profiles, and streaming-server status in the overflow menu.
- Desktop CI unit tests run without the Gradle configuration cache after Maven 429 and cache
  serialization failures on linux-jar.

## 1.44.20

- Fix the crash when scrolling down the ART optimizer to the per-app search
  filters: the search bar now owns the single horizontal scrolling strip.
- Fix the same nested-scrolling problem in the shared app search/filter bar,
  preserving filter selection, search text and existing translations.
- Add Android regression coverage for scrolling to the real ART header and
  reaching every filter with Greek labels at normal and doubled font scale.

## 1.44.19

- Fix 18 reproduced dependency advisories in the root Dokka documentation tooling,
  including critical FreeMarker path traversal (CVE-2026-84939, Dependabot #57).
- Apply security dependency rules to the root project as well as subprojects;
  verify resolved Dokka dependencies before submitting the dependency graph.
- Align Guava 33.8.0 and jsoup 1.24.1 across declarations and transitive pins.
  Keep Android Guava in Android app classpaths and JRE Guava in build tools.
- Retain full Guava/jsoup license texts in APK assets and document private
  vulnerability reporting and the dependency audit evidence.

## 1.44.13

- System tweaks: save working tweaks and optionally re-apply them on every boot (Root/Shizuku).
- Telos Media / Stremio player: hide controls and stats until the screen is tapped.
- Torrents: extra public trackers, longer magnet wait, DHT reannounce to find seeders faster.
- Shizuku pairing popup when the wireless ADB code is on screen; Gradle no longer hangs waiting for NDK download.

## 1.44.12

- Compile: androidx RemoteInput for wireless ADB pairing notification; rename PlaybackSpectrum capture toggle (JVM clash).
- Release CI allows a GitHub Release created on the same tag commit so APKs can attach, including a non-draft release.

## 1.44.11

- Jackson 2.22.4 / annotations 2.22, Gson 2.15, plugin-classpath CVE pins (Dependabot).
- Linux CI: `setup-android` v4. Workflows set explicit `GITHUB_TOKEN` permissions.
- Zip Slip guard on comic archives, explicit notification PendingIntents, biometric `Cipher.doFinal` gates.

## 1.44.10

- Privacy Shield and built-in Shizuku live in Telos System. The home search-bar shield icon stays a shield and opens Telos System.
- Wireless debugging opens the system Wireless debugging page. When the pairing-code dialog is shown, a notification asks for the 6 digits (Shizuku-style RemoteInput).
- CI: `setup-android` v4 (Ubuntu 26 runners). Jackson 2.22.4 / annotations 2.22, Gson 2.15, plugin-classpath CVE pins. Workflows set explicit `GITHUB_TOKEN` permissions. Zip Slip, explicit notification PendingIntents, biometric `Cipher.doFinal` gates.

## 1.41.86

### Telos Media & Flow-Tube UI
- **Flow-Tube UI Redesign:** Fully adapted YouTube & YT Music interface to Flow-Tube specifications with rounded cards (`20.dp`), thumbnail duration tags, channel badges, and glass floating mini-player.
- **1-Click YouTube Playlist Import:** Paste any public or unlisted YouTube playlist link in the Account Panel to import all tracks into Telos Media in seconds.
- **Synced / Timed LRC Lyrics:** Real-time line-by-line scrolling lyrics (`SyncedLyricsSheet`) with active line highlighting and 1-tap seek to timestamp.
- **10-Band Equalizer & AudioFx Suite:** Integrated 10-Band `Equalizer`, `BassBoost`, 3D Spatial Audio `Virtualizer` (headset 3D surround effect), and `LoudnessEnhancer` audio normalization.
- **Mood Radio Builder & Quick Mix:** Generates custom radio queues based on track + selected mood (*Chill*, *Workout*, *Party*, *Focus*).
- **Sleep Timer with Volume Fade-Out:** Configurable sleep timer (`MediaSleepTimer`) with smooth 30s linear volume fade-out before pausing.
- **Ambient Artwork Palette Glow:** Dynamic background lighting glow derived from album artwork colors.

### Shizuku Auto-Start & System Automation
- **Bypass `FLAG_STOPPED` (Force-Stop Bypass):** Multi-stage background wake-up engine (ContentProvider ping, Foreground Broadcast, Service Poke, and Fallback Silent Activity launch) to automatically wake Shizuku on boot without PC/ADB.
- **Conditional Installation Handling:** Checks if Shizuku is installed. If missing, greys out the setting row and redirects to Telos Store pre-filtered to `"Shizuku"` with 1 tap.
- **Shizuku Diagnostic Dialog:** Live OS compatibility checklist, auto-grant button, and step-by-step background battery optimization guide.

### Telos Store & App Control Hub
- **App Control Hub for All Installed Apps:** Tapping any installed app in the "Εφαρμογές" tab opens its **App Details** page (Inure Analytics, Telos Firewall for Wi-Fi/Mobile Data, Root Component Blocker, Audio Focus Mixer, and Freeze policy).
- **Allow Version Downgrades (`-d` flag):** Toggle in Store Settings to allow installing updates even if package version code is lower than installed.

### Dialer, Calculator & UI Fixes
- **Responsive Dialer Layout:** Adaptive key sizing and font scale (`digitSp`, `sublabelSp`) in landscape and portrait for accessibility fonts without key clipping.
- **Contact Deletion Fix:** Uses `ContactsContract.Contacts.CONTENT_URI` to delete contacts permanently regardless of default dialer status.
- **YouTube Login Browser Fix:** Excludes `com.android.vending` (Play Store) from Custom Tabs to open your actual web browser (`https://music.youtube.com/`) directly.

### Themes, Documentation & Badges
- **Default Theme:** Set **"Colorful Grid"** as default launcher theme with transparencies disabled by default. Renamed "Telos" theme to **"Telos theme beta"**.
- **Documentation Badges & Banner:** Added 1-click **Obtainium Import Badge** (`badge_obtainium.svg`), **Get it on GitHub Badge** (`badge_github.svg`), and top **Keep Android Open** banner (`keepandroidopen.org`).
- **Complete Translations:** 100% complete localized Greek translations across all 1,762 string keys (`values-el/strings.xml`).

## 1.41.13

### Files
- Free and total space on internal storage, SD/USB and remote volumes that report a quota (Dropbox, Drive, OneDrive, WebDAV, SFTP, SMB when available).
- ISO 9660 / Joliet disc images open as folders. Unencrypted zip can be edited and saved inside the archive.
- Camera RAW (embedded JPEG) and HEIC/HEIF stills, HEVC/H.265 video, share one preview catalog with Telos Viewer.
- SMB by computer name, `.local`, IP or `smb://host/share`; nearby mDNS servers; share listing when the share name is empty.
- Long-press peek, picture filmstrip, disk-cached thumbnails. Tabs, two panes, transfer queue and batch rename.

### Viewer
- Immersive photo viewer with glass chrome, PDF text highlight, comics (CBZ/CBR/CBT/CB7), SVG/PSD/AI/CDR previews.

### Phone
- Full-bleed incoming contact photo, swipe-up Remind / Reject+SMS.
- Call recording: VOICE_CALL first, silence retry, Shizuku/root CAPTURE_AUDIO_OUTPUT.
- Call blocker (prefix, regex, outgoing).

### Media
- Listen / Watch / Live destinations, brightness and volume swipes, TV and radio live, blur theme.

### Store and appearance
- Glass Store cards, wrapping names, Play Store tab, AMOLED accent palettes.
- Magnifier and flashlight/morse/siren virtual apps (1.41.12).
