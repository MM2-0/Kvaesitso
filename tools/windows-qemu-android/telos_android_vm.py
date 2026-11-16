#!/usr/bin/env python3
"""TelosAndroidVm for Linux (KVM) and macOS Apple Silicon (TCG x86_64 guest).

Intel macOS is unsupported. Windows uses TelosAndroidVm.exe.
BlissOS stays x86_64; on Apple Silicon QEMU uses TCG (HVF cannot run a foreign arch).
"""
from __future__ import annotations

import argparse
import os
import shutil
import socket
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ADB_PORT = 5555
RAM_MB = 1024
SMP = 2


def which(name: str) -> str | None:
    return shutil.which(name)


def require(name: str, extra: str) -> str:
    p = which(name)
    if not p:
        raise FileNotFoundError(f"{name} not on PATH. {extra}")
    return p


def host_kind() -> str:
    if sys.platform.startswith("linux"):
        return "linux"
    if sys.platform == "darwin":
        machine = os.uname().machine.lower()
        if machine in ("arm64", "aarch64"):
            return "darwin-arm64"
        raise SystemExit(
            f"macOS {machine} is unsupported. TelosAndroidVm supports Apple Silicon only."
        )
    raise SystemExit(f"Use TelosAndroidVm.exe on Windows; this runner is Linux/macOS ARM ({sys.platform})")


def kvm_ok() -> bool:
    return os.path.exists("/dev/kvm") and os.access("/dev/kvm", os.R_OK | os.W_OK)


def accel_cpu(kind: str) -> tuple[str, str]:
    if kind == "linux" and kvm_ok():
        return "kvm", "host"
    if kind == "linux":
        return "tcg,thread=multi", "qemu64"
    # darwin-arm64 running x86_64 BlissOS: TCG only
    return "tcg,thread=multi", "max"


def wait_port(host: str, port: int, proc: subprocess.Popen, timeout: float) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if proc.poll() is not None:
            raise RuntimeError(f"QEMU exited before ADB port opened ({proc.returncode})")
        try:
            with socket.create_connection((host, port), timeout=0.4):
                return
        except OSError:
            time.sleep(0.4)
    raise TimeoutError(f"{host}:{port} did not open")


def adb(adb_bin: str, *args: str) -> str:
    r = subprocess.run([adb_bin, *args], capture_output=True, text=True, timeout=120)
    return r.stdout


def wait_boot(adb_bin: str, proc: subprocess.Popen) -> None:
    deadline = time.time() + 8 * 60
    serial = f"127.0.0.1:{ADB_PORT}"
    while time.time() < deadline:
        if proc.poll() is not None:
            raise RuntimeError("QEMU exited during Android boot")
        adb(adb_bin, "connect", serial)
        out = adb(adb_bin, "-s", serial, "shell", "getprop", "sys.boot_completed").strip()
        if out == "1":
            return
        time.sleep(2)
    raise TimeoutError("sys.boot_completed was not set in time")


def qemu_args(
    qemu: str,
    disk: Path,
    iso: Path | None,
    kernel: Path,
    initrd: Path,
    accel: str,
    cpu: str,
) -> list[str]:
    cmd = [
        qemu,
        "-accel", accel,
        "-machine", "q35",
        "-cpu", cpu,
        "-smp", str(SMP),
        "-m", str(RAM_MB),
        "-display", "none",
        "-vga", "virtio",
        "-usb",
        "-device", "usb-tablet",
        "-device", "virtio-keyboard-pci",
        "-drive", f"file={disk},if=virtio,format=qcow2,discard=unmap,detect-zeroes=unmap",
        "-netdev", f"user,id=net0,hostfwd=tcp:127.0.0.1:{ADB_PORT}-:5555",
        "-device", "virtio-net-pci,netdev=net0",
        "-device", "virtio-serial-pci",
        "-monitor", "none",
        "-serial", "null",
        "-no-reboot",
    ]
    if kernel.is_file() and initrd.is_file() and iso and iso.is_file():
        cmd += [
            "-kernel", str(kernel),
            "-initrd", str(initrd),
            "-append", "root=/dev/ram0 SRC=/ DATA=vda SETUPWIZARD=1 ENCRYPT=1 androidboot.selinux=permissive",
            "-drive", f"file={iso},if=ide,media=cdrom,readonly=on",
        ]
    else:
        if iso and iso.is_file():
            cmd += ["-drive", f"file={iso},media=cdrom,readonly=on"]
        cmd += ["-boot", "order=dc"]
    return cmd


def maybe_update(root: Path, do_update: bool, assume_yes: bool) -> None:
    if not do_update:
        return
    from blissos_catalog import run_cli

    argv = ["--root", str(root), "--update"]
    if assume_yes:
        argv.append("--yes")
    rc = run_cli(argv)
    if rc not in (0, 2):
        raise SystemExit(rc)


def run(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--update-bliss", action="store_true")
    parser.add_argument("--yes", action="store_true")
    parser.add_argument("--check-bliss", action="store_true")
    args = parser.parse_args(argv)

    kind = host_kind()
    root = args.root.resolve()
    sys.path.insert(0, str(root))

    if args.check_bliss:
        from blissos_catalog import run_cli
        return run_cli(["--root", str(root), "--check"])

    maybe_update(root, args.update_bliss, args.yes)

    qemu_hint = (
        "Install qemu-system-x86_64 (Linux: distro qemu-desktop/qemu-system-x86; "
        "macOS ARM: brew install qemu)."
    )
    scrcpy_hint = (
        "Install scrcpy (Linux: distro package; macOS ARM: brew install scrcpy)."
    )
    adb_hint = "Install android-tools / android-platform-tools (adb)."
    qemu = require("qemu-system-x86_64", qemu_hint)
    scrcpy = require("scrcpy", scrcpy_hint)
    adb_bin = require("adb", adb_hint)

    disk = root / "images" / "android-data.qcow2"
    iso = root / "images" / "blissos-x86_64.iso"
    kernel = root / "images" / "kernel"
    initrd = root / "images" / "initrd-telos.img"
    if not disk.is_file():
        raise FileNotFoundError(f"{disk} missing. Run ./prepare-qcow2.sh")
    if not iso.is_file():
        raise FileNotFoundError(f"{iso} missing. Run ./fetch-toolchain.sh then optional --update-bliss")

    accel, cpu = accel_cpu(kind)
    print(f"host={kind} accel={accel} cpu={cpu} qemu={qemu}", flush=True)
    cmd = qemu_args(qemu, disk, iso if iso.is_file() else None, kernel, initrd, accel, cpu)
    qemu_proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    scrcpy_proc = None
    try:
        wait_port("127.0.0.1", ADB_PORT, qemu_proc, 120)
        adb(adb_bin, "start-server")
        adb(adb_bin, "connect", f"127.0.0.1:{ADB_PORT}")
        wait_boot(adb_bin, qemu_proc)
        hook = root / "scripts" / "first-boot-lock.sh"
        if hook.is_file():
            adb(adb_bin, "-s", f"127.0.0.1:{ADB_PORT}", "push", str(hook), "/data/local/tmp/first-boot-lock.sh")
            adb(adb_bin, "-s", f"127.0.0.1:{ADB_PORT}", "shell", "sh", "/data/local/tmp/first-boot-lock.sh")
        apk = os.environ.get("TELOS_APK")
        if apk and Path(apk).is_file():
            adb(adb_bin, "-s", f"127.0.0.1:{ADB_PORT}", "install", "-r", apk)
        scrcpy_proc = subprocess.Popen(
            [
                scrcpy,
                "-s", f"127.0.0.1:{ADB_PORT}",
                "--window-borderless",
                "--window-title", "Telos Android",
                "--stay-awake",
                "--push-target", "/sdcard/Download",
            ]
        )
        scrcpy_proc.wait()
    finally:
        try:
            adb(adb_bin, "-s", f"127.0.0.1:{ADB_PORT}", "shell", "reboot", "-p")
        except Exception:
            pass
        if scrcpy_proc and scrcpy_proc.poll() is None:
            scrcpy_proc.terminate()
        deadline = time.time() + 45
        while qemu_proc.poll() is None and time.time() < deadline:
            time.sleep(0.4)
        if qemu_proc.poll() is None:
            qemu_proc.kill()
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(run())
    except KeyboardInterrupt:
        raise SystemExit(130)
