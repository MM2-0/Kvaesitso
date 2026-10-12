# Design and interaction principles

This is a design brief, not a shipped Intelia UI specification. Existing Kvaesitso
UI remains unchanged until a focused implementation task validates a change.

## One consistent interaction model

Search, home and contextual surfaces should share a small vocabulary: find,
inspect, activate, return and dismiss. Keep primary actions reachable by the
thumb; support left/right handed preferences without changing semantic meaning.
Respect system navigation, keyboard insets, screen readers, font scaling and
reduced-animation preferences. A gesture-only feature needs a discoverable button
or keyboard equivalent.

Use a quiet hierarchy: primary content first, a small number of contextual actions
second, editing/customization outside the common path. Prefer existing theme and
shape primitives while developing Intelia's own visual language. Avoid decorative
animation, repeated prompts, moving targets and UI that exposes implementation
details to users.

## FLOW interaction

- Typing computes results; it never executes a command. Activation is a separate,
  explicit click or keyboard action.
- Show a user-owned alias and the exact action/target before activation. An alias
  must not become an opaque AI suggestion or silently override ordinary search.
- Missing or unavailable targets have an understandable state and a repair path.
  Preserve the user's alias rather than retargeting it to another app.
- For future multi-step workflows, show ordered steps, side effects and what can
  run offline. Allow cancellation between steps and distinguish a launch request
  from confirmation that another app completed something.
- Destructive or externally visible future steps need appropriate confirmation
  and failure semantics; repeated taps must not create duplicate executions.

Task 010 intentionally delivers a single app-launch step. More steps require an
explicit follow-up task and execution contract.

## ORBIT gesture philosophy

Radial positions should be stable, sparse and learnable. A customization must not
move unrelated targets. Prefer consistent direction, activation thresholds and
haptic feedback over gesture novelty. Provide a way to cancel before committing.
Define conflict priority with scrolling, long-press editing, search, IME and system
edge navigation before introducing chained gestures. Never consume Android's
system gesture areas indiscriminately.

First experiments stay within the launcher. Accessibility integration and
system-wide overlays are deferred capabilities, not prerequisites. Record real
device reachability and accidental-activation observations before choosing radial
geometry or claiming usability improvements.

## MORPH and POCKET

MORPH changes information density, not the meaning of navigation. Minimal and
expanded layouts must preserve search entry, return/dismiss behavior and gesture
mapping. Prefer progressive disclosure to parallel UI frameworks.

POCKET is temporary: describe why a panel appeared, provide explicit dismissal
and define when its state expires. Returning from an external app must not imply
that a workflow step succeeded. Avoid automatic sensitive-context capture.

## Design acceptance for feature PRs

Explain the normal path, unavailable state and cancellation path. Include a
manual device checklist for touch, keyboard, TalkBack, large fonts, light/dark
themes, orientation and interrupted navigation when relevant. Screenshots or
mockups support review; they do not replace functional evidence.
