# Telos at a glance

Telos is a search-focused, free and open source launcher for Android. It is a fork of
[Kvaesitso](https://github.com/MM2-0/Kvaesitso) and keeps everything the original launcher does, then adds a
set of **built-in apps** that run inside the launcher: a phone app, messages, a file manager, a
gallery, music, video and radio players, an app store and an app freezer.

::: tip Two parts
- [**The launcher**](./launcher/) is search, home screen, widgets, icons and themes (inherited from Kvaesitso).
- **The Telos apps** are added on top. Each one has its own page below.
- [**Telos Desktop**](./linux-desktop) is a separate JVM shell (`:desktop-linux`), not in the APK — Linux session + Windows setup `.exe` / portable zip (macOS later).
- [**TelosAndroidVm**](./android-vm) is a portable BlissOS QEMU + scrcpy guest (Windows / Linux / Apple Silicon), not in the APK.
:::

## ✨ Built-in apps

<div class="telos-grid">

<a href="./phone/"><b>📞 Phone</b><span>Dialer, recents, contacts, dual SIM, call recording, screening, SIP</span></a>
<a href="./messages/"><b>💬 Messages</b><span>SMS and MMS conversations, default SMS app, scheduled messages</span></a>
<a href="./ermis/"><b>✉️ Ermis</b><span>Gmail-style email, IMAP/SMTP, notes on Gmail messages</span></a>
<a href="./files/"><b>📁 Files</b><span>Local, USB, network and cloud, space meters, ISO and zip, RAW/HEIC thumbs</span></a>
<a href="./screenshot/"><b>📸 Screenshot</b><span>Full, partial and scrolling screenshots with an editor</span></a>
<a href="./screen-recorder/"><b>🎥 Screen Recorder</b><span>Screen to video with microphone, pause and countdown</span></a>
<a href="./voice-recorder/"><b>🎙️ Voice Recorder</b><span>Recordings with search, pause and call recordings</span></a>
<a href="./camera/"><b>📷 Camera</b><span>Telos viewfinder, RAW, discreet capture, IP webcam</span></a>
<a href="./notes/"><b>📝 Notes</b><span>Notes with labels, Markdown folder and Nextcloud sync, Keep and Evernote import</span></a>
<a href="./calendar/"><b>📅 Calendar</b><span>Month and agenda on your calendars, local calendar, Google and CalDAV through the system</span></a>
<a href="./downloads/"><b>⬇️ Downloads</b><span>Download manager: several connections, resume, queue, folder of your choice</span></a>
<a href="./calculator/"><b>🧮 Calculator</b><span>Scientific calculator, VAT, unit and currency converter, history</span></a>
<a href="./photos/"><b>🖼️ Viewer</b><span>Gallery, immersive photos, PDF highlight, comics, SVG/PSD/AI/CDR, Office</span></a>
<a href="./music/"><b>🎵 Music</b><span>Local library, lyrics, scrobbling, tag editor, NextLib decoders</span></a>
<a href="./video/"><b>🎬 Video</b><span>Library, player, NextLib decoders, streams, torrents, subtitles, Trakt</span></a>
<a href="./radio/"><b>📻 Radio</b><span>Internet radio with station search, sleep timer, NextLib decoders</span></a>
<a href="./network/"><b>🛡️ Network</b><span>VPN based firewall and DNS filter: DNS servers, per-app rules, blocklists, WireGuard</span></a>
<a href="./help/"><b>❓ Help</b><span>In-app tour of the launcher, Telos apps and useful settings</span></a>
<a href="./store/"><b>🛍️ Store</b><span>Install and update apps from GitHub, F-Droid and more</span></a>
<a href="./system/"><b>⚙️ System</b><span>CPU, RAM, Wi-Fi, AppOps, built-in Shizuku server, pairing, ADB</span></a>
<a href="./freeze/"><b>❄️ Smart Freeze</b><span>Freeze or hide apps through Shizuku, Dhizuku, root, Island or device owner</span></a>
<a href="./media/"><b>🎬 Media</b><span>Listen / Watch / Live: music, radio, YouTube, TV, Stremio Fork (deep links, calendar)</span></a>
<a href="./linux-desktop"><b>🖥️ Telos Desktop</b><span>Linux session + Windows jar; vault as \$HOME on Linux</span></a>
<a href="./android-vm"><b>📦 TelosAndroidVm</b><span>BlissOS in QEMU + scrcpy on Windows, Linux, and Apple Silicon</span></a>

</div>

<style>
.telos-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; margin: 16px 0; }
.telos-grid a { display: flex; flex-direction: column; gap: 4px; padding: 14px 16px; border: 1px solid var(--vp-c-divider); border-radius: 12px; background: var(--vp-c-bg-soft); text-decoration: none; transition: border-color .2s; }
.telos-grid a:hover { border-color: var(--vp-c-brand-1); }
.telos-grid a b { color: var(--vp-c-text-1); font-size: 1.05em; }
.telos-grid a span { color: var(--vp-c-text-2); font-size: .9em; line-height: 1.4; }
</style>

| App | Purpose | Details |
| --- | --- | --- |
| Telos Phone | Calls, recents, contacts, call recording, call screening, SIP | [Phone](./phone/) |
| Telos Messages | Text and picture messages | [Messages](./messages/) |
| Ermis | Gmail-style email with IMAP/SMTP and notes on Gmail messages | [Ermis](./ermis/) |
| Telos Files | Local, USB and remote space, ISO/zip folders, RAW/HEIC thumbs, SMB by name or IP | [Files](./files/) |
| Telos Screenshot | Full, partial and scrolling screenshots, editor | [Screenshot](./screenshot/) |
| Telos Screen Recorder | Records the screen to a video | [Screen Recorder](./screen-recorder/) |
| Telos Voice Recorder | Voice recordings with a list and search | [Voice Recorder](./voice-recorder/) |
| Telos Camera | Telos camera, RAW, discreet capture, IP webcam | [Camera](./camera/) |
| Telos Notes | Notes with sync and import | [Notes](./notes/) |
| Telos Calendar | Calendar with local and synced calendars | [Calendar](./calendar/) |
| Telos Downloads | Download manager for links with resume and a queue | [Downloads](./downloads/) |
| Telos Calculator | Standard and scientific calculator, VAT, converters | [Calculator](./calculator/) |
| Telos Viewer | Photos, immersive viewer, PDF highlight, comics, SVG/PSD/AI/CDR, Office edit | [Viewer](./photos/) |
| Telos Media | Music, radio, video and TV in one app | [Media](./media/) |
| Telos Music | Player for the music on the phone | [Music](./music/) |
| Telos Video | Video library and player, streams and torrents | [Video](./video/) |
| Telos TV | Live TV channels (opens the TV space in Telos Media) | [Media / TV](./media/#tv) |
| Telos Radio | Internet radio | [Radio](./radio/) |
| Telos Network | Firewall and DNS filter through the VPN service, blocklists, WireGuard, logs (early version) | [Network](./network/) |
| Telos Store | Obtainium-like app installer and updater, manages the Telos apps | [Store](./store/) |
| Telos System | CPU, RAM, Wi-Fi, AppOps, built-in Shizuku server and pairing | [System](./system/) |
| Smart Freeze | Freeze or hide installed apps | [Smart Freeze](./freeze/) |

## Launcher and apps compared

| | The launcher | The Telos apps |
| --- | --- | --- |
| Origin | Kvaesitso | Added by Telos |
| Where it runs | Launcher process | Launcher process (Video can use its own process) |
| Can be switched off | No | Yes, from the [Store](./store/) (except the Store itself) |
| Guarded by the crash guard | n/a | Radio, Music, Video, Viewer |
| Docs | [The launcher](./launcher/) | One page per app |

## How the built-in apps work

The Telos apps are **virtual apps**. They are not separate APKs: their code is part of the launcher and
they run inside the launcher process (the video player can optionally run in a separate process, see
[Video](./video/)).

- **They look like normal apps.** Each one has an icon in the app grid and shows up in search.
- **They can be switched on and off.** Open [Telos Store](./store/) and "install" or "remove" an app.
  "Installing" shows its icon, "removing" hides it. Telos Store itself cannot be removed, otherwise
  there would be no way back.
- **Switched-off apps cost nothing.** For Radio, Music, Video and Viewer the services and screens are
  disabled, so they use no memory or CPU and are not offered in "Open with". Phone and Messages are
  never touched by this.
- **They appear in the share menu.** Other apps can hand text, pictures, videos, `sms:`, `tel:`,
  `magnet:` and `obtainium:` links to them. Each entry appears under its own name and icon, and only
  while that app is installed.
- **They are guarded against crashes.** Radio, Music, Video and Viewer are switched off automatically
  when they crash or hang twice within a day, with a notification that points to the Store. Installing
  the app again resets the counter. Phone and Messages are never switched off, because Android needs them.

| Share target | Accepts |
| --- | --- |
| Telos Messages | text, pictures, videos, `sms:` links |
| Telos Viewer | pictures |
| Telos Video | videos, magnet links, torrent files |
| Telos Store | `obtainium:` links |
| Telos Phone | `tel:` links |

## Where to find the settings

- Each app has a settings page that you reach from its own screen. The Phone settings use rounded
  cards with accent colored section captions.
- Launcher-wide settings (search, grids, icons, plugins, integrations) are in the launcher settings.
- Cloud and network storages are managed in **Settings > Integrations > Cloud and network storage**.

::: warning Honest status
Some features are new and not tested on every device. They are marked <Badge type="warning" text="experimental" /> or
<Badge type="info" text="untested" /> on the app pages. Please read the "Limitations" section of an app before relying on it.
:::

## Credits

Telos builds on Kvaesitso and on ideas and code from several open source projects. The full list with
licenses is in the project [readme](https://github.com/koukomin/Telos-Launcher#projects-telos-is-based-on).

## Flashlight: multilingual Morse

Enter text in any supported Telos language. Greek uses its radio Morse alphabet, including accented vowels and final sigma. Other scripts are romanized offline with AnyAscii into international Morse; the actual romanized text is shown before sending. This is **not** national Japanese Wabun, Korean SKATS, or the Chinese telegraph code. Romanization is character based and can differ from a word’s pronunciation. Unsupported symbols are reported; empty conversions cannot be sent. Messages are limited to 2,000 Unicode code points.

Select **Light**, **Sound**, or **Light and sound**. The light source selector chooses camera flash, screen, or both. Sound uses media volume and sustained keyed tones; dots last 100 ms and dashes 300 ms, with 100/300/700 ms symbol/letter/word gaps. SOS repeats until stopped. During screen signaling, the lit color alternates with black; tap anywhere to stop. The screen stays awake while transmitting. Stop, leaving the app, or changing output clears signals and restores brightness. Camera availability, torch permissions and media volume still depend on the device.
