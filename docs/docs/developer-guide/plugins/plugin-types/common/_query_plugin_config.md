Search plugins have the following configuration properties:

- `storageStrategy`: Describes how the launcher should store a search result in its internal
  database. This is relevant when a user pins a search result to favorites, or when they assign a
  tag or custom label. In these situations, the launcher needs to be able to restore the search
  result from its database. There are two different strategies:
    - **`StorageStrategy.StoreCopy`** (default): The launcher stores all relevant information about
      this search result in its own internal database. The result can be restored without querying
      the plugin again. The launcher will try refresh the search result at its own discretion (e.g.
      when a user long-presses a restored search result to view its details). This strategy is the
      default and should be used whenever the plugin can't restore a search result immediately. It
      is best suited for online search plugins.
    - **`StorageStrategy.StoreReference`**: The launcher only stores the ID of the search result,
      and the plugin that created it. To restore a result, the plugin is queried again. This
      allows the plugin to update key fields (i.e. the label) immediately. However, plugins that
      use this strategy must guarantee, that they can restore a search result at any time, in a
      timely manner. In particular, the plugin must be able to restore a search result without
      any network requests. This strategy is best suited for on-device search plugins.
