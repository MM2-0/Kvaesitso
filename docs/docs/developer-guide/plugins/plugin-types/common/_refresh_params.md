- `params` provides additional parameters:
    - `lang` is the current language of the launcher. This can differ from the system language, as
      the user can set a different language per app. This value should be used to localize the
      result.
    - `lastUpdated` the timestamp (in milliseconds) when `item` was last updated. This should be
      used to determine if the item needs to be refreshed again. If you decide not to refresh the
      item, you should return the original `item` parameter.