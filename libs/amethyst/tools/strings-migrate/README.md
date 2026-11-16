# strings-migrate

Moves string keys from the Android app's `res/` into commons Compose
resources (`commonsUI/src/commonMain/composeResources`) across **all**
locales in one shot, preserving each element byte-for-byte so Crowdin
sees a pure move. Both trees are Crowdin-managed with the same android
layout (see `crowdin.yml`), so a migrated key keeps its translations.

```bash
tools/strings-migrate/migrate.py copy_to_clipboard relay_info
```

After migrating, repoint the code: `R.string.key` becomes `Res.string.key`
(`com.vitorpamplona.amethyst.commons.resources`), and the composable keeps
calling `stringRes(...)` via the commons bridge
(`com.vitorpamplona.amethyst.commons.ui.stringRes`).

The tool refuses keys whose value uses bare `%s`/`%d`: compose-resources
only formats positional `%1$s` args. Rewrite those keys (code and every
locale) first.
