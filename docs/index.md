---
layout: home

hero:
  name: 'Telos'
  text: 'Search first. Own your phone.'
  tagline: A free and open source Android launcher built around fast search, with a family of privacy-minded Telos apps.
  image:
    src: /icon.png
    alt: Telos
  actions:
    - theme: brand
      text: Telos apps overview
      link: /docs/user-guide/telos/
    - theme: alt
      text: Launcher features
      link: /docs/user-guide/telos/launcher/
    - theme: alt
      text: User guide
      link: /docs/user-guide/

features:
  - icon: 🔎
    title: Search-first launcher
    details: Apps, contacts, files, calendar, calculator, unit converter, web and more in one fast, customizable search.
    link: /docs/user-guide/telos/launcher/
  - icon: 📞
    title: Telos Phone
    details: Contacts, calls and an encrypted block list, with recoverable contact-detail loading.
    link: /docs/user-guide/telos/phone/
  - icon: 💬
    title: Messages
    details: Read and send messages from a simple, focused app.
    link: /docs/user-guide/telos/messages/
  - icon: 📁
    title: Files
    details: Local, USB and remote space, ISO and zip folders, RAW/HEIC previews, SMB by name or IP.
    link: /docs/user-guide/telos/files/
  - icon: 📸
    title: Screenshot
    details: Full, partial and scrolling screenshots, with crop, pixelate, blur and drawing.
    link: /docs/user-guide/telos/screenshot/
  - icon: 🎥
    title: Screen Recorder
    details: Record the screen to a video with the microphone, pause and a countdown.
    link: /docs/user-guide/telos/screen-recorder/
  - icon: 🎙️
    title: Voice Recorder
    details: Voice recordings with a list, search, pause and the call recordings of Telos Phone.
    link: /docs/user-guide/telos/voice-recorder/
  - icon: 📷
    title: Camera
    details: Telos camera, RAW, discreet capture and an IP webcam for OBS.
    link: /docs/user-guide/telos/camera/
  - icon: 📝
    title: Notes
    details: Notes with labels, sync with a Markdown folder or Nextcloud, import from Google Keep and Evernote.
    link: /docs/user-guide/telos/notes/
  - icon: 📅
    title: Calendar
    details: Month and agenda on your calendars, a local calendar and Google or CalDAV through the system.
    link: /docs/user-guide/telos/calendar/
  - icon: ⬇️
    title: Downloads
    details: Download manager with several connections per file, resume, a queue and the folder of your choice.
    link: /docs/user-guide/telos/downloads/
  - icon: 🧮
    title: Calculator
    details: Standard and scientific calculator with VAT, unit and currency conversion and a history.
    link: /docs/user-guide/telos/calculator/
  - icon: 🖼️
    title: Viewer
    details: Photos, PDF highlight, comics, SVG/PSD/AI/CDR previews, Office documents.
    link: /docs/user-guide/telos/photos/
  - icon: 🎵
    title: Music
    details: Local music with one mini player, lyrics and home-screen playback controls.
    link: /docs/user-guide/telos/music/
  - icon: 🎬
    title: Video
    details: Local and remote video with brightness, volume and stop-and-back controls.
    link: /docs/user-guide/telos/video/
  - icon: 📻
    title: Radio
    details: Internet radio with one now-playing bar and home-screen station/track controls.
    link: /docs/user-guide/telos/radio/
  - icon: 🛍️
    title: Store
    details: Discover and update apps, including opt-in frozen-app updates through authorized backends.
    link: /docs/user-guide/telos/store/
  - icon: ⚙️
    title: System
    details: Device stats, saved Wi-Fi, AppOps, and a built-in Shizuku server with wireless pairing or a one-line adb command.
    link: /docs/user-guide/telos/system/
  - icon: ❄️
    title: Smart Freeze
    details: Freeze apps, launch them on demand, and temporarily unfreeze/restore saved groups for updates.
    link: /docs/user-guide/telos/freeze/
  - icon: 🎬
    title: Media
    details: Listen, Watch and Live in one app — local files, YouTube, TV, Stremio Fork (deep links, calendar, continue watching), NextLib decoders.
    link: /docs/user-guide/telos/media/
  - icon: ✉️
    title: Ermis
    details: IMAP/SMTP mail with TLS, conversations, drafts and optional Google/microG consent.
    link: /docs/user-guide/telos/ermis/
  - icon: 📦
    title: TelosAndroidVm
    details: BlissOS in headless QEMU + scrcpy on Windows, Linux (KVM) and Apple Silicon. Encrypted guest disk survives opt-in ISO updates.
    link: /docs/user-guide/telos/android-vm
  - icon: 🛡️
    title: Privacy and crash guard
    details: No tracking by default, and a crash guard that keeps the launcher usable if something goes wrong.
    link: /docs/user-guide/
---

<script setup>
  import { withBase } from 'vitepress'
  import Footer from '.vitepress/theme/Footer.vue'
  const shots = [
  {
    "src": "/img/telos-development-01-launcher.webp",
    "caption": "Launcher — home clock and search"
  },
  {
    "src": "/img/telos-development-02-media-listen.webp",
    "caption": "Media — local music"
  },
  {
    "src": "/img/telos-development-03-youtube-music.webp",
    "caption": "YouTube Music"
  },
  {
    "src": "/img/telos-development-04-youtube-video.webp",
    "caption": "YouTube video"
  },
  {
    "src": "/img/telos-development-05-stremio-fork.webp",
    "caption": "Stremio Fork — account sign-in"
  },
  {
    "src": "/img/telos-development-06-files.webp",
    "caption": "Files"
  },
  {
    "src": "/img/telos-development-07-store.webp",
    "caption": "Store"
  },
  {
    "src": "/img/telos-development-08-viewer.webp",
    "caption": "Viewer"
  },
  {
    "src": "/img/telos-development-09-notes.webp",
    "caption": "Notes"
  },
  {
    "src": "/img/telos-development-10-calendar.webp",
    "caption": "Calendar"
  },
  {
    "src": "/img/telos-development-11-calculator.webp",
    "caption": "Calculator"
  },
  {
    "src": "/img/telos-development-12-downloads.webp",
    "caption": "Downloads"
  },
  {
    "src": "/img/telos-development-13-phone.webp",
    "caption": "Phone"
  },
  {
    "src": "/img/telos-development-14-messages.webp",
    "caption": "Messages"
  },
  {
    "src": "/img/telos-development-15-radio.webp",
    "caption": "Radio"
  },
  {
    "src": "/img/telos-development-16-tv.webp",
    "caption": "TV"
  },
  {
    "src": "/img/telos-development-17-video.webp",
    "caption": "Local video"
  },
  {
    "src": "/img/telos-development-18-clock.webp",
    "caption": "Clock"
  },
  {
    "src": "/img/telos-development-19-ermis.webp",
    "caption": "Ermis — mail account setup"
  },
  {
    "src": "/img/telos-development-20-akou.webp",
    "caption": "Akou — music recognition"
  },
  {
    "src": "/img/telos-development-21-network.webp",
    "caption": "Network"
  },
  {
    "src": "/img/telos-development-22-system.webp",
    "caption": "System"
  },
  {
    "src": "/img/telos-development-23-trash.webp",
    "caption": "Trash"
  },
  {
    "src": "/img/telos-development-24-camera.webp",
    "caption": "Camera"
  },
  {
    "src": "/img/telos-development-25-magnifier.webp",
    "caption": "Magnifier"
  },
  {
    "src": "/img/telos-development-26-flashlight.webp",
    "caption": "Flashlight"
  },
  {
    "src": "/img/telos-development-27-voice-recorder.webp",
    "caption": "Voice recorder"
  },
  {
    "src": "/img/telos-development-28-screen-recorder.webp",
    "caption": "Screen recorder"
  },
  {
    "src": "/img/telos-development-29-screenshot.webp",
    "caption": "Screenshot"
  },
  {
    "src": "/img/telos-development-30-settings.webp",
    "caption": "Settings"
  }
]
</script>
<div class="telos-note">
  <strong>Development preview · Pixel 7 emulator · Android API 35</strong><br />
  30 captures · English UI · dark wallpaper and shared blur · clean debug build, without personal accounts.
  Πραγματικές λήψεις από καθαρό development build σε emulator, χωρίς προσωπικούς λογαριασμούς.
  <a :href="withBase('/docs/user-guide/telos/screenshots')">All screenshots / Όλα τα screenshots</a>
</div>
<div class="home-screenshots">
  <figure v-for="shot in shots" :key="shot.src">
    <a :href="withBase(shot.src)"><img :src="withBase(shot.src)" :alt="shot.caption" loading="lazy" width="1080" height="2400" /></a>
    <figcaption>{{ shot.caption }}</figcaption>
  </figure>
</div>
<div class="telos-story">

## Latest improvements

- **Media:** one mini player per Music/Radio page, home-screen metadata and controls for built-in
  playback, and separate Music, Radio, TV and YouTube notifications. Video, Music, Radio, TV and
  YouTube share Media3 plus official NextLib FFmpeg decoders; playback stays in Telos. Viewer
  (photos and documents) stays built in.
- **Video, TV and Stremio Fork:** brightness-left and volume-right sliders with icons; a top-left
  stop-and-back button. TV keeps a separate minimize action.
- **Freeze Manager:** consistent snowflake/grayscale and icon packs, verified Disable/Suspend
  transitions, and actual-state scans with Unfreeze all / Freeze again for saved user/system groups.
- **TelosAndroidVm:** portable BlissOS guest (QEMU + scrcpy) on Windows, Linux KVM, and Apple Silicon, with opt-in ISO updates that keep the encrypted data disk. [Guide](/docs/user-guide/telos/android-vm).
- **Store:** opt-in frozen-app updates with durable restoration and streaming Shizuku/root installs.
  Published source artwork is shown for uninstalled apps, and full installer failures are readable.
  Plain Device Admin is insufficient; Device Owner supports only states it can restore.

Details: [Media](/docs/user-guide/telos/media/), [Store](/docs/user-guide/telos/store/),
[Freeze](/docs/user-guide/telos/freeze/backends-profiles).

## The story behind Telos

Telos started as a personal project, and I want to be upfront about how it was made: **I built it with zero coding knowledge.** All of the development was done with AI assistants — [ChatGPT](https://chatgpt.com), [Claude](https://claude.ai), Grok and Gemini. I couldn't have written this software by hand. What I could do was describe what I wanted, test each build on my own phone, and keep iterating with the AI until it worked. In that sense Telos is as much an experiment in AI-assisted development as it is a launcher.

It all comes down to one missing feature. I was already a happy user of [Kvaesitso](https://github.com/MM2-0/Kvaesitso), which I think is an excellent launcher. But a few things I really wanted weren't there — above all **freezing apps and launching frozen apps straight from the launcher.** For me that is the single most important feature, for two reasons:

- **Battery.** A frozen app can't wake up, run in the background, sync or drain power. Freeze the apps you only open once in a while and the battery lasts noticeably longer.
- **Privacy.** A frozen app can't phone home, track you, or quietly listen in the background. (You know the feeling — you talk about something near your phone and suddenly see an ad for it? A frozen app simply isn't running. *Are you listening, Facebook?*) Being able to freeze an app and still open it on demand, right from the home screen, gives you the convenience without leaving it running all day.

Once that worked, I kept going. I started adding the things I use every day — a dialer, messaging, a file manager, a gallery, music, video, radio, downloads, a firewall and more — not as separate apps you install, but as **virtual apps built into the launcher itself.** Little by little the project grew, and at some point it stopped feeling like "a launcher with a few extras" and started feeling like something closer to a small **operating system on top of Android**: one place that does most of what I need a phone to do, built around my own priorities of speed, battery and privacy.

Telos is still a personal fork and a work in progress, shaped by what one person actually wanted from their phone. If any of that resonates with you, you're welcome to use it.


</div>

<p class="telos-note">Telos is a fork of <a href="https://github.com/MM2-0/Kvaesitso">Kvaesitso</a> by MM2-0 and its contributors, released under the GPL-3.0.</p>
<Footer></Footer>


### Launcher appearance and installation controls

Store offers a selectable Android-session, Shizuku, root or installed external APK installer.
The dock keeps frozen apps' normal icons. Drawer opacity and blur are adjustable with a soft-glass
preset for fresh installs and full-width tablet layout. Wallpaper settings also accept animated
GIFs through the live-wallpaper preview, subject to the documented size limits.

Built-in apps use localized names without a Telos prefix, while `telos` and English/Greek feature
names remain search aliases. Their own artwork is retained in the launcher, Store and Recents.

The tablet drawer offers a full-width switch in Settings → Icons → App drawer; columns adapt to available width unless overridden.

Frozen application targets assigned to icon shutters and folder children use the same app-lock and verified thaw-before-launch path as drawer and dock icons. An authorized freeze backend is required to thaw an app.

Store cards wrap full app names and separate actions from the title for large system fonts. Tabs scroll horizontally instead of squeezing labels. Tap a tracked or recommended card to visit the project or app listing (not the release); Details retains per-app management. Direct APK sources without a known project resolve to the hosting site's home page. This uses source addresses only, without AI or generated descriptions. Shizuku does not bypass Android update-signature validation.

Dock and shutter app pickers show applications only and start a fresh search on every opening. App picker names wrap at large font sizes, overflow action menus scroll, and Telos Media reserves the navigation-bar inset even when no mini player is visible. Other general item pickers retain their supported item types.

Transfer network rules independently block Wi-Fi, mobile data, roaming and metered connections. Downloader has its own profile; torrents, Video and Stremio share a profile because the torrent session is shared. Downloader queues pause and resume as connectivity changes, torrent sessions pause on disallowed networks, and online video data sources check the policy before opening and reading. The player also stops loading on a blocked connection and prepares again when permitted. Existing Downloader Wi-Fi-only preferences and the mirrored Video Wi-Fi-only preference provide initial defaults. VPNs without an identifiable underlying transport are blocked when transport restrictions are active.
