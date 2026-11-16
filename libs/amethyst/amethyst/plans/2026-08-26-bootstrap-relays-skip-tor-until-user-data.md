# Defaults stand in for the user's relay lists only while we have no event

**Status:** proposal — not implemented
**Goal:** first-login startup on a Tor-enabled install
**Related:** `fix/tor-bootstrap-stall-and-ondemand`, `[[fresh-install-routes-everything-via-tor]]`

## The rule

Three states, currently collapsed into two:

| we have | effective list | today |
|---|---|---|
| **no event** for the user | app defaults | defaults ✅ |
| event, **empty** list | **empty** — the user chose nothing | defaults ❌ |
| event with relays | those relays | those relays ✅ |

Everything below follows from separating "we don't know" from "we know, and it's nothing".

## Why the first login is slow

On a fresh install **100% of relay traffic is Tor-routed by construction**.
`TorRelayState.trustedRelays` is empty, so `TorRelayEvaluation.useTor()` falls through to
`newRelaysViaTor` (**default true**) for every URL — and the kind-10002 that would populate it can
only be fetched over Tor. Measured (SM-T220, same account, same ~app+8-10s login, fresh install
each; the Tor-OFF arm sets the pref, force-stops, then starts the timed run so Arti never boots):

| @20s census | Tor ON | Tor OFF |
|---|---|---|
| feed on screen | login+18s | **login+11s** |
| relays opened | 18/40 | **32/41** |
| relays serving events | 9 | **22** |
| events ingested | 2,830 | **6,134 / 7,641** |

≈7s of first paint and half the relay coverage.

## Finding 1 — every `WithBackup` helper keys on emptiness, not absence

This is a pre-existing bug against the rule above, and it must be fixed first because the whole
feature depends on the distinction being real.

```kotlin
// AdvertisedRelayListEvent
fun relays()         = tags.mapNotNull(AdvertisedRelayInfo::parse)                    // [] when none
fun readRelaysNorm() = tags.mapNotNull(AdvertisedRelayInfo::parseReadNorm).ifEmpty { null }   // null!
fun writeRelaysNorm()= tags.mapNotNull(AdvertisedRelayInfo::parseWriteNorm).ifEmpty { null }  // null!
```

| helper | fallback fires when | correct |
|---|---|---|
| `normalizeNIP65AllRelayListWithBackup` | event absent only | ✅ (by accident — `relays()` has no `ifEmpty`) |
| `normalizeNIP65Read/WriteRelayListWithBackup` | event absent **or list empty** | ❌ |
| `normalizeIndexerRelayListWithBackup` | `?.ifEmpty { null } ?: DefaultIndexerRelayList` | ❌ |
| `normalizeSearchRelayListWithBackup` | `?.ifEmpty { null } ?: DefaultSearchRelayList` | ❌ |

Consequence today: **a user who publishes a kind-10002 with only write relays gets
`Constants.bootstrapInbox` silently substituted as their inbox list.** Same for a deliberately empty
search or indexer list. The app overrides an explicit choice.

The mirror problem sinks the obvious implementation: the `NoDefaults` variants return `emptySet()`
for *both* "no event" and "empty event", so `trustedRelays.isEmpty()` cannot be used as the
"do we have data yet" signal.

**Fix:** make presence explicit, and never infer it from emptiness.

```kotlin
// absent -> defaults; present -> whatever it says, including nothing
fun readRelayList(note: Note): Set<NormalizedRelayUrl> =
    nip65Event(note)?.let { it.readRelaysNorm()?.toSet() ?: emptySet() } ?: Constants.bootstrapInbox
```

Same shape for write/all, and drop the `?.ifEmpty { null }` from the indexer and search helpers.
Worth doing on its own merits even if the rest of this plan is dropped.

**This removes the need for any window or timeout.** The fallback becomes a pure function of "do we
have the event", so it ends the instant one arrives — even an empty one. No per-account bookkeeping,
no 30s backstop, no race to close.

## Finding 2 — do NOT put defaults into `TrustedRelayListsState`

Tempting (it already merges all nine lists) but wrong: `account.trustedRelays.flow` feeds
`Account.kt:454`

```kotlin
isInMyRelayList = { relayUrl -> ... it in trustedRelays.flow.value }
```

which feeds `RelayAuthPermissionLedger` -> `RelayAuthResolver` -> **the NIP-42 AUTH decision**.
Adding defaults there would make the app **auto-AUTH to the six hardcoded bootstrap relays as if
they were the user's own** — signing a challenge with the user's key and revealing the pubkey — at
exactly the moment we are also going clearnet. That converts a modest timing leak into a signed
identity assertion. See `[[relay-auth-always-was-gated]]` and `[[inbox-wine-notify-auth-billing]]`
for why AUTH is the sensitive edge.

(The `saveTrustedRelayList(trustedRelays + relay)` write path in `RelayGroupChannelListScreen:449`
is **not** a hazard — it reads `account.trustedRelayList` (the NIP-51 list), not the merged
`trustedRelays`. Checked.)

**Instead:** add a separate, purpose-named flow consumed only by Tor evaluation, e.g.
`Account.relaysAssumedWhileUnknown` — the union of the with-defaults views, non-empty only while the
corresponding events are absent. `AccountsTorStateConnector` feeds it into a new
`TorRelayState.assumedRelays`. Nothing else reads it.

## Where the check goes in `useTor()`

```
torType == OFF        -> false
isLocalHost           -> false
isOverlayNetwork      -> false
isOnion               -> onionRelaysViaTor
in moneyOpRelayList   -> moneyOperationsViaTor
in dmRelayList        -> dmRelaysViaTor
in trustedRelayList   -> trustedRelaysViaTor
in assumedRelayList   -> trustedRelaysViaTor      <-- new, immediately above the fallback
else                  -> newRelaysViaTor
```

Landing immediately above the fallback means **.onion, money-operation and DM relays keep their own
policy for free** — the change can only ever affect URLs that would have been treated as "new".

Resolve to `trustedRelaysViaTor`, **not** a hardcoded `false`:

- default user (`false`) -> clearnet -> fast start;
- hardened user (`true`) -> stays on Tor, automatically, with no new setting to discover.

That is the difference between "the app overrides you" and "the app treats its stand-in list the way
you asked your own list to be treated".

## Privacy, for the PR body

The window correlates the user's **IP with their pubkey** at ~6 hardcoded relays, because the REQ
asks those relays for that pubkey's events. A first login is the most sensitive moment there is.

What makes it defensible: **`trustedRelaysViaTor` already defaults to false**, so the moment
kind-10002 lands the user's own relays are dialled over clearnet anyway. This moves an existing
disclosure slightly earlier, to a different well-known set. It is not a new class of exposure for
the default configuration — and it is *not* an AUTH disclosure, provided Finding 2 is respected.

If `trustedRelaysViaTor` ever becomes default-true, **this feature must be revisited in the same
commit** — its justification disappears. Leave a comment at the default linking the two.

Residual, worth verifying rather than assuming: `useTor()` is keyed by relay **URL**, and the pool
multiplexes every subscription for a URL over one socket. During the window, anything addressed to a
default relay rides that clearnet socket — including a kind-1059 giftwrap subscription, since the DM
list is also absent. Measure it (below) before deciding it is acceptable.

## Testing

Unit — the rule itself, per list type: absent event -> defaults; present-but-empty -> **empty**;
present-with-values -> values. The middle case is the regression guard and the one that fails today.

Unit (`TorRelayEvaluationTest`): an assumed relay resolves to `trustedRelaysViaTor` (both values);
.onion / money-op / DM keep their own policy while also listed as assumed; a non-assumed "new" relay
still resolves to `newRelaysViaTor`; an empty assumed set is byte-for-byte today's behaviour.

Unit: `isInMyRelayList` does **not** see assumed relays (guards Finding 2 permanently).

Device — the number that justifies the change. `relaytiming.sh` + `BootRelayDiag` census,
`VERBOSE_LOGS=true` benchmark build, fresh install each, counterbalanced, n>=3:
- primary: login -> first note; login -> own profile + follow list;
- secondary: relays opened / serving / events at the 20s census;
- guard: grep the verbose log for any request to a default relay during the window that is not for
  the account's own pubkey, and for any AUTH sent to one.

Harness traps (all in `[[fresh-install-routes-everything-via-tor]]`): the tablet raises its lock
screen during long waits (`wm dismiss-keyguard`, not just `KEYCODE_WAKEUP`); the login layout shifts
when the IME opens, so dismiss it before tapping fixed coordinates; `BACK` on the home screen exits
the app; always assert the run left the login screen before trusting its timing.

## Expected outcome

Approach the Tor-OFF column: ≈**-7s to first paint, ~2x relay coverage** in the first 20s, with
everything after the first event behaving exactly as today.

If the gain is materially smaller, the likely cause is that the feed is gated on outbox-discovered
relays (which stay "new", hence Tor) rather than the user's own list — in which case the win is
limited to profile and follows, and may not be worth the privacy cost. Decide on the numbers.

## Order of work

1. Fix the absent-vs-empty bug in the four helpers + tests. Independently correct; ship separately.
2. Add `relaysAssumedWhileUnknown` + `TorRelayState.assumedRelays` + the `useTor()` branch.
3. Device A/B. Keep only if it earns its keep.
