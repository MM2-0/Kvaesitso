#!/usr/bin/env bash
#
# Verify that libzxingcpp_android.so builds reproducibly.
#
# Builds the native library twice from a clean state into scratch directories
# and confirms the two outputs are byte-for-byte identical, then compares them
# against what is committed under jniLibs. Both builds compile in the canonical
# path, so a match here means any checkout -- ours, F-Droid's, an auditor's --
# produces the same bytes. See README.md -> "Reproducible builds".
#
# The second build runs against a copy of the NDK in a different directory. Two
# builds from the same NDK path cannot see a dependency on where the NDK lives,
# and that is exactly how the build-id leak got through: lld hashed debug info
# that named the NDK's install path, so F-Droid's rebuild differed from ours in
# 20 bytes while this script reported a perfect match. Each output is also
# checked for absolute paths, which is what such a leak usually looks like.
#
# Usage:
#   ./verify-reproducible.sh                  # every ABI
#   ./verify-reproducible.sh --abi arm64-v8a  # one ABI (faster)
#
# Exit 0 = reproducible and matching the commit, exit 1 = they differ.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
JNILIBS="$PROJECT_ROOT/amethyst/src/main/jniLibs"
LIB_NAME="libzxingcpp_android.so"
BUILD_ROOT="${ZXING_REPRO_DIR:-/tmp/amethyst-zxingcpp-build}"

# Only the ABIs this run actually rebuilds. Hashing the whole tree would let an
# untouched ABI hash identically in both runs and report the lot reproducible --
# the same trap tools/arti-build hit and documents.
ABIS=(arm64-v8a armeabi-v7a x86 x86_64)
PASSTHRU=()
while [ $# -gt 0 ]; do
    case "$1" in
        --abi)
            [ $# -ge 2 ] && [ -n "$2" ] || { echo "--abi needs a value" >&2; exit 2; }
            ABIS=("$2"); PASSTHRU+=("$1" "$2"); shift 2 ;;
        *) echo "Unknown argument: $1 (only --abi is supported here)" >&2; exit 2 ;;
    esac
done

sha256() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$@"; else shasum -a 256 "$@"; fi
}

hashes_in() {
    local dir="$1" abi
    # `if`, not `[ -f ] && sha256`: the latter returns 1 for a missing last ABI, and under set -e
    # that aborted the caller's $(...) with no message at all.
    ( cd "$dir" && for abi in "${ABIS[@]}"; do
        if [ -f "$abi/$LIB_NAME" ]; then sha256 "$abi/$LIB_NAME"; fi
    done )
}

NDK="$("$SCRIPT_DIR/build-zxingcpp.sh" --print-ndk)"

RUN_A="$(mktemp -d -t zxingcpp-verify-a.XXXXXX)"
RUN_B="$(mktemp -d -t zxingcpp-verify-b.XXXXXX)"
# Next to the real NDK so it can be hard-linked (instant, no extra space); a plain copy into the
# scratch dir is the fallback when that directory is not writable or on another filesystem.
RELOCATED_NDK="$(dirname "$NDK")/.zxingcpp-verify-ndk.$$"
trap 'rm -rf "$RUN_A" "$RUN_B" "$RELOCATED_NDK"' EXIT

if ! cp -al "$NDK" "$RELOCATED_NDK" 2>/dev/null; then
    rm -rf "$RELOCATED_NDK"
    RELOCATED_NDK="$RUN_B/ndk"
    printf '==> Copying the NDK to a second location (could not hard-link it)\n'
    cp -a "$NDK" "$RELOCATED_NDK"
fi

printf '==> Build 1 of 2 (NDK at %s)\n' "$NDK"
ANDROID_NDK_HOME="$NDK" "$SCRIPT_DIR/build-zxingcpp.sh" --out "$RUN_A/out" ${PASSTHRU[@]+"${PASSTHRU[@]}"} >/dev/null

printf '==> Build 2 of 2 (NDK at %s)\n' "$RELOCATED_NDK"
ANDROID_NDK_HOME="$RELOCATED_NDK" "$SCRIPT_DIR/build-zxingcpp.sh" --out "$RUN_B/out" ${PASSTHRU[@]+"${PASSTHRU[@]}"} >/dev/null

STRINGS_TOOL=""
for candidate in "$NDK"/toolchains/llvm/prebuilt/*/bin/llvm-strings; do
    [ -x "$candidate" ] && { STRINGS_TOOL="$candidate"; break; }
done
[ -n "$STRINGS_TOOL" ] || STRINGS_TOOL="$(command -v strings || true)"

if [ -n "$STRINGS_TOOL" ]; then
    LEAKS=""
    for abi in "${ABIS[@]}"; do
        for run in "$RUN_A/out" "$RUN_B/out"; do
            found="$("$STRINGS_TOOL" "$run/$abi/$LIB_NAME" | grep -F -e "$NDK" -e "$RELOCATED_NDK" -e "$BUILD_ROOT" -e "$PROJECT_ROOT" || true)"
            # Plus any other host path. Anchored on real top-level directories rather than "any
            # /x/y", which random bytes in .rodata match -- and never /build/ or /zxing-cpp/, the
            # stable prefixes repro-env.sh rewrites paths to on purpose.
            found="$found$("$STRINGS_TOOL" "$run/$abi/$LIB_NAME" | grep -E '^/(home|Users|root|tmp|private|var|opt|usr|mnt)/' || true)"
            [ -n "$found" ] && LEAKS="$LEAKS\n  $abi: $(printf '%s' "$found" | head -3 | tr '\n' ' ')"
        done
    done
    if [ -n "$LEAKS" ]; then
        printf '\nNOT REPRODUCIBLE -- absolute paths in the output:%b\n' "$LEAKS"
        exit 1
    fi
else
    printf 'warning: no strings tool found; absolute paths not checked\n'
fi

A="$(hashes_in "$RUN_A/out")"
B="$(hashes_in "$RUN_B/out")"

if [ "$A" != "$B" ]; then
    printf '\nNOT REPRODUCIBLE -- the two builds differ:\n'
    diff <(printf '%s\n' "$A") <(printf '%s\n' "$B") || true
    exit 1
fi

printf '\nReproducible: two builds produced identical bytes.\n'
printf '%s\n' "$A" | sed 's/^/  /'

# A reproducible build that does not match the commit is still a problem: it
# means the shipped library came from something other than this tag.
COMMITTED="$(hashes_in "$JNILIBS")"
if [ "$COMMITTED" != "$A" ]; then
    printf '\nWARNING: the committed libraries do NOT match this build.\n'
    diff <(printf '%s\n' "$COMMITTED") <(printf '%s\n' "$A") || true
    printf '\nRun build-zxingcpp.sh and commit the result.\n'
    exit 1
fi

printf '\nCommitted libraries match.\n'
