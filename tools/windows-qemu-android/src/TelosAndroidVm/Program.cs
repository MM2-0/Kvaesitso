using System.Diagnostics;
using System.Net.Sockets;
using System.Text;

namespace Telos.AndroidVm;

/// <summary>
/// Invisible Windows wrapper: headless QEMU (WHPX → HAXM → TCG) + ADB + scrcpy.
/// Closing scrcpy powers off the guest, then QEMU is stopped.
/// </summary>
internal static class Program
{
    private const int AdbPort = 5555;
    private const int RamMb = 1024;
    private const int Smp = 2;
    private static readonly TimeSpan BootTimeout = TimeSpan.FromMinutes(8);
    private static readonly TimeSpan ShutdownTimeout = TimeSpan.FromSeconds(45);

    private static int Main(string[] args)
    {
        try
        {
            var root = ResolveBundleRoot(args);
            if (args.Any(a => a.Equals("--update-bliss", StringComparison.OrdinalIgnoreCase)))
            {
                var yes = args.Any(a => a.Equals("--yes", StringComparison.OrdinalIgnoreCase));
                return BlissosUpdate.Run(root, yes);
            }

            var qemu = FindQemu(root);
            var scrcpyDir = FindScrcpyDir(root);
            var adb = Path.Combine(scrcpyDir, "adb.exe");
            var scrcpy = Path.Combine(scrcpyDir, "scrcpy.exe");
            var disk = FindDisk(root);
            var iso = FindIso(root);
            var kernel = Path.Combine(root, "images", "kernel");
            var initrd = Path.Combine(root, "images", "initrd-telos.img");

            if (!File.Exists(qemu))
                throw new FileNotFoundException("qemu-system-x86_64.exe missing. Run fetch-toolchain.ps1", qemu);
            if (!File.Exists(adb) || !File.Exists(scrcpy))
                throw new FileNotFoundException("scrcpy/adb missing. Run fetch-toolchain.ps1", scrcpyDir);
            if (!File.Exists(disk))
                throw new FileNotFoundException("android-data.qcow2 missing. Run prepare-qcow2.ps1", disk);

            var accel = ProbeAccel(qemu);
            var qemuArgs = BuildQemuArgs(disk, iso, kernel, initrd, accel);

            using var qemuProc = StartHidden(qemu, qemuArgs, Path.GetDirectoryName(qemu)!);
            try
            {
                WaitForTcp("127.0.0.1", AdbPort, TimeSpan.FromMinutes(2), qemuProc);
                Adb(adb, "start-server");
                Adb(adb, $"connect 127.0.0.1:{AdbPort}");
                WaitBootCompleted(adb, qemuProc);
                Adb(adb, $"-s 127.0.0.1:{AdbPort} wait-for-device");
                RunOptionalFirstBootHooks(adb, root);
                SideloadTelosApkIfPresent(adb, root);

                using var scrcpyProc = StartHidden(
                    scrcpy,
                    $"-s 127.0.0.1:{AdbPort} --window-borderless --window-title \"Telos Android\" --stay-awake --push-target /sdcard/Download",
                    scrcpyDir,
                    createNoWindow: false);

                scrcpyProc.WaitForExit();
            }
            finally
            {
                GracefulShutdown(adb, qemuProc);
            }

            return 0;
        }
        catch (Exception ex)
        {
            try
            {
                File.WriteAllText(
                    Path.Combine(AppContext.BaseDirectory, "telos-android-vm.log"),
                    ex.ToString());
            }
            catch
            {
                /* ignore */
            }

            return 1;
        }
    }

    private static string ResolveBundleRoot(string[] args)
    {
        foreach (var a in args)
        {
            if (a.StartsWith("--root=", StringComparison.OrdinalIgnoreCase))
                return Path.GetFullPath(a["--root=".Length..]);
        }

        var baseDir = AppContext.BaseDirectory;
        foreach (var candidate in new[]
                 {
                     baseDir,
                     Path.GetFullPath(Path.Combine(baseDir, "..")),
                     Path.GetFullPath(Path.Combine(baseDir, "..", "..")),
                 })
        {
            if (Directory.Exists(Path.Combine(candidate, "vendor")) ||
                Directory.Exists(Path.Combine(candidate, "images")))
                return candidate;
        }

        return baseDir;
    }

    private static string FindQemu(string root)
    {
        var hits = Directory.GetFiles(Path.Combine(root, "vendor"), "qemu-system-x86_64.exe", SearchOption.AllDirectories);
        if (hits.Length > 0) return hits[0];
        return Path.Combine(root, "vendor", "qemu", "qemu-system-x86_64.exe");
    }

    private static string FindScrcpyDir(string root)
    {
        var hits = Directory.GetFiles(Path.Combine(root, "vendor"), "scrcpy.exe", SearchOption.AllDirectories);
        if (hits.Length > 0) return Path.GetDirectoryName(hits[0])!;
        return Path.Combine(root, "vendor", "scrcpy");
    }

    private static string FindDisk(string root) =>
        Path.Combine(root, "images", "android-data.qcow2");

    private static string? FindIso(string root)
    {
        var dir = Path.Combine(root, "images");
        var preferred = Path.Combine(dir, "blissos-x86_64.iso");
        if (File.Exists(preferred)) return preferred;
        if (!Directory.Exists(dir)) return null;
        return Directory.GetFiles(dir, "*.iso").FirstOrDefault(p => !p.EndsWith(".bak", StringComparison.OrdinalIgnoreCase));
    }

    private static string ProbeAccel(string qemu)
    {
        // WHPX (Hyper-V / Windows Hypervisor Platform), then Intel HAXM, then TCG.
        foreach (var accel in new[] { "whpx,kernel-irqchip=off", "hax", "tcg" })
        {
            try
            {
                using var p = Process.Start(new ProcessStartInfo
                {
                    FileName = qemu,
                    Arguments = $"-accel {accel} -machine none -nographic -version",
                    CreateNoWindow = true,
                    UseShellExecute = false,
                    RedirectStandardError = true,
                    RedirectStandardOutput = true,
                });
                if (p == null) continue;
                if (!p.WaitForExit(8000))
                {
                    try { p.Kill(entireProcessTree: true); } catch { /* ignore */ }
                    continue;
                }

                if (p.ExitCode == 0) return accel;
            }
            catch
            {
                /* try next */
            }
        }

        return "tcg";
    }

    private static string BuildQemuArgs(string disk, string? iso, string kernel, string initrd, string accel)
    {
        var sb = new StringBuilder();
        sb.Append($"-accel {accel} ");
        sb.Append("-machine q35 ");
        sb.Append("-cpu qemu64 ");
        sb.Append($"-smp {Smp} ");
        sb.Append($"-m {RamMb} ");
        sb.Append("-display none ");
        sb.Append("-vga virtio ");
        sb.Append("-usb -device usb-tablet ");
        sb.Append("-device virtio-keyboard-pci ");
        sb.Append($"-drive file=\"{disk}\",if=virtio,format=qcow2,discard=unmap,detect-zeroes=unmap ");
        if (File.Exists(kernel) && File.Exists(initrd) && !string.IsNullOrEmpty(iso) && File.Exists(iso))
        {
            sb.Append($"-kernel \"{kernel}\" ");
            sb.Append($"-initrd \"{initrd}\" ");
            sb.Append($"-append \"root=/dev/ram0 SRC=/ DATA=vda SETUPWIZARD=1 ENCRYPT=1 androidboot.selinux=permissive\" ");
            sb.Append($"-drive file=\"{iso}\",if=ide,media=cdrom,readonly=on ");
        }
        else
        {
            if (!string.IsNullOrEmpty(iso) && File.Exists(iso))
                sb.Append($"-drive file=\"{iso}\",media=cdrom,readonly=on ");
            sb.Append("-boot order=dc ");
        }
        sb.Append($"-netdev user,id=net0,hostfwd=tcp:127.0.0.1:{AdbPort}-:5555 ");
        sb.Append("-device virtio-net-pci,netdev=net0 ");
        sb.Append("-device virtio-serial-pci ");
        sb.Append("-monitor none ");
        sb.Append("-serial null ");
        sb.Append("-no-reboot ");
        return sb.ToString();
    }

    private static Process StartHidden(string file, string args, string workDir, bool createNoWindow = true)
    {
        var psi = new ProcessStartInfo
        {
            FileName = file,
            Arguments = args,
            WorkingDirectory = workDir,
            CreateNoWindow = createNoWindow,
            UseShellExecute = false,
            WindowStyle = createNoWindow ? ProcessWindowStyle.Hidden : ProcessWindowStyle.Normal,
            RedirectStandardOutput = createNoWindow,
            RedirectStandardError = createNoWindow,
        };
        var p = Process.Start(psi) ?? throw new InvalidOperationException("Failed to start " + file);
        return p;
    }

    private static void WaitForTcp(string host, int port, TimeSpan timeout, Process qemu)
    {
        var deadline = DateTime.UtcNow + timeout;
        while (DateTime.UtcNow < deadline)
        {
            if (qemu.HasExited)
                throw new InvalidOperationException("QEMU exited before ADB port opened (code " + qemu.ExitCode + ").");
            try
            {
                using var c = new TcpClient();
                var task = c.ConnectAsync(host, port);
                if (task.Wait(500) && c.Connected) return;
            }
            catch
            {
                /* retry */
            }

            Thread.Sleep(500);
        }

        throw new TimeoutException($"ADB port {host}:{port} did not open.");
    }

    private static void WaitBootCompleted(string adb, Process qemu)
    {
        var deadline = DateTime.UtcNow + BootTimeout;
        while (DateTime.UtcNow < deadline)
        {
            if (qemu.HasExited)
                throw new InvalidOperationException("QEMU exited during Android boot.");
            Adb(adb, $"connect 127.0.0.1:{AdbPort}");
            var prop = AdbOut(adb, $"-s 127.0.0.1:{AdbPort} shell getprop sys.boot_completed");
            if (prop.Trim() == "1") return;
            Thread.Sleep(2000);
        }

        throw new TimeoutException("sys.boot_completed was not set in time.");
    }

    private static void RunOptionalFirstBootHooks(string adb, string root)
    {
        var script = Path.Combine(root, "scripts", "first-boot-lock.sh");
        if (!File.Exists(script)) return;
        Adb(adb, $"-s 127.0.0.1:{AdbPort} push \"{script}\" /data/local/tmp/first-boot-lock.sh");
        Adb(adb, $"-s 127.0.0.1:{AdbPort} shell sh /data/local/tmp/first-boot-lock.sh");
    }

    private static void SideloadTelosApkIfPresent(string adb, string root)
    {
        var env = Environment.GetEnvironmentVariable("TELOS_APK");
        string? apk = null;
        if (!string.IsNullOrEmpty(env) && File.Exists(env)) apk = env;
        else
        {
            var hits = Directory.Exists(root)
                ? Directory.GetFiles(root, "app-default-*.apk", SearchOption.AllDirectories)
                : Array.Empty<string>();
            apk = hits.FirstOrDefault();
        }

        if (apk == null) return;
        Adb(adb, $"-s 127.0.0.1:{AdbPort} install -r \"{apk}\"");
        Adb(adb, $"-s 127.0.0.1:{AdbPort} shell cmd package set-home-activity de.mm20.launcher2/.ui.launcher.LauncherActivity");
    }

    private static void GracefulShutdown(string adb, Process qemu)
    {
        try
        {
            Adb(adb, $"-s 127.0.0.1:{AdbPort} shell reboot -p");
        }
        catch
        {
            /* guest may already be gone */
        }

        var until = DateTime.UtcNow + ShutdownTimeout;
        while (!qemu.HasExited && DateTime.UtcNow < until)
            Thread.Sleep(500);

        if (!qemu.HasExited)
        {
            try { qemu.Kill(entireProcessTree: true); } catch { /* ignore */ }
            qemu.WaitForExit(5000);
        }
    }

    private static void Adb(string adb, string args) => AdbOut(adb, args);

    private static string AdbOut(string adb, string args)
    {
        using var p = Process.Start(new ProcessStartInfo
        {
            FileName = adb,
            Arguments = args,
            CreateNoWindow = true,
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        });
        if (p == null) return string.Empty;
        var stdout = p.StandardOutput.ReadToEnd();
        p.WaitForExit(60_000);
        return stdout;
    }
}


