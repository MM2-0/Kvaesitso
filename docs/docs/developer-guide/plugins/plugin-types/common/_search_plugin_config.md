Search plugins have the following configuration properties:

- `storageStrategy`: Describes how the launcher should store a search result in its internal
  database. This is relevant when a user pins a search result to favorites, or when they assign a
  tag or custom label. In these situations, the launcher needs to be able to restore the search
  result from its database. There are two different strategies:
    - **`StorageStrategy.StoreReference`**: The launcher only stores the ID of the search result,
      and the plugin that created it. To restore a result, the plugin is queried again. This
      strategy allows the plugin provider to update a search result at a later point in time.
      However, plugins that use this strategy must guarantee that a search result can be restored in
      a timely manner. In particular, the plugin provider must be able to restore a search result
      without any network requests.
    - **`StorageStrategy.StoreCopy`** (default): The launcher stores all relevant information about
      this search result in its own internal database. The result can be restored without querying
      the plugin again. This strategy is very easy to implement. The downside is, that results
      cannot be updated at a later point in time.
    - **`StorageStrategy.Deferred`** (default): The launcher stores all relevant information in its
      own internal database, like [StoreCopy]. A fresh copy is fetched from the plugin provider when
      the user opens the search result's detail view. This allows the plugin provider to update the
      search result at a later point in time, without the time constraints of [StoreReference].
