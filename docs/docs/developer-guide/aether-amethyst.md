# Embedded Amethyst in Aether

Aether composes the real Amethyst Android client inside the Telos APK. It does
not install, launch or require an external Amethyst APK. The integration is included
in version 1.45.70. Earlier published versions contain the local preview.

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

## Validation

[Run 44314675281](https://github.com/koukomin/Telos-Launcher/actions/runs/44314675281)
validated application revision `c3d24e2fc`:

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

Screenshots of the account selector before/after restart and settings at normal
and enlarged fonts were visually reviewed. They show opaque readable sheets,
Cybertech settings cards and adaptive landscape navigation. The initial capture
shows the loading transition, so it is not visual evidence of the login form.
The shared glass wrapper and settled captures are being revalidated on the next
revision; this record must retain the exact tested source revision.

The workflow's old PR-creation job failed because GitHub Actions cannot create
or approve pull requests in this repository. This was separate from all four
passing integration jobs and is removed from the validation workflow.

APK jobs retain separate Gradle task-output caches so library cache writes do
not discard launcher compilation outputs. The device APK is x86_64 solely for
validation; shipping APKs remain ARM-specific. Captures go through MediaStore
and survive test-APK removal. The evidence branch records the tested revision.

Live relay interoperability, signing/publication, external signers, physical
OnePlus/Android 17 behavior and the complete theme/font/wallpaper matrix are not
established by these automated checks. The shipped distribution is the full
F-Droid client; proprietary Play-only integrations are not included.
