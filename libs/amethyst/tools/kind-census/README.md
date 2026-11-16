# Kind census: which event kinds are live on relays that Quartz can't parse

A census of the event kinds people actually publish, crawled with `amy`. It checks every kind
seen against `EventFactory.isKnownKind`, i.e. whether Quartz has a typed event class for it. It
keeps real signed samples of every kind Quartz does not type, so they can be reverse-engineered.

## The 2026-10-08 run (`data/2026-10-08/`)

| | |
|---|---|
| Window | events with `created_at` ≥ 2026-10-07 00:00 UTC, crawled until ~03:20 UTC on 2026-10-08 (~27h) |
| Relay universe | 8,134 relay URLs from 12.6k NIP-66 kind:30166 monitor events and 94k kind:10002 lists; `amy relay probe` found **5,846 reachable** (`live_relays.txt`) |
| Relays crawled | the **743 most-referenced** live relays (by kind:10002 mentions), then the crawl was stopped by request. 355 of them served events; 111 refused filters without authors/tags; ~30 required NIP-42 AUTH |
| Events | **2,358,562** distinct, signature-verified events |
| Kinds | **573** distinct kinds; **386 have no typed Quartz class** (412,747 events) |
| Of those | 147 kinds have ≥3 distinct authors (researched one by one); 239 have 1–2 authors (clustered) |

Files:

- `kind_histogram.tsv` — every kind seen: events, distinct authors, typed-in-quartz, quartz label.
- `unsupported_kinds.tsv` / `.json` — per unsupported kind: class, counts, tag-name presence,
  content shape (json / nip44-looking / nip04 / opaque / text / empty), JSON keys, `alt`/`client`.
- `samples/kind-<K>.jsonl` — up to 10 raw signed events per unsupported kind, one NIP-01 JSON per
  line, round-robin across authors (newest first), ≤120 KB per kind. Feed them straight to
  `EventFactory.create` / `Event.fromJson` in a test. **Not committed:** these are other
  people's events, and some apps put personal data in plaintext (emails, visitor IPs,
  WebRTC addresses; see Privacy leaks). `profile.py` regenerates them from `census.db`.
  Before copying one into a test fixture, check it for anything personal.
- `findings.json` → `FINDINGS.md` — what each kind is: app/protocol, spec or source link,
  structure, encrypted or not, confidence, and relevance to Amethyst.

### Caveats

- **Author counts overstate users** for apps that sign with throwaway keys: 4333 uses one key per
  event, 1060 one key per ratchet, and 37195 mostly comes from Iris web sessions.
- The **"nip44" shape label is a heuristic** (base64 starting with `A`). 4333, 30701 and 1013 are
  custom formats with a `0x01` version byte, and 443 is a bare base64 MLS KeyPackage.
- Ephemeral kinds (20000–29999) are mostly absent: relays don't store them.
- Big relays are under-sampled. relay.damus.io throttles fast sequential REQs on one connection
  (see the amy issues below), so its paged walk ended after a few hundred events.
- The kind is the only signal. Several numbers are shared by unrelated apps (see Collisions).
- Identifications marked `low` come from the payload alone, with no public source found.

## What to do with it

Ordered by how directly each gap affects Amethyst.

1. **Marmot legacy KeyPackages (443).** *Fixed after this census:* White Noise's MDK 0.8.x still
   publishes kind:443, and 7 of the 20 authors seen published *only* 443, but Amethyst asked relays
   for 30443 alone, so those users could not be invited. Quartz now types 443
   (`LegacyKeyPackageEvent`) and the invite fetch (`KeyPackageFetcher.fetchKeyPackageForInvite`,
   used by the app and `amy marmot group add`) reads both kinds, preferring a valid 30443. MDK 0.8
   packages can join legacy-profile groups but not current-profile ones until those users upgrade;
   MDK 0.7.1 packages lack `mls_proposals` and stay invalid. Keychat's **10443** is a different
   case: it is a replaceable KeyPackage that requires the old NIP-EE extension `0xF233`, not
   `0xF2EE`, so a reader alone would not make it interoperate.
2. **NIP-34 git additions.**
   - **1624 cover note**: overrides a PR or issue description. `cli/ROADMAP.md` already lists it
     as missing.
   - **NIP-C1 CI family**: 9841 job result, 9842 workflow result, 39842 progress, 19843
     coordinator advert, 19844 runner watch list, 39844 repository status. Used by ngit /
     gitworkshop; would give CI badges on patches and PRs.
3. **Mostro split 38383 into four kinds.** 38384 user rating, 38385 instance info (fees, limits,
   currencies; render-worthy), 38386 dispute (1 event seen), plus 8383 dev-fee receipts. Also, Paygress
   publishes compute offers on **38383** and heartbeats on 38384, so `P2POrderEvent` must tolerate
   non-Mostro shapes. RoboSats coordinator ratings are **31986**.
4. **Small siblings of kinds Quartz already has:**
   - 5128 nsite manifest snapshot (next to 15128/35128; 3 authors)
   - 4454 NIP-4E key-transfer request (next to 10044; 10 authors). Its reply, 4455, was not seen
   - 33402 POWR workout template (next to 33401/1301 in `experimental/fitness`; 1 author)
   - 30031 sticker pack (next to the 30030 emoji pack; 1 author)
   - 30024 NIP-23 drafts (1 author)
5. **1080 Private Note Storage** (NIP PR #1893, already unwrapped by nostrdb). It is live and
   could hold private drafts or settings across devices.
6. **Render-worthy app content:**
   - Ditto: 16767 active profile theme, 36767 theme definition, 13473 posting streak (58 authors)
     and 18678 Top 8 (1 author) — *typed after this census, no cards yet*; 31124 Blobbi pets
   - 10222 Communikeys community definition
   - 30301 Kanban board (NIP PR #1665; collides with WalletScrutiny verifications)
   - 5555 Word5 score
   - 9401 WalletScrutiny reproducible-build registrations
7. **High-volume traffic that only needs a named kind, not rendering:**
   - 7368: 122k events/day, nostr-vpn/FIPS peer link-quality "fact op" ratings; addressable
     snapshots on 37368.
   - 1060: 93k events, Iris Chat double-ratchet DMs (NIP PR #1813). Real interop would mean
     implementing the ratchet.
   - 37195: FIPS mesh overlay adverts.

### Collisions seen in the wild

| Kind | Expected use | Also used by |
|---|---|---|
| 30078 | NIP-78 app data | Bro's orders |
| 31990 | NIP-89 handler info | Agent Reach "service cards" |
| 38383 / 38384 | Mostro | Paygress |
| 30301 | — | kanban boards, WalletScrutiny, an encrypted planner |
| 30388 | Corny Chat slide set (formerly) | anonymate account state |
| 15750 | NIP-79 story view receipt (Stories PR #2386 proposes it) | live WabiSabi coinjoin coordinator announcements |
| 30421 | Napstr | BetPod |
| 38421 | Routstr | lnproxy |
| 1000, 4242, 30079, 30100 | — | several unrelated apps each |

Any typed parser for these kinds has to check the shape before trusting it.

### Privacy leaks seen

- 30800 puts email addresses in a public tag.
- 1000 carries WebRTC ICE candidates (IPv6) in plaintext.
- 10600 carries visitor IPs and user agents in plaintext analytics.
- 1050 login pings expose when each user is active.

## amy issues found while crawling

1. **`fetch --paginate --limit 0` buffers the whole result.** It holds every event plus a
   Jackson tree before printing, so a relay with ~28k events (230 MB of JSON) runs a 1 GB heap
   out of memory. That killed 64 of 743 crawl runs. The events were already verified and stored
   by then, so no data was lost, but the exit code was a crash.
2. **`fetch --paginate --limit N` sends N as each REQ's `limit`.** purplepag.es closes a
   `limit` of 50000 with `blocked: limit too high … (max 500)`, so the walk never starts.
3. **A paged walk treats an empty EOSE as "drained".** relay.damus.io answers fast sequential
   REQs on one connection with 3-event, then empty, pages, even though a fresh connection
   returns ~490 events for the same `until`. The walk then ends `DRAINED` after ~700 of ~250k
   events. Using a fresh subscription id per page did not change this (tested), so it is
   throttling, not the id reuse. One mitigation: back off and re-ask once after an empty page
   that follows a short page.
4. **`relay probe` needs `AMY_PASSPHRASE` when there is no TTY**, because it signs the
   reachability cache with the operator key.

## Re-running

```bash
./gradlew :cli:installDist
AMY=cli/build/install/amy/bin/amy
export HOME=/some/scratch/home              # keep the crawl out of your real ~/.amy
SINCE=$(date -u -d 'today 00:00' +%s)

# 1. Kinds Quartz has typed classes for (one TSV line per kind).
java -cp "$(ls cli/build/install/amy/lib/*.jar | tr '\n' ':')" \
  tools/kind-census/KnownKinds.java known_kinds.tsv

# 2. Relay universe: NIP-66 monitor reports + NIP-65 lists land in amy's store.
$AMY --json fetch --kind 30166 --since $((SINCE-172800)) --limit 0 --paginate \
  --relay wss://monitorlizard.nostr1.com,wss://relaypag.es --timeout 15 > /dev/null
$AMY --json fetch --kind 10002 --since $((SINCE-2592000)) --limit 0 --paginate \
  --relay wss://nos.lol,wss://relay.primal.net,wss://indexer.coracle.social,wss://user.kindpag.es \
  --timeout 15 > /dev/null
#    Extract the d tags of the 30166s and the r tags of the 10002s from
#    $HOME/.amy/shared/events.db (table event_headers) into relay_universe.txt.

# 3. Probe them; the live ones are the operator-signed 30166s carrying an rtt-open tag.
AMY_PASSPHRASE=scratch $AMY --json relay probe --file relay_universe.txt --concurrency 400

# 4. Crawl (resumable), then profile and render.
python3 tools/kind-census/crawl.py live_relays.txt work $SINCE known_kinds.tsv 24 300
python3 tools/kind-census/profile.py work/census.db known_kinds.tsv out 10 120000
python3 tools/kind-census/findings_md.py out   # after writing out/findings.json
```

`crawl.py` runs one `amy fetch --paginate --limit 0` per relay, each in its own amy HOME. After
every relay it folds that store into `census.db` (`harvest.py`) and deletes it. Keeping the
per-relay stores fills the disk within a few hundred relays, because each one repeats the same
popular events plus their FTS index. The crawl is CPU-bound on signature verification: about 25
relays a minute on 4 cores, so all ~5.8k live relays take roughly 4 hours.
