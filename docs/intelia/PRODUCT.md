# Intelia product direction

Intelia is an Android launcher derived from the open-source Kvaesitso project.
Its existing search, providers, widgets and customization are the foundation for
an original interaction model, not evidence that Intelia's signature capabilities
already exist. The bootstrap changed documentation only; the subsequent FLOW v0
feature branch implements exact local app aliases. Device validation remains
pending, and multi-step FLOW, ORBIT, MORPH and POCKET are still plans.

## Product contract

The launcher should be fast, fluid, battery-efficient and comfortable to use with
one hand. The default experience is quiet and minimal; deeper customization is
available without forcing every user through it. Local data and deterministic
actions work without an account or a network connection. AI may help author
proposals later, but must never become a prerequisite for using the launcher.

Keep visual noise low. Avoid engagement loops, attention-seeking prompts,
unnecessary permissions, background polling and speculative dependencies. User
intent takes priority over recommendations. Users control what is stored, shown
and executed, with predictable cancellation and recovery.

## Signature capabilities: planned, not implemented

| Capability | User outcome | First boundary |
| --- | --- | --- |
| FLOW | One search surface for finding things and invoking inspectable commands, aliases and workflows. | Exact local alias to one chosen app; see task 010. |
| ORBIT | Configurable radial navigation, stable quick-action positions and eventually chained gestures. | Launcher-local gestures using the existing scaffold. |
| MORPH | One home interaction model that scales from extreme minimalism to richer layouts. | Existing home components, widget state and theme settings. |
| POCKET | Temporary contextual panels and useful shortcuts that can be dismissed. | Local, explicitly opened panel with a clear lifetime. |

FLOW's eventual `vlak` example can combine access to Můj vlak, an ISIC shortcut
and a useful contextual panel. It does not promise that Intelia can drive another
app's private UI, read a ticket or keep several external apps simultaneously in
the foreground. External launch, return and panel steps need explicit contracts;
offline capability also depends on the target application's own behavior.

Future ideas are RETURN (unfinished intentions), QUIET (notification control),
optional Gemini-assisted authoring and lightweight personalization. They are
research concepts, not approved integrations or current features.

## Delivery order

1. Preserve and document the working Android baseline and recent reliability fixes.
2. Deliver FLOW's first useful slice: user-created, persistent, exact app aliases
   with explicit activation and no network requirement.
3. Validate usability and measure the cost of that slice on real devices.
4. Define typed workflow steps, previews, cancellation and failure handling before
   adding a multi-step executor or POCKET panels.
5. Explore ORBIT and MORPH in separate, bounded tasks, sharing those action
   contracts where useful. Keep existing gestures and home behavior as fallbacks.
6. Consider AI only after deterministic editing/execution works independently.

## Non-goals for the bootstrap and first FLOW task

- Replacing the repository with another upstream clone or discarding inherited fixes.
- Copying several launchers into one, or merely reskinning Kvaesitso.
- Renaming namespaces, application IDs, resources, SDKs or every upstream reference.
- A general automation language, shell commands, arbitrary intent imports, remote
  workflow execution, background scheduling or automatic activation while typing.
- Accessibility-driven third-party UI automation or system-wide overlays.
- New accounts, telemetry, AI SDKs, cloud synchronization or notification access.

## What counts as progress

Each feature needs a user-visible contract, meaningful tests, graceful unavailable
states and a documented performance impact. Mockups and proposals must be labeled
as such. Build success establishes a development baseline; it does not establish
device UX, battery improvements or production readiness.

See [DESIGN](DESIGN.md), [PERFORMANCE](PERFORMANCE.md) and
[DECISIONS](DECISIONS.md) for the working constraints.
