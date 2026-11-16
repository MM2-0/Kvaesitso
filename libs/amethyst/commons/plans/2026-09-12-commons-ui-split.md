# Split `commons` into `commons` (headless) + `commonsUI` (Compose)

**Date:** 2026-09-12
**Status:** shipped on this branch

## Why

`cli` (`amy`) depends on `:commons`. Until now `:commons` declared Compose
UI, Coil, Compose resources, markdown and (on JVM) `compose.desktop.currentOs`
as dependencies, so every CLI distribution dragged ~40 MB of Compose + Skiko
jars it never loads (`create-release.yml` had a 200 MB budget "until commons
is split into core + ui modules"; `BUILDING.md` flagged the same for the
Homebrew bundle). The UI / non-UI boundary documented in
`commons/ARCHITECTURE.md` §1 was a convention enforced by nothing.

## What

A new KMP module **`:commonsUI`** (same targets and source-set layout as
`:commons`, plus `skikoMain`) that `api`-depends on `:commons` and owns every
Compose-dependent file. `:commons` keeps only the Compose *runtime*
(`@Stable`/`@Immutable`, snapshot state) and `lifecycle-viewmodel`; it no
longer applies the `org.jetbrains.compose` plugin, has no `composeResources`,
no Coil, no markdown, no `highlights`, no desktop Compose.

**Files keep their packages.** The split is a build-graph boundary, not a
package rename: `231` files were `git mv`'d from `commons/src/…` to
`commonsUI/src/…` and not a single import changed in `amethyst`,
`desktopApp`, `nappletHost` or `cli`. The generated `Res` class stays at
`com.vitorpamplona.amethyst.commons.resources` for the same reason.

Consumers: `amethyst`, `desktopApp`, `nappletHost` (for
`NappletWebContract`, which reads the shell/shim from `composeResources`) and
`benchmark` (robohash) gained `project(":commonsUI")`. `cli`, `geode`,
`marmotBench` are untouched and must stay that way.

## Method (reproducible)

1. Classify every `.kt` under `commons/src` as **UI** if it imports
   `androidx.compose.{ui,foundation,material3,animation}`,
   `org.jetbrains.compose.*`, `coil3`, `org.jetbrains.skia`,
   `androidx.lifecycle.compose`, `…commons.resources`, or declares
   `@Composable`.
2. Build the intra-module reference graph (imports + same-package simple-name
   hits) and verify **no headless file references a UI file**. Two real hits
   surfaced and were fixed by surgery rather than by moving logic into the UI
   module:
   - `richtext/GalleryParser` carried a vestigial
     `@OptIn(ExperimentalLayoutApi::class)` — dropped; it is pure logic.
   - `privacylock/PrivacyLockState` declared the `LocalPrivacyLockState`
     CompositionLocal + `lockStateFor()` next to the state machine — the two
     Compose members moved to `commonsUI/…/privacylock/LocalPrivacyLockState.kt`
     (same package).
3. Additionally move the rest of the UI-only packages (`ui/**`, `icons`,
   `hashtags`, `robohash`, `<feature>/ui`) **unless a headless file depends
   on the file**. That rule keeps the feed DAL (`ui/feeds/FeedFilter`,
   `AdditiveFeedFilter`, `ChangesFlowFilter`, `FeedContentState`,
   `RepostRenderability`, `AdditiveComplexFeedFilter`…) and
   `ui/note/ParentNote`+`ReplyContext` in `commons` (ViewModels use them).
4. Tests follow their subject; test fixtures shared with headless tests
   (`ui/note/StubCache`) stay in `commons`.
5. Check `expect`/`actual` pairs never straddle the boundary (none did) and
   that no `internal` declaration is used across it (none was).

## Verified

- `:commons:compileKotlinJvm`, `:commonsUI:compileKotlinJvm`,
  `:cli:compileKotlin`, `:desktopApp:compileKotlin`,
  `:nappletHost:compileDebugKotlin`, `:amethyst:compileFdroidDebugKotlin`.
- `:commons:jvmTest`, `:commonsUI:jvmTest`, `:cli:test`,
  `:commons:verifyKmpPurity`, `:commonsUI:verifyKmpPurity`.
- `:cli` runtime classpath no longer contains `org.jetbrains.compose.ui`,
  `foundation`, `material3`, `skiko` or `coil`.

iOS targets could not be linked in the Linux CI container; the workflow runs
`:commonsUI:iosSimulatorArm64Test` + `:commonsUI:compileTestKotlinIosArm64`
next to the `:commons` ones on macOS.

## Follow-ups (done in the same branch)

- **CLI size budget tightened to 120 MB** in `create-release.yml`. Measured
  after the split (1.15.2, Linux x64): JVM tarball 55 MB, jlink image tarball
  80 MB, `lib/` 60 MB on disk — vs ~70 MB JVM tarball before.
- **Feed DAL moved out of `ui.feeds`** into `commons/…/feeds/` (root of the
  existing `feeds` package, next to `feeds/custom`). Consumer imports rewritten.
- **Chess composables moved to `nip64Chess/ui`** in `commonsUI`; the logic
  stays in `commons/…/nip64Chess/`.
- **`ui/note/ParentNote` + `ReplyContext` moved to `commons/…/model/`** next to
  `ThreadAssembler` (with the `StubCache` test fixture). No `ui.*` package is
  left in `commons`.

## Decided: `commons` keeps the Compose compiler plugin

Measured rather than guessed. With `composeCompiler { reportsDestination }`
on the three GUI modules and full (non-incremental, `--rerun`) compiles in
both configurations, removing the plugin from `commons` flips composable
parameters typed with unannotated commons classes from runtime-stable to
unstable:

| Module | unstable params, plugin on | plugin off |
|---|---|---|
| `commonsUI` | 20 | 28 |
| `desktopApp` | 33 | 65 |
| `amethyst` (fdroidDebug) | 90 | 149 |

The classes involved (`TopFilter`, `TorSettings`, `TorServiceStatus`,
`ProfileBroadcastStatus`, `ScheduledPost`, `EmojiPackState`,
`Nip65RelayListState`, `PendingAuthApproval`, `UserSearchEngine`, …) are
all-`val` classes with no `@Stable`/`@Immutable` annotation; the plugin infers
their stability and keeps inferring it as they evolve. Hand-annotating them
would reproduce today's result but rot silently the first time a `var` is
added, so the plugin stays. Recorded in `commons/build.gradle.kts`.

