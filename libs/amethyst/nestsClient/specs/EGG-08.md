# EGG-08: Scheduling

`status: draft`
`requires: EGG-01`
`category: optional`

## Summary

A host can announce a room before it opens by publishing a `kind:30312`
with `status="planned"` and a `["starts", <unix-seconds>]` tag. Listing
clients render these as upcoming-room cards; the audio plane stays cold
until the host transitions the room to `open`.

## Wire format

```json
{
  "kind": 30312,
  "pubkey": "<host pubkey hex>",
  "tags": [
    ["d",       "<room d>"],
    ["room",    "<room name>"],
    ["status",  "planned"],
    ["starts",  "<unix seconds>"],
    ["service", "<...>"],
    ["endpoint","<...>"],
    ["p", "<host pubkey>", "<relay hint>", "host"]
    // ...other EGG-01 tags
  ],
  "content": "",
  ...
}
```

The `starts` value is a base-10 ASCII unix timestamp in seconds. Clients
that need finer-than-second precision are out of scope; round to the
nearest second.

## Behavior

1. A planned room MUST carry `status="planned"` AND a `starts` tag with
   a non-empty numeric value. A planned room without a `starts` value
   MUST be treated as un-renderable (rooms with unknown start times
   pollute the listing UI).
2. The `starts` value SHOULD be in the future at the time the event is
   published. Receivers MUST tolerate past values: a planned room whose
   `starts` is in the past renders as "starts soon" rather than
   surfacing the past time.
3. To start a planned room, the host MUST re-publish the same `(kind, d)`
   with `status="open"` and a higher `created_at`. The host MAY drop the
   `starts` tag at this point (no longer informative).
4. To cancel a planned room, the host MUST re-publish the same
   `(kind, d)` with `status="closed"`. The `starts` tag SHOULD be
   preserved so receivers can label the cancellation
   ("Cancelled — was scheduled for ...").
5. Planned rooms MUST NOT mint moq-auth tokens (EGG-02). The auth sidecar
   MUST reject `POST /auth` requests targeting a `(host, room d)` whose
   most-recent `kind:30312` is `status="planned"`, returning HTTP 403
   with `{"error":"room_closed"}` per the EGG-02 error taxonomy. (The
   auth path becomes available the moment the host re-publishes with
   `status="open"`; clients SHOULD NOT pre-mint tokens against `starts`.)
6. Listing UIs SHOULD sort planned rooms ahead of live ones until 5
   minutes before `starts`, then promote the room to the same surface as
   live rooms (the host is unlikely to be more than 5 minutes late).
7. A peer MUST NOT use `["expiration", <ts>]` (NIP-40) on a planned
   `kind:30312`. The implicit 8-hour staleness window from EGG-01 is
   the only auto-close mechanism.

## Example

```json
{
  "kind": 30312,
  "pubkey": "abc...host",
  "created_at": 1714000000,
  "tags": [
    ["d", "monthly-q-and-a"],
    ["room", "Monthly Q&A"],
    ["summary", "Bring your questions"],
    ["status", "planned"],
    ["starts", "1714172400"],
    ["service",  "https://moq.nostrnests.com"],
    ["endpoint", "https://moq.nostrnests.com"],
    ["p", "abc...host", "wss://relay.example", "host"]
  ],
  "content": "",
  "id": "...",
  "sig": "..."
}
```

## Compatibility

Receivers without EGG-08 see a planned room as `status="planned"` and
SHOULD treat it as un-joinable per EGG-01 rule 6 (status-not-`open`
means non-renderable for live UIs). The room becomes joinable as soon
as the host re-publishes with `status="open"`.
