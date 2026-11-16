#!/usr/bin/env bash
# Create a growable 16 GiB QCOW2 data/system disk (sparse).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
IMAGES="$ROOT/images"
mkdir -p "$IMAGES"
DISK="$IMAGES/android-data.qcow2"
SIZE="${QCOW2_SIZE:-16G}"

QEMU_IMG="$(command -v qemu-img || true)"
if [[ -z "$QEMU_IMG" ]]; then
  WIN_IMG="$(find "$ROOT/vendor/qemu" -name qemu-img.exe 2>/dev/null | head -1 || true)"
  if [[ -n "$WIN_IMG" ]]; then
    echo "qemu-img.exe is a Windows binary; using Python QCOW2 creator on Linux."
    QEMU_IMG=""
  fi
fi

if [[ -n "$QEMU_IMG" ]]; then
  if [[ -f "$DISK" ]]; then
    echo "already have $DISK"
  else
    "$QEMU_IMG" create -f qcow2 -o cluster_size=65536,preallocation=off,lazy_refcounts=on "$DISK" "$SIZE"
  fi
  "$QEMU_IMG" info "$DISK"
  exit 0
fi

python3 - <<'PY' "$DISK" "$SIZE"
import os, struct, sys
path, size_s = sys.argv[1], sys.argv[2]
if os.path.exists(path):
    print("already have", path)
    sys.exit(0)
s = size_s.strip().upper()
if s.endswith("G"):
    nbytes = int(s[:-1]) << 30
elif s.endswith("M"):
    nbytes = int(s[:-1]) << 20
else:
    nbytes = int(s)
cluster_bits = 16
cluster = 1 << cluster_bits
l2_span = (cluster // 8) * cluster
l1_size = (nbytes + l2_span - 1) // l2_span
rt_off, rb_off, l1_off = cluster, 2 * cluster, 3 * cluster
header = bytearray(cluster)
struct.pack_into(">4sI", header, 0, b"QFI\xfb", 3)
struct.pack_into(">I", header, 20, cluster_bits)
struct.pack_into(">Q", header, 24, nbytes)
struct.pack_into(">I", header, 36, l1_size)
struct.pack_into(">Q", header, 40, l1_off)
struct.pack_into(">Q", header, 48, rt_off)
struct.pack_into(">I", header, 56, 1)
struct.pack_into(">I", header, 96, 4)
struct.pack_into(">I", header, 100, 104)
rt = bytearray(cluster)
struct.pack_into(">Q", rt, 0, rb_off)
rb = bytearray(cluster)
for i in range(4):
    struct.pack_into(">H", rb, i * 2, 1)
l1 = bytearray(cluster)
with open(path, "wb") as f:
    f.write(header)
    f.write(rt)
    f.write(rb)
    f.write(l1)
print("wrote qcow2 v3", path, "virtual", nbytes, "file", os.path.getsize(path))
PY
