# Feed

The feed is a personalized content page that lives to the left of your home screen, that can be
opened without leaving the launcher. Traditionally, launchers show the Google Discover feed here,
but there are other alternatives.

## Feed providers

The feed content is provided by a third party app, which needs to be installed on the device.

### Google Discover

On most devices, the Google app is preinstalled as a feed provider. Unfortunately, the Google app
can't be embedded as a feed directly, because it only allows to be embedded by system apps and
development builds. If you want to use Google Discover as your feed provider, you can
install [AIDL Bridge](https://github.com/amirzaidi/AIDLBridge/releases)
as a workaround. AIDL Bridge acts as a bridge to allow third party apps to connect to the
Google Discover feed.

> [!WARNING]
> AIDL Bridge uses an unofficial workaround to expose the Google Discover feed to third party apps.
> This workaround might stop working without further notice. Use at your own risk.

### Other providers

The following feed providers have been tested and are known to be working:

- **[Neo-Feed](https://github.com/NeoApplications/Neo-Feed)**: A simple RSS feed reader
- **[Smartspacer](https://github.com/KieronQuinn/Smartspacer)**: A customizable widgets page

## Enable the feed

After you have installed at least one feed provider, you can enable it under Settings >
Integrations > Feed. Then you can assign the feed action to the swipe right gesture under Settings >
Gestures

> [!NOTE]
> The feed can only be assigned to the swipe right gesture