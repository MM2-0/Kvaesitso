# File Search

File search provider plugins need to extend
the <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.files/-file-provider/index.html" target="_blank">`FileProvider`</a>
class:

```kt
class MyFileSearchPlugin() : FileProvider(
    QueryPluginConfig()
)

```

In the super constructor call, pass
a <a href="/reference/core/shared/de.mm20.launcher2.plugin.config/-query-plugin-config/index.html" target="_blank">`QueryPluginConfig`</a>
object.

## Plugin config

<!--@include: ./common/_query_plugin_config.md-->

## Search files

To implement file search, override

```kt
suspend fun search(query: String, params: SearchParams): List<File>
```

- `query` is the search term

<!--@include: ./common/_search_params.md-->

`search` returns a list of `File`s. The list can be empty if no results were found.

### The `File` object

A `File` has the following properties:

- `id`: A unique and stable identifier for this file. This is used to track usage stats so if two
  files are identical, they must have the same ID, and if they are different, they need to have
  different IDs.
- `uri`: A URI that is used to open the file.
- `displayName`: The name that is shown to the user
- `mimeType`: The MIME type of the file. This is only used for informational purposes, i.e. to
  determine the icon.
- `size`: The file size in bytes.
- `path`: The file path. This is shown for informational purposes. It is not used to read or open
  the file.
- `isDirectory`: Whether the file is a folder. If true, a folder icon is shown.
- `thumbnailUri`: An optional URI to a file thumbnail. Supported schemes
  are: `content`, `file`, `android.resource`, `http`, and `https`. If this is a `content` URI, make
  sure that the launcher has the permissions to access it.
- `owner`: The name of the owner of the file. This is mainly relevant for files that are stored in a
  cloud drive and are not owned by the user themselves, but shared with them.
- `metadata`: Additional file metadata.

## Refresh a file

If you have set `config.storageStrategy` to `StorageStrategy.StoreCopy`, the launcher will
periodically
try to refresh the stored copy. This happens for example when a user long-presses a file to view its
details. To update the file, you can override

```kt
suspend fun refresh(item: File, params: RefreshParams): File?
```

The stored file will be replaced with the return value of this method. If the file is no longer
available, it should return `null`. In this case, the launcher will remove it from its database. If
the file is temporarily unavailable, an exception should be thrown.

- `item` is the version that the launcher has currently stored

<!--@include: ./common/_refresh_params.md-->

The default implementation returns `item` without any changes.

## Get a file

If you have set `config.storageStrategy` to `StorageStrategy.StoreReference`, you must override

```kt
suspend fun get(id: String, params: GetParams): File?
```

This method is used to lookup a file by its `id`. If the file is no longer available, it should
return `null`. In this case, the launcher will remove it from its database.

- `id` is the ID of the file that is being requested

<!--@include: ./common/_get_params.md-->

## Plugin state

<!--@include: ./common/_plugin_state.md-->

## Examples

- [OneDrive plugin](https://github.com/Kvaesitso/Plugin-OneDrive)
