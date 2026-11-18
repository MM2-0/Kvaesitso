# Aether

Aether in Telos 1.45.71 embeds the Amethyst F-Droid Nostr client
inside Telos. No separate Amethyst application is required. Earlier Telos versions contain
the preview rather than the embedded client.

## Accounts

You can use multiple accounts. Open the profile menu, choose **Accounts**, then
**Add New Account**. Select an existing account to switch to it, or use its
logout action to remove it. Each account retains its own preferences and
credentials. Removing one account does not remove the others.

A public `npub` signs in for reading. Publishing requires a signing-capable
account, an imported private key or a supported external signer. Keep recovery
keys safe: Android backup deliberately excludes Aether account credentials.

## Features and appearance

The embedded client includes feeds, profiles, account management, messaging,
relay management, media, wallets and the original client settings. Features that
need an external service or signer retain their respective requirements. The
F-Droid distribution does not include proprietary Play-only integrations.

Open **Settings** from the profile menu to manage the client. Search for the
feature-set setting and select **Complete** to show optional destinations;
the upstream default is **Simplified**. Appearance follows
Telos by default, including font scaling, the Aether icon and Cybertech settings chrome. You can
choose a separate client theme or accent color; **System** restores the inherited
accent. Background notifications remain opt-in and preserve per-account choices.

See [integration and validation](../../developer-guide/aether-amethyst.md) for
source provenance and checks completed before release.
