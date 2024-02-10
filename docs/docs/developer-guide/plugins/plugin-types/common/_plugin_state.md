Some plugins need to be configured before they can be used. For example, users might need to connect an account, or provide an API key.

If your plugin has such requirements, you can override

```kt
suspend fun getPluginState(): PluginState
```

This method can either return <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk/-plugin-state/-ready/index.html" target="_blank">`PluginState.Ready`</a>, or <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk/-plugin-state/-setup-required/index.html" target="_blank">`PluginState.SetupRequired`</a>.

- `PluginState.Ready` can have a status `text` to describe what the plugin does in its current configuration. For example _"Search {username}'s files on {service}_". This overrides the plugin's [description](/docs/developer-guide/plugins/metadata.html#description-1).
- `PluginState.SetupRequired` needs to have a `setupActivity` Intent that starts the setup. You can also provide a `message` to describe what kind of setup needs to be performed. For example _"Sign in with {service} to search files on {service}"_

> [!IMPORTANT]
> This method is only meant to provide hints to the launcher's user interface. You should not rely on it as a safeguard for your other plugin methods. There is still a chance that your other plugin methods are called regardless of the return value of this method. Make sure to check your requirements in the other plugin methods as well.
