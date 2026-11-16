# Telos Media

![Telos Media](https://raw.githubusercontent.com/koukomin/Telos-Launcher/main/docs/public/img/telos-development-02-media-listen.webp)


Telos Media combines local music, radio, YouTube Music, local video, YouTube, TV and Stremio Fork in one app.

## Switching spaces

Three destinations sit in the bottom bar: **Listen** (Music and YouTube Music), **Watch** (local Video, YouTube and Stremio Fork) and **Live** (Radio and TV). Chips under the top bar pick the source inside a destination. The last space you used is remembered (it is part of the launcher settings backup). The three older apps still exist and open Telos Media on their space, so existing shortcuts and search results keep working. A space whose app is hidden in the Store disappears from the chips.

## Shared mini player

YouTube and YouTube Music use a shared native background player. Open the person / Account control to sign in on the official websites in the browser ([browser account panel](youtube-account.md)). That does not import the account into the native player. microG is not used here. Video and series posters load from TVMaze or iTunes when no TMDB key is set. The hub shows one mini player. System Back leaves the current Media page (album, series, now playing) before closing the app.

A single mini player sits above the bottom bar on every space except Video and Stremio Fork (those
use the full-screen player). It shows the title and artist (music), the station (radio) or the
channel (TV), artwork and playback controls. Tapping it opens the matching player. Vertical swipe
on the video and TV players: left half brightness, right half volume, with on-screen level bars.

The home-screen music area also recognizes built-in Telos playback, using the same notification
access and display settings as external players. Explicit player exclusions still apply. Playing
sessions take priority over newer paused notifications, and Music/Radio session taps open the
correct section. Each built-in media service has its own notification ID.

## Playback engine

Music, Radio, YouTube, YouTube Music, local Video, TV and Stremio streams use **Media3 / ExoPlayer**
inside Telos. Official **NextLib** (`io.github.anilbeesetti:nextlib-media3ext` on Maven Central) adds
FFmpeg software decoders (AC3, E-AC3, DTS, TrueHD and more) when the phone has no hardware decoder.
There is no separate player APK. Photos and documents stay in [Telos Viewer](../photos/). Video formats include
`mp4 mkv webm avi mov 3gp m4v ts flv wmv mpg mpeg m2ts mts vob ogv asf hevc h265 hvc hvc1`. Audio formats include
`mp3 m4a aac ogg oga opus flac wav wma amr mid midi aiff aif ape mka ac3`. Extra codecs (AC3, DTS, TrueHD, HEVC
when the SoC has no hardware decoder) come from NextLib.

## What is kept when you switch

Search text, the open tab and the library state of each space are kept; an open album or series page is not.

## Music, radio and video at the same time

Starting a video pauses music and radio; the paused music keeps its track and position and radio reconnects to the same station.
In the video player options, **Resume music or radio after the video** (off by default) resumes them automatically when you leave
the video. The paused state expires after 30 minutes. Music and radio also pause each other.

## Share

Long-press an item to **Share** it: songs (the audio file), albums and artists (a title list), radio stations (see below), videos
(a copy of the file, with a confirmation above 50 MB, or the link or magnet of a web video). Contacts (vCard), notes (text or a
Markdown file), files from Telos Files (network and cloud files are downloaded first, with a warning above 50 MB; vault files cannot be
shared), documents in the viewer, and installed apps (an APK, or an `.apks` archive for split apps) have Share in their long-press or
overflow menus too. Files from Files, Viewer and Media use the same private share cache and temporary read grants.

**Radio stations:** the shared text has the name, the address and a `telos-radio://add` link. If the receiver has Telos, opening it
asks "Add station X to Telos Radio?" with the name and address editable. Nothing is played or fetched before the receiver confirms,
only http and https addresses without credentials are accepted. Telos Radio also accepts shared stream and playlist links
(`.m3u`, `.pls`, `.mp3`, `.aac`, `icy://`). Telos Video accepts web video links, magnets and `.torrent` files shared to it.

## TV

The TV space plays live channels from the open [iptv-org](https://github.com/iptv-org/api) list (public domain data; the streams belong to third parties, and availability and legality depend on your country).

- **First use:** a disclaimer card. Nothing is downloaded until you accept it, and no network access happens before you open TV.
- **Catalog:** channel list, logos and stream addresses are downloaded from `iptv-org.github.io` when you open TV (cached on the device, checked for changes, refreshed in the background when older than 24 hours; **Refresh** forces it). Adult channels, closed channels and blocklisted ones are left out.
- **Country and language:** your home country and the app language are preselected; **Countries** lets you pick several (flags, channel counts, search) and filter by language. Category chips filter the list; search is Greek and Greeklish aware.
- **Favorites, recents, your channels:** the star on a card adds a favorite (shelf at the top, **Move up/down** in the long-press menu), recents are kept, and **Add channel** creates your own (name, http or https stream address, optional logo and group). **Import M3U playlist** (up to 2 MB and 5000 entries) adds many at once. **Export / Import TV channels** saves a JSON backup file.
- **Player:** tap a card for the full player; tap the picture for the controls, swipe left or right to change channel, the top-left back button stops playback, while minimize (or Android system Back) keeps it playing in the mini player.
- **Self-healing streams:** if a stream fails or is not ready within 8 seconds, Telos tries the next stream of the same channel; if all fail it checks online whether the stream list was updated and retries with the new addresses before showing an error ("Looking for another stream…" is shown meanwhile).
- **Search:** Settings > Search > **TV channels** (off by default) shows favorite, custom and recent channels; TV and Radio results carry a type chip.
- **Extra Greek sources (optional):** when Greece is among the selected countries, two public playlists ([Free-TV/IPTV](https://github.com/Free-TV/IPTV) Greece and [greektvm3u](https://github.com/filipposfilippides/greektvm3u)) add more streams to the same channels (tried after the iptv-org streams when one fails) and a few extra channels, marked **Extra**. They and the **Programme guide** have switches in the Countries sheet (on by default, only shown with Greece selected). Everything is loaded on your device only while TV is open, from `raw.githubusercontent.com`, `ext.greektv.app` and `epgshare01.online`; nothing is bundled (the playlists have no license file), and if a source does not work nothing is shown for it.
- **What is on:** the programme guide (XMLTV, refreshed at most every 6 hours) adds a **Now:** line on the channel cards and, in the player, now and next with the times and a progress bar; tap the now line for the description. Without guide data nothing is shown.
- **Offline channels:** when a channel cannot play, even after trying all its streams and checking for an updated list, the player shows animated TV static (a frozen frame with reduced animations) with "This channel is temporarily offline", **Retry** and **Back**; channels whose streams all fail get a small frozen static on their tile.
- TV pauses music and radio when it starts. It contacts only `iptv-org.github.io` and the stream hosts.

## TV: favorites, background playback, reconnecting

- **Favorites first:** once you have favorites, the TV home shows them (a large **Continue watching** card, the favorites grid, then recently watched and your own channels). **Browse** has everything else (search, categories, countries, extra sources); the last view you used is remembered. Category names are shown in the app language.
- **Keep playing in the background:** in the TV overflow menu, **Playback** has the switch (off by default). Turning the screen off keeps the live stream playing (the video track stays selected so HLS/DASH is not torn down). Leaving the app while the screen is on pauses the channel unless this switch is on; with it on, playback continues after leaving the app, with a notification (logo, name, "Live TV", play/pause, stop), lock-screen and Bluetooth controls, and the video switched off while the app is in the background with the screen still on to save data; it stops after 5 minutes without playback. Android 13 and newer asks for the notification permission. The switch is part of the TV backup.
- **Reconnecting:** when the connection to a stream is lost, Telos Radio, web videos and TV keep trying until it is back (offline they just wait, online they retry after 1, 2, 4, 8 and then every 15 seconds), unless you press stop or pause. For TV, nothing is marked as broken and the offline static is not shown while the device is offline; the failover to other streams applies only while online.

## Telos Media destinations

The compact bar has Music, Radio, YouTube Music and Watch on the listen side, and Listen, Video, YouTube, TV and Stremio on the watch side. Each destination opens that space. Source chips in the toolbar still choose Music, YouTube Music, Video, YouTube, Stremio Fork, Radio or TV. Searching **TV**, **τηλεόραση** or Greeklish such as **tileorasi** opens Telos TV (the TV space), not Radio or the last Media space. Searching **Stremio** or **Stremio Fork** opens the **Stremio Fork** icon (Rondo disc) and the Stremio space, not TV or the last Media space. Hiding the older Telos Music, Radio, Video or TV icons in the Store only removes those chips.

## Notification controls

Music, radio, TV and video each show a media notification while they play (also on the lock screen and in the system media controls, e.g. the media output on newer Android versions) with play/pause and previous/next (songs, stations, channels, episodes). Leaving the video screen pauses the video; its notification then lets you continue with sound only. Android 13 and newer asks for the notification permission.

## Stremio Fork

The **Stremio Fork** space is Telos' own client for [Stremio add-ons](https://github.com/Stremio/stremio-addon-sdk). It was written
from the public description of the add-on protocol (MIT licensed); no code of the Stremio apps or of other Stremio clients
is used. **Telos is not affiliated with Stremio and this is not the Stremio app.**

::: warning What Telos does and does not do
Telos ships **no add-on** and hosts **no content**. An add-on is a small web service run by someone else; it decides which
catalogs and streams you see, and Telos does not check them. You are responsible for what you add and watch, and for having the
right to watch it where you live. Some add-ons use BitTorrent, which shows your IP address to the other users of the torrent
(the add-on list and the stream list say so). The first time you sign in, it shows a notice that you must accept.
Streams of the torrent kind go through the same torrent player as Telos Video, so its Wi-Fi only setting and its VPN route apply.
:::

- **Add-ons:** the puzzle-piece button in the toolbar (also **Add-ons** in the menu) opens the list after the first notice,
  with or without a Stremio account. Paste an add-on address (`https://…/manifest.json`) or a `stremio://` link and tap
  **Add add-on**. Opening such a link from the browser offers **Stremio Fork** and installs it. **Browse add-ons** lists
  community add-ons from installed `addon_catalog` directories and Stremio's public list. A one-tap suggestion adds Cinemeta.
- **Discover:** movies or series, mixed with **Popular / New / Trending / Featured**, optional **Trakt** catalogs, **year**
  and **genre** (genre and year are sent together when the add-on catalog accepts both extras).
- **Home:** one row of posters for every catalog of every add-on (catalogs that need extra input are left out), with a search
  field that asks all searchable catalogs. **Continue watching** shows what you played last, with a progress bar and remaining time; tapping it opens the title and the stream list for that episode.
- **Calendar / History:** upcoming catalog episodes for saved series, plus local watch history. Optional notifications for new library episodes can be turned off in Stremio Fork settings.
- **`stremio://` links:** add-on manifests still install; `stremio:///detail/{type}/{id}` opens that title. Add-ons with a configuration page show **Configure** in the add-on list.
- **Streams:** besides Play, a stream can **Open with** another player (http(s) or magnet) or be sent to Telos Downloads. The overflow menu reports whether the shared streaming server at `127.0.0.1:11470` answers. Without a Stremio account, named **Profiles** keep separate local libraries.
- **Detail page:** artwork, year, runtime, rating, genres, description and cast. A movie has **Play**; a series has seasons
  and episodes. **Save** puts the title in your **Library** tab (stored on this phone only).
- **Streams:** tapping Play or an episode asks all stream add-ons and lists what they offer, with the stream you used last for
  that title first. Web streams play in Telos Video's player (with its resume, picture-in-picture and notification controls),
  torrents open as magnet links in the same player, YouTube and web page links open in the matching app. Other kinds (Usenet,
  archives) are shown as not supported.

- **Subtitles:** subtitles that a stream carries, and those of subtitle add-ons (asked for a few seconds, with the file name of the
  stream), are handed to the player; pick them in the player's subtitle menu next to the ones of Telos Video's own subtitle search.
- **File inside a torrent (`fileIdx`):** a torrent can hold many files, for example a whole season. When an add-on names the
  file of the episode (its index in the torrent), that file plays; without it, the biggest video of the torrent plays.
- **Directory of add-ons:** **Browse add-ons** (in the add-ons sheet) lists add-ons offered by the directories of your installed
  add-ons (the `addon_catalog` part of the protocol) and by Stremio's public list of community add-ons (an address that
  Stremio can change or remove; then the list is shorter). They are made by others and are not checked by Telos or Stremio.
- **Stremio account:** opening this space while signed out shows an explanation of Stremio Fork and the sign-in form immediately. Sign in with your Stremio account to keep your **add-ons, library and continue watching** in
  step with it, the way the Stremio apps back them up. Telos uses the same account service as the Stremio apps
  (`api.strem.io`), documented in Stremio's open-source core (MIT). Your password is sent once to Stremio and never stored; only
  the session key is kept, encrypted with the Android Keystore. Add-ons and library items are merged (nothing of the account is
  deleted by signing in), and changes made here are sent to the account; **Sync now** repeats the merge. This is **unofficial**:
  Stremio can change or block the service, and we have not been able to check its terms of use. If you want to stay clear of that,
  you can use the other Telos Media spaces without signing in.

Use **Stremio account** in the toolbar to sync or **Sign out**. Signing out returns to the sign-in form and clears Stremio watched indicators. **Trakt** in the toolbar opens the same account settings used by Telos Video.

Movies display **Watched · Stremio**, **Watched · Trakt**, or both sources on posters and detail pages. Episode rows show the corresponding marks. Stremio episode marks follow its [watched-bitfield format](https://github.com/Stremio/stremio-watched-bitfield); merely starting playback is not counted as watched. Trakt marks match IMDb IDs and episode season/number, and work even when scrobbling is disabled. History refreshes on returning to this space and through **Sync now**. Trakt marks require a connected account and a catalog that supplies IMDb IDs.

What is not there: a bundled copy of Stremio's streaming server binary (Telos probes a local server if you run one, and torrents use the torrent engine of Telos Video) and the MPV player. Continue watching uses the stored player offset. Posters and chips are focusable for a D-pad. The local add-on list, library and history are not part of the Telos backup.

### Greek supplemental channels / Πρόσθετα ελληνικά κανάλια

The supplemental greektvm3u source uses its current `main` branch. TV Refresh checks Free-TV and greektvm3u alongside the main catalog; temporarily offline extra channels remain listed for later retry. Sources are fetched on the device and their live availability or geographic restrictions still apply.

Η «Ανανέωση» ελέγχει και τις πρόσθετες λίστες Free-TV και greektvm3u. Τα πρόσθετα κανάλια που είναι προσωρινά εκτός σύνδεσης παραμένουν στη λίστα για νέα δοκιμή. Η διαθεσιμότητα και τυχόν γεωγραφικοί περιορισμοί εξαρτώνται από την πηγή.


## YouTube και YouTube Music

Η Ακρόαση περιλαμβάνει μουσική συσκευής, ραδιόφωνο και YouTube Music. Τα Βίντεο περιλαμβάνουν την τοπική βιβλιοθήκη και το YouTube. Τηλεόραση και Stremio Fork παραμένουν ξεχωριστές επιλογές. Κάτω από το κεντρικό pill επιλέγετε πηγή και βλέπετε καθαρά την ενεργή ενότητα.

Στο YouTube μπορείτε να αναζητήσετε ή να επικολλήσετε σύνδεσμο βίντεο. Οι δύο ενότητες μοιράζονται player και χειριστήρια ειδοποίησης/οθόνης κλειδώματος. Η αναπαραγωγή συνεχίζεται στο παρασκήνιο. Η σύνδεση λογαριασμού βρίσκεται ακόμη υπό ανάπτυξη· προς το παρόν η native αναζήτηση δεν απαιτεί λογαριασμό.

Υλοποίηση βασισμένη σε επιλεγμένα GPL components του [Flow](https://github.com/Flow-Tube/Flow) και στο [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor). Η δυνατότητα εξαγωγής ροών εξαρτάται από τις αλλαγές του YouTube και δεν αντικαθιστά ολόκληρη την εφαρμογή Flow.

### YouTube playback

Choose YouTube Music under Listen or YouTube under Video. Search or paste a video link; both sources use one native Media3 session with background playback and notification controls. Account integration is still in development. The implementation adapts selected Flow components and uses NewPipeExtractor; it does not embed the whole Flow app.


## Κοινός κάδος Telos

Πριν από διαγραφή επιλέγετε μεταφορά στον κάδο ή οριστική διαγραφή. Τα τοπικά αρχεία μεταφέρονται ατομικά σε ιδιωτικό φάκελο του app στον ίδιο χώρο αποθήκευσης· αποτυχία μεταφοράς δεν διαγράφει το πρωτότυπο. Προεπιλεγμένη διατήρηση: 30 ημέρες, ρυθμιζόμενη σε 1–365 ημέρες για επόμενες διαγραφές. Οι ήδη αποθηκευμένες ημερομηνίες λήξης δεν αλλάζουν. Ο καθημερινός καθαρισμός αφαιρεί ληγμένα στοιχεία όταν το Android εκτελέσει την εργασία. Ο κάδος είναι διαθέσιμος από Files, Viewer και Media, με επαναφορά στην αρχική θέση χωρίς αντικατάσταση υπάρχοντος αρχείου.

Χωρίς δικαίωμα πρόσβασης στο τοπικό αρχείο, φωτογραφίες/βίντεο/ήχος μπορούν να χρησιμοποιήσουν τον κάδο του Android (Android 11+), με συναίνεση του συστήματος και σταθερή διατήρηση 30 ημερών. Εμφανίζονται έως 1.000 στοιχεία του συστήματος που επιτρέπουν τα τρέχοντα δικαιώματα πολυμέσων. Remote/root/archive/vault πηγές και providers που δεν επιτρέπουν ασφαλή μεταφορά δεν έχουν γενικό κάδο· η οριστική διαγραφή ζητά επιβεβαίωση. Ο ιδιωτικός κάδος αφαιρείται όταν απεγκατασταθεί το Telos ή διαγραφούν τα δεδομένα του app.

### Shared trash

Files, Viewer and Media share a delete/trash choice and restore UI. Local files use an atomic same-volume move with durable metadata, a default 30-day retention configurable to 1–365 days for future deletions, and daily expiry cleanup. Restore never overwrites an existing file. Android-managed media trash uses the system’s fixed 30-day retention and permission dialog. Unsupported storages offer explicitly confirmed permanent deletion. The private trash is removed on app uninstall or app-data deletion.

## Screen-off playback

TV, YouTube, music and the video player keep their media service running when the screen turns off. TV disables the video track while background audio continues, including with the older background-playback preference off; explicit pause/stop still works. Network playback has a wake lock while playing. Torrent startup uses non-null libtorrent flags and waits for asynchronous torrent registration. Screen-off behavior and battery restrictions still require testing on the target device.

## Compact navigation and TV controls

The source title and contextual actions share one toolbar. Open the source menu to change between Music, Radio, YouTube Music, Video, YouTube, TV and Stremio; reorder controls remain available there. Recording, sleep and file actions use labeled menus. Category tabs scroll at large font sizes; search fields grow with text.

TV changes channel with horizontal swipes (left: next; right: previous). Opening controls refreshes now/next, and tapping either programme opens its details. Greek playlists can populate the catalog even when the main catalog is unavailable. Enable the optional Greek playlists if needed; stream availability and geographical restrictions still depend on the broadcaster.

## Source-specific playback and compact controls

Music, YouTube Music and Radio retain separate last-played items. Opening one source never substitutes the last MP3 from another source. YouTube Music supports local favorites and named playlists. Artist-radio actions use related recommendations from a seed track; these are not an official account-synchronized artist station. The playback queue belongs to the playback service and survives closing the hub.

Scrollable headers and content reduce controls competing with media at large font sizes. Virtual-app pages share wallpaper-aware surfaces and keep player contrast controls where needed.


The TV mini player provides a contrasting play/pause icon reflecting the current playback state; tap it to pause or resume.


Tap anywhere on a TV or video picture, including the blank bands above and below a landscape picture in portrait, to toggle playback controls and Android system bars. TV continues audio playback when switching apps by default; a one-time upgrade enables this for legacy configurations. You can disable it in TV playback settings. Music, Radio, TV, YouTube and Video expose media-session controls in Android notifications; video notifications have separate IDs so they do not replace other players.


Stremio Fork → menu → Settings offers primary and secondary audio/subtitle languages, including None, plus a 1/2/5/10/20 GB HTTP playback cache. Primary None disables automatic subtitles; empty audio preferences leave the stream/player default. Secondary languages are fallbacks when available. The cache evicts older HTTP data automatically; settings take effect on the next playback. This is a per-player HTTP cache, not a total limit on temporary torrent files. The same settings sheet exposes shared torrent speed limits, connections, discovery, encryption, seeding and transfer-network rules. It does not claim full settings parity with the official Stremio client.


During playback, Subtitles opens a searchable sheet listing available subtitle languages/tracks and the current choice, with None, local file and online search actions.

Stremio Settings also offers opt-in next-episode playback. It continues only when catalog episode metadata and a compatible stream from the same add-on are available; otherwise it stops. Multi-file Stremio torrents no longer automatically run into unrelated files. Optional resolution, maximum source size and CAM/screener exclusion are metadata preferences: unknown sources remain available, and manual browsing restores the full list if all known sources would be filtered out. These filters do not change video encoding or force an unsupported playback mode.

### Compact controls and local previews

Use the search icon to expand or collapse media search. Radio stations use compact
rows with small logos. The TV player's list button opens searchable channel
selection without leaving playback. Viewer supports selecting several files and
then using Delete; the shared dialog offers a bin or permanent deletion where
supported. Local video frames are also used when a movie/series cover is absent
or fails to load. Formats or codecs unsupported by Android retain a file-type icon.

In Άκου, hide the recording information with “Do not show again”. The top-right
menu opens the information and its visibility switch again.

## Account and audio implementation limits

YouTube native session cookies are Keystore protected. Cached library data is bounded and scoped to the session; logout clears Telos credentials/library data and YouTube cookies without clearing unrelated Web app sessions. Embedded login and private playlist endpoints remain provider-dependent, and current extraction does not promise every continuation page.

Audio effects keep separate resources for live player sessions and release them with the player. Shared Telos effect settings apply across those sessions. A two-times amplitude boost is capped at about +6.02dB; native/vendor effect availability still varies. The unused extra service/mini player/timer additions were removed in favor of existing playback/session interfaces.

## Listening and watching

Listen groups local music, YouTube Music and radio. Watch groups local video, YouTube, TV and Stremio. Source chips and the source menu keep all seven sources reachable; the menu also reorders sources without resetting your saved order. Wide, tall windows use a navigation rail; compact or short windows use bottom navigation. Your Telos theme, wallpaper treatment and system font size remain in effect. Music emphasizes artwork shelves and track rows; watch sections use 16:9 thumbnails and growing metadata.

YouTube and YouTube Music load home content on entry and expose account playlists in both modes. Use Home to refresh after signing in. Artist radio uses a Music mix seeded by the selected song. Local libraries, favorites, playlists, imports, downloads, queues, player tools, radio recording/settings, TV sources/EPG and Stremio settings remain available through their existing actions. Live account/provider availability and playback still depend on YouTube responses.

Tap the active YouTube Music mini player to open its artwork player and queue. Seek, previous/next, pause and stop use the existing session; no second player is started. On video play, the watch page scrolls to the inline player. Account help distinguishes sign-in inside Telos from the separate browser session.

## Detail readability

Stremio film and series details cover the catalog with a readable theme surface. Play and Save wrap onto separate rows when space is limited. Settings and app backgrounds retain shared wallpaper blur when available and fall back to a solid theme background when blurred wallpaper pixels are unavailable.

## Compact navigation and search

Tap the source title and its arrow to open all Media spaces and Move up/down ordering. The top-right actions belong to the open space; there is no second hub overflow menu or separate actions row. Source chips scroll to the selected space automatically.

Music, local Video, Radio and YouTube/YouTube Music expose search through a toolbar icon; Stremio Home places it beside Home/Library. TV filters and Photos use an icon in their own panel. Opening the icon reveals the search field; closing it or using Back folds it away. Local filters clear when closed; YouTube keeps submitted results/query and uses the keyboard Search action or search button to submit. No search request is triggered just by opening the field.

Stremio poster images keep a stable width rather than growing with the system font size. Names and captions wrap below them, section headings use the theme’s title hierarchy, and Home/Library scroll horizontally when larger labels need space. All source/account/settings/player actions remain available.


Music, Radio and YouTube Music source buttons use labeled icons and filled rounded rectangles. Music library and online music filters share the compact button style. Radio keeps Collection and History buttons; its toolbar magnifier opens online station search. Collection filtering remains in the overflow menu. The radio transparent header retains the active theme tone rather than restoring transparent black.


Stremio Fork’s overflow menu exposes the HTTP cache budget directly: 1, 2, 5, 10 or 20 GB. This is the existing per-player-process LRU HTTP cache, not a total torrent quota. Another Stremio installation’s private cache is not opened or modified; Android storage isolation and different cache formats prevent safe direct sharing.

The shared Music/Radio/TV mini player uses a compact opaque tonal bar and plain playback glyphs, retaining artwork, progress, next/record/stop actions and the tap-to-open player.

## Stremio and Trakt playback synchronization

The player now records real millisecond positions and durations through the main-process bridge, including isolated playback. Library files are separated by Stremio account; anonymous or another account's entries are not automatically uploaded on login. Updates are saved atomically off Main and pending progress is retried on synchronization. A launch attempt is no longer treated as a watched title. Watch evidence uses the Stremio core's actual-watch-time threshold (70%), separately from its credits/resume threshold (90%); setting one episode preserves other watched bits. Account-isolation, failed-write and playback-state regression tests passed. Trakt also refreshes exact-ID movie/show watchlists, watched episode identities and playback positions at login; actual player updates are queued per session. Delayed events preserve newer remote watched/progress state. Live Stremio/Trakt account interoperability has not been verified.

### Shared Stremio cache

The cache settings sheet now includes **Use shared Stremio cache** and a streaming-server address (default `http://127.0.0.1:11470/`). Connect to read the server’s existing cache folder. The Apply action changes that server’s cache size and optional folder, preserving its other torrent settings and verifying the result. The folder is a path on that server, not an Android document-picker URI.

When enabled, torrent playback uses the official Stremio server’s hash/file-index route, including tracker hints and filename filters. Both clients therefore use the same torrent storage/index/eviction engine. Telos skips its additional HTTP cache for that playback. Keep Stremio’s compatible streaming server running; unavailable/incompatible servers produce an error instead of silently creating a second torrent download. Automatic episode continuation retains the same transport. Ordinary HTTP streams still use the separately configured Telos HTTP cache.

A running compatible streaming server is required. Telos cannot read another app’s sandbox or reinterpret a Media3 cache index as Stremio torrent data. Stremio builds that do not expose this API cannot share their private cache through this option. Server network/download policies are owned by that server. The settings read/write protocol is covered by a local HTTP integration test. Interoperability with an installed Stremio streaming server has not been verified.

Delayed Trakt playback updates check remote watched history and newer resume timestamps before replaying. Completed offline updates retain their original watch time. Account credentials are initialized off the UI thread before choosing playback state. The integration is still undergoing final build and live-account validation.
