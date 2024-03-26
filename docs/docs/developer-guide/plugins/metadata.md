# Metadata

You can customize how your plugin appears in Kvaesitso's UI.

## Plugin package

These properties apply to your whole plugin package. They are shown in the list of plugins and at the top section of the plugin detail page. `<meta-data />` elements must be added as a direct child of the `<application />` element in your `AndroidManifest.xml`.

### Label

```xml
<meta-data
    android:name="de.mm20.launcher2.plugin.label"
    android:value="@string/plugin_package_name" />
```

The label that is used for the plugin package. If none is set, the application label is used instead.

### Description

```xml
<meta-data
    android:name="de.mm20.launcher2.plugin.description"
    android:value="@string/plugin_package_description" />
```

A description for your plugin package.

### Author

```xml
<meta-data
    android:name="de.mm20.launcher2.plugin.author"
    android:value="Your Name" />
```

The name of the plugin author.

### Icon

```xml
<meta-data
    android:name="de.mm20.launcher2.plugin.icon"
    android:resource="@drawable/ic_plugin_icon" />

```

The icon for the plugin package. If none is set, the app icon is used instead.

## Plugin

These properties apply to a single plugin. One plugin package can contain multiple plugins, but all plugins of one plugin package are grouped on the same settings screen.

Plugin metadata are either set as attributes on the `<content-provider />` element, or as `<meta-data />` elements that are direct children of the `<content-provider />` element in the `AndroidManifest.xml`:

### Label

```xml
<content-provider
    android:label="@string/plugin_label" />
```

This is used in several places in the launcher UI, depending on the type of plugin. For example, if your plugin is a weather plugin, this is shown in the weather provider settings. If your plugin is a file search plugin, this is shown on the file search settings screen. If none is set, the application label is used instead.

### Icon

```xml
<content-provider
    android:icon="@drawable/ic_plugin" />

```

For search plugins, this is used on the little badge that indicates from which plugin a search result originated. If none is set, the application icon is used instead.

### Description

```xml
<meta-data
    android:name="de.mm20.launcher2.plugin.description"
    android:value="@string/plugin_description" />
```

A static description what your plugin does. This can be overridden by the plugin itself to display dynamic information instead.
