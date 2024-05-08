# Plugins

Plugins are a way to extend the launcher's functionality. Plugins act as data providers for several
launcher components.

As of now, only weather provider plugins and file search plugins are supported, but in the long
term,
support for all search categories is planned.

## Usage

### Installation

Plugins are installed the same way as any other app. You can either download them from an app store,
or install them manually as APK files.

### Activation

After the plugin has been installed, it needs to be activated in the launcher settings. To do this,
go to settings > plugins, tap on the plugin you want to activate, and enable it. The plugin will
show a permission dialog. Tap on "Allow" to give the launcher the necessary permission to access
the plugin's data.

Next, you may or may not need to perform steps to configure the plugin. For example, a plugin might
require you to sign in with an account in order to use it. A banner is shown when additional
configuration steps are needed:

<img src="/img/plugin-configuration.png" width="300"/>

Last but not least, you need to enable the plugin functionalities that you want to use.

For weather plugins, you need to change the weather integration settings to use the plugin as
weather provider.

For search plugins, you need to enable the search provider in search settings. Shortcuts to these
settings are available on the plugin's settings page.

## Available plugins

As of now, the following plugins are available:

- **OneDrive plugin**: adds OneDrive file
  search [[Github]](https://github.com/Kvaesitso/Plugin-OneDrive) [[Download]](https://fdroid.mm20.de/app/de.mm20.launcher2.plugin.onedrive)
- **OpenWeatherMap plugin**: adds OpenWeatherMap weather
  provider [[Github]](https://github.com/Kvaesitso/Plugin-OpenWeatherMap) [[Download]](https://fdroid.mm20.de/app/de.mm20.launcher2.plugin.openweathermap)

## Plugin development

If you are a developer and you are interested in developing your own plugin for the launcher,
you can find more information in the [developer guide](/docs/developer-guide/plugins/get-started).