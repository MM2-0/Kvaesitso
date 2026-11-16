#!/usr/bin/env python3
"""Discover official BlissOS FOSS Generic x86_64 ISOs and apply opt-in updates.

The encrypted guest disk (images/android-data.qcow2) is never deleted or formatted.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import ssl
import sys
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

ISO_NAME = re.compile(
    r"^(Bliss-v(\d+\.\d+\.\d+)-x86_64-OFFICIAL-foss-(\d{8}))\.iso$"
)
CHANNELS = (
    "Official/BlissOS16/FOSS/Generic",
    "Official/BlissOS17/FOSS/Generic",
    "Official/BlissOS18/FOSS/Generic",
)
RSS = "https://sourceforge.net/projects/blissos-x86/rss?path=/{channel}&limit=100"
DL = "https://downloads.sourceforge.net/project/blissos-x86/{channel}/{filename}"
UA = "TelosAndroidVm-blissos-catalog/1.0 (+https://github.com/koukomin/Telos-Launcher)"


@dataclass(frozen=True, order=True)
class Release:
    version_tuple: tuple[int, ...]
    date: str
    version: str
    channel: str
    filename: str
    id: str

    @property
    def url(self) -> str:
        return DL.format(channel=self.channel, filename=self.filename)

    @property
    def sha256_url(self) -> str:
        return self.url + ".sha256"

    def sort_key(self) -> tuple:
        return (self.version_tuple, self.date, self.channel)

    def to_lock(self) -> dict:
        return {
            "channel": self.channel,
            "id": self.id,
            "version": self.version,
            "date": self.date,
            "filename": self.filename,
            "url": self.url,
            "sha256_url": self.sha256_url,
            "keep_data_disk": True,
        }


def parse_filename(name: str, channel: str) -> Release | None:
    m = ISO_NAME.match(name)
    if not m:
        return None
    ident, version, date = m.group(1), m.group(2), m.group(3)
    nums = tuple(int(p) for p in version.split("."))
    return Release(nums, date, version, channel, name, ident)


def parse_rss(xml_text: str, channel: str) -> list[Release]:
    root = ET.fromstring(xml_text)
    found: list[Release] = []
    for item in root.findall(".//item"):
        title = (item.findtext("title") or "").strip()
        name = Path(title.replace("\\", "/")).name
        rel = parse_filename(name, channel)
        if rel:
            found.append(rel)
    return found


def _opener() -> urllib.request.OpenerDirector:
    ctx = ssl.create_default_context()
    return urllib.request.build_opener(urllib.request.HTTPSHandler(context=ctx))


def fetch_text(url: str, timeout: int = 45) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with _opener().open(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8", errors="replace")


def discover(channels: Iterable[str] = CHANNELS) -> list[Release]:
    all_rel: list[Release] = []
    for ch in channels:
        try:
            xml_text = fetch_text(RSS.format(channel=ch))
        except OSError:
            continue
        all_rel.extend(parse_rss(xml_text, ch))
    uniq = {r.id: r for r in all_rel}
    return sorted(uniq.values(), key=lambda r: r.sort_key(), reverse=True)


def load_lock(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def lock_release(lock: dict) -> Release | None:
    return parse_filename(lock.get("filename", ""), lock.get("channel", CHANNELS[0]))


def major(ver: str) -> int:
    return int(ver.split(".", 1)[0])


def parse_sha256_file(text: str) -> str:
    token = text.strip().split()[0]
    token = token.lower()
    if len(token) != 64 or any(c not in "0123456789abcdef" for c in token):
        raise ValueError("invalid sha256 sidecar")
    return token


def sha256_file(path: Path, chunk: int = 1024 * 1024) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while True:
            b = f.read(chunk)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


def download(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".partial")
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with _opener().open(req, timeout=120) as resp, tmp.open("wb") as out:
        while True:
            b = resp.read(1024 * 1024)
            if not b:
                break
            out.write(b)
    tmp.replace(dest)


def apply_iso_update(root: Path, release: Release, sha256: str) -> Path:
    images = root / "images"
    images.mkdir(parents=True, exist_ok=True)
    iso = images / "blissos-x86_64.iso"
    data = images / "android-data.qcow2"
    new_iso = images / release.filename
    download(release.url, new_iso)
    got = sha256_file(new_iso)
    if got != sha256:
        new_iso.unlink(missing_ok=True)
        raise ValueError(f"SHA-256 mismatch: {got} != {sha256}")
    if iso.exists():
        bak = images / "blissos-x86_64.iso.bak"
        iso.replace(bak)
    new_iso.replace(iso)
    if not data.exists():
        print("note: android-data.qcow2 missing; encrypted data was not present", file=sys.stderr)
    else:
        print(f"kept encrypted data disk {data}", file=sys.stderr)
    return iso


def write_lock(path: Path, release: Release) -> None:
    path.write_text(json.dumps(release.to_lock(), indent=2) + "\n", encoding="utf-8")


def confirm(prompt: str, assume_yes: bool) -> bool:
    if assume_yes:
        return True
    if not sys.stdin.isatty():
        print("refusing update without a TTY; pass --yes", file=sys.stderr)
        return False
    ans = input(prompt + " [y/N] ").strip().lower()
    return ans in ("y", "yes", "ναι", "Ναι")


def run_cli(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument("--check", action="store_true", help="print latest vs lock and exit 0")
    parser.add_argument("--update", action="store_true", help="opt-in ISO update; keeps qcow2")
    parser.add_argument("--yes", action="store_true", help="accept the prompt")
    parser.add_argument("--offline-rss", type=Path, help="test fixture RSS XML")
    args = parser.parse_args(argv)
    root = args.root
    lock_path = root / "blissos.lock.json"
    lock = load_lock(lock_path)
    pinned = lock_release(lock)

    if args.offline_rss:
        releases = parse_rss(args.offline_rss.read_text(encoding="utf-8"), lock.get("channel", CHANNELS[0]))
        releases.sort(key=lambda r: r.sort_key(), reverse=True)
    else:
        releases = discover()

    latest = releases[0] if releases else pinned
    print(f"pinned: {lock.get('id')}")
    print(f"latest_official: {latest.id if latest else 'unknown'}")

    if args.check and not args.update:
        return 0
    if not args.update:
        return 0
    if latest is None or pinned is None:
        print("no official catalog", file=sys.stderr)
        return 1
    if latest.id == pinned.id:
        print("already on latest official FOSS Generic ISO")
        return 0
    if major(latest.version) != major(pinned.version):
        extra = (
            f"BlissOS {pinned.version} → {latest.version} is a major line change. "
            "Encrypted /data may not unlock. The qcow2 still will not be deleted. Continue?"
        )
        if not confirm(extra, args.yes):
            return 2
    msg = (
        f"Download {latest.filename} and replace the ISO? "
        "android-data.qcow2 (encrypted userdata) is kept."
    )
    if not confirm(msg, args.yes):
        print("skipped")
        return 0
    sha_text = fetch_text(latest.sha256_url)
    sha = parse_sha256_file(sha_text)
    apply_iso_update(root, latest, sha)
    write_lock(lock_path, latest)
    print("ISO updated; re-run patch-android-boot.sh to refresh kernel/initrd")
    return 0


if __name__ == "__main__":
    sys.exit(run_cli())
