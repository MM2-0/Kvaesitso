using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Xml.Linq;

namespace Telos.AndroidVm;

internal sealed record BlissosRelease(
    string Channel,
    string Id,
    string Version,
    string Date,
    string Filename,
    string Url,
    string Sha256Url);

internal static class BlissosUpdate
{
    private static readonly Regex IsoName = new(
        @"^(Bliss-v(\d+\.\d+\.\d+)-x86_64-OFFICIAL-foss-(\d{8}))\.iso$",
        RegexOptions.Compiled);

    private static readonly string[] Channels =
    {
        "Official/BlissOS16/FOSS/Generic",
        "Official/BlissOS17/FOSS/Generic",
        "Official/BlissOS18/FOSS/Generic",
    };

    internal static int Run(string root, bool assumeYes)
    {
        var lockPath = Path.Combine(root, "blissos.lock.json");
        if (!File.Exists(lockPath))
            throw new FileNotFoundException("blissos.lock.json missing", lockPath);

        using var http = new HttpClient();
        http.DefaultRequestHeaders.UserAgent.ParseAdd("TelosAndroidVm/1.0");
        http.Timeout = TimeSpan.FromMinutes(30);

        var lockDoc = JsonDocument.Parse(File.ReadAllText(lockPath));
        var pinnedName = lockDoc.RootElement.GetProperty("filename").GetString() ?? "";
        var pinned = Parse(pinnedName, lockDoc.RootElement.GetProperty("channel").GetString() ?? Channels[0]);
        var latest = Discover(http) ?? pinned;
        if (latest == null || pinned == null)
            return 1;
        if (latest.Id == pinned.Id)
            return 0;

        var majorJump = latest.Version.Split('.')[0] != pinned.Version.Split('.')[0];
        var msg =
            $"Download BlissOS {latest.Version} ({latest.Filename})?\n" +
            "The encrypted data disk (android-data.qcow2) is kept.\n" +
            (majorJump ? "Major line change: existing /data may not unlock.\n" : "") +
            "ISO only; userdata is not formatted.";
        if (!assumeYes && !Ask(msg))
            return 0;

        var shaText = http.GetStringAsync(latest.Sha256Url).GetAwaiter().GetResult();
        var sha = shaText.Trim().Split()[0].ToLowerInvariant();
        if (sha.Length != 64)
            throw new InvalidOperationException("invalid sha256 sidecar");

        var images = Path.Combine(root, "images");
        Directory.CreateDirectory(images);
        var dest = Path.Combine(images, latest.Filename);
        Download(http, latest.Url, dest);
        var got = HashFile(dest);
        if (got != sha)
        {
            File.Delete(dest);
            throw new InvalidOperationException($"SHA-256 mismatch {got}");
        }

        var iso = Path.Combine(images, "blissos-x86_64.iso");
        if (File.Exists(iso))
            File.Replace(dest, iso, Path.Combine(images, "blissos-x86_64.iso.bak"));
        else
            File.Move(dest, iso);

        var json = JsonSerializer.Serialize(new
        {
            channel = latest.Channel,
            id = latest.Id,
            version = latest.Version,
            date = latest.Date,
            filename = latest.Filename,
            url = latest.Url,
            sha256_url = latest.Sha256Url,
            keep_data_disk = true,
        }, new JsonSerializerOptions { WriteIndented = true });
        File.WriteAllText(lockPath, json + Environment.NewLine);
        return 0;
    }

    private static BlissosRelease? Parse(string filename, string channel)
    {
        var m = IsoName.Match(filename);
        if (!m.Success) return null;
        var url = $"https://downloads.sourceforge.net/project/blissos-x86/{channel}/{filename}";
        return new BlissosRelease(channel, m.Groups[1].Value, m.Groups[2].Value, m.Groups[3].Value, filename, url, url + ".sha256");
    }

    private static BlissosRelease? Discover(HttpClient http)
    {
        BlissosRelease? best = null;
        foreach (var ch in Channels)
        {
            try
            {
                var xml = http.GetStringAsync(
                    $"https://sourceforge.net/projects/blissos-x86/rss?path=/{ch}&limit=100")
                    .GetAwaiter().GetResult();
                var doc = XDocument.Parse(xml);
                foreach (var title in doc.Descendants("title"))
                {
                    var name = Path.GetFileName((title.Value ?? "").Replace('\\', '/').Trim());
                    var rel = Parse(name, ch);
                    if (rel == null) continue;
                    if (best == null || Compare(rel, best) > 0) best = rel;
                }
            }
            catch
            {
                /* next channel */
            }
        }

        return best;
    }

    private static int Compare(BlissosRelease a, BlissosRelease b)
    {
        var va = a.Version.Split('.').Select(int.Parse).ToArray();
        var vb = b.Version.Split('.').Select(int.Parse).ToArray();
        for (var i = 0; i < Math.Max(va.Length, vb.Length); i++)
        {
            var x = i < va.Length ? va[i] : 0;
            var y = i < vb.Length ? vb[i] : 0;
            if (x != y) return x.CompareTo(y);
        }

        return string.CompareOrdinal(a.Date, b.Date);
    }

    private static void Download(HttpClient http, string url, string dest)
    {
        using var resp = http.GetStreamAsync(url).GetAwaiter().GetResult();
        using var fs = File.Create(dest);
        resp.CopyTo(fs);
    }

    private static string HashFile(string path)
    {
        using var fs = File.OpenRead(path);
        return Convert.ToHexString(SHA256.HashData(fs)).ToLowerInvariant();
    }

    private static bool Ask(string message)
    {
        try
        {
            return Native.MessageBox(IntPtr.Zero, message, "TelosAndroidVm — BlissOS", 0x00000004) == 6;
        }
        catch
        {
            Console.WriteLine(message + " [y/N]");
            var line = Console.ReadLine() ?? "";
            return line.Equals("y", StringComparison.OrdinalIgnoreCase) || line == "yes";
        }
    }
}

internal static class Native
{
    [System.Runtime.InteropServices.DllImport("user32.dll", CharSet = System.Runtime.InteropServices.CharSet.Unicode, EntryPoint = "MessageBoxW")]
    internal static extern int MessageBox(IntPtr hWnd, string text, string caption, uint type);
}
