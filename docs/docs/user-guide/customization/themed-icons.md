# Themed Icons

Themed Icons is a feature that adapt app icons to the launcher's color scheme:

![Themed icons off](/img/themed-icons-off.png)
![Themed icons on](/img/themed-icons-on.png)

Themed icons can be enabled for supported apps in Settings > Grid & icons > Themed Icons.

> [!NOTE]
> If you are an app developer, you can support themed icons by adding a `<monochrome>` drawable to
> your app icon. For more information refer to
> [the official documentation](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive#add_your_adaptive_icon_to_your_app).

While themed icons were originally introduced in Android 13, support has been backported to Android
8.0-12.
Note however, that some apps (especially preinstalled system apps) may not support themed icons on
older Android versions.

## Themable icon packs

Some icon packs, notably [Lawnicons](https://github.com/LawnchairLauncher/lawnicons)
and [Arcticons](https://github.com/Donnnno/Arcticons), have support
for themed icons. This is indicated by a <span class="material-symbols-rounded">palette</span> icon
in the icon pack selection dialog.

> [!NOTE]
> If you are an icon pack developer, you can indicate that your icon pack supports themed icons by
> adding
> the following intent filter:
>
> ```xml
> <intent-filter>
>
>    <action android:name="app.lawnchair.icons.THEMED_ICON" />
>    <category android:name="android.intent.category.DEFAULT" />
>
> </intent-filter>
> ```

## Auto generated themed icons

There is also an option to force themed icons to all icons (Settings > Grid & icons > Force themed
icons). This scales the foreground layer of an icon down, adds a monochrome color filter and
replaces the background with a solid color. Using this option is generally not recommended because
icons may become illegible, but it might work well in combination with certain monochrome icon
packs. You can also apply auto generated themed icons on a per-app basis by using
the [customize menu](per-item-customization).
