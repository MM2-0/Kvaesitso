# Verifying Gemini-side AppFunctions discovery

> **Status:** shipped — Verification doc; verbs ship with `@AppFunction(isDescribedByKDoc = true)` description-based discovery as the doc concludes.
> _Audited 2026-06-30._

**Date:** 2026-05-26
**Status:** Active — answers the open question from
`2026-05-25-appfunctions-signer-prompts.md`

The Phase 2 work proves the app side: 21 `@AppFunction` verbs are
registered, indexed by `AppFunctionManagerService`, and dispatchable
via `adb shell cmd app_function execute-app-function`. The remaining
unknown is whether **Gemini's chat UI** actually surfaces our verbs to
the user — that's a separate layer (model-side tool picker) we can't
exercise from the test command.

## What we know

* **Library state.** Built against `androidx.appfunctions
  1.0.0-alpha09`. Schemas (`@AppFunctionSchemaDefinition`) are
  optional and the official Google sample (`android/appfunctions`
  ChatApp) doesn't use them — meaning we're not at a structural
  disadvantage by not defining our own. There's no canonical
  `nostr.social` schema registry yet.
* **Discovery strategy.** Without schemas, Gemini's tool picker
  matches on the function's natural-language description (KDoc, via
  `@AppFunction(isDescribedByKDoc = true)`) and the parameter
  descriptions. We've reworked every verb's first sentence to be a
  use-when imperative — "Find a person on Nostr by name…" — instead
  of an implementation description ("Searches kind:0 metadata…").

## What we don't know yet

* Whether Gemini's model picks up our verbs at all from a typical
  user query.
* Whether Gemini's `AppFunctionSearchSpec` filters by
  `schemaCategory` / `schemaName` (in which case we're invisible
  until we annotate) or by description (in which case we should
  surface).
* What feature flags / Gemini-app versions are required. App
  Functions is generally available on Android 16+, but Gemini's
  third-party tool picker has shipped in waves.

## Verification protocol

### 1. Confirm the device is set up

```bash
# Pixel 8 or newer on Android 16 QPR1+
adb shell getprop ro.build.version.release
adb shell pm list packages | grep -i gemini   # com.google.android.apps.bard
```

### 2. Reinstall the Play debug APK with the new descriptions

```bash
./gradlew :amethyst:assemblePlayDebug
adb install -r amethyst/build/outputs/apk/play/debug/amethyst-play-universal-debug.apk
adb shell am start -n com.vitorpamplona.amethyst.debug/com.vitorpamplona.amethyst.ui.MainActivity
# sign in if needed, give Amethyst a few seconds to register
```

### 3. Confirm metadata is indexed end-to-end

```bash
adb shell cmd app_function list-app-functions | grep -c amethyst
# should print ≥ 21 — one entry per @AppFunction across our class
```

### 4. Test prompts in Gemini

These are deliberately mapped to one specific verb each. Run them in
order, take notes on which surface a tool call and which don't.

| Prompt to Gemini | Should pick |
|---|---|
| "Find vitorpamplona on Nostr" | searchProfiles |
| "What's happening on Nostr today?" | getRecentFromFollows |
| "Who am I logged in as on Nostr?" | getActiveAccountInfo |
| "Did anyone DM me on Nostr recently?" | getRecentDms |
| "How many sats did I earn on Nostr this week?" | getZapsReceived |
| "Show me Nostr posts about bitcoin" | searchByHashtag |
| "Tell me about npub1xq5eqwlhxy3ldakahsfglccvzy4j6ayyxje5a92zu90hc05dxn7qrsns90" | getProfile |
| "What are people I follow saying on Nostr?" | getRecentFromFollows |
| "Catch me up on what Snowden's been posting" | getNotesByUser |

For each: did Gemini offer to call the tool? Did it call the right
one? Did it render the result?

### 5. Diagnose any miss

If Gemini doesn't surface a verb:

1. **Check Gemini's tools view.** In the Gemini app:
   Settings → Apps. Our package should appear in the list of apps
   the assistant can interact with. If it's not there at all, the
   system hasn't told Gemini about us yet — wait a few minutes after
   install or force-reindex by clearing AppSearch.
2. **Force a re-index.**
   ```bash
   adb shell pm clear --user 0 com.android.appsearch || true
   adb shell am force-stop com.vitorpamplona.amethyst.debug
   adb shell am start -n com.vitorpamplona.amethyst.debug/com.vitorpamplona.amethyst.ui.MainActivity
   ```
3. **Verify per-prompt.** If the package is listed but a specific
   prompt doesn't trigger a tool call, the issue is description
   matching — our use-when phrasing isn't catching that query.
   Adjust the kdoc and rebuild.

## When schemas become worth doing

We'll move from "skipped" to "implement" if:

1. Step 4 above shows Gemini consistently fails to surface verbs that
   should obviously match (suggesting it's filtering by schema, not
   description), OR
2. Another Nostr Android client ships AppFunctions and wants to
   co-implement a shared schema namespace (so a Nostr-aware agent
   could route to whichever client is installed).

Until either of those happens, the simpler description-matching path
is in place and is what every public AppFunctions sample uses today.

## Open follow-ups (independent of this verification)

* NIP-55 (Amber) signer support — write verbs currently refuse with
  `AppFunctionNotSupportedException` because we can't launch Amber's
  approval activity from a background dispatch. The PendingIntent
  escape hatch (Option A in the signer-prompt plan) is the next move
  if NIP-55 usage matters.
* NWC auto-pay for `zapUser` / `zapEvent` — today we return the
  BOLT11 invoice; the caller pastes it into a wallet. With NWC
  configured we could pay automatically.
* Schema definitions if step 4 above shows we need them.
