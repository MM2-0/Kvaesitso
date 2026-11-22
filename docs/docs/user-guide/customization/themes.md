# Import and Export Themes

Your custom color schemes, typographies, shapes and transparencies can be exported to a theme file
(`.kvtheme`) and imported on another device, or shared with other people.

Both options are at settings > appearance, at the bottom of the screen.

## Export a theme

1. Go to settings > appearance > <span class="material-symbols-rounded">arrow_circle_up</span> **Export theme**.
2. Enter a **name** and, optionally, an **author**.
3. Select what to include. A theme file can contain one of each:
   - a color scheme
   - a typography
   - a shape scheme
   - a transparency scheme

   Each part is optional, but at least one must be selected. Only your own schemes can be
   exported, built-in ones are not listed.
4. Either **Share** the theme directly with another app, or choose **Save as file**.

## Import a theme

There are two ways to import a theme file:

- Go to settings > appearance > <span class="material-symbols-rounded">arrow_circle_down</span> **Import theme** and select the file.
- Open a `.kvtheme` file from a file manager or from another app (for example a file that was
  shared with you). Kvaesitso opens the import dialog.

The import screen lists the contents of the theme. Then tap **Import**.

- If a scheme from the file already exists on your device (because you have imported the same
  theme before), it is marked with “Theme already exists and will be updated” and is replaced
  by the version from the file. Otherwise it is added as a new scheme.
- If **Apply theme** is enabled (the default), the imported schemes are selected right away.
  Disable it to only add them to your lists, so that you can select them later.

## File format

A `.kvtheme` file is a JSON file (MIME type `application/vnd.de.mm20.launcher2.theme`) with the
following top-level fields:

| Field            | Description                                         |
|------------------|-----------------------------------------------------|
| `version`        | Format version, `2` for the current format          |
| `name`           | Name of the theme                                   |
| `author`         | Optional author                                     |
| `colors`         | Optional [color scheme](color-schemes)              |
| `typography`     | Optional typography                                 |
| `shapes`         | Optional shape scheme                               |
| `transparencies` | Optional transparency scheme                        |

Each scheme keeps its ID, which is how an existing scheme is recognized on import. Theme files
from older versions of Kvaesitso (without `"version": 2`) can still be imported.

> [!INFO]
> Theme files only contain the appearance. Tags, favorites, widgets and other settings are not
> part of a theme; use settings > backup and restore to move them to another device.
