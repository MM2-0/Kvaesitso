# Unicode emoji palette

`generate.py` builds
`commonsUI/src/commonMain/composeResources/files/emoji/emoji.tsv`, the palette the
shared emoji picker (`commonsUI/.../commons/unicodeEmoji/ui/EmojiPicker.kt`) shows.
Desktop has no system emoji keyboard, so this picker is the only way to type an
emoji there.

The source is Unicode's `emoji-test.txt` (UTS #51), distributed under the
permissive [Unicode License v3](https://www.unicode.org/license.txt); the
generated file keeps its copyright notice in its header.

```bash
curl -sSO https://unicode.org/Public/emoji/latest/emoji-test.txt
python3 tools/emoji-data/generate.py emoji-test.txt \
    commonsUI/src/commonMain/composeResources/files/emoji/emoji.tsv
```

`--max-version` (default `15.0`) caps the Emoji version offered: newer glyphs
render as tofu on desktops whose system emoji font has not caught up yet. Raise
it once the fonts shipped by Windows, macOS and the common Linux distributions
cover the newer set.
