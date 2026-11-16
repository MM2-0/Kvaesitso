# NIP-43 relay membership in geode

Status: implemented (2026-09-27).

PR #4236 added the NIP-86 role / invite-code RPCs (`createrole`, `editrole`,
`deleterole`, `assignrole`, `unassignrole`, `listclaims`, `createclaim`,
`deleteclaim`) and persisted their state in `BanStore` / `RuntimeConfig`, but
nothing consumed that state: geode advertised the methods and they were silent
no-ops. This plan makes them do what NIP-43 says.

## Survey

| Piece | Where | Status |
|---|---|---|
| NIP-43 event models (13534, 33534, 8000, 8001, 28934, 28936) | `quartz/nip43RelayMembers/*` | reused as-is |
| Roles, assignments, claims, allow list | `quartz/nip86RelayManagement/server/BanStore` | reused as-is |
| Local ingest bypassing policies | `NostrServer.ingest(event, skipVerify)` | reused (mirror path) |
| Hook for EVENTs addressed to the relay | — | **new**: `EventCommandHandler` in `quartz/nip01Core/relay/server` |
| Join / leave / republish engine | — | **new**: `quartz/nip43RelayMembers/server/RelayMembershipServer` (generic, any Quartz relay can use it) |
| Members-only write gate | `BanListPolicy` | extended: `membersOnly` |
| Role / claim RPC gating + post-RPC hook | `Nip86Server` | extended: `nip43Methods`, `afterMutation` |
| Relay key, config, NIP-11 `self` | geode `RelayEngine`, `StaticConfig`, `RelayIdentity`, `Main` | geode wiring |

## Decisions

**Membership is the NIP-86 pubkey allow list.** NIP-43 defines 13534 as "pubkeys
that have access to a given relay"; NIP-86's allow list is exactly the set of
pubkeys with write access, and NIP-86 already says `banpubkey` removes a pubkey
from it. Keeping one set avoids two lists drifting apart. So:

- join (28934) = `allowpubkey`; leave (28936) = `unallowpubkey`;
- `allowpubkey`, `unallowpubkey` and `banpubkey` over NIP-86 are membership
  changes and produce 8000 / 8001 + a fresh 13534;
- `[authorization].pubkey_whitelist` seeds the initial members.

**Members-only means closed even when empty.** Plain NIP-86 treats an empty
allow list as "no restriction". With membership on, that would leave a fresh
relay wide open until its first join, and the first join would suddenly close it.
`BanListPolicy(membersOnly = true)` rejects every non-member write with
`restricted: …` regardless of list size. Relay-authored events use
`NostrServer.ingest` and never see the policy; join / leave requests are
consumed before it. Reads are not gated — use NIP-42 + a read policy for that.

**Invite codes are reusable until revoked.** Neither NIP spells out single-use.
NIP-86 calls `listclaims` "invite codes currently accepted by the relay" and
gives revocation its own method (`deleteclaim`); NIP-43 lets users mint claims
(`createclaim`) to share. Consuming a code on first use would make shared invite
links fail for the second person, so a code keeps working until an admin deletes
it. The allow-list reason records which code admitted each pubkey (audit trail).

**Roles are independent of membership.** Assignments live in `BanStore`
regardless of whether the pubkey is currently a member; 13534 lists only members
(with their roles). Leaving keeps assignments, so a returning member gets them
back — role assignment is an operator decision, not the member's.

**Publishing is a reconcile.** `RelayMembershipServer.sync()` diffs the
`BanStore` against what the relay last published (read back from its own store
on first sync, so restarts don't republish) and signs only the difference:
8000 / 8001 per pubkey entering / leaving, one 13534 when members or roles
changed, one 33534 per new / edited role, one NIP-09 kind 5 (`a` =
`33534:<self>:<id>`, `k` = 33534) per deleted role. Every mutation path — join,
admin RPC, a state file edited while offline — converges on the same events, and
repeated syncs are no-ops. The first sync on a fresh relay emits 8000 for each
seeded member (they were added) plus the initial 13534.

Replaceable events are stamped `max(now, previous + 1)` so two edits within one
second still supersede each other (a `created_at` tie would be won by the lower
id, not the newer event), and a re-created role is stamped after its deletion's
tombstone.

**deleterole → NIP-09.** 33534 is addressable; NIP-43 defines no removal, so the
relay deletes the address with a kind 5 it signs itself — the standard way for an
author to retract an addressable event, and what the store already enforces.

**Join / leave run ahead of the policy chain** (`EventCommandHandler`):

- signature and id verified (parallel verify means nothing upstream checked it);
- `created_at` within `[membership].request_window_seconds` (default 300) of now → else `invalid:`;
- join: `claim` tag required → else `restricted:`; banned → `restricted:`; already a member → `OK true "duplicate: …"`; unknown / revoked code → `restricted: that is an invalid invite code.`; success → `OK true "info: welcome to <url>!"`;
- leave: NIP-70 `-` tag required (the spec's MUST) → else `invalid:`; not a member → `OK true "duplicate: …"`; success → `OK true "info: you have left this relay."`;
- neither request is stored or fanned out — a join carries the invite code, and
  both are ephemeral kinds anyway.

They don't require NIP-42 AUTH: the request's signature already proves the
author, and the relay is the recipient rather than a re-publisher (NIP-70's AUTH
rule is about accepting protected events for storage). A captured leave request
could be replayed within the window, which only re-removes the same user.

**Relay identity.** `[identity].secret_key` (nsec or hex) or
`[identity].secret_key_file` (created with a fresh key, mode 0600, if missing).
With membership on and neither set, the key is generated at
`<[admin].state_file>.relay-key`; with no state file either, an in-memory key
is used with a warning. When a key exists, NIP-11 `self` is forced to its pubkey
at boot (overriding a persisted doc). `43` is added to `supported_nips` iff
membership is on — and removed otherwise, since clients only send 28934 to
relays that advertise it.

**Off by default.** `[membership].enabled = false` keeps geode exactly as before
except that the eight role / claim RPCs are no longer advertised or accepted
(`method not supported`) — they were silent no-ops, which is what the review
flagged. Their persisted state is kept untouched in the state file.

## Not done

- Rate limiting join attempts (brute-forcing short invite codes).
- Read gating for members-only relays.
- Kind 28935 (invite request) — deprecated in the current NIP-43.
- Cleaning up 13534 / 33534 signed by a previous relay key after a key change.
