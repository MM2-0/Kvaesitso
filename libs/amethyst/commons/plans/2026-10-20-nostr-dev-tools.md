# Nostr developer tools in Amethyst

Status: prototype (2026-10-20). The screens are shared UI in `commonsUI`, so Android and Desktop
both have them:
- a hub;
- a NIP-19 converter;
- an event inspector;
- a kind lookup: type a number and see the kind's name, then every way clients encode it, sampled
  from relays, grouped by client or by shape, each with tag usage and a real example;
- a kind browser;
- "find this event on relays";
- a relay console with REQ/COUNT builders, CLOSE for open subscriptions, a signed-event publisher
  and AUTH behind a confirm.

The rest of this plan is proposed.

## Why

Developers building on Nostr keep a browser tab of single-purpose tools open: nak web for
decoding, nostr-tools.com for keys and signatures, kinds.amethyst.social for "is anyone using kind
N", and websocketking.com to poke a relay. Amethyst already holds every primitive these tools
need. Quartz has bech32/TLV, id hashing and Schnorr verify, the kind registry, NIP-11/45/66/77, and
the fan-out accessories. `amy` already exposes most of them on the command line. The app only
lacked the screens.

## What exists elsewhere (surveyed 2026-10-20)

| Tool | What it does | Primitive it needs |
|--|--|--|
| [kinds.amethyst.social](https://kinds.amethyst.social) (`vitorpamplona/nostrkinds`) | Fans `{kinds:[k]}` out to ~990 hard-coded relays, 10 at a time. Pages each with `until`, with a 5 s idle timeout and ephemeral-key AUTH. Shows a per-relay status/count/last-seen table and the newest event. | REQ fan-out |
| [nostr-tools.com](https://nostr-tools.com) | Key generator, NIP-19 converter, event creator, fetcher, republisher, verifier, NIPs reference. | bech32, sign/verify, REQ, publish |
| [nak](https://github.com/fiatjaf/nak) / [nak web](https://nak.nostr.com) | The de-facto developer tool. The web version decodes anything pasted, compares the implied id to the stated one, checks the signature and fetches through hints. | everything |
| [Grimoire](https://grimoire.rocks) | A developer-mode client with tiling windows: `req`, `count`, `relay`, `conn`, `log`, `kinds`, `nips`, `decode`. | REQ console, relay log |
| noStrudel tools | Event Console (a REQ playground) and a raw-JSON publisher. | REQ, publish |
| [websocketking.com](https://websocketking.com) | A generic WebSocket client: connect, send frames, see frames. | raw socket |
| [nostr.watch](https://nostr.watch) | NIP-66 monitors (kind 30166/10166), dashboard, API. | NIP-66, NIP-11 |
| [metadata.nostr.com](https://metadata.nostr.com) | Kind 0/3/10002 history, diff and restore. | fan-out of old versions, publish |
| [Lightning Decoder](https://lightningdecoder.com) | LNURL / BOLT11 / BOLT12 decode. No NIP-57 debugger exists anywhere. | LNURL HTTP, BOLT11 |
| [Nostrbook](https://nostrbook.dev), [nips.nostr.com](https://nips.nostr.com) | Kind and NIP registries. | none |

Not reachable from the survey container (outage or proxy, unverified): nostr.band, nostrcheck.me,
api.nostr.watch.

## Survey of this repo

| Need | Already here | Use |
|--|--|--|
| NIP-19/21 decode and encode | `quartz/nip19Bech32` (`Nip19Parser`, `NPub`…`NEmbed`) | reused |
| Id and signature checks | `nip01Core/crypto/EventExt.kt` (`generateId`, `verifySignature`) | reused |
| Kind names and NIPs | `quartz/kinds/KindNames` (378 kinds); localized overlay `kindName()` in commonsUI | reused |
| Relay fan-out with per-relay outcome | `fetchAllWithHooks` → `FetchAllResult.doneReasons` (eose / closed / cannot / auth-refused) | reused |
| NIP-45 COUNT | `INostrClient.count(relay, filter)` | not needed yet; the console sends COUNT raw |
| Throwaway relay client | `BaseAccountViewModel.buildCrawlClient()`: relay-authenticated, blocked relays removed, events kept out of `LocalCache` | reused |
| Raw sockets that obey Tor | `AccountViewModelHost.websocketBuilder` | reused |
| NIP-66 relay discovery | `RelayDiscoveryEvent` (30166) | reused |
| Relay info / NIP-11 / latency | `RelayInformationScreen` (`Route.RelayInfo`) | linked, not duplicated |
| Active subscriptions | `ActiveSubscriptionsScreen` | linked from the hub |
| CLI twins | `amy decode`, `encode`, `verify`, `kind`, `nip`, `count`, `relay info` | logic kept in step |

No screen in the app decoded NIP-19 or verified an event. The only in-app tool was the "Copy raw
JSON" note action.

## What the prototype ships

Entry points:
- Settings › App settings › **Developer Tools** (searchable).
- A note's menu › **Inspect event**, next to "Copy raw JSON".

Headless code in `commons/…/devtools/` (CLI-safe, unit-tested in `DevToolsInspectorsTest`):

- `Nip19Inspector` takes npub, nsec, note, nevent, nprofile, naddr, nrelay or nembed, with or
  without `nostr:`, or a 64-char hex.
  - It returns typed rows (key, id, author, kind, d tag, `a` tag, relay hints, embedded JSON) and
    every other encoding of the same value.
  - Raw hex is ambiguous, so it is offered as npub, nprofile, note and nevent alike.
  - An nsec is flagged secret.
- `EventInspector` takes a bare event, a relay frame `["EVENT",sub,{…}]`, a client command
  `["EVENT",{…}]` or an nembed.
  - It recomputes the id and reports a mismatch with the computed value, and checks the signature.
  - It flags a `created_at` in the future and kinds missing from the registry.
- `KindSamplerViewModel` + `KindVariantCollector` are behind Kind Lookup.
  - The sampler asks each relay for its 200 newest events of the kind, 24 relays at a time, and
    stops starting new relays at 5,000 events.
  - The collector keeps only aggregates and the newest example per group, in two groupings:
    - by the NIP-89 `client` tag, exact string (so "Yakihonne", "YakiHonne" and "yakihonne.com"
      stay apart: that difference is itself an encoding);
    - by shape: the set of tag names plus the content format.
  - Each group reports events, authors, the clients behind it and its newest event. A group's
    snapshot is rebuilt only after it gains an event, so the progress publishes every 300 ms cost
    a sort, not a fold (5,000 events in ~4,900 shapes: ~16 ms down to ~0.5 ms per publish on the
    JVM).
  - Content format is one of empty, JSON object, JSON array, encrypted (NIP-04 `?iv=` or a NIP-44
    base64 payload) or text.
  - The relay set is the relays the app is connected to plus the account's search relays (kind
    10007, or the defaults), read when the lookup starts. It never asks NIP-66 monitors for more:
    a lookup should not open sockets to hundreds of relays the user never chose.
- `RelayProbeViewModel` is "find this event on relays".
  - Its relay set is the app's pool, the indexer, search and finder defaults, and, behind a
    switch that is on by default, every relay NIP-66 monitors reported on in the last day. The
    monitors are read from `relay.nostr.watch`, `relaypag.es` and `monitorlizard.nostr1.com`; on
    2026-10-20 each returned ~390 relays from 11–13 monitors.
  - It sends one `{ids:[id]}` REQ per relay, 24 relays in parallel, with a 6 s idle timeout and a
    20 s cap.
  - Each relay ends as one of found / none / auth required / refused / offline / timed out /
    waiting for Tor, taken from `doneReasons`.
- `FilterDraft` is the console's REQ/COUNT form.
  - Fields: kinds, authors, ids, one `#tag` with values, a since window, limit and NIP-50 search.
  - Authors, ids and `#p`/`#e`/`#a` values accept NIP-19 as well as hex.
  - An invalid token is reported with the field it is in.
- `RelayConsoleViewModel` is a raw WebSocket to one relay with every frame both ways, unparsed.
  - It tracks the REQs it opened (dropping any the relay CLOSED), so CLOSE offers exactly those.
  - It sends REQ/COUNT/CLOSE/EVENT through Quartz's own command serializers.
  - It answers a relay's AUTH challenge only after the user confirms a dialog that names the relay,
    the challenge and the signing npub.

Screens in `commonsUI/…/ui/screen/loggedIn/devtools/`:

| Route | Screen |
|--|--|
| `DevTools` | `DevToolsHubScreen` |
| `DevToolsNip19(input?)` | `Nip19ToolScreen`: decode, re-encode, open in Amethyst, crawl the kind |
| `DevToolsEventInspector(noteKey?)` | `EventInspectorScreen`: verdict, fields, tag chips, content, JSON, then find on relays / crawl kind / open |
| `DevToolsKinds` | `KindBrowserScreen`, the hub's Kind Lookup entry: search by number, name or NIP; tapping a kind opens its lookup, and an unknown number still offers a crawl |
| `DevToolsKindUsage(kind)` | `KindLookupScreen`, opened on a kind: a kind field whose one action is search (stop while that kind is running; searching the same kind again re-runs it). Then the kind's name and NIP when the registry knows it, one status line with a progress bar, and one card per client or per shape: its share, event/author counts, the clients behind a shape, and the newest event's JSON, shown open. The tags and content are read from the JSON, not re-rendered as chips |
| `DevToolsEventFinder(eventId)` | `EventFinderScreen`: the same table for one event id |
| `DevToolsRelayConsole(url?)` | `RelayConsoleScreen`, laid out like a chat. On top, a connection bar: an address field with Connect and the user's relays as suggestions, then one status line (relay, ping, deflate, open subs) with Disconnect and the AUTH offer. Then the frame log. At the bottom, closable chips for open REQs, then a composer: + opens a builder sheet (REQ, COUNT or EVENT tabs, errors on the field, exact frame previewed) and the field sends raw frames. |

Tor: the desktop and Android socket builders do not dial a Tor-routed relay until Tor is up
(`WebsocketBuilder.canConnect`). With the default "Untrusted Relays → Tor", every relay outside
the user's lists is Tor-routed. So the probe marks such a relay **Waiting for Tor** at once,
instead of burning the 20 s deadline and calling it a timeout. The console says the same instead
of hanging on "Connecting".

Checked by hand on the desktop app at phone size, read-only npub login:
- The note menu opens the inspector on that note, which reports a valid id and signature.
- The NIP-19 converter decodes an nevent and re-encodes it.
- The kind browser search works.
- Kind Lookup on 30023 sampled 587 events from 4 of 10 relays (7 connected + 3 search) in about
  2 s, in 34 client groups; kind 1 gave ~570 events in 31 groups. (Before 2026-10-21 it also crawled
  every NIP-66-reported relay: ~3k events from ~220 relays in about a minute.)
- The relay console built and exchanged REQ (npub converted to hex, since applied), COUNT and
  CLOSE with nos.lol.
- AUTH could not be exercised: a read-only login cannot sign.

Not checked on an Android device.

Choices worth reviewing:
- **Kind Lookup shows encodings, not relay coverage.** Which relays hold a kind is not the
  question it answers, so the per-relay table lives only on "find this event". Shape grouping is
  noisy on kinds with many optional tags (30023: ~300 shapes in a 3k sample); grouping by client
  is the readable default.
- **The crawl runs on `buildCrawlClient()`**, so crawled events never enter `LocalCache`, and the
  account's blocked relays (kind 10006) are never contacted.
- **The console bypasses the relay pool** on purpose: no reconnects, no automatic AUTH, no dedup. A
  developer sees exactly what the relay sent.

## Next (proposed), ranked by value for cost

1. **Rebroadcast from the event finder.** "Send to the relays missing it" through
   `publishAndConfirmDetailed`, with a per-relay `OK` and message. This is the companion users ask
   for, and nostr-tools.com's republisher.
2. **REQ playground.** A filter editor (kinds, authors, tags, since/until, limit) over chosen relays,
   with EVENT/EOSE/CLOSED/NOTICE live, sitting on `subscribeAsFlow`. The console covers the raw case;
   this is the structured one (Grimoire `req`, noStrudel Event Console).
3. **Replaceable history and restore** for kinds 0, 3 and 10002. Collect every version from
   reachable relays, diff them, and republish one. This answers the recurring lost-follow-list
   crisis (metadata.nostr.com, nak).
4. **Zap / LNURL debugger.** Fetch lnurlp, check `allowsNostr`/`nostrPubkey`, decode BOLT11, and
   validate a receipt with `ZapReceiptValidator`. No tool for this exists anywhere, and zap
   failures are a top support issue.
5. **NIP-05 checker.** Fetch `nostr.json`, check the pubkey, CORS and redirects, and list relays and
   `nip46`. Reuses `Nip05Client`.
6. **Outbox inspector for a user.** Their NIP-65 read/write relays, whether each is reachable, and
   where Amethyst looks for their notes, for "why can't I see X" reports.
7. **Relay traffic log.** The last N frames per pooled relay, from `RelayLogger`, beside Active
   Subscriptions.
8. **Event creator / signer.** Raw JSON to a signed event, with an option to sign with a throwaway
   key. Publishing on the user's key needs a confirm step.
9. **NIP-46 bunker tester.** Connect, `get_public_key`, then sign/encrypt round trips with timings.
10. **Blossom check.** BUD-01/02 capabilities, a test upload with hash check, and blob presence
    across the kind 10063 servers.
11. **NIPs reference.** `amy nip` already fetches NIP text. It could be a screen, linked from every
    "NIP-xx" chip.

Also proposed:
- A desktop menu bar item (`DesktopMenuBar`) for the hub.
- "Open in relay console" from `RelayInformationScreen`.
- Moving `RelayProbeViewModel.MONITOR_RELAYS` into `defaults/` once a second caller needs it.

## Open questions

- Should the hub be hidden behind a "developer mode" switch, or always reachable from settings? It
  is always visible in this prototype.
- Should the event inspector offer "re-sign as me" for an id-mismatch event? That would publish
  under the user's key, so it is left out for now.
