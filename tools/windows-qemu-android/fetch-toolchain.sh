#!/usr/bin/env bash
# Windows: QEMU + scrcpy zips. Linux/macOS ARM: BlissOS ISO only (use distro/brew qemu+scrcpy+adb).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
VENDOR="$ROOT/vendor"
IMAGES="$ROOT/images"
mkdir -p "$VENDOR/qemu" "$VENDOR/scrcpy" "$IMAGES" "$VENDOR/dl"

QEMU_URL="${QEMU_URL:-https://qemu.weilnetz.de/w64/2026/qemu-w64-setup-20260811.exe}"
SCRCPY_URL="${SCRCPY_URL:-https://github.com/Genymobile/scrcpy/releases/download/v5.0.1/scrcpy-win64-v5.0.1.zip}"
if [[ -z "${ISO_URL:-}" && -f "$ROOT/blissos.lock.json" ]]; then
  ISO_URL="$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['url'])" "$ROOT/blissos.lock.json")"
fi
ISO_URL="${ISO_URL:-https://downloads.sourceforge.net/project/blissos-x86/Official/BlissOS16/FOSS/Generic/Bliss-v16.9.7-x86_64-OFFICIAL-foss-20241011.iso}"
HOST="$(uname -s)"

download() {
  local url="$1" dest="$2"
  if [[ -f "$dest" ]]; then
    echo "already have $dest"
    return 0
  fi
  echo "GET $url"
  curl -L --fail --retry 3 --retry-delay 2 -o "$dest.partial" "$url"
  mv "$dest.partial" "$dest"
}

if [[ "$HOST" == MINGW* || "$HOST" == MSYS* || "$HOST" == CYGWIN* || "$HOST" == Windows_NT ]]; then
  download "$QEMU_URL" "$VENDOR/dl/qemu-w64-setup.exe"
  download "$SCRCPY_URL" "$VENDOR/dl/scrcpy-win64.zip"
elif [[ "$HOST" == Linux ]]; then
  echo "Linux: install qemu-system-x86_64 scrcpy adb (e.g. pacman -S qemu-desktop scrcpy android-tools)"
elif [[ "$HOST" == Darwin ]]; then
  echo "macOS Apple Silicon: brew install qemu scrcpy android-platform-tools"
  echo "BlissOS is x86_64; QEMU uses TCG (no HVF for this guest)."
fi

if [[ -f "$VENDOR/dl/scrcpy-win64.zip" && ! -f "$VENDOR/scrcpy/scrcpy.exe" ]]; then
  unzip -qo "$VENDOR/dl/scrcpy-win64.zip" -d "$VENDOR/dl/scrcpy-unpack"
  inner="$(find "$VENDOR/dl/scrcpy-unpack" -name scrcpy.exe | head -1)"
  cp -a "$(dirname "$inner")/." "$VENDOR/scrcpy/"
fi

if [[ -f "$VENDOR/dl/qemu-w64-setup.exe" && ! -f "$VENDOR/qemu/qemu-system-x86_64.exe" ]]; then
  echo "Extracting QEMU NSIS installer with 7z (Windows binaries only)…"
  7z x -y -o"$VENDOR/dl/qemu-unpack" "$VENDOR/dl/qemu-w64-setup.exe" >/dev/null
  exe="$(find "$VENDOR/dl/qemu-unpack" -name qemu-system-x86_64.exe | head -1)"
  if [[ -z "$exe" ]]; then
    echo "QEMU extractor layout unexpected; copying whole tree" >&2
    cp -a "$VENDOR/dl/qemu-unpack/." "$VENDOR/qemu/"
  else
    cp -a "$(dirname "$exe")/." "$VENDOR/qemu/"
  fi
fi

ISO_DEST="$IMAGES/blissos-x86_64.iso"
if [[ ! -f "$ISO_DEST" ]]; then
  echo "GET BlissOS ISO (~2.3 GiB) $ISO_URL"
  curl -L --retry 3 --retry-delay 2 -C - -o "$ISO_DEST.partial" "$ISO_URL"
  mv "$ISO_DEST.partial" "$ISO_DEST"
fi

echo "ISO: $ISO_DEST ($(du -h "$ISO_DEST" | awk '{print $1}'))"
echo "Linux/macOS: python3 telos_android_vm.py"
echo "Windows: TelosAndroidVm.exe"
echo "Opt-in BlissOS ISO update (keeps qcow2): python3 blissos_catalog.py --update"
