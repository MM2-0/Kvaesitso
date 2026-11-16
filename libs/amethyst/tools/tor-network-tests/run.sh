#!/usr/bin/env bash
#
# Repeatable Tor networking scenario suite for Amethyst (Android).
#
# Drives a connected device/emulator through the network transitions that have
# historically broken Tor (cold start, hostile/offline bootstrap, WiFi<->Cellular
# handover, airplane mode, app pause/resume) and asserts the Tor lifecycle
# invariants from logcat. See README.md for the rationale of each scenario and
# when to re-run (new Arti version, changes to TorService/TorManager/the dial gate).
#
# Usage:
#   tools/tor-network-tests/run.sh [scenario ...]
#
#   With no args, runs the full suite. Otherwise runs only the named scenarios:
#     cold_start  offline_bootstrap  wifi_cellular  airplane  pause_resume
#
# Requirements:
#   - adb on PATH with exactly one device/emulator connected and Tor egress.
#   - The Amethyst *debug* build installed with Tor set to INTERNAL and routing
#     relays over Tor (see README "Device setup"). The suite never changes Tor
#     settings — it only manipulates the network and the app lifecycle.
#
# Exit code: 0 if every selected scenario passes, 1 otherwise.

set -u

PKG="${AMETHYST_PKG:-com.vitorpamplona.amethyst.debug}"
ACT="${AMETHYST_ACTIVITY:-$PKG/com.vitorpamplona.amethyst.ui.MainActivity}"

# ---- thresholds (mirror the constants in TorService.kt / TorManager.kt) --------
BOOTSTRAP_DEADLINE_S=120   # cold start should reach Active within this
TIMEOUT_LOG_DEADLINE_S=90  # empty-cache offline start should log the bootstrap timeout within this
RECOVER_DEADLINE_S=120     # after restoring network, Tor should reach Active within this
PAUSE_WINDDOWN_S=60        # backgrounded relays should pause within this (WhileSubscribed=30s + slack)

PASS=0
FAIL=0
RESULTS=()

# ---- helpers -------------------------------------------------------------------

log()    { printf '  %s\n' "$*"; }
banner() { printf '\n=== %s ===\n' "$*"; }

restart_clean_network() {
    adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1
    adb shell svc wifi enable  >/dev/null 2>&1
    adb shell svc data enable  >/dev/null 2>&1
}

force_restart_app() {
    adb shell am force-stop "$PKG" >/dev/null 2>&1
    sleep 1
    adb shell am start -n "$ACT" >/dev/null 2>&1
}

wipe_arti_state() {
    adb shell "run-as $PKG rm -rf /data/data/$PKG/files/arti" >/dev/null 2>&1
}

# Wait up to $1 seconds for logcat (current buffer) to contain the regex in $2.
# Returns 0 as soon as it appears, 1 on timeout.
wait_for_log() {
    local deadline=$1 pattern=$2 i
    for (( i = 0; i < deadline; i += 3 )); do
        if adb logcat -d 2>/dev/null | grep -qE "$pattern"; then return 0; fi
        sleep 3
    done
    return 1
}

count_log() { adb logcat -d 2>/dev/null | grep -cE "$1"; }

# Doomed Tor dials issued BEFORE the SOCKS proxy was ready (the #3223 gate must keep this ~0).
pre_ready_dials() {
    local first_active
    first_active=$(adb logcat -d 2>/dev/null | grep -m1 "SOCKS proxy active" | awk '{print $2}')
    [ -z "$first_active" ] && { echo "-1"; return; }
    adb logcat -d 2>/dev/null | grep "SOCKS: Connection refused" \
        | awk -v t="$first_active" '$2 < t' | wc -l | tr -d ' '
}

record() { # name  pass(0/1)  detail
    if [ "$2" -eq 0 ]; then PASS=$((PASS+1)); RESULTS+=("PASS  $1 — $3"); log "PASS: $3"
    else                    FAIL=$((FAIL+1)); RESULTS+=("FAIL  $1 — $3"); log "FAIL: $3"; fi
}

# ---- scenarios -----------------------------------------------------------------

scenario_cold_start() {
    banner "cold_start — Tor bootstraps to Active on a healthy network, no pre-ready dial storm"
    restart_clean_network; sleep 2
    adb logcat -c
    force_restart_app
    if wait_for_log "$BOOTSTRAP_DEADLINE_S" "SOCKS proxy active"; then
        local pre; pre=$(pre_ready_dials)
        if [ "$pre" -le 5 ]; then
            record cold_start 0 "reached Active; pre-ready doomed dials=$pre (gate OK)"
        else
            record cold_start 1 "reached Active but pre-ready doomed dials=$pre (>5 — dial gate regressed)"
        fi
    else
        record cold_start 1 "Tor did not reach Active within ${BOOTSTRAP_DEADLINE_S}s"
    fi
}

scenario_offline_bootstrap() {
    banner "offline_bootstrap — empty cache + no network must NOT wedge (PR: 60s bootstrap timeout)"
    adb shell am force-stop "$PKG" >/dev/null 2>&1; sleep 1
    wipe_arti_state
    adb shell cmd connectivity airplane-mode enable >/dev/null 2>&1
    sleep 3
    adb logcat -c
    force_restart_app
    log "cold-started offline with empty cache; waiting for the bootstrap-timeout log..."
    if wait_for_log "$TIMEOUT_LOG_DEADLINE_S" "bootstrap timed out"; then
        record offline_bootstrap 0 "native bootstrap bounded — 'bootstrap timed out' logged (lock released for self-heal)"
    else
        record offline_bootstrap 1 "no 'bootstrap timed out' within ${TIMEOUT_LOG_DEADLINE_S}s — create_bootstrapped is unbounded (PR #3225 not effective)"
    fi
    log "restoring network; expecting recovery to Active..."
    restart_clean_network
    if wait_for_log "$RECOVER_DEADLINE_S" "SOCKS proxy active"; then
        record offline_bootstrap_recover 0 "reached Active after network restore"
    else
        record offline_bootstrap_recover 1 "did not reach Active within ${RECOVER_DEADLINE_S}s after restore"
    fi
}

scenario_wifi_cellular() {
    banner "wifi_cellular — WiFi off (failover to cellular) then on triggers onNetworkChange + recovery"
    restart_clean_network
    if ! wait_for_log "$BOOTSTRAP_DEADLINE_S" "SOCKS proxy active"; then
        record wifi_cellular 1 "precondition failed: Tor not Active before the handover"; return
    fi
    adb logcat -c
    adb shell svc wifi disable >/dev/null 2>&1
    sleep 20
    adb shell svc wifi enable  >/dev/null 2>&1
    sleep 5
    local changes; changes=$(count_log "onNetworkChange|network identity changed")
    if wait_for_log "$RECOVER_DEADLINE_S" "SOCKS proxy active"; then
        record wifi_cellular 0 "handover recovered to Active (network-change events seen: $changes)"
    else
        record wifi_cellular 1 "Tor did not return to Active within ${RECOVER_DEADLINE_S}s after the handover"
    fi
}

scenario_airplane() {
    banner "airplane — full offline pauses relays cleanly; restore recovers Tor + resumes relays"
    restart_clean_network
    wait_for_log "$BOOTSTRAP_DEADLINE_S" "SOCKS proxy active" >/dev/null
    adb logcat -c
    adb shell cmd connectivity airplane-mode enable >/dev/null 2>&1
    if wait_for_log 30 "Connectivity Off|Pausing Relay Services"; then
        log "relays paused on connectivity loss"
    else
        log "WARN: no 'Pausing Relay Services' seen on airplane-on"
    fi
    sleep 5
    adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1
    if wait_for_log "$RECOVER_DEADLINE_S" "SOCKS proxy active|Resuming Relay Services"; then
        record airplane 0 "recovered after airplane off/on"
    else
        record airplane 1 "did not recover within ${RECOVER_DEADLINE_S}s after airplane off"
    fi
}

scenario_pause_resume() {
    banner "pause_resume — backgrounding winds relays down (~30s); resume reconnects them"
    restart_clean_network
    wait_for_log "$BOOTSTRAP_DEADLINE_S" "SOCKS proxy active" >/dev/null
    adb logcat -c
    adb shell input keyevent KEYCODE_HOME
    if wait_for_log "$PAUSE_WINDDOWN_S" "Pausing Relay Services"; then
        record pause 0 "relays paused after backgrounding (WhileSubscribed wind-down)"
    else
        record pause 1 "relays did not pause within ${PAUSE_WINDDOWN_S}s of backgrounding"
    fi
    local before; before=$(count_log "OnOpen")
    adb shell am start -n "$ACT" >/dev/null 2>&1
    wait_for_log 30 "Resuming Relay Services" >/dev/null
    sleep 15
    local after; after=$(count_log "OnOpen")
    if [ "$after" -gt "$before" ]; then
        record resume 0 "relays reconnected on resume (OnOpen $before -> $after)"
    else
        record resume 1 "no relay reconnects observed on resume (OnOpen stuck at $before)"
    fi
}

# ---- driver --------------------------------------------------------------------

ndev=$(adb devices | grep -cw "device")
if [ "$ndev" -ne 1 ]; then
    echo "ERROR: need exactly one connected device/emulator (found $ndev). Set ANDROID_SERIAL or unplug extras." >&2
    exit 2
fi
if ! adb shell pm list packages 2>/dev/null | grep -q "$PKG"; then
    echo "ERROR: $PKG is not installed. Install the debug build first (see README)." >&2
    exit 2
fi

scenarios=("$@")
[ ${#scenarios[@]} -eq 0 ] && scenarios=(cold_start offline_bootstrap wifi_cellular airplane pause_resume)

echo "Tor network suite — package=$PKG, scenarios: ${scenarios[*]}"
for s in "${scenarios[@]}"; do
    case "$s" in
        cold_start)        scenario_cold_start ;;
        offline_bootstrap) scenario_offline_bootstrap ;;
        wifi_cellular)     scenario_wifi_cellular ;;
        airplane)          scenario_airplane ;;
        pause_resume)      scenario_pause_resume ;;
        *) echo "  unknown scenario: $s" ; FAIL=$((FAIL+1)) ; RESULTS+=("FAIL  $s — unknown scenario") ;;
    esac
done

restart_clean_network  # leave the device in a good state

banner "SUMMARY"
for r in "${RESULTS[@]}"; do printf '  %s\n' "$r"; done
printf '\n  %d passed, %d failed\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
