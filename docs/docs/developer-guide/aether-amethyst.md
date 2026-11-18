# Embedded Amethyst in Aether

Aether composes the real Amethyst Android client inside the Telos APK. It does
not install, launch or require an external Amethyst APK. This integration is on
the development branch; the latest published v1.45.69 still has the preview.

## Source and distribution

`libs/amethyst` preserves the MIT-licensed Android client and its `commons`,
`commonsUI`, `nestsClient`, `nappletHost` and test relay modules at revision
`75bb07fa3adf4ef5d590bb5379d87f01c139a430`. The application module is an Android
library selected through a composite build. Telos selects the F-Droid distribution;
Google Play-specific Firebase, Cast and Google AI extensions are not included.
The Play source set is retained for source provenance, with no Firebase key shipped.

## Host boundaries

- `LauncherApplication` defers client initialization until Aether or one of its
  background entry points needs it. Initialization is serialized on IO. The
  keyless `:napplet` sandbox cannot initialize an account or start launcher
  Koin, preferences and privileged transports.
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
- Workers and push callbacks await initialization. Aether notifications use their
  own tag; clearing them does not cancel Telos alarms or downloads. Media playback
  uses notification ID 7407, distinct from the other Telos players.

## Validation

[Actions run 44277134771](https://github.com/koukomin/Telos-Launcher/actions/runs/44277134771)
passed the combined ARM64 debug APK build, library build and Android storage
instrumentation. The library host suite reported 1,526 tests, zero failures/errors
and nine skips. These results apply to revision `896782b45`, before subsequent
UI/chrome changes; they are not a release or full client runtime certification.

`validate-aether.yml` additionally builds the real Telos APK for an x86_64
emulator solely for validation (release APKs remain ARM-specific). A separate
instrumentation process drives public-key-only login, multiple accounts, settings
and process restart with network disabled, plus large-font portrait/landscape
settings captures. After all integration gates pass, Actions opens the review PR. Screenshots are exported through
MediaStore before test APK removal. The public-fixture evidence branch records
the tested source revision for visual review.

Production R8, real signer consent, publication, external relay interoperability,
physical OnePlus/Android 17 behavior and the full visual/font matrix must be
reported from executed checks, never inferred from compilation.
