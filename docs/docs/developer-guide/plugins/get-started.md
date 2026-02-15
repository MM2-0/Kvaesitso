# Get started

## Get the plugin SDK

Kvaesitso comes with a plugin SDK that abstracts away the low level details of inter-app
communication and streamlines the process of creating plugins.

Add the following dependency to your project:

```
de.mm20.launcher2:plugin-sdk:$version
```

The current version
is: [![](https://img.shields.io/maven-central/v/de.mm20.launcher2/plugin-sdk?style=flat-square)](https://central.sonatype.com/artifact/de.mm20.launcher2/plugin-sdk)

## Create your first plugin

Start by creating a new class and giving it a meaningful name.

```kt
class MyFirstPlugin

```

This class needs to extends one of the plugin base classes. Which class to extend depends on the
kind of plugin that you want to develop. Please refer to the article of the respective plugin type
to continue. But first, regardless of plugin type, you need to register your plugin in the
`AndroidManifest.xml`.

Under the hood, plugins are implemented using Android's content provider APIs. While the plugin SDK
abstracts most of that away from you, you still need to register the plugin class as a content
provider in the `AndroidManifest.xml`:

```xml

<provider android:name=".MyFirstPlugin" android:authorities="your.package.name.authority"
    android:exported="true">
    <intent-filter>
        <action android:name="de.mm20.launcher2.action.PLUGIN" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</provider>
```

- `android:name` is the class name of your plugin class.
- `android:authorities` must be a globally unique name. It is a good practice to prefix it with your
  app's package name and add a unique suffix.
  > [!WARNING]
  > You must not change this later or things will break.
- The `<intent-filter />` lets Kvaesitso know that this content provider is a plugin.

## Next steps

Your next steps depend on the type of plugin that you want to develop:

- Weather provider
  plugin: [Weather Provider](/docs/developer-guide/plugins/plugin-types/weather.html)
- File search
  plugin: [File Search Provider](/docs/developer-guide/plugins/plugin-types/file-search.html)
- Places search
  plugin: [Places Search Provider](/docs/developer-guide/plugins/plugin-types/places-search.html)
- Contact search
  plugin: [Contact Search Provider](/docs/developer-guide/plugins/plugin-types/contact-search.html)
- Calendar provider
  plugin: [Calendar Provider](/docs/developer-guide/plugins/plugin-types/calendar.html)
