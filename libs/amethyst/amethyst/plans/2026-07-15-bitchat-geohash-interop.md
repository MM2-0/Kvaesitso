# Bitchat geohash chat interop

Status: **Phase 1 (public geohash channels) shipped.** Phase 2 (encrypted DMs)
is designed but not implemented.

## What Bitchat does (verified against `permissionlesstech/bitchat`
iOS + `bitchat-android`)

Bitchat's Nostr side has two chat features. Amethyst is a pure-Nostr client
(no BLE mesh / Noise identity), so only the Nostr halves are in scope.

### Public geohash channels ("location channels") — SHIPPED
- Message = **kind 20000** (ephemeral), content = plain UTF-8 text.
  Tags: `["g", geohash]` (required), `["n", nickname]` (optional),
  `["t","teleport"]` (optional). Optional NIP-13 `["nonce", …]` PoW, default 8
  bits, used to relax per-sender relay rate limits.
- Presence = **kind 20001**, only the `g` tag, empty content.
- Subscribe: `kinds:[20000,20001]`, `#g:[geohash]` (exact cell).
- Precision levels (geohash chars): building 8, block 7, neighborhood 6,
  city 5, province 4, region 2.
- Identity = a per-geohash throwaway key `HMAC-SHA256(deviceSeed, geohash)`,
  deterministic per (device, cell), unlinkable to the user's npub.
- **Relay routing is geographic and load-bearing:** a cell's traffic goes to the
  5 relays nearest the cell center, chosen from the public MIT-licensed
  `permissionlesstech/georelays` CSV both clients load. If Amethyst used any
  other relay set its messages would not rendezvous with Bitchat clients.

### Private DMs — NOT YET IMPLEMENTED (Phase 2)
- Standard NIP-17/59: rumor kind 14, seal kind 13, gift wrap kind 1059, wrap
  under a throwaway key, NIP-44 v2.
- **The kind-14 rumor content is NOT plain text.** It is
  `"bitchat1:" + base64url(<binary bitchat packet>)` — a `BitchatPacket`
  (TLV + a `NoisePayloadType` byte) carrying the private message, delivery ACKs,
  and read receipts. Full DM interop therefore requires porting that binary
  framing (`NostrEmbeddedBitChat.swift` / `NostrEmbeddedBitChat.kt`).
- Two DM flavors: geohash DMs (gift-wrapped to a participant's per-geohash
  pubkey) and stable-identity DMs (to an npub learned via Bitchat's mesh
  `[FAVORITED]:<npub>` handshake — mesh-specific, mostly N/A for a Nostr client).

## What shipped (Phase 1)

- **quartz** `experimental/bitchat/`: `GeohashChatEvent` (20000),
  `GeohashPresenceEvent` (20001), `GeohashKeyDerivation` (per-geohash key),
  registered in `EventFactory`. PoW reuses the existing `nip13Pow` `PoWTag`.
- **commons** `service/georelay/`: `GeoRelayDirectory` (closest-N by haversine,
  host tie-break, `:443` dedup), `GeoRelayCsvLoader` (runtime CSV fetch + fallback).
- **amethyst**: `GeohashChatScreen` + `GeohashChatViewModel` (live subscription +
  send), `GeohashChatDeviceSeed` (global encrypted seed store),
  `Account.signWithAndSendPrivately`, `Route.GeohashChat`, and a chat action on
  the geohash feed screen.
- **cli**: `amy geochat listen|send|keys` — the interop harness. Verified with a
  live send→relay→listen round-trip (kind 20000, PoW, `g`/`n` tags intact).

## Follow-ups

1. **Encrypted DMs (Phase 2).** Port the `bitchat1:` binary packet
   (`BitchatPacket` TLV + `NoisePayloadType`) into quartz, wrap/unwrap it in the
   existing NIP-17 stack (`GiftWrapEvent`/`SealEvent`/`ChatMessageEvent`),
   handle geohash DMs (to a per-geohash pubkey) and delivery/read receipts.
   Add `amy geochat dm` for interop testing.
2. **Desktop UI.** The shared pieces (quartz events, `GeoRelayDirectory`) are
   already cross-platform; add a desktop `GeohashChatScreen` equivalent.
3. **LocalCache integration (optional).** The current Android screen manages its
   own subscription/state rather than routing through `LocalCache`/the chatroom
   list. Integrating would give unread badges and a unified chat list, at the
   cost of a `Channel`/feed-filter/datasource fork.
4. **Location-driven channel picker.** Use `LocationState` to offer the
   region/province/city/neighborhood/block/building cells for the user's current
   position, plus a manual/teleport entry.
5. **Presence heartbeats + i18n.** Periodically emit kind 20001 while a channel
   is open; extract the hardcoded screen strings into `strings.xml`.
