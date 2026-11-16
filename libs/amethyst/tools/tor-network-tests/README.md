# Tor networking scenario suite

A repeatable, device-driven test harness for Amethyst's embedded Tor (Arti)
across the network transitions that have historically broken it. These checks
live outside the JVM/instrumented test tree on purpose: they manipulate the
**device's network** (airplane mode, WiFi/cellular, app lifecycle) and assert on
the real `ArtiNative` bootstrap — things a unit test can't reach.

## When to run this

Run the suite whenever you touch anything in the Tor management path, and treat
a green run as a release gate for those changes:

- **Bumping Arti** (`tools/arti-build`, the rebuilt `libarti_android.so`) — a new
  Arti version can change bootstrap timing, the SOCKS error mapping, or guard
  handling. This is the most important trigger.
- **`TorService.kt`** — lifecycle (`start`/`stop`/`reset`/`resetWithCleanState`),
  the bootstrap-timeout handling (`ARTI_ERROR_BOOTSTRAP_TIMEOUT`), or cache/state
  wiping.
- **`TorManager.kt`** — the self-heal watchdog, `onNetworkChange`, cooldowns, or
  status routing.
- **The dial gate** — `WebsocketBuilder.canConnect` / the `canDial` wiring in
  `AppModules.kt` that holds Tor-routed relays until the SOCKS port is ready.
- **The connectivity layer** — `ConnectivityManager` / `RelayProxyClientConnector`
  (pause/resume, reconnect-on-change).

## How it relates to the other tests

| Layer | Where | Covers |
|-------|-------|--------|
| Pure logic (fast, deterministic) | `amethyst/src/test/.../tor/TorManagerTest.kt` | watchdog, cooldown, `onNetworkChange`, status routing — virtual time, in-memory fakes, no Arti |
| Pure logic (fast) | `amethyst/src/test/.../service/relayClient/TorCircuitHealthTrackerTest.kt` | the Active-but-failing discriminator (companion circuit-health PR) |
| Real Arti, single process | `amethyst/src/androidTest/.../tor/TorBootstrapInstrumentedTest.kt` (`@Ignore`'d) | real `initialize` → `create_bootstrapped` → SOCKS round-trip + `destroy` |
| **Real Arti + real network transitions** | **this suite** | **bootstrap bounding, network change, airplane, pause/resume — end to end** |

The unit tests are the first line of defense (run in CI). This suite is the
end-to-end backstop you run by hand (or in a device lab) before shipping a Tor
change, because the failures it catches only appear with a real radio.

## Device setup (once)

1. Connect **one** device or emulator with internet egress to the Tor network.
   An emulator that exposes both WiFi and Cellular (e.g. a stock Pixel AVD) is
   ideal — the `wifi_cellular` scenario needs a second transport to fail over to.
2. Install the **debug** build: `./gradlew :amethyst:installPlayDebug`.
3. In the app, set **Tor = INTERNAL** and enable routing relays over Tor
   (Settings → Network/Tor). The suite does **not** change Tor settings; it only
   manipulates the network and the app lifecycle, so it tests the config the user
   actually runs.

## Running

```bash
# full suite
tools/tor-network-tests/run.sh

# a single scenario
tools/tor-network-tests/run.sh offline_bootstrap

# override the package (e.g. a different flavor)
AMETHYST_PKG=com.vitorpamplona.amethyst.debug tools/tor-network-tests/run.sh
```

Exit code is `0` only if every selected scenario passes. The suite restores a
clean network state (airplane off, WiFi+data on) when it finishes.

## Scenarios

Each scenario clears logcat, drives a transition, then asserts on Tor's lifecycle
log markers (`SOCKS proxy active`, `bootstrap timed out`, `onNetworkChange`,
`Pausing/Resuming Relay Services`, `OnOpen`).

### `cold_start`
Force-restart on a healthy network. **Asserts:** Tor reaches `Active`, and the
number of Tor-routed dials issued *before* the SOCKS port was ready stays ~0
(the #3223 gate). A spike here means the gate regressed and the pool is hammering
a dead proxy during bootstrap.

### `offline_bootstrap` — the headline regression test for this PR
Wipe Arti state (empty cache), enable airplane mode, then cold-start so
`create_bootstrapped` runs with no network and nothing cached. **Asserts:** the
native bootstrap is *bounded* — a `bootstrap timed out` line appears (the 60s
`tokio::time::timeout` releasing `lifecycleMutex` so the self-heal watchdog can
run), instead of blocking for many minutes. Then restore the network and assert
recovery to `Active`.

> Without the timeout (`fix: bound Arti bootstrap with a 60s timeout`, this PR),
> `create_bootstrapped` blocks indefinitely holding the lifecycle lock — observed
> wedging at 95s+ on an emulator — and this scenario fails with "no 'bootstrap
> timed out'". That regression is invisible to every JVM test, which is why this
> suite exists.

### `wifi_cellular`
With Tor Active, disable WiFi (the default route fails over to cellular), wait,
re-enable. **Asserts:** the transport change drives `onNetworkChange` and Tor
returns to `Active`. Catches the "stale guards/circuits from the old network"
class of wedge.

### `airplane`
Toggle airplane mode on (full offline) then off. **Asserts:** relays pause
cleanly on connectivity loss (no Tor thrash while offline) and Tor + relays
recover on restore.

### `pause_resume`
Background the app (HOME), wait for the `WhileSubscribed(30s)` wind-down, then
foreground it. **Asserts:** relays pause (`Pausing Relay Services`) while
backgrounded and reconnect (`OnOpen` increases) on resume. The reconnection burst
on resume is the same shape as the post-Active warmup burst, so this also guards
against the circuit-health self-heal firing a false positive on resume.

## Interpreting failures

The summary prints `PASS`/`FAIL` per check with a one-line reason. Common ones:

- `offline_bootstrap` FAIL "no 'bootstrap timed out'" → the native timeout isn't
  in the installed `.so` (rebuild `libarti_android.so` from `tools/arti-build`,
  or you're on a branch without the fix).
- `cold_start` FAIL "pre-ready doomed dials >5" → the `canDial`/`canConnect` gate
  regressed.
- `wifi_cellular` / `airplane` FAIL "did not reach Active" → bootstrap is slow or
  wedged on the new network; pull the full log and look for repeated
  `ExitTimeout` / `AllGuardsDown`.

For a deeper look at any run, dump the lifecycle directly:

```bash
adb logcat -d | grep -E "TorService|TorManager|ManageRelayServices" \
  | grep -iE "Initializing|bootstrap|proxy active|timed out|self-heal|reset|onNetworkChange|Pausing|Resuming"
```
