# geode

A standalone [Nostr](https://github.com/nostr-protocol/nips) relay for the JVM,
built on Quartz's relay-server code (Ktor CIO). It speaks the core relay
protocol plus NIP-11 (info doc), NIP-42 (AUTH), NIP-45 (COUNT), NIP-50
(full-text search), NIP-77 (Negentropy sync), NIP-86 (relay management) and
NIP-FE (REQ/COUNT/EVENT over plain HTTP), stores events in SQLite (or a filesystem backend), and can mirror upstream
relays strfry-router style.

geode depends only on quartz — no Android, no Compose. `amy serve` (the
Amethyst CLI) embeds the same engine in-process.

## Install

Pick the channel that matches how you deploy. For a real relay, the **Docker
image** or the **`.deb`/`.rpm` + systemd** are the two production paths;
Homebrew and the portable tarball are handy for local testing.

### Docker (recommended for servers)

Images are published to GHCR on every release:

```bash
# Ephemeral — in-memory store, events vanish on restart:
docker run --rm -p 7447:7447 ghcr.io/vitorpamplona/geode:latest

# Persistent + configured:
docker run -d --name geode -p 7447:7447 \
  -v $PWD/geode.toml:/etc/geode/geode.toml:ro \
  -v geode-data:/var/lib/geode \
  ghcr.io/vitorpamplona/geode:latest --config /etc/geode/geode.toml
```

with `in_memory = false` and `file = "/var/lib/geode/events.db"` in your
`geode.toml`. Pin a version (`:1.20.0`) instead of `:latest` for reproducible
deploys. To build the image yourself, from the repo root:

```bash
docker build -f geode/Dockerfile -t geode:local .
```

### Debian/Ubuntu (`.deb`) and Fedora/RHEL (`.rpm`)

Download `geode-<version>-linux-x64.deb` (or `.rpm`) from the
[release page](https://github.com/vitorpamplona/amethyst/releases) and install
it — a minimal JRE is bundled, so no system Java is required. It installs to
`/opt/geode/` with the launcher at `/opt/geode/bin/geode`.

```bash
sudo dpkg -i geode-1.20.0-linux-x64.deb    # or: sudo rpm -i geode-1.20.0-linux-x64.rpm
```

To run it as a managed service, wire up the shipped systemd unit
(`/opt/geode/share/geode/geode.service`) — the unit file's header comment has
the copy-paste steps (create the `geode` user, drop your config in
`/etc/geode/geode.toml`, `systemctl enable --now geode`).

### Homebrew

```bash
brew install vitorpamplona/tap/geode-relay    # from the tap, once published
```

The formula depends on `openjdk` and installs the no-JRE jar bundle. Reference
formula: [`packaging/homebrew/geode-relay.rb`](packaging/homebrew/geode-relay.rb).

### Portable tarball (any Linux/macOS, no system Java)

Download `geode-<version>-<os>-<arch>.tar.gz`, unpack, and run:

```bash
tar xzf geode-1.20.0-linux-x64.tar.gz
./geode/bin/geode --config geode/share/geode/config.example.toml
```

The tarball bundles a jlink'd JRE plus `share/geode/config.example.toml` and
`share/geode/geode.service`.

### From source

```bash
./gradlew :geode:run --args="--config /etc/geode.toml"
```

## Configure

geode runs with zero config (binds `0.0.0.0:7447`, in-memory SQLite). For
anything real, copy [`config.example.toml`](config.example.toml) and edit it —
the sections mirror nostr-rs-relay's `config.toml` so existing configs port
almost verbatim. Precedence is **CLI flags > TOML > built-in defaults**.

```bash
geode --config /etc/geode/geode.toml
geode --help          # full flag list
geode --version
```

Key sections: `[info]` (NIP-11 doc), `[network]` (bind + thread pools),
`[database]` (SQLite path/tuning), `[options]` (AUTH / verify / search),
`[authorization]` (allow/deny lists), `[[mirror]]` (upstream mirroring),
`[http]` (NIP-FE limits), `[rate_limits]` (per-client EVENT and REQ rates),
`[admin]` (NIP-86 management), `[identity]` (the
relay's own key, NIP-11 `self`) and `[membership]` (NIP-43). See the example
file for every knob.

### Commands over HTTP (NIP-FE)

Besides the websocket, geode answers one command per HTTP `POST` to the relay
URL: the body is the frame you would send on the socket, and the answer is the
socket's frames as NDJSON, streamed — no socket, no subscription left open:

```bash
curl -N --compressed -d '["REQ","q",{"kinds":[1],"limit":2}]' http://localhost:7447/
# ["EVENT","q",{"id":"…","kind":1,…}]
# ["EVENT","q",{"id":"…","kind":1,…}]
# ["EOSE","q"]
curl -d '["COUNT","c",{"kinds":[1]}]' http://localhost:7447/   # ["COUNT","c",{"count":2}]
curl -d "[\"EVENT\",$(cat signed-event.json)]" http://localhost:7447/   # ["OK","<id>",true,""]
```

A body that does not end on `EOSE`/`CLOSED` (REQ), `COUNT`/`CLOSED` (COUNT) or
`OK` (EVENT) was cut off. On an AUTH-gated relay the answer is `401` until the
request carries a NIP-98 `Authorization: Nostr …` header whose `u` is the
relay's http URL and whose `payload` is the body's sha256. NIP-86 admin calls
share the URL, told apart by `Content-Type: application/nostr+json+rpc`.
Streamed answers are gzipped for clients that send `Accept-Encoding: gzip`
(`curl --compressed`), sync-flushed so events still arrive as they are found;
`[http].gzip = false` turns it off. Quartz's `HttpRelayClient` does all of this
for clients, over OkHttp via `OkHttpRelayTransport` or any `HttpRelayTransport`.

### Rate limits

`[rate_limits]` caps how many EVENTs and REQs (COUNTs, NEG-OPENs) one client may
send a minute, with a burst on top. The client is its address, or the one a
trusted proxy reports (`[http].trusted_proxies`), and its allowance is shared by
all its sockets and HTTP commands. Past it, the command is refused as NIP-01
`rate-limited:` and the frame states how long to back off, in milliseconds
(nostr-protocol/nips#2498):

```
["OK","<id>",false,"rate-limited: too many events, slow down","1500"]
["CLOSED","sub","rate-limited: too many subscriptions opened, slow down","1500"]
```

The back-off is exactly when the client's next token is due, so a client that
waits it out is let through. Over HTTP the same refusal is a `429` with
`Retry-After` set to it, rounded up to seconds. Quartz's `NostrClient` (Amethyst,
`amy`) honors it: it holds that relay's REQs or EVENTs until the back-off passes,
reconnects included, then sends them one per back-off.

## Membership (NIP-43)

With `[membership] enabled = true` geode is a members-only relay: the NIP-86
pubkey allow list is the member list and gates writes. Admins mint invite codes
with `createclaim` (e.g. `amy admin RELAY create-claim CODE`); a user joins by
sending a kind 28934 request carrying one (codes stay valid until
`deleteclaim`) and leaves with a kind 28936. The relay signs — with its
`[identity]` key, advertised as NIP-11 `self` — and serves kind 13534 (members
and their role ids), 33534 (roles from `createrole` / `editrole`; `deleterole`
publishes a NIP-09 deletion) and 8000 / 8001 (member added / removed), keeping
them in step with every join, leave and admin change. Off by default, and while
off the role / claim RPCs aren't offered. Design notes:
[`plans/2026-09-27-nip43-membership.md`](plans/2026-09-27-nip43-membership.md).

## Verbs

```
geode [relay] [flags]        serve the relay (default)
geode import [flags] [FILE…] bulk-load NDJSON events into the store
geode export [flags]         dump the store as NDJSON to stdout
```

`import`/`export` are geode's `strfry import` / `strfry export` — one JSON event
per line, for seeding, migrating, or backing up a relay. Both stream, so a
multi-million-event corpus round-trips in roughly constant memory.

## Release process

geode ships on the same tag-driven pipeline as the rest of Amethyst: pushing a
`v*` tag runs `.github/workflows/create-release.yml`, whose `build-geode` job
produces the tarball + `.deb`/`.rpm` + Homebrew jvm bundle, `docker-geode`
builds and pushes the GHCR image, and `bump-homebrew-geode-formula.yml` syncs
the reference formula. Design notes:
[`plans/2026-07-24-geode-release.md`](plans/2026-07-24-geode-release.md).
