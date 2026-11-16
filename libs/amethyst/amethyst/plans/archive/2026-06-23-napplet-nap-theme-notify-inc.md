# Napplet NAP domains: theme, notify, inc

> **Status:** shipped — `NappletCapability` has `THEME`/`NOTIFY`/`INC` and `NappletIncBus` is wired into `NappletBrokerService`.
> _Audited 2026-06-30._

Date: 2026-06-23
Status: in progress

## Problem

Real-world demo napplets (e.g. kehto/web's `apps/playground/napplets/*`) hard-gate
their own boot: each reads `window.napplet.shell.supports(<domain>)` for every
domain in its manifest `requires` and aborts ("unavailable") if any is missing.
**Every** kehto demo `requires: theme`; most also need `inc`; toaster needs
`notify`. Amethyst's `fromNapDomain` returns `null` for `theme/notify/inc/cvm`,
so `shell.supports()` is false for them and all demos fail at boot.

These are real NAP service domains (kehto ships reference handlers). We add the
three the demos need (theme, notify, inc); `cvm` is deferred (its own design).

## Wire contracts (verified against kehto reference services + demos)

- **theme** — `theme.get` → `theme.get.result { theme: { colors: { background, text, primary } } }`.
  Optional host push `theme.changed { theme }` (we skip the push for v1; the
  app theme rarely changes while a napplet is foreground). Read-only, **no consent**.
- **notify** — `notify.create { title, body }` → `notify.created { id }` (past-tense,
  **not** the generic `.result`), `notify.list` → `notify.listed { notifications }`,
  `notify.dismiss { notificationId }` fire-and-forget. Consent-gated (ask once).
  Host shows a system notification + tracks a per-coordinate store for list/dismiss.
- **inc** — a topic pub/sub bus. `inc.emit { topic, args, payload }` (fire-and-forget)
  delivers `inc.event { topic, payload }` to **other** subscribed napplet sessions
  (no echo to the sender). `inc.subscribe`/`inc.unsubscribe` register interest.
  Gated on the INC declaration at the router edge (like `identity.watch`), no
  per-call consent. NOTE: Amethyst runs napplets **foreground-only, one at a time**,
  so cross-napplet delivery is usually a no-op in practice — but the bus is correct
  if/when multiple sessions overlap, and it lets the demos boot + emit without error.

## Capability mapping & consent

`NappletCapability` gains `THEME`, `NOTIFY`, `INC`; `fromNapDomain` maps the bare
domains. `requiresConsent` is false for `SHELL` and `THEME` (negotiation/cosmetic),
true otherwise. INC is authorized at the router (declared-only) and never reaches
the broker consent path.

## Touch points

- commons: `NappletCapability`, `NappletRequest`, `NappletResponse`,
  `NappletBrokerCollaborators` (new gateways), `NappletBroker`,
  `protocol/NappletProtocolJson` (decode + custom reply types + inc/theme pushes),
  `NappletRequestRouter` (inc edge ops).
- amethyst: `gateways/AccountNappletGateways` (+ theme/notify gateways), an
  app-wide `NappletIncBus`, and `NappletBrokerService` wiring (notify store + inc
  push transport, like `NappletLiveSubscriptions`/`NappletIdentityWatch`).

Staged commits: (1) theme, (2) notify, (3) inc.
