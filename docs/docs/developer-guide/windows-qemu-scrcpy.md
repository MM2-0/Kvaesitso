---
sidebar_position: 9
---

# Portable Windows QEMU + scrcpy runner

`tools/windows-qemu-android/` is a **portable Windows bundle** that boots an android-x86 (BlissOS FOSS) guest in **headless QEMU** and shows it with **scrcpy**. The C# wrapper `TelosAndroidVm.exe` starts QEMU with `CreateNoWindow`, waits for `sys.boot_completed` on `127.0.0.1:5555`, then scrcpy (`--window-borderless --stay-awake`). Closing scrcpy runs `adb shell reboot -p` and stops QEMU.

It is separate from [`:desktop-linux`](./desktop-linux) (Compose Desktop). `:app:app` must not depend on this tree. Large vendor ISOs and Windows binaries are gitignored.

## Resources

- RAM 1 GiB, 2 SMP cores.
- Acceleration: WHPX (`kernel-irqchip=off`), then HAXM, then TCG.
- Disk: growable QCOW2, virtual size 16 GiB (`images/android-data.qcow2`).
- ADB: user-mode NIC `hostfwd=tcp:127.0.0.1:5555-:5555`.

## First boot (SUW + encryption)

`patch-android-boot.sh` adds `SETUPWIZARD=1 DATA=vda ENCRYPT=1` to ISO boot configs. Overlay props request FBE (`ro.crypto.type=file`). After boot, `scripts/first-boot-lock.sh` keeps the Setup Wizard enabled and refuses a disabled lockscreen. The user sets PIN/pattern/password; that credential is the CE unwrap key on later boots. The host does not set or store the PIN.

FBE on android-x86 is best-effort: it needs a fstab/`fileencryption` line the guest honors. If the build only supports installer FDE, `ENCRYPT=1` still encrypts userdata.

## Build the wrapper

Needs the .NET 8 SDK (cross-publish from Linux is supported):

```bash
cd tools/windows-qemu-android
./fetch-toolchain.sh
./prepare-qcow2.sh
dotnet publish src/TelosAndroidVm/TelosAndroidVm.csproj -c Release -r win-x64 --self-contained true -o .
```

Copy `vendor/`, `images/`, `scripts/`, and `TelosAndroidVm.exe` to a Windows machine. Enable **Windows Hypervisor Platform** for WHPX.
