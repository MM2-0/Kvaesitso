#!/usr/bin/env bash
# Patch BlissOS / android-x86 ISO boot config for SUW + DATA on virtio + ENCRYPT.
# Rebuilds a new ISO next to the original when xorriso is available; otherwise
# writes a sidecar grub snippet and extracts kernel/initrd for direct -kernel boot.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
ISO="${1:-$ROOT/images/blissos-x86_64.iso}"
WORK="$ROOT/images/iso-work"
OVERLAY="$ROOT/overlay"
mkdir -p "$WORK"

if [[ ! -f "$ISO" ]]; then
  echo "ISO not found: $ISO" >&2
  exit 1
fi

echo "Extracting boot files from $ISO"
7z x -y -o"$WORK" "$ISO" "boot/grub/grub.cfg" "isolinux/isolinux.cfg" "isolinux/android.cfg" "kernel" "initrd.img" 2>/dev/null || \
  7z x -y -o"$WORK" "$ISO" 2>/dev/null | tail -5

append_opts="SETUPWIZARD=1 DATA=vda ENCRYPT=1 SRC= androidboot.selinux=permissive"

patch_file() {
  local f="$1"
  [[ -f "$f" ]] || return 0
  python3 - "$f" "$append_opts" <<'PY'
import sys
path, extra = sys.argv[1], sys.argv[2]
text = open(path, encoding="utf-8", errors="replace").read()
for token in extra.split():
    key = token.split("=", 1)[0]
    # drop disabled wizard
    text = text.replace("SETUPWIZARD=0", "SETUPWIZARD=1")
    text = text.replace("SETUPWIZARD=DISABLED", "SETUPWIZARD=1")
if extra not in text:
    # append to linux / kernel lines
    out = []
    for line in text.splitlines(True):
        stripped = line.strip()
        if stripped.startswith("linux") or stripped.startswith("kernel") or "/kernel" in stripped and stripped.startswith("append"):
            if extra not in line:
                line = line.rstrip("\n") + " " + extra + "\n"
        out.append(line)
    text = "".join(out)
open(path, "w", encoding="utf-8").write(text)
print("patched", path)
PY
}

find "$WORK" -iname 'grub.cfg' -o -iname 'isolinux.cfg' -o -iname 'android.cfg' | while read -r cfg; do
  patch_file "$cfg"
done

# Repack initrd with Telos crypto overlay if cpio/gzip exist.
INITRD="$(find "$WORK" -name 'initrd.img' | head -1 || true)"
if [[ -n "$INITRD" ]] && command -v gzip >/dev/null && command -v cpio >/dev/null; then
  TMP="$WORK/initrd-unpack"
  rm -rf "$TMP"
  mkdir -p "$TMP"
  if gzip -t "$INITRD" 2>/dev/null; then
    gzip -dc "$INITRD" | (cd "$TMP" && cpio -idm --no-absolute-filenames 2>/dev/null || true)
  else
    (cd "$TMP" && cpio -idm --no-absolute-filenames < "$INITRD" 2>/dev/null || true)
  fi
  mkdir -p "$TMP/telos-overlay"
  cp -a "$OVERLAY/." "$TMP/telos-overlay/"
  mkdir -p "$TMP/ramdisk"
  echo "import /telos-overlay/init.telos-crypto.rc" >> "$TMP/init.rc" 2>/dev/null || true
  cat "$OVERLAY/telos-crypto.prop" >> "$TMP/default.prop" 2>/dev/null || true
  (cd "$TMP" && find . | cpio -o -H newc 2>/dev/null | gzip -9 > "$ROOT/images/initrd-telos.img")
  echo "Wrote $ROOT/images/initrd-telos.img"
  KERNEL="$(find "$WORK" -name kernel | head -1 || true)"
  if [[ -n "$KERNEL" ]]; then
    cp -a "$KERNEL" "$ROOT/images/kernel"
    echo "Wrote $ROOT/images/kernel (use with -kernel / -initrd on QEMU if desired)"
  fi
else
  echo "gzip/cpio not available or no initrd; boot files left in $WORK"
fi

if command -v xorriso >/dev/null; then
  OUT="$ROOT/images/blissos-x86_64-telos.iso"
  xorriso -as mkisofs -o "$OUT" -b isolinux/isolinux.bin -c isolinux/boot.cat \
    -no-emul-boot -boot-load-size 4 -boot-info-table "$WORK" || \
    echo "xorriso rebuild skipped; original ISO still used with DATA=vda via live boot"
else
  echo "xorriso not installed; C# runner boots the original ISO plus 16G QCOW2 (DATA=vda)."
fi

echo "Patch pass finished."
