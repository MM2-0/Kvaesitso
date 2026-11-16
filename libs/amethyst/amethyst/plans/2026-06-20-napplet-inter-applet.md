# Napplet inter-applet communication (NAP-INC / NAP-INTENT) — design notes

> **Status:** queued — Explicitly deferred; no `MESSAGING` capability exists in `NappletCapability` and the prerequisites (multi-applet hosting, archetype registry) are unbuilt.
> _Audited 2026-06-30._

**Date:** 2026-06-20
**Status:** Deferred — design only. Prereqs not yet built (see below).
**Parent:** `amethyst/plans/2026-06-19-napplet-sandbox-host.md`

## Why this is deferred (not just "next")

Inter-applet messaging is the one napplet capability that needs **new
architecture**, not just a new broker op + gateway. Two hard prerequisites are
missing today:

1. **Multiple applets running at once.** `NappletHostActivity` is declared
   `launchMode="singleTask"` and hosts exactly one applet. True live A↔B
   messaging (the full NAP-INC request/result transport) requires either
   multi-applet hosting (several iframes in one host, or several host processes)
   plus a routing layer — none of which exists.
2. **An archetype / handler registry.** NAP-INTENT dispatches by *archetype*
   (`note`, `feed`, `profile`, …) to a default-handler napplet. Our
   `NappletManifest` has no `handles`/archetype declaration and there is no
   "which napplet is the default handler for X" registry.

Both are sizeable subsystems. Shipping a half-version would also risk **forking
the wire format** from upstream while it is still being defined.

## Upstream model (napplet/naps survey, 2026-06-20)

Inter-applet is split into two shell-mediated specs (applets never reach each
other directly — every message crosses the shell/broker):

- **NAP-INC** (`inc`) — the transport. Messages are `{ type: "domain.action",
  id, … }`, request/result correlated by `id`. Addressing is direct
  (napplet→napplet) *or* archetype-mediated by the runtime.
- **NAP-INTENT** (`intent`) — invoke a napplet by **archetype** via
  default-handler dispatch (`shell.supports("intent")`). The shell launches the
  handler; napplets cannot invoke directly.
- **NAP-1…5** — concrete protocols on top of NAP-INC: `profile:*` (NAP-1),
  `stream:*` (NAP-2), `chat:*` (NAP-3), `note:open` (NAP-4), `feed:*` (NAP-5).
  Producer/consumer model.

Discovery is capability-probe based: `shell.supports("inc")`,
`shell.supports("inc", "NAP-N")`.

## How it would map onto our boundary

The broker model fits "shell-mediated" naturally — every message would cross
`NappletBrokerService` exactly like every other capability, gated by the ledger.
The pieces:

1. **Capability.** Add `NappletCapability.MESSAGING` mapped from NAP domains
   `inc` / `intent` (default-deny like every other domain). Consent is a *link*
   grant ("Applet A may message / open Applet B"), distinct from per-op consent.
2. **Protocol.** New `NappletRequest`/`NappletResponse` variants under
   `MESSAGING`, shaped to mirror NAP-INC (`type = "domain.action"`, `id`
   correlation) so we don't fork the wire format.
3. **Addressing.** Direct by napplet coordinate first; archetype dispatch only
   after the registry (below) exists.

### Two viable implementation shapes (pick at build time)

- **NAP-INTENT, direct coordinate** — `napplet.intent({ target, payload })` →
  consent → broker resolves `target` to a manifest in `LocalCache` → launches it
  via a `NappletIntentLauncher` gateway, passing an initial payload the target
  reads on startup (`napplet.intent` / `onIntent`). Fits the single-applet model
  (you switch to the target). No simultaneous hosting needed. **Lowest lift; most
  aligned with NAP-INTENT.** Result-return across the switch is awkward (fire-and-
  forget, or a callback event).
- **NAP-INC brokered mailbox** — `napplet.sendTo(coordinate, msg)` /
  `pollMessages()` with broker-persisted per-napplet inboxes, consent per link.
  Works with no simultaneous hosting and is fully unit-testable, but it is async
  fire-and-collect, not the request/result transport upstream describes.

Full live NAP-INC (simultaneous A↔B, request/result) needs the multi-applet
hosting prereq regardless.

## Prerequisites to build first

1. **Multi-applet hosting** — either N iframes in one `NappletHostActivity` with
   per-iframe origin isolation + routing, or a host-per-applet process model and
   a cross-process router in the broker. Decide the model before coding NAP-INC.
2. **Archetype registry** — a manifest `handles`/archetype tag (align with
   upstream naps), an index over installed napplets, and a user-set default
   handler per archetype (mirror NIP-89 handler selection, which Amethyst already
   models for app recommendations).
3. **Link-consent UX** — distinct from capability consent: "Allow *Chess* to open
   *Wallet*?", revocable per pair in a permissions screen.

## Recommendation

When picked up: start with **NAP-INTENT direct-coordinate** (smallest, aligned,
no new hosting), build the archetype registry next (unlocks default-handler
dispatch + reuses NIP-89 patterns), and only then tackle live NAP-INC once
multi-applet hosting lands. Keep the wire `type`/`id` shape identical to upstream
NAP-INC throughout to avoid a fork.
