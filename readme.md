# Kvæsitso

<img src="https://raw.githubusercontent.com/MM2-0/Kvaesitso/main/app/src/main/ic_launcher-playstore.png" width="128">

Kvæsitso is a launcher application for Android which replaces the device's default home screen. It
has been built from scratch, meaning it is not based on the AOSP launcher (like many other third
party launchers) nor does it try to recreate this launcher. Instead, Kvæsitso follows its own
concepts.

The main feature is a global search which does not only let you search on device for apps, contacts,
and calendar events, but also on web services like Wikipedia or your Nextcloud Instance.
Additionally it includes some useful tools, for example a calculator and a unit converter. You are
looking for a document or an information? Just search for it and Kvæsitso shows you the fastest way
to it.

## Installation

### Using the F-Droid application

The preferred way of installation is using the [F-Droid](https://f-droid.org) application. That way
you will always be notified about updates. However, Kvæsitso is not avaiable in the official F-Droid
repositories (and if it ever will be, all features depending on external APIs will probably be
disabled). Instead there is a [repository](https://github.com/MM2-0/fdroid) for all of MM20's apps. Just scan the code below or open
the link on your phone:

![qr code](https://raw.githubusercontent.com/MM2-0/fdroid/main/qrcode.png)

https://raw.githubusercontent.com/MM2-0/fdroid/main/fdroid/repo/?fingerprint=156FBAB952F6996415F198F3F29628D24B30E725B0F07A2B49C3A9B5161EEE1A

### Manual installation

You can also download the latest release from
the [releases page](https://github.com/MM2-0/Kvaesitso/releases) and install it manually.

## Report issues

If you notice any bugs or issues create a new issue in
the [issue tracker](https://github.com/MM2-0/Kvaesitso/issues). Before you do, please search the
existing issues for any similar issues. Please include any relevant information such as steps to
reproduce, stack traces, logs, and device information. These information can be founder under
Settings > About > Crash reporter and Settings > About > Export debug information.

## Feature requests

If you have an idea for a new feature, just create a new issue. However, there is no guarantee that
they will be implemented. If it's important for you, consider implementing it yourself,
see [contribute](#contribute).

## Build

Just open up the project in the latest stable version of Android Studio and run it. Before you do,
follow the steps below:

### Additional configuration

Some modules require additional configuration in order to work properly (for example some modules
require API keys which are not included in this repository). While you can still build this app
successfully even if you skip these steps, some features might be disabled in the resulting app if
you do so. Please refer to the instructions in the respective modules to learn how to set them up
properly.

Modules that require additional configuration:

- `:g-services`
- `:ms-services`
- `:weather`

## Contribute

Contributions are always welcome. If you want to fix any existing issues or implement smaller new
features just create a new pull request. If you plan to implement any (bigger) new features, please
create an issue first so we can discuss if and how this feature should be implemented.

If you want to help translating, go to the [Weblate project instance](https://i18n.mm20.de/engage/kvaesitso/).

<a href="https://i18n.mm20.de/engage/kvaesitso/">
<img src="https://i18n.mm20.de/widgets/kvaesitso/-/287x66-grey.png" alt ="Translation Status">
</a>

## Links

- Telegram group: https://t.me/Kvaesitso
- F-Droid-Repository: https://raw.githubusercontent.com/MM2-0/fdroid/master/fdroid/repo
- Archive (non-free pre-1.0 releases): https://github.com/MM2-0/Quaesitio-Archive

## License

This software is free software licensed under the GNU General Public License 3.0.

```

Copyright (C) 2021–2022 MM2-0 and the Kvæsitso contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
