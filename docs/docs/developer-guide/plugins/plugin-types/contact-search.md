# Contact Search

Contact search provider plugins need to extend
the <a href="/reference/plugins/sdk/de.mm20.launcher2.sdk.contacts/-contact-provider/index.html" target="_blank">`ContactProvider`</a>
class:

```kt
class MyContactSearchPlugin() : ContactProvider(
    QueryPluginConfig()
)

```

In the super constructor call, pass
a <a href="/reference/core/shared/de.mm20.launcher2.plugin.config/-query-plugin-config/index.html" target="_blank">`QueryPluginConfig`</a>
object.

## Plugin config

<!--@include: ./common/_query_plugin_config.md-->

## Search contacts

To implement contact search, override

```kt
suspend fun search(query: String, params: SearchParams): List<Contact>
```

- `query` is the search term

<!--@include: ./common/_search_params.md-->

`search` returns a list of `Contact`s. The list can be empty if no results were found.

### The `Contact` object

A `Contact` has the following properties:

- `id`: A unique and stable identifier for this contact. This is used to track usage stats so if two
  contacts are identical, they must have the same ID, and if they are different, they need to have
  different IDs.
- `uri`: A URI that is used to open the contact.
- `name`: The display name of this concact. First name + last name, if applicable.
- `photoUri`: Uri to a contact photo. If `null`, a default icon is used.
- `phoneNumbers`: A list of phone numbers for this contact. Can be an empty list.
- `emailAddresses`: A list of email addresses for this contact. Can be an empty list.
- `postalAddresses`: A list of postal addresses for this contact. Can be an empty list.
- `customActions`: Custom actions to contact this person.

#### Custom actions

Custom actions are channels handled by third party apps to contact a person. This could be a message on Slack, a voice call on WhatsApp, or a video call on Microsoft Teams. A `CustomContactAction` has the following properties:

- `label`: Label that describes the action
- `uri`: Uri that is passed the intent to start the action
- `mimeType`: Type that is passed to the intent to start the action
- `packageName`: Package name of the receiver app. If the app is not installed, the action is ignored.

## Refresh a contact

If you have set `config.storageStrategy` to `StorageStrategy.StoreCopy`, the launcher will
periodically
try to refresh the stored copy. This happens for example when a user long-presses a contact to view its
details. To update the contact, you can override

```kt
suspend fun refresh(item: Contact, params: RefreshParams): Contact?
```

The stored contact will be replaced with the return value of this method. If the contact is no longer
available, it should return `null`. In this case, the launcher will remove it from its database. If
the contact is temporarily unavailable, an exception should be thrown.

- `item` is the version that the launcher has currently stored

<!--@include: ./common/_refresh_params.md-->

The default implementation returns `item` without any changes.

## Get a contact

If you have set `config.storageStrategy` to `StorageStrategy.StoreReference`, you must override

```kt
suspend fun get(id: String, params: GetParams): Contact?
```

This method is used to look up a contact by its `id`. If the contact is no longer available, it should
return `null`. In this case, the launcher will remove it from its database.

- `id` is the ID of the contact that is being requested

<!--@include: ./common/_get_params.md-->

## Plugin state

<!--@include: ./common/_plugin_state.md-->

