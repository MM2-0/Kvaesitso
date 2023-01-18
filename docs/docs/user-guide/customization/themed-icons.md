# Themed Icons

Themed Icons is a feature that adapt app icons to the launcher's color scheme:

![Themed icons off](/img/themed-icons-off.png)
![Themed icons on](/img/themed-icons-on.png)

Themed icons can be enabled in Settings > Appearance > Themed Icons.

## Themed icons on pre Android 13

It was first introduced by Google on Android 12. Unfortunately, Google did not provide an official API for third party app developers to support themed icons in their apps until Android 13. Instead they hardcoded all the themed icons into the Pixel Launcher. This is the reason why only Google icons could be themed on Android 12 Pixel devices.

In order to not violate any trademarks, Kvaesitso does not ship any third party icons. However, it can extract these themed icons from the Pixel launcher if it is installed on the device. The issue remains, that only Google icons are supported. Fortunately, the Lawnchair developers started a community effort to bring themed icon support to all apps for their launcher: [Lawnicons](https://github.com/LawnchairLauncher/lawnicons).

Kvaesitso supports themed icons through Lawnicons natively. All you need to do is to install the latest Lawnicons APK and enable Themed icons in the launcher settings (Settings > Appearance > Themed Icons). Kvaesitso will detect that Lawnicons is installed and extract the icons automatically.

## Themed icons on Android 13+

In Android 13, a [new API](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive#add_your_adaptive_icon_to_your_app) has been added for app developers to support themed icons in their app. It remains to be seen, how well developers adopt this API so you might still want to install Lawnicons (as described [here](#themed-icons-on-pre-android-13)).

## Auto generated themed icons

There is also an option to force themed icons to all icons (Settings > Appearance > Force themed icons). This scales the foreground layer of an icon down, adds a monochrome color filter and replaces the background with a solid color. Using this option is generally not recommended because icons may become illegible, but it might work well in combination with certain monochrome icon packs. You can also apply auto generated themed icons on a per-app basis by using the [customize menu](per-item-customization).
