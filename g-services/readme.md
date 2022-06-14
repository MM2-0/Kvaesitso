# :g-services

⚠️ Depends on non-free external services.

This module manages API calls to Google APIs and connected Google accounts.

## Configuration

This module requires additional configuration in order to work properly. You can skip this step but
then Google API related features (e.g. Google Drive search) won't be available.

In order to use Google APIs, you need to setup a new project in the Google Cloud Console first.

1. Open the [Google Cloud Console](https://console.cloud.google.com)
1. Create a new project.
1. Enable the Drive API:
    1. Go to APIs & Services > Library and search for the Google Drive API.
    1. Enable this API for your project.
1. Setup your OAuth consent screen
    1. Go to APIs & Services > OAuth consent screen
    1. On the Oauth consent screen page, fill out all the required fields
    1. On the Scopes page, click add or remove scopes and add the following scopes: `userinfo.profile` and `drive.metadata.readonly`
1. Create a new Oauth 2.0 client (you need to do this twice, for debug builds and for release builds)
    1. Go to APIs & Services > Credentials
    1. Click on Create Credentials > OAuth client ID
    1. Choose application type Android
    1. Enter the package name (de.mm20.launcher2.debug for debug builds or de.mm20.launcher2.release for release builds)
    1. Enter the SHA-1 certificate fingerprint of your APK signing key
    1. Click create
1. Download the client config file (repeat this step for both the debug and the release client)
    1. On the APIs & Services > Credentials page, find your OAuth client in the list under OAuth 2.0 Client IDs.
    1. Click the download icon to download a client_config.json
    1. Place this file under src/debug/res/raw/g_services.json or src/release/res/raw/g_services.json
   
