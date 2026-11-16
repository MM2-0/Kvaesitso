# EGG-09: User server list (`kind:10112`)

`status: draft`
`requires: NIP-01, EGG-01`
`category: optional`

## Summary

A user MAY publish a list of nests servers they prefer to host on. Clients
read this list to default-fill the `streaming` (relay) and `auth` (sidecar)
fields of a new `kind:30312` room (EGG-01), and to suggest peer rooms in
discovery surfaces.

This is the audio-rooms equivalent of the user-server-list patterns in
Blossom (BUD-03) and Mostr — but every entry carries **two** URLs because
the moq-relay and moq-auth sidecar live on different hosts in the
deployed reference (see EGG-01 §4).

## Wire format

`kind:10112` is a NIP-01 *replaceable* event. Each `["server", relay, auth]`
tag carries one deployment:

```json
{
  "kind": 10112,
  "pubkey": "<user pubkey hex>",
  "tags": [
    ["server", "<https URL of moq-relay>", "<https URL of moq-auth>"],
    ["server", "<https URL of moq-relay>", "<https URL of moq-auth>"],
    ...
  ],
  "content": "",
  ...
}
```

The first element of the tag is the literal string `"server"`. The second
is the moq-relay (WebTransport) URL — what the kind-30312 `streaming` tag
will end up with. The third is the moq-auth (JWT mint) URL — what the
kind-30312 `auth` tag will end up with.

The relative ordering MUST be preserved by receivers — earlier entries are
higher priority.

### Back-compat shapes

Earlier deployed clients used two looser shapes; receivers MUST tolerate
both, publishers MUST emit only the canonical 3-element form above:

1. **Legacy first-element name `relay`.** The earliest NestsUI iteration
   wrote `["relay", relay, auth]`. Receivers MUST accept this name as a
   synonym for `server`.
2. **Auth-URL omitted.** A 2-element `["server", relay]` (or
   `["relay", relay]`) entry has no auth URL on the wire. Receivers MUST
   derive the auth URL from the relay URL using the following rule, which
   matches the deployed nostrnests fallback:
   - Replace a leading `moq.` host label with `moq-auth.` (preserving the
     scheme and dropping any explicit port).
     Example: `https://moq.example.com:4443` → `https://moq-auth.example.com`.
   - If the relay host has no `moq.` prefix, prepend `moq-auth.`.
     Example: `https://relay.example.com:4443` → `https://moq-auth.relay.example.com`.
   - If the relay URL is unparseable, drop the entry.

   Receivers SHOULD NOT cache the legacy 2-element form — when re-publishing
   the user's list (EGG-09 rule 4), they MUST emit the 3-element form with
   the derived auth URL filled in.

## Behavior

1. Each `relay` and each `auth` value MUST be a fully-qualified URL beginning
   with `https://`. Receivers MUST drop entries that are not well-formed
   HTTPS URLs.
2. Receivers MUST de-duplicate entries by exact-string match on the relay
   URL (after trimming a single trailing `/`). Order of remaining entries
   MUST be preserved (the FIRST occurrence wins).
3. When the user opens the create-room sheet, the client SHOULD pre-fill
   the EGG-01 `streaming` and `auth` fields from the FIRST entry in the
   list. The `streaming` field gets the entry's relay URL; the `auth`
   field gets the entry's auth URL. Clients MUST NOT collapse the two
   into a single field.
4. Users MAY enumerate up to 64 servers. Receivers MUST tolerate longer
   lists by truncating to the first 64.
5. Hosts who list a server in their `kind:10112` are not declaring an
   alliance — they are merely advertising "I will probably create rooms
   here". Receivers MUST NOT use the list as a moderation signal.
6. The list is purely a defaults / discovery hint. A `kind:30312` event's
   own `streaming` / `auth` tags are authoritative for that specific room
   and override the user list at join time.

## Example

```json
{
  "kind": 10112,
  "pubkey": "abc...host",
  "created_at": 1714003000,
  "tags": [
    ["server", "https://moq.nostrnests.com:4443", "https://moq-auth.nostrnests.com"],
    ["server", "https://relay.example.org:4443",  "https://moq-auth.example.org"]
  ],
  "content": "",
  "id": "...",
  "sig": "..."
}
```

## Compatibility

A peer that does not implement EGG-09 simply does not pre-fill server
fields and does not surface "rooms hosted by this user are usually on…"
hints in profile screens. All other interop is unaffected.
