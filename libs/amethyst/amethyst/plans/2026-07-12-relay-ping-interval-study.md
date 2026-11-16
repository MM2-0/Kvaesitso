# WebSocket Ping Interval Study — 122 Production Relays

**Date:** 2026-07-12
**Question:** Would relays drop Amethyst's connections if the client WebSocket
ping interval were raised (e.g. 120s → 240s on mobile data to save battery)?
Was the long-standing 120s value ever load-bearing, and what is the best
middle ground?

**Answer (TL;DR):** Keep a single **120s** ping interval on every network.
Raising it to 240s saves almost no battery — 90% of surveyed relays send
their *own* pings every 30–70s, which OkHttp must answer, so the radio's
wake cadence is set by the relays, not by our interval — and it starts
dropping real relay tiers: 240s pings lose `relay.ditto.pub` (~240s idle
timeout) and every `nostr1.com`-hosted relay (~300s tier); 300s pings even
lose `relay.snort.social` (~600s tier). Lowering below 120s would only
rescue a ~120s tier of 6/122 relays that already cycle today, at 2× the
ping traffic on every other connection. 120s is, by measurement, the sweet
spot it was presumably never designed to be.

---

## 1. Motivation

`OkHttpClientFactoryForRelays` sets `pingInterval(120s)` on every relay
WebSocket. During battery work the interval was tentatively doubled on
mobile data on the theory that each client ping on an otherwise-idle
cellular connection wakes the radio and pays the multi-second tail-energy
cost. The maintainer asked the right question: *do we actually know how
production relays react to different ping intervals?* Nobody had tested
the 120s value. This study answers it empirically.

Two distinct drop mechanisms are in play:

1. **Relay/reverse-proxy idle timeouts** — testable from any vantage.
2. **Carrier NAT idle timeouts** — only testable from a real cellular
   network (not from this environment; see §7).

## 2. Relay population

Production relays were harvested by fetching **600 kind:10002 (NIP-65)
relay-list events** from indexer relays (`indexer.coracle.social`,
`user.kindpag.es`) and counting `r`-tag references: **1,468 distinct
relays**, ranked by how many users actually list them. The **top 140**
(plus all Amethyst default relays) formed the test population.

- **122 relays accepted a WebSocket** from the test vantage.
- 18 were unreachable *from a datacenter IP* (Cloudflare 403 challenges:
  `nostr.wine`, `relay.0xchat.com`; TCP resets: `relay.nostr.band`,
  `nostr.bitcoiner.social`, `relayable.org`, `nostr.fmt.wiz.biz`; plus
  ordinary 5xx/410s). These blocks are IP-reputation-based, not
  ping-related, and don't affect the conclusions — but they mean the
  study cannot speak for those relays.

## 3. Method

Three experiments, all through the same stack (Python `websocket-client`,
TLS, one REQ per connection whose filter matches nothing, so the relay
answers EOSE and the connection then carries zero application traffic).
Server pings were always answered with pongs automatically (as OkHttp
does) and logged.

- **Phase A — idle survival.** 140 relays, **zero client pings**, hold
  for **780s (13 min)**. Records: drop time, close code, server-ping
  timestamps. A relay surviving 780s of total client-ping silence proves
  *any* client interval ≤ 780s is safe for it.
- **Phase B — ping efficacy.** Every Phase A dropper re-tested with
  client pings at **55 / 110 / 120 / 180 / 240 / 300s** (one connection
  per interval, window = observed idle timeout + 2 ping cycles + margin,
  capped at 780s). This distinguishes "pings reset the relay's idle
  timer" from "only data frames count".
- **Case study —** `relay.ditto.pub` with 60s pings for 420s (it had
  dropped an idle connection at 257s while *its own* ping got our pong at
  123s — proving pongs don't reset its timer but client pings do).

## 4. Phase A results — idle survival with zero client pings

**99 of 122 relays (81%) survived 13 minutes of complete client-ping
silence.** For four out of five relays, the client ping interval is
irrelevant to connection survival at any plausible value.

The 23 droppers cluster into clean idle-timeout tiers:

| Tier | Count | Relays |
|---|---|---|
| < 30s (probe rejected / non-idle close) | 2 | `nostr.petrkr.net/strfry`, `next.nsite.run` |
| **~60s** | 8 | `nostr.pareto.space` (47s), `nostr.vps.satsnode.xyz` (×2), `relay.mostro.network`, `nostr.bond/alpha`, `nostr.sgiath.dev`, `nostr.bitcoinplebs.de`, `nostr.schneimi.de` |
| **~120s** | 8 | `cfrelay.snowcait.workers.dev` (118s), `nostr-verified.wellorder.net` (120.7s), `nostr-pub.wellorder.net` (120.8s), `git.shakespeare.diy` (125.7s), `nostr-relay.irgenius.org` (126.0s), `nostr-verif.slothy.win` (126.1s), `nostr.bit4use.com` (126.6s), `sendit.nosflare.com` (131.4s) |
| **~240s** | 1 | `relay.ditto.pub` (240.9s; 257.1s in an earlier run) |
| **~300s** | 2 | `david.nostr1.com` (300.5s), `dkkc.nostr1.com` (300.7s) — i.e. the **nostr1.com / relay.tools hosting tier** |
| **~600s** | 2 | `nos.lol/<haven path>` (600.8s), `relay.snort.social` (601.0s) |

### Server-ping cadence (the finding that reframes the question)

Among the 99 relays that held an idle connection for the full window:

| Server→client ping cadence | Relays |
|---|---|
| ≤ 35s | 45 |
| 36–70s | 37 |
| ~300s | 8 |
| no server pings at all | 9 |

**90 of 99 relays ping the client; 82 of them every ≤ 70s.** OkHttp
answers every server ping with a pong regardless of the client-side
`pingInterval`. So on a connected cellular device the radio is being
woken every 30–70s *per connection* by the relays themselves. Changing
the client interval from 120s to 240s does not change that cadence at
all — the client ping is a rounding error in the connection's keepalive
traffic. **The claimed battery saving of a longer client ping interval
does not exist in practice.** (Corollary: the real mobile-battery lever
is connected time and connection count in the background — which the
app already minimizes by disconnecting 30s after backgrounding — not
the ping schedule.)

## 5. Phase B results — which client intervals keep the droppers alive

For every idle-dropper, one connection per candidate interval
(`x@T` = dropped at T seconds despite pinging at that interval;
`skip` = interval ≥ observed idle timeout, unsafe by construction):

| relay | idle-drop | 55s | 110s | 120s | 180s | 240s | 300s | max safe |
|---|---|---|---|---|---|---|---|---|
| nostr.pareto.space | 46.9s | skip | skip | skip | skip | skip | skip | none |
| nostr.vps.satsnode.xyz | 51.1s | skip | skip | skip | skip | skip | skip | none |
| nostr.vps.satsnode.xyz/… | 51.2s | skip | skip | skip | skip | skip | skip | none |
| relay.mostro.network | 60.5s | x@60.8 | skip | skip | skip | skip | skip | none |
| nostr.bond/alpha | 60.8s | x@60.9 | skip | skip | skip | skip | skip | none |
| nostr.sgiath.dev | 60.8s | x@60.9 | skip | skip | skip | skip | skip | none |
| nostr.bitcoinplebs.de | 60.9s | x@61.1 | skip | skip | skip | skip | skip | none |
| nostr.schneimi.de | 62.2s | x@61.1 | skip | skip | skip | skip | skip | none |
| cfrelay.snowcait.workers.dev | 118.2s | x@85.0 | x@74.5 | skip | skip | skip | skip | none |
| nostr-verified.wellorder.net | 120.7s | **OK** | x@120.7 | x@120.8 | skip | skip | skip | 55s |
| nostr-pub.wellorder.net | 120.8s | **OK** | x@120.8 | x@120.8 | skip | skip | skip | 55s |
| git.shakespeare.diy/… | 125.7s | **OK** | x@125.9 | x@126.1 | skip | skip | skip | 55s |
| nostr-relay.irgenius.org | 126.0s | **OK** | x@125.8 | x@125.9 | skip | skip | skip | 55s |
| nostr-verif.slothy.win | 126.1s | **OK** | x@126.1 | x@126.3 | skip | skip | skip | 55s |
| nostr.bit4use.com | 126.6s | **OK** | x@126.4 | x@126.5 | skip | skip | skip | 55s |
| sendit.nosflare.com | 131.4s | x@81.7 | x@41.9 | x@7.0 | skip | skip | skip | none |
| **relay.ditto.pub** | 240.9s | OK | OK | **OK** | x@673.5 | **x@609.8** | skip | **120s** |
| **david.nostr1.com** | 300.5s | OK | OK | **OK** | x@300.6 | **x@300.7** | x@300.7 | **120s** |
| **dkkc.nostr1.com/…** | 300.7s | OK | OK | **OK** | x@300.7 | **x@301.1** | x@300.6 | **120s** |
| nos.lol/<haven path> | 600.8s | OK | OK | OK | OK | OK | x@602.1 | 240s |
| **relay.snort.social** | 601.0s | OK | OK | OK | OK | OK | **x@607.7** | 240s |

Key observations:

1. **A client ping interval numerically below the idle timeout is NOT
   sufficient.** 180s and 240s pings failed against the ~300s
   `nostr1.com` tier, and 300s pings failed against the ~600s
   `snort.social` tier, even though each ping "should" have arrived in
   time. The empirical rule across every tier: **pings only reliably
   reset a relay's idle timer when the interval is at most roughly half
   the timeout.** (Likely cause: these stacks check activity in coarse
   windows rather than resetting a precise per-frame deadline, so an
   interval near the window size loses boundary races.)
2. The **~60s tier is unsalvageable** — even 55s pings didn't help
   (their timers count only data frames). These 8 relays drop idle
   Amethyst connections *today* under the 120s setting and would under
   any setting; the existing reconnect-on-demand path is the correct
   handling for them.
3. The **~120s tier is only rescued by ≤55s pings** — meaning
   **today's 120s interval never kept them alive either** (110s and
   120s pings both failed). They cycle today; they'd cycle at 240s.
   No candidate change affects them.
4. The tiers that DO depend on our ping interval are exactly
   **ditto (~240s), nostr1.com (~300s), and snort/nos.lol-haven
   (~600s)** — and 120s holds all of them, while 240s loses the first
   two and 300s loses all three.

Connections kept alive (of 122 reachable), by candidate interval:
**55s → 110 · 120s → 104 · 240s → 101 · 300s → 99.**

The `relay.ditto.pub` case study confirms the mechanism: with an idle
connection its *own* ping at t=123s received our pong and it still
closed at 257s (pongs don't count as activity), but with 60s client
pings it stayed up indefinitely (client pings do count).

## 6. Why not go lower than 120s?

55s pings would rescue the ~120s tier (6 relays). But:

- those relays already cycle today, so the status quo loses nothing;
- 55s pings double the client-ping traffic on all ~100+ connections to
  rescue 5% of relays whose operators chose aggressive timeouts;
- the radio is already woken every ≤70s on 82/122 connections by server
  pings, so the *incremental* battery cost is modest — but so is the
  benefit, and drop/reconnect for those 6 relays is already handled
  gracefully by `BasicRelayClient`'s backoff + the keep-alive sweep.

A per-relay adaptive interval (shorten pings only for relays observed to
drop idle connections) is possible future work, but OkHttp's
`pingInterval` is per-client, not per-socket, so it would require
per-relay client instances — not worth the complexity for 6 relays.

## 7. Carrier NAT — the part this study cannot measure

The other purpose of client pings is keeping carrier NAT/firewall
mappings alive on cellular. That is untestable from a datacenter vantage.
Published measurements and platform folklore put aggressive carrier TCP
idle timeouts around 4–5 minutes (most are 15–30 min; FCM survives on
~28 min heartbeats *with OS cooperation Amethyst doesn't get*). 120s
sits comfortably inside even the aggressive bound, so relay-side and
NAT-side constraints agree on the same answer. Anyone wanting to raise
the interval later must first re-run Phase B *and* validate on real
cellular networks — the relay data alone already rules out 240s.

## 8. Decision

- **`WEBSOCKET_PING_INTERVAL_SECS = 120`, one value for wifi and mobile.**
  The tentative 240s mobile value was reverted in this same branch after
  these measurements: it saved ~nothing (server pings dominate radio
  wakes) and dropped the ditto and nostr1.com tiers.
- OkHttp's `pingInterval` doubles as the dead-connection detector (a
  missed pong fails the socket within one interval), so 120s also keeps
  failure detection twice as fast as 240s would — relevant after silent
  network path changes.

## 9. Reproduction

Vantage caveats: datacenter egress IP (18 relays refused it), all
traffic via an HTTP CONNECT proxy. A control connection with 100s pings
survived every window, ruling out proxy-imposed idle limits ≤ 780s.

Sketch (Python `websocket-client`): open `wss://` to each relay, send
one REQ whose filter matches nothing (`{"kinds":[1],"authors":["00…01"],
"limit":1}`), auto-pong server pings, and either never ping (Phase A,
780s window) or ping at the candidate interval (Phase B). Log connect /
EOSE / server-ping / close timestamps. Population: top-N relays by
`r`-tag frequency across kind:10002 events fetched from indexer relays.

## Appendix — Phase A survivor cadences (99 relays)

Server-ping cadence measured over the 13-minute window. `none` means the
relay sent no pings at all and still held the idle connection.

| cadence | relays |
|---|---|
| ~25–35s | `articles.layer3.news`, `aegis.relayted.de`, `assistantrelay.rodbishop.nz`, `bots.utxo.one`, `custom.fiatjaf.com`, `dev.calendar-relay.edufeed.org`, `greensoul.space` (×2), `groups.0xchat.com`, `groups.satsdisco.com`, `h.codingarena.top/inbox`, `haven.calva.dev/inbox`, `haven.nostrfreedom.net`, `haven.relayted.de`, `hist.nostr.land`, `lang.relays.land` (×3), `nexus.libernet.app`, `nip17.com`, `nostr-01.uid.ovh`, `nostr-relay.derekross.me` (×2), `nostr.damupi.com/inbox`, `nostr.easydns.ca`, `nostr.kfx.fr` (×2), `nostr.land`, `nostr.nothing.is-lost.org/haven`, and 17 more at ~30s; `nostrelites.org`, `purplepag.es`, `relay.noswhere.com` at ~30s |
| ~55–70s | `nostr.thalheim.io`, `nostr.xmr.rocks`, `offchain.pub`, `relay.mostr.pub`, `relay.nostr.net`, `relay.primal.net`, `relay.damus.io`, `indexer.coracle.social`, `directory.yabu.me`, `user.kindpag.es`, `profiles.nostr1.com`, `nostr.oxtr.dev`, and ~25 more |
| ~300s | `nostr-relay.corb.net`, `nostr.001.j5s9.dev`, `nostr.8777.ch`, `nostr.einundzwanzig.space`, `nostr.mikoshi.de`, `nostr.pbfs.io`, `nostr.sectiontwo.org`, `nostr.wild-vibes.ts.net` |
| none | `nos.lol`, `nostr.mom`, `relay.divine.video`, `relay.fountain.fm`, `koru.bitcointxoko.org`, `nostr-pr02.redscrypt.org`, 2 × Cloudflare-Workers relays, 1 other |

Raw JSON for both phases (per-relay timestamps, close codes, server-ping
series) was captured during the study session; the tables above are the
complete decision-relevant summary.
