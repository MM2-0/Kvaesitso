# Notes Widget

The notes widget allows you to write down quick notes on your home screen. It is not enabled by
default, but you can add it by tapping on the "Edit widgets" button at the bottom of the home screen
and selecting "Add widget" > "Notes".

## Usage

### Editing

The notes widget consists of a text field that you can use to write down notes. Basic markdown
syntax is supported:

<details>

- `**Bold**`
- `*Italic*`
- `` `Monospace` ``
- `# Heading 1`
- `## Heading 2`
- `### Heading 3`
- `#### Heading 4`
- `##### Heading 5`
- `###### Heading 6`
- `--- Horizontal rule`
- `> Quote`
- ` ``` Code block ``` `
- `- List item`
- `1. Ordered list item`
- `- [ ] Unchecked task`
- `- [x] Checked task`
- `[Link](https://example.com)`

</details>

### Export notes

Notes can be exported as markdown files. To do so, tap the <span class="material-symbols-rounded">
more_vert</span> icon in the bottom right corner and select "Save".

### Add notes

A new instance of the notes widget can be added by tapping
the <span class="material-symbols-rounded">more_vert</span> in an existing notes widget and
selecting "New note".

### Dismiss notes

Notes can be dismissed by tapping the <span class="material-symbols-rounded">more_vert</span> icon
in the bottom right corner and
selecting "Dismiss". If you dismiss a note, the widget will be removed, unless it is the last
instance
of a note widget. In this case the note widget's content will be cleared instead.

## File linking

An instance of a notes widget can be linked to a file. If a file is linked, the content of the file
is kept in sync with the note widget's content. To link a file, tap on
the <span class="material-symbols-rounded">link</span> icon and select a file. If the notes widget
is not empty and you select a file that is not empty, you will be asked which content you want to
keep.

To unlink a file, tap
on **<span class="material-symbols-rounded">link_off</span> Unlink**. 