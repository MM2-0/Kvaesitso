# Architectural decisions

## D008 — Intelia identity and own native UI (user-approved, 2026-10-13)

The user explicitly requested Intelia Launcher as its own product, interface,
navigation, animations and file naming, while preserving and adapting the working
foundation. The user also explicitly approved apps + FLOW and setting it as Home.
An original `dev.intelia.launcher.ui` layer reuses app discovery/fuzzy search,
hidden-item storage, guarded FLOW and bounded backup interfaces. Runtime identity
is `dev.intelia.launcher` with build suffixes. Existing internal namespaces and
module notices remain; no blind source/package replacement or new launcher import.

The active UI is local/personal-profile only, with Home/library/FLOW, its own
settings and transitions. Old notification, Accessibility, login and external
theme entry points and their unnecessary permissions are disabled for this slice.
Original copyright/GPL/library notices are isolated in Legal and retained in
source; `.intelia` replaces legacy export naming without discarding import
compatibility. An embedded, exportable project-source snapshot avoids claiming
that an unpublished GitHub branch is already available to APK recipients.

No performance guarantees follow from branding or build success. Preserve the
foundation rather than deleting unused implementations; bring additional
features into the new interface only through measured, scoped adapters.

Record durable decisions here; factual run results belong in STATUS. Revisit a
decision with new evidence, preserving its rationale and superseding entry.

## D001 — Preserve the upstream baseline (accepted, 2026-10-12)

Intelia evolves the existing repository. Preserve Kvaesitso history, attribution,
GPL-3.0-or-later policy and module-specific licenses. Bootstrap is documentation
only, with no dependency, manifest, namespace or behavior change. This protects
recent reliability fixes and makes the first feature's impact reviewable.

## D002 — Shared, compact agent context (accepted, 2026-10-12)

`AGENTS.md` is canonical for both coding agents; `CLAUDE.md` imports it. STATUS
and numbered tasks provide current context. Specialized documents are consulted
on demand. One coordinator is the default, with optional bounded delegation and
one integrator. This reduces repeated audits and competing edits.

## D003 — FLOW starts with explicit local app aliases (accepted, 2026-10-12)

Task 010 delivers exact alias matching, local persistence, visible target preview
and one user-initiated app launch. Reuse search-action storage, settings and result
chips. Do not reinterpret `AppSearchAction` (ACTION_SEARCH) as app launching.
Reuse profile-aware `Application.launch` through a dedicated adapter. First
targets are visible apps in the current personal profile; work/private profiles
and generalized execution are deferred rather than silently retargeted.

No new Room table is planned for this slice: use a new action discriminator and
versioned payload in existing entity fields, with backward-compatible readers
and backup round-trip checks. Recheck that this fits the implementation before
editing; record a reasoned amendment if a schema change becomes necessary.

## D004 — New interaction remains launcher-local (accepted, 2026-10-12)

ORBIT extends scaffold gesture state; MORPH extends existing home components;
POCKET may later be a temporary scaffold component. No new overlay or
Accessibility automation is approved. Existing inherited global actions remain
unchanged. Multi-step workflows require explicit cancellation, return and failure
semantics before implementation.

## D005 — AI is optional authoring assistance (accepted, 2026-10-12)

No AI SDK, external service or credential is introduced now. A later AI feature
may propose workflows, which must be inspectable and independently executable
without AI. Sending user data and introducing an external service require their
own explicit scope and consent model.

## D006 — Measurement precedes performance claims (accepted, 2026-10-12)

Cloud build/unit-test success is a development baseline. Device UX, jank, startup,
heap and battery claims require recorded device evidence. PERFORMANCE budgets
are proposed targets until measured. Fix observed regressions before speculative
optimizations or adding benchmark dependencies.

## D007 — A focused alias editor and scoped activation (accepted, 2026-10-12)

FLOW v0 uses a dedicated sheet on the existing Search Actions settings route;
the sealed generic intent editor and its existing customization types stay
unchanged. Alias persistence uses existing entity fields with version-1 JSON and
stable IDs. Alias mutations retain raw legacy/unknown records. The repository is
shared, with serialized writes rather than separate per-editor locks.

`SearchAction.activate` is the lifecycle-aware dispatch hook. Ordinary actions
retain their synchronous implementation through its default adapter; alias chips
resolve/dispatch in a Compose-owned coroutine and exclude aliases from the
best-match fallback. Legacy synchronous alias activation fails closed with a
user-facing instruction. One shared execution guard spans rebuilt result objects,
and cancellation/target eligibility are checked before dispatch. Leaving the
action UI or stopping its lifecycle cancels pending jobs; backup/restore shares
the repository write guard. No new schema,
production external dependency, permission or background service is required.

## D008 — Own Intelia surface over the audited foundation (accepted, 2026-10-13)

The user explicitly requested an original Intelia identity, UI and transitions
while preserving the functioning base. Reuse/adapters solve app discovery,
offline matching, storage and backup. A new native Compose surface is necessary
to implement the requested independent navigation and visual system; modifying
every inherited screen would retain unrelated product flows. Internal namespaces
remain compatibility boundaries. The active package is dev.intelia.launcher
(debug suffix in local QA), with its own icon/settings and .intelia exports.

The local product surface enables only applications and explicit FLOW. The
manifest removes online/sensitive permissions and unused exported activities,
notification/Accessibility services and background initialization. Existing
foundation source/history is retained. This is a modified GPL work, not a
clean-room rewrite. Preserve original attribution and deliver corresponding
source offline through Legal. No claim of trademark clearance is made.

## D009 — LENS and local shallow ARC (accepted, 2026-10-14)

User's recovery/design brief supersedes the header/search-bar/tab-dock Home.
Reuse current native repositories and backup/preferences layer rather than
replacing the launcher again. Home is portrait, with real system wallpaper,
optional restrained clock/date and one bottom LENS. Search is an in-activity
surface. Hold/drag/release ARC is launcher-only, user-owned, with persistent slot
keys; no automatic ranking or fake default apps. Its pure selection reducer is
separate from drawing/dispatch so cancellation and duplicate release are tested.
Public OS blur is conditional, with runtime fallback and no wallpaper-reading
permission. Vendor-private Nothing effects are not claimed. Existing widget
hosting infrastructure remains intact but widgets stay outside this UI task.

## D010 — Explicit release preview retaining local data (accepted, 2026-10-14)

Debug Compose/tooling overhead contributes materially to observed LENS jank.
The user explicitly approved a phone-only backup and updating the existing
installation without data wipe. A developer-only Gradle opt-in
`-Pintelia.localPreview=true` selects the existing `.debug` identity for release;
same signing key plus install -r retain preferences, FLOW and Home role. Default
release/debug identities and build behavior remain unchanged. Rollback APK is
kept locally; the private backup remains on the phone and is not committed/copied.
R8's inherited dontoptimize/dontobfuscate rules remain for compatibility; do not
claim this preview is fully optimized. Minimal crossfade replaces animated
content lookahead; window appearance/keep-awake flags update only when needed.
Separate measured build-mode gain from unproven individual micro-optimizations.

## D011 — Ordered app FLOW and optional global Pulse (accepted, 2026-10-14)

User explicitly requested multi-app workflows, ARC overshoot tolerance, swipe-up
LENS search, original clock/library and an island usable above other apps. This
supersedes task 020's no-overlay limit for task 030 only. Retain existing aliases,
eligibility adapter, repository and backup boundary rather than a second engine.
Single-target payloads remain v1; ordered two-to-four-app payloads are v2. Resolve
every eligible target before dispatch, recheck the whole ordered set and launch
through the existing LauncherApps adapter in one main-thread turn. No delays,
background scheduling or third-party UI driving. The last requested app is the
foreground destination; OS acceptance is not proof that earlier private content
(such as a card) has loaded. A platform failure can leave an earlier app open.

Pulse is optional and off by default. The user separately enabled it and granted
overlay/notification-listener access through Android UI for physical testing.
Use a narrowly implemented system-bound listener with public MediaSessionManager
callbacks, not the inherited general notification repository, which processes
broader notification payloads. No message-content access, storage, networking,
Accessibility service, foreground worker or media polling is introduced. Ignore
notification payloads; limit default listener categories to ongoing notifications.
Hide the overlay while locked/off, unregister session callbacks when disabled,
and never enable the global feature merely by restoring a backup. Android overlay
windows stay below status bar/IME and may be hidden by protected app screens;
there is no claim of replacing a vendor's cutout/system UI.

Original clock paths and paired-stroke Pulse glyph are drawn in project code;
no proprietary artwork/source was imported. Spaces are assigned explicitly by
the user, without usage tracking or guessed classifications. Attribution and
the modified-GPL source delivery remain intact.

## D012 — Native foundation and shared appearance adapter (accepted, 2026-10-14)

User's priority extension requires independent diagnosis of background rendering
and HOME/Recents behavior. Insets belong to content, full-window wallpaper/contrast
to the root. Inspect real role/task/Quickstep state and target evidence; do not
present an inset-gradient repair as a fix for vendor overview transitions.

Keep real System/Device icons as the default. Extend existing icon-pack importer
and LauncherIcon renderer through a small native adapter shared by Compose and
Pulse, with a bounded cache and explicit theme/package invalidation. Do not copy
private Nothing settings/assets or replace app launch identities with icon data.
Offer installed compatible packs, original neutral Intelia framing, monochrome
and safe per-app rendering modes. Locally persist and back up validated choices.

Split working appearance settings into Colors, Icons, Typography and Interface.
Use actual system fonts; no new font/image/dependency import. Defer larger geometry,
font-import, custom resource-picker and advanced slider work to task 050 instead
of adding inert controls. Appearance updates must not activate global Pulse consent.

## D013 — Pulse 2.0 documentation gate (2026-10-15)

User stopped implementation and requested documentation/planning only, followed
by confirmation and a model switch. This overrides implementation instructions
inside the attached Pulse 2.0 brief. Task 050 is deferred without source edits.
The proposed design is [PULSE_V2](PULSE_V2.md); technical choices there remain
proposals until validated. Do not assume camera-area touch capture, automatic
Accessibility permission, Play acceptance or an independent background lifetime.
Reuse existing launch/media/icon adapters and preserve explicit FLOW previews.
No automatic expanded panel; incoming events may only request compact/live UI.
Resume with bounded feasibility stage A only after the user's confirmation.

User supplied that confirmation after checkpoint `1e7311aa1`; stage A started.
The OEM cutout rect was insufficient for visual alignment on the target phone.
Use automatic geometry only as an editable initial suggestion; user calibration
takes precedence. Debug calibration owns bounded local profiles, later requiring
explicit production config/backup integration. Keep action dispatch absent from
calibration. Application overlays need a safe anchor; camera interaction currently
requires the explicitly enabled debug accessibility host. A received pointer
CANCEL always forbids dispatch; prefer hold/release/tap over assuming continuous
camera-to-ARC drag. Production Pulse is temporarily suspended only during the
debug calibration to prevent its own capsule covering those controls.
