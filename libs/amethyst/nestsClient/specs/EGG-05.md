# EGG-05: In-room chat (`kind:1311`)

`status: draft`
`requires: EGG-01`
`category: optional`

## Summary

In-room text chat reuses NIP-53's `kind:1311` "live activities chat message"
event, addressed to the room via an `a`-tag. No new event kind, no new
relay convention.

A peer that does not implement EGG-05 simply does not render or emit chat;
all other EGGs continue to interop.

## Wire format

```json
{
  "kind": 1311,
  "pubkey": "<author pubkey hex>",
  "tags": [
    ["a", "30312:<host pubkey hex>:<room d>", "<relay hint>"]
  ],
  "content": "<plaintext message>",
  ...
}
```

The `content` field carries the plaintext (UTF-8) message.

## Behavior

1. Senders MUST emit chat events with `kind:1311` and exactly one `a`-tag
   pointing at the room (EGG-01).
2. Receivers MUST subscribe to relays declared on the room's `relays` tag
   (EGG-01) with filter `{kinds:[1311], "#a":["30312:<host>:<d>"]}`.
3. Receivers MUST de-duplicate by event id when the same message arrives
   from multiple relays.
4. Receivers MUST sort the chat panel by `created_at` ascending so the
   newest message is at the bottom.
5. Senders MUST NOT include private content. The chat plane is public;
   any peer subscribed to the room's relays sees every message.
6. Senders MAY include other NIP tags (`p` mentions, `e` quotes, `q`
   embeds). Receivers MAY render these per the relevant NIP; lacking
   support MUST NOT cause the message to be hidden.
7. A peer SHOULD NOT publish chat events while `status` of the
   `kind:30312` is `closed` — but receivers MUST tolerate them gracefully
   in case of a race against the host's close.
8. **Content size.** Senders SHOULD keep `content` under 8 KB of UTF-8.
   Receivers MUST tolerate up to 64 KB and MAY truncate or drop messages
   above that threshold (with a visible "[message truncated]" marker).
9. **Rate limiting.** Receivers SHOULD render at most 3 messages per
   second per author in the live chat panel; over-quota messages remain
   visible on scroll-back but do NOT animate / scroll the panel.

## Example

```json
{
  "kind": 1311,
  "pubkey": "def...co",
  "created_at": 1714003205,
  "tags": [
    ["a", "30312:abchost:office-hours-2026-04", "wss://relay.example"]
  ],
  "content": "Hi everyone, glad to be here!",
  "id": "...",
  "sig": "..."
}
```

## Compatibility

EGG-05 events flow on Nostr relays, not the audio plane. A relay that
implements only the audio half of nests can be paired with any NIP-01
relay to provide the chat half.

EGG-06 (reactions) targets `kind:7` events at the same `a`-tag. Chat and
reactions are independent: a peer MAY render reactions without rendering
chat or vice versa.
