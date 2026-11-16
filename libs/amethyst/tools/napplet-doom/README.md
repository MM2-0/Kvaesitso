# Freedoom napplet (proof of concept)

DOOM, as a Nostr event. This builds [doomgeneric](https://github.com/ozkl/doomgeneric) and the
[Freedoom](https://freedoom.github.io/) Phase 1 IWAD into one self-contained `index.html` that
publishes as a NIP-5D napplet, so anyone who sees it in their feed can open it in Amethyst and play.
Nothing in Amethyst changes: the game runs in the existing napplet sandbox, under the existing CSP.

It is also a worked example of packaging a game for that sandbox.

## Build, test, play

```bash
tools/napplet-doom/build.sh                   # -> tools/napplet-doom/build/index.html (~14 MB)
```

```bash
node --test tools/napplet-doom/doom.test.mjs  # headless Chromium, production shell + shim + CSP
```

`build.sh` downloads wasi-sdk 25 and Freedoom 0.13.0 (each checked against a pinned sha256), checks
out doomgeneric at a pinned commit, applies `src/doomgeneric.patch` and compiles. The output is
reproducible: a clean rebuild gives the same artifact sha256, so anyone can rebuild a published
napplet and compare it with its `x` tag. It needs `curl`, `git`, `unzip` and `python3`; Linux
(x86_64, arm64) and macOS (x86_64, arm64) are pinned.

The test plays the build inside Amethyst's real `shell.html` and `shim.js`, with the prelude and CSP
injected the way `NappletWebContract.injectPrelude` does on the device, and a fake native bridge
that keeps storage in memory. It boots to the title loop with music, starts a game from the
keyboard, turns and fires, slides one pointer around the touch pad (directions change without
lifting it, its corners move and turn at once, and its middle presses nothing; USE is beside FIRE), saves to napplet storage, loads that save on a fresh launch, and keeps
playing when storage is denied or not declared. The mods test builds a PWAD that replaces the title
picture, serves it through a `nostr:` note that links it by `blossom:` hash, and checks the title,
the remembered mod list, the per-mod saves, and that a wrong hash is refused. Screenshots land in `build/screens/`. It uses the
preinstalled `playwright`; set `PLAYWRIGHT_MODULE` and `CHROMIUM` to use others.

Opening `build/index.html` straight in a browser also works (no napplet, so saves last only for
that session).

## Publish

```bash
./gradlew :cli:installDist
cli/build/install/amy/bin/amy napplet publish tools/napplet-doom/build/index.html \
  --server https://blossom.primal.net \
  --optional storage,resource \
  --d freedoom --title "Freedoom" \
  --description "DOOM-engine shooter: doomgeneric + Freedoom Phase 1, all in one napplet" \
  --source https://github.com/vitorpamplona/amethyst/tree/main/tools/napplet-doom \
  --relay wss://relay.damus.io,wss://nos.lol
```

`storage` keeps saves and settings; `resource` fetches mods. Both are optional: the game asks for
them only when it needs them (Amethyst prompts once) and plays either way. The Blossom server must accept a 14 MB blob. On a device, follow
`tools/napplet-test/README.md` to find and open it.

## Mods

The start screen has a **Mods** panel. Paste a link to a PWAD, or pick a local `.wad`, and it loads
on top of the game, in order, like `-file`:

- a `nostr:` note or event that links one: a `blossom:` URI or an `https://….wad` URL in its text,
  NIP-94 `url`/`x` tags, or an `imeta` tag;
- a `blossom:<sha256>.wad` URI, or an `https:` URL.

The napplet fetches it through the host's `resource` capability (up to 10 MiB, Amethyst's
`resource.bytes` cap), checks the WAD directory, refuses an IWAD, and refuses a file whose sha256
differs from the one the link names. Linked mods are remembered and fetched again on the next
launch; a local file lasts for the session. Saves and settings are kept per set of mods
(`fs:<id>:` keys), so a save from one mod never loads into another; the plain game keeps `fs:`.

Sharing a level is two steps with `amy`: `amy blossom upload --server URL level.wad`, then post a
note that contains the `blossom:` URI it prints (or the URL). Anyone can paste that note's
`nevent` into the panel.

Limits, from vanilla Doom: the shareware `doom1.wad` refuses PWADs by design ("You cannot -file
with the shareware version"), so mods need Freedoom or a registered IWAD; DeHackEd patches and
`-merge`-style sprite or flat replacements are not compiled in (`FEATURE_DEHACKED`,
`FEATURE_WAD_MERGE`). New maps, textures, music, sounds and graphics that replace lumps work.

## How it fits the sandbox

| Napplet rule | What the game does |
|---|---|
| One HTML artifact, `connect-src 'none'` | `doom.wasm` and `freedoom1.wad` are embedded as gzip+base64 and unpacked with `DecompressionStream`. The WAD is 28.8 MB raw, 10.2 MB gzipped; the page is 14 MB, under the 20 MiB the host downloads (`NappletBlobHttp.MAX_BLOB_BYTES`). |
| `script-src 'wasm-unsafe-eval'`, no `eval` | `WebAssembly.compile` on the bytes; no generated JS. |
| `worker-src 'none'` | Single-threaded build; no pthreads, no `SharedArrayBuffer`. |
| `media-src 'none'` | No `<audio>`: sound effects are DMX lumps decoded into `AudioBuffer`s, music is the engine's MUS converted to MIDI (`mus2mid`) and played by a small oscillator synth in the page. Audio starts on the Play tap. |
| Opaque origin: no `localStorage`/IndexedDB | The engine's files live in an in-memory WASI file system; `*.dsg` saves and `*.cfg` settings are gzipped into `window.napplet.storage` (512 KiB quota) and restored on the next launch. |
| Main thread only | The page calls `dg_tick()` once per 35 Hz game tic from `requestAnimationFrame`. `src/doomgeneric.patch` turns the blocking screen-melt loop into a per-tic step, and clears the frame buffer so the first melt starts from black. Hidden time is not game time, so returning to the app does not fast-forward. |
| No pointer lock, phones have no keyboard | Keyboard as in vanilla Doom, plus on-screen controls on touch devices and standard-mapping gamepads. Weapon cycling is bound to `[` `]`. |

The engine imports 14 WASI calls (files, `proc_exit`) and 10 page functions (`draw_frame`,
`ticks_ms`, sound and music); see `src/doomgeneric_napplet.c`.

## What it tells us about the hosts

- **It runs on Android's current host unchanged**, in the sense the test covers: production shell,
  shim, CSP and srcdoc bootstrap in Chromium. Not yet tried on a device: Android WebView's memory
  with a 14 MB artifact that the host base64-encodes into the shell (about 19 MB) and decodes again,
  hardware keyboards through `NappletKeyActions`, and whether the Gamepad API reaches the iframe.
- **Desktop runs it in Chromium** (JCEF), with the same shell, shim and CSP:
  `CefNappletSessionTest` boots it when `AMETHYST_DOOM_HTML` points at the build
  (`desktopApp/plans/2026-06-21-napplet-desktop-host.md`).
- **Size is the real constraint.** A bigger IWAD (Phase 2 is the same size, a PWAD on top is more)
  would not fit in one artifact. Splitting the WAD into a Blossom blob fetched through
  `resource.bytes` hits the 10 MiB `RESOURCE_MAX_BYTES` cap and a base64 hop over IPC.

## Running doom.wasm without a WebView

A spike measured whether a native card could run the napplet's `doom.wasm` on a WebAssembly
runtime instead of a WebView: one 35 Hz game tic, with every runtime's frames checked against V8's.
The benchmark itself (a Kotlin twin of the page's WASI and `doom` imports, run on
[Chicory](https://github.com/dylibso/chicory) and [Chasm](https://github.com/CharlieTap/chasm))
was removed once it had answered the question; it is in commit `6ad203fc` if it is ever needed
again.

### Results

Doom shareware `doom1.wad` (v1.9). Intel Xeon @ 2.1 GHz, 4 vCPU cloud VM; OpenJDK 21 (HotSpot);
Kotlin 2.4.21; Node 22 (V8 12.4); Chasm 2.3.0; Chicory 1.7.5. Milliseconds per tic; the budget is
28.57 ms.

| Runtime | load + start | mean | p50 | p95 | p99 | max | over budget | vs V8 |
|---|---|---|---|---|---|---|---|---|
| V8 (Node), the WebView path | 0.05 s | 0.45 | 0.40 | 0.66 | 0.82 | 4.1 | 0% | 1× |
| Chicory, compiled to JVM bytecode | 1.8 s | 3.03 | 2.75 | 5.61 | 6.61 | 26.0 | 0% | 6.7× |
| Chasm, JVM | 1.1 s | 14.53 | 12.59 | 22.38 | 24.06 | 28.9 | 0.1% | 32× |
| Chasm, Kotlin/Native (linuxX64) | 0.14 s | 20.16 | 18.18 | 31.95 | 34.33 | 46.6 | 12.2% | 45× |
| Chicory, interpreter | 1.5 s | 116.41 | 106.0 | 170.2 | 181.6 | 210.9 | 100% | 259× |

Rows are the tics after the first 350 (JIT warm-up excluded). Freedoom Phase 1 gives the same
picture, a little heavier: V8 0.60, Chicory compiled 3.59, Chasm JVM 17.59 (4.4% over budget),
Chicory interpreter 138.6.

### What it says

- **Desktop can play it natively today.** Chicory's compiler runs a tic in 3 ms, a tenth of the
  budget, with the module compiled when it loads (1.5 s on this VM). It works on any `.wasm` that
  arrives at runtime, so a game from the feed stays content.
- **Interpreters are marginal.** Chasm averages 15 ms on the JVM and 20 ms as native code, with a
  long enough tail that 12% of native tics miss the budget on this machine. A device with a faster
  core than this cloud VM could hold 35 fps, a slower one would not; Chasm on Kotlin/Native is the
  only runtime here that reaches iOS. Chicory's interpreter is about 4× too slow.
- **Android is unmeasured.** There is no device or KVM emulator in this environment. Android runs
  ART rather than HotSpot, Chicory's runtime compiler emits JVM bytecode that ART cannot load (its
  Android path converts to DEX and is experimental), and compiling the wasm into the APK at build
  time would ship the GPL engine inside Amethyst. The WebView napplet, at V8 speed, remains the
  known-good path there.

Next step: run `commonMain` (Chasm) and Chicory's interpreter on a mid-range Android phone, for
example from an instrumented test, before choosing a runtime for a native card.

## Licensing

- doomgeneric: GPL-2.0 (`build/doomgeneric/LICENSE`). The built `index.html` links it, so the
  artifact is GPL-2.0: publish it with `--source` pointing at this directory (the build pins the
  exact doomgeneric commit and patch).
- Freedoom: BSD-3-Clause (`build/freedoom-0.13.0/COPYING.txt`), redistributable. Do not swap in
  the id Software IWADs: those are not redistributable.
- `src/doomgeneric_napplet.c` and `src/doomgeneric.patch` modify or adapt doomgeneric, so they are
  GPL-2.0-or-later. The loader page, scripts and test are MIT, like Amethyst. None of this
  directory ships in Amethyst's own artifacts.
