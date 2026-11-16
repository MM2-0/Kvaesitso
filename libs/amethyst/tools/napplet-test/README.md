# Napplet test harness

A self-contained napplet for **on-device verification** of Amethyst's NIP-5D host — it calls every
`window.napplet.*` API and shows each result on screen, so you can confirm the whole
shim → shell → broker → consent round-trip (and the newer `identity.getList/getZaps/getBadges`,
`identity.onChanged`, `keys.onAction`, and `resource.bytes` `nostr:` paths) works on a real device.

`index.html` is the napplet: read-only checks run on load; publish/upload/pay are behind buttons; live
pushes (`identity.changed`, `keys.action`) land in the top banner.

## Publish it

`amy napplet publish` uploads the single self-contained HTML artifact to Blossom (BUD-02 signed) and broadcasts the NIP-5D
event in one step, as the account `amy` is logged in as:

```bash
./gradlew :cli:installDist            # builds amy → cli/build/install/amy/bin/amy
amy napplet publish tools/napplet-test/index.html \
  --server https://blossom.primal.net \
  --requires identity,relay,storage,resource --optional theme \
  --d napplet-test --title "Napplet Test Harness" \
  --description "Exercises Amethyst napplet domains and sandbox isolation" \
  --relay wss://relay.damus.io,wss://nos.lol
```

- `--d` makes it an addressable kind-35129 napplet; omit it for a root kind-15129.
- `--icon <sha256> --icon-mime image/png` references an icon already uploaded to the declared Blossom server. PNG, JPEG and WebP are supported; invalid icons use generic artwork.
- For a plain static site, use `amy nsite publish <dir> --server …` (kind 15128/35128).
- Use the **same key you're logged in as in Amethyst**, so the napplet appears under your account and
  the identity reads (`getProfile`, `getFollows`, …) have data.

Verify it resolves (optional): `amy napplet fetch <your-pubkey-hex> --d napplet-test`.
List everything you've published: `amy napplet list <your-pubkey-hex>` (or `amy nsite list <pubkey>`).

## Preview in a browser (optional)

`amy napplet serve` resolves the published manifest and serves its static content locally (each blob
sha256-verified, just like the device host) so you can eyeball that it loads:

```bash
amy napplet serve <your-pubkey-hex> --d napplet-test --port 8080   # then open http://127.0.0.1:8080
```

This serves the **files only** — the `window.napplet.*` runtime needs the Amethyst host, so the API
rows won't pass in a plain browser. Use it to confirm the files publish and route correctly.

## Run on a device

Build & install the debug app and watch the logs:

```bash
./gradlew :amethyst:installPlayDebug
adb logcat -s NappletHostActivity NappletBrokerService NappletContentServer
```

Logged in as the publishing key, find **"Napplet Test Harness"** in your Apps / Napplets list (or its
feed card) and tap **Open**. It launches in the sandboxed `:napplet` process.

## What to verify

- **On load:** each read row turns green. `window.napplet` contains the exposed domains immediately; no shell handshake is needed. Optional unavailable domains are skipped. Sensitive capabilities prompt for consent; theme does not.
- **identity.getList/getZaps/getBadges:** open your own profile first so the cache has your lists /
  zaps / badges, then relaunch — the rows show your data (empty arrays are valid if you have none).
- **identity.onChanged:** the surface remains bound to its launching account. Logging that account out sends an empty key; switching the active account elsewhere must not change the surface identity.
- **keys.onAction:** with a hardware keyboard (or `adb shell input keyevent 47` for "S" while holding
  Ctrl), press **Ctrl+S** → banner shows `keys.action → save fired`, and the keystroke is consumed.
- **resource.bytes `nostr:`:** paste a `nostr:nevent1…` / `note1…` / `naddr1…` / `npub1…` and run →
  it returns the event JSON as a blob. Also try an `https://…` image URL.
- **Side effects (deliberate):** `relay.publish` signs+broadcasts a note as you; `upload.blob` uploads
  a tiny blob; `value.payInvoice` pays a BOLT-11 invoice (needs a connected wallet). Each prompts.
- **Security:** the applet has no direct network (a plain `fetch()` inside it fails — CSP
  `connect-src 'none'`); an undeclared capability is denied even if you'd allow it.

## Notes

- Re-running `amy napplet publish … --d napplet-test` replaces the same addressable event.

## Current schema and automated checks

Legacy manifests must be republished: `x` is now the HTML artifact hash, descriptions live in event content, and capabilities use `R`/`O` tags. Unknown required domains prevent launch; unknown optional domains do not. Storage is isolated by signed manifest key and artifact hash, so legacy storage is not reused by a current-schema deployment.

`theme.onChanged` should fire when the user changes the app theme, without a prior `theme.get`. Close its returned handle to stop local callbacks. The keys/upload/value controls are optional probes; Amethyst currently exposes identity, relay, storage, resource, and theme only.

Browser checks exercise the actual shell and shim with a fake native bridge, including CSP, WebAssembly, theme delivery, and canonical resource bytes:

```sh
# Install playwright-core in a scratch directory; use the installed Chrome binary.
PLAYWRIGHT_MODULE=/path/to/playwright-core/index.mjs node --test tools/napplet-test/protocol.test.mjs
```

Set `CHROMIUM` when Chrome is not at its standard macOS location. These checks do not publish events or use an account.
