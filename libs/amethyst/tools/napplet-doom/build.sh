#!/usr/bin/env bash
# Builds the Freedoom napplet: doomgeneric compiled to wasm32-wasi with wasi-sdk, plus the
# Freedoom Phase 1 IWAD, packed into one self-contained index.html.
#
#   tools/napplet-doom/build.sh            # -> tools/napplet-doom/build/index.html
#   IWAD=~/doom1.wad TITLE=Doom tools/napplet-doom/build.sh   # another IWAD you may redistribute
#
# Every input is pinned (a git commit or a sha256), so the same inputs give the same artifact
# hash, and anyone can rebuild a published napplet and compare its `x` tag.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$HERE/build}"
OUT="${OUT:-$WORK/index.html}"

DOOMGENERIC_REPO=https://github.com/ozkl/doomgeneric
DOOMGENERIC_COMMIT=e257a09527062bfb986b6e5e0780839b6cbd5b71

FREEDOOM_VERSION=0.13.0
FREEDOOM_SHA256=3f9b264f3e3ce503b4fb7f6bdcb1f419d93c7b546f4df3e874dd878db9688f59

WASI_SDK_VERSION=25
case "$(uname -s)-$(uname -m)" in
  Linux-x86_64) WASI_SDK_HOST=x86_64-linux WASI_SDK_SHA256=52640dde13599bf127a95499e61d6d640256119456d1af8897ab6725bcf3d89c ;;
  Linux-aarch64 | Linux-arm64) WASI_SDK_HOST=arm64-linux WASI_SDK_SHA256=47fccad8b2498f2239e05e1115c3ffc652bf37e7de2f88fb64b2d663c976ce2d ;;
  Darwin-x86_64) WASI_SDK_HOST=x86_64-macos WASI_SDK_SHA256=55e3ff3fee1a15678a16eeccba0129276c9f6be481bc9c283e7f9f65bf055c11 ;;
  Darwin-arm64) WASI_SDK_HOST=arm64-macos WASI_SDK_SHA256=e1e529ea226b1db0b430327809deae9246b580fa3cae32d31c82dfe770233587 ;;
  *) echo "No pinned wasi-sdk for $(uname -s)-$(uname -m)" >&2; exit 1 ;;
esac

sha256() {
  if command -v sha256sum > /dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

# fetch URL FILE SHA256: download once, then always verify.
fetch() {
  if [ ! -f "$2" ]; then
    echo "Downloading $1"
    curl -fsSL -o "$2.part" "$1"
    mv "$2.part" "$2"
  fi
  local actual
  actual="$(sha256 "$2")"
  if [ "$actual" != "$3" ]; then
    echo "sha256 mismatch for $2: expected $3, got $actual" >&2
    exit 1
  fi
}

mkdir -p "$WORK"
cd "$WORK"

# ---- Toolchain -------------------------------------------------------------------------------
SDK_DIR="wasi-sdk-$WASI_SDK_VERSION.0-$WASI_SDK_HOST"
fetch "https://github.com/WebAssembly/wasi-sdk/releases/download/wasi-sdk-$WASI_SDK_VERSION/$SDK_DIR.tar.gz" \
  "$SDK_DIR.tar.gz" "$WASI_SDK_SHA256"
[ -d "$SDK_DIR" ] || tar xzf "$SDK_DIR.tar.gz"
CC="$WORK/$SDK_DIR/bin/clang"
SYSROOT="$WORK/$SDK_DIR/share/wasi-sysroot"

# ---- IWAD ------------------------------------------------------------------------------------
# Freedoom by default. IWAD=path swaps in another one; whether you may publish it is up to you.
if [ -n "${IWAD:-}" ]; then
  WAD="$IWAD"
  TITLE="${TITLE:-Doom}"
else
  fetch "https://github.com/freedoom/freedoom/releases/download/v$FREEDOOM_VERSION/freedoom-$FREEDOOM_VERSION.zip" \
    "freedoom-$FREEDOOM_VERSION.zip" "$FREEDOOM_SHA256"
  unzip -q -o "freedoom-$FREEDOOM_VERSION.zip" "freedoom-$FREEDOOM_VERSION/freedoom1.wad" "freedoom-$FREEDOOM_VERSION/COPYING.txt"
  WAD="$WORK/freedoom-$FREEDOOM_VERSION/freedoom1.wad"
  TITLE="${TITLE:-Freedoom}"
fi

# ---- Engine ----------------------------------------------------------------------------------
if [ ! -d doomgeneric/.git ]; then
  git init -q doomgeneric
  git -C doomgeneric remote add origin "$DOOMGENERIC_REPO"
fi
git -C doomgeneric fetch -q --depth 1 origin "$DOOMGENERIC_COMMIT"
git -C doomgeneric checkout -q --force FETCH_HEAD
git -C doomgeneric clean -q -fdx
git -C doomgeneric apply "$HERE/src/doomgeneric.patch"

SRC="$WORK/doomgeneric/doomgeneric"
# The engine sources of doomgeneric's Emscripten build, minus its SDL front end and SDL sound.
ENGINE="dummy am_map doomdef doomstat dstrings d_event d_items d_iwad d_loop d_main d_mode d_net
  f_finale f_wipe g_game hu_lib hu_stuff info i_cdmus i_endoom i_joystick i_scale i_sound i_system
  i_timer memio m_argv m_bbox m_cheat m_config m_controls m_fixed m_menu m_misc m_random p_ceilng
  p_doors p_enemy p_floor p_inter p_lights p_map p_maputl p_mobj p_plats p_pspr p_saveg p_setup
  p_sight p_spec p_switch p_telept p_tick p_user r_bsp r_data r_draw r_main r_plane r_segs r_sky
  r_things sha1 sounds statdump st_lib st_stuff s_sound tables v_video wi_stuff w_checksum w_file
  w_main w_wad z_zone w_file_stdc i_input i_video doomgeneric mus2mid"

# i_sound.c includes SDL_mixer.h under FEATURE_SOUND without using it.
mkdir -p stub obj
: > stub/SDL_mixer.h

CFLAGS=(--target=wasm32-wasip1 "--sysroot=$SYSROOT" -O2 -w
  -DFEATURE_SOUND -DDOOMGENERIC_RESX=320 -DDOOMGENERIC_RESY=200
  -D_WASI_EMULATED_SIGNAL -D_WASI_EMULATED_PROCESS_CLOCKS
  -I"$WORK/stub" -I"$SRC")

rm -f obj/*.o
echo "Compiling doomgeneric ($DOOMGENERIC_COMMIT)"
for name in $ENGINE; do
  "$CC" "${CFLAGS[@]}" -c "$SRC/$name.c" -o "obj/$name.o"
done
"$CC" "${CFLAGS[@]}" -Wall -c "$HERE/src/doomgeneric_napplet.c" -o obj/doomgeneric_napplet.o

# A reactor: the page calls _initialize, then dg_start and dg_tick, instead of a blocking main().
"$CC" --target=wasm32-wasip1 "--sysroot=$SYSROOT" -O2 -mexec-model=reactor \
  -lwasi-emulated-signal -lwasi-emulated-process-clocks \
  obj/*.o -o doom.wasm

python3 "$HERE/assemble.py" "$HERE/src/index.template.html" doom.wasm "$WAD" "$OUT" "$TITLE"
