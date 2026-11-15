# Virtual-app surfaces

New virtual apps include a Rondo icon set in the same change. See
[Rondo icons](./rondo-icons.md) and [ICON_DESIGN.md](/ICON_DESIGN.md).

VirtualAppTheme in app/ui/component/VirtualAppBlur.kt supplies wallpaper-aware backgrounds through the launcher theme for TelosAppActivity and embedded virtual routes. New routes should use this wrapper rather than add an opaque root surface. Preserve contrast on media/night-mode surfaces. Use scrolling content with fixed critical actions, wrap labels, and adapt to available width rather than reduce the user font scale.

Camera owns its CameraX/Camera2 capture pipeline. Ermis owns native IMAP/SMTP transport, encrypted storage and mail UI. The embedded terminal was removed. Official Termux is a Store recommendation and external keyboard-shortcut destination.

Ermis scopes a readable tonal palette inside the transparent virtual-app theme: cleared parent surface colors cannot be reused as mail-card or input backgrounds. Its root retains a small amount of wallpaper translucency; composer/reader pages use opaque surfaces. Android Email contact picking grants access only to the chosen data URI, read on Dispatchers.IO; it does not enumerate the full address book.

NextDNS is a route under the existing Network app, with its repository registered in networkModule. It uses the already pinned OkHttp dependency, cancellable bounded responses, fixed-origin requests and a separate Android Keystore vault in noBackupFilesDir. API credentials never appear in editor state or launcher preferences. The legacy Copilot standalone module is superseded rather than registered as a second virtual app. DNS selection remains an explicit operation on DnsController.

Ermis mailbox identity is (account, folder, UID), with UIDVALIDITY persisted per mailbox and legacy Inbox keys retained. MOVE fallback never expunges unrelated deleted messages. Forward references include source UIDVALIDITY and produce a bounded original-message attachment before SMTP. Passive HTML is sanitized with the already present Jsoup, stripped of active content/remote images, rendered with scripts/storage/file/content/network access disabled, and never persisted. Only a single message retains formatted HTML in memory. This local document component is not a webmail login or an external mail application.

ErmisCachePolicy also bounds all resident bodies together to 2,000,000 characters, retaining the selected body and at most 32,000 characters per other message. This avoids cumulative heap growth when many large messages are opened across folders; original messages remain on the server.

Ermis custom folder mutations validate full IMAP names, reject Inbox/control characters/wildcards and never overwrite existing targets. Rename rechecks server SPECIAL-USE metadata and refuses mailbox hierarchies; cached source rows and UIDVALIDITY are invalidated. Forward draft references follow the renamed leaf, retaining their independent UIDVALIDITY guard. Recent IMAP search uses server sender/subject/body terms within the existing 5,000-message/100-result bounds; the UI does not filter server matches using truncated local previews. Notification baselines include only Inbox messages, avoiding UID collisions with other folders.

SMTP completion preserves the original send result even if QUIT/disconnect fails; the send operation is never repeated automatically. Files FTP now uses a bounded passive-only session server in services:comms, rooted in SecureDirectoryStream handles with NOFOLLOW opens. Passwords are generated per session; upload opt-in uses CREATE_NEW to prohibit replacement. Foreground dialog lifecycle closes control/passive/data sockets. Integration tests use the already pinned Apache Commons Net FTP client as a test-only dependency.

Installed-app export uses a CreateDocument grant and performs package enumeration and bounded streaming copies on IO. InstalledApkExport preserves single APK bytes and writes every split to a ZIP using a 64 KiB buffer. Metadata is rechecked after export; failed or cancelled documents are deleted where supported. No bitmap list or whole APK buffer is retained. Communication JVM tests verify single APK identity and split bundle completeness.

Ermis messages can be marked unread from the message actions. The change is applied to the selected account, mailbox and UID on the IMAP server before updating the cache; unavailable messages cannot falsely appear updated.

## Shared cached frosted backdrop

FrostedGlassProvider in LauncherTheme is inherited by inline apps and shared across standalone Telos app surfaces. GlassWallpaperStore only processes explicitly selected home images, binds a <=512-pixel snapshot to WallpaperManager wallpaper ID, and rejects live/mismatched sources. Decode, three separable box passes and file IO run on IO; the service retains at most two ARGB frames (glass + optional secondary-page wallpaper radius), without recycling frames still used by Compose. Input is capped at 32 MiB / 100 MP. The thumbnail is atomically persisted and no source photo copy survives selection. API 26/27 EXIF orientation is handled explicitly.

FrostedGeometry defines one center-crop in window coordinates. Each surface subtracts its position in the window, clips to its own shape and draws the cached image plus exactly one requested tint, subtle highlight and border. Scrolling affects coordinates only, not decoding. Source capture and native window-blur calls were removed, along with the Haze runtime dependency. VirtualAppTheme prevents nested wrappers from adding a second full-window tint; explicit media/document content surfaces remain readable. Radius/color notifications are coalesced, and lifecycle resume/wallpaper colors revalidate the wallpaper ID. Pure JVM tests cover subsampling limits, blur invariants and portrait/landscape/split-window crop geometry. Real-device screenshot/performance validation remains outstanding.

The provider supplies pixels only; it never paints a full-screen layer over the home wallpaper. The existing wallpaper-blur preference is drawn only on secondary pages that do not own their glass backing, so enabling it does not blur the home wallpaper or add another layer under widget cards and inline apps.

Radius is converted from dp using the actual window-to-source center-crop scale, including portrait/landscape changes. Settings and Calendar avoid adding a second full-window backing; full-screen Ermis dialogs own exactly one shared backing in their own window. Photos Home/Both actions use the same snapshot writer as the wallpaper settings.

Material container roles are opaque tonal surfaces for inner controls and cards; only full-window background/surface roles are cleared. Existing wallpaper target/action messages are completed in all supported locales when reused from Photos.


Telos video wallpaper glass samples only the service’s own rendered surface with asynchronous PixelCopy, at most 192 pixels per edge and eight updates per second (slower than video playback). One readback can be in flight across engines; CPU blur runs on Default and shares up to four radius/viewport profiles across active Activity providers. No player rebuild, screen capture, permission prompt or extra decoder is needed. Sampling stops when the wallpaper is hidden, playback pauses for battery/thermal policy, or no started glass provider requests frames; failures clear to translucent tint. Preview engines never publish. Compose reads the shared frame state during drawing, avoiding full-page recomposition on each frame. Static wallpaper caching remains unchanged. Physical-device visual/performance verification remains outstanding.


The home clock, transparent resting search bar and dock page dots have no automatic rectangular contrast backing. Wallpaper-aware foreground selection still runs, while the search bar keeps its explicitly selected style and active surface. Other widget/app glass panels and their contrast policy are unchanged. This also removes the extra backing that appeared when wallpaper sampling was unavailable on Android 13 and newer.


## Cyber-Tech chrome (virtual apps)

Shared kit in `app/ui/.../component/TelosCyber.kt` and `TelosTechBorder.kt`:
`TelosCyber` neon accents (`#00E676` / `#00F0FF` / `#FF007F` / `#FF9100`),
`telosTechSurface` / `telosTechCard` glowing borders on opaque
`surfaceContainerLow`, `TelosTechPill` for tabs/filters, `TelosDotMatrixText`
and gauges for HUD/telemetry, plus `TelosCyberAppBarTitle` /
`TelosCyberHubTile` / `TelosCyberStatChip` for hub chrome.

Apply Cyber-Tech to virtual-app headers, status HUDs, hub tiles, filter pills
and mini-players. Prefer opaque Material containers under neon borders — do not
use transparent root `surface` as card fill or icon foreground. Do **not** wrap
full-bleed camera/video viewfinders, Ermis mail bodies, or dense Files list rows
in neon cards; keep chrome on toolbars/tabs and leave content planes readable.
Popups over glass still use `virtualAppPopup()`.

## TelosSurfaceTokens (shared Helix / Android chrome kit)

Formal role names live in `:ui-kmp` (`TelosSurfaceTokens`) and are mirrored on
Android in `app/ui/.../component/TelosSurfaceTokens.kt` (MaterialTheme accessors).
`:app:app` must not depend on `:desktop-linux`; both Helix and virtual apps import
`:ui-kmp` (or the Android Material bindings) only.

| Token | Role | Contract |
| --- | --- | --- |
| `panelGlass` | Root / top-panel glass | **Only** role that may carry alpha (wallpaper translucency). |
| `cardOpaque` | Cards under neon borders | Opaque `surfaceContainerLow` / shared card fill (`alpha = 1`). |
| `popupOpaque` | Menus, dialogs, Control Center | Opaque `surfaceContainer` / `virtualAppPopup()` (`alpha = 1`). |
| `hudLabel` | Dot-matrix / telemetry labels | Accent (desktop secondary / Android neon cyan). |
| `techPill` | Unselected tech pills | Opaque container fill under neon selection. |

Helix Settings cards, Control Center, top panel, Overview snap bars and panel
popups consume these tokens. Android `telosTechSurface` / `TelosTechPill` use
`cardOpaque` / `techPill`; dialogs keep `popupOpaque` → `virtualAppPopup()`.

## Readable overlays, controls and search

Keep `surface`/`background` clear for root scaffolds, but preserve opaque
`surfaceContainer` for Material popup menus, list pickers and preference
dialogs (`virtualAppPopup()`). Settings → Appearance (theme, shapes,
transparency, dynamic-color source) and Web apps (swipe/scroll direction
`ListPreference`) use the same frosted root as Telos apps;
dialog `Surface`/`AlertDialog` must set an opaque container via
`virtualAppPopup()`, never the cleared root `surface`. Preference rows use opaque `surfaceContainerLow`. Files and Phone drawers explicitly
use that role: wallpaper blur remains behind the app, while overlay labels do
not mix with underlying content. Playback buttons use paired
primaryContainer/onPrimaryContainer roles, never the transparent root surface as
foreground. Test both paused and playing TV mini player states.

Files workspace chrome uses separate rows for scrollable tabs and fixed actions,
so large fonts and narrow windows cannot squeeze a selected tab into a sliver.
Phone Recents search appends ContactSearch results after filtered recent calls in
one lazy list. It reuses ContactsViewModel privacy filtering, preferred numbers,
contact detail rows and existing call confirmation, without changing tabs.

Agent visual checks must include Greek labels, large system fonts, narrow and
wide windows, light/dark and animated wallpapers, and menus/drawers opened over
text. Preserve the no-plate home clock/search/dock-dot rule and single shared
blur backing documented above. Physical-device verification is still required.


## Process and package replacement continuity

PersistentUiState stores small tab selections independently of Android's optional
saved-instance bundle. Clock, Store, Phone, Radio, Music, Video, Photos, Stremio,
NextDNS and Network logs use it. PersistentNavigation checkpoints bounded (32
routes / 64 KiB) virtual-app navigation stacks in standalone and embedded hosts;
serialization and disk reads run on IO. Routes restore only under the same root,
without replacing navigation that changed during loading. Transient confirmation
routes are excluded; failed/incompatible snapshots fall back to the root. R8
retains NavKey class names for this lookup. This is navigation continuity, not a
snapshot of arbitrary ViewModels, playback sessions, dialogs or unsaved forms.

Files already persists open paths, selected tabs, split panes and active pane.
ClockAlarms persists alarm definitions before scheduling, and its receiver
reschedules on boot, package replacement, timezone/time changes and exact-alarm
authorization. Process death does not itself remove AlarmManager registrations.
Network persists wanted/off state, configuration and pause deadline; sticky VPN
restart, package replacement and activity resume restore it, subject to existing
VPN consent and the user's boot policy. Android force-stop/revoked permissions
are not equivalent to a recoverable crash. End-to-end update/crash checks on a
physical device remain outstanding.


## Recents task cleanup

SettingsActivity is a compatibility entry point for virtual apps. After forwarding to TelosAppActivity it removes a root redirect task, including when the redirect arrives through onNewIntent. A non-root redirect finishes only itself. Virtual-app document launches do not request RETAIN_IN_RECENTS, and explicitly finishing a root TelosAppActivity removes its document task. This prevents a blank Settings card from replaying the original virtual-app launch. Unfinished tasks still retain normal Android process-recreation and navigation recovery. Device validation of swipe-away, Back and re-opening from Recents remains required.


## Media control visibility and background playback

TV toggles controls over its full-window gesture surface and synchronizes system bars with controls. Local/stream video owns the single-tap gesture sequence across the full PlayerView, including portrait letterbox regions, without double toggling through native ACTION_UP. Existing double-tap, seek, volume and brightness gestures remain. Video controller visibility also drives system bars.

TV upgrades legacy pause-on-leave settings once to background playback; subsequent explicit opt-outs are saved with backgroundPolicyVersion=1. Video uses notification IDs 7405/7406 (normal/isolated process); Music, Radio, TV and YouTube already use distinct 7401–7404 IDs and MediaSession services. Actual Android media-card display still depends on playback/session state and system notification settings.


## Stremio playback preferences

StremioPlaybackSettings stores ordered audio/text language preferences and a validated 1/2/5/10/20 GiB HTTP cache choice, read/written on IO and shared through an atomic file with the isolated player. Empty audio preferences leave automatic selection; empty primary text disables text and automatic subtitle lookup even if a secondary language remains. Stremio launch intents carry an explicit marker so local videos retain their existing behavior. Media3 receives the preference lists before preparation; manual player track selection remains possible.

StremioStreamCache uses a process-local SimpleCache handle, separate disk locks for normal/isolated players, a touch-aware LRU evictor and immediate trim on the next player open after a budget reduction. A cache error falls back to the normal routed data source. The budget applies per player HTTP cache; torrent staging files remain separate and are not a bounded rolling torrent cache. Temporary torrent files are still removed by the existing TorrentStreamer cleanup. Settings embed the existing shared torrent controls for rate limits, peer discovery, encryption and connections; no second torrent session is created.


## Stremio episode continuation and safe source preferences

Stremio-marked torrent playback queues only the selected file, avoiding accidental automatic playback of unrelated files. An opt-in end-of-playback continuation resolves the next catalog-provided episode ID (bounded queue of 100), from the same add-on and transport, with a 15-second deadline. It only accepts web/torrent playback; no external browser is launched. The activity is recreated with the next intent so isolated playback does not explicitly finish/kill its process between episodes. The media service briefly defers stopping an empty session to allow replacement attachment. No match or a provider/network error leaves playback ended. These transitions still require device lifecycle validation.

Optional resolution, maximum advertised size and CAM/screener preferences read source metadata and names. Unknown fields remain visible; manual browsing restores all sources if a filter would hide everything. Automatic continuation does not use that empty-result fallback. No URL, codec, bitrate, torrent piece behavior or video decode path is changed by filtering.

SubtitleSelectionSheet lists individual text tracks with language/label and selected state, supports search and None, and keeps file/online actions below a lazy scrolling list that adapts to landscape height.

Automatic torrent continuation additionally requires a non-negative provider file index; the opened torrent must contain that video index, otherwise it stops instead of selecting the largest file. Episode IDs and intent titles are bounded to 512 characters each to keep the 100-entry continuation queue within a predictable intent payload.

## Utility and media readability

File attachments from Telos apps use `ui.common.share.ShareActions` as the shared boundary.
`shareUris`/`shareFile` stage files in the app's cache, choose MIME types, and attach read grants
through `ClipData`; callers that copy or read content must dispatch that work off the main thread.
Files, Viewer, Media, Screenshot, Voice Recorder, and Screen Recorder use this boundary for file
shares. Multi-select recorder sharing must preserve the call-recording lock and decrypt call
recordings only into the existing temporary readable-copy flow. New file-sharing surfaces should
use this helper rather than construct their own `ACTION_SEND` intent.
The chooser-intent builder is reserved for cases such as notification actions that need a prebuilt
`PendingIntent`; its caller must supply a URI that is already safe to grant.

The shared virtual-app palette now keeps every container role opaque, while the
root backing retains wallpaper blur. Cleared root roles retain their RGB tone so
legacy opacity adjustments do not accidentally become black. Radio inherits the launcher palette by default, and its scoped theme supplies
its own content color, so a light Radio palette cannot inherit white text from a dark
launcher. Compact station rows use fixed-size logos and adaptive columns. Media
search opens from an icon without adding a custom expansion animation. Drawer width leaves an
outside dismissal area even on narrow windows with large fonts.

Clock suggestions appear on focused empty search, starting with Athens unless
Greece is the home country or app locale. The search uses the shared pill.
Magnifier filters move into a menu and controls occupy a smaller landscape panel.
The trash unlock screen reattaches to AndroidX's retained biometric prompt; the
unlock state lives only in the task ViewModel. Process death requires authentication.

Video thumbnails use the bounded shared ViewerThumbnails cache and retriever
fallback, not just MediaStore.loadThumbnail. Poster load errors fall back to a
local video frame. Unsupported formats/codecs still show the file-type tile.
Viewer selection reuses the shared URI delete/trash flow for multiple files.

HWUI startup restoration is asynchronous and honors the existing opt-in. OpenGL
uses skiagl. The selected property is not proof of the renderer in an already
running process: Android locks that pipeline at initialization. The notice is
on by default and configurable. Saved Wi-Fi reveal lives under **Telos Network →
Settings** (moved out of System). Reveal is authenticated. Telos reads configured
networks through privileged `IWifiManager` (Shizuku or root, preferring the Telos
built-in Shizuku binder) and falls back to the config store files. It detects
Shizuku/root automatically and opens System → Shizuku when the external manager
APK is absent. Parsing cannot cross network blocks, and backgrounding clears
revealed credentials. OEM/SELinux restrictions may still prevent reading the store.

Developer Options stealth cloak snapshots the related Global/System keys before
clearing `development_settings_enabled` and restores that snapshot on uncloak or
when the master Developer Options flag returns to enabled, so animator scales and
similar toggles are not discarded with the flag.

Use the [adaptive UI contract](./adaptive-ui) for shared tokens, preference/navigation components,
font scaling, insets and required validation. See [UI validation](./ui-validation) for execution
evidence; shared host changes are not proof that every hardware/editor state was visually tested.


Recents uses one non-exported activity alias per document-task identity, each with
a static localized label and icon targeting TelosAppActivity. This preserves app
identity on OEM launchers that fall back to ActivityInfo instead of dynamic
TaskDescription metadata. Media and Viewer route aliases retain their existing
shared task identities. Existing Android snapshots may retain old metadata until
the old task is closed/reopened; no old task or unsaved editor is forcibly removed.

## Notes export and Morse outputs

Notes image export uses StaticLayout on IO, renders sequential 1080-pixel PNG pages, and recycles each bitmap before the next. It exports full title/body content, bounded to 100,000 characters and 32 pages, without retaining a bitmap list. FileProvider read grants and ClipData support ACTION_SEND or ACTION_SEND_MULTIPLE. PNG rendering is functional document content, not another app backdrop. The editor toolbar scrolls horizontally at large font sizes.

MorseCodec uses the Greek radio alphabet directly and AnyAscii 0.3.3 for other Unicode scripts, with romanization and unsupported-character disclosure. The dependency is about 321 KiB compressed; its Unicode tables avoid the substantially larger ICU4J dependency and work on Android 8+. AnyAscii loads blocks relative to its class package; preserve the narrow keepnames rule and Java resources in release builds. Conversion runs on IO because those tables are lazily loaded. Playback runs in one UI-scoped coroutine using 1/3-unit marks and 1/3/7-unit gaps. ToneGenerator uses media volume and a sustained DTMF tone, explicitly stopped at every gap; the old fixed-duration beep shortened dashes. Generation guards prevent cancellation cleanup from disabling a newer output. Lifecycle stop/disposal clears hardware, screen, brightness and tones. Physical sound, camera/OEM response and full visual matrix require device verification.

## October 25 implementation review

See [the feature/UI audit](./audits/2026-10-25-gemini-review) for the reviewed main baseline, fixes, UI/backend inventory and explicit limitations. Wi-Fi regex regressions must run on Android ICU as well as the desktop JVM. Authentication failure cannot reveal keys; backgrounding clears both credential lists and QR content. JSON export writes only to an explicitly chosen document after fresh authentication. System app snapshots are built on IO and use the existing BloatDescriptions provider.

## Overlay readability correction

Stremio keeps its catalog composed to preserve its scroll/navigation state; the detail surface and hero gradient use opaque surfaceContainerLow to obscure it. Play/Save actions wrap onto additional rows at narrow widths and large fonts. The shared VirtualAppTheme root keeps the existing wallpaper blur, but requests an opaque base-surface fallback when no valid blurred frame exists (including loading, denied pixels and video readback failure). Inner chrome does not request that fallback. surfaceBright, surfaceDim and surfaceVariant retain opaque theme tones for settings rows and legacy controls in both palettes.

## Phone call stability and privacy follow-up

CallScreenContent is a stateless photo/identity/control scene. Photo decoding is bounded to 1280 pixels per edge and uses Fit with loading/error initials. The real window width and height select portrait or two panes after safe-drawing insets; critical call actions stay outside the scrolling controls. Incoming, outgoing and active states share the large photo. The obsolete photo-background switch is removed; stored preferences remain readable.

CallUiVisibility uses owner tokens without Activity references. InCallService cancels the small call notification while the full-screen host is resumed, and restores it when backgrounded without replaying the same full-screen intent. Answer commands re-resolve the current ringing Telecom call; missing OEM details do not dereference null. Automatic capture is owned solely by the service, serialized with manual starts, dispatched on IO and guarded against stop/start races and optional capture exceptions. The subsequently provided Android 16 crash trace identifies an unqualified ongoing CallStyle notification; the notification policy below addresses that confirmed cause.

PhoneNumberKey supplies conservative Greek/international canonical keys without arbitrary suffix matching. Hide Contact writes all numbers in one preference update and locks the hider; live contact-directory expansion protects contacts hidden by earlier versions in Recents, T9 history and the home Recents widget. Existing visibility switches and authenticated unlock still apply. Caller lookup remains explicit opt-in, excludes saved/hidden callers from automatic submission, never scans history over the network, follows only one same-origin number-page redirect, bounds responses/cache, and labels community names as suggestions with source links. Unknown-number details expose the switch and result.

## Confirmed Android 16 CallStyle failure

The supplied OnePlus Android 16 stack trace shows NotificationManager rejecting notification 7201 after answer: this bound InCallService is not a foreground service. Only the first incoming alert uses CallStyle, always with its real full-screen intent. Ongoing/held/outgoing and restored incoming notifications use ordinary CATEGORY_CALL notifications with explicit Hang up or Answer/Reject actions; they never borrow a fake foreground-service flag or an ongoing full-screen intent. OEM presentation rejection falls back to ordinary actions.

Posting checks the live session token and phase so delayed settings/identity emissions cannot resurrect ended or replaced calls. Unbind, service destruction and an empty-session service restart explicitly clear notification 7201. Notification construction is separated from posting so Android tests can reproduce the policy constraint and post/cancel a synthetic ongoing call without a carrier call.

Launcher startup also reconciles an orphan from a previous process crash: Telecom is queried on IO and the notification is removed on Main only when Telecom explicitly reports no call and the local session is still idle. Denied/unknown Telecom state leaves notifications untouched.

## Compact Media chrome

MediaToolbarHeader hosts one horizontally scrollable, width-bounded action strip beside a source-title button. Hub source/reorder entries remain in a popup anchored to that button; no hub overflow icon or second action row is emitted. Source chips use a keyed LazyRow and scroll to the selected source, respecting reduced-animation preference. Title text uses titleLarge with two-line toolbar overflow; its full text remains in semantics and in the source menu.

MediaSearchAction and MediaSearchBar separate the icon from the controlled search field; nested panels can use self-contained search. Expansion is saveable, Back closes the field, existing query owners and YouTube submission callbacks remain authoritative. TelosSearchBar supplies the single padding layer, focus, IME search, query clear and optional submit/close actions. StremioBrowseControls keeps tabs and search in one bounded row; poster widths stay independent of font size while captions grow. TV filter chrome uses an opaque container instead of overlapping translucent text.

Compact search expansion is immediate, avoiding extra motion and intermediate layouts at large fonts. YouTube submit remains disabled while a request is running, preserving its prior enabled-state behavior.

## Root Back and stable font controls

Standalone Telos app hosts intercept root system Back and shared navigation-header Back to background their document task without popping its root entry. Child Back continues to pop normally. Explicit task finish and swipe-away keep their existing removal behavior. This preserves the current composed screen and saveable state while the task lives; it does not guarantee restoration of arbitrary in-memory state after process death.

Per-app font controls use the inherited base density for their window and gesture geometry. The sample reserves its measured maximum-size height and applies the live app multiplier inside that stable area. App content follows live changes; persistence remains on gesture completion.

Shared preference rows use fine Cyber-Tech borders over opaque tonal fills; category labels use the shared technical heading with the theme foreground accent. Tech-pill selected fills are composited over the opaque container, with paired readable text and a minimum 48 dp touch height. These shared changes are not a claim that every individual screen has completed visual review.

Validation: freeze and preference JVM suites pass locally. Android compilation and the continuous-drag, enrollment and privileged-helper emulator regressions are defined in `validate-freeze.yml`; their execution results must be checked before release. Physical-device visual validation remains outstanding.

Cyber-Tech follow-up: shared launcher cards carry a fine cyan outline only when their configured surface has nonzero opacity. Transparent clock/search/dock treatments remain free of a new plate. Inner cards, app search pills and common preference headers share the accent system; Radio settings, cloud connections, Akou and WireGuard reuse the app-bar title. This is common chrome coverage, with per-screen visual review still pending.

### Common title chrome coverage

App bars and alert-dialog titles now reuse `TelosCyberHeading` across launcher sheets, Telos apps and detailed settings. The heading adds a small themed accent rail and flexible content bounds, preserving each existing title, accessibility text and action callback. The initial pass updates 213 title slots in 122 source files, including Files, TV, camera controls, notifications, account dialogs and settings pickers. Content areas such as video, camera preview and message bodies keep their original rendering. This count records source coverage; it is not a completed visual acceptance report.

### Hardware icon recomposition

`StaticLauncherIcon.hardware()` produces GPU-backed preview layers. A later `render()` may need to composite those layers into a software bitmap, including recursively nested folder grids. Bitmap layers now create a temporary ARGB copy only when the source is hardware-backed, on the existing Default render dispatcher; the temporary copy is released after drawing and the shared GPU source remains intact. Direct GPU display remains supported. Android regressions cover re-rendering a hardware preview at another size, output-cache reuse and nested grid composition.
