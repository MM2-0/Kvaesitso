# Color Schemes

## Default

The default color scheme is different depending on the Android version:

- On **Android 12+**: the default color scheme is the color scheme provided by the system, usually based on the wallpaper (“Material You”).
- On **Android 8.1 - Android 11**: the default color scheme is based on the current wallpaper (“Material You Compat”)
- On **Android 8.0**: the default color scheme is a blueish fallback theme because the APIs to extract wallpaper colors do not exist on this version. This is also used on Android 8.1 - Android 11 if the wallpaper color extraction fails for some reason.

## Black and White

A high contrast theme using only black and white.

## Custom

Customize the launchers color scheme to your likings. There is a simple mode and an advanced mode. You can toggle between them using the overflow menu in the top right corner.

Since Kvaesitso uses Material Design 3, all colors are based on the [Material 3 color system](https://m3.material.io/styles/color/the-color-system/key-colors-tones).

### Simple mode

In simple mode, you specify six base colors from which a light and a dark color scheme will be generated.

:::tip
You can also specify only one color, the primary color, and generate the rest using the “Generate from primary color” option in the overflow menu. This will generate a whole palette from one color, using the same method that wallpaper-based color schemes use to generate the entire color scheme from only one base color.
:::

These are the six base colors. Refer to the [Material 3 documentation](https://m3.material.io/styles/color/the-color-system/key-colors-tones) for more information.

- **Primary**: mainly used for interactive key components, like buttons, switches and input fields. Also used for [themed icons](./themed-icons) and as elevation overlay color (cards, dialogs and other elevated surfaces will have a slight tint of this color)
- **Secondary**: used for less prominent components, such as chips, badges and some (non interactive) headlines
- **Tertiary**: not used anywhere at the moment, reserved for future use
- **Neutral**: used as background for cards, dialogs, settings, and other surfaces. Also used for text and some icons.
- **Neutral variant**: used for banners, switch and slider tracks and for outlines.
- **Error**: used to indicate errors, like invalid inputs in text fields.

Several color tones (brightness levels) are generated from each color. These tonal values are fixed so that the brightness of any given color doesn't actually matter that much. Surfaces and texts will always have high contrast to each other even though both are generated from the same base color.

### Advanced mode

In advanced mode, you have full control over every color that might or might not be used anywhere in the launcher UI. In this mode, you can customize light and dark mode color scheme seperately. Colors are used as is, without any further processing.

:::tip
Since there are quite a lot of different colors and color shades, it can be tedious to customize every single color. A good approach is to set the base colors in simple mode and then switch to advanced mode to tweak only individual colors.
:::

:::caution
If you switch back from advanced to simple mode at a later point, you will lose your customizations.
:::

Please refer to the [Material Design 3 documentation](https://m3.material.io/styles/color/the-color-system/color-roles) to get an understanding how each of these colors is used.
