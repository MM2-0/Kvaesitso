# Microsoft Graph Services

Microsoft Graph Services are used for OneDrive search. To enable OneDrive integration in your builds, follow these steps:

1. Go to the [Microsoft Azure Portal](https://portal.azure.com)
1. Create a new project.
   1. Search for App Registrations
   1. Add a new registration
      1. Supported account types: Accounts in any organizational directory and personal Microsoft accounts
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
   1. Copy the JSON below MSAL Configuration to `ms-services/src/debug/res/raw/msal_auth_config.json` (you'll need to create this file first)
1. Repeat the previous step for the release config
1. Add the required scopes
   1. Go to API permissions
   1. Add a permission
   1. Select Microsoft Graph > Delegated permissions
   1. Tick the following scopes:
      - Files.Read.All
      - User.Read
   1. Click Add permissions
