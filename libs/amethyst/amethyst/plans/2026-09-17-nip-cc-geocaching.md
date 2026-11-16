# NIP-CC (Geocaching) — gap analysis for Quartz and Amethyst

Status: **steps 1–2 done** — the Quartz protocol package (`quartz/…/nipCCGeocaching/`) with its
tests, and the Amethyst read path: caches and found logs are stored, rendered, searchable, and
appear in the geohash/AroundMe feeds. Steps 3 onwards (cache detail screen, composing, the
verification QR flow, curation lists) are unstarted.

Spec: <https://github.com/nostr-protocol/nips/blob/master/CC.md> (merged into
`master`; listed in the NIPs README at line 117 and in the kind tables for
`7516`, `7517`, `37516`, `37517`). Read at 2026-09-17.

## 1. What the spec defines

| Kind    | Class       | Purpose                                                                 |
|---------|-------------|-------------------------------------------------------------------------|
| `37516` | addressable | Geocache listing — name, location, difficulty/terrain/size, hint, …       |
| `7516`  | regular     | Found log — claims a find, optionally carrying an embedded `7517`         |
| `7517`  | regular     | Verification — **signed by the cache's verification key**, not the finder |
| `37517` | addressable | Curation list — ordered `a` references to `37516` listings                |
| `1111`  | existing    | DNF / note / maintenance / archived logs, as NIP-22 comments on the cache |

Secondary mechanics: `n` type modifiers (`first-to-find`, `art`) with a
"one per category, ignore unknown" rule, an `F` tag that locks in the
first-to-find winner, a `mission` ("Key Quest") tag, and a client SHOULD to
ROT13 hints so they don't spoil.

### Confirmed with a grep, not from memory

At the time of the survey,
```
grep -rn "37516\|7517\|37517\|[Gg]eocach" --include=*.kt
```
returned only incidental hex substrings in unrelated tests — no geocaching code anywhere in
`quartz`, `commons`, `commonsUI`, `amethyst`, or `cli`. The Quartz half of that gap is now
closed; the rest stands.

## 2. Quartz — new code

New package `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nipCCGeocaching/`,
laid out like `experimental/roadstr/` (event + `TagArrayExt` + `TagArrayBuilderExt`
+ `tags/`), which is the closest existing analogue: a geohash-anchored,
user-submitted place event.

```
nipCCGeocaching/
├── listing/
│   ├── GeocacheListingEvent.kt          # 37516, BaseAddressableEvent + SearchableEvent
│   ├── TagArrayExt.kt / TagArrayBuilderExt.kt
│   └── tags/
│       ├── CacheNameTag.kt              # "name" (required)
│       ├── DifficultyTag.kt             # "D", 1..5, reject out-of-range
│       ├── TerrainTag.kt                # "T", 1..5
│       ├── CacheSizeTag.kt              # "S" -> enum micro|small|regular|large|other
│       ├── CacheTypeTag.kt              # "t", defaults to `traditional` when absent
│       ├── TypeModifierTag.kt           # "n" -> first-to-find | art (+ category)
│       ├── HintTag.kt                   # "hint"
│       ├── MissionTag.kt                # "mission", first-wins if duplicated
│       ├── VerificationKeyTag.kt        # "verification" (hex pubkey)
│       └── FirstToFindWinnerTag.kt      # "F" (hex pubkey), first-wins
├── log/
│   ├── GeocacheFoundLogEvent.kt         # 7516
│   └── tags/EmbeddedVerificationTag.kt  # "verification" holding a 7517 as JSON
├── verification/
│   ├── GeocacheVerificationEvent.kt     # 7517
│   ├── tags/FinderCacheTag.kt           # the composite "a" tag — see §2.3
│   └── GeocacheVerificationValidator.kt # the 4-step check from the spec
├── curation/
│   ├── GeocacheCurationListEvent.kt     # 37517, BaseAddressableEvent + SearchableEvent
│   └── tags/ThemeTag.kt, MapStyleTag.kt # "theme", "map"
├── comment/GeocacheLogTypeTag.kt        # "t" on kind 1111: dnf|note|maintenance|archived
└── FirstToFindResolver.kt               # winner selection + F lock-in precedence
```

### 2.1 Reuse — do not re-implement

The listing event is mostly existing tags wearing a new kind number:

| Spec tag        | Reuse                                                                  |
|-----------------|------------------------------------------------------------------------|
| `d`             | `nip01Core/tags/dTag`                                                    |
| `g`             | `nip01Core/tags/geohash/GeoHashTag` + `geohashes()`                      |
| `image`         | `nip23LongContent/tags/ImageTag`                                         |
| `r`             | `nip51Lists/tags/RelayTag` (`"r"`)                                       |
| `a`             | `nip01Core/tags/aTag/ATag` (curation list + found log)                   |
| `title`, `description` (37517) | `nip51Lists/tags/TitleTag`, `.../DescriptionTag`          |
| embedded 7517   | `Event.fromJson(...)` (same trick `ZapReceiptEvent.zapRequest()` uses)        |
| signature check | `Event.verifySignature()` in `nip01Core/crypto/EventExt.kt`              |
| kind 1111 logs  | `nip22Comments/CommentEvent` **as-is** — it already models `A/K/P` + `a/k/p` addressable root+parent (`rootAddress()`, `replyAddress()`, `hasRootAddress()`). Only the `t` log-type vocabulary is new. |
| required-tag gate | `containsAllTagNamesWithValues(REQUIRED_FIELDS)`, as `ClassifiedsEvent.isWellFormed()` does |

`GeoHashTag.geohashMipMap()` already emits the full precision ladder, but
**reversed and starting at 1 char**; the spec asks for precisions 3–9 in
coarse-to-fine order. Add a bounded variant (`geohashMipMap(min, max)`) rather
than publishing 1- and 2-character geohashes that are useless for proximity
search and noisy on relays.

### 2.2 Type modifiers need a category model, not a string set

Rule 2 ("at most one `n` per category, first occurrence wins") and rule 4
("ignore unrecognised values") mean `n` cannot be a flat `Set<String>`. Model it
as `enum class TypeModifierCategory { CLAIM_SEMANTICS, PRIZE_NATURE }` with
`first-to-find -> CLAIM_SEMANTICS`, `art -> PRIZE_NATURE`, and a parser that
keeps the first value per category and drops unknowns. Forward compatibility is
an explicit spec requirement, so unknown values must not make the event
unparseable.

### 2.3 Spec warts to handle defensively

1. **The `7517` `a` tag is not a NIP-01 `a` tag.** It is
   `"<finder-pubkey-hex>:<geocache-naddr>"` — two colon-joined parts, where a
   real `a` value is `kind:pubkey:d`. `Address.parse` will not parse it: a 2-part
   value fails the `parts.size > 2` guard, falls through to the `naddr1` branch,
   doesn't start with `naddr1` either, and returns null **after logging a
   warning** — so nothing silently misreads it, but any generic `a`-tag consumer
   that meets a `7517` will spam `AddressableId` warnings. It needs its own
   `FinderCacheTag` parser, and the `naddr` half goes
   through `nip19Bech32/entities/NAddress`. Worth an upstream issue; implement
   the spec as written, tolerate the sane `kind:pubkey:d` form on read.
2. **`7517` is signed by a key the user does not own.** The finder scans a QR at
   the cache carrying the verification *private* key, then signs a `7517` with
   it. That cannot go through the account's `NostrSigner` — it needs an ephemeral
   `NostrSignerInternal(KeyPair(privKeyFromQr))`. That private key must never
   touch the Keystore, account state, or a log line. See §4.
3. **`created_at` is forgeable, so first-to-find ordering is advisory.** The spec
   says so itself: earliest `created_at`, ties broken by ascending event `id`,
   *until* the owner publishes an `F` tag — after which `F` wins unconditionally.
   `FirstToFindResolver` should encode exactly that precedence and the UI should
   mark a pre-`F` winner as provisional.
4. **`37515` is referenced but never defined.** The 37517 `a`-tag prose says
   "kind 37516 or 37515". Accept both on read; only ever write `37516`.
5. **`NIP-GD.md` (Good Deed) does not exist.** The `mission` tag's cross-reference
   is a dangling link (`raw.githubusercontent.com/.../NIP-GD.md` → 404). Implement
   `mission` as free text; do not build a Good-Deed integration against a spec
   that isn't published.
6. **`archived` is an `n`-modifier-shaped value living in `t`.** Owners archive a
   cache by adding `["t", "archived"]` to the *listing*, while `t` on the listing
   otherwise means cache *type* (`traditional`/`multi`/`mystery`) and `t` on a
   *comment* means log type. Three meanings, one tag name — parsers must be
   scoped per kind, not shared.

### 2.4 Registration points (each one is a real integration, not a formality)

- `quartz/utils/EventFactory.kt` — 4 new `KIND ->` branches. Without this the
  events parse as bare `Event` and every accessor above is dead code.
- `quartz/kinds/KindNames.kt` — 4 `KindName(..., "CC")` entries.
- `quartz/nip50Search/SearchableKinds.kt` — add `7516`, `37516`, `37517` if they
  implement `SearchableEvent`. **`SearchableKindsTest` sweeps kinds 0–65535 and
  asserts this list is exactly the searchable set**, so a missed entry lands as a
  failing test naming the number. `indexableContent()` for `37516` should be
  name + content; the `hint` deliberately should *not* be indexed (searching for
  a hint is spoiling).
- Tests under `quartz/src/commonTest/.../nipCCGeocaching/`: round-trip each kind
  against the spec's own example JSON, the `D`/`T` range guards, the
  one-`n`-per-category rule, the `F`-beats-`created_at` precedence, and a
  verification event whose signature does *not* match the `verification` pubkey.

## 3. Amethyst — new code

Good news: the expensive parts already exist and were built for road events
(kind 1315/1316).

**Reuse as-is:**
- `amethyst/ui/note/creators/location/LocationPickerMap.kt`,
  `LocationPreviewMap.kt`, `MapPinIcon.kt`, `GeohashLocationPickerDialog.kt` —
  an osmdroid/OSM interactive map with markers, tap-to-place, and a geohash
  picker dialog.
- `service/location/LocationState` + the `AroundMe` top-nav feed for "caches
  near me".
- `commons/.../relayClient/geohash/FilterPostsByGeohash.kt` — adding
  `GeocacheListingEvent.KIND` to `PostsByGeohashKinds` is a one-line change that
  puts caches into the existing geohash feed.
- NIP-22 comment rendering and composing — DNF/note/maintenance logs are already
  displayable threads once the root kind is known.

**New:**
- `commonsUI/.../ui/note/GeocacheCard.kt` — cache card (name, D/T/S badges,
  type + modifier badges, distance, ROT13-toggle hint), modelled on
  `RoadEventCard.kt`.
- `amethyst/ui/note/types/Geocache.kt` + dispatch branches in
  `ui/note/NoteCompose.kt` (~line 1405/1450 is where `ClassifiedsEvent` and
  `RoadEventReportEvent` branch) and in `ThreadFeedView.kt`.
- `LocalCache.kt` — consume the four kinds (the `is ClassifiedsEvent,` /
  `is RoadEventReportEvent,` groups at ~3759/3933 are the template).
- Cache detail screen: map, logs tab, "Log a find" / "Log DNF" actions, and —
  for a `first-to-find` cache with any valid verified find — the spec's
  "render as effectively archived, hide find affordances, show the winner".
- Cache composer (owner side): map picker → geohash ladder, D/T/S pickers, hint,
  optional verification keypair generation + QR export, mission.
- QR scan → `7517` signing flow (see §4).
- Curation list (37517) browse + detail, honouring `theme`/`map` as *defaults*
  the user can override.
- ROT13 helper — **does not exist anywhere in the repo** (`grep -i rot13` is
  empty). Small, belongs in `commons` next to the other text utils, with the
  hint rendered rotated and a tap to reveal.
- Strings for every label above (`commonsUI` `composeResources`), plus any new
  Material Symbol codepoints — which means running
  `./tools/material-symbols-subset/subset.sh` and committing the regenerated
  `.ttf`, or the icon renders as tofu.

## 3.5 Interoperability — checked against the network, not just the spec

Two other implementations exist: **treasures.to** (the originating client, whose caches are the
spec's examples) and **Lightning Piggy** (`BenGWeeks/lightning-piggy-mobile`, whose
`src/services/nostrPlacesService.ts` is a readable second opinion). A corpus of real events was
pulled off relay.damus.io / nos.lol / relay.primal.net / nostr.wine with `amy fetch` and is
pinned at `quartz/src/commonTest/resources/nipcc.interop.json`; `NipCCInteropTest` parses it.

What the network confirmed:

- **The `g` ladder really is 3..9.** All 60 sampled listings published exactly that band, and
  Lightning Piggy's builder loops `for (n = 3; n <= 9; n++)`. The bounded `geoMipMap` overload
  matches.
- `a` on a found log is a plain `37516:<pubkey>:<d>`; the composite `<finder-hex>:<naddr>` on an
  embedded 7517 is exactly as specified. Both parse.
- `archived` really does appear in `t` alongside cache types on live listings, and real `F`
  lock-ins and `n` modifiers exist. The split parsers handle them.
- Listings carry `client`, `expiration`, NIP-32 `L`/`l`, `content-warning` and payout hints we
  model nothing for; ignoring them is harmless.

**The one real divergence: `hint`.** NIP-CC contradicts itself — the tag table says "plaintext"
and the example is plaintext, while the Clients section asks for ROT13 — and the network split
down the middle: of 34 sampled hints, **17 plaintext and 17 ROT13**, consistent within each
author but not across them. Either fixed reading spoils half the caches in the world. So
`HintObfuscation` picks per hint, showing whichever of the two rotations scores worse against
English letter frequency, and the card *toggles* rather than revealing once, so a wrong guess
costs a tap instead of the hint.

Worth raising upstream: the spec should say which form goes on the wire. Until it does, a writer
has to pick, and this code writes ROT13 — the reference client's choice, and the one that fails
safe (a reader assuming plaintext sees noise, not the answer).

Note the trap if anyone revisits the heuristic: counting vowels is backwards. ROT13 maps `n→a`,
`r→e`, `h→u`, `b→o`, so English ciphertext usually has *more* vowels than its plaintext.

### Rendering, compared kind by kind

Read against Lightning Piggy's screens (`CacheDetailSheet`, `HuntRailCard`,
`HuntPiggyDetailScreen`, `HuntRecentFindsSection`). What their UI does that ours did not:

- **The hint shows no text until tapped** — "Stuck? Tap to reveal the hint", never the encoded
  form. Adopted: a wall of ROT13 reads as corruption, and with nothing rendered before the tap
  the hint heuristic can no longer spoil anything, only mislabel after an explicit ask.
- **The cache photo leads the card.** 87 `image` tags across 60 sampled listings; both their rail
  card and detail screen render it. We parsed `image` and rendered nothing. Adopted (first image
  only; the rest belong on a detail screen).
- **A found-log row names the cache it is about.** Ours said "Found it!" and nothing else — the
  one thing a reader already assumed. Adopted, plus the log's own photo (8 of 60 carry one). The
  listing was already being loaded to validate the proof, so the name is free.

Where we deliberately differ, or lead:

- Our feed card carries the D/T/S and modifier badges; their rail card is name + kind + distance
  and puts the chips on a detail sheet. Amethyst's feed cards are richer by house style
  (cf. `RoadEventCard`), so this stays.
- They read `t` as one value, so an archived cache renders "archived" *as its cache type*. Our
  split parser keeps the type and adds an Archived badge.
- They parse no `n` modifiers, no `F`, no `mission` and no `verification`; there is no reference
  rendering for first-to-find, art, Key Quest or verified finds, and nobody validates a 7517.
  Ours is the first — worth saying out loud, because it means those four have been checked
  against the spec and against real events, but not against another client's behaviour.
- Neither client renders the kind 1111 `dnf`/`maintenance` log type. They have a builder and no
  reader, exactly as we do. A shared ecosystem gap rather than a divergence.
- NIP-40: they stamp `expiration` on every listing (a year by default) and drop expired caches.
  Amethyst honours NIP-40 globally in `CachePruner.pruneExpiredEvents`, so no per-kind work —
  though pruning is periodic rather than render-time, which is true of every kind in the app.

### The card's visual hierarchy

The first card was a faithful dump of the tag list: a full-width **square** map (the map slot
never overrode `LocationPreviewMap`'s `aspectRatio = 1f`) immediately followed by a 16:9 photo,
then up to **nine** chips at identical weight, and no distance. A real cache off the relays —
*Treasure Troll's Trunk*, which carries every modifier the spec defines — lit up all nine.

Rebuilt around the three questions a reader actually has:

- **One hero.** Photo when there is one, with the map demoted to a 76dp corner inset that still
  answers "where"; the map alone at 16:9 when there is no photo. `GeocacheMap` gained an
  `aspectRatio` parameter so the card, not the host, decides the shape — the same slot now serves
  both the wide hero and the square inset.
- **Ratings are a line.** `Traditional · Regular · D1 · T1` in one quiet row instead of four
  chips, which is what Lightning Piggy's sheet does.
- **Colour only where it is rare.** Gold for an unclaimed first-to-find, green for verified
  finds, muted for claimed and archived. The gold chip is suppressed once a cache is claimed — a
  prize nobody can win should not glitter. Nine chips became four. Tints are backgrounds, not
  text colours: the theme's amber is unreadable as text on a light ground, `onSurface` over a
  wash reads in both.
- **The name is a title** that wraps to two lines, not a one-line pill clipped over a map.
- **Distance, top right.**

On distance: it reads `geolocationFlow().value` rather than collecting it. That flow is
`SharingStarted.WhileSubscribed`, so collecting from a feed card would switch the GPS on for
anyone who merely scrolled past a geocache — a real battery and privacy cost, paid silently, for
one line of text. Its initial value is the last cached fix, so this costs nothing and yields a
distance whenever something else (Around Me, the location picker) has already asked. The trade
is that it does not update as the reader walks; a card in a feed is not a compass, and the detail
screen is where a live fix belongs. Rounding is deliberately coarse (metres up close, one decimal
to 10km, whole km beyond) because Amethyst holds only `ACCESS_COARSE_LOCATION`.

## 4. Security review items

These are the parts worth a careful reviewer, not the event codecs:

1. **Verification private keys from QR codes are attacker-controlled input.**
   They arrive by scanning an object in the physical world. Treat the scanned
   value as untrusted: validate it is a 32-byte secp256k1 scalar, use it for one
   ephemeral signature, never persist it, never log it, and never route it
   through `NostrSignerInternal` instances that outlive the call.
2. **A `7517` proves presence, nothing else.** Validation must check all four
   spec steps — signature valid, signer == the listing's `verification` pubkey,
   finder pubkey in the `a` tag == the 7516 author, and the naddr resolves to
   *this* cache. Skipping step 2 or 3 lets anyone replay someone else's
   verification into their own log.
3. **Cache locations are precise real-world locations of the user.** Publishing a
   9-character geohash is ~5m. The composer must make it obvious that this is
   public, and the "log a find" flow must not attach the *finder's* location.
4. **Hints/missions are untrusted remote text** — same rendering rules as any
   other user content (no auto-linkifying into a privileged surface).

## 5. Suggested sequencing

1. ~~Quartz protocol package + tests + the three registration files.~~ **Done** — 79 tests in
   `quartz/src/commonTest/…/nipCCGeocaching/`, plus the three new rows in the
   `indexable-content.golden` fixture and the `searchable-kinds.md` table. The layout follows
   nip88Polls: a folder per subject, each with its event, `TagArrayExt`, `TagArrayBuilderExt`
   and `tags/`.
2. ~~Read path in Amethyst.~~ **Done** — `GeocacheCard`/`GeocacheFoundLogCard` in `commonsUI`
   (map-hero slot, D/T/S and modifier badges, ROT13 hint with tap-to-reveal), `Geocache.kt`
   wrappers in `amethyst/` supplying the osmdroid `LocationPreviewMap`, `LocalCache` consume +
   `computeReplyTo`, dispatch in `NoteCompose` and `ThreadFeedView`, the one-line
   `PostsByGeohashKinds` addition, and the search window (`RenderableKinds` + a `kind:geocache`
   alias).

   Two things worth knowing about how it was wired:

   - **The found-log proof badge is computed, never assumed.** A `verification` tag is a string
     until it has been checked against the listing's key, the log's own author and the cache it
     claims, so `RenderGeocacheFoundLog` loads and observes the listing and runs
     `GeocacheVerificationValidator`. Until the listing arrives the state is `UNKNOWN` and the
     card shows no badge — never an optimistic one. A verification that fails the check renders
     as a warning rather than silently as "no proof".
   - **"Claimed" comes off the listing's own `F` tag**, not from the cache's found logs. `F` is
     authoritative once published and travels inside the event; the provisional timestamp-ordered
     winner needs every verified log for the cache, so it belongs on the detail screen that
     subscribes to them rather than on a feed card reading whatever replies happen to be in
     memory.
3. Cache detail screen + NIP-22 logs (mostly wiring existing comment UI). This is where the
   `dnf`/`note`/`maintenance` badge belongs: kind 1111 has no card of its own in `NoteCompose`,
   it falls through to the generic text body, so badging a log type means touching the path every
   NIP-22 comment in the app takes — worth doing once there is a cache thread to do it for. It is
   also where the provisional first-to-find winner and the "multiple DNFs mean the cache is gone"
   status heuristic go.
4. Write path: found logs, then DNF/note comments.
5. Verification: QR scan → ephemeral `7517` → embedded in the found log.
6. First-to-find / `F` lock-in, and the archived rendering rules.
7. Curation lists (37517) — independent of 1–6 and the easiest to defer.

Steps 1–2 are the "make Amethyst not blind to geocaches" milestone and are worth
shipping before anything in 3+ is designed.

## 6. Full integration design — screens, routes and entry points

Steps 3–7 above say *what* is missing. This section says what it looks like. Every Quartz
builder needed for the write path already exists and is tested (`GeocacheListingEvent.build`,
`GeocacheFoundLogEvent.build(message, cache, verification, images)`,
`GeocacheVerificationEvent.build(finderPubKey, cache)`, `GeocacheCurationListEvent.build`,
`GeocacheLogComment`), so **none of this needs protocol work** — it is navigation, composers and
one map. Tag coverage was re-checked against the spec's tables and is complete: 14/14 on 37516,
3/3 on 7516, 1/1 on 7517, 8/8 on 37517.

Wireframes: https://claude.ai/artifact/736F8KCTiaUkXCEpEii54C

### 6.1 Six screens, eight routes

| # | Screen | Route(s) | Template to copy |
|---|--------|----------|------------------|
| 1 | Geocaches hub (5 tabs) | `Route.Geocaches(initialTab: GeocacheTab? = null)` | `DiscoverScreen` (tab row + pager) |
| 2 | Cache detail | `Route.GeocacheDetail(kind, pubKeyHex, dTag)` | `calendars/detail/CalendarEventDetailScreen` |
| 3 | Log a find | `Route.LogGeocacheFind(kind, pubKeyHex, dTag)` | `composableFromBottomArgs`, NewPost chrome |
| 4 | Hide a cache | `Route.NewGeocache(draft)` / `Route.EditGeocache(address)` | `calendars/create/NewCalendarEventScreen` |
| 5 | Hunt detail | `Route.GeocacheHunt(kind, pubKeyHex, dTag)` | `SoftwareAppDetail` shape |
| 6 | Hunt composer | `Route.NewGeocacheHunt(draft)` / `Route.EditGeocacheHunt(address)` | follow-set picker |

The address-carrying routes use the same three-field `(kind, pubKeyHex, dTag)` + `Address`
secondary-constructor shape as `Route.EditCommunity` and `Route.AwardBadge`.

Hub tabs — one shared feed state, five views, so the map costs no extra subscription:
**Nearby** (distance-sorted) · **Map** · **Hunts** (37517) · **Finds** (my 7516s) · **Mine**
(caches I own). Needs a `GeocacheTab` enum next to `DiscoverTab` and a `dal/` filter per tab.

### 6.2 Deliberately sheets, not screens

- **Didn't find it** — one note + photo, publishing a kind 1111 with the `dnf` type. The same
  sheet serves "add a note" and "needs maintenance"; only `GeocacheLogTypeTag` changes. This is
  why no `logType` param gets bolted onto `Route.GenericCommentPost`.
- **Pin peek** on the map — raising a sheet instead of navigating keeps panning fluid.
- **Verification QR** (owner) — `QrCodeDrawer`, with the "only copy" warning.

### 6.3 Entry points

`NavBarItem.GEOCACHES` → `Route.Geocaches()` is the only new navigation surface, and it ships
**visible by default**. Adding the id to `NavBarCatalog` forces a drawer placement — it goes in
`DrawerFeedsItems` beside Polls, Products, Workouts and Calendars, and `DrawerSectionsTest` fails
if a catalog id appears in no section, so the repo already prevents a new destination from
silently vanishing. Hiding it is opt-out, via `DrawerSettingsScreen`.

The bottom bar is a separate list (`DefaultBottomBarItems` is five entries: Home, Messages,
Wallet, Browser, Notifications) and is left alone — geocaching earns a drawer row, not one of
those five slots.

Everything else is a destination change on something already tappable: a `GeocacheCard` in any feed, a "N caches
here" chip on the geohash screen, an `naddr` through `uriToRoute` (the treasures.to link path —
the interop entry that matters most), the notification feed (7516s already file under the cache
via `computeReplyTo`), the `kind:geocache` search alias, and a find count on a profile.

**The verification secret must never reach the global QR scanner.** `NIP19QrCodeScanner` maps
scans to routes; a cache's *published* `naddr` belongs there, its verification *private key* does
not. That scan happens only inside screen 3, via `SimpleQrCodeScanner`, which returns a raw
string.

### 6.4 What's genuinely new vs. reused

Reused wholesale: `GeocacheCard`/`GeocacheFoundLogCard`, `LocationPreviewMap`,
`LocationPickerMap`, `GeohashLocationPickerDialog`, `SimpleQrCodeScanner`, `QrCodeDrawer`,
`FeedTopNavFilterState`, `SaveableFeedContentState`, the NIP-22 thread UI, the upload pipeline,
`GeocacheVerificationValidator`.

Genuinely new Android UI: **a multi-marker map overlay with clustering**. Every map composable in
the repo places exactly one `Marker` (`LocationPreviewMap.kt:219`, `LocationPickerMap.kt:240`
both `removeAll { it is Marker }` first). That is the one piece with no precedent here.

### 6.5 Rules the screens must honour

- **Archived, or FTF locked in for someone else** → the find actions are replaced by a status
  strip. NIP-CC asks for this explicitly.
- **`theme` / `map` on a 37517 are defaults, not mandates** — apply, then let the reader override.
- **`a` tag order on a hunt is meaningful** and `curatedGeocaches()` already preserves it.
- **Found logs publish to the cache's own `r` relays** (`logRelays()`) plus the user's write
  relays.
- **A scanned verification key is attacker-controlled**: validate it is a 32-byte secp256k1
  scalar, sign one 7517 on `Dispatchers.Default`, drop it. Never persisted, never logged, never
  handed to a signer that outlives the call.
- **The find composer must not attach the finder's location**, and must say so on screen.

### 6.6 Build order — **built**

All six screens, eight routes and the sheets below now exist and compile. What landed:

| Piece | Where |
|---|---|
| 8 routes + `GeocacheTab` | `ui/navigation/routes/Routes.kt` |
| Drawer + catalog + bottom-bar category | `NavBarItem.kt`, `DrawerSections.kt` |
| Relay filter family (8 files) | `commons/.../relayClient/geocaches/` |
| Follow list, prefs, data source, 4 feed states | `AccountSettings`, `Account`, `LocalPreferences`, `RelaySubscriptionsCoordinator`, `AccountFeedContentStates` |
| 4 feed filters | `ui/.../geocaches/dal/` |
| Hub, 5 tabs, top bar, FAB, feed renderer | `ui/.../geocaches/` |
| Cache detail, log thread, owner actions, DNF sheet | `ui/.../geocaches/detail/` |
| Find composer + verification | `ui/.../geocaches/log/` |
| Cache composer | `ui/.../geocaches/create/` |
| Map tab with clustering | `ui/.../geocaches/map/` |
| Hunt detail + composer | `ui/.../geocaches/hunt/` |
| Owner revisions (archive, `F` lock-in) + 6 tests | `quartz/.../listing/GeocacheListingRevision.kt` |

Two things behave differently from the sketch, both deliberately:

- **Photos on a found log go through the standard upload pipeline**, not a geocaching-specific
  one, so a find inherits Blossom/NIP-96 server choice and compression from the user's settings.
- **The Key Quest answer is appended to the log body** rather than given a tag. NIP-CC defines
  `mission` on the listing and nothing for the response, so the only interoperable place for it
  is the text every client already renders.

### 6.6a What the first pass got wrong

Two corrections worth keeping, because both were "done" in an earlier draft of this section and
neither was:

- **Five of the eight entry points did not exist.** `Route.GeocacheDetail` appeared in exactly
  two places in the repo: its nav registration and the hub's own feed row. Everything else —
  feed taps, notification taps, `nostr:naddr` deep links, QR scans, search hits — fell through
  to the bare note view. All five run through one function, `routeForInner` in `RouteMaker.kt`,
  where the calendar branch three lines above says so in as many words. Two branches fixed the
  lot. The `naddr` case is the one that mattered: a treasures.to link is how most people will
  first meet a cache in Amethyst.
- **Curation was write-once.** "Add to a hunt" only ever created a *new* hunt, so the moment a
  player wanted a second cache on a route they had already built, the app had no answer. Now a
  picker sheet lists the hunts the user owns and republishes the chosen one with the cache
  appended (`GeocacheCurationRevision`, built on `update {}` so foreign tags survive).

Also added since: the "N caches here" chip on the geohash screen, and photo removal in the cache
composer, which could previously only add.

**Deliberately not built:** a find count on someone else's profile. It would read from
`LocalCache`, which for a stranger holds ~0 of their 7516s, so it would render "0 finds" for
almost everybody. Doing it honestly needs a per-profile subscription — a profile tab like Gallery
or Zaps — which is more work than the three items above combined, for a vanity number.

Still open, and honestly so: **none of it has been seen rendering**, and the cluster threshold
(50) is a guess that wants measuring on a mid-range device with a busy city on screen.

### 6.6b Original build order

1. Cache detail + log thread — gives every entry point somewhere to land.
2. Log a find, with verification — the first thing a player does, and it makes Amethyst the only
   client that can *produce* a 7517 rather than only check one.
3. The hub minus the map — four feed filters and the drawer entry.
4. Hide a cache — the form plus the key ceremony; the largest single piece.
5. The map tab.
6. FTF lock-in and the archived rules.
7. Hunts — fully independent of 1–6.

### 6.7 Decisions (settled)

- **Nearby may turn on GPS**, behind an explicit "Use my location" the user taps. Feed cards keep
  reading the cached fix and never subscribe; a distance-sorted list is worthless without a fix,
  but scrolling a feed must still never start the GPS.
- **Geocaching gets a drawer row, on by default** — `DrawerFeedsItems`, hideable in
  `DrawerSettingsScreen`. Not a default bottom-bar slot.
- **We write ROT13 hints.** The network is split 17/17 and the spec contradicts itself; reading
  handles both, writing must pick. ROT13 is the reference client's choice and the one that fails
  safe (a reader assuming plaintext sees noise, not the answer).
- **Cluster the map above roughly 50 visible markers** — a starting figure to measure on a real
  device, not a guess to ship.
