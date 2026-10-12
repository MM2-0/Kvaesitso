# 010 — FLOW v0: exact offline app aliases

Status: READY after bootstrap review. Suggested branch: `feat/flow-aliases`.
Read AGENTS, STATUS, ARCHITECTURE search/execution and DECISIONS D003. This is the
first implementation task; none of the behavior below exists in the baseline.

## Outcome and scope

The user creates an alias such as `vlak`, chooses one installed, visible app in
their current personal profile and saves it locally. Typing the exact alias shows
an action with alias and target preview. Explicitly activating it requests an
ordinary app launch. Editing/deleting and restart persistence work offline.
Typing, highlighting or recomposition never launches an app.

This slice proves authoring → persistence → matching → preview → activation.
It is a single-step command, not the multi-app/ISIC/panel workflow in PRODUCT.
No work/private-profile targeting, arbitrary intents, multi-step executor, AI,
network service, background jobs, new permissions or external dependencies.

## Behavior contract

- Normalize aliases and input with Unicode NFKC, trim, collapse whitespace to one
  space and lowercase with `Locale.ROOT`. Preserve the display spelling; do not
  strip accents. Matching is full-string equality, never a prefix/fuzzy match or
  a substring. ` VLAK ` matches `vlak`; `vlak dnes` does not.
- Reject empty aliases, control characters in stored alias input and aliases
  longer than 64 Unicode code points after normalization. Reject duplicate
  normalized aliases on create/edit (excluding the edited record).
- Exactly one visible alias action appears per exact match; unmatched/blank
  queries preserve all ordinary search/action behavior. An alias does not replace
  app/provider results, alter ranking globally or steal automatic best-match
  activation. Explicit action-chip activation is sufficient for v0.
- Target selection and resolution use exact activity identity in the current
  personal profile, not package-name fallback. Exclude hidden, suspended,
  unavailable and private/work-profile apps. Recheck availability/visibility on
  activation so uninstall, hiding or profile changes cannot bypass the policy.
- Preserve a saved alias when its target disappears. Hide its executable action;
  show an unavailable state and repair/delete path in settings without disclosing
  a now-hidden app's label. Never choose another app automatically.
- Activation delegates to the existing profile-aware app-launch path, handles a
  rejected/failed launch visibly and never claims completion of another app's
  task. Deduplicate repeated activation while that launch request is in progress;
  scope any resolving job to the UI lifecycle.

## Preferred implementation seams

1. Add a pure Kotlin normalizer/matcher/validator with no Android context or IO.
   Keep it inside `data/search-actions` initially; no new module is needed.
2. Add a dedicated alias builder/action and a `flow_alias` entity discriminator.
   Persist a versioned alias/target payload in existing `SearchActionEntity`
   fields. Keep existing types/positions intact and use the existing repository
   and DAO ordering/transaction behavior. Unknown/malformed versions must be
   skipped safely without breaking other actions; do not add a Room migration
   unless the existing fields prove inadequate and that decision is recorded.
3. Resolve targets through `AppRepository.findMany()` plus exact identity and
   visibility/profile checks. Do not use `findOne(packageName,user)` as an exact
   activity resolver. A small injected adapter may depend on the existing
   `data/applications` project after checking dependency direction. Do not
   repurpose `AppSearchAction`: it sends ACTION_SEARCH, not a launcher launch.
4. Extend `EditSearchActionSheet*` with a FLOW alias choice, validation feedback
   and a visible personal-app picker; preserve existing editor types. The existing
   search-actions settings route provides edit/delete and local persistence.
5. Route exact-match actions through `SearchActionService`,
   `SearchResults.searchActions` and `SearchBarActions`. Carry matching/preview
   state explicitly; never evaluate execution while producing search results.
   Review `SearchVM` best-match logic so merely typing an alias has no side effect.
6. Preserve backup/export/restore compatibility for existing records and include
   alias round-trip coverage. Do not overwrite unrelated saved actions on edit.

These are preferred seams, not invented existing classes. Verify signatures and
the dependency graph before implementation. If a seam cannot meet the contract,
record a narrow decision and adjust it without expanding feature scope.

## Acceptance and verification

- Unit-test normalization/idempotence, Unicode and locale independence, blank,
  control/length limits, duplicates, exact versus prefix/suffix matching and
  unmatched behavior. Test a versioned payload round-trip plus malformed and
  unknown payloads. Reuse JUnit from the version catalog for task tests if needed.
- With a fake target resolver/launcher, prove matching performs zero launches,
  explicit activation requests the exact target once, duplicate taps do not
  double-launch, and missing/hidden/wrong-profile/suspended/removed targets fail
  safely. Include cancellation and launch failure, not just the happy path.
- Exercise create/edit/delete, app removal, process restart and existing-action
  preservation using the repository's suitable local or instrumented test route.
  JVM Android stubs cannot prove Room/UI behavior; mark device-dependent checks
  unrun if no device is available rather than weakening assertions.
- On a device: create `vlak` for a chosen visible app; disable networking; type
  ` VLAK ` and inspect the target; activate once; return; restart launcher;
  edit/delete; uninstall/hide the target and verify safe unavailability. Check
  TalkBack labels, keyboard input, large fonts and an unchanged ordinary search.
  The target app itself may need a network; FLOW matching/launch dispatch must not.
- Run `:app:app:assembleDefaultDebug`, `:data:search-actions:testDebugUnitTest`,
  `:core:base:testDebugUnitTest` and `:data:locations:testDebugUnitTest` with the
  wrapper. New tests must execute, not produce a zero-test success. Check backup
  evidence and report any device checks separately.
- No new permission, background service, external dependency or namespace change.
  Update STATUS, record evidence and open a focused PR without merging.

## Handoff

Record implemented paths, payload version/compatibility, matching/visibility
policy, exact test counts, device evidence or limitations, and any follow-up.
Only then plan typed multi-step FLOW and POCKET; do not silently add them here.
