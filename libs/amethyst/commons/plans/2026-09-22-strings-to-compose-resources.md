# Wave 1: Android string resources → commons Compose resources

**Date:** 2026-09-22
**Scope:** every `R.string` / `R.plurals` reference in the `amethyst` module,
and the string catalog behind them.

This is the first wave of the "build the Android app for JVM/desktop" plan. It
is a precondition for every later wave: a screen cannot move to `commonsUI`
while its labels come from `amethyst/src/main/res`.

## Why this wave is not a search-and-replace

The obvious framing — "swap `R.string.x` for `Res.string.x`" — is wrong, and
the reason is worth writing down because it is what sets the wave's size.

Compose resources has exactly two accessors:

| | Android `R` | Compose resources |
|---|---|---|
| in composition | `stringResource(id)` | `stringResource(res)` |
| outside composition | `ctx.getString(id)` | `suspend getString(res)` — **no blocking form** |

The app had **458 call sites** shaped `stringRes(context, R.string.x)`, spread
across ViewModels, notification builders, foreground services, intent helpers
and Android callbacks. None of them can keep working after the key moves,
because there is no non-suspend Context-free way to read a Compose resource.

Deferring those keys does not help: only **167 of 1,912** keys are used
*exclusively* at composable call sites. `cancel`, `save`, `error` and friends
appear on both sides, and a key can only live in one resource system. So the
Context call sites had to be dealt with before any key could move.

**Decision (maintainer, 2026-09-22): no `runBlocking` bridge.** A
`stringResBlocking(id) = runBlocking { getString(id) }` shim would have made
all 458 sites a mechanical rename, but it puts a blocking resource read on
paths that can run on the main thread. Every site was converted properly
instead.

## What "converted properly" meant, by scope

| scope | count | conversion |
|---|---|---|
| `@Composable` | 264 | drop the `context` argument — `stringRes(id)` |
| already inside `launch { }` / `withContext { }` | 46 | `loadStringRes(id)` (the existing suspend bridge in `commonsUI/.../ui/StringRes.kt`) |
| plain function, caller already in a coroutine | ~80 | function becomes `suspend` |
| plain function, no coroutine in reach | ~68 | label hoisted into composition and passed as a `String` |

The interesting cases:

- **`TimeAgoFormatter`** — every caller handed it `LocalContext.current`, so the
  formatters became `@Composable` and lost the parameter entirely. `context`
  survives only in `timeAbsolute`, where `DateFormat.getTimeFormat` needs it.
  `RelayCompose` had one inside `derivedStateOf { }`, which a composable cannot
  enter; it now formats directly in composition (it is a cheap formatter).
- **The notification stack** is one connected component — `NotificationCategory`,
  `NotificationUtils`, `CallNotifier`, `SignerConsentNotifier`,
  `CalendarReminderNotifier`, `ZapNotification`, `InlineReplyFeedback`,
  `ConversationShortcuts`, `RelayPurposeSummary` — so it went `suspend` as a
  unit. Its two public entry points (`postStandard`, `postConversation`) already
  were. The ripple reached `NotificationChannels.Entry`, whose `channelId`/
  `ensure` lambdas are now `suspend`, and the notification settings screen,
  which drives them from `repeatOnLifecycle` + `produceState` instead of
  `LifecycleResumeEffect`.
- **`ScheduledPostNotifier`** (a `commons` interface) went `suspend` on both
  implementations; its only driver is `CoroutineWorker.doWork`.
- **`FlowProgressForegroundService.render`** is now `suspend`, which is what the
  POW-mining and Blossom-sync services needed.
- **`PowDuration`** is read from three composables *and* from the mining
  service, so it has a `@Composable` pair and a `suspend` pair
  (`loadApproxDuration` / `loadTimeLeft`) rather than forcing either caller into
  the wrong shape.
- **Biometric prompts** (`authenticate` in `UpdateZapAmountDialog`) run from
  `onClick` and from Android's own callbacks. Their four labels are resolved in
  composition into an `@Immutable AuthPromptLabels` and passed down.
- **Share sheets** (`shareIcs`, `startShareUrlIntent`, `shareRelayGroup`) take
  their chooser title as a `String` parameter now.
- **`ToastManager`** already carried resource *ids* and resolved them where the
  toast is drawn — so the two `ReusableZapButton` sites needed no lookup at all,
  just the id overload. The toast message classes now carry `StringResource`.
- **`Name.name(context)`** became `suspend nameOrDefault()`, and
  `FeedFilterSpinner` reads it through a `rememberDisplayName` helper that shows
  the plain `name()` for the frame the resource takes to arrive.

## The XML move

`tools/strings-migrate/migrate.py` moves elements verbatim so Crowdin sees a
pure move (both trees are Crowdin-managed with the same android layout). Two
changes were needed for a move this size:

- **`%%` was read as a bare conversion.** The old `BARE_FORMAT` scanned for a
  `%` not followed by `%`, but the *second* `%` of `%1$d%% of all` then started
  a fresh match, and the space flag in `[-#+ 0,(]*` let `% o` look like a
  conversion. Two keys (`active_subs_share`, `uptime`) were rejected for a
  format bug they did not have. The scanner now consumes `%%` as a unit and
  callers test `is_bare()` rather than `.search()`.
- **A bulk `@keyfile` mode.** The per-key path re-read and rewrote each locale
  file once per key: 1,900 keys × 58 files is ~110k rewrites of a 300 KB file.
  `extract_many()` does one pass per file. Verified byte-identical to the old
  path on a 40-key sample across all 57 locale dirs.

Nine keys used non-positional format specifiers (`%d`, `%s`), which
compose-resources cannot format. They were rewritten to `%1$d` / `%1$s` across
all 58 locale files (480 elements) — positional form is valid for Android too,
so behaviour is unchanged on both sides.

Result: **1,906 keys moved**, 67,275 elements across 57 locale dirs.
`amethyst/src/main/res/values/strings.xml` went 2,051 → 143 keys;
`commonsUI/.../composeResources/values/strings.xml` went 3,016 → 4,926.

Six keys did not move:

- `app_name`, `block_hide_user`, `rename`, `save` are referenced from
  `AndroidManifest.xml` / `res/xml`, so the Android copy stays and a copy was
  added to the Compose catalog for the Kotlin side.
- `podcast_value_for_value`, `show_less` already existed in commons; the
  duplicate app-side copies were dropped.

## Guard against silent key loss

A first attempt at the call-site rewrite ate the key in
`stringRes(Res.string.x, arg)` — the Context-detector matched *any* two-argument
call, so the first argument was consumed as if it were a `context`. 344
references disappeared with no error. Two things came out of that:

- The detector uses an **allowlist** of expressions that really are a Context in
  this tree (`context`, `ctx`, `appContext`, `applicationContext`, `actContext`,
  `localContext`, `targetContext`, `this`, and the two
  `Amethyst.instance.appContext` spellings), not "anything before the comma".
- `check_refs.py` (in the migration scratch, worth keeping alongside the tool)
  diffs the per-key, per-file multiset of `R.*` + `Res.*` references against
  HEAD. A rewrite may move a key between namespaces; it may not change the
  multiset. Every rewrite pass after that was run through it.

## Not every string could move: the synchronous-platform tier

The original goal — "zero `R.string` in Kotlin" — was wrong, not merely unmet.

Some strings are read by a platform API that resolves **synchronously and under
a deadline**, where neither Compose accessor can run. The clearest case is a
foreground service: `onStartCommand` must call `startForeground` promptly or
Android kills the service, so `FlowProgressForegroundService` builds its
notification on that thread (`runCatching { startForegroundCompat(...) }`).
A `suspend` accessor cannot be called there at all, and a `runBlocking` bridge
is exactly what the maintainer ruled out. The same holds for a notification
channel created during service startup and for a PiP `RemoteAction`, which is
assembled inside `onUserLeaveHint` / the PiP transition.

So `amethyst/src/main/res` keeps a small, deliberate tier, and
`ui/StringResourceCache.kt` keeps its Android-resource accessors (with the
`LruCache`, which exists because `Resources.getString` measured >1 ms on some
phones). What lives there:

| what | keys |
|---|---|
| always-on relay notification, call notification | restored with the six service files |
| PoW mining notification (`formatApproxDuration`/`formatTimeLeft` + `powKindLabelResId` twins) | 15 |
| nest foreground service + the two PiP activities | 12 |
| napplet capability labels (`labelResId` twin — the sandbox process has no resources of its own) | 11 |
| media PiP action labels | 4 |
| `AndroidManifest.xml` / `res/xml` references | 4 |

Most are **copies**, not moves: the same key is read from composition elsewhere,
so it lives in both trees. That is why the helpers come in pairs
(`powKindLabelRes` / `powKindLabelResId`, `formatApproxDuration(seconds)` /
`formatApproxDuration(context, seconds)`) rather than one being converted.

Two escaping traps came with the copies:

- **aapt and compose-resources do not share escaping rules.** A value moved back
  to `res/values` needs its apostrophes escaped again or the resource merge
  fails with `Invalid unicode escape sequence in string`. The inverse of
  `tools/strings-migrate/fix_escapes.py`; a value Android wrapped in quotes to
  protect whitespace, and a bare `@string/` alias, must be left alone.
- **A copy can duplicate a key** a locale already had, which fails
  `packagePlayDebugResources` — not the Kotlin compile, so it surfaces only
  after the module is otherwise green.

Where the deadline is *not* real, the string stays in the catalog and the read
moves instead. `NotificationRelayService`'s per-job breakdown now recomputes off
the notification path and reposts the card when the text changes, which keeps
those strings shared.

## What the compile-fix loop actually found

1,130 errors at first compile, cleared over eight rounds. The classes, and what
each one taught:

- **Suspend does not propagate to a fixed point.** Marking each caller `suspend`
  went 304 → 307 → 309 → 312 over four rounds and was abandoned. The direction
  that converges is the opposite: **hoist the read** to the nearest composition
  or existing coroutine. `WalletViewModel` went 22 errors → 1 by wrapping eight
  NWC callback *bodies* in `viewModelScope.launch { }` rather than trying to make
  the callbacks suspend (the relay dispatcher invokes them; they cannot be).
- **A callback's message belongs in its signature.** `payViaIntent` resolved one
  fixed "no wallet found" string, which made it `suspend`, which made it
  uncallable from the 20 `onPayViaIntent` lambdas that are its only callers. The
  message is now a parameter, resolved in the composable that owns the callback.
  `FavoriteAppLauncher.launch` took the same treatment.
- **Much of the `suspend` was never real.** Roughly 30 functions were marked
  `suspend` only because they once read a string; the read had since moved but
  the modifier had not, and every plain callback then refused to call them. The
  wallet entry points are the clearest: `fetchTransactions`, `sendPayment` and
  `createInvoice` hand straight off to `viewModelScope.launch`, which is the
  shape a ViewModel entry point should have anyway.
  *A script that removed the modifier wherever it could not find a suspension
  point was a mistake and was reverted: it has no parse of interface members
  (which have no body) and silently un-suspended `IAccount`, `ITorManager` and
  ~170 other files, including already-green modules.*
- **`derivedStateOf` is not composable scope.** `TimeAgo` builds its text there,
  so the formatters split: plain `timeAgoWith` / `timeAbsoluteWith` cores taking
  a `TimeAgoLabels` holder resolved once in composition, with the `@Composable`
  wrappers delegating. This is strictly better than the all-composable form —
  a composable formatter cannot be memoized in a `remember`.
- **A `StringResource` is not `Saveable` and not a valid LazyColumn key.**
  `NewConversationScreen`'s accordion keyed itself by the title resource; it now
  keys by the resource's `String` id.
- **Nullable format args.** The commonest argument is `Throwable.message`, and
  compose-resources takes a non-null `Any`. The bridge's varargs are `Any?` and
  a null renders as an empty string rather than the literal `"null"` that
  `Resources.getString` would have printed at the user.
- **`kotlin-errors=0` is not a green build.** A duplicate `app_name` broke
  `packagePlayDebugResources` *after* Kotlin reported zero errors. Check the
  `BUILD SUCCESSFUL` line, not the error count.

## Status

- `:amethyst:compilePlayDebugKotlin` — **green**
- `:quartz:jvmTest`, `:commons:jvmTest`, `:commonsUI:jvmTest`,
  `:amethyst:testPlayDebugUnitTest` — **pass** (1,636 tests in `:amethyst`)
- `.claude/hooks/orphan_strings_check.py` — passes
- `./gradlew spotlessApply` — clean
- `R.string` / `R.plurals` references left in Kotlin: **77**, over **68**
  distinct keys; `amethyst/src/main/res/values/strings.xml` holds **204**
  (the extras are manifest-referenced, or `plurals` a key reaches by quantity).
  All of it is the synchronous-platform tier described above — that is the
  correct end state, not a shortfall.

`:cli:test` could not be driven to completion here — its dependency resolution
is 429-throttled by the container's shared egress IP — but nothing in this wave
touches `cli`.
