# Launcher Cannot Be Updated

If you are trying to update the launcher, but the installation fails, you are most likely trying
to crossgrade from the F-Droid version to the GitHub version or vice versa. Both versions use
different signing keys, so you cannot update one with the other.

## Kvaesitso versions

There are two different release versions of Kvaesitso:

- **GitHub version**: This is the version that is released on GitHub. It includes all features and
  is also available on
  the [MM20 F-Droid repository](https://fdroid.mm20.de/app/de.mm20.launcher2.release) and on
  the [IzzyOnDroid F-Droid repository](https://apt.izzysoft.de/fdroid/index/apk/de.mm20.launcher2.release).
- **F-Droid version**: This version is built and signed, and distributed by the F-Droid maintainers.
  It is available on
  the [official F-Droid repository](https://f-droid.org/packages/de.mm20.launcher2.release). Some
  features disabled that depend on external APIs; most notably, there are fewer
  weather providers available. Furthermore, new versions are usually released with a delay (a few
  days up to a week).

## Check which version you have installed

Go to Settings > About. If the version number is something like `x.y.z`, you have the GitHub version
installed. If the version number ends in `-fdroid`, you have the F-Droid version installed.

## Switch between versions

You cannot switch versions without uninstalling the current version first. First, backup your
data in Settings > Backup & restore. Then, uninstall the current version and install the other
version. You can then restore your data in Settings > Backup & restore > Restore.

::: warning
The backup file format isn't guaranteed to be backward compatible. To ensure that all data is restored
correctly, it is recommended to install the previous version first, restore the backup, then update to
the latest version. For example, if you want to upgrade from `1.34.2-fdroid` to `1.35.0` (GitHub), install
`1.34.2` (GitHub) first, restore the backup, then update to `1.35.0` (GitHub).
:::
