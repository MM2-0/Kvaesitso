# Access Control

Plugins potentially deal with sensitive user data. It is important to control, which apps can access a plugin's APIs in order to avoid any data breaches.

For that reason, the plugin SDK has a built-in permission system. When a user first tries to enable a plugin in Kvaesitso, the launcher sends a permission request to the plugin. The plugin then shows a dialog which allows the user to either grant or deny the request. Once the request is granted, the package name of the requesting app is added to an internally stored allowlist. When an app tries to use a plugin, the plugin checks whether the calling app has been allowlisted before. If it hasn't, it throws a `SecurityException`.

All the plugin SDK's plugin base classes already implement this system so there are no additional steps needed and your plugin is protected by default. However, if you wish to add or remove apps to or from the allowlist manually, you can use the <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.permissions/-plugin-permission-manager/index.html" target="_blank">`PluginPermissionManager`</a> class.
