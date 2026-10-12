# 000 — Preserve and document the Android baseline

Type: documentation/environment bootstrap. Branch: `docs/intelia-bootstrap`.
Status: COMPLETE; [PR #1](https://github.com/ondrejmacko-it/Intelia-Launcher/pull/1)
is open for review, not merged.
Application changes: none. Baseline: `3d18f3056dce2276569aa335343175ad2ca6700e`.

## Deliverables

- Shared root AGENTS instructions and importing CLAUDE entry point.
- Product, verified architecture, design, performance, workflow, decision and
  status documents under `docs/intelia/`.
- A bounded first FLOW task with behavior, integration seams and acceptance tests.
- Build/test evidence and a reviewable PR against `main`; no automatic merge.

## Verification checklist

- [x] Inspect actual checkout, recent history, local changes and remote default.
- [x] Branch from the current baseline; preserve crash/resource fixes and licenses.
- [x] Map existing search/action, home/gesture, persistence and DI boundaries.
- [x] Full onboarding default debug build and relevant tests pass.
- [x] Repeat setup and cached bootstrap build pass; record cache status accurately.
- [x] Record fresh bootstrap test results and documentation/link checks.
- [x] Confirm only documentation paths changed and no lock/license/source changes.
- [x] Commit, push and create PR; no merge.

## Exact Android checks

From the repository root, with WORKFLOW tool activation:

```sh
bash ./gradlew :app:app:assembleDefaultDebug \
  :core:base:testDebugUnitTest :data:locations:testDebugUnitTest \
  --offline --console=plain --max-workers=4
bash ./gradlew :core:base:testDebugUnitTest --rerun \
  :data:locations:testDebugUnitTest --rerun \
  --offline --console=plain --max-workers=4
git diff --check
```

Offline mode assumes the prepared cache. Read fresh XML counts and inspect the
APK/signature; do not represent unit tests as device tests. Baseline results and
environment/CI limitations are recorded in STATUS rather than duplicated here.

## Definition of done

Both agents can start independently from repository files. Claims distinguish
existing behavior from proposals. The task 010 contract is implementation-ready.
Only the requested Markdown documentation is committed, with attribution intact.
PR creation is complete when a real PR URL is verified; a compare link alone is
not a PR. Record blocked delivery honestly while preserving completed local work.
