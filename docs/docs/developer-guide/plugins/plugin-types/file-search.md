# File Search

File search provider plugins need to extend the <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.files/-file-provider/index.html" target="_blank">`FileProvider`</a> class:

```kt
class MyFileSearchPlugin : FileProvider(
    SearchPluginConfig()
)

```

In the super constructor call, pass a <a href="/reference/core/shared/de.mm20.launcher2.plugin.config/-search-plugin-config/index.html" target="_blank">`SearchPluginConfig`</a> object.

## Plugin config

<!--@include: ./common/_search_plugin_config.md-->

## Search files

To implement file search, override

```kt
suspend fun search(query: String, allowNetwork: Boolean): List<File>
```

- `query` is the search term
- `allowNetwork` is a flag that indicates whether the user has enabled online search for this query. Plugins are generally advised to respect this request. This flag exists mainly for privacy reasons: the majority of searches target offline results (like apps, or contacts). Sending every single search request to external servers is overkill and can be a privacy issue. (Besides, it's not very nice to overload servers with unnecessary requests.) To reduce the amount of data that is leaked to external servers, users can control, whether a search should include online results or not.

`search` returns a list of `File`s. The list can be empty if no results were found.

### The `File` object

A `File` has the following properties:

- `id`: A unique and stable identifier for this file. This is used to track usage stats so if two files are identical, they must have the same ID, and if they are different, they need to have different IDs.
- `uri`: A URI that is used to open the file.
- `displayName`: The name that is shown to the user
- `mimeType`: The MIME type of the file. This is only used for informational purposes, i.e. to determine the icon.
- `size`: The file size in bytes.
- `path`: The file path. This is shown for informational purposes. It is not used to read or open the file.
- `isDirectory`: Whether the file is a folder. If true, a folder icon is shown.
- `thumbnailUri`: An optional URI to a file thumbnail. Supported schemes are: `content`, `file`, `android.resource`, `http`, and `https`. If this is a `content` URI, make sure that the launcher has the permissions to access it.
- `owner`: The name of the owner of the file. This is mainly relevant for files that are stored in a cloud drive and are not owned by the user themselves, but shared with them.
- `metadata`: Additional file metadata.

## Get a file

If you have set `config.storageStrategy` to `StorageStrategy.StoreReference`, you must override

```kt
suspend fun get(id: String): File?
```

This method is used to lookup a file by its `id`. If the file is no longer available, it should return `null`. In this case, the launcher will remove it from its database.

## Plugin state

<!--@include: ./common/_plugin_state.md-->

## Examples

- [OneDrive plugin](https://github.com/Kvaesitso/Plugin-OneDrive)
