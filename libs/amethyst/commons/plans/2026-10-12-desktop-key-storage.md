# Desktop key storage as a first-class feature

> **Status:** built 2026-10-12. Follows the keyring fixes of the same week (the macOS prompt storm,
> the Linux release builds that never reached the keyring).

The desktop keeps each account's private key in the OS keyring through `SecureKeyStorage`
(`commons/src/jvmMain/.../keystorage/`, `java-keyring`). Until now it failed in three ways, and only
one of them was in the code:

1. **It worked in development and broke in release.** Both Linux failures (dbus-java's transport
   stripped by ProGuard, the signed `hkdf` jar refused after ProGuard rewrote it) existed only in
   the shrunk release jars. Nothing exercised the keyring of a packaged build.
2. **It failed silently.** A missing backend, a denied macOS prompt or a locked keyring ended as a
   read-only login or a key that was quietly not kept.
3. **It had no fallback a GUI could use.** Without an OS keyring the app asked for a master
   password on a terminal it does not have.

## 1. Every release build proves its keyring works

`Amethyst --keyring-self-test` (beside `--publish-scheduled` in `AmethystDesktop.main`) opens the
OS keyring exactly as a login does, writes, reads back and deletes a probe secret under its own
service name (`amethyst-desktop-self-test`, never the user's items), prints the backend it used and
exits 0, or prints why and exits 1. It never falls back to the password file: a build whose keyring
is broken must fail. `AMETHYST_SELF_TEST_REPORT=<file>` also writes the report to a file, for the
Windows launcher, whose GUI subsystem has no console.

CI runs it on the **packaged** app, where the release-only failures live:

| Workflow | Leg | Keyring |
|---|---|---|
| `smoke-test-desktop.yml` (PRs touching `desktopApp/`) | `release-deb-launch`, the installed release `.deb` | GNOME Keyring in a `dbus-run-session` |
| `create-release.yml` (tags) | macOS release `.app` | the runner's keychain |
| `create-release.yml` | Windows x64 / arm64 release app image | Credential Manager |
| `create-release.yml` | `linux-portable`: the Flatpak bundle, installed and run with `flatpak run` | GNOME Keyring in a `dbus-run-session` |

The build-time guards stay: `verifyJkeychainNativeSurvivesProguard` and
`verifyDbusTransportSurvivesProguard` catch the two known regressions before packaging, and the
ProGuard task drops jar signatures it invalidates.

## 2. The state is visible

`SecureKeyStorage.status` (`KeyStorageStatus`, `commons/commonMain/keystorage`) says where keys
live: the OS keyring (and which), the keyring present but unreachable (denied, locked, failed),
the password file (locked or open), or nothing. It is set when the backend opens and on every
failure.

- **Denied is not "not found".** java-keyring turns every failure into `PasswordAccessException`;
  the backends word a missing item as `No stored credentials match…` (macOS, Secret Service) or
  Windows error 1168. Anything else is an access failure. A denied read of the vault no longer
  rewrites the vault, no longer lets the legacy import mint a fresh metadata key, and no longer
  logs the account in read-only: the strict read throws, and the app shows **Amethyst couldn't open
  your keychain** with **Try again**.
- **Key Backup** carries the status as one card (`KeyStorageStatusCard`, shown when the platform
  provides a `KeyStorageController` through `AppRoot.keyStorage`) with Try again or Unlock. It
  began as its own Settings › Key storage screen; it moved to Key Backup, where users already go
  for their key, and the Key Backup entry shows wherever there is key storage, so an account
  loaded without its key still reaches Unlock.
- **The login screen** says when this device cannot keep a key, before the user types an nsec.

## 3. A fallback without an OS keyring, and keys that are never kept

Both choices are offered.

**Password file (NIP-49).** When no OS keyring exists, keys go to
`<data dir>/keys.ncryptsec` (a properties file), each one a NIP-49 `ncryptsec` (scrypt, XChaCha20-Poly1305)
under one device password. The first key to be kept asks the user to choose that password (12
characters at least, `Nip49.MIN_PASSWORD_LENGTH`); each launch asks for it once, in a dialog, before
the default account loads. A wrong password asks again; Skip loads the account read-only for this
session. The password lives in memory for the session only. Keys kept by the old console fallback
(`~/.amethyst/keys.enc`) are moved into the new file when that password unlocks them.

Only private keys (32 bytes) can be kept this way; the other aliases the legacy app used are
imported through the OS keyring only.

**Don't remember my key.** The login screen (desktop, through `AppPlatform.offersRememberKeyChoice`)
has *Remember my key on this device*, on by default. Off, the account and its settings are saved
but the key is not (`AccountSettings.rememberKey = false`, `AccountInfo.keyRemembered = false`); the
next launch asks for the key in a dialog (Skip = read-only). The same choice is offered when the
password file asks for a new password. Any key already stored for that account is deleted.

The prompts are one headless queue (`KeyStoragePrompts`, commons) that the storage and the session
store suspend on, and one shared dialog host (`KeyStoragePromptHost`, commonsUI) that answers it.

## 4. Smaller fixes

- The vault fold-in at each launch only probes the keys of accounts that hold their own key;
  bunker transport aliases are probed during the one-time legacy import only.
- Deleting an account also deletes its legacy bunker transport key from the keyring.
- **Not code:** macOS release builds must be signed (`AMETHYST_MAC_SIGN_IDENTITY` and the notary
  secrets in the release workflow) for *Always Allow* to survive an update; unsigned builds are a
  new app to the Keychain after every rebuild.
