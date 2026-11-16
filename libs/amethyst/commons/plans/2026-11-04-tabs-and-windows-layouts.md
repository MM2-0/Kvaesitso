# Tabs and windows: two new navigation layouts

Status: **First lean cut built (desktop).** Date: 2026-11-04.

## What the lean cut builds

It skips the separate `NavTabs` / `AppWindows` models, `SingleScreenNav` and the
`AccountSession` split described below. Tabs and windows are instead **modes of the existing
`NavBackStacks`** (`mode: NavigationLayoutType`). `Nav`, the shell, the drawer and rail, and
every screen keep their types.

- **The setting.** `UiSettings.navigationLayout` (`STACK` / `TABS` / `WINDOWS`), a device
  setting. `AppPlatform.navigationLayouts` lists what a front end offers. Desktop offers all
  three; Android offers only the stack for now, so the setting row is hidden there.
  `AmethystApp` applies the setting with `NavBackStacks.changeMode`.
- **`NavBackStacks` in a single-screen mode.** Every entry is one screen.
  - `push`, `newStack` and `switchTab` call `open()`. That focuses an equal route if one is
    already open, and otherwise inserts a new tab after the active one, remembering it as the
    opener.
  - `pop` closes the active tab, which falls back to its opener.
  - `popUpTo` replaces the screen in place.
  - `top` is the active screen, so `currentRoute`, the rail and deep links work unchanged.
- **Back arrows.** `Nav.canPop` is false in both modes. Top bars that drew their arrow
  without asking (`TopBarExtensibleWithBackButton` and 37 screens) now ask the screen's
  `nav.canPop()` (first built as a `LocalBackArrowAllowed` local; see the second refactor pass).
  Stack mode and deck columns are unchanged. A tabs
  window narrower than the docked drawer shows the opener chain as a stack, with a back arrow
  that closes the tab.
- **Tabs.** `ScreenTabStrip` sits above the main pane while the drawer is docked:
  - each tab has a title from `routeTitle` and a ×;
  - middle-click closes a tab;
  - + opens a new Search tab.
- **Windows.** The main window keeps the shell and shows the sections (Home and the bar's
  tabs). Every other screen is an entry that `ScreenWindows` renders through
  `AppRoot.ScreenWindow`, which on desktop is an `AppWindow`.
  - The windows are declared inside the main window's composition, so the account and its
    subscriptions run once. Closing the main window quits the app (Phase 2a).
  - Each window has its own ViewModel store and emoji panel.
  - Its screen's nav (`Nav.forEntry`, first built as `ScreenWindowNav`) closes the window on
    `popBack`, replaces the screen on `popUpTo`, and opens or raises other windows for everything else.
- **Saving.** The mode, the active screen and the openers are saved with the stack in
  `rememberSaveable`. They aren't persisted across restarts yet.
- **Second round (after the design review):**
  - **Strip:** it now spans the main screen and the notification panel, so their headers line
    up. The active tab has a pill and an accent. Tabs show an icon or the person's picture,
    shrink from 220 to 120 dp before scrolling, and the active tab scrolls into view. The ×
    shows only on the active or hovered tab.
  - **Pinned sections:** Home and the bar's sections are icon-only tabs at the start of the
    strip.
  - **Tab menu (right-click):** Duplicate, Pin, Copy link, Move to new window, Close, Close
    others, Close to the right, and Close tabs opened from here. A tab can also be dragged to
    reorder it.
  - **Tab list:** a list of open tabs with a search field.
  - **Activity and flashes:** a dot shows new replies or messages on background tabs. A tab
    flashes when it is asked for again, or when it opens in the background.
  - **Shortcuts:** Ctrl/Cmd+T, +W, +Shift+T (reopen closed), Ctrl+Tab and Ctrl+Shift+Tab.
    These take the deck's keys while each screen has its own tab or window. Ctrl/Cmd-click
    opens a screen in the background.
  - **Screen windows:**
    - they open beside their opener, at the size their kind was last left at;
    - they carry the shortcuts and the macOS menu bar;
    - the Window menu has Show main window, Bring all to front, Close all, Move into a tab,
      and the list of windows;
    - toasts show in the window that has the focus;
    - closing the main window keeps the app running while screen windows are open.
  - **Restart:** open tabs and windows come back, through `AccountSettings.openScreens`.
    Transient (`--login`) accounts save no settings, so theirs don't.
  - **Titles:** a top bar holding nothing but its caption is dropped while a tab or window
    already names the screen.
- **Deck as a fourth layout:** the setting is now Stack · Deck · Tabs · Windows, and the deck's
  own switch is gone.
  - **Migration:** a stored `ui.deck_mode = true` loads as `DECK`.
  - **Behaviour:** the deck's main screen is a plain stack (`changeMode(DECK)` keeps the
    `STACK` mode). Its columns show only in the deck layout, on windows of about 1,200 dp and
    up; narrower windows show the stack.
  - **Shortcuts:** Cmd/Ctrl+T and +W now follow the layout, so the deck and tabs no longer
    compete for them.
  - **Android:** it offers Stack and Deck.
- **Refactor, one model per layout:** `NavBackStacks` holds a `NavModel`: `StackModel`,
  `TabsModel` or `WindowsModel`. Tabs and windows share `SingleScreenModel` (openers, close,
  reopen, attention and window focus).
  - `NavigationLayoutType` (the user's choice) maps to `NavArrangement` (the model), so the model
    never sees the deck.
  - Every navigation is a `NavRequest` (`Push`, `Drawer`, `Section`, `Restart`, `Replace`, plus
    origin and background). `Nav` turns the `INav` verbs into requests, and screen windows send
    theirs with their own entry as the origin.
  - What a screen means to one layout (pinned, moved out to a window) lives in that model, not
    on `NavStackEntry`.
  - Saving uses one serializable class per model.
- **Refactor, second pass:**
  - **One destination table:** content lambdas get their `nav` when they draw
    (`DestinationScope`), from `Nav.forEntry(entry)`, so the main pane, the deck columns and the
    screen windows share one `NavDestinations`. `ScreenWindowNav` and the per-column builds are gone.
  - **One back-arrow rule:** a top bar shows its arrow when the screen's `nav.canPop()` says so.
    `LocalBackArrowAllowed` and its decorator are gone. A moved-out tab has no arrow and no bar,
    and `titleShownOutside()` (tab strip or window title) decides when a bare title bar is dropped.
  - **Layout chrome:** `rememberLayoutChrome` gives the shell the tab strip, the deck and the
    screen windows in one place.
  - **`DesktopWindowManager`:** the main frame, the screen frames, the focus, placement and saved
    sizes left `DesktopNavigator` and `DesktopAppRoot`.
- **Not built yet:** the singleton clean-up, Android windows, the account-session split
  (Phase 2b), and per-kind window identity for window-manager rules.

The rest of this document is the full design.

## Goal

Add a setting, *Settings › App › Navigation layout*, with three choices:

| Layout | What it does | Why |
|---|---|---|
| **Stack** (default, what ships today) | One back stack per window. The bottom bar or rail switches tabs inside that stack. | Keep the current behaviour for everyone who wants it. |
| **Tabs** | Every screen opens in its own browser-style tab in the main pane. A tab holds one screen and has no back button: you close it instead. You can switch, close and reorder tabs. | Follow many things at once (a thread, a profile, a hashtag, a DM) without losing your place in any of them. |
| **Windows** | Each screen opens in its own OS window. A window holds one screen and has no back button: you close it instead. | Break Amethyst into pieces a window manager (i3, sway, Hyprland, macOS Stage Manager, Android desktop windowing) can tile, stack and place. |

**Desktop sizes only.** On phones and in narrow windows, anything other than a nav stack gets
too complicated: there's no room for a tab strip, and windows mean nothing there. So:

- **Tabs** apply only while the window is on the permanent-drawer tier
  (`NavigationStyle.PERMANENT_DRAWER`). The desktop app, a landscape tablet, and Android on a
  laptop or in desktop windowing all qualify. Below that tier the same window falls back to the
  stack: it shows the active tab with the bar or rail and a back arrow to the tab that opened
  it. It keeps the other tabs, and brings the strip back when the window widens again.
- **Windows** apply only on desktop-class devices: the desktop app, and Android in desktop
  windowing (Android 16+, ChromeOS, DeX). Within that, each window lays itself out by its own
  size. A narrow tiled window still renders its one screen like a phone column, because the
  window manager chose that size.
- **Phones never see the setting.** It stays Stack, and nothing in this plan changes the phone
  UI: no tab switcher, and no *Open in new tab* items in phone menus.

## What exists today (survey)

| Component | Where | Use in this plan |
|---|---|---|
| `NavBackStacks` / `NavStackEntry` (headless back stack, JSON `encode`/`decode`) | `commons/…/model/navigation/NavBackStacks.kt` | **Reused as-is.** Each tab and each window renders through a one-entry stack, so the host and its decorators work unchanged. |
| `Nav : INav` (each op runs after `ImeSettler`), `rememberNav()` | `commonsUI/…/ui/navigation/navs/Nav.kt`, `RememberNavs.kt` | **Reused.** The tab and window navs wrap it the way `DeckColumnNav` and `TwoPaneNav` do. |
| `INav` | `commonsUI/…/ui/navigation/navs/INav.kt` | **Extended** with one method (`openNew`) that defaults to `nav(route)`, so the ~hundreds of call sites don't change. |
| Deck: `DeckArea`, `DeckColumnPane`, `DeckColumnNav`, `DeckLayout` in `AccountSettings.deck` | `commonsUI/…/ui/navigation/deck/`, `commons/…/model/deck/DeckLayout.kt` | **The template to copy.** A deck column already gets its own back stack, its own `ViewModelStoreOwner` (entry keys restart at `nav-0` in each stack), its own `NavigationEventDispatcherOwner` (back goes only to the focused column), and its own `EmojiPanelHost`. Columns are persisted as JSON, and a column whose route no longer decodes is dropped instead of breaking the whole layout. Tabs and windows need all of that. |
| `NavigationHost` (retained = stack + savedTabs; only `stack` is displayed) | `commonsUI/…/ui/navigation/host/NavigationHost.kt` | **Reused** once per tab and once per window. |
| `NavDestinations` + `sharedDestinations` + `AppRoot.registerDestinations` | `ui/navigation/host/` | **Reused.** Built per nav, the way `rememberDeck` builds them per column. `NavFamily` (fade / drill-in / modal) also decides what kind of window a route gets. |
| `AppShellLayout` (bar / rail / drawer, `deck` slot) | `ui/navigation/shell/AppShellLayout.kt` | **Extended** with a tab-strip slot above the content. |
| `ScreenLayoutSpec` / `LocalScreenLayout` | `ui/layouts/ScreenLayout.kt` | **Reused.** The column pattern (`ColumnScreenLayout`, rail tier, no docked panels) carries over to secondary windows. |
| `UiSettings` / `UiSettingsFlow` / `UiSettingsStore` (`deckMode`) | `commons/…/model/` | **Extended** with `navigationLayout`. It's a device setting, not a synced one, like `deckMode`. |
| `AppSettingsScreen.DeckModeTile` | `commonsUI/…/settings/AppSettingsScreen.kt` | **Extended** with a segmented row next to it. |
| `AmethystDesktop.main` → one `AppWindow`, plus `DesktopNappletWindows` declared inside it | `desktopApp/…/desktop/app/AmethystDesktop.kt`, `…/napplet/DesktopNappletWindows.kt` | **The precedent** for extra windows declared inside the app's composition so they inherit its providers. `SavedWindowBounds` gets generalised. |
| `DesktopNavigator` + `DesktopMenuBar` + `Command` shortcuts | `desktopApp/…/app/DesktopMenuBar.kt` | **Made per window.** Shortcut conflicts are listed under Decisions. |
| `MainActivity` with `launchMode="singleInstance"` | `amethyst/src/main/AndroidManifest.xml` | **Left alone.** Android windows get a separate `ScreenActivity`. |

**Duplicates to avoid:** don't write a second back-stack type, a second persistence format or a
second per-pane ViewModel scheme. Tabs and windows are "deck columns with different chrome", so
reuse the deck's pieces, or pull them out into shared helpers, before writing new ones.

### Things that break as soon as there are two shells (found in the survey)

1. **Account-wide work runs once per composition.** `LoggedInPage` in `AmethystApp.kt` starts
   `RelayAuthSubscription`, `AccountFilterAssemblerSubscription`, every `*Preload`,
   `root.LoggedInEffects` (on desktop: OS notifications and the scheduled-post loop),
   `RelayAuthPromptHost` and the toast/notify/crash hosts. A second window that runs
   `AmethystApp` again would start all of it twice. That's worse than wasted work:
   `ComposeSubscriptionManager` keeps queries in a map keyed by the query value
   (`commons/…/relayClient/composeSubscriptionManagers/ComposeSubscriptionManager.kt`).
   Two windows subscribe the *same* account query, so when one window closes its
   `unsubscribe` removes the other window's subscription too. The account session has to be
   hoisted so it runs exactly once.
2. **Process-wide singletons that assume one window:** `DeckFocus` and `DeckCommandBus`
   (their KDoc says "one window shows one deck"), and `NavTransitionTier.isLargeScreen` (a global
   `var` mirrored from the tier). A small secondary window next to a large main one would fight
   over it. `DesktopNavigator` is one per app, and `GlobalMediaPlayer` and `NowPlayingBar` are
   fine as one per app but must be drawn in exactly one window.
3. **`rememberForever*State` (`ui/feeds/RememberForeverStates.kt`)** stores scroll positions in
   one global map keyed by constants like `HOME_FOLLOWS`. Two Home tabs or windows would restore
   each other's position. Prefix the key with the owning tab or window id.
4. **ViewModel keys collide across stacks.** Each `NavBackStacks` starts at id 0, so `nav-0`
   exists in every tab. The deck already solves this with one `ViewModelStoreOwner` per column.

## Design

### Shared pieces (Phase 0, no visible change)

- **Setting.** Add `enum class NavigationLayout { STACK, TABS, WINDOWS }` to `UiSettings` /
  `UiSettingsFlow` / `UiSettingsStore`, defaulting to `STACK`. Add an
  `AppPlatform.isDesktopClass` port. Desktop returns true. Android returns true while the
  device offers free-form windows (`FEATURE_FREEFORM_WINDOW_MANAGEMENT`, desktop windowing,
  ChromeOS, DeX), and false on phones and on tablets outside desktop mode. Where it is false,
  the settings row isn't shown at all. Where it is true, the row shows all three options, with
  the caption "Tabs show on windows wide enough for the side drawer". Under Windows, hide the
  deck toggle, because the window manager does the deck's job.
- **The effective layout.** One function decides which layout a window actually runs:
  `effectiveLayout(setting, spec, isDesktopClass)`.
  - `TABS` runs only when `spec.navigationStyle == PERMANENT_DRAWER`; otherwise the window
    runs `STACK`.
  - `WINDOWS` runs only when `isDesktopClass` is true; otherwise the window runs `STACK`.

  Everything below reads this function rather than the raw setting. It is a pure function, so
  it gets a unit test.
- **Split `LoggedInPage`** into:
  - `AccountSession(account, root) { accountViewModel -> … }`: everything account-wide listed
    above, run once per process or account.
  - `ShellWindow(accountViewModel, navHolder, chrome)`: the per-window part (`rememberNav`, the
    destinations, `AppShellLayout`, `EmojiPanelScope`, `RelockOnLeave`, `TrackScreen`). Today's
    single window is `AccountSession { ShellWindow(it, …) }`, so the stack layout behaves
    exactly as it does now.
  - The toast, notify and crash hosts render in the **focused** window only. Add a
    `LocalIsActiveShell` that each window sets from its focus state.
- **Per-window state instead of singletons.** Make `DeckFocus`, `DeckCommandBus` and
  `NavTransitionTier` CompositionLocals (or fields on a `ShellWindowState`) provided by
  `ShellWindow`. Give `rememberForever*State` a key-prefix local (`LocalStateKeyScope`) that
  defaults to `""`, so the stack layout keeps its keys.
- **`INav.openNew(route: Route, background: Boolean = false)`**, with a default of `nav(route)`. In
  the stack layout nothing changes. The tab nav opens a tab and the window nav opens a window.
- **Capture the click modifier once per window.** Most call sites do `nav.nav(route)` from an
  `onClick` with no pointer details. Rather than touch all of them, `ShellWindow` adds an
  `Initial`-pass `pointerInput` (the same trick `DeckColumnPane` uses for focus) that records
  "this press was a middle click or Ctrl/Cmd-click" into a per-window `OpenIntentState`. A
  tab or window nav's `nav()` reads that flag (and clears it) and turns the push into
  `openNew(route, background = true)`. Only the stack layout ignores it.
- **`rememberRouteTitle(route): String`** in `commonsUI`: "Home", "@alice", "#bitcoin", the first
  line of a note, a chat name. Tab labels and window titles both use it. Build it on top of
  `destinations.serialNameOf` and the deck's `columnChoices` labels, and observe the user and
  note for the dynamic parts.

### One screen per tab or window: no back button

In both layouts a tab or a window holds exactly **one** screen. With no history to go back
through, there's no back arrow, no back gesture and no Esc-to-go-back. You leave a screen by
closing it: the tab's ×, Ctrl/Cmd+W, middle-click on the tab, or the window manager's close.

- **Every navigation opens a new tab or window.** `nav(route)` opens one next to the current one
  and switches to it; a modifier-click or middle-click opens it in the background.
  - If a tab or window already shows an equal route, that one is focused instead of a duplicate
    being opened.
  - Sections (Home, Notifications, Messages, Bookmarks, Settings, …: the drawer, rail and
    menu-bar roots) are found and focused the same way.
  - Search is the exception: every search is its own tab or window.
- **One shared base, `SingleScreenNav : INav`,** behind both `TabbedNav` and `WindowNav`:
  - `canPop()` returns `false`. About 30 shared top bars draw their back arrow only when
    `nav.canPop()` (`TopBarWithBackButton`, `TopBarExtensibleWithBackButton`,
    `SettingsSearchTopBar`, `UserDrawerSearchTopBar`, the chat headers, …), so every back arrow
    disappears without changing a single screen. Phase 1 adds one audit: grep for back icons
    wired straight to `popBack()` without that check, and gate them.
  - `showsBottomBar()` returns `false`. The drawer tier has no bottom bar anyway, and screen
    windows have no bar.
  - `popBack()` **closes this tab or window and focuses its opener.** About 180 call sites in
    `commonsUI` call `popBack()` when a job is finished: posting from a composer, leaving or
    deleting a group, finishing a picker. They keep working: the finished job's tab or window
    closes.
  - `newStack(route)` and `popUpTo(route, klass)` replace this tab's or window's screen in place
    (a new `routeVersion`, as `NavBackStacks.newStack` does today), which multi-step flows rely
    on.
  - No back handler is registered, so Esc stays with text fields, menus and dialogs.
- **Each tab and window still renders through a one-entry `NavBackStacks`.** That way
  `NavigationHost` and the ViewModel and saved-state decorators are reused unchanged; the
  stack is never pushed onto.
- **What stays inside a screen:** list/detail screens (`TwoPaneNav`: Messages, groups) keep
  their in-screen detail pane, because that's layout rather than history. Dialogs and bottom
  sheets stay dialogs.

### Tabs (Phase 1, shared UI, desktop-size windows on either platform)

**Model (headless, `commons/…/model/navigation/NavTabs.kt`).** This is a direct analogue of
`DeckLayout`:

```kotlin
@Serializable data class NavTabSaved(val id: String, val route: Route, val routeVersion: Int = 0, val openerId: String? = null, val pinned: Boolean = false)
@Stable class NavTabs { val tabs: SnapshotStateList<NavTab>; var activeId; fun open(route, after, background); fun close(id); fun move(id, delta); fun activate(id); fun reopenClosed(); fun encode()/decode() }
class NavTab(val id: String, val screen: NavBackStacks /* always one entry */, var openerId: String?, var pinned: Boolean)
```

- A tab is one screen (see above). A new tab is inserted right after the tab that opened it, so
  related tabs sit together. The opener is remembered for closing and for the narrow fallback.
- Closing the last tab opens a fresh Home tab, so the list is never empty. Closing the active
  tab activates its opener if that tab is still open, and otherwise the tab next to it, as
  browsers do.
- Keep a small stack of recently closed tabs for *Reopen closed tab*.
- Persist per account in `AccountSettings` next to `deck`, on the same "drop what no longer
  decodes" rule. Tabs come back after a restart; on Android, `rememberSaveable` also covers
  process death.
- Cap tabs at about 30. Past about 8 live tabs, the least recently used ones are *discarded*: the
  route stays, the ViewModels and saved UI state go, and the tab is rebuilt when you next open
  it. Browsers keep memory bounded the same way.

**Navigation (`commonsUI/…/ui/navigation/tabs/TabbedNav.kt`).** `TabbedNav : SingleScreenNav`
implements the rules above over `NavTabs`. Deep links, intents and OS notifications open (or
focus) a tab in the foreground: `NavigationEffects` / `NavigateIfIntentRequested` and
`DesktopDeepLinks` call `openNew` instead of `newStack`.

**Rendering.** For each tab, one `NavigationHost` inside a `key(tab.id)` with its own
`ViewModelStoreOwner` (child of the account store), a `SaveableStateHolder` slot and an
`EmojiPanelHost`, copied from `DeckColumnPane`. Only the active tab is composed. Inactive tabs
keep ViewModels and saved state, but their compose-scoped relay subscriptions stop while hidden,
which is the right default for a background tab. A tab may show a "new activity" dot later, but
that's out of scope here.

**Chrome.**
- *Drawer tier only:* a tab strip across the top of the content area, right of the drawer and
  left of the deck or notification panel. Each tab has a title, a close button and
  drag-to-reorder; middle-click closes. A `+` button opens a new Search tab. Overflow scrolls
  horizontally, like `DeckArea`'s column row.
- *A window that narrows to the rail or bottom-bar tier* drops the strip and becomes a stack
  again. The stack is built from the active tab's opener chain (Home › #nostr › Thread), so
  the back arrow returns, and pressing it shows the tab that opened this one. Nothing is
  closed, and the strip and every tab come back when the window widens. There's no tab
  switcher on small screens, and the bar and rail behave as they do today.
- *Entry points:* modifier-click and middle-click, plus *Open in new tab* in the existing note,
  profile and link drop-down menus (right-click on desktop). The menu item shows only while
  `effectiveLayout` is `TABS`.
- *Desktop shortcuts:* Ctrl/Cmd+T new tab, Ctrl/Cmd+W close, Ctrl+Tab / Ctrl+Shift+Tab cycle,
  Ctrl/Cmd+Shift+T reopen. Ctrl/Cmd+1…9 activate a tab only if the decision below says so. The
  *Window* menu lists the open tabs.

**Deck in tabs mode.** Deck columns stay where they are, to the right of the tabbed main pane.
Later, a column header can get *Open in tab* and a tab can get *Pin as column*. Columns keep
their own back stacks; only the tabbed main pane is single-screen.

### Windows on Desktop (Phase 2)

**Model.** `AppWindows` (headless, in `commons/…/model/navigation/`) holds a list of
`ScreenWindowSpec(id, route: Route, routeVersion, openerId, kind: NavFamily, bounds)`, persisted per
account like the tabs so the window manager gets the same windows back on the next start.
`desktopApp` renders it: inside the main window's composition (the `DesktopNappletWindows`
precedent), `appWindows.forEach { key(it.id) { ScreenWindow(it) } }`.

**Each `ScreenWindow`** is an `AppWindow` with:
- `ShellWindow` running in *single-screen chrome*: no drawer, no bar or rail, and the screen's own
  top bar **without a back arrow** (`SingleScreenNav`). `LocalScreenLayout` comes from the window's own size
  (`AppRoot.rememberScreenLayoutSpec()` already reads the window), so a narrow tiled window
  lays out like a phone column and a wide one like a desktop.
- Its own `Nav`, `ViewModelStoreOwner`, `NavigationEventDispatcherOwner`, `DesktopNavigator` and
  `DesktopMenuBar` (on macOS the menu follows the focused window), and its own
  `SavedWindowBounds`.
- A title from `rememberRouteTitle`, in a stable form such as `"#bitcoin — Amethyst"` or
  `"Notifications — Amethyst"`. Window-manager rules match on titles, so the format is a
  documented contract. Setting a per-kind X11 `WM_WINDOW_ROLE` or Wayland `app_id` is a research
  item: AWT has no public API for either, and `setX11WindowClass` already sets the
  process-wide `WM_CLASS`.

**What `WindowNav : INav` does in windows mode:**

| Call | Effect |
|---|---|
| `nav(route)` (drill-in or fade family) | Opens a new top-level window next to the opener, or focuses one already showing that route |
| `nav(route)` (modal family: composers, pickers) | Opens an **owned** child window. Window managers float transient windows, which suits a composer |
| `openNew` / modifier-click | Same as `nav`, but stays behind the opener (background) |
| `newStack(route)` / `popUpTo` | Replaces this window's screen in place (multi-step flows) |
| `navBottomBar(route)` / menu-bar sections | Home, Notifications, Messages, Search, … are **singletons**: focus the one that exists, or else open it |
| `popBack()` | Closes the window and focuses its opener. No back arrow is drawn; screens call it when a job is done |

**Main window and lifetime.** The main window keeps the full shell (drawer, rail, Home) as the
hub, the place to log in, switch accounts and find everything. In Phase 2a it also hosts
`AccountSession`, so closing it quits the app. That's simple, and it's how many multi-window apps
behave. Phase 2b moves `AccountSession` to `application {}` scope, with an app-level
`ViewModelStoreOwner` and an "any window focused" flow for `DesktopAppRoot.LoggedInEffects`'s
notification gating. Any window can then be the last one open, and the hub can be reopened
from any window's *Window* menu.

**Per-app versus per-window, decided once:** the privacy lock gates every window (each renders
`PrivacyLockGate`), and the wallet blur follows each window's own focus. `NowPlayingBar` and
`GlobalFullscreenOverlay` stay in the main window, while playback is global already. Toasts go to
the focused window. Switching account closes the previous account's windows and restores the
new account's saved set. Deep links and OS-notification clicks open a window.

### Windows on Android (Phase 3)

Tabs need no Android-specific work, since they're shared UI gated by the drawer tier.
Windows on Android are only for desktop windowing (`isDesktopClass`). Phones and plain tablets
never reach this code.

- Add a new `ScreenActivity` (`launchMode="standard"`, `documentLaunchMode="always"`,
  resizeable) that hosts `ShellWindow` for one route, passed as the route's JSON (`Route` is
  `@Serializable`). Start it with `FLAG_ACTIVITY_NEW_DOCUMENT | FLAG_ACTIVITY_MULTIPLE_TASK`,
  plus `FLAG_ACTIVITY_LAUNCH_ADJACENT` in split screen, and `ActivityOptions.setLaunchBounds`
  in free-form mode. `MainActivity` stays `singleInstance` so its intent handling doesn't
  change.
- Every activity is its own composition, so `AccountSession` has to become **process-level**.
  Move the account-wide subscriptions behind a ref-counted holder in the main process,
  started while any Amethyst activity is at least `STARTED`. That keeps
  `AccountForegroundFilterAssemblerSubscription`'s pause-on-stop working across several
  windows. This is the biggest piece of Android work. Run it through `android-expert` (two
  processes: none of this may touch `:napplet`) and `relay-client`.
- `AppWindows` on Android is the set of live `ScreenActivity` tasks, tracked by the window id
  in the intent. A singleton route (Home, Notifications) brings its task to the front through
  `ActivityManager.appTasks` instead of starting a new one.

### Moving between modes (Phase 4, polish)

- Tab context menu: *Move to new window*. Window menu: *Move into a tab of the main window*.
  Both carry the route and the opener across.
- Switching the setting at runtime:
  - Stack → Tabs turns each screen of the current stack into a tab, in order, each opened by
    the one before it.
  - Tabs → Windows turns each tab into a window, with a confirmation when there are more
    than 5.
  - Going back to Stack rebuilds the stack from the active tab's or window's opener chain,
    the same as the narrow fallback.

## Phasing

| Phase | Ships | Module(s) | Done when |
|---|---|---|---|
| 0 | Setting (Stack only selectable), `AccountSession`/`ShellWindow` split, de-singletoned deck and transition state, `openNew`, `OpenIntentState`, `rememberRouteTitle` | commons, commonsUI, desktopApp | No visible change. Existing nav tests pass, and desktop and Android look the same |
| 1 | Tabs on the drawer tier, falling back to the stack below it | commons (`NavTabs`), commonsUI | `NavTabsTest` and `effectiveLayout` test. Headless desktop run at 1440×900 shows the strip; resized to 480×900 it shows today's stack, and widened again it shows the same tabs. Phone UI unchanged |
| 2a | Desktop windows, main window owns the session | commons (`AppWindows`), commonsUI, desktopApp | Headless run shows N windows. Tested by hand under i3/sway and macOS |
| 2b | Session at application scope, hub reopenable | desktopApp, commonsUI | Close the hub with other windows still open: nothing restarts and nothing is subscribed twice |
| 3 | Android windows | amethyst, commons | Checked on an Android 16 desktop-windowing emulator image or a DeX/ChromeOS device |
| 4 | Move tab ↔ window ↔ column, runtime layout switch | commonsUI, desktopApp | Manual test sheet |

Phases 0 and 1 are worth shipping even if Windows is reconsidered. Phase 0 also fixes the latent
singleton bugs above for the deck.

## Testing

- **Headless, no Compose:** `NavTabsTest` (open, close, opener fallback, reorder, reopen,
  never-empty, encode/decode with an unknown route, discard keeps the route) and
  `AppWindowsTest` (singleton routes, opener focus on pop, persistence). Model them on
  `NavBackStacksTest` and the deck tests.
- **Nav tests in `commonsUI` `commonTest`:** `TabbedNavTest` and `WindowNavTest`, run the way
  `NavBottomBarStackTest` and `NavDrawerTest` are (`runTest`, no mocks). They cover:
  - `canPop()` is false;
  - `popBack()` closes and focuses the opener;
  - an equal route focuses instead of duplicating;
  - `newStack` replaces in place;
  - the modifier-click → `openNew` path;
  - the opener-chain stack used by the narrow fallback.
- **Subscription regression:** two `ShellWindow`s over one `AccountSession`. Close one, and the
  account's REQs must still be open. This is the failure mode found in the survey.
- **Run the app:** `tools/desktop-run/run-headless.sh start`, with screenshots of:
  - the tab strip;
  - the same session at phone width (`AMETHYST_SCREEN=480x900x24`), which must look exactly
    like today's stack;
  - two or three screen windows. Xvfb
  has no window manager, so windows overlap at their saved positions. That still shows
  titles, sizes and contents. Tiling behaviour needs a hand test under a real window manager.

## Decisions for the maintainer

1. **Shortcut conflicts.** Ctrl/Cmd+T and +W are the deck's add-column and close-column today,
   and Ctrl/Cmd+1/2/3 are Home, Messages and Notifications. Proposal: under Tabs, the browser
   keys go to tabs and the deck moves to Ctrl/Cmd+Alt+T/W. Ctrl/Cmd+1…3 keep opening sections,
   and Alt+1…9 (now the deck's focus keys) select tabs when the deck is off.
2. **Close shortcut only, or Esc too?** Proposal: only Ctrl/Cmd+W (and the tab's × or the window
   manager's close) closes a tab or window. Esc stays with text fields and dialogs, so a stray
   Esc never throws away a screen.
3. **Hub lifetime.** Is Phase 2a's "closing the main window quits" acceptable for a first
   release, or is 2b a blocker?
4. **Tab persistence.** Restore tabs and windows after a restart (proposed, per account), or
   start fresh each launch?
5. **Duplicates.** Proposal: opening a route that is already open focuses the existing tab or
   window. The alternative is to always open a new one, as browsers do, which grows the strip
   faster.
