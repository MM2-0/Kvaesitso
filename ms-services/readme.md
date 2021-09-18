# :ms-services

⚠️ Depends on non-free external services.

This module manages API calls to Microsoft APIs and connected Microsoft accounts.

## Configuration

This module requires additional configuration in order to work properly. You can skip this step but
then Microsoft API related features (e.g. OneDrive search) won't be available.

In order to use Microsoft Graph APIs, you need to setup a new project in the Microsoft Azure Portal first.

1. Open the [Microsoft Azure Portal](https://portal.azure.com)
1. Create a new project.
   1. Search for Azure Active Directory
   1. On the left side, select App registrations
   1. Add a new registration
      1. Supported account types: Personal Microsoft Accounts only
1. Add an authentication platform
   1. Go to Authentication
   1. Add a platform > Android
   1. Enter the debug package name (de.mm20.launcher2.debug) and the signature hash of your debug key
      1. You can use the following command to generate the signature hash:
         `keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore | openssl sha1 -binary | openssl base64`
   1. Click Configure > Done
   1. In the newly created Android section, click on Add URI
   1. Add package name (de.mm20.launcher2.release) and signature hash of your release key
1. Download the client details
   1. In the debug client row, click on View
   1. Copy the JSON below MSAL Configuration to ./src/debug/res/raw/msal_auth_config.json
1. Repeat the previous step for the release config
1. Add the required scopes
   1. Go to API permissions
   1. Add a permission
   1. Select Microsoft Graph > Delegated permissions
   1. Tick the following scopes:
      - Files.Read.All
      - User.Read
   1. Click Add permissions

