#!/usr/bin/env python3
"""Append custom (non-Material) glyphs to the subset Material Symbols font.

Some icons Amethyst needs are third-party logos that Google's Material
Symbols font will never carry (e.g. the OpenTimestamps stamp). Their traced
SVG outlines live in `custom/` and this script bakes them into the subset
TTF at private-use codepoints, so `MaterialSymbols.kt` can reference them
exactly like any Material glyph.

subset.sh runs this automatically after pyftsubset; it can also be run
directly against an existing font:

    python3 add_custom_glyphs.py <path-to-ttf>

The SVGs are potrace output: the raw path coordinates are y-up already (the
file's group transform re-flips them for SVG display), so they map into font
space with a plain uniform scale + translate.
"""

import os
import re
import sys

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.svgLib.path import parse_path
from fontTools.ttLib import TTFont

HERE = os.path.dirname(os.path.abspath(__file__))

# codepoint -> (glyph name, traced SVG, content-box fractions). Keep
# codepoints at the very end of the BMP private-use area (U+F8xx tail) to
# stay clear of the range Material Symbols actually allocates.
#
# Material's own solid shapes draw inside 80..880 of a 960 upm em; a glyph
# with fine outlines (like the OpenTimestamps stamp) reads much lighter at
# the same bounds, so it gets a near-full-em box to appear the same size.
CUSTOM = {
    0xF8F0: ("opentimestamps", os.path.join(HERE, "custom", "opentimestamps.svg"), 20 / 960, 940 / 960),
}


def svg_path_data(svg_file):
    with open(svg_file, "r", encoding="utf-8") as f:
        svg = f.read()
    return re.findall(r'<path[^>]*\bd="([^"]+)"', svg)


def draw_svg(paths, pen):
    for d in paths:
        parse_path(d, pen)


def build_glyph(paths, upm, box_min_frac, box_max_frac):
    # First pass: raw outline bounds.
    bounds = BoundsPen(None)
    draw_svg(paths, bounds)
    x_min, y_min, x_max, y_max = bounds.bounds

    # Uniform scale into the content box, centered on both axes.
    box_min = box_min_frac * upm
    box_max = box_max_frac * upm
    box = box_max - box_min
    scale = box / max(x_max - x_min, y_max - y_min)
    tx = box_min + (box - (x_max - x_min) * scale) / 2 - x_min * scale
    ty = box_min + (box - (y_max - y_min) * scale) / 2 - y_min * scale

    tt_pen = TTGlyphPen(None)
    quad_pen = Cu2QuPen(tt_pen, max_err=1.0)
    draw_svg(paths, TransformPen(quad_pen, (scale, 0, 0, scale, tx, ty)))
    return tt_pen.glyph()


def main(font_path):
    font = TTFont(font_path)
    upm = font["head"].unitsPerEm
    order = font.getGlyphOrder()

    for codepoint, (name, svg_file, box_min_frac, box_max_frac) in sorted(CUSTOM.items()):
        glyph = build_glyph(svg_path_data(svg_file), upm, box_min_frac, box_max_frac)

        if name not in order:
            order.append(name)
            font.setGlyphOrder(order)
        font["glyf"][name] = glyph
        glyph.recalcBounds(font["glyf"])
        font["hmtx"][name] = (upm, glyph.xMin)
        for table in font["cmap"].tables:
            if table.isUnicode():
                table.cmap[codepoint] = name
        print(f"Added {name} at U+{codepoint:04X}")

    font["maxp"].numGlyphs = len(order)
    font.save(font_path)
    print(f"Wrote {font_path}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
