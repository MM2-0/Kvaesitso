# Relay AUTH permissions — state review & redesign

**Date:** 2026-08-03
**Module:** `amethyst` (+ `commons`)
**Status:** Implemented — see "As-built" below
**Mockups:** `2026-08-03-auth-permissions-redesign.html` (open in a browser)
**Supersedes the UI half of:** `2026-07-01-auth-permission-architecture.md`

## As-built

Shipped as designed. Where it diverged or went further:

- **Purpose now comes from `SubPurpose`.** `commons/relayauth/SubPurposeToAuthPurpose.kt`
  maps the purpose every `ExplainedFilter` already declares onto `AuthPurposeKind`;
  `RelayAuthPurposeDeriver` reads it and keeps tag-shape inference only as the
  fallback for a plain `Filter`. A declared `READ_VENUE` prefers the assembler's
  `entityIds` over sniffing `#e`, so note ids can no longer arrive as "venues".
- **`MY_INBOX` + `THREAD` added**, and both added to the ledger's
  `hasAttributablePurpose` — they name no counterparty by design, so without that
  they would have fallen to a silent DENY instead of ASK.
- **`MY_OWN_RELAY` is reachable from the UI, not the deriver.** One socket is
  shared by every logged-in account, so a shared purpose list cannot know *whose*
  relay it is. The prompt carries `isMyOwnRelay` per account instead.
- **Prompts are now per account, not per challenge.** The dialog names the account
  whose npub would be revealed, so the old "first account to ASK answers for
  everyone" shortcut had to go, and `RelayAuthPromptBus.inFlight` is keyed by
  `(relay, account)`. In practice this rarely means two dialogs — `isFirstParty`
  already drops every account without its own reason to be on the relay.
- **`rememberVenueLabel` only get-or-creates a channel for `POST_VENUE`** (whose id
  really is a channel root); a read looks up an existing channel and otherwise
  degrades. This is what stops the phantom-channel side effect.
- **`TopBarWithBackButton` gained an `actions` slot** (defaulted, so no other caller
  changed) to carry the account chip.
- **Renamed rather than reused every string whose meaning or placeholders changed**
  (`relay_auth_reason_*` → `relay_auth_why_*`, `relay_auth_prompt_title` →
  `relay_auth_login_as`, the toggles → `relay_auth_auto_*`, …). A stale Crowdin
  translation binding to a reused key would have shown the *old* copy — "Notify:"
  where the new sentence belongs — or silently dropped a new `%1$s`. New keys fall
  back to the new English until Crowdin catches up. The 401 now-orphaned
  translations were deleted from the 11 locale files that carried them.
- **The 60s timeout now runs from display, not from arrival.** Found while
  answering "what happens if several auths are requested inside one 60s window?".
  The host shows one dialog at a time but every prompt's deadline started when its
  challenge arrived, so a burst of relays meant prompt 2..N counted down while
  invisible. They expired unseen — a silent deny — and if the user did reach one
  after it expired, the click was swallowed whole: `complete()` is a no-op on a
  resolved deferred, so no auth was sent and not even the "always allow" rule was
  written. `RelayAuthPrompt.markShown()` now starts the window, gated on a host
  actually collecting (no UI → the old arrival clock, which is what the timeout was
  always for) and capped by `queueWaitMs` so a stuck queue can't suspend a
  connection forever. A second challenge for the same (relay, account) rides along
  on the owner's answer with no deadline of its own — running one would let it
  resolve the shared deferred and tear down a dialog mid-read.
- **Corrected later:** this plan left the decision model alone, including the
  blanket `isFirstParty` gate on `CUSTOM`. That gate turned out to make
  `readFollows` ("…I'm reading someone I follow") unreachable — a follow's outbox
  relay is theirs, so it is never first-party for us, and every follow produced a
  prompt with the toggle explicitly on. `RelayAuthResolver.customAllows` now
  checks that one category ahead of the gate; the other three still require it.
- **Still not done:** what a timeout should *look like*. It is now an honest 60s of
  visible time rather than a clock the user never saw, but it is still a dialog
  that vanishes and an event left pending in the outbox with no feedback. That
  needs a product decision, not a layout.

Verified: `:amethyst:testFdroidDebugUnitTest` 1096 tests green (38 in the relay-auth
suites, 5 of them new), `:commons:jvmTest` 1446 green, `spotlessApply` clean.

Scope is the two **NIP-42 AUTH** permission surfaces only — not the napplet/nSite
permission screens, not the Android runtime permission prompts.

| Surface | File |
|---|---|
| The AUTH prompt dialog | `amethyst/.../service/relayClient/authCommand/compose/RelayAuthPromptHost.kt` |
| The AUTH settings screen | `amethyst/.../ui/screen/loggedIn/relayauth/RelayAuthSettingsScreen.kt` |

## Findings

### 1. The same fact, restated up to four times

On the commonest prompt (`SEND_DM`, one recipient) the recipient is named in the
title, in the purpose label, in the avatar row, and again in the red consequence
line — four mentions of one name, three askings of one question, across six
stacked blocks (icon, title, boilerplate paragraph, relay chip, person row,
consequence).

The boilerplate `relay_auth_prompt_message` renders on *every* state, and the one
genuinely privacy-relevant fact — **which account** is about to be revealed — is
never shown, on an app that answers AUTH per account
(`AuthCoordinator.signWithAllLoggedInUsers`).

### 2. Four buttons, three of which mean yes

`Allow once` / `Always deliver my messages` / `Always allow this relay` /
`Block this relay`. Two of the yeses have completely different blast radii and
nothing on screen says so: `relay_auth_always_deliver` switches
`defaultRelayAuthPolicy` to `CUSTOM` and turns on **two** account-wide toggles
(`changeRelayAuthTrustMessageFollows`, `changeRelayAuthTrustMessageStrangers`).
A dialog button should not rewrite settings the user cannot see.

### 3. The reason shown is often not the reason

`RelayAuthPurposeDeriver` re-infers intent from raw filter shape —
`authors` → `READ_OUTBOX`, `#e`/`#a` → venue, `p`-tags on a pending event →
`NOTIFY_INBOX`. Two common cases fall through wrong:

- **Downloading your own replies/zaps.** `filterNotificationsToPubkey` is
  `#p = me` with no `authors` — it matches no branch, so it sets
  `unattributedRead` and contributes nothing. The dialog then either shows the
  blank `OTHER` copy ("Use this relay") or, when unrelated pending traffic shares
  the socket, borrows that traffic's **"Notify:"** label and facepile. The user is
  *reading*; the prompt says they are *writing*.
- **Opening a thread.** `ReactionsFilterAssembler` fetches likes/zaps/reposts with
  `#e = [noteIds]`. Any `#e` filter is classified `READ_VENUE`, so the prompt asks
  **"Open 3f8a12c9?"**. Worse, `rememberVenueLabel` treats any 64-hex venue id as a
  NIP-28 channel and calls `checkGetOrCreatePublicChatChannel` — minting a phantom
  channel in `LocalCache` for a note that was never a chat, plus a metadata
  subscription for it.

**The fix is upstream and already exists.** `ExplainedFilter` carries
`purpose: SubPurpose` (`NOTIFICATIONS`, `DIRECT_MESSAGES`, `PUBLIC_CHATS`, …),
`accountPubKeys` and `entityIds` on every filter the app opens. The auth path
discards it and re-guesses. Reading the declared purpose removes this whole class
of mis-attribution and unlocks two states the prompt cannot express today: *your
own inbox* and *this conversation*.

## State inventory

### Prompt (`RelayAuthPromptHost`)

| ID | Trigger | Title today | Verdict |
|---|---|---|---|
| P1 | `SEND_DM`, 1 recipient | Send your message to Alice? | redundant |
| P2 | `SEND_DM`, 3+ recipients | …to Alice and others? + facepile | redundant |
| P3 | `NOTIFY_INBOX` | Notify Alice? | often wrong |
| P4 | `READ_OUTBOX` | Load profile, posts and engagement from Alice? | verbose |
| P5 | `POST_VENUE` | Post to Bitcoin Devs? | empty body |
| P6 | `READ_VENUE` | Open Bitcoin Devs? | empty body |
| P7 | 2+ purposes live | title of the winner only, labelled sections stack | scrolls |
| P8 | `OTHER` | Confirm it's you to this relay? | honest but blank |
| P9 | metadata not loaded | …to `a1b2c3d4`? | raw hex as a name |
| P10 | `#p = me` read (own inbox) | falls to P8, or borrows P3 | unexpressible |
| P11 | `#e` read on note ids | Open `3f8a12c9`? | wrong + side effect |

Venue purposes (P5/P6) carry `venues`, never `counterparties`, but the body loop
iterates counterparties — so the middle of the dialog is an empty `Column`.

`MY_OWN_RELAY` has a string and a ledger branch but the deriver never emits it, so
`primaryNamed()` can never select it. The dialog also self-resolves to `DISMISS`
after 60 s — a silent deny the user never sees, leaving the event pending in the
outbox forever (the known limitation from the 2026-07-01 plan, now also a UI gap).

### Settings (`RelayAuthSettingsScreen`)

| ID | Condition | Verdict |
|---|---|---|
| S1 | policy = `ALWAYS` | ok |
| S2 | policy = `NEVER` | ok |
| S3 | policy = `CUSTOM` (default) | 7 descriptive paragraphs before the first relay |
| S4 | list empty | ok |
| S5 | `decision == null` (allowed by policy) | chip reads "Allow" |
| S6 | `decision == ALLOW` (explicit) | chip reads "Allow" — identical to S5 |
| S7 | `decision == DENY` | ok |
| S8 | rationale/last-used present or absent | facepile is unlabelled |
| S9 | relay on block list (kind 10006) | **not rendered at all** |

Further problems:

- Two headers ("When to authenticate" / "What to log in to") for one decision; the
  second group is a child of the third card in the first and nothing shows it.
- Every switch description restates its own title.
- The header says **Per-relay overrides** but the list is
  `allDecisions() ∪ allRationales() ∪ allLastUsed()` — most rows are a usage log.
- A bare ✕ next to a red chip reads as "block"; it actually clears the override
  *and* the rationale, so the row silently returns on next use.
- The three-state model (`ALLOW` / `DENY` / none) is driven by a two-state chip.
- Nothing names the account, though decisions are stored per account.

## Proposed

### Prompt: one title, one sentence, two buttons

```
⬤ inbox.nostr.wine            (relay icon + host — the thing being trusted)
  asks who you are

Log in as @vitor?             (constant title; names the account — new)

It won't accept your message for ⬤Alice Nakamoto from someone
it can't identify.            (the ONE variable line; avatar inline)

[ Remember for this relay              (•  ) ]   (switch, default off)

[ Not now ]  [ Log in ]
Never allow            How Amethyst decides
```

The one reason sentence replaces the old title + purpose label + avatar row +
consequence line. Four buttons become two plus a switch. Nothing in the dialog
writes a global setting; the link navigates to the settings screen instead.

`relay_auth_prompt_message` is **cut, not shortened**, and nothing replaces it.
"Log in" already means "identify yourself" and the title names the account, so a
sentence explaining the disclosure is boilerplate on every state — the same
duplication this proposal removes everywhere else. An npub under the title was
tried and dropped for the same reason: truncated, it can't be verified by eye, so
it is decoration that costs a line on every single prompt.

Reason sentence per state (P1′–P12′, incl. the two new kinds and the
never-reachable `MY_OWN_RELAY`) — see the copy deck in the HTML.

Multi-purpose (P7′) stops stacking labelled sections: the winning purpose keeps
the sentence, the rest collapse to one expandable line, so the dialog stays a
fixed height and the buttons never leave the fold.

### Settings: one decision, then two honest lists

- Account chip on the app bar.
- One radio group, "When a relay asks who you are": *Always log in* /
  *Decide per relay* / *Never log in*, one clause each that adds a fact the title
  doesn't already carry.
- Toggle group header carries the grammar — "Log in without asking when…" — so each
  row is a three-to-four-word completion with no description.
- The single mislabelled list splits into what it actually contains:
  **Exceptions** (explicit overrides only, two-way segmented `Always`/`Never`,
  plus **✕ Remove exception** — today's `Forget`, kept and retitled: it clears the
  override so the relay drops back to your rules and asks again next time,
  confirmed by an undo snackbar. It is unambiguous now in a way it wasn't: it no
  longer sits beside a red chip that also reads as "block", and it no longer wipes
  the usage history, which has its own list),
  **Blocked by your block list** (locked, kind 10006 — previously invisible),
  **Recent logins** (the log, with `SubPurpose` chips — "your inbox",
  "a conversation", "reading 4 follows" — instead of an unlabelled facepile).
- An empty state per list.

## What this needs before it can be built

1. **`RelayAuthPurposeDeriver`** — downcast `activeRequests` filters to
   `ExplainedFilter` and map `SubPurpose` → `AuthPurposeKind`; keep tag-shape
   inference only as the fallback for plain `Filter`s. Kills P10, P11 and the
   phantom-channel side effect.
2. **`AuthPurpose`** — add `MY_INBOX` and `THREAD`; make `MY_OWN_RELAY` reachable.
3. **`RelayAuthPromptHost.rememberVenueLabel`** — a venue purpose should carry its
   kind rather than sniffing 64-hex string length before get-or-creating a channel.
4. **`AuthCoordinator` / `RelayAuthPromptBus`** — carry the account into the prompt
   so the dialog can name it; re-examine the `askChoice` shortcut that reuses one
   answer across accounts once the dialog is account-specific.
5. **`RelayAuthSettingsScreen`** — split the list at the source.
6. **`RelayAuthPromptBus`** — decide what the 60 s silent `DISMISS` should look like.

The decision model is unchanged: `RelayAuthResolver`'s precedence ladder, the
per-relay gate and the four custom toggles all stay exactly as they are. This is a
copy and layout proposal plus one upstream correction so the copy describes what is
actually happening.
