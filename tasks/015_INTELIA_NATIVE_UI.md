# 015 — Intelia identity and native interface

Assigned by the user, 2026-10-13. Branch: `codex/intelia-native-ui`.
Preserve the existing Android foundation and verified FLOW/backup fixes.

## Approved outcome

An original, minimal Intelia Launcher interface and its own Android identity,
home/library/FLOW navigation, transitions and settings. No competitor branding
in the normal interface. New backups use `.intelia`. Keep legally required
attribution/license notices in a dedicated legal surface and deliver the exact
corresponding source with the build. Do not delete upstream history or recreate
the repository. The user explicitly authorized an apps + FLOW configuration and
setting the resulting launcher as the phone's default Home.

## Scope and acceptance

- Native Compose interface above existing app discovery/search, FLOW storage,
  safe target execution and backup interfaces; no new third-party UI or launcher.
- A clear Home, local app library and FLOW authoring with explicit activation.
- Own name/icon/runtime package and backup naming, with legacy import compatibility.
- Own settings and legal/source export; preserve licenses and avoid an unverified
  public-source URL for the unpublished build.
- Minimal active permissions/components for this local slice; no AI, telemetry,
  arbitrary automation, Accessibility service or notification collection.
- Build and meaningful tests, actual installation/Home/search/alias persistence
  on A059 / Android 16, and measured observations before performance claims.

Allowed files: application identity/manifest/build, a focused `ui/intelia` layer,
existing FLOW editor/adapters, branding strings, source distribution packaging,
relevant tests and project/task evidence. Retain current uncommitted verified
security changes. No push/merge is authorized by this UI task.

## Limits

This is a bounded original interface over the reused foundation. Later typed
multi-step FLOW, full work/private-profile UI, widgets, AI and global gestures
need their own acceptance. No claim of universally safe code or fixed FPS.

## Completion — 2026-10-13

Implemented and physically verified this native slice; see
[handoff/evidence](../docs/intelia/INTELIA_NATIVE_REPORT.md). Own Home/library/FLOW,
identity, settings, legal/source export and backup restoration passed. Current
default Home is Intelia, with Nothing preserved. No commit/push/merge performed.
95 tests passed, one existing skip. Performance observations identify remaining
debug jank; production performance and accessibility acceptance are separate.
