---
sidebar_position: 8
---

# Telos Desktop (`:desktop-linux`)

`:desktop-linux` is a **standalone Compose for Desktop / JVM** module for **Telos Desktop**. It is registered in `settings.gradle.kts` and is **not** a dependency of `:app:app`. The Android APK does not ship Compose Desktop, cryptsetup helpers, or Linux icon scanners. CI publishes Linux UberJars and Windows UberJar **plus** a WiX/jpackage **setup `.exe`** (and a portable zip with `telos-desktop.exe` + bundled JRE) to GitHub; macOS is deferred until those run cleanly.

## Build

```bash
./gradlew :desktop-linux:packageUberJarForCurrentOS
./gradlew :desktop-linux:packageDeb
./gradlew :desktop-linux:packageRpm
./gradlew :desktop-linux:packageAppImage
```

### Windows `.exe` (Compose jpackage)

Run these **on Windows** with **JDK 21** and **WiX Toolset 3.14** on `PATH` (`light.exe` / `candle.exe`). Linux CI cannot cross-build a real Windows installer (Wine-only packaging is not used).

```bash
# GUI installer (bundled JRE) — primary downloadable artifact
./gradlew :desktop-linux:packageExe
# → desktop-linux/build/compose/binaries/main/exe/*.exe

# Portable app image (folder with telos-desktop.exe + runtime; zip the folder)
./gradlew :desktop-linux:createDistributable
# → desktop-linux/build/compose/binaries/main/app/telos-desktop/

# UberJar fallback (needs a JDK 21+ on the target machine)
./gradlew :desktop-linux:packageUberJarForCurrentOS
```

CI: `.github/workflows/build-desktop-binaries.yml` validates Linux/Windows on PRs and manual runs; tag builds retain the unique Linux ARM64 artifact. `build-release.yml` builds and publishes the x64 Linux/Windows release assets, including the Windows setup executable, portable ZIP and UberJar. Builds are **unsigned** (SmartScreen may warn until a code-signing cert is configured).

Main class: `com.telos.desktop.MainKt`. The window is undecorated fullscreen.

Cold-start costs (icon/cursor scans, network probes, stego carriers, HW crypto
probe) are deferred until first use — see [desktop-startup.md](./desktop-startup).

## Vault

VOS-4 (partial): `DesktopSessionSync` AES zip4j session bundles; **LAN pairing** via `LanPairingServer` / `LanPairProtocol` (`telos-sync://pair?code=&host=&port=`, private bind only) and Android `DesktopLanPairClient` in Sync → Settings. Pairing tokens are stored as digests; codes/tokens are never logged.

VOS-5 (partial): `DesktopVaultRestore` confirms AES session-bundle restore into vault `desktop.properties` + `~/.config/telos`; `DesktopLuksImageRestore` copies a locked-vault `telos_vault.img` backup with SHA-256 (never mounts with a password); `DesktopSignedUpdate` verifies Ed25519-signed manifests (`file://`, HTTPS preference, or GitHub Releases default `telos-desktop-update.manifest` via `DesktopGitHubReleasesChannel`) and stages jars under portable/vault temp without auto-install; `DesktopUpdateRestartHelper` offers a best-effort post-stage restart (marker + replace script, then process exit).

VOS-6 OpSec (partial): `TelosOpSecGuard` secure clipboard + `ClipboardHistoryStore` (bounded 20, 5 min TTL, scrub via `looksSensitive`, optional vault-only `~/.config/telos/opsec/clipboard-history` mode 0600; never host home outside isolator; no cloud sync) + Helix popup (Super+V / panel glyph, clear-all, pin-until-lock) + `TelosSessionAuditLog` (append-only under vault `~/.config/telos/opsec/audit.log`, mode 0600). Attestation light writes os-release / compositor / portable mode into the audit log on unlock, and `TpmAttestation` adds PCR 0/7 digest fields when `tpm2-tools` are on `PATH` (optional quote digest if `tpm/ak.handle` is set). Missing TPM records `tpm=unavailable` without failing unlock. This is local PCR light only — not remote attestation (RAP).

The Phantom Vault can sit on a single image or a folder of media carriers (stego shards +
`dmsetup` linear span), grow online with BTRFS zstd, use AES-NI `aes-xts-plain64` plus Argon2id,
and rclone-sync modified carriers on lock. Pre-boot offers host home, unlock, create, and tmpfs ghost modes.

**VOS-1 profiles (partial):** `DesktopProfiles` keeps named folders under config `profiles/` and the
active name in `profile.active` (vault `desktop.properties` + marker file; never passwords).
`profile.meta` may store an optional host-id fingerprint (SHA-256 of hostname+`os.name`, plus a
human label) for multi-host awareness text — no secrets. `VaultProfileSwitchPanel` lists/creates/
switches profiles and portable vault roots in pre-boot and Helix Settings. On Windows Portable
Data, profile config stays under the portable session home so the host profile is not written.
Selecting a portable root rejects the host home / `.config` / `.cache` / `.local`.
**Profile-scoped appearance:** when profile folders exist, `profiles/<name>/desktop.properties`
may override `theme.intensity`, `theme.accent`, `icons.theme`, and `cursor.theme` via
`ProfileAppearanceOverlay` (overlay wins; missing keys fall back to global vault props). Paths use
`DesktopProfiles.profilesRoot()` so Portable Data never writes host HOME. After a profile switch,
`TelosDesktopSettings.reloadAppearanceFromProfile()` reloads overlay then global.
**Panic mute:** session flag + optional persist `theme.panicMute` (`PanicMuteController`);
Super+Shift+P or Control Center / Settings Appearance forces Quiet Ops and hides neon tech borders
and gauges. Visual only — does not touch nftables or the kill-switch.
**Lock-gated switch (enforced while unlocked):** re-auth via password callback →
`PhantomVaultEngine.lock()` → switch profile name or portable root; the UI tells the user to
unlock again. Telos does **not** automatically remount a different LUKS image without a password.
**Encrypted portable export:** `PortableProfileExport` writes an AES-256 zip of the profile folder
plus a name-only `desktop.properties` marker; WireGuard private keys and secret property keys are
excluded. Import requires an explicit confirmation flag and never persists the passphrase.

**Stego FS (Mode B, partial):** carrier pools write `telos-carriers.json` (offsets/sizes only).
`assembleAndOpen` prefers Linux `losetup`/`dmsetup`; when that is unavailable it concatenates
payloads into `stego_assembled.tvault`. Portable Data / Portable Vault use
`TelosPortableData/carriers/` via `PortableStegoFs` so media carriers stay outside the host
profile home. This is optional transport obfuscation, not forensic steganography or completed VOS-6 OpSec.

## Vault (legacy image)

`LuksVaultManager` asks for a master password, then unlocks `~/.local/share/telos/telos_vault.img` with `cryptsetup` / `udisksctl` / `pkexec` and mounts it at `~/.local/share/telos/vault_mnt`. Desktop settings, state and databases are written only under that mount. A JVM shutdown hook syncs and locks the vault.

## Desktop shell

- Freedesktop `.desktop` scan and launch (`LinuxAppScanner`, `DesktopFileLauncher`)
- Icon themes with `Inherits=` fallback (`LinuxIconThemeResolver`): scans `~/.local/share/icons`, `/usr/share/icons`, `XDG_DATA_DIRS`, Flatpak exports, plus Telos-managed packs under vault `icons/` or portable `TelosPortableData/icons/` (never host HOME in Portable Data). Settings lists system + downloaded themes and a Telos catalog (Papirus, Tela, candy-icons — GPL-3.0, downloaded on demand). Built-in **CandyCyber** Compose glyphs remain the chrome fallback on Windows/macOS when no XDG theme exists. These are freedesktop themes, not Android ADW icon packs.
- XDG user dirs and a live `WatchService` desktop icon grid
- Wayland/X11 display modes (`wlr-randr`, `kscreen-doctor`, `xrandr`)
- Klassy / SierraBreeze-inspired Compose window chrome and Sweet cursor presets
- KDE-compatible XCursor / Plasma cursor themes: discovery under Freedesktop icon roots (`…/icons/<Theme>/cursors` + `index.theme` `[Cursor Theme]`), host `kcminputrc` / `kdeglobals` / GTK cursor name, vault `desktop.properties` keys `cursor.theme` / `cursor.size` (16/24/32/48). Linux exports `XCURSOR_THEME` / `XCURSOR_SIZE` to child processes and best-effort loads an XCursor frame into an AWT cursor; Compose Sweet/Breeze overlay remains the fallback. Windows/macOS map to stock AWT cursors only. Optional GPL downloads (BreezeX, Bibata) install into the portable/vault icons root — same layout as icon packs, separate from any icon-pack downloader.
- Unlock theatre: the locked pre-boot screen uses Quiet Ops charcoal + the vault glyph only (never a user wallpaper that could leak identity). After a successful unlock, the shell crossfades to the vault wallpaper path (`wallpaper.path`) and chosen intensity (`theme.intensity` = Quiet Ops / Vault Neon / Paper Ops) — no particle effects.
- Store / Flatpak permission chrome: thin `telosTechCard` accent ring by sandbox tier (Host = neon orange caution, Net-isolated = cyan, Vault FS = green, Standard = magenta or muted in Quiet Ops). Visual OpSec only; it does not change Flatpak policy by itself.
- Matched icon+cursor packs: Settings offers one-click GPL pairs (Papirus↔BreezeX, candy-icons↔Bibata; CandyCyber remains the built-in Compose chrome glyph). Downloads stay under vault / portable `icons/`.

## Adaptive shell (proposal, implemented on Linux)

One Telos product, three chrome modes from the same settings store:

- **Phone (Android `:app:app`):** existing launcher; no Linux module in the APK.
- **Tablet:** top status bar + AppGrid (this desktop shell when width is tablet-sized).
- **Linux desktop:** KDE-style top panel, AppGrid with Telos + `.desktop` apps, floating windows.

Shared preferences (theme, icon pack, grid size, vault, display) live in the vault `desktop.properties` on Linux and Datastore on Android. They are not compiled together; names stay aligned so a future sync can copy keys.

**Theme Studio** (Helix Settings): `theme.intensity` (`quiet` / `neon` / `paper`), `theme.accent` (neon hex or `wallpaper`), `theme.density` (`compact` / `comfort` / `touch`), `panel.surfaceMode`, `panel.iconContrast`, and `theme.panicMute` co-locate intensity, accent, density presets (panel height/icon + snap/overview pill scale), panel surface / icon contrast, panic mute, panel opacity, Night Light, and quick icon/cursor picks. Quiet Ops keeps low Cyber-Tech border glow; Vault Neon raises border alpha and shows HUD gauges; panic mute forces Quiet Ops and hides neon chrome without touching nftables; popups stay opaque `surfaceContainer` with no second blur. Wallpaper accent samples a local vault/portable image for a Material tonal seed (digest-cached; neon cyan fallback).

## Windowed vs fullscreen

F11 and Settings toggle a normal resizable window vs undecorated fullscreen. When
`followSystemTheme` is on, accent/icon/cursor names are read from Plasma `kcminputrc`,
`~/.config/kdeglobals`, and GTK `settings.ini` (plus `XCURSOR_*` when set). The left Telos candy/cyberpunk vector logo opens a Plasma-style AppGrid.

**Wallpaper → local tonal seed:** vault `desktop.properties` keys `theme.accent=wallpaper`
or `theme.wallpaperTonal=true` make Helix chrome use a Material-style tonal seed sampled
from the vault/portable wallpaper on `Dispatchers.IO` (`WallpaperTonalSeed`). Sampling and
tonal derivation are **local only** — pixels are never uploaded; `:libs:material-color-utilities`
is Android-only, so desktop uses a JVM-local quantize/chroma score inspired by the same
Material constants. Results are cached by SHA-256 content digest and invalidated when the
wallpaper path or bytes change. Sampling failure falls back to neon cyan (`#00F0FF`).
Optional `theme.wallpaperPath` points at a vault/portable image; otherwise Pictures /
`.config/telos` defaults are searched.

## Plasma replacement shell

The desktop shell includes a configurable top bar (default 36 dp; 32–48 dp, tint, alpha, icon size, and `panel.surfaceMode` = `transparent` \| `blurred` \| `solid` persist in `desktop.properties`). Transparent keeps configurable alpha with blur forced off; blurred uses `panelBlur` and a frost plate when host wallpaper backdrop blur is unavailable; solid paints opaque `surfaceContainer`. Only the panel root may clear — menus/popups stay opaque. Right-click the bar for style (Cyber-Tech surface pills also live under Settings → Panel surface / Theme Studio). Workspaces 1–4 show subtle neon tint strips under the digits (`theme.workspaceTints`, default on; suppressed when defensive panic-mute keys such as `theme.panicMute` are true; Quiet Ops keys lower strip alpha). Panel and Compose tray glyph contrast follows `panel.iconContrast` (`auto` \| `light` \| `dark`): auto samples the local wallpaper strip behind the panel on an IO thread (SHA-256 digest cache) and picks light or dark icons; host StatusNotifier pixmaps from `telos-sni` are not recolored. **Day/night chrome** (`theme.dayNight`): Auto uses Paper Ops by day and Quiet Ops by night in the vault timezone (`theme.timezone`); light/dark force a phase; off leaves the last intensity. Sunrise/sunset use lat/lon only when the user set coords or an **opt-in** one-shot network assist (`theme.autoTimezone` + `location.networkAssist`, both default false) returned them; otherwise night is approximated as 20:00–06:00 local. Network assist contacts a public IP→geo endpoint once, stores only timezone/country/optional coords (never logs IP), and falls back to manual TZ with PreferenceOnly/Failed on kill-switch or offline. Work-area struts keep maximized windows below the bar. Telos Quake (`F12` / Ctrl+\`) and System Monitor (`Ctrl+Escape`) read `/proc` and `/sys`. **HelixCommandPalette** (`Super+Period` / `Ctrl+Space`) is a privacy-safe global launcher: apps, windows, calculator, Files/Settings/Network, kill focused (labwc/`wlrctl` best-effort), vault clipboard paste — opaque `menu`/`surfaceContainer`, vault/portable file roots only. See [telos-helix.md](./telos-helix.md) H2b / H3.

Window chrome: titlebar-only `awaitEachGesture` drag, 8-edge resize, minimize / maximize / close, double-click shade, edge snap (halves, corners, maximize). Geometry is Compose snapshot state so frames follow the pointer. Helix snap layouts (`SnapLayoutGeometry`: 50/50, 2/3–1/3, 2×2) apply via Super+Arrow and Overview/Control Center pills; `HostWmBridge` best-effort talks to `niri msg` / `wmctrl` (output geometry via `wlr-randr`). `desktop.windowMode=tiling|stacking` selects niri vs labwc on the next session when `TELOS_COMPOSITOR` is unset.

## File routing

`TelosFileHandler` opens directories in Telos Files and media in Telos Media (then `xdg-open` / MPRIS). Other types call `xdg-open`. Desktop icons, the dock shortcuts, and the Files list all use this path.

## Locale

Desktop chrome uses the **same string names** as the Android launcher (`:core:i18n`). Gradle copies `core/i18n/src/main/res/values*` XML into the UberJar. `TelosI18n` picks `values`, then `values-<lang>` for `Locale.getDefault()`. No second translation table. Window titles stay English because they are window-manager ids.

## Telos apps on Linux

Floating windows host Files, Photos, Downloads, Trash, Media Player, Notes, Calendar, Calculator, Clock, Network, Store, Settings, System Monitor, and System Optimizer. They are JVM Compose UIs with the same names as the Android virtual apps. They do not import `:app:ui`.

## Settings isolation

`PlatformEnvironment` / `SettingsVisibilityFilter` exist twice:

- Android: `app/ui/.../settings/platform/PlatformEnvironment.kt` — shows Shizuku, work profile, T9/SMS, wallpaper; hides LUKS, XDG, xrandr, Klassy, Sweet, Linux icon packs.
- Linux: `desktop-linux/.../platform/PlatformEnvironment.kt` — the inverse.

The two copies are intentional so `:app:app` never depends on `:desktop-linux`.

## labwc session (P0 / P1, Linux only)

`desktop-linux/session/telos-session` unlocks the vault (`java -jar … --unlock-only`), writes `TELOS_HOST_HOME` / `TELOS_VAULT_MOUNT`, exports **`$HOME` to the vault mount**, then starts labwc (not `exec`). On compositor exit it runs `--lock-only` (umount + nftables clear). Telos runs as `java -jar … --session --already-unlocked` and reattaches using the **host** home for the LUKS image, not the vault `$HOME`. Host apps are normal labwc windows. Optional FIDO2: `fido2-token` / `fido2-assert` mixed into the password. nftables table `inet telos_session` default-drops outbound except loopback, DNS, and IPv4/IPv6 CIDRs in `~/.config/telos/net-allow.txt` (`/0` and other invalid lines are ignored). If nftables cannot be applied, the session still starts and prints a warning. Panic (`Ctrl+Alt+L`) and logout also lock and ask labwc to exit.

W0 (Wayland shell): labwc `<margin>` and niri `struts` reserve 36 px top / 56 px bottom for host windows. `telos-notifyd` owns `org.freedesktop.Notifications`. Autostart launches xdg-desktop-portal, `-wlr`, and `-gtk` when present; `portals.conf` prefers gtk FileChooser and wlr Screenshot/ScreenCast. Lock calls `loginctl lock-session`; unlock tries `telos-pam-auth` then the vault password. W1: `telos-layer` (GtkLayerShell) draws the panel and dock as layer surfaces; Compose hides its chrome after the Unix socket `ready` event. Missing GIR keeps Compose chrome. W2: `telos-portal` implements FileChooser and Notification; Compose shows `TelosFilePickerModal`. `telos-sni` is the StatusNotifierWatcher; the panel tray activates items. W3: idle lock, media idle-inhibit, GTK greeter when a display exists, per-output scale, fcitx5/ibus, AT-SPI. Compose is still an xdg-toplevel for the desktop. See [telos-wayland-shell.md](./telos-wayland-shell.md).

Install `labwc`. `./install-telos-de.sh` copies the jar and session files to `/opt/telos-desktop`, installs `/usr/local/bin/telos-session`, `/usr/local/bin/start-telos-de`, and `/usr/local/bin/telos-desktop-test`, and registers `/usr/share/wayland-sessions/telos.desktop`. Desktop JVM flags use Generational ZGC, string deduplication, AlwaysPreTouch, and OpenGL Java2D. `.desktop` scanning is parallel with an mtime cache; `/proc/stat` and `/proc/meminfo` use a reused byte buffer. Arm64 uses the same `packageUberJarForCurrentOS` on an aarch64 machine. `--session --already-unlocked` exits if the vault is not mounted. Empty unlock passwords are rejected. `packageUberJarForCurrentOS` copies `session/` next to the jar. Autostart reads `TELOS_JAR` from the environment (no sed). A second `telos-session` is refused via flock. Nesting labwc on an existing Wayland session needs `TELOS_ALLOW_NESTED=1`. `:desktop-linux:test` covers nftables allowlist parsing and shell quoting.

**VOS-2 Network MVP:** `DesktopNetworkController` + multi-tab `TelosNetworkApp` (Status/DNS/session nftables allowlist/Apps routing/WireGuard/Tor). Config under vault `~/.config/telos/` (`net-allow.txt`, `dns.txt`, `dns-block.txt`, `dns-allow.txt`, `nextdns-id.txt`, optional `dns-stub.port` / `dns-stub.enabled`, `kill-switch.txt`, `app-routing.txt`, `wireguard/`, `tor/`). Kill-switch on Linux re-applies `inet telos_session` and blocks clear while enabled; portable platforms show preference-only. Per-app routing: Linux launcher consults the table (deny blocks launch; vpn sets `TELOS_APP_ROUTE=vpn` best-effort); nftables owner/cgroup routing is not applied. Linux applies DoT via `resolvectl` when present (including NextDNS config ID → `<id>.dns.nextdns.io`). Enabling Telos DNS stub (or setting `dns-stub.port`) starts a 127.0.0.1 UDP/TCP forwarder that NXDOMAINs blocklist hits and uses nftables `inet telos_dns` to redirect outbound DNS — host lists become Enforced only when bind + nft succeed. Windows portable stores preferences only. See [desktop-roadmap.md](./desktop-roadmap.md) VOS rows.

**VOS-3 App runtime (partial):** Store can open a Flatpak permissions pane (sandbox tiers + common overrides via `flatpak override --user`). Without Flatpak the UI shows an unavailable state and does not crash. Store also lists available package backends (Flatpak / Winget / Scoop): on Windows, winget/scoop list/search/install run via `ProcessBuilder` with an install confirmation step; on Linux/macOS those backends stay PreferenceOnly/unavailable. Settings and Store permissions expose Camera/Microphone portal toggles that record requested state under `~/.config/telos/portal-devices.properties` and call xdg-desktop-portal Device/Camera when portal tools exist; otherwise PreferenceOnly. Host launches can wrap with `unshare --net` or `ip netns exec` when app-routing is `deny` (or NetIsolated awareness) and tools exist; Flatpak `run` keeps Flatpak `--unshare=network` and does not claim a host netns without tools. `telos://` links (`app`, `search`, `files`, short aliases) are parsed from `--open` / argv, queued into the Compose shell, and registered as `x-scheme-handler/telos` on Linux session install / boot (Windows HKCU protocol best-effort). See [desktop-roadmap.md](./desktop-roadmap.md) VOS rows.

P2 (in tree): AppGrid **All** tab runs `DesktopSearch`. Store uses Flatpak. Files: XDG shortcuts, trash, zip (split parts resolve to the central `.zip`), jar/apk/7z/tar, AES zip passwords in memory, rar via host `7z`, GTK bookmarks and `$XDG_RUNTIME_DIR/gvfs`. ACE archives browse via host `unar` or `unace` (not bundled; empty list if neither is installed). Split `.c00` maps to the `.ace`. Media libraries + MPRIS. Files workspaces persist tabs/dual pane under the vault config (paths only). Nextcloud/WebDAV uses PROPFIND over HTTPS; the username may be saved, the password stays in memory and is cleared when the vault locks. smb/sftp still use gio. Nextcloud Login Flow v2: POST `/index.php/login/v2`, open the login URL, poll for `appPassword` (memory only). Dropbox/Google Drive/OneDrive: OAuth with PKCE, loopback `http://localhost:53682/` (Google `127.0.0.1`), your own client ID (Google also needs the client secret). Access/refresh tokens stay in memory and are cleared when the vault locks. Files lists the cloud after sign-in; click downloads and opens, long-press saves to Downloads, Add uploads a local file (Dropbox simple ≤100 MB then sessions, OneDrive ≤4 MB then sessions, Drive resumable media). Search All tab also evaluates `2+2` style calculator queries, walks Music/Pictures/Videos under allowed vault/portable roots, and Flatpak search (install on tap). **Vault-local file name index (opt-in, off by default):** `VaultFileNameIndex` + `privacy.fileNameIndex` in Settings → OpSec; AES-GCM blob under `{session}/.cache/telos/file-index` (owner-only); filenames/extensions only; throttled incremental rescan; never indexes the host profile unless `BootMode.HostHome`; no content cloud upload. Media filters libraries (depth 4, Downloads too) and opens audio/video with playerctl. Store lists installed Flatpaks in a lazy list, uninstalls, and runs `flatpak update`. P3: `TELOS_COMPOSITOR=niri` (session `telos-niri.desktop`), fractional scale 100/125/150/175/200% via wlr-randr, Spectacle-class screenshots (`TelosCaptureTool`: full / region / window via grim+slurp / spectacle / gnome-screenshot / import / screencapture best-effort) saved under vault or portable `Pictures/Screenshots` as owner-only PNGs (`0600`; clipboard only with `TELOS_SHOT_CLIPBOARD=1` or the panel toggle; optional Compose redaction overlay). macOS/BSD hide LUKS. CI tests and jars on x64 and arm64. P4: `:ui-kmp` shared screens run on JVM (desktop) and Android (`:app:ui` hosts: search calculator keypad, clock local time, empty Notes, Calendar agenda month). Full Telos Calculator/Notes/Clock/Calendar remain the Android apps. `:app:app` does not depend on `:desktop-linux` or `:ui-kmp`.
