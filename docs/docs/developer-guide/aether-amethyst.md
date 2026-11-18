# Embedded Amethyst in Aether

Aether composes the real Amethyst Android client inside the Telos APK. It does
not install, launch or require an external Amethyst APK. The integration is included
in version 1.45.71. Earlier published versions contain the local preview.

## Source and distribution

`libs/amethyst` preserves the MIT-licensed Android client and its `commons`,
`commonsUI`, `nestsClient`, `nappletHost` and test relay modules at revision
`75bb07fa3adf4ef5d590bb5379d87f01c139a430`. The application module is an Android
library selected through a composite build. Telos selects the F-Droid distribution;
Google Play-specific Firebase, Cast and Google AI extensions are not included.
The Play source set is retained for source provenance, with no Firebase key shipped.

## Launcher cold-start regression

Real-APK instrumentation found that asynchronous Koin module registration raced
with startup consumers: resolving `DesktopModeManager` crashed the process before
the Aether screen appeared. All definitions are now registered synchronously;
eager instances remain deferred to a background coroutine. Startup hooks that
need Koin begin only after registration. The same real-APK login/restart test is
the regression gate. This does not initialize the Nostr graph at launcher startup.

## Host boundaries

- `LauncherApplication` defers client initialization until Aether or one of its
  background entry points needs it. Initialization is serialized on IO. The
  keyless `:napplet` sandbox cannot initialize an account or start launcher
  Koin, preferences and privileged transports. The standalone client's
  process-wide thread-priority polling governor is not started in the host: it
  would also demote launcher, call-lookup and messaging workers.
- `EmbeddedAether` renders the upstream app, account manager, feeds, settings,
  relays, messaging, media and signer workflows. Multiple accounts use the
  original session manager; adding, switching and removing accounts stay available.
- `AetherStorageContext` places files and databases under `noBackupFilesDir/aether`.
  Encrypted account preferences retain the original encryption and separate
  namespaces in `aether_private.xml`, excluded from both Android backup formats.
  Clearing one account namespace cannot clear another account or launcher settings.
- Native entry activities redirect into the existing Telos document-task host.
  Nostr links, shares, attachment ClipData and URI grants pass through the bridge.
  Both logged-in and logged-out navigation filter internal Telos task identity
  URIs through that bridge, without consuming the host's Recents identity.
  Telos cannot offer itself as an external Nostr Connect signer.
- The default appearance inherits launcher colors, typography, shapes and font
  scaling. Explicit client accent colors remain selectable. Settings cards,
  headings and section labels use the host Cybertech components; drawers and
  floating login cards use opaque tonal containers over the shared root backing.
  `VirtualAppWindow` supplies the same shared wallpaper/glass backing as other
  Telos apps. The client surface role is opaque for nested dialogs; only page
  background inherits transparency. Inherited dark/light state follows the host palette.
  Login uses the Aether name and existing Rondo glyph; the imported decorative
  background glow is disabled in the host to preserve the single root backing.
- Workers and push callbacks await initialization. Aether notifications use their
  own tag; clearing them does not cancel Telos alarms or downloads. Media playback
  uses notification ID 7407, distinct from the other Telos players. Client
  reminder, signer, call, room, upload and Tor notifications also use the host
  Aether foreground glyph; account avatars and media artwork remain intact.

## Included-build dependency security

The 1.45.70 tag is an unpublished validation candidate. Its publication was held
when [dependency review](https://github.com/koukomin/Telos-Launcher/actions/runs/44351458402)
identified vulnerable transitives in the included build, despite the launcher's
existing constraints. Version 1.45.71 applies matching security constraints to
both plugin and project classpaths in Amethyst's own settings. The pins cover
Bouncy Castle 1.87, Guava 33.8.0, JDOM 2.0.6.1, jose4j 0.9.7, HttpClient 4.5.14,
Commons Lang 3.22.0 and OpenTelemetry API/context 1.68.0. Guava keeps its Android
artifact on Android compile/runtime classpaths and its JRE artifact for tools.

`verifyBuildSecurityDependencies` resolves real buildscript and Android runtime
graphs in both builds and compares affected components against advisory minima.
[Dependency review](https://github.com/koukomin/Telos-Launcher/actions/runs/44355647038)
reports zero added vulnerable components against v1.45.69 for `6837a4661`.
Compilation and tests are being revalidated; the rendering evidence below
records the earlier tested application revision, not a claim of completed
security validation for the follow-up.

## Validation

[Run 44336725713](https://github.com/koukomin/Telos-Launcher/actions/runs/44336725713)
validated application revision `e8ef99978`:

- Actual ARM64 Telos debug and minified/resource-shrunk release APK builds passed.
- The library suite executed 1,526 tests: no failures/errors, nine upstream skips.
- All four Android encrypted-storage, namespace/editor, database-path and
  notification-isolation regressions passed without skips.
- The real-Telos external UI driver passed public-key login, adding two accounts,
  switching to the first, settings navigation and restoring both accounts after
  force-stop/relaunch. It also captured settings at system font scale 2.0 in
  portrait and landscape. No real private keys or publication were used.
- The resolved Dokka security graph passed. Release helper tests and the static
  Pages build also passed locally; see the Dependabot guide for the unpatched
  docs-tool advisory and the scoped review of upstream scanner fixtures.

The first device attempt was blocked by a Pixel Launcher ANR dialog in the
emulator. Retrying the unchanged application revision on a fresh emulator passed.
The earlier administrative PR-creation job was removed: this repository does not
permit GitHub Actions to create or approve pull requests.

### Reviewed captures

These captures come from the tested revision, using public read-only accounts,
English and the light host theme. Login no longer reports the internal Telos task
URI as an invalid Nostr link. Settings cards and account sheets remain readable,
including font scale 2.0 and landscape; both accounts survive process restart.
They establish this rendering scope, not the full wallpaper/theme/locale matrix.

| Screen | Evidence |
| --- | --- |
| Aether login | [Capture](../../screenshots/development/aether/aether-login.png) |
| Account settings | [Capture](../../screenshots/development/aether/aether-settings.png) |
| Settings at font scale 2.0 | [Capture](../../screenshots/development/aether/aether-settings-large-font.png) |
| Font scale 2.0 in landscape | [Capture](../../screenshots/development/aether/aether-settings-large-font-landscape.png) |
| Two accounts | [Capture](../../screenshots/development/aether/aether-multiple-accounts.png) |
| Accounts after process restart | [Capture](../../screenshots/development/aether/aether-restored-accounts.png) |

APK jobs retain separate Gradle task-output caches so library cache writes do
not discard launcher compilation outputs. The device APK is x86_64 solely for
validation; shipping APKs remain ARM-specific. Captures go through MediaStore
and survive test-APK removal. The evidence branch records the tested revision.

Live relay interoperability, signing/publication, external signers, physical
OnePlus/Android 17 behavior and the complete theme/font/wallpaper matrix are not
established by these automated checks. The shipped distribution is the full
F-Droid client; proprietary Play-only integrations are not included.
