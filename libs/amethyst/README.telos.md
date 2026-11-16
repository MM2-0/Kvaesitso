# Amethyst source for embedded Aether

Source: https://github.com/vitorpamplona/amethyst
Revision: `75bb07fa3adf4ef5d590bb5379d87f01c139a430` (MIT; see LICENSE).

This is an integration work in progress, not a released client. The Android
application module is converted into a library; the shared client, UI, native
code, tests and sandbox modules remain in this build. No external Amethyst APK
is used. The Telos host connection and runtime verification are still pending.

The upstream Play and F-Droid source sets remain separate. Production ABI
selection, signing, minification and APK versions belong to the Telos host.

## Local adaptations

- App module converted to AAR; host owns signing, versions and ABI splits.
- Android Gradle plugin aligned to Telos 9.5.1.
- Independent build includes only Android client dependencies and the test relay.
- Application provides an IO initialization entry point for a future lazy host.
- EmbeddedAether renders the original full client while inheriting host Material roles.
- No changes to the Nostr protocol, signing, account storage or shared client models.
- Production host integration and cold-component startup audit are not complete.
