# `commonsUI` — the Compose half of the shared layer

`commonsUI` holds every piece of shared code that needs **Compose UI** and is
therefore useless to the headless `cli`:

- composables (`ui/<area>`, `<feature>/ui` — e.g. `nip64Chess/ui`,
  `profile/ui` — and the historical flat `audio` renderers),
- `ImageVector` icons (`icons`, `hashtags`, `robohash`) and the icon fonts,
- theme, layouts, markdown rendering (`ui/markdown`, jvmAndroid),
- Coil (`service/image`: `CoilImageBridge` + BlurHash/ThumbHash/Base64/Blossom
  fetchers),
- the `composeResources` tree (translated strings, fonts, the napplet shell +
  shim files) and the generated `com.vitorpamplona.amethyst.commons.resources.Res`,
- the `@Composable` relay-client entry points (`observeUser*`,
  `*FilterAssemblerSubscription`, `KeyDataSourceSubscription`),
- state holders that carry a `foundation`/`ui` type (`LevelFeedViewModel` with
  its `LazyListState`, `ChatNewMessageState` with `TextFieldValue`,
  `EmojiSuggestionState` with `TextFieldState`).

Its end state is **the whole app UI**: every screen, the navigation host and the
navigation chrome for every window size (bottom bar, rail, permanent drawer), with
`amethyst` and a new JVM `desktopApp` as thin shims around it. Screens still in
`amethyst/` are waiting on their own app-only helpers and the app root, not staying
there by design. `AccountViewModel` lives here (`commons.viewmodels`) as a thin
subclass of `commons`' `BaseAccountViewModel`: it adds the `ToastManager` that headless
`UserNotice`s land in (worded by `notices/ui/UserNoticeText.kt`) and the call/nest
bridges. See `commons/plans/2026-09-27-one-ui-android-desktop.md` and
`commons/plans/2026-10-09-headless-account-viewmodel.md`.

It depends on `:commons` (and quartz, `libs.quartz`) as **`api`**, so a consumer that adds
`:commonsUI` sees the headless layer transitively. `amethyst`, `desktopApp`,
`nappletHost` and `benchmark` depend on it; `cli`, `geode`
must never.

## Platform slots

Shared composables that need something only a front end can draw or do get it from a
CompositionLocal the front end installs at its root (Android in `AmethystTheme`):
`LocalRichTextPlatform` (rich-text leaves, markdown), `LocalInlineQuoteRenderer`,
`LocalTranslationPlatform`, and `LocalNotePlatform` (media players, map, platform-engine note
types, reactions/zap row, post editor; call it through the same-named shims in
`commons.ui.note.platform`). Each default draws nothing or plain text, so previews and a front
end still wiring its pieces never crash. Small platform verbs are expect/actuals instead:
`rememberTextSharer`, `rememberShortNotice`, `rememberBlossomUriOpener`, `rememberViewModel`.

## Same packages as `commons`

Every file keeps its `com.vitorpamplona.amethyst.commons.*` package. The split
is a **build-graph boundary, not a package rename**: nothing in the apps had to
change an import, and a file can move between the two modules with a `git mv`.
The rule for which module a file lives in is mechanical:

> imports `androidx.compose.ui` / `foundation` / `material3` / `animation`,
> Coil, `org.jetbrains.compose.resources`, `Res`, `org.jetbrains.skia`, or
> declares a `@Composable` → **`commonsUI`**. Otherwise → **`commons`**.

The only cross-module gotcha is `internal`: a `commonsUI` file cannot see an
`internal` declaration in `commons`. Make it public (or move the caller).

The full package taxonomy — including which packages are "mixed" (logic in
`commons`, composables here, same package name) — is the table in
`commons/ARCHITECTURE.md` §2. Read that first; this file only documents what
is specific to the UI module.

## Source sets

| Source set    | For |
|---------------|-----|
| `commonMain`  | Composables, icons, theme, Coil fetchers, `composeResources`. Gated by `verifyKmpPurity` like `commons`. |
| `jvmAndroid`  | Markdown renderer (`ui/markdown`), Coil-OkHttp + Blossom read-auth fetcher, the `viewModel()` helper, and the avatar/image engine (`ui/components/RobohashAsyncImage.kt`: `RobohashFallbackAsyncImage`, `GifProfilePicture`) behind the `UserAvatar` `AvatarImage` actual. For a user avatar call the commonMain `UserAvatar`; don't add another avatar composable. |
| `jvmMain`     | Desktop Coil bridge (`CoilImageBridge.jvm.kt`), a no-op `AnimatedImageAutoPlay` (Coil decodes only the first frame), `compose.desktop.currentOs`. `dependsOn(jvmAndroid)` + `skikoMain`. |
| `androidMain` | Android Coil bridge, the `AnimatedImageAutoPlay` actual that starts/stops GIF/AVIF drawables. `dependsOn(jvmAndroid)`. |
| `skikoMain`   | `org.jetbrains.skia` pixel helpers shared by desktop JVM + iOS (`SkiaBitmapConverter`). |
| `iosMain`     | iOS Coil bridge, a plain `AsyncImage` `AvatarImage` actual. Compile-only spike today. |

## Tooling that points here

- Icon fonts: `tools/material-symbols-subset/subset.sh` and
  `tools/icon-font/build_icon_font.py` read `icons/` and write
  `composeResources/font/` in this module (see root `AGENTS.md`, "Icons").
- Translations: `crowdin.yml` and `tools/strings-migrate/` target
  `commonsUI/src/commonMain/composeResources/`. This is where **every new
  user-visible string** goes, Android-only screens included; the app's own
  `res/values/strings.xml` holds only the synchronous-platform tier (see root
  `AGENTS.md`, "Strings").
- CI: `.github/workflows/build.yml` runs `:commonsUI:jvmTest`,
  `:commonsUI:verifyKmpPurity` and the iOS compile/test tasks next to the
  `:commons` ones.
