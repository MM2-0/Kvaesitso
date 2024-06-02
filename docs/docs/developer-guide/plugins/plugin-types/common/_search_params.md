- `params` provides additional parameters for the search:
    - `allowNetwork` is a flag that indicates whether the user has enabled online search for this
      query. Plugins are generally advised to respect this request. This flag exists mainly for
      privacy reasons: the majority of searches target offline results (like apps, or contacts).
      Sending every single search request to external servers is overkill and can be a privacy
      issue. (Besides, it's not very nice to overload servers with unnecessary requests.) To reduce
      the amount of data that is sent to external servers, users can control, whether a search
      should include online results or not.
    - `lang` is the current language of the launcher. This can differ from the system language, as
      the user can set a different language per app. This value should be used for any localization
      in the search results.