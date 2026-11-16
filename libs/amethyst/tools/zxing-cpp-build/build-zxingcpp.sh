#!/usr/bin/env bash
#
# Build libzxingcpp_android.so from zxing-cpp source, reproducibly.
#
# Replaces the prebuilt io.github.zxing-cpp:android AAR, whose four .so files
# were built by a third party on unknown toolchains and which nothing in this
# tree could verify. Same standard tools/arti-build holds libarti_android.so
# to: pinned NDK, pinned source, canonical build path, verifiable bytes.
#
# Usage:
#   ./build-zxingcpp.sh                  # every ABI
#   ./build-zxingcpp.sh --abi arm64-v8a  # one ABI (faster)
#   ./build-zxingcpp.sh --out DIR        # write .so somewhere else (verify uses this)
#   ./build-zxingcpp.sh --print-ndk      # print the pinned NDK's path and exit
#
# Prerequisites: git, cmake, ninja, and the exact NDK revision in
# tools/arti-build/ANDROID_NDK_VERSION. Any other revision is refused — it
# would change the output bytes, which is the whole point.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

ZXING_VERSION="$(tr -d '[:space:]' < "$SCRIPT_DIR/ZXING_CPP_VERSION")"
# The tag names the release; the commit is what is actually pinned. A tag can be moved upstream,
# and a build keyed on the name alone would then quietly differ between a machine with an old
# clone and a fresh one (F-Droid's), while still printing the same version.
ZXING_COMMIT="$(tr -d '[:space:]' < "$SCRIPT_DIR/ZXING_CPP_COMMIT")"
# One pin for the whole repo, deliberately not a copy of our own. :amethyst
# already reads this same file for `ndkVersion` (the NDK that strips whatever
# lands in src/main/jniLibs), so a second copy here could only ever be a way
# to disagree with the toolchain that packages the .so we produce — the exact
# byte-level drift the pin exists to prevent. Bumping it rebuilds both
# libarti_android.so and libzxingcpp_android.so, which is correct: they must
# be built and stripped by one NDK.
NDK_VERSION="$(tr -d '[:space:]' < "$PROJECT_ROOT/tools/arti-build/ANDROID_NDK_VERSION")"

# Canonical build path. Codegen and link ordering can key on the real build
# directory even with path remapping in place, so everyone builds here or
# nobody's bytes match. Overriding it changes the output; only do that if you
# do not care about matching the published .so.
BUILD_ROOT="${ZXING_REPRO_DIR:-/tmp/amethyst-zxingcpp-build}"
SOURCE_DIR="$BUILD_ROOT/.zxing-cpp-source"
OUTPUT_DIR="$PROJECT_ROOT/amethyst/src/main/jniLibs"
LIB_NAME="libzxingcpp_android.so"
MIN_SDK_VERSION=26

# Every ABI the app splits on. A QR scanner that does not load is a broken core
# feature — the decoder this replaced was pure Java and worked everywhere — so
# every split gets one, and :amethyst's verifyNativeAbis fails the build if one
# is missing or built for the wrong architecture.
ABIS=(arm64-v8a armeabi-v7a x86 x86_64)

PRINT_NDK=false

need_value() {
    [ $# -ge 2 ] && [ -n "$2" ] || { echo "$1 needs a value" >&2; exit 2; }
}

while [ $# -gt 0 ]; do
    case "$1" in
        --abi) need_value "$@"; ABIS=("$2"); shift 2 ;;
        --out) need_value "$@"; OUTPUT_DIR="$2"; shift 2 ;;
        --print-ndk) PRINT_NDK=true; shift ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
done

info()  { printf '\033[0;34m==>\033[0m %s\n' "$1"; }
ok()    { printf '\033[0;32m  ok\033[0m %s\n' "$1"; }
fail()  { printf '\033[0;31merror:\033[0m %s\n' "$1" >&2; exit 1; }

# ---------------------------------------------------------------------------
# NDK: find the pinned revision, refuse anything else
# ---------------------------------------------------------------------------

ndk_revision() {
    sed -n 's/^Pkg\.Revision *= *//p' "$1/source.properties" 2>/dev/null | tr -d '[:space:]' || true
}

find_ndk() {
    # No wildcards. An exported ANDROID_NDK_HOME is only a hint: CI images and
    # IDE installs routinely point it at a bundled NDK that is not ours, so each
    # candidate is checked against source.properties and skipped when it does
    # not match. Picking "some NDK" is exactly how arti's committed binaries
    # ended up built by r25b while its docs asked for r27.
    local candidate revision rejected=""
    for candidate in \
        "${ANDROID_NDK_HOME:-}" \
        "${ANDROID_NDK_ROOT:-}" \
        "${ANDROID_HOME:-}/ndk/$NDK_VERSION" \
        "${ANDROID_SDK_ROOT:-}/ndk/$NDK_VERSION" \
        "${HOME:-}/Android/Sdk/ndk/$NDK_VERSION" \
        "${HOME:-}/Library/Android/sdk/ndk/$NDK_VERSION" \
        "/usr/local/lib/android/sdk/ndk/$NDK_VERSION"; do
        [ -n "$candidate" ] || continue
        [ -d "$candidate" ] || continue
        revision="$(ndk_revision "$candidate")"
        if [ "$revision" = "$NDK_VERSION" ]; then
            echo "$candidate"
            return 0
        fi
        [ -n "$revision" ] && rejected="$rejected\n    $candidate (r$revision)"
    done

    printf 'error: NDK %s not found.\n' "$NDK_VERSION" >&2
    [ -n "$rejected" ] && printf '  rejected:%b\n' "$rejected" >&2
    printf '  install it with: sdkmanager "ndk;%s"\n' "$NDK_VERSION" >&2
    return 1
}

# An ELF tool, preferring the pinned NDK's own llvm-* copy so the post-build
# checks work on hosts without binutils (notably macOS).
ndk_tool() {
    local name="$1" candidate
    for candidate in "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/bin/"llvm-$name"; do
        [ -x "$candidate" ] && { echo "$candidate"; return 0; }
    done
    command -v "$name" 2>/dev/null && return 0
    return 1
}

# ---------------------------------------------------------------------------
# Source
# ---------------------------------------------------------------------------

fetch_source() {
    if [ ! -d "$SOURCE_DIR/.git" ]; then
        info "Cloning zxing-cpp $ZXING_VERSION"
        rm -rf "$SOURCE_DIR"
        git clone --depth 1 --branch "$ZXING_VERSION" \
            https://github.com/zxing-cpp/zxing-cpp.git "$SOURCE_DIR" >/dev/null 2>&1 \
            || fail "could not clone zxing-cpp at $ZXING_VERSION"

        # A fresh clone is the one place the tag can be checked against the pin. Disagreeing
        # means upstream moved the tag: stop rather than build either side of that silently.
        local tagged
        tagged="$(git -C "$SOURCE_DIR" rev-parse HEAD)"
        [ "$tagged" = "$ZXING_COMMIT" ] \
            || fail "tag $ZXING_VERSION now points at $tagged, not the pinned $ZXING_COMMIT -- upstream moved it"
    elif ! git -C "$SOURCE_DIR" cat-file -e "$ZXING_COMMIT^{commit}" 2>/dev/null; then
        info "Fetching zxing-cpp $ZXING_COMMIT"
        git -C "$SOURCE_DIR" fetch --depth 1 origin "$ZXING_COMMIT" >/dev/null 2>&1 \
            || fail "could not fetch zxing-cpp $ZXING_COMMIT"
    fi

    # Forced and cleaned: a plain checkout carries uncommitted edits in a reused clone along with
    # it, and the build would then report the pinned commit while compiling something else.
    git -C "$SOURCE_DIR" checkout -q -f --detach "$ZXING_COMMIT" \
        || fail "could not check out zxing-cpp $ZXING_COMMIT"
    git -C "$SOURCE_DIR" clean -q -fdx

    local head
    head="$(git -C "$SOURCE_DIR" rev-parse HEAD)"
    [ "$head" = "$ZXING_COMMIT" ] || fail "clone is at $head, expected $ZXING_COMMIT"
    [ -z "$(git -C "$SOURCE_DIR" status --porcelain)" ] || fail "clone at $SOURCE_DIR has local changes"
    ok "zxing-cpp $ZXING_VERSION at $head"
}

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------

build_abi() {
    local abi="$1"
    local build_dir="$BUILD_ROOT/build/$abi"

    info "Building $abi"
    rm -rf "$build_dir"
    mkdir -p "$build_dir"

    # The wrapper's own CMakeLists pulls in core/ and sets the flags upstream
    # cares about (readers only, hidden visibility, --exclude-libs). Everything
    # added here is either reproducibility or the release/strip settings the
    # published AAR applies at packaging time rather than in CMake.
    cmake -S "$SOURCE_DIR/wrappers/android/zxingcpp/src/main/cpp" -B "$build_dir" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$abi" \
        -DANDROID_PLATFORM="android-$MIN_SDK_VERSION" \
        -DANDROID_ARM_NEON=ON \
        -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
        -DCMAKE_BUILD_TYPE=Release \
        -DZXING_WRITERS=OFF \
        -DZXING_READERS=ON \
        -DZXING_UNIT_TESTS=OFF \
        -DCMAKE_C_FLAGS="$ZXING_REPRO_CFLAGS" \
        -DCMAKE_CXX_FLAGS="$ZXING_REPRO_CFLAGS" \
        -DCMAKE_SHARED_LINKER_FLAGS="$ZXING_REPRO_LDFLAGS" \
        >/dev/null || fail "cmake configure failed for $abi"

    cmake --build "$build_dir" --target zxingcpp_android >/dev/null \
        || fail "build failed for $abi"

    local built="$build_dir/$LIB_NAME"
    [ -f "$built" ] || fail "$LIB_NAME not produced for $abi"

    # Strip: the published AAR ships stripped libraries (AGP strips at packaging
    # time), so an unstripped one would differ from what the app used to carry
    # for no benefit — debug info in a shipped .so helps nobody here.
    local strip_tool
    strip_tool="$(ndk_tool strip)" || fail "no strip tool found"
    "$strip_tool" --strip-unneeded "$built"

    mkdir -p "$OUTPUT_DIR/$abi"
    cp "$built" "$OUTPUT_DIR/$abi/$LIB_NAME"

    verify_abi "$abi" "$OUTPUT_DIR/$abi/$LIB_NAME"
}

# Re-check the NDK stamp in the output rather than trusting that the right NDK
# was selected: this is what catches a toolchain that was picked up despite the
# revision gate (a stale CMake cache, an overriding environment variable).
verify_abi() {
    local abi="$1" lib="$2"
    local readelf
    readelf="$(ndk_tool readelf)" || { ok "$abi (no readelf; stamp not checked)"; return 0; }

    # .note.android.ident records the min SDK as a little-endian word followed by the NDK's
    # release name and build number as NUL-padded strings, e.g. `1a 00 00 00  r30  16248370`.
    # readelf prints it as raw hex, so the strings are recovered from that rather than grepped
    # for directly -- min SDK 26 is the byte 0x1a and never appears as the text "26".
    local hex ascii
    hex="$("$readelf" --notes "$lib" 2>/dev/null | sed -n 's/.*description data: *//p' | head -1)"
    [ -n "$hex" ] || fail "$abi: no .note.android.ident -- not an Android library?"

    # bash's own printf, not /usr/bin/printf through xargs: BSD printf(1) on macOS only knows
    # octal escapes, so \xHH came back literally and every ABI failed the NDK check below.
    local escaped
    escaped="$(printf '%s' "$hex" | tr -d ' ' | sed 's/../\\x&/g')"
    ascii="$(printf '%b' "$escaped" | tr -c '[:print:]' ' ')"

    # The build number is everything after the last dot of the pinned revision.
    local ndk_build_number="${NDK_VERSION##*.}"
    printf '%s' "$ascii" | grep -q "$ndk_build_number" \
        || fail "$abi: built by the wrong NDK -- .note.android.ident has no build number $ndk_build_number (got: $ascii)"

    # First word, little-endian, is the min SDK the library targets.
    local min_sdk_hex min_sdk
    min_sdk_hex="$(printf '%s' "$hex" | awk '{printf "%s%s%s%s", $4, $3, $2, $1}')"
    min_sdk=$((16#$min_sdk_hex))
    [ "$min_sdk" = "$MIN_SDK_VERSION" ] \
        || fail "$abi: targets minSdk $min_sdk, expected $MIN_SDK_VERSION"

    local size
    size="$(wc -c < "$lib" | tr -d '[:space:]')"
    ok "$abi  minSdk $min_sdk  NDK $ndk_build_number  ${size} bytes"
}

# ---------------------------------------------------------------------------

main() {
    ANDROID_NDK_HOME="$(find_ndk)" || exit 1
    export ANDROID_NDK_HOME
    if [ "$PRINT_NDK" = true ]; then
        echo "$ANDROID_NDK_HOME"
        exit 0
    fi

    command -v cmake >/dev/null || fail "cmake not found"
    command -v ninja >/dev/null || fail "ninja not found"
    command -v git   >/dev/null || fail "git not found"

    ok "NDK $NDK_VERSION at $ANDROID_NDK_HOME"

    mkdir -p "$BUILD_ROOT"
    fetch_source

    # shellcheck source=repro-env.sh
    . "$SCRIPT_DIR/repro-env.sh"
    ok "SOURCE_DATE_EPOCH=${SOURCE_DATE_EPOCH:-unset}"

    for abi in "${ABIS[@]}"; do
        build_abi "$abi"
    done

    info "Done. Libraries in $OUTPUT_DIR"
}

main "$@"
