#!/usr/bin/env bash
#
# test-amy-args.sh — pins the argument-validation surface of the `amy` verbs the
# buzz-agent path drives (`buzz agent`, `buzz workflow`, `buzz dm`, `git`).
#
# These are the strings a human or a wrapper script keys on: a flag name silently
# renamed, or a usage/error message reworded, breaks callers without breaking any
# compile. Everything here runs offline against an isolated ~/.amy — the failing
# cases are all rejected before a relay is contacted.
#
#   ./gradlew :cli:installDist
#   ./tools/buzz-agent/test-amy-args.sh
#   AMY=/path/to/amy ./tools/buzz-agent/test-amy-args.sh   # or point at another build
#
# Exits 0 when every case passes, 1 otherwise.

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
AMY="${AMY:-$ROOT/cli/build/install/amy/bin/amy}"
if [[ ! -x "$AMY" ]]; then
    echo "no amy binary at $AMY — build one with:  ./gradlew :cli:installDist" >&2
    echo "(or set AMY=/path/to/amy)" >&2
    exit 2
fi

# Isolate the data dir: `amy.home` is the seam the JVM tests use too, so this never
# touches a real ~/.amy.
AMY_HOME="$(mktemp -d)"
trap 'rm -rf "$AMY_HOME"' EXIT
export JAVA_OPTS="-Damy.home=$AMY_HOME"

pass=0
fail=0

expect() { # name, expected-substring, amy args...
    local name="$1" want="$2"
    shift 2
    local out
    out="$("$AMY" "$@" 2>&1)"
    if [[ "$out" == *"$want"* ]]; then
        printf '  PASS  %s\n' "$name"
        pass=$((pass + 1))
    else
        printf '  FAIL  %s\n        want output containing %q\n        got: %s\n' \
            "$name" "$want" "$(echo "$out" | head -3 | tr '\n' ' ')"
        fail=$((fail + 1))
    fi
}

reject() { # name, unwanted-substring, amy args...
    local name="$1" unwanted="$2"
    shift 2
    local out
    out="$("$AMY" "$@" 2>&1)"
    if [[ "$out" != *"$unwanted"* ]]; then
        printf '  PASS  %s\n' "$name"
        pass=$((pass + 1))
    else
        printf '  FAIL  %s\n        output must NOT contain %q\n        got: %s\n' \
            "$name" "$unwanted" "$(echo "$out" | head -3 | tr '\n' ' ')"
        fail=$((fail + 1))
    fi
}

printf '\namy arg-surface harness: %s\n\n' "$AMY"

"$AMY" --account harness init > /dev/null 2>&1 || { echo "could not create a throwaway account" >&2; exit 1; }
A=(--account harness)
DEAD_RELAY=wss://127.0.0.1:1   # closed port: connects and fails immediately

# --- the positional name every `git` verb shares ---------------------------
for verb in browse cat log; do
    expect "git $verb names its missing positional" "repo-naddr-or-coordinates" git "$verb"
done

# --- required flags on the workflow verbs ----------------------------------
expect "workflow trigger demands a channel" "pass --channel GID" \
    "${A[@]}" buzz workflow trigger "$DEAD_RELAY" wf1 --task "do a thing"
expect "workflow list demands a channel" "pass --channel GID" \
    "${A[@]}" buzz workflow list "$DEAD_RELAY"

# --- the --base-ref flag name ----------------------------------------------
# `agent up` checks --repo before parsing the rest, so point it at a real repo to
# reach rejectUnknown.
expect "agent up rejects a mistyped --base-ref" "unknown flag: --base-reff" \
    "${A[@]}" buzz agent up "$DEAD_RELAY" --repo "$ROOT" --approver npub1qqq --base-reff x --timeout 1
reject "agent up accepts --base-ref" "unknown flag" \
    "${A[@]}" buzz agent up "$DEAD_RELAY" --repo "$ROOT" --approver npub1qqq --base-ref x --timeout 1
expect "agent serve rejects a mistyped --base-ref" "unknown flag: --base-reff" \
    "${A[@]}" buzz agent serve "$DEAD_RELAY" --exec true --base-reff x
reject "agent serve accepts --base-ref" "unknown flag" \
    "${A[@]}" buzz agent serve "$DEAD_RELAY" --exec true --base-ref x --once --timeout 1

# --- the no_relays detail ---------------------------------------------------
# Passing --relays wins over the outbox, so an all-unparseable list yields an empty
# set and must NOT tell the user to pass the flag they just passed. Note a bare word
# like "garbage" is *accepted* (the normalizer prepends wss://) — a pasted http url
# is the realistic rejection.
for bad in "http://x" "ws://" "," "!!!"; do
    expect "buzz dm list names the real problem for --relays '$bad'" \
        "no usable relays in --relays" "${A[@]}" buzz dm list --relays "$bad" --timeout 2
done
reject "buzz dm list accepts a well-formed relay" "no_relays" \
    "${A[@]}" buzz dm list --relays "$DEAD_RELAY" --timeout 2

printf '\n  %d passed, %d failed\n\n' "$pass" "$fail"
[[ "$fail" -eq 0 ]]
