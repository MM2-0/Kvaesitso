# :i18n

This module contains all data required for internationalization and localization. This includes
strings, icons (if they require localization), and config and default values. **All resources that
might need localization must go to this module.**

## Contribute

![Translation status](https://i18n.mm20.de/widgets/kvaesitso/-/i18n/multi-auto.svg)

Go to the [Weblate project instance](https://i18n.mm20.de/engage/kvaesitso/) and start translating.

There are two components: i18n and units:

- `i18n` is the main component that contains most of the strings that are used within the app.

- `units` is an extra component that contains all the strings that are used by the unit converter.
Each unit has a `unit_[name]` and a `unit_[name]_symbol` resource. `unit_[name]_symbol` is the
symbol that is used in the search query. For SI units, this should be the SI symbol (m, s, kg and so on),
but other, non-SI units may need to be translated (e.g. nautical miles or horse powers.
**For technical reasons, these symbols may not contain spaces.**

