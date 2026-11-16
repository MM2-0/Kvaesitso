# Tier 1-4 coding plans — index

> **Status:** shipped — index for the four tier coding plans, all of which shipped.
> _Audited 2026-06-30._

Concrete, file-level coding plans for shipping the punchlist captured
in `2026-04-26-nostrnests-integration-audit.md`. Split across four
files (one per tier) so each commit / PR stays small and reviewable.

| Tier | Doc | Scope |
|---|---|---|
| 1 | [`2026-04-26-tier1-coding-plan.md`](2026-04-26-tier1-coding-plan.md) | Listener-counter, presence aggregation, augmented presence tags, live chat (kind 1311), reactions (kind 7 / 9735), edit + close + scheduled rooms, role parsing + promotion, hand-raise queue, kick (kind 4312). |
| 2 | [`2026-04-26-tier2-coding-plan.md`](2026-04-26-tier2-coding-plan.md) | Participant grid, per-avatar context menu (follow / mute / zap / promote / kick), zap entry points, share-via-naddr. |
| 3 | [`2026-04-26-tier3-coding-plan.md`](2026-04-26-tier3-coding-plan.md) | Room theming PARSER (graceful fallback), background-audio + wake-lock audit. |
| 4 | [`2026-04-26-tier4-coding-plan.md`](2026-04-26-tier4-coding-plan.md) | moq-auth token re-mint on long sessions, moq-lite Connection.Reload-style reconnect with backoff. |

## Sequence dependencies

- **Tier 1 Step 1 (presence aggregation)** unblocks several later
  items: listener counter (T1-S1.x), hand-raise queue (T1-S5),
  participant grid (T2-S1).
- **Tier 1 Step 5 (role parsing + promotion)** is required before
  the context-menu's role / kick rows in Tier 2 Step 2.
- **Tier 1 Step 6 (kick / kind 4312)** depends on Step 5 wiring.
- **Tier 2 Steps 1-4** can ship independently once Tier 1 is in.
- **Tier 3** is independent — can ship in parallel with anything.
- **Tier 4 Step 1** is subsumed by Tier 4 Step 2 once the
  reconnect-with-backoff path exists.

## What each plan deliberately leaves OUT

- API.md endpoints — confirmed dead (LiveKit-era). Not in any plan.
- NIP-71, hashtags, spotlight, `ends` — confirmed not used by
  nostrnests. Not in any plan.
- Multi-track / video, fetch / replay, bitrate probes — confirmed
  unused. Not in any plan.
- WebSocket fallback — browser-only fallback, irrelevant for
  Android.
- Kind 36767 (Ditto themes) and kind 16767 (per-user profile
  themes) — out of scope for the basic theming sliver in Tier 3.

For the underlying gap rationale see the audit doc.
