# Color Schemes

Color schemes can be managed at settings > appearance > color schemes.

## Default

The default color scheme is different depending on the Android version:

- On **Android 12+**: the default color scheme is the color scheme provided by the system, usually based on the wallpaper (“Material You”).
- On **Android 8.1 - Android 11**: the default color scheme is based on the current wallpaper (“Material You Compat”)
- On **Android 8.0**: the default color scheme is a blueish fallback theme because the APIs to extract wallpaper colors do not exist on this version. This is also used on Android 8.1 - Android 11 if the wallpaper color extraction fails for some reason.

## Black and White

A high contrast theme using only black and white.

## Custom color schemes

Customize the launchers color scheme to your likings. You can have as many color schemes as you want.
Create a new color scheme by clicking the <span class="material-symbols-rounded">add</span> button in the bottom right corner, or by
selecting <span class="material-symbols-rounded">more_vert</span> > <span class="material-symbols-rounded">content_copy</span> **Duplicate** on an existing color scheme.

You can then edit a color scheme by selecting <span class="material-symbols-rounded">more_vert</span> > <span class="material-symbols-rounded">edit</span> **Edit**.

Kvaesitso uses the Material Design 3 system for its user interface. The color schemes are therefore
based on the Material Design 3 color system.

> [!INFO]
> For a detailed explanation of the color system, see https://m3.material.io/styles/color/the-color-system

### Key colors

A color scheme is defined by a set of key colors. These key colors are not used on their own, but
they are used to generate the rest of the color scheme. The key colors are:

- **Primary**: mainly used for interactive key components, like buttons, switches and input
  fields. Also used for [themed icons](/docs/user-guide/customization/themed-icons) and as elevation overlay color (cards, dialogs and other elevated
  surfaces will have a slight tint of this color)
- **Secondary**: used for less prominent components, such as chips, badges and some (non interactive) headlines
- **Tertiary**: not used anywhere at the moment, reserved for future use
- **Neutral**: used as background for cards, dialogs, settings, and other surfaces. Also used for text and some icons
- **Neutral variant**: used for banners, switch and slider tracks and for outlines
- **Error**: used to indicate errors, like invalid inputs in text fields

Key colors can be edited in the "color palette" section in the first row of the color scheme editor.
For each key color, you can select a custom color, or you can use the system default, which is
usually based on the wallpaper.

Material Design 3 comes with an algorithm to generate an entire key color palette based on a single
seed color (this is what the system uses to generate an entire color palette based on a single
wallpaper key color). To make use of this algorithm, each key color (except primary) has
a <span class="material-symbols-rounded">auto_fix_high</span> **From primary** button, which generates a new color based on the currently
selected primary color.

### Scheme colors

Scheme colors are colors that are derived from the key colors and that are used for actual UI
components.

> [!INFO]
> To derive these colors, Material 3 uses a color model called HCT (Hue, Chroma, Tone). In its core,
> it is similar to the probably more commonly known HSL (Hue, Saturation, Lightness) color model, but it
> better reflects how humans perceive colors. For more information, read https://material.io/blog/science-of-color-design
>
> Each scheme color is generated from a specific key color by changing the key color's tone.

To learn how the different scheme colors are used in components, refer to the
[Material 3 Design docs](https://m3.material.io/styles/color/the-color-system/color-roles).
Examples are shown in the color scheme editor. Keep in mind, that not all scheme colors are currently
used by Kvaesitso.

> [!TIP]
> Long press a color to display its name.

For each scheme color, there is one variant for light mode, and one for dark mode. To toggle between
light and dark mode variants, use the <span class="material-symbols-rounded">light_mode</span> and <span class="material-symbols-rounded">dark_mode</span> buttons in the top right corner
of each section.

For each scheme color, you can either select a custom color, or you can derive a color from a key color.
To derive a color from a key color, select <span class="material-symbols-rounded">palette</span> **Palette**, and then select the key color
to derive from. You can then adjust the tone of the derived color by dragging the **T** slider.
