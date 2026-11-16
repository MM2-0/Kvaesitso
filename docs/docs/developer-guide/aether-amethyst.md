# Embedded Amethyst integration status

This is work in progress. The Aether route in released Telos builds is still
a local preview. No production integration or feature parity is claimed.

## Source and build

`libs/amethyst` contains the full Android client and the `commons`,
`commonsUI`, `nestsClient`, `nappletHost` and test relay (`geode`) modules
from Amethyst revision `75bb07fa3adf4ef5d590bb5379d87f01c139a430`.
The Android application is converted to a library with its original resource
namespace and both upstream distribution source sets. The original MIT
license and source headers are preserved.

The library can be configured independently without changing Telos builds:

```sh
./gradlew -p libs/amethyst :amethyst:assembleFdroidDebug
```

`EmbeddedAether` composes the real upstream client and inherits host Material
colors, typography, shapes and font density. `Amethyst.initializeEmbedded()`
initializes the runtime on IO and serializes concurrent initialization.
A host can override `initializeOnCreate` only after the cold-entry paths below
are handled; the current upstream application startup remains the default.

## Required before enabling or releasing

- Connect the included build, dependency substitution and variant selection.
- Connect LauncherApplication and the guarded Aether route to runtime startup.
- Ensure every cold worker, receiver, foreground service and auxiliary activity
  awaits initialization; preserve the keyless `:napplet` sandbox.
- Forward upstream main-activity intents through the Telos virtual-app guard,
  retaining deep links, share attachments and URI grants.
- Reconcile manifest application attributes, resource names, provider authorities,
  WorkManager setup, native libraries and duplicate class versions.
- Complete dependency/license/security review and narrow consumer R8 rules.
- Verify account creation/import, read-only login, switching/removal, signer
  consent, settings, relays, feeds, publication, media, notifications and links
  using actual Android runtime tests; review Cybertech appearance and large fonts.
- Re-fetch GitHub immediately before merge, integrate newer main changes and
  validate the combined code before signing a release.

No host, device, account or release validation has passed for this integration.
